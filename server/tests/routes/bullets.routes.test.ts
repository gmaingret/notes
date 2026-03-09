import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';
import cookieParser from 'cookie-parser';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

const DOC_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const BULLET_ID = 'b2c3d4e5-f6a7-8901-bcde-f12345678901';
const USER_ID = 'c3d4e5f6-a7b8-9012-cdef-123456789012';

const mockBullet = {
  id: BULLET_ID,
  documentId: DOC_ID,
  userId: USER_ID,
  parentId: null,
  content: 'Test bullet',
  position: 1.0,
  isComplete: false,
  isCollapsed: false,
  deletedAt: null,
  createdAt: new Date(),
  updatedAt: new Date(),
};

const mockUndoStatus = { canUndo: true, canRedo: false };

// Mock requireAuth middleware to inject a test user (bypasses passport-jwt)
vi.mock('../../src/middleware/auth.js', () => ({
  requireAuth: vi.fn((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID };
    next();
  }),
}));

// Mock db (used directly in bullets.ts for content PATCH and DELETE completed)
vi.mock('../../db/index.js', () => ({
  db: {
    update: vi.fn().mockReturnThis(),
    delete: vi.fn().mockReturnThis(),
    set: vi.fn().mockReturnThis(),
    where: vi.fn().mockReturnThis(),
    returning: vi.fn(),
  },
}));

// Mock bulletService
vi.mock('../../src/services/bulletService.js', () => ({
  getDocumentBullets: vi.fn(),
  createBullet: vi.fn(),
  indentBullet: vi.fn(),
  outdentBullet: vi.fn(),
  moveBullet: vi.fn(),
  softDeleteBullet: vi.fn(),
  markComplete: vi.fn(),
  setCollapsed: vi.fn(),
}));

// Mock undoService (includes recordUndoEvent used by bullets.ts)
vi.mock('../../src/services/undoService.js', () => ({
  undo: vi.fn(),
  redo: vi.fn(),
  getStatus: vi.fn(),
  recordUndoEvent: vi.fn(),
}));

import { bulletsRouter } from '../../src/routes/bullets.js';
import { undoRouter } from '../../src/routes/undo.js';
import * as bulletService from '../../src/services/bulletService.js';
import * as undoService from '../../src/services/undoService.js';
import { db } from '../../db/index.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/bullets', bulletsRouter);
  app.use('/api', undoRouter);
  return app;
}

// Helper to build an app with requireAuth returning 401
function buildUnauthApp() {
  const { requireAuth } = require('../../src/middleware/auth.js');
  const savedImpl = requireAuth.getMockImplementation();
  requireAuth.mockImplementation((_req: express.Request, res: express.Response) => {
    res.status(401).json({ error: 'Unauthorized' });
  });
  const app = buildApp();
  // Restore after building
  requireAuth.mockImplementation(savedImpl);
  return app;
}

beforeEach(() => {
  vi.clearAllMocks();
  // Reset db mock chain
  const dbMock = db as any;
  dbMock.update = vi.fn().mockReturnValue({
    set: vi.fn().mockReturnValue({
      where: vi.fn().mockReturnValue({
        returning: vi.fn().mockResolvedValue([mockBullet]),
      }),
    }),
  });
  dbMock.delete = vi.fn().mockReturnValue({
    where: vi.fn().mockResolvedValue([]),
  });
});

describe('GET /api/bullets/documents/:docId/bullets', () => {
  it('returns 200 with bullet array for authenticated user', async () => {
    vi.mocked(bulletService.getDocumentBullets).mockResolvedValue([mockBullet]);
    const app = buildApp();
    const res = await request(app).get(`/api/bullets/documents/${DOC_ID}/bullets`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body[0].id).toBe(BULLET_ID);
  });

  it('returns 401 when not authenticated', async () => {
    const { requireAuth } = await import('../../src/middleware/auth.js');
    vi.mocked(requireAuth).mockImplementationOnce((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });
    const app = buildApp();
    const res = await request(app).get(`/api/bullets/documents/${DOC_ID}/bullets`);
    expect(res.status).toBe(401);
  });
});

describe('POST /api/bullets', () => {
  it('returns 201 with created bullet', async () => {
    vi.mocked(bulletService.createBullet).mockResolvedValue(mockBullet);
    const app = buildApp();
    const res = await request(app)
      .post('/api/bullets')
      .send({ documentId: DOC_ID, content: 'New bullet' });
    expect(res.status).toBe(201);
    expect(res.body.id).toBe(BULLET_ID);
  });

  it('returns 400 for missing documentId', async () => {
    const app = buildApp();
    const res = await request(app)
      .post('/api/bullets')
      .send({ content: 'No doc id' });
    expect(res.status).toBe(400);
  });

  it('returns 401 when not authenticated', async () => {
    const { requireAuth } = await import('../../src/middleware/auth.js');
    vi.mocked(requireAuth).mockImplementationOnce((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });
    const app = buildApp();
    const res = await request(app)
      .post('/api/bullets')
      .send({ documentId: DOC_ID });
    expect(res.status).toBe(401);
  });
});

