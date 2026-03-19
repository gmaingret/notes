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

/**
 * Apply an UndoOp directly to the database (used by undo/redo).
 * Does NOT call bulletService to prevent circular dependency.
 */
async function applyOp(dbInstance: DB, op: UndoOp): Promise<void> {
  switch (op.type) {
    case 'create_bullet': {
      // Re-insert a previously deleted (hard) bullet — used as inverse of delete_bullet
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
      // Sets deletedAt = null (restores a soft-deleted bullet)
      await dbInstance
        .update(bullets)
        .set({ deletedAt: null })
        .where(eq(bullets.id, op.id));
      break;
    }
    case 'update_bullet': {
      const fields = op.fields as Record<string, unknown>;
      // Build a safe set object with only known columns
      const set: Record<string, unknown> = {};
      if ('content' in fields) set['content'] = fields['content'];
      if ('parentId' in fields) set['parentId'] = fields['parentId'];
      if ('position' in fields) set['position'] = fields['position'];
      if ('isComplete' in fields) set['isComplete'] = fields['isComplete'];
      if ('isCollapsed' in fields) set['isCollapsed'] = fields['isCollapsed'];
      if ('deletedAt' in fields) set['deletedAt'] = fields['deletedAt'];
      if ('note' in fields) set['note'] = fields['note'];
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
 * Record a new undo event.
 *
 * Must be called INSIDE an existing Drizzle transaction (the same tx used for
 * the bullet mutation) so both the mutation and the event record atomically.
 *
 * @param dbInstance  The tx handle (or db for standalone use in tests)
 * @param userId      User performing the action
 * @param eventType   Human-readable label ('create_bullet', 'indent', etc.)
 * @param forwardOp   Op to re-apply if redoing
 * @param inverseOp   Op to apply if undoing
 */
export async function recordUndoEvent(
  dbInstance: DB,
  userId: string,
  eventType: string,
  forwardOp: UndoOp,
  inverseOp: UndoOp
): Promise<void> {
  // Get current cursor position
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;
  const newSeq = currentSeq + 1;

  // Truncate redo stack: delete events with seq > currentSeq
  await dbInstance
    .delete(undoEvents)
    .where(and(eq(undoEvents.userId, userId), gt(undoEvents.seq, currentSeq)));

  // Enforce 50-step FIFO cap: drop oldest events beyond 50
  await dbInstance
    .delete(undoEvents)
    .where(and(eq(undoEvents.userId, userId), lt(undoEvents.seq, newSeq - 50)));

  // Insert the new event
  await dbInstance.insert(undoEvents).values({
    userId,
    seq: newSeq,
    schemaVersion: 1,
    eventType,
    forwardOp: forwardOp as unknown as Record<string, unknown>,
    inverseOp: inverseOp as unknown as Record<string, unknown>,
  });

  // Upsert cursor to advance to newSeq
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

  if (currentSeq === 0) {
    // Check if there are any events at all (for canRedo)
    const nextEvent = await dbInstance.query.undoEvents.findFirst({
      where: and(eq(undoEvents.userId, userId), gt(undoEvents.seq, 0)),
    });
    return { canUndo: false, canRedo: nextEvent != null };
  }

  // canUndo: cursor > 0 means there's an event to undo
  const canUndo = currentSeq > 0;

  // canRedo: there's an event with seq > currentSeq
  const nextEvent = await dbInstance.query.undoEvents.findFirst({
    where: and(eq(undoEvents.userId, userId), gt(undoEvents.seq, currentSeq)),
  });
  const canRedo = nextEvent != null;

  return { canUndo, canRedo };
}

/**
 * Undo the most recent event for a user.
 * Applies the inverseOp of the event at currentSeq and decrements cursor.
 */
export async function undo(
  dbInstance: DB,
  userId: string
): Promise<UndoStatus> {
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;

  if (currentSeq === 0) {
    return getStatus(dbInstance, userId);
  }

  // Fetch the event to undo
  const event = await dbInstance.query.undoEvents.findFirst({
    where: and(eq(undoEvents.userId, userId), eq(undoEvents.seq, currentSeq)),
  });

  if (!event) {
    return getStatus(dbInstance, userId);
  }

  // Parse and apply the inverse op
  const inverseOp = event.inverseOp as unknown as UndoOp;
  await applyOp(dbInstance, inverseOp);

  // Decrement cursor
  await dbInstance
    .update(undoCursors)
    .set({ currentSeq: currentSeq - 1 })
    .where(eq(undoCursors.userId, userId));

  return getStatus(dbInstance, userId);
}

/**
 * Redo the next event for a user.
 * Applies the forwardOp of the event at currentSeq+1 and increments cursor.
 */
export async function redo(
  dbInstance: DB,
  userId: string
): Promise<UndoStatus> {
  const cursor = await dbInstance.query.undoCursors.findFirst({
    where: eq(undoCursors.userId, userId),
  });
  const currentSeq = cursor?.currentSeq ?? 0;
  const nextSeq = currentSeq + 1;

  // Check if there's an event to redo
  const event = await dbInstance.query.undoEvents.findFirst({
    where: and(eq(undoEvents.userId, userId), eq(undoEvents.seq, nextSeq)),
  });

  if (!event) {
    return getStatus(dbInstance, userId);
  }

  // Parse and apply the forward op
  const forwardOp = event.forwardOp as unknown as UndoOp;
  await applyOp(dbInstance, forwardOp);

  // Advance cursor
  await dbInstance
    .update(undoCursors)
    .set({ currentSeq: nextSeq })
    .where(eq(undoCursors.userId, userId));

  return getStatus(dbInstance, userId);
}

// Re-export db for default usage (allows callers to pass a custom dbInstance)
export { defaultDb };
