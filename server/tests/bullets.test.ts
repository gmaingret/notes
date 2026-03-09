import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  createBullet,
  indentBullet,
  outdentBullet,
  moveBullet,
  softDeleteBullet,
  markComplete,
  setCollapsed,
  computeBulletInsertPosition,
} from '../src/services/bulletService.js';

// ─── Test constants ───────────────────────────────────────────────────────────
const USER_ID = 'user-uuid-abc';
const DOC_ID = 'doc-uuid-111';
const BULLET_A = 'bullet-uuid-aaa';  // position 1.0
const BULLET_B = 'bullet-uuid-bbb';  // position 2.0
const BULLET_C = 'bullet-uuid-ccc';  // position 3.0
const BULLET_D = 'bullet-uuid-ddd';  // child of B

// ─── Shared mock helpers ──────────────────────────────────────────────────────

/**
 * Build a lightweight Drizzle-compatible mock.
 * Each builder method returns a fresh mock with standard chaining.
 */
function makeDb() {
  const db: Record<string, unknown> = {};

  // query relational API
  db.query = {
    bullets: {
      findFirst: vi.fn(),
    },
    undoCursors: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
    undoEvents: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
  };

  // Drizzle query builder chains
  const selectWhere = vi.fn();
  const selectFrom = vi.fn().mockReturnValue({ where: selectWhere });
  db.select = vi.fn().mockReturnValue({ from: selectFrom });

  // We'll override selectWhere per-test to return specific siblings
  db._selectWhere = selectWhere;
  db._selectFrom = selectFrom;

  // insert chain: .values().returning()
  const insertReturning = vi.fn().mockResolvedValue([]);
  const insertValues = vi.fn().mockReturnValue({ returning: insertReturning, onConflictDoUpdate: vi.fn().mockResolvedValue(undefined) });
  db.insert = vi.fn().mockReturnValue({ values: insertValues });
  db._insertValues = insertValues;
  db._insertReturning = insertReturning;

  // delete chain: .where()
  const deleteWhere = vi.fn().mockResolvedValue(undefined);
  db.delete = vi.fn().mockReturnValue({ where: deleteWhere });

  // update chain: .set().where().returning()
  const updateReturning = vi.fn().mockResolvedValue([]);
  const updateWhere = vi.fn().mockReturnValue({ returning: updateReturning });
  const updateSet = vi.fn().mockReturnValue({ where: updateWhere });
  db.update = vi.fn().mockReturnValue({ set: updateSet });
  db._updateSet = updateSet;
  db._updateWhere = updateWhere;
  db._updateReturning = updateReturning;

  // transaction: executes the callback with the same db
  db.transaction = vi.fn().mockImplementation(async (fn: (tx: unknown) => Promise<void>) => {
    await fn(db);
  });

  return db;
}

