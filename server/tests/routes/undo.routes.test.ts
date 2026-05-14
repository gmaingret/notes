import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

const USER_ID = 'c3d4e5f6-a7b8-9012-cdef-123456789012';

// Mock requireAuth to inject a test user (bypasses passport-jwt)
vi.mock('../../src/middleware/auth.js', () => ({
  requireAuth: vi.fn((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID };
    next();
  }),
}));

// Mock db (used inside undoRouter via undoService — services themselves are stubbed below)
vi.mock('../../db/index.js', () => ({
  db: {},
}));

// Mock undoService so we control canUndo/canRedo + undo/redo outcomes
vi.mock('../../src/services/undoService.js', () => ({
  undo: vi.fn(),
  redo: vi.fn(),
  getStatus: vi.fn(),
}));

import { undoRouter } from '../../src/routes/undo.js';
import { undo, redo, getStatus } from '../../src/services/undoService.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api', undoRouter);
  return app;
}

describe('undoRouter — ERR-02: 422 on empty stack', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('POST /api/undo returns 422 { error: "Nothing to undo" } when canUndo=false', async () => {
    (getStatus as any).mockResolvedValue({ canUndo: false, canRedo: false });

    const res = await request(buildApp()).post('/api/undo');

    expect(res.status).toBe(422);
    expect(res.body).toEqual({ error: 'Nothing to undo' });
    expect(undo).not.toHaveBeenCalled();
  });

  it('POST /api/undo invokes undo() when canUndo=true', async () => {
    (getStatus as any).mockResolvedValue({ canUndo: true, canRedo: false });
    (undo as any).mockResolvedValue({ canUndo: false, canRedo: true });

    const res = await request(buildApp()).post('/api/undo');

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ canUndo: false, canRedo: true });
    expect(undo).toHaveBeenCalledWith({}, USER_ID);
  });

  it('POST /api/redo returns 422 { error: "Nothing to redo" } when canRedo=false', async () => {
    (getStatus as any).mockResolvedValue({ canUndo: true, canRedo: false });

    const res = await request(buildApp()).post('/api/redo');

    expect(res.status).toBe(422);
    expect(res.body).toEqual({ error: 'Nothing to redo' });
    expect(redo).not.toHaveBeenCalled();
  });

  it('POST /api/redo invokes redo() when canRedo=true', async () => {
    (getStatus as any).mockResolvedValue({ canUndo: true, canRedo: true });
    (redo as any).mockResolvedValue({ canUndo: true, canRedo: false });

    const res = await request(buildApp()).post('/api/redo');

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ canUndo: true, canRedo: false });
    expect(redo).toHaveBeenCalledWith({}, USER_ID);
  });

  it('GET /api/undo/status returns service result', async () => {
    (getStatus as any).mockResolvedValue({ canUndo: true, canRedo: true });

    const res = await request(buildApp()).get('/api/undo/status');

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ canUndo: true, canRedo: true });
  });
});

describe('global error handler — ERR-01: { error: "Internal server error" }', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns JSON 500 when a route throws an unhandled error', async () => {
    (getStatus as any).mockRejectedValue(new Error('boom — DB exploded'));

    const app = express();
    app.use(express.json());
    app.use('/api', undoRouter);
    // Mirror the production handler from server/src/index.ts
    app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
      res.status(500).json({ error: 'Internal server error' });
    });

    const res = await request(app).post('/api/undo');

    expect(res.status).toBe(500);
    expect(res.body).toEqual({ error: 'Internal server error' });
    expect(res.headers['content-type']).toMatch(/application\/json/);
  });
});
