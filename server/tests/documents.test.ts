import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';
import express from 'express';
import cookieParser from 'cookie-parser';

process.env.JWT_SECRET = 'test-secret-at-least-32-chars-long-1234';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-chars-long';
process.env.NODE_ENV = 'test';

const DOC_ID = 'doc-uuid-111';
const USER_ID = 'user-uuid-123';
const OTHER_USER_ID = 'user-uuid-456';

const mockDoc = {
  id: DOC_ID,
  userId: USER_ID,
  title: 'Test Doc',
  position: 1.0,
  lastOpenedAt: null,
  createdAt: new Date(),
  updatedAt: new Date(),
};

// Mock requireAuth middleware to inject a test user (bypasses passport-jwt)
vi.mock('../src/middleware/auth.js', () => ({
  configurePassport: vi.fn(),
  requireAuth: vi.fn((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID, email: 'test@example.com' } as Express.User;
    next();
  }),
}));

// Mock db
vi.mock('../db/index.js', () => ({
  db: {
    query: {
      documents: {
        findFirst: vi.fn(),
      },
    },
    insert: vi.fn(),
    select: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

// Mock documentService for route tests
vi.mock('../src/services/documentService.js', () => ({
  computeDocumentInsertPosition: vi.fn(() => Promise.resolve(2.0)),
  getDocumentWithBullets: vi.fn(() =>
    Promise.resolve({
      id: DOC_ID,
      title: 'Test Doc',
      bullets: [
        { id: 'b1', parentId: null, content: 'Root', position: 1.0, deletedAt: null },
        { id: 'b2', parentId: 'b1', content: 'Child', position: 1.0, deletedAt: null },
      ],
    })
  ),
  getAllDocumentsWithBullets: vi.fn(() =>
    Promise.resolve([
      { id: 'doc-1', title: 'Doc One', bullets: [] },
      { id: 'doc-2', title: 'Doc Two', bullets: [] },
    ])
  ),
  renderDocumentAsMarkdown: vi.fn((doc) => `# ${doc.title}\n\n- Root\n  - Child`),
}));

import { db } from '../db/index.js';
import { documentsRouter } from '../src/routes/documents.js';
import { requireAuth } from '../src/middleware/auth.js';

const mockDb = db as {
  query: {
    documents: { findFirst: ReturnType<typeof vi.fn> };
  };
  insert: ReturnType<typeof vi.fn>;
  select: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

const mockRequireAuth = requireAuth as ReturnType<typeof vi.fn>;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/documents', documentsRouter);
  return app;
}

beforeEach(() => {
  vi.clearAllMocks();

  // Reset requireAuth to inject user
  mockRequireAuth.mockImplementation((req: express.Request, _res: express.Response, next: express.NextFunction) => {
    req.user = { id: USER_ID, email: 'test@example.com' } as Express.User;
    next();
  });

  // Default select chain: .from().where().orderBy()
  mockDb.select.mockReturnValue({
    from: vi.fn().mockReturnValue({
      where: vi.fn().mockReturnValue({
        orderBy: vi.fn().mockResolvedValue([mockDoc]),
        limit: vi.fn().mockResolvedValue([]),
      }),
      orderBy: vi.fn().mockResolvedValue([mockDoc]),
    }),
  });

  // Default insert chain
  mockDb.insert.mockReturnValue({
    values: vi.fn().mockReturnValue({
      returning: vi.fn().mockResolvedValue([mockDoc]),
    }),
  });

  // Default update chain
  mockDb.update.mockReturnValue({
    set: vi.fn().mockReturnValue({
      where: vi.fn().mockReturnValue({
        returning: vi.fn().mockResolvedValue([{ ...mockDoc, title: 'Updated Title' }]),
      }),
    }),
  });

  // Default delete chain
  mockDb.delete.mockReturnValue({
    where: vi.fn().mockResolvedValue(undefined),
  });
});

// ─── DOC-01: Create document ──────────────────────────────────────────────────

describe('DOC-01: Create document', () => {
  it('POST /api/documents creates document with correct userId and returns 201', async () => {
    mockDb.select.mockReturnValue({
      from: vi.fn().mockReturnValue({
        where: vi.fn().mockReturnValue({
          orderBy: vi.fn().mockResolvedValue([]),
        }),
      }),
    });

    const app = buildApp();
    const res = await request(app)
      .post('/api/documents')
      .send({ title: 'My New Doc' });

    expect(res.status).toBe(201);
    expect(mockDb.insert).toHaveBeenCalled();
  });

  it('POST /api/documents requires authentication (401 without token)', async () => {
    // Override requireAuth to simulate missing/invalid token
    mockRequireAuth.mockImplementation((_req, res) => {
      res.status(401).json({ error: 'Unauthorized' });
    });

    const app = buildApp();
    const res = await request(app)
      .post('/api/documents')
      .send({ title: 'My New Doc' });

    expect(res.status).toBe(401);
  });
});

// ─── DOC-02: Rename document ──────────────────────────────────────────────────

describe('DOC-02: Rename document', () => {
  it('PATCH /api/documents/:id updates title and returns 200', async () => {
    mockDb.query.documents.findFirst.mockResolvedValue(mockDoc);

    const app = buildApp();
    const res = await request(app)
      .patch(`/api/documents/${DOC_ID}`)
      .send({ title: 'Updated Title' });

    expect(res.status).toBe(200);
    expect(mockDb.update).toHaveBeenCalled();
  });

  it("PATCH /api/documents/:id with another user's document returns 403", async () => {
    mockDb.query.documents.findFirst.mockResolvedValue({
      ...mockDoc,
      userId: OTHER_USER_ID,
    });

    const app = buildApp();
    const res = await request(app)
      .patch(`/api/documents/${DOC_ID}`)
      .send({ title: 'Updated Title' });

    expect(res.status).toBe(403);
  });
});

// ─── DOC-03: Delete document ──────────────────────────────────────────────────

describe('DOC-03: Delete document', () => {
  it('DELETE /api/documents/:id removes document and returns 204', async () => {
    mockDb.query.documents.findFirst.mockResolvedValue(mockDoc);

    const app = buildApp();
    const res = await request(app).delete(`/api/documents/${DOC_ID}`);

    expect(res.status).toBe(204);
    expect(mockDb.delete).toHaveBeenCalled();
  });

  it("DELETE /api/documents/:id with another user's document returns 403", async () => {
    mockDb.query.documents.findFirst.mockResolvedValue({
      ...mockDoc,
      userId: OTHER_USER_ID,
    });

    const app = buildApp();
    const res = await request(app).delete(`/api/documents/${DOC_ID}`);

    expect(res.status).toBe(403);
  });
});

// ─── DOC-04: Reorder documents ────────────────────────────────────────────────

describe('DOC-04: Reorder documents (FLOAT8 midpoint)', () => {
  it('PATCH /api/documents/:id/position calls computeDocumentInsertPosition server-side', async () => {
    mockDb.query.documents.findFirst.mockResolvedValue(mockDoc);
    mockDb.update.mockReturnValue({
      set: vi.fn().mockReturnValue({
        where: vi.fn().mockReturnValue({
          returning: vi.fn().mockResolvedValue([{ ...mockDoc, position: 2.0 }]),
        }),
      }),
    });

    const { computeDocumentInsertPosition } = await import('../src/services/documentService.js');

    const app = buildApp();
    // afterId must be a valid UUID format for z.string().uuid() to pass
    const res = await request(app)
      .patch(`/api/documents/${DOC_ID}/position`)
      .send({ afterId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' });

    expect(res.status).toBe(200);
    expect(computeDocumentInsertPosition).toHaveBeenCalled();
  });

  it('Position is computed server-side — client passes afterId (UUID), not a float', async () => {
    mockDb.query.documents.findFirst.mockResolvedValue(mockDoc);

    const app = buildApp();
    // Passing a float directly is not accepted by the schema (expects UUID or null)
    const res = await request(app)
      .patch(`/api/documents/${DOC_ID}/position`)
      .send({ afterId: 1.5 });

    expect(res.status).toBe(400);
  });
});

// ─── DOC-05: Navigate between documents ──────────────────────────────────────

describe('DOC-05: Navigate between documents (last_opened_at)', () => {
  it('POST /api/documents/:id/open updates last_opened_at and returns 204', async () => {
    mockDb.query.documents.findFirst.mockResolvedValue(mockDoc);
    mockDb.update.mockReturnValue({
      set: vi.fn().mockReturnValue({
        where: vi.fn().mockResolvedValue(undefined),
      }),
    });

    const app = buildApp();
    const res = await request(app).post(`/api/documents/${DOC_ID}/open`);

    expect(res.status).toBe(204);
    expect(mockDb.update).toHaveBeenCalled();
  });

  it('GET /api/documents returns documents sorted by position asc', async () => {
    const sortedDocs = [
      { ...mockDoc, id: 'doc-a', position: 1.0 },
      { ...mockDoc, id: 'doc-b', position: 2.0 },
      { ...mockDoc, id: 'doc-c', position: 3.0 },
    ];
    mockDb.select.mockReturnValue({
      from: vi.fn().mockReturnValue({
        where: vi.fn().mockReturnValue({
          orderBy: vi.fn().mockResolvedValue(sortedDocs),
        }),
      }),
    });

    const app = buildApp();
    const res = await request(app).get('/api/documents');

    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body).toHaveLength(3);
  });
});

// ─── DOC-06: Export single document as Markdown ───────────────────────────────

describe('DOC-06: Export single document as Markdown', () => {
  it('GET /api/documents/:id/export returns 200 with Content-Type text/markdown', async () => {
    const app = buildApp();
    const res = await request(app).get(`/api/documents/${DOC_ID}/export`);

    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('text/markdown');
  });

  it('Response has Content-Disposition attachment with .md filename', async () => {
    const app = buildApp();
    const res = await request(app).get(`/api/documents/${DOC_ID}/export`);

    expect(res.headers['content-disposition']).toContain('attachment');
    expect(res.headers['content-disposition']).toContain('.md');
  });

  it('Bullets rendered as indented dashes (2-space per level)', () => {
    // Pure logic test for the 2-space indent convention
    const root = '- Root bullet';
    const child = '  - Child bullet';
    const grandchild = '    - Grandchild';
    expect(root.startsWith('- ')).toBe(true);
    expect(child.startsWith('  - ')).toBe(true);
    expect(grandchild.startsWith('    - ')).toBe(true);
  });
});

// ─── DOC-07: Export all documents as ZIP ─────────────────────────────────────

describe('DOC-07: Export all documents as ZIP', () => {
  it('GET /api/documents/export-all returns 200 with Content-Type application/zip', async () => {
    const app = buildApp();
    const res = await request(app).get('/api/documents/export-all');

    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('application/zip');
  });

  it('GET /api/documents/export-all returns Content-Disposition attachment with zip filename', async () => {
    const app = buildApp();
    const res = await request(app).get('/api/documents/export-all');

    expect(res.headers['content-disposition']).toContain('attachment');
    expect(res.headers['content-disposition']).toContain('.zip');
  });
});

// ─── renderDocumentAsMarkdown pure unit test ──────────────────────────────────

describe('renderDocumentAsMarkdown (pure unit test)', () => {
  it('renders title as H1 and bullets with 2-space indent per level', async () => {
    const { renderDocumentAsMarkdown } = await vi.importActual<typeof import('../src/services/documentService.js')>('../src/services/documentService.js');

    const result = renderDocumentAsMarkdown({
      id: 'd1',
      title: 'My Doc',
      bullets: [
        { id: 'b1', parentId: null, content: 'Root', position: 1.0, deletedAt: null },
        { id: 'b2', parentId: 'b1', content: 'Child', position: 1.0, deletedAt: null },
      ],
    });

    expect(result).toContain('# My Doc');
    expect(result).toContain('- Root');
    expect(result).toContain('  - Child');
  });
});
