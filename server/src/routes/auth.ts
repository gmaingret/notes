import { Router } from 'express';
import { z } from 'zod';
import passport from 'passport';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { db } from '../../db/index.js';
import { users } from '../../db/schema.js';
import { eq } from 'drizzle-orm';
import {
  issueAccessToken,
  setRefreshCookie,
  clearRefreshCookie,
  hashPassword,
  createInboxIfNotExists,
} from '../services/authService.js';

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
  const existing = await db.query.users.findFirst({ where: eq(users.email, email) });
  if (existing) {
    return res.status(409).json({ field: 'email', message: 'Email already registered' });
  }

  const passwordHash = await hashPassword(password);
  const [user] = await db.insert(users).values({ email, passwordHash }).returning();

  // AUTH-05: Inbox for new user
  await createInboxIfNotExists(user.id);

  const accessToken = issueAccessToken(user.id);
  setRefreshCookie(res, user.id);

  return res.status(201).json({ accessToken, user: { id: user.id, email: user.email } });
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
  setRefreshCookie(res, user.id);

  return res.json({ accessToken, user: { id: user.id, email: user.email } });
});

// AUTH-02: Refresh token
authRouter.post('/refresh', (req, res) => {
  const token = req.cookies?.refreshToken;
  if (!token) return res.status(401).json({ error: 'No refresh token' });

  try {
    const payload = jwt.verify(token, process.env.JWT_REFRESH_SECRET!) as { sub: string };
    const accessToken = issueAccessToken(payload.sub);
    return res.json({ accessToken });
  } catch {
    return res.status(401).json({ error: 'Invalid or expired refresh token' });
  }
});

// AUTH-04: Logout
authRouter.post('/logout', (req, res) => {
  clearRefreshCookie(res);
  return res.json({ message: 'Logged out' });
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
    setRefreshCookie(res, user.id);

    // Redirect to app root with token in hash fragment (never sent to server)
    return res.redirect(`/?token=${encodeURIComponent(accessToken)}`);
  }
);
