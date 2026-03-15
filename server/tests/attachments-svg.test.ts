import { describe, it, expect, vi, beforeEach } from 'vitest';
import express from 'express';
import supertest from 'supertest';

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../src/middleware/auth.js', () => ({
  requireAuth: (req: any, _res: any, next: any) => {
    req.user = { id: 'user-uuid-1' };
    next();
  },
}));

vi.mock('../src/services/attachmentService.js', () => ({
  createAttachment: vi.fn(),
  getAttachmentsByBullet: vi.fn(),
  getAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
}));

// Mock sendFile so it doesn't try to read from disk
vi.mock('express', async (importOriginal) => {
  const actual = await importOriginal<typeof import('express')>();
  return actual;
});

import { getAttachment } from '../src/services/attachmentService.js';
import { attachmentsRouter } from '../src/routes/attachments.js';

// Build a minimal Express app for testing
function makeApp() {
  const app = express();
  // Patch res.sendFile to avoid actual file reading
  app.use((_req, res, next) => {
    (res as any).sendFile = (_path: string, _opts: any, cb?: (err?: any) => void) => {
      res.end('file-contents');
      if (cb) cb();
    };
    next();
  });
  app.use('/api/attachments', attachmentsRouter);
  return app;
}

// ─── SVG Content-Disposition tests ───────────────────────────────────────────

describe('GET /:id/file — Content-Disposition', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns Content-Disposition: attachment for SVG files', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-1',
      userId: 'user-uuid-1',
      filename: 'image.svg',
      mimeType: 'image/svg+xml',
      storagePath: '/data/attachments/some-uuid.svg',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-1/file');

    expect(res.status).toBe(200);
    expect(res.headers['content-disposition']).toMatch(/^attachment/);
  });

  it('returns Content-Disposition: inline for PNG files', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-2',
      userId: 'user-uuid-1',
      filename: 'photo.png',
      mimeType: 'image/png',
      storagePath: '/data/attachments/some-uuid.png',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-2/file');

    expect(res.status).toBe(200);
    expect(res.headers['content-disposition']).toMatch(/^inline/);
  });

  it('returns Content-Disposition: inline for JPEG files', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-3',
      userId: 'user-uuid-1',
      filename: 'photo.jpg',
      mimeType: 'image/jpeg',
      storagePath: '/data/attachments/some-uuid.jpg',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-3/file');

    expect(res.status).toBe(200);
    expect(res.headers['content-disposition']).toMatch(/^inline/);
  });

  it('returns Content-Disposition: inline for PDF files', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-4',
      userId: 'user-uuid-1',
      filename: 'document.pdf',
      mimeType: 'application/pdf',
      storagePath: '/data/attachments/some-uuid.pdf',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-4/file');

    expect(res.status).toBe(200);
    expect(res.headers['content-disposition']).toMatch(/^inline/);
  });
});

// ─── Filename sanitization tests ──────────────────────────────────────────────

describe('GET /:id/file — filename sanitization in Content-Disposition', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('replaces double quotes in filename with single quotes', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-5',
      userId: 'user-uuid-1',
      filename: 'my"file".png',
      mimeType: 'image/png',
      storagePath: '/data/attachments/some-uuid.png',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-5/file');

    expect(res.status).toBe(200);
    const disposition = res.headers['content-disposition'];
    expect(disposition).not.toContain('"my"file"');
    // Should have replaced inner quotes
    expect(disposition).toContain("my'file'.png");
  });

  it('strips newline characters from filename', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-6',
      userId: 'user-uuid-1',
      filename: 'evil\nfile.png',
      mimeType: 'image/png',
      storagePath: '/data/attachments/some-uuid.png',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-6/file');

    expect(res.status).toBe(200);
    const disposition = res.headers['content-disposition'];
    expect(disposition).not.toContain('\n');
    expect(disposition).toContain('evilfile.png');
  });

  it('strips carriage return characters from filename', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-7',
      userId: 'user-uuid-1',
      filename: 'evil\rfile.png',
      mimeType: 'image/png',
      storagePath: '/data/attachments/some-uuid.png',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-7/file');

    expect(res.status).toBe(200);
    const disposition = res.headers['content-disposition'];
    expect(disposition).not.toContain('\r');
    expect(disposition).toContain('evilfile.png');
  });

  it('strips control characters (0x00-0x1F) from filename', async () => {
    (getAttachment as any).mockResolvedValue({
      id: 'att-8',
      userId: 'user-uuid-1',
      filename: 'evil\x01\x1Ffile.png',
      mimeType: 'image/png',
      storagePath: '/data/attachments/some-uuid.png',
    });

    const app = makeApp();
    const res = await supertest(app).get('/api/attachments/att-8/file');

    expect(res.status).toBe(200);
    const disposition = res.headers['content-disposition'];
    expect(disposition).toContain('evilfile.png');
  });
});