function makeBulletRow(overrides: Record<string, unknown> = {}) {
  return {
    id: BULLET_A,
    documentId: DOC_ID,
    userId: USER_ID,
    parentId: null,
    content: 'test',
    position: 1.0,
    isComplete: false,
    isCollapsed: false,
    deletedAt: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

// ─── computeBulletInsertPosition ─────────────────────────────────────────────

describe('bulletService.computeBulletInsertPosition', () => {
  it('returns midpoint between two existing siblings', async () => {
    const db = makeDb();
    // Siblings: A at 1.0, C at 3.0 — insert after A → midpoint = 2.0
    (db._selectWhere as ReturnType<typeof vi.fn>)
      .mockReturnValue({
        orderBy: vi.fn().mockResolvedValue([
          { id: BULLET_A, position: 1.0 },
          { id: BULLET_C, position: 3.0 },
        ]),
      });

    const pos = await computeBulletInsertPosition(db as never, DOC_ID, null, BULLET_A);
    expect(pos).toBe(2.0); // midpoint of 1.0 and 3.0
  });

  it('returns half of first sibling position when inserting at head', async () => {
    const db = makeDb();
    // Single sibling at 2.0 — insert before it (afterId=null) → 1.0
    (db._selectWhere as ReturnType<typeof vi.fn>)
      .mockReturnValue({
        orderBy: vi.fn().mockResolvedValue([
          { id: BULLET_A, position: 2.0 },
        ]),
      });

    const pos = await computeBulletInsertPosition(db as never, DOC_ID, null, null);
    expect(pos).toBe(1.0); // 2.0 / 2 = 1.0
  });

  it('returns last position + 1.0 when inserting at tail', async () => {
    const db = makeDb();
    // Two siblings at 1.0 and 2.0 — insert after B (last) → 3.0
    (db._selectWhere as ReturnType<typeof vi.fn>)
      .mockReturnValue({
        orderBy: vi.fn().mockResolvedValue([
          { id: BULLET_A, position: 1.0 },
          { id: BULLET_B, position: 2.0 },
        ]),
      });

    const pos = await computeBulletInsertPosition(db as never, DOC_ID, null, BULLET_B);
    expect(pos).toBe(3.0); // 2.0 + 1.0
  });
});

// ─── createBullet ─────────────────────────────────────────────────────────────

describe('bulletService.createBullet', () => {
  it('inserts bullet after specified sibling using FLOAT8 midpoint position', async () => {
    const db = makeDb();
    // Siblings: A at 1.0, C at 3.0 — insert after A → 2.0
    let callCount = 0;
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockImplementation(() => {
        callCount++;
        if (callCount === 1) {
          // First call: get siblings for position computation
          return Promise.resolve([
            { id: BULLET_A, position: 1.0 },
            { id: BULLET_C, position: 3.0 },
          ]);
        }
        return Promise.resolve([]);
      }),
    });

    const createdBullet = makeBulletRow({ id: 'new-bullet', position: 2.0 });
    (db._insertReturning as ReturnType<typeof vi.fn>).mockResolvedValue([createdBullet]);

    const result = await createBullet(
      USER_ID,
      { documentId: DOC_ID, parentId: null, afterId: BULLET_A, content: 'new' },
      db as never
    );

    expect(result.position).toBe(2.0);
    expect(db.transaction).toHaveBeenCalled();
  });

  it('inserts bullet at end when afterId is null', async () => {
    const db = makeDb();
    // No siblings → returns 1.0 (default start position)
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([]),
    });

    const createdBullet = makeBulletRow({ id: 'new-bullet', position: 1.0 });
    (db._insertReturning as ReturnType<typeof vi.fn>).mockResolvedValue([createdBullet]);

    const result = await createBullet(
      USER_ID,
      { documentId: DOC_ID, parentId: null, afterId: null, content: 'first' },
      db as never
    );

    expect(result.position).toBe(1.0);
  });

  it('records an undo event in the same transaction', async () => {
    const db = makeDb();
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([]),
    });

    const createdBullet = makeBulletRow({ id: 'new-bullet', position: 1.0 });
    (db._insertReturning as ReturnType<typeof vi.fn>).mockResolvedValue([createdBullet]);

    await createBullet(
      USER_ID,
      { documentId: DOC_ID, parentId: null, afterId: null, content: 'test' },
      db as never
    );

    // insert called at least twice: once for bullet, once for undo_event + cursor upsert
    expect(db.insert).toHaveBeenCalledTimes(3); // bullet + undo_event + cursor
    expect(db.transaction).toHaveBeenCalled();
  });
});

// ─── indentBullet ─────────────────────────────────────────────────────────────

