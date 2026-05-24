import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { createHash } from 'crypto';
import type { Response } from 'express';
import { db } from '../../db/index.js';
import { users, documents, refreshTokens } from '../../db/schema.js';
import { and, eq, isNull, ne } from 'drizzle-orm';

const BCRYPT_ROUNDS = 12;
const ACCESS_TOKEN_TTL = '15m';
const REFRESH_TOKEN_TTL = '90d';
const REFRESH_COOKIE_MAX_AGE = 90 * 24 * 60 * 60 * 1000;

// Refresh tokens rotate on every use, with a short grace window so two concurrent
// refresh requests from the same client both succeed (b5503ea race). The newly
// issued token is cached against the old token's hash for ROTATION_GRACE_MS so
// the racing request can be served the same successor.
const ROTATION_GRACE_MS = 30 * 1000;
const rotationGraceCache = new Map<string, { newToken: string; expiresAt: number }>();

export function issueAccessToken(userId: string): string {
  return jwt.sign({ sub: userId }, process.env.JWT_SECRET!, { expiresIn: ACCESS_TOKEN_TTL });
}

export function issueRefreshToken(userId: string): string {
  return jwt.sign({ sub: userId }, process.env.JWT_REFRESH_SECRET!, { expiresIn: REFRESH_TOKEN_TTL });
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

/**
 * Issues a new refresh token, stores its hash in DB, returns the raw token.
 * Callers decide how to deliver it (cookie for web, JSON body for mobile).
 */
export async function issueAndStoreRefreshToken(userId: string): Promise<string> {
  const token = issueRefreshToken(userId);
  const tokenHash = hashToken(token);
  await db.insert(refreshTokens).values({
    userId,
    tokenHash,
    expiresAt: new Date(Date.now() + REFRESH_COOKIE_MAX_AGE),
  });
  return token;
}

/**
 * Sets a refresh token as an httpOnly cookie on the response.
 * Pass an existing token (from issueAndStoreRefreshToken) to avoid double-issuing.
 */
export function setRefreshCookieFromToken(res: Response, token: string): void {
  res.cookie('refreshToken', token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    maxAge: REFRESH_COOKIE_MAX_AGE,
  });
}

export function clearRefreshCookie(res: Response): void {
  res.clearCookie('refreshToken', {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
  });
}

export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, BCRYPT_ROUNDS);
}

export async function isRefreshTokenRevoked(token: string): Promise<boolean> {
  const hash = hashToken(token);
  const row = await db.query.refreshTokens.findFirst({
    where: and(eq(refreshTokens.tokenHash, hash), isNull(refreshTokens.revokedAt)),
  });
  return !row; // If no active row found, it's revoked (or never stored)
}

export type RefreshAttempt =
  | { kind: 'invalid' }
  | { kind: 'rotated'; userId: string; newToken: string }
  | { kind: 'graced'; userId: string; newToken: string };

/**
 * Verifies, rotates, or grace-serves a refresh token.
 *
 *  - active token (revokedAt IS NULL) → rotate: issue T', mark old revoked,
 *    cache T' for grace window, return kind='rotated'.
 *  - just-revoked token within grace window → return cached successor T',
 *    return kind='graced'.
 *  - anything else (missing, expired, revoked past grace, bad signature) →
 *    kind='invalid'.
 */
export async function rotateOrGraceRefreshToken(rawToken: string): Promise<RefreshAttempt> {
  let payload: { sub: string };
  try {
    payload = jwt.verify(rawToken, process.env.JWT_REFRESH_SECRET!) as { sub: string };
  } catch {
    return { kind: 'invalid' };
  }

  const tokenHash = hashToken(rawToken);
  const row = await db.query.refreshTokens.findFirst({
    where: eq(refreshTokens.tokenHash, tokenHash),
  });
  if (!row) return { kind: 'invalid' };
  if (row.expiresAt && row.expiresAt.getTime() < Date.now()) return { kind: 'invalid' };

  if (row.revokedAt === null) {
    const newToken = await issueAndStoreRefreshToken(payload.sub);
    await db.update(refreshTokens)
      .set({ revokedAt: new Date() })
      .where(eq(refreshTokens.tokenHash, tokenHash));
    setRotationGrace(tokenHash, newToken);
    return { kind: 'rotated', userId: payload.sub, newToken };
  }

  const graced = getRotationGrace(tokenHash);
  if (graced) return { kind: 'graced', userId: payload.sub, newToken: graced };
  return { kind: 'invalid' };
}

function setRotationGrace(oldHash: string, newToken: string): void {
  rotationGraceCache.set(oldHash, { newToken, expiresAt: Date.now() + ROTATION_GRACE_MS });
}

function getRotationGrace(oldHash: string): string | null {
  const entry = rotationGraceCache.get(oldHash);
  if (!entry) return null;
  if (entry.expiresAt < Date.now()) {
    rotationGraceCache.delete(oldHash);
    return null;
  }
  return entry.newToken;
}

export async function revokeRefreshToken(token: string): Promise<void> {
  const hash = hashToken(token);
  await db.update(refreshTokens)
    .set({ revokedAt: new Date() })
    .where(and(eq(refreshTokens.tokenHash, hash), isNull(refreshTokens.revokedAt)));
}

export async function revokeAllUserTokensExcept(userId: string, currentToken: string): Promise<void> {
  const currentHash = hashToken(currentToken);
  await db.update(refreshTokens)
    .set({ revokedAt: new Date() })
    .where(and(
      eq(refreshTokens.userId, userId),
      isNull(refreshTokens.revokedAt),
      ne(refreshTokens.tokenHash, currentHash),
    ));
}

// AUTH-05: Create Inbox document if user has no documents (idempotent)
export async function createInboxIfNotExists(userId: string): Promise<void> {
  const existing = await db
    .select({ id: documents.id })
    .from(documents)
    .where(eq(documents.userId, userId))
    .limit(1);

  if (existing.length === 0) {
    await db.insert(documents).values({
      userId,
      title: 'Inbox',
      position: 1.0,
    });
  }
}
