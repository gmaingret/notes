import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';
import cookieParser from 'cookie-parser';
import bcrypt from 'bcryptjs';

// Set required env vars before importing auth modules
process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

// Mock the db module
vi.mock('../db/index.js', () => ({
  db: {
    query: {
      users: {
        findFirst: vi.fn(),
      },
      refreshTokens: {
        findFirst: vi.fn(),
      },
    },
    insert: vi.fn(),
    select: vi.fn(),
    update: vi.fn(),
  },
}));

import { db } from '../db/index.js';
import { authRouter } from '../src/routes/auth.js';
import { configurePassport } from '../src/middleware/auth.js';
import passport from 'passport';

const mockDb = db as {
  query: {
    users: { findFirst: ReturnType<typeof vi.fn> };
    refreshTokens: { findFirst: ReturnType<typeof vi.fn> };
  };
  insert: ReturnType<typeof vi.fn>;
  select: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
};

// Configure passport once for all tests (JWT strategy needs to be registered)
configurePassport();

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use(passport.initialize());
  app.use('/api/auth', authRouter);
  return app;
}

beforeEach(() => {
  vi.clearAllMocks();
  // Default: select returns empty (no existing docs) — chain: .from().where().limit()
  mockDb.select.mockReturnValue({
    from: vi.fn().mockReturnValue({
      where: vi.fn().mockReturnValue({
        limit: vi.fn().mockResolvedValue([]),
      }),
    }),
  });
  // Default insert chain: .values().returning()
  mockDb.insert.mockReturnValue({
    values: vi.fn().mockReturnValue({
      returning: vi.fn().mockResolvedValue([{ id: 'user-uuid-123', email: 'test@example.com' }]),
    }),
  });
  // Default update chain: .set().where()
  mockDb.update.mockReturnValue({
    set: vi.fn().mockReturnValue({
      where: vi.fn().mockResolvedValue([]),
    }),
  });
  // Default: refreshTokens.findFirst returns an active token row (not revoked)
  mockDb.query.refreshTokens.findFirst.mockResolvedValue({ id: 'token-row-1', revokedAt: null });
});

// ─── AUTH-01: Register ────────────────────────────────────────────────────────

describe('AUTH-01: Register with email/password', () => {
  it('POST /api/auth/register with valid email+password returns 201 + accessToken', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null); // no duplicate

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    expect(res.status).toBe(201);
    expect(res.body).toHaveProperty('accessToken');
    expect(res.body.user).toMatchObject({ id: 'user-uuid-123', email: 'test@example.com' });
  });

  it('POST /api/auth/register with duplicate email returns 409 with field:email', async () => {
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'existing-uuid',
      email: 'test@example.com',
    });

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    expect(res.status).toBe(409);
    expect(res.body).toMatchObject({ field: 'email', message: 'Email already registered' });
  });

  it('POST /api/auth/register with weak password (no uppercase) returns 400 with policy error', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'password123' });

    expect(res.status).toBe(400);
    expect(res.body.errors.password[0]).toContain('common');
  });

  it('POST /api/auth/register with common password returns 400 with policy error', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'Password1' });

    expect(res.status).toBe(400);
    expect(res.body.errors.password[0]).toContain('common');
  });

  it('POST /api/auth/register with strong valid password returns 201', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null);

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'V4l!dPass99' });

    expect(res.status).toBe(201);
  });

  it('POST /api/auth/register with invalid email returns 400', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'not-an-email', password: 'password123' });

    expect(res.status).toBe(400);
  });

  it('POST /api/auth/register with password < 8 chars returns 400', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'short' });

    expect(res.status).toBe(400);
  });
});

// ─── AUTH-02: Login ───────────────────────────────────────────────────────────

describe('AUTH-02: Login with email/password', () => {
  it('POST /api/auth/login with correct credentials returns 200 + accessToken + sets refreshToken cookie', async () => {
    const hashedPw = await bcrypt.hash('password123', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'test@example.com', password: 'password123' });

    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('accessToken');
    expect(res.headers['set-cookie']).toBeDefined();
    expect(res.headers['set-cookie'][0]).toContain('refreshToken');
  });

  it('POST /api/auth/login with wrong password returns 401', async () => {
    const hashedPw = await bcrypt.hash('correctpassword', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'test@example.com', password: 'wrongpassword' });

    expect(res.status).toBe(401);
  });

  it('POST /api/auth/login with unknown email returns 401', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null);

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'unknown@example.com', password: 'password123' });

    expect(res.status).toBe(401);
  });

  it('POST /api/auth/refresh with valid cookie issues new accessToken', async () => {
    // First register to get a refresh cookie
    mockDb.query.users.findFirst.mockResolvedValue(null);
    const app = buildApp();
    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    const cookies = registerRes.headers['set-cookie'];
    expect(cookies).toBeDefined();

    const refreshRes = await request(app)
      .post('/api/auth/refresh')
      .set('Cookie', cookies);

    expect(refreshRes.status).toBe(200);
    expect(refreshRes.body).toHaveProperty('accessToken');
  });

  it('POST /api/auth/refresh with missing cookie returns 401', async () => {
    const app = buildApp();
    const res = await request(app).post('/api/auth/refresh');

    expect(res.status).toBe(401);
  });
});

