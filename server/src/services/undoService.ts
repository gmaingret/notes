import { db as defaultDb } from '../../db/index.js';
import { undoEvents, undoCursors, bullets } from '../../db/schema.js';
import { eq, and, gt, lt, isNull } from 'drizzle-orm';
import type { DB } from '../../db/index.js';

// Discriminated union of all ops that can be stored in undo_events
export type UndoOp =
  | { type: 'create_bullet'; bullet: BulletRow }
  | { type: 'delete_bullet'; id: string }
  | { type: 'restore_bullet'; id: string }
  | { type: 'restore_bullet_delete'; id: string }
  | { type: 'update_bullet'; id: string; fields: Partial<BulletRow> }
  | { type: 'move_bullet'; id: string; parentId: string | null; position: number }
  | { type: 'batch'; ops: UndoOp[] };

export type BulletRow = {
  id: string;
  documentId: string;
  userId: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  isCollapsed: boolean;
  deletedAt: Date | null;
};

export type UndoStatus = {
  canUndo: boolean;
  canRedo: boolean;
};

const ALLOWED_COLUMNS = ['content', 'parentId', 'position', 'isComplete', 'isCollapsed', 'deletedAt', 'note'] as const;

/**
 * Apply an UndoOp directly to the database (used by undo/redo).
 */
async function applyOp(dbInstance: DB, op: UndoOp): Promise<void> {
  switch (op.type) {
    case 'create_bullet': {
      await dbInstance.insert(bullets).values({
        id: op.bullet.id,
        documentId: op.bullet.documentId,
        userId: op.bullet.userId,
        parentId: op.bullet.parentId ?? null,
        content: op.bullet.content,
        position: op.bullet.position,
        isComplete: op.bullet.isComplete,
        isCollapsed: op.bullet.isCollapsed,
      });
      break;
    }
    case 'delete_bullet': {
      await dbInstance
        .update(bullets)
        .set({ deletedAt: new Date() })
        .where(eq(bullets.id, op.id));
      break;
    }
    case 'restore_bullet':
    case 'restore_bullet_delete': {
      await dbInstance
        .update(bullets)
        .set({ deletedAt: null })
        .where(eq(bullets.id, op.id));
      break;
    }
    case 'update_bullet': {
      const fields = op.fields as Record<string, unknown>;
      const set: Record<string, unknown> = {};
      for (const col of ALLOWED_COLUMNS) {
        if (col in fields) set[col] = fields[col];
      }
      if (Object.keys(set).length > 0) {
        await dbInstance
          .update(bullets)
          .set(set as Parameters<ReturnType<typeof dbInstance.update>['set']>[0])
          .where(eq(bullets.id, op.id));
      }
      break;
    }
    case 'move_bullet': {
      await dbInstance
        .update(bullets)
        .set({ parentId: op.parentId, position: op.position })
        .where(eq(bullets.id, op.id));
      break;
    }
    case 'batch': {
      for (const subOp of op.ops) {
        await applyOp(dbInstance, subOp);
      }
      break;
    }
  }
}

/**
 * Record a new undo event (must be called inside the same tx as the mutation).
 */
export async function recordUndoEvent(
  dbInstance: DB,
  userId: string,
  eventType: string,
  forwardOp: UndoOp,
  inverseOp: UndoOp
): Promise<void> {
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;
  const newSeq = currentSeq + 1;

  // Truncate redo stack
  await dbInstance
    .delete(undoEvents)
    .where(and(eq(undoEvents.userId, userId), gt(undoEvents.seq, currentSeq)));

  // Enforce 50-step FIFO cap
  await dbInstance
    .delete(undoEvents)
    .where(and(eq(undoEvents.userId, userId), lt(undoEvents.seq, newSeq - 50)));

  await dbInstance.insert(undoEvents).values({
    userId,
    seq: newSeq,
    schemaVersion: 1,
    eventType,
    forwardOp: forwardOp as unknown as Record<string, unknown>,
    inverseOp: inverseOp as unknown as Record<string, unknown>,
  });

  await dbInstance
    .insert(undoCursors)
    .values({ userId, currentSeq: newSeq })
    .onConflictDoUpdate({
      target: undoCursors.userId,
      set: { currentSeq: newSeq },
    });
}

/**
 * Returns the current undo/redo availability for a user.
 */
export async function getStatus(
  dbInstance: DB,
  userId: string
): Promise<UndoStatus> {
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;

  const canUndo = currentSeq > 0;
  const nextEvent = await dbInstance.query.undoEvents.findFirst({
    where: and(eq(undoEvents.userId, userId), gt(undoEvents.seq, currentSeq)),
  });

  return { canUndo, canRedo: nextEvent != null };
}

/**
 * Shared undo/redo implementation. Direction determines which op to apply
 * and how to adjust the cursor.
 */
async function applyUndoRedo(
  dbInstance: DB,
  userId: string,
  direction: 'undo' | 'redo'
): Promise<UndoStatus> {
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;

  const targetSeq = direction === 'undo' ? currentSeq : currentSeq + 1;
  if (direction === 'undo' && currentSeq === 0) {
    return getStatus(dbInstance, userId);
  }

  const event = await dbInstance.query.undoEvents.findFirst({
    where: and(eq(undoEvents.userId, userId), eq(undoEvents.seq, targetSeq)),
  });
  if (!event) return getStatus(dbInstance, userId);

  const op = (direction === 'undo' ? event.inverseOp : event.forwardOp) as unknown as UndoOp;
  await applyOp(dbInstance, op);

  const newSeq = direction === 'undo' ? currentSeq - 1 : currentSeq + 1;
  await dbInstance
    .update(undoCursors)
    .set({ currentSeq: newSeq })
    .where(eq(undoCursors.userId, userId));

  return getStatus(dbInstance, userId);
}

/** Undo the most recent event for a user. */
export async function undo(dbInstance: DB, userId: string): Promise<UndoStatus> {
  return applyUndoRedo(dbInstance, userId, 'undo');
}

/** Redo the next event for a user. */
export async function redo(dbInstance: DB, userId: string): Promise<UndoStatus> {
  return applyUndoRedo(dbInstance, userId, 'redo');
}

export { defaultDb };
