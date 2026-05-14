import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.GOOGLE_CLIENT_ID = 'web-client-id.apps.googleusercontent.com';
process.env.GOOGLE_ANDROID_CLIENT_ID = 'android-client-id.apps.googleusercontent.com';
process.env.NODE_ENV = 'test';

// Capture what `audience` value the handler passes to verifyIdToken.
// Must be set up via vi.hoisted so it exists when the (hoisted) vi.mock
// factory runs.
const { verifyIdToken } = vi.hoisted(() => ({
  verifyIdToken: vi.fn(),
}));

vi.mock('google-auth-library', () => ({
  OAuth2Client: vi.fn().mockImplementation(() => ({
    verifyIdToken,
  })),
}));

vi.mock('../../db/index.js', () => ({
  db: {
    query: { users: { findFirst: vi.fn() } },
    insert: vi.fn(),
    update: vi.fn(),
  },
}));

import { authRouter } from '../../src/routes/auth.js';
import { db } from '../../db/index.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/auth', authRouter);
  return app;
}

describe('POST /api/auth/google/token — accepts both Web and Android audiences', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('passes BOTH client IDs to verifyIdToken as audience', async () => {
    verifyIdToken.mockResolvedValue({
      getPayload: () => ({ email: 'a@b.com', sub: 'google-uid-1' }),
    });
    (db.query.users.findFirst as any).mockResolvedValue({
      id: 'u-1',
      email: 'a@b.com',
      googleId: 'google-uid-1',
    });
    (db.update as any).mockReturnValue({
      set: vi.fn().mockReturnValue({ where: vi.fn().mockResolvedValue(undefined) }),
    });

    await request(buildApp()).post('/api/auth/google/token').send({ idToken: 'fake' });

    expect(verifyIdToken).toHaveBeenCalledTimes(1);
    const call = verifyIdToken.mock.calls[0][0];
    expect(call.audience).toEqual([
      'web-client-id.apps.googleusercontent.com',
      'android-client-id.apps.googleusercontent.com',
    ]);
  });

  it('falls back to only Web client ID when GOOGLE_ANDROID_CLIENT_ID is unset', async () => {
    const prev = process.env.GOOGLE_ANDROID_CLIENT_ID;
    delete process.env.GOOGLE_ANDROID_CLIENT_ID;
    try {
      vi.resetModules();
      const { authRouter: reloadedRouter } = await import('../../src/routes/auth.js');
      const app = express();
      app.use(express.json());
      app.use('/api/auth', reloadedRouter);

      verifyIdToken.mockResolvedValue({
        getPayload: () => ({ email: 'a@b.com', sub: 'google-uid-1' }),
      });
      (db.query.users.findFirst as any).mockResolvedValue({
        id: 'u-1',
        email: 'a@b.com',
        googleId: 'google-uid-1',
      });

      await request(app).post('/api/auth/google/token').send({ idToken: 'fake' });

      const call = verifyIdToken.mock.calls[verifyIdToken.mock.calls.length - 1][0];
      expect(call.audience).toEqual(['web-client-id.apps.googleusercontent.com']);
    } finally {
      if (prev !== undefined) process.env.GOOGLE_ANDROID_CLIENT_ID = prev;
    }
  });
});