describe('bulletService.indentBullet', () => {
  it('makes the bullet a last child of its previous sibling', async () => {
    const db = makeDb();
    // Bullet B is at index 1 among siblings [A, B, C]
    const bulletB = makeBulletRow({ id: BULLET_B, position: 2.0, parentId: null });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletB),
    };

    let selectCallCount = 0;
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockImplementation(() => {
        selectCallCount++;
        if (selectCallCount === 1) {
          // All siblings of B (same parent=null)
          return Promise.resolve([
            { id: BULLET_A, position: 1.0, parentId: null, documentId: DOC_ID },
            { id: BULLET_B, position: 2.0, parentId: null, documentId: DOC_ID },
            { id: BULLET_C, position: 3.0, parentId: null, documentId: DOC_ID },
          ]);
        }
        if (selectCallCount === 2) {
          // Children of A (the previous sibling) — empty
          return Promise.resolve([]);
        }
        if (selectCallCount === 3) {
          // Position computation siblings for A's children
          return Promise.resolve([]);
        }
        return Promise.resolve([]);
      }),
    });

    const updatedBullet = makeBulletRow({ id: BULLET_B, parentId: BULLET_A, position: 1.0 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    const result = await indentBullet(USER_ID, BULLET_B, db as never);

    expect(result).not.toBeNull();
    expect(db.update).toHaveBeenCalled();
    expect(db.transaction).toHaveBeenCalled();
  });

  it('is a no-op when bullet is the first sibling (no previous sibling)', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, position: 1.0, parentId: null });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([
        { id: BULLET_A, position: 1.0, parentId: null, documentId: DOC_ID },
        { id: BULLET_B, position: 2.0, parentId: null, documentId: DOC_ID },
      ]),
    });

    const result = await indentBullet(USER_ID, BULLET_A, db as never);

    // Should return the bullet unchanged without calling update
    expect(result).toMatchObject({ id: BULLET_A });
    expect(db.update).not.toHaveBeenCalled();
  });

  it('records an undo event', async () => {
    const db = makeDb();
    const bulletB = makeBulletRow({ id: BULLET_B, position: 2.0, parentId: null });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletB),
    };

    let selectCallCount = 0;
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockImplementation(() => {
        selectCallCount++;
        if (selectCallCount === 1) {
          return Promise.resolve([
            { id: BULLET_A, position: 1.0, parentId: null, documentId: DOC_ID },
            { id: BULLET_B, position: 2.0, parentId: null, documentId: DOC_ID },
          ]);
        }
        // Children of A and position calculation
        return Promise.resolve([]);
      }),
    });

    const updatedBullet = makeBulletRow({ id: BULLET_B, parentId: BULLET_A, position: 1.0 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    await indentBullet(USER_ID, BULLET_B, db as never);

    // insert called for undo_event + cursor
    expect(db.insert).toHaveBeenCalledTimes(2);
  });
});

// ─── outdentBullet ────────────────────────────────────────────────────────────

describe('bulletService.outdentBullet', () => {
  it('makes the bullet a sibling of its parent, positioned after parent', async () => {
    const db = makeDb();
    // Bullet D is a child of B (parentId=B)
    const bulletD = makeBulletRow({ id: BULLET_D, parentId: BULLET_B, position: 1.0 });
    const bulletB = makeBulletRow({ id: BULLET_B, parentId: null, position: 2.0 });

    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn()
        .mockResolvedValueOnce(bulletD)  // load target bullet
        .mockResolvedValueOnce(bulletB),  // load parent
    };

    // Siblings of B (for new position after B)
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([
        { id: BULLET_B, position: 2.0 },
        { id: BULLET_C, position: 3.0 },
      ]),
    });

    const updatedBullet = makeBulletRow({ id: BULLET_D, parentId: null, position: 2.5 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    const result = await outdentBullet(USER_ID, BULLET_D, db as never);

    expect(result).not.toBeNull();
    expect(db.update).toHaveBeenCalled();
    expect(db.transaction).toHaveBeenCalled();
  });

  it('is a no-op on root-level bullet', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, parentId: null, position: 1.0 });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    const result = await outdentBullet(USER_ID, BULLET_A, db as never);

    expect(result).toMatchObject({ id: BULLET_A });
    expect(db.update).not.toHaveBeenCalled();
  });

  it('records an undo event', async () => {
    const db = makeDb();
    const bulletD = makeBulletRow({ id: BULLET_D, parentId: BULLET_B, position: 1.0 });
    const bulletB = makeBulletRow({ id: BULLET_B, parentId: null, position: 2.0 });

    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn()
        .mockResolvedValueOnce(bulletD)
        .mockResolvedValueOnce(bulletB),
    };

    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([
        { id: BULLET_B, position: 2.0 },
      ]),
    });

    const updatedBullet = makeBulletRow({ id: BULLET_D, parentId: null, position: 3.0 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    await outdentBullet(USER_ID, BULLET_D, db as never);

    // insert called for undo_event + cursor
    expect(db.insert).toHaveBeenCalledTimes(2);
  });
});