describe('POST /api/bullets/:id/indent', () => {
  it('returns 200 with indented bullet', async () => {
    vi.mocked(bulletService.indentBullet).mockResolvedValue(mockBullet);
    const app = buildApp();
    const res = await request(app).post(`/api/bullets/${BULLET_ID}/indent`);
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(BULLET_ID);
  });

  it('returns 401 when not authenticated', async () => {
    const { requireAuth } = await import('../../src/middleware/auth.js');
    vi.mocked(requireAuth).mockImplementationOnce((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });
    const app = buildApp();
    const res = await request(app).post(`/api/bullets/${BULLET_ID}/indent`);
    expect(res.status).toBe(401);
  });
});

describe('POST /api/bullets/:id/outdent', () => {
  it('returns 200 with outdented bullet', async () => {
    vi.mocked(bulletService.outdentBullet).mockResolvedValue(mockBullet);
    const app = buildApp();
    const res = await request(app).post(`/api/bullets/${BULLET_ID}/outdent`);
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(BULLET_ID);
  });
});

describe('POST /api/bullets/:id/move', () => {
  it('returns 200 on valid move', async () => {
    vi.mocked(bulletService.moveBullet).mockResolvedValue(mockBullet);
    const app = buildApp();
    const res = await request(app)
      .post(`/api/bullets/${BULLET_ID}/move`)
      .send({ newParentId: null, afterId: null });
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(BULLET_ID);
  });

  it('returns 400 when cycle guard triggers', async () => {
    vi.mocked(bulletService.moveBullet).mockRejectedValue(
      new Error('Cannot move a bullet under one of its own descendants')
    );
    const app = buildApp();
    const res = await request(app)
      .post(`/api/bullets/${BULLET_ID}/move`)
      .send({ newParentId: DOC_ID, afterId: null });
    expect(res.status).toBe(400);
    expect(res.body.error).toContain('descendant');
  });
});

describe('PATCH /api/bullets/:id', () => {
  it('returns 200 when patching content', async () => {
    const dbMock = db as any;
    dbMock.update = vi.fn().mockReturnValue({
      set: vi.fn().mockReturnValue({
        where: vi.fn().mockReturnValue({
          returning: vi.fn().mockResolvedValue([{ ...mockBullet, content: 'Updated' }]),
        }),
      }),
    });
    const app = buildApp();
    const res = await request(app)
      .patch(`/api/bullets/${BULLET_ID}`)
      .send({ content: 'Updated' });
    expect(res.status).toBe(200);
  });

  it('returns 200 when patching isComplete', async () => {
    vi.mocked(bulletService.markComplete).mockResolvedValue({ ...mockBullet, isComplete: true });
    const app = buildApp();
    const res = await request(app)
      .patch(`/api/bullets/${BULLET_ID}`)
      .send({ isComplete: true });
    expect(res.status).toBe(200);
    expect(res.body.isComplete).toBe(true);
  });

  it('returns 200 when patching isCollapsed', async () => {
    vi.mocked(bulletService.setCollapsed).mockResolvedValue({ ...mockBullet, isCollapsed: true });
    const app = buildApp();
    const res = await request(app)
      .patch(`/api/bullets/${BULLET_ID}`)
      .send({ isCollapsed: true });
    expect(res.status).toBe(200);
    expect(res.body.isCollapsed).toBe(true);
  });
});

describe('DELETE /api/bullets/:id', () => {
  it('returns 200 on soft delete', async () => {
    vi.mocked(bulletService.softDeleteBullet).mockResolvedValue(mockBullet);
    const app = buildApp();
    const res = await request(app).delete(`/api/bullets/${BULLET_ID}`);
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
  });

  it('returns 401 when not authenticated', async () => {
    const { requireAuth } = await import('../../src/middleware/auth.js');
    vi.mocked(requireAuth).mockImplementationOnce((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });
    const app = buildApp();
    const res = await request(app).delete(`/api/bullets/${BULLET_ID}`);
    expect(res.status).toBe(401);
  });
});

describe('POST /api/undo', () => {
  it('returns 200 with { canUndo, canRedo, affectedBullets }', async () => {
    vi.mocked(undoService.undo).mockResolvedValue(mockUndoStatus);
    const app = buildApp();
    const res = await request(app).post('/api/undo');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('canUndo');
    expect(res.body).toHaveProperty('canRedo');
  });

  it('returns 401 when not authenticated', async () => {
    const { requireAuth } = await import('../../src/middleware/auth.js');
    vi.mocked(requireAuth).mockImplementationOnce((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });
    const app = buildApp();
    const res = await request(app).post('/api/undo');
    expect(res.status).toBe(401);
  });
});

describe('POST /api/redo', () => {
  it('returns 200 with { canUndo, canRedo, affectedBullets }', async () => {
    vi.mocked(undoService.redo).mockResolvedValue(mockUndoStatus);
    const app = buildApp();
    const res = await request(app).post('/api/redo');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('canUndo');
    expect(res.body).toHaveProperty('canRedo');
  });
});

describe('GET /api/undo/status', () => {
  it('returns 200 with { canUndo, canRedo }', async () => {
    vi.mocked(undoService.getStatus).mockResolvedValue(mockUndoStatus);
    const app = buildApp();
    const res = await request(app).get('/api/undo/status');
    expect(res.status).toBe(200);
    expect(res.body).toHaveProperty('canUndo');
    expect(res.body).toHaveProperty('canRedo');
  });
});
