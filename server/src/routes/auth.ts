import { Router } from 'express';
import { z } from 'zod';
import passport from 'passport';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { OAuth2Client } from 'google-auth-library';
import { db } from '../../db/index.js';
import { users } from '../../db/schema.js';
import { eq } from 'drizzle-orm';
import {
  issueAccessToken,
  issueAndStoreRefreshToken,
  setRefreshCookieFromToken,
  clearRefreshCookie,
  hashPassword,
  createInboxIfNotExists,
  isRefreshTokenRevoked,
  revokeRefreshToken,
  revokeAllUserTokensExcept,
} from '../services/authService.js';
import { validatePasswordPolicy } from '../services/passwordPolicy.js';
import { requireAuth } from '../middleware/auth.js';

const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

export const authRouter = Router();

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

// AUTH-01: Register
authRouter.post('/register', async (req, res) => {
  const result = registerSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  const { email, password } = result.data;

  // AUTH-01/AUTH-02: Enforce password policy before checking for duplicates
  const policyError = validatePasswordPolicy(password);
  if (policyError) {
    return res.status(400).json({ errors: { password: [policyError] } });
  }

  const existing = await db.query.users.findFirst({ where: eq(users.email, email) });
  if (existing) {
    return res.status(409).json({ field: 'email', message: 'Email already registered' });
  }

  const passwordHash = await hashPassword(password);
  const [user] = await db.insert(users).values({ email, passwordHash }).returning();

  // AUTH-05: Inbox for new user
  await createInboxIfNotExists(user.id);

  const accessToken = issueAccessToken(user.id);
  const refreshToken = await issueAndStoreRefreshToken(user.id);
  setRefreshCookieFromToken(res, refreshToken);

  return res.status(201).json({ accessToken, refreshToken, user: { id: user.id, email: user.email } });
});

// AUTH-02: Login
authRouter.post('/login', async (req, res) => {
  const result = loginSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  const { email, password } = result.data;
  const user = await db.query.users.findFirst({ where: eq(users.email, email) });

  if (!user || !user.passwordHash) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  const valid = await bcrypt.compare(password, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }

  const accessToken = issueAccessToken(user.id);
  const refreshToken = await issueAndStoreRefreshToken(user.id);
  setRefreshCookieFromToken(res, refreshToken);

  return res.json({ accessToken, refreshToken, user: { id: user.id, email: user.email } });
});

// AUTH-02: Refresh token
authRouter.post('/refresh', async (req, res) => {
  const token = req.cookies?.refreshToken;
  if (!token) return res.status(401).json({ error: 'No refresh token' });

  try {
    const payload = jwt.verify(token, process.env.JWT_REFRESH_SECRET!) as { sub: string };
    if (await isRefreshTokenRevoked(token)) {
      return res.status(401).json({ error: 'Token has been revoked' });
    }

    const accessToken = issueAccessToken(payload.sub);
    return res.json({ accessToken });
  } catch {
    return res.status(401).json({ error: 'Invalid or expired refresh token' });
  }
});

// AUTH-04: Logout (cookie-based — web client)
authRouter.post('/logout', async (req, res) => {
  const token = req.cookies?.refreshToken;
  if (token) {
    try { await revokeRefreshToken(token); } catch { /* ignore — best effort */ }
  }
  clearRefreshCookie(res);
  return res.json({ message: 'Logged out' });
});

// Token-based refresh — for native clients (Android) that manage tokens explicitly
// instead of relying on httpOnly cookies. Accepts refresh token in request body,
// returns new access token as JSON. Refresh token is NOT rotated — it stays valid
// for its full 7-day lifetime to avoid race conditions on mobile.
authRouter.post('/refresh/token', async (req, res) => {
  const { refreshToken: token } = req.body ?? {};
  if (!token) return res.status(401).json({ error: 'No refresh token' });

  try {
    const payload = jwt.verify(token, process.env.JWT_REFRESH_SECRET!) as { sub: string };
    if (await isRefreshTokenRevoked(token)) {
      return res.status(401).json({ error: 'Token has been revoked' });
    }
    const accessToken = issueAccessToken(payload.sub);
    return res.json({ accessToken });
  } catch {
    return res.status(401).json({ error: 'Invalid or expired refresh token' });
  }
});