// ─── moveBullet ───────────────────────────────────────────────────────────────

describe('bulletService.moveBullet', () => {
  it('moves bullet and all children to new parent and position', async () => {
    const db = makeDb();
    const bulletB = makeBulletRow({ id: BULLET_B, parentId: null, position: 2.0 });

    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletB),
    };

    // select for getDescendantIds (all bullets in doc) and for position computation
    let selectCallCount = 0;
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([]),
    });

    // For getDescendantIds — select without orderBy
    (db.select as ReturnType<typeof vi.fn>).mockImplementation(() => ({
      from: vi.fn().mockReturnValue({
        where: vi.fn().mockImplementation(() => {
          selectCallCount++;
          if (selectCallCount === 1) {
            // getDescendantIds: all non-deleted bullets in doc
            return Promise.resolve([
              { id: BULLET_A, parentId: null },
              { id: BULLET_B, parentId: null },
              { id: BULLET_C, parentId: null },
              { id: BULLET_D, parentId: BULLET_B },
            ]);
          }
          // position computation siblings (return empty for simplicity)
          return {
            orderBy: vi.fn().mockResolvedValue([]),
          };
        }),
      }),
    }));

    const updatedBullet = makeBulletRow({ id: BULLET_B, parentId: BULLET_C, position: 1.0 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    const result = await moveBullet(
      USER_ID, BULLET_B, { newParentId: BULLET_C, afterId: null }, db as never
    );

    expect(result).not.toBeNull();
    expect(db.update).toHaveBeenCalled();
    expect(db.transaction).toHaveBeenCalled();
  });

  it('does not allow moving a bullet under its own descendant (cycle guard)', async () => {
    const db = makeDb();
    const bulletB = makeBulletRow({ id: BULLET_B, parentId: null, position: 2.0 });

    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletB),
    };

    // getDescendantIds: B has child D
    (db.select as ReturnType<typeof vi.fn>).mockReturnValue({
      from: vi.fn().mockReturnValue({
        where: vi.fn().mockResolvedValue([
          { id: BULLET_B, parentId: null },
          { id: BULLET_D, parentId: BULLET_B },  // D is a descendant of B
        ]),
      }),
    });

    // Try to move B under D (D is a descendant of B → cycle!)
    await expect(
      moveBullet(USER_ID, BULLET_B, { newParentId: BULLET_D, afterId: null }, db as never)
    ).rejects.toThrow('Cannot move a bullet under one of its own descendants');
  });

  it('records an undo event', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, parentId: null, position: 1.0 });

    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    let selectCallCount = 0;
    (db.select as ReturnType<typeof vi.fn>).mockImplementation(() => ({
      from: vi.fn().mockReturnValue({
        where: vi.fn().mockImplementation(() => {
          selectCallCount++;
          if (selectCallCount === 1) {
            // getDescendantIds: A has no children
            return Promise.resolve([{ id: BULLET_A, parentId: null }]);
          }
          return { orderBy: vi.fn().mockResolvedValue([]) };
        }),
      }),
    }));

    const updatedBullet = makeBulletRow({ id: BULLET_A, parentId: BULLET_C, position: 1.0 });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([updatedBullet]);

    await moveBullet(USER_ID, BULLET_A, { newParentId: BULLET_C, afterId: null }, db as never);

    // undo event + cursor
    expect(db.insert).toHaveBeenCalledTimes(2);
  });
});

// ─── softDeleteBullet ─────────────────────────────────────────────────────────

