import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the DB so service functions don't need a real database
vi.mock('../db/index.js', () => ({
  db: {
    insert: vi.fn(),
    select: vi.fn(),
    delete: vi.fn(),
  },
}));

// Mock fs/promises to avoid real file operations
vi.mock('node:fs/promises', () => ({
  unlink: vi.fn().mockResolvedValue(undefined),
}));

import { db } from '../db/index.js';
import {
  createAttachment,
  getAttachmentsByBullet,
  getAttachment,
  deleteAttachment,
  verifyBulletOwnership,
} from '../src/services/attachmentService.js';

const mockAttachment = {
  id: 'att-uuid-1',
  userId: 'user-uuid-1',
  bulletId: 'bullet-uuid-1',
  filename: 'photo.jpg',
  mimeType: 'image/jpeg',
  size: 12345,
  storagePath: '/data/attachments/some-uuid.jpg',
  createdAt: new Date(),
};

describe('attachmentService.createAttachment', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('uploads attachment and returns record', async () => {
    const returning = vi.fn().mockResolvedValue([mockAttachment]);
    const values = vi.fn().mockReturnValue({ returning });
    (db.insert as any).mockReturnValue({ values });

    const result = await createAttachment('user-uuid-1', 'bullet-uuid-1', {
      filename: 'photo.jpg',
      mimeType: 'image/jpeg',
      size: 12345,
      storagePath: '/data/attachments/some-uuid.jpg',
    });

    expect(result).toMatchObject({
      id: 'att-uuid-1',
      filename: 'photo.jpg',
      mimeType: 'image/jpeg',
      size: 12345,
      bulletId: 'bullet-uuid-1',
    });
  });

  it('storagePath is under /data/attachments', async () => {
    const returning = vi.fn().mockResolvedValue([mockAttachment]);
    const values = vi.fn().mockReturnValue({ returning });
    (db.insert as any).mockReturnValue({ values });

    const result = await createAttachment('user-uuid-1', 'bullet-uuid-1', {
      filename: 'photo.jpg',
      mimeType: 'image/jpeg',
      size: 12345,
      storagePath: '/data/attachments/some-uuid.jpg',
    });

    expect(result.storagePath).toMatch(/^\/data\/attachments\//);
  });
});

describe('attachmentService.getAttachmentsByBullet', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists attachments by bullet', async () => {
    const where = vi.fn().mockResolvedValue([mockAttachment]);
    const from = vi.fn().mockReturnValue({ where });
    (db.select as any).mockReturnValue({ from });

    const result = await getAttachmentsByBullet('user-uuid-1', 'bullet-uuid-1');

    expect(Array.isArray(result)).toBe(true);
    expect(result.length).toBe(1);
    expect(result[0]).toMatchObject({ id: 'att-uuid-1', bulletId: 'bullet-uuid-1' });
  });
});

describe('attachmentService.deleteAttachment', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('deletes attachment and removes record', async () => {
    // getAttachment (used internally by deleteAttachment)
    const where1 = vi.fn().mockResolvedValue([mockAttachment]);
    const from1 = vi.fn().mockReturnValue({ where: where1 });

    // db.delete chain
    const where2 = vi.fn().mockResolvedValue(undefined);
    (db.delete as any).mockReturnValue({ where: where2 });
    (db.select as any).mockReturnValue({ from: from1 });

    const result = await deleteAttachment('user-uuid-1', 'att-uuid-1');

    expect(result).toEqual({ deleted: true });
  });
});

describe('attachmentService.verifyBulletOwnership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns true when bullet belongs to user', async () => {
    const where = vi.fn().mockResolvedValue([{ id: 'bullet-uuid-1' }]);
    const from = vi.fn().mockReturnValue({ where });
    (db.select as any).mockReturnValue({ from });

    const result = await verifyBulletOwnership('user-uuid-1', 'bullet-uuid-1');

    expect(result).toBe(true);
  });

  it('returns false when bullet does not belong to user', async () => {
    const where = vi.fn().mockResolvedValue([]);
    const from = vi.fn().mockReturnValue({ where });
    (db.select as any).mockReturnValue({ from });

    const result = await verifyBulletOwnership('user-uuid-1', 'bullet-uuid-other');

    expect(result).toBe(false);
  });

  it('returns false when bullet does not exist', async () => {
    const where = vi.fn().mockResolvedValue([]);
    const from = vi.fn().mockReturnValue({ where });
    (db.select as any).mockReturnValue({ from });

    const result = await verifyBulletOwnership('user-uuid-1', 'nonexistent-bullet');

    expect(result).toBe(false);
  });
});