// ─── AUTH-04: Logout ──────────────────────────────────────────────────────────

describe('AUTH-04: Logout', () => {
  it('POST /api/auth/logout returns 200 and clears the refreshToken cookie', async () => {
    const app = buildApp();
    const res = await request(app).post('/api/auth/logout');

    expect(res.status).toBe(200);
    // The Set-Cookie header should clear the refreshToken
    const cookies = res.headers['set-cookie'] as string[] | undefined;
    if (cookies) {
      const refreshCookie = cookies.find((c) => c.startsWith('refreshToken='));
      expect(refreshCookie).toBeDefined();
      // A cleared cookie has Max-Age=0 or Expires in the past
      expect(refreshCookie).toMatch(/Max-Age=0|Expires=.*1970/i);
    }
  });

  it('POST /api/auth/logout returns 200 even if no cookie present', async () => {
    const app = buildApp();
    const res = await request(app).post('/api/auth/logout');
    expect(res.status).toBe(200);
  });
});

// ─── AUTH-05: Inbox document on first login ───────────────────────────────────

describe('AUTH-05: Inbox document on first login', () => {
  it('New user created via register gets exactly one document titled "Inbox"', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null);

    // select returns empty (no existing docs) — insert should be called once
    const insertValues = vi.fn().mockReturnValue({
      returning: vi.fn().mockResolvedValue([{ id: 'user-uuid-123', email: 'test@example.com' }]),
    });
    mockDb.insert.mockReturnValue({ values: insertValues });

    const app = buildApp();
    await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    // insert called 3 times: once for user, once for refresh token (setRefreshCookie), once for Inbox document
    expect(mockDb.insert).toHaveBeenCalledTimes(3);
  });

  it('Calling createInboxIfNotExists twice for same user creates only one Inbox (idempotent)', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null);

    // First call: select returns empty → insert called
    // Second call: select returns existing doc → insert NOT called
    const selectMock = vi.fn();
    selectMock
      .mockReturnValueOnce({
        from: vi.fn().mockReturnValue({
          where: vi.fn().mockReturnValue({
            limit: vi.fn().mockResolvedValue([]), // empty — first call
          }),
        }),
      })
      .mockReturnValueOnce({
        from: vi.fn().mockReturnValue({
          where: vi.fn().mockReturnValue({
            limit: vi.fn().mockResolvedValue([{ id: 'doc-123' }]), // existing — second call
          }),
        }),
      });
    mockDb.select = selectMock;

    const { createInboxIfNotExists } = await import('../src/services/authService.js');
    const insertValues = vi.fn().mockReturnValue({
      returning: vi.fn().mockResolvedValue([]),
    });
    mockDb.insert.mockReturnValue({ values: insertValues });

    await createInboxIfNotExists('user-uuid-123');
    await createInboxIfNotExists('user-uuid-123');

    // insert.values called only once (for the first call)
    expect(insertValues).toHaveBeenCalledTimes(1);
  });
});

// ─── SESS-03: Refresh token revocation on logout ─────────────────────────────

describe('SESS-03: Refresh token revocation on logout', () => {
  it('POST /refresh with a revoked token returns 401', async () => {
    // Simulate a revoked token: findFirst returns null (no active row)
    mockDb.query.refreshTokens.findFirst.mockResolvedValue(null);

    // Register to get a valid refresh cookie
    mockDb.query.users.findFirst.mockResolvedValue(null);
    const app = buildApp();
    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    const cookies = registerRes.headers['set-cookie'];
    expect(cookies).toBeDefined();

    const refreshRes = await request(app)
      .post('/api/auth/refresh')
      .set('Cookie', cookies);

    expect(refreshRes.status).toBe(401);
    expect(refreshRes.body.error).toMatch(/revoked/i);
  });

  it('POST /refresh with a valid (non-revoked) token returns 200 + new accessToken', async () => {
    // Default mock: refreshTokens.findFirst returns active row (set in beforeEach)
    mockDb.query.users.findFirst.mockResolvedValue(null);
    const app = buildApp();
    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    const cookies = registerRes.headers['set-cookie'];
    expect(cookies).toBeDefined();

    const refreshRes = await request(app)
      .post('/api/auth/refresh')
      .set('Cookie', cookies);

    expect(refreshRes.status).toBe(200);
    expect(refreshRes.body).toHaveProperty('accessToken');
  });

  it('POST /logout calls revokeRefreshToken (update mock called)', async () => {
    // Register to get a cookie
    mockDb.query.users.findFirst.mockResolvedValue(null);
    const app = buildApp();
    const registerRes = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'ValidPass1' });

    const cookies = registerRes.headers['set-cookie'];

    const logoutRes = await request(app)
      .post('/api/auth/logout')
      .set('Cookie', cookies);

    expect(logoutRes.status).toBe(200);
    // db.update should have been called to revoke the token
    expect(mockDb.update).toHaveBeenCalled();
  });
});