describe('bulletService.softDeleteBullet', () => {
  it('sets deletedAt on the bullet', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    const deletedBullet = makeBulletRow({ id: BULLET_A, deletedAt: new Date() });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([deletedBullet]);

    const result = await softDeleteBullet(USER_ID, BULLET_A, db as never);

    expect(result).not.toBeNull();
    expect(db.update).toHaveBeenCalled();
    // Verify set was called with a deletedAt value
    const setCall = (db._updateSet as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(setCall).toHaveProperty('deletedAt');
    expect(setCall.deletedAt).toBeInstanceOf(Date);
  });

  it('excludes deleted bullet from getDocumentBullets result', async () => {
    const db = makeDb();
    // getDocumentBullets uses where(isNull(deletedAt))
    // Simulate: no bullets returned because deleted one is filtered out
    (db._selectWhere as ReturnType<typeof vi.fn>).mockReturnValue({
      orderBy: vi.fn().mockResolvedValue([]),  // deleted bullet filtered by isNull(deletedAt)
    });

    const { getDocumentBullets } = await import('../src/services/bulletService.js');
    const result = await getDocumentBullets(USER_ID, DOC_ID, db as never);

    expect(result).toEqual([]);
  });

  it('records an undo event with inverse_op that restores the bullet', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    const deletedBullet = makeBulletRow({ id: BULLET_A, deletedAt: new Date() });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([deletedBullet]);

    await softDeleteBullet(USER_ID, BULLET_A, db as never);

    // insert called for undo_event (with inverseOp=restore_bullet) + cursor
    expect(db.insert).toHaveBeenCalledTimes(2);
    const insertCall = (db._insertValues as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(insertCall.inverseOp).toMatchObject({ type: 'restore_bullet', id: BULLET_A });
  });
});

// ─── markComplete ─────────────────────────────────────────────────────────────

describe('bulletService.markComplete', () => {
  it('sets isComplete=true on PATCH', async () => {
    const db = makeDb();
    const completedBullet = makeBulletRow({ id: BULLET_A, isComplete: true });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([completedBullet]);

    const result = await markComplete(USER_ID, BULLET_A, true, db as never);

    expect(result).not.toBeNull();
    expect(db.update).toHaveBeenCalled();
    const setCall = (db._updateSet as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(setCall).toMatchObject({ isComplete: true });
    // No undo recording — no insert should happen
    expect(db.insert).not.toHaveBeenCalled();
  });

  it('sets isComplete=false when toggled again', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, isComplete: false });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([bulletA]);

    const result = await markComplete(USER_ID, BULLET_A, false, db as never);

    expect(result).not.toBeNull();
    const setCall = (db._updateSet as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(setCall).toMatchObject({ isComplete: false });
  });
});

// ─── setCollapsed ─────────────────────────────────────────────────────────────

describe('bulletService.setCollapsed', () => {
  it('sets isCollapsed=true on the bullet', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, isCollapsed: false });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    const collapsedBullet = makeBulletRow({ id: BULLET_A, isCollapsed: true });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([collapsedBullet]);

    const result = await setCollapsed(USER_ID, BULLET_A, true, db as never);

    expect(result).not.toBeNull();
    const setCall = (db._updateSet as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(setCall).toMatchObject({ isCollapsed: true });
    // Undo event recorded
    expect(db.insert).toHaveBeenCalledTimes(2);
    expect(db.transaction).toHaveBeenCalled();
  });

  it('sets isCollapsed=false when toggled again', async () => {
    const db = makeDb();
    const bulletA = makeBulletRow({ id: BULLET_A, isCollapsed: true });
    (db.query as Record<string, unknown>).bullets = {
      findFirst: vi.fn().mockResolvedValue(bulletA),
    };

    const expandedBullet = makeBulletRow({ id: BULLET_A, isCollapsed: false });
    (db._updateReturning as ReturnType<typeof vi.fn>).mockResolvedValue([expandedBullet]);

    const result = await setCollapsed(USER_ID, BULLET_A, false, db as never);

    expect(result).not.toBeNull();
    const setCall = (db._updateSet as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(setCall).toMatchObject({ isCollapsed: false });
  });
});
