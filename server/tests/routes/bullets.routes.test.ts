import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';
import cookieParser from 'cookie-parser';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

const DOC_ID = 'doc-uuid-111';
const BULLET_ID = 'bullet-uuid-222';
const USER_ID = 'user-uuid-123';

// Mock requireAuth middleware to inject a test user (bypasses passport-jwt)
vi.mock('../../src/middleware/auth.js', () => ({
  requireAuth: vi.fn((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID };
    next();
  }),
}));

// Mock bulletService (not yet implemented — tests will fail at import)
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

// Mock undoService (not yet implemented — tests will fail at import)
vi.mock('../../src/services/undoService.js', () => ({
  undo: vi.fn(),
  redo: vi.fn(),
  getStatus: vi.fn(),
}));

// Note: bulletsRouter and undoRouter do not exist yet — import will fail → RED state
import { bulletsRouter } from '../../src/routes/bullets.js';
import { undoRouter } from '../../src/routes/undo.js';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/bullets', bulletsRouter);
  app.use('/api', undoRouter);
  return app;
}

describe('GET /api/bullets/documents/:docId/bullets', () => {
  it('returns 200 with bullet array for authenticated user', () => {
    throw new Error('not yet implemented');
  });

  it('returns 401 when not authenticated', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/bullets', () => {
  it('returns 201 with created bullet', () => {
    throw new Error('not yet implemented');
  });

  it('returns 400 for missing documentId', () => {
    throw new Error('not yet implemented');
  });

  it('returns 401 when not authenticated', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/bullets/:id/indent', () => {
  it('returns 200 with indented bullet', () => {
    throw new Error('not yet implemented');
  });

  it('returns 401 when not authenticated', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/bullets/:id/outdent', () => {
  it('returns 200 with outdented bullet', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/bullets/:id/move', () => {
  it('returns 200 on valid move', () => {
    throw new Error('not yet implemented');
  });

  it('returns 400 when cycle guard triggers', () => {
    throw new Error('not yet implemented');
  });
});

describe('PATCH /api/bullets/:id', () => {
  it('returns 200 when patching content', () => {
    throw new Error('not yet implemented');
  });

  it('returns 200 when patching isComplete', () => {
    throw new Error('not yet implemented');
  });

  it('returns 200 when patching isCollapsed', () => {
    throw new Error('not yet implemented');
  });
});

describe('DELETE /api/bullets/:id', () => {
  it('returns 200 on soft delete', () => {
    throw new Error('not yet implemented');
  });

  it('returns 401 when not authenticated', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/undo', () => {
  it('returns 200 with { canUndo, canRedo, affectedBullets }', () => {
    throw new Error('not yet implemented');
  });

  it('returns 401 when not authenticated', () => {
    throw new Error('not yet implemented');
  });
});

describe('POST /api/redo', () => {
  it('returns 200 with { canUndo, canRedo, affectedBullets }', () => {
    throw new Error('not yet implemented');
  });
});

describe('GET /api/undo/status', () => {
  it('returns 200 with { canUndo, canRedo }', () => {
    throw new Error('not yet implemented');
  });
});
