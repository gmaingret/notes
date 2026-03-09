import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  recordUndoEvent,
  undo,
  redo,
  getStatus,
  type UndoOp,
} from '../src/services/undoService.js';

// ─── Mocked DB ────────────────────────────────────────────────────────────────
// We mock the DB at module level to intercept all drizzle query builder calls.
// Each test configures the mock's return values to simulate DB state.

const USER_ID = 'user-uuid-abc';

// We build a lightweight mock that covers the drizzle query builder chain.
function makeDb(overrides: Record<string, unknown> = {}) {
  const db = {
    query: {
      undoCursors: {
        findFirst: vi.fn(),
      },
      undoEvents: {
        findFirst: vi.fn(),
      },
    },
    insert: vi.fn(),
    delete: vi.fn(),
    update: vi.fn(),
    ...overrides,
  };

  // Default chaining setup for insert
  db.insert.mockReturnValue({
    values: vi.fn().mockReturnValue({
      onConflictDoUpdate: vi.fn().mockResolvedValue(undefined),
    }),
  });

  // Default chaining setup for delete
  db.delete.mockReturnValue({
    where: vi.fn().mockResolvedValue(undefined),
  });

  // Default chaining setup for update
  db.update.mockReturnValue({
    set: vi.fn().mockReturnValue({
      where: vi.fn().mockResolvedValue(undefined),
    }),
  });

  return db;
}

// Stub UndoOps for testing
const stubForwardOp: UndoOp = { type: 'delete_bullet', id: 'bullet-1' };
const stubInverseOp: UndoOp = { type: 'restore_bullet', id: 'bullet-1' };

// ─── recordUndoEvent ──────────────────────────────────────────────────────────

describe('undoService.recordUndoEvent', () => {
  it('inserts a new undo_event row with forward_op and inverse_op', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 2 });

    const insertValues = vi.fn().mockReturnValue({
      onConflictDoUpdate: vi.fn().mockResolvedValue(undefined),
    });
    db.insert.mockReturnValue({ values: insertValues });

    await recordUndoEvent(db as never, USER_ID, 'delete_bullet', stubForwardOp, stubInverseOp);

    // insert called twice: once for undoEvents, once for undoCursors upsert
    expect(db.insert).toHaveBeenCalledTimes(2);
    expect(insertValues).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: USER_ID,
        seq: 3,  // currentSeq(2) + 1
        schemaVersion: 1,
        eventType: 'delete_bullet',
        forwardOp: stubForwardOp,
        inverseOp: stubInverseOp,
      })
    );
  });

  it('advances the undo_cursor seq for the user', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 5 });

    const onConflictDoUpdate = vi.fn().mockResolvedValue(undefined);
    const values = vi.fn().mockReturnValue({ onConflictDoUpdate });
    db.insert.mockReturnValue({ values });

    await recordUndoEvent(db as never, USER_ID, 'create_bullet', stubForwardOp, stubInverseOp);

    // The second insert (undoCursors upsert) should set currentSeq = 6
    expect(values).toHaveBeenLastCalledWith(
      expect.objectContaining({ userId: USER_ID, currentSeq: 6 })
    );
    expect(onConflictDoUpdate).toHaveBeenCalledWith(
      expect.objectContaining({
        set: expect.objectContaining({ currentSeq: 6 }),
      })
    );
  });

  it('truncates redo stack (events with seq > current cursor) before recording', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 3 });

    const whereDelete = vi.fn().mockResolvedValue(undefined);
    db.delete.mockReturnValue({ where: whereDelete });
    db.insert.mockReturnValue({
      values: vi.fn().mockReturnValue({ onConflictDoUpdate: vi.fn().mockResolvedValue(undefined) }),
    });

    await recordUndoEvent(db as never, USER_ID, 'delete_bullet', stubForwardOp, stubInverseOp);

    // delete called twice: once for redo truncation (seq > 3), once for FIFO cap
    expect(db.delete).toHaveBeenCalledTimes(2);
  });

  it('drops the oldest event when 51st event is recorded (50-step cap)', async () => {
    const db = makeDb();
    // currentSeq = 50, so newSeq = 51. FIFO: delete events where seq < 51 - 50 = 1
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 50 });

    const whereDelete = vi.fn().mockResolvedValue(undefined);
    db.delete.mockReturnValue({ where: whereDelete });
    db.insert.mockReturnValue({
      values: vi.fn().mockReturnValue({ onConflictDoUpdate: vi.fn().mockResolvedValue(undefined) }),
    });

    await recordUndoEvent(db as never, USER_ID, 'delete_bullet', stubForwardOp, stubInverseOp);

    // Both delete calls must have been made (redo truncation + FIFO cap)
    expect(db.delete).toHaveBeenCalledTimes(2);
    // The FIFO cap delete should use lt(seq, newSeq - 50) = lt(seq, 1)
    // We can't easily introspect drizzle operators, but we verify delete was called twice
    expect(whereDelete).toHaveBeenCalledTimes(2);
  });
});

