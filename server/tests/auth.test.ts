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
    },
    insert: vi.fn(),
    select: vi.fn(),
    update: vi.fn(),
  },
}));

import { db } from '../db/index.js';
import { authRouter } from '../src/routes/auth.js';

const mockDb = db as {
  query: { users: { findFirst: ReturnType<typeof vi.fn> } };
  insert: ReturnType<typeof vi.fn>;
  select: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
};

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
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
});

// ─── AUTH-01: Register ────────────────────────────────────────────────────────

describe('AUTH-01: Register with email/password', () => {
  it('POST /api/auth/register with valid email+password returns 201 + accessToken', async () => {
    mockDb.query.users.findFirst.mockResolvedValue(null); // no duplicate

    const app = buildApp();
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'password123' });

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
      .send({ email: 'test@example.com', password: 'password123' });

    expect(res.status).toBe(409);
    expect(res.body).toMatchObject({ field: 'email', message: 'Email already registered' });
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
      .send({ email: 'test@example.com', password: 'password123' });

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
      .send({ email: 'test@example.com', password: 'password123' });

    // insert called twice: once for user, once for Inbox document
    expect(mockDb.insert).toHaveBeenCalledTimes(2);
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