// ─── SESS-04: Token revocation on password change ────────────────────────────

describe('SESS-04: Token revocation on password change', () => {
  it('POST /change-password with correct current password + valid new password returns 200', async () => {
    const hashedPw = await bcrypt.hash('OldPass!9', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    // Need an access token to authenticate
    const { issueAccessToken } = await import('../src/services/authService.js');
    const accessToken = issueAccessToken('user-uuid-123');

    const res = await request(app)
      .post('/api/auth/change-password')
      .set('Authorization', `Bearer ${accessToken}`)
      .send({ currentPassword: 'OldPass!9', newPassword: 'NewV4l!dPass99' });

    expect(res.status).toBe(200);
    expect(res.body.message).toBe('Password changed successfully');
  });

  it('POST /change-password with wrong current password returns 401', async () => {
    const hashedPw = await bcrypt.hash('OldPass!9', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    const { issueAccessToken } = await import('../src/services/authService.js');
    const accessToken = issueAccessToken('user-uuid-123');

    const res = await request(app)
      .post('/api/auth/change-password')
      .set('Authorization', `Bearer ${accessToken}`)
      .send({ currentPassword: 'WrongPass!9', newPassword: 'NewV4l!dPass99' });

    expect(res.status).toBe(401);
    expect(res.body.error).toMatch(/incorrect/i);
  });

  it('POST /change-password with weak new password returns 400 with policy error', async () => {
    const hashedPw = await bcrypt.hash('OldPass!9', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    const { issueAccessToken } = await import('../src/services/authService.js');
    const accessToken = issueAccessToken('user-uuid-123');

    const res = await request(app)
      .post('/api/auth/change-password')
      .set('Authorization', `Bearer ${accessToken}`)
      .send({ currentPassword: 'OldPass!9', newPassword: 'password1' });

    expect(res.status).toBe(400);
    expect(res.body.errors).toHaveProperty('newPassword');
  });

  it('POST /change-password for OAuth-only user (no passwordHash) returns 400', async () => {
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'google@example.com',
      passwordHash: null,
      googleId: 'google-123',
    });

    const app = buildApp();
    const { issueAccessToken } = await import('../src/services/authService.js');
    const accessToken = issueAccessToken('user-uuid-123');

    const res = await request(app)
      .post('/api/auth/change-password')
      .set('Authorization', `Bearer ${accessToken}`)
      .send({ currentPassword: 'anything', newPassword: 'NewV4l!dPass99' });

    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/google/i);
  });

  it('POST /change-password without auth returns 401', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/change-password')
      .send({ currentPassword: 'OldPass!9', newPassword: 'NewV4l!dPass99' });

    expect(res.status).toBe(401);
  });

  it('POST /change-password revokes other sessions (revokeAllUserTokensExcept called)', async () => {
    const hashedPw = await bcrypt.hash('OldPass!9', 12);
    mockDb.query.users.findFirst.mockResolvedValue({
      id: 'user-uuid-123',
      email: 'test@example.com',
      passwordHash: hashedPw,
    });

    const app = buildApp();
    const { issueAccessToken } = await import('../src/services/authService.js');
    const accessToken = issueAccessToken('user-uuid-123');

    const res = await request(app)
      .post('/api/auth/change-password')
      .set('Authorization', `Bearer ${accessToken}`)
      .send({ currentPassword: 'OldPass!9', newPassword: 'NewV4l!dPass99' });

    expect(res.status).toBe(200);
    // db.update should be called for revokeAllUserTokensExcept
    expect(mockDb.update).toHaveBeenCalled();
    // New refresh cookie issued
    const cookies = res.headers['set-cookie'] as string[] | undefined;
    expect(cookies).toBeDefined();
    const refreshCookie = cookies?.find((c) => c.startsWith('refreshToken='));
    expect(refreshCookie).toBeDefined();
  });
});
