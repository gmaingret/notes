import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import type { Response } from 'express';
import { db } from '../../db/index.js';
import { users, documents } from '../../db/schema.js';
import { eq } from 'drizzle-orm';

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

export function setRefreshCookie(res: Response, userId: string): void {
  const token = issueRefreshToken(userId);
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
