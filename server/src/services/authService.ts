import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { createHash } from 'crypto';
import type { Response } from 'express';
import { db } from '../../db/index.js';
import { users, documents, refreshTokens } from '../../db/schema.js';
import { and, eq, isNull, ne } from 'drizzle-orm';

const BCRYPT_ROUNDS = 12;
const ACCESS_TOKEN_TTL = '15m';
const REFRESH_TOKEN_TTL = '7d';
const REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60 * 1000;

export function issueAccessToken(userId: string): string {
  return jwt.sign({ sub: userId }, process.env.JWT_SECRET!, { expiresIn: ACCESS_TOKEN_TTL });
}

export function issueRefreshToken(userId: string): string {
  return jwt.sign({ sub: userId }, process.env.JWT_REFRESH_SECRET!, { expiresIn: REFRESH_TOKEN_TTL });
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

export async function setRefreshCookie(res: Response, userId: string): Promise<void> {
  const token = issueRefreshToken(userId);
  res.cookie('refreshToken', token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'strict',
    maxAge: REFRESH_COOKIE_MAX_AGE,
  });
  // Store hash of token in DB for revocation support
  const tokenHash = hashToken(token);
  await db.insert(refreshTokens).values({
    userId,
    tokenHash,
    expiresAt: new Date(Date.now() + REFRESH_COOKIE_MAX_AGE),
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
