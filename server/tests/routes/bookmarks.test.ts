import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';
import cookieParser from 'cookie-parser';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

const USER_ID = 'c3d4e5f6-a7b8-9012-cdef-123456789012';
const BULLET_ID = 'b2c3d4e5-f6a7-8901-bcde-f12345678901';
const DOC_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

vi.mock('../../src/middleware/auth.js', () => ({
  requireAuth: vi.fn((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID };
    next();
  }),
}));

vi.mock('../../src/services/bookmarkService.js', () => ({
  addBookmark: vi.fn().mockResolvedValue({
    id: 'bm-uuid-1',
    userId: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
    bulletId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    createdAt: new Date(),
  }),
  removeBookmark: vi.fn().mockResolvedValue(undefined),
  getUserBookmarks: vi.fn().mockResolvedValue([
    { id: 'b2c3d4e5-f6a7-8901-bcde-f12345678901', content: 'bookmarked bullet', documentId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', documentTitle: 'Inbox' },
  ]),
}));

import { bookmarksRouter } from '../../src/routes/bookmarks.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/bookmarks', bookmarksRouter);
  return app;
}

describe('POST /api/bookmarks', () => {
  it('creates a bookmark and returns 201', async () => {
    const res = await request(buildApp())
      .post('/api/bookmarks')
      .send({ bulletId: BULLET_ID });
    expect(res.status).toBe(201);
    expect(res.body.bulletId).toBe(BULLET_ID);
  });
});

describe('DELETE /api/bookmarks/:bulletId', () => {
  it('removes a bookmark and returns 204', async () => {
    const res = await request(buildApp())
      .delete(`/api/bookmarks/${BULLET_ID}`);
    expect(res.status).toBe(204);
  });
});

describe('GET /api/bookmarks', () => {
  it('returns user bookmarks with doc titles', async () => {
    const res = await request(buildApp()).get('/api/bookmarks');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body[0]).toHaveProperty('documentTitle');
  });
});