// Token-based logout — for native clients (Android)
authRouter.post('/logout/token', async (req, res) => {
  const { refreshToken: token } = req.body ?? {};
  if (token) {
    try { await revokeRefreshToken(token); } catch { /* ignore — best effort */ }
  }
  return res.json({ message: 'Logged out' });
});

// SESS-04: Change password
const changePasswordSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: z.string().min(8),
});

authRouter.post('/change-password', requireAuth, async (req, res) => {
  const result = changePasswordSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  const { currentPassword, newPassword } = result.data;
  const userId = (req.user as { id: string }).id;

  const user = await db.query.users.findFirst({ where: eq(users.id, userId) });
  if (!user || !user.passwordHash) {
    return res.status(400).json({ error: 'Account uses Google sign-in. Password cannot be changed.' });
  }

  const valid = await bcrypt.compare(currentPassword, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: 'Current password is incorrect' });
  }

  const policyError = validatePasswordPolicy(newPassword);
  if (policyError) {
    return res.status(400).json({ errors: { newPassword: [policyError] } });
  }

  const newHash = await hashPassword(newPassword);
  await db.update(users)
    .set({ passwordHash: newHash, updatedAt: new Date() })
    .where(eq(users.id, userId));

  // Revoke all other sessions; issue new refresh cookie for current session
  const currentToken = req.cookies?.refreshToken;
  if (currentToken) {
    await revokeAllUserTokensExcept(userId, currentToken);
  }
  const newRefreshToken = await issueAndStoreRefreshToken(userId);
  setRefreshCookieFromToken(res, newRefreshToken);

  return res.json({ message: 'Password changed successfully' });
});

// AUTH-03: Google token endpoint for native Android Credential Manager flow
authRouter.post('/google/token', async (req, res) => {
  const { idToken } = req.body;
  if (!idToken) return res.status(400).json({ error: 'Missing idToken' });

  try {
    const ticket = await googleClient.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    const payload = ticket.getPayload();
    if (!payload?.email || !payload?.sub) {
      return res.status(400).json({ error: 'Invalid Google token payload' });
    }

    // Same account-linking logic as Passport Google strategy in middleware/auth.ts
    let user = await db.query.users.findFirst({ where: eq(users.googleId, payload.sub) });
    if (!user) {
      user = await db.query.users.findFirst({ where: eq(users.email, payload.email) });
      if (user) {
        // Link Google account to existing email/password user
        await db.update(users)
          .set({ googleId: payload.sub, updatedAt: new Date() })
          .where(eq(users.id, user.id));
      } else {
        // New user via Google
        const [newUser] = await db.insert(users)
          .values({ email: payload.email, googleId: payload.sub })
          .returning();
        user = newUser;
        await createInboxIfNotExists(user.id);
      }
    }

    const accessToken = issueAccessToken(user.id);
    const refreshToken = await issueAndStoreRefreshToken(user.id);
    setRefreshCookieFromToken(res, refreshToken);
    return res.json({ accessToken, refreshToken, user: { id: user.id, email: user.email } });
  } catch (e) {
    console.error('Google token verification failed:', e);
    return res.status(401).json({ error: 'Google token verification failed' });
  }
});

// AUTH-03: Google OAuth initiation
authRouter.get(
  '/google',
  passport.authenticate('google', { scope: ['profile', 'email'], session: false })
);

// AUTH-03: Google OAuth callback
authRouter.get(
  '/google/callback',
  passport.authenticate('google', { session: false, failureRedirect: '/login?error=oauth' }),
  async (req, res) => {
    const user = req.user as { id: string; email: string };

    // AUTH-05: Create Inbox for new Google users (idempotent)
    await createInboxIfNotExists(user.id);

    const accessToken = issueAccessToken(user.id);
    const refreshToken = await issueAndStoreRefreshToken(user.id);
    setRefreshCookieFromToken(res, refreshToken);

    // Redirect to app root with token in hash fragment (never sent to server)
    // SESS-01: use #token= not ?token= so the JWT is never in the URL query string
    return res.redirect(`/#token=${encodeURIComponent(accessToken)}`);
  }
);
