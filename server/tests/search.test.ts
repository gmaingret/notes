import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock DB before importing services
vi.mock('../db/index.js', () => ({
  db: {
    select: vi.fn(),
  },
}));

// Mock drizzle-orm to capture the pattern passed to ilike
vi.mock('drizzle-orm', async (importOriginal) => {
  const actual = await importOriginal<typeof import('drizzle-orm')>();
  return {
    ...actual,
    ilike: vi.fn((_col, pattern) => ({ __ilikePattern: pattern })),
  };
});

import { db } from '../db/index.js';
import { ilike } from 'drizzle-orm';
import { searchBullets } from '../src/services/searchService.js';
import { getBulletsForTag } from '../src/services/tagService.js';

// ─── escapeIlike helper (tested via searchBullets) ────────────────────────────

describe('searchBullets ILIKE escaping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Set up db chain that resolves to empty array
    const limit = vi.fn().mockResolvedValue([]);
    const where = vi.fn().mockReturnValue({ limit });
    const innerJoin = vi.fn().mockReturnValue({ where });
    const from = vi.fn().mockReturnValue({ innerJoin });
    (db.select as any).mockReturnValue({ from });
  });

  it('escapes % with backslash so it is treated as a literal percent', async () => {
    await searchBullets('user-1', '%hello%');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%\\%hello\\%%');
  });

  it('escapes _ with backslash so it is treated as a literal underscore', async () => {
    await searchBullets('user-1', 'hello_world');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%hello\\_world%');
  });

  it('escapes combined % and _ characters', async () => {
    await searchBullets('user-1', '%hello_world%');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%\\%hello\\_world\\%%');
  });

  it('leaves normal alphanumeric input unchanged', async () => {
    await searchBullets('user-1', 'hello world');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%hello world%');
  });

  it('returns empty array for empty query', async () => {
    const result = await searchBullets('user-1', '');
    expect(result).toEqual([]);
  });
});

// ─── getBulletsForTag ILIKE escaping ─────────────────────────────────────────

describe('getBulletsForTag ILIKE escaping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const limit = vi.fn().mockResolvedValue([]);
    const where = vi.fn().mockReturnValue({ limit });
    const innerJoin = vi.fn().mockReturnValue({ where });
    const from = vi.fn().mockReturnValue({ innerJoin });
    (db.select as any).mockReturnValue({ from });
  });

  it('escapes % in tag value before ILIKE pattern', async () => {
    await getBulletsForTag('user-1', 'tag', '50%off');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%#50\\%off%');
  });

  it('escapes _ in tag value before ILIKE pattern', async () => {
    await getBulletsForTag('user-1', 'tag', 'my_tag');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%#my\\_tag%');
  });

  it('escapes _ in mention value before ILIKE pattern', async () => {
    await getBulletsForTag('user-1', 'mention', 'john_doe');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%@john\\_doe%');
  });

  it('escapes % in date value before ILIKE pattern', async () => {
    await getBulletsForTag('user-1', 'date', '2024%01%01');
    const calls = (ilike as any).mock.calls;
    expect(calls.length).toBeGreaterThan(0);
    const pattern = calls[0][1];
    expect(pattern).toBe('%!![2024\\%01\\%01]%');
  });
});
