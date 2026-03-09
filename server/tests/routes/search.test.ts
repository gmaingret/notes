import { describe, it, expect, vi } from 'vitest';
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

vi.mock('../../src/services/searchService.js', () => ({
  searchBullets: vi.fn().mockResolvedValue([
    { id: BULLET_ID, content: 'hello world', documentId: DOC_ID, documentTitle: 'Inbox' },
  ]),
}));

import { searchRouter } from '../../src/routes/search.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/search', searchRouter);
  return app;
}

describe('GET /api/search', () => {
  it('returns matching bullets for plain query', async () => {
    const res = await request(buildApp()).get('/api/search?q=hello');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
  });

  it('accepts #tag syntax in query param', async () => {
    const res = await request(buildApp()).get('/api/search?q=%23milk');
    expect(res.status).toBe(200);
  });

  it('returns 400 if q param missing', async () => {
    const res = await request(buildApp()).get('/api/search');
    expect(res.status).toBe(400);
  });
});