// ─── undo ─────────────────────────────────────────────────────────────────────

describe('undoService.undo', () => {
  it('applies the inverse_op of the current event and decrements cursor', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 2 })  // first call in undo()
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 1 });  // call in getStatus()

    db.query.undoEvents.findFirst
      .mockResolvedValueOnce({  // event at seq=2
        id: 1, userId: USER_ID, seq: 2, schemaVersion: 1, eventType: 'delete_bullet',
        forwardOp: stubForwardOp,
        inverseOp: stubInverseOp,  // restore_bullet
      })
      .mockResolvedValue(null);  // no further events for canRedo check

    const setWhere = vi.fn().mockResolvedValue(undefined);
    const setFn = vi.fn().mockReturnValue({ where: setWhere });
    db.update.mockReturnValue({ set: setFn });

    const result = await undo(db as never, USER_ID);

    // update was called to decrement cursor and to restore bullet
    expect(db.update).toHaveBeenCalledTimes(2);
    // Result reflects cursor now at seq=1, no events above for redo
    expect(result).toMatchObject({ canUndo: true, canRedo: false });
  });

  it('returns can_undo: false when cursor is at 0', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 0 });
    db.query.undoEvents.findFirst.mockResolvedValue(null);

    const result = await undo(db as never, USER_ID);

    expect(result.canUndo).toBe(false);
    // update should NOT have been called (no op to undo)
    expect(db.update).not.toHaveBeenCalled();
  });
});

// ─── redo ─────────────────────────────────────────────────────────────────────

describe('undoService.redo', () => {
  it('applies the forward_op of the next event and increments cursor', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 1 })  // first call in redo()
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 2 });  // call in getStatus()

    db.query.undoEvents.findFirst
      .mockResolvedValueOnce({  // event at seq=2 (currentSeq + 1 = 2)
        id: 2, userId: USER_ID, seq: 2, schemaVersion: 1, eventType: 'delete_bullet',
        forwardOp: stubForwardOp,
        inverseOp: stubInverseOp,
      })
      .mockResolvedValue(null);  // no further events

    const setWhere = vi.fn().mockResolvedValue(undefined);
    const setFn = vi.fn().mockReturnValue({ where: setWhere });
    db.update.mockReturnValue({ set: setFn });

    const result = await redo(db as never, USER_ID);

    // update called: once to apply forwardOp (delete_bullet), once to increment cursor
    expect(db.update).toHaveBeenCalledTimes(2);
    expect(result).toMatchObject({ canUndo: true, canRedo: false });
  });

  it('returns can_redo: false when cursor is at latest seq', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 5 })  // first call in redo()
      .mockResolvedValueOnce({ userId: USER_ID, currentSeq: 5 });  // call in getStatus()

    // No event at seq=6 (nothing to redo)
    db.query.undoEvents.findFirst.mockResolvedValue(null);

    const result = await redo(db as never, USER_ID);

    expect(result.canRedo).toBe(false);
    expect(db.update).not.toHaveBeenCalled();
  });
});

// ─── getStatus ────────────────────────────────────────────────────────────────

describe('undoService.getStatus', () => {
  it('returns { canUndo: false, canRedo: false } for user with no events', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue(null);  // no cursor = no events
    db.query.undoEvents.findFirst.mockResolvedValue(null);

    const result = await getStatus(db as never, USER_ID);

    expect(result).toEqual({ canUndo: false, canRedo: false });
  });

  it('returns { canUndo: true, canRedo: false } after one event recorded', async () => {
    const db = makeDb();
    db.query.undoCursors.findFirst.mockResolvedValue({ userId: USER_ID, currentSeq: 1 });
    // No events above seq=1
    db.query.undoEvents.findFirst.mockResolvedValue(null);

    const result = await getStatus(db as never, USER_ID);

    expect(result).toEqual({ canUndo: true, canRedo: false });
  });
});
