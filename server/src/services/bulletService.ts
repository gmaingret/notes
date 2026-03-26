import { db as defaultDb } from '../../db/index.js';
import { bullets } from '../../db/schema.js';
import { eq, and, isNull, asc } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { recordUndoEvent } from './undoService.js';
import type { DB } from '../../db/index.js';
import type { BulletRow, UndoOp } from './undoService.js';

// Full bullet shape as returned from DB
export type Bullet = {
  id: string;
  documentId: string;
  userId: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  isCollapsed: boolean;
  note: string | null;
  deletedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
};

// ─── Helpers ────────────────────────────────────────────────────────────────

/** Load a bullet by id+userId, optionally requiring it to be non-deleted. */
async function loadBullet(
  dbInstance: DB,
  bulletId: string,
  userId: string,
  opts: { requireActive?: boolean } = {}
): Promise<Bullet | null> {
  const conditions = opts.requireActive
    ? and(eq(bullets.id, bulletId), eq(bullets.userId, userId), isNull(bullets.deletedAt))
    : and(eq(bullets.id, bulletId), eq(bullets.userId, userId));
  const row = await dbInstance.query.bullets.findFirst({ where: conditions });
  return (row as Bullet) ?? null;
}

/** Update a bullet inside a transaction and record an undo event. */
async function updateWithUndo(
  dbInstance: DB,
  userId: string,
  bulletId: string,
  set: Record<string, unknown>,
  actionName: string,
  forwardOp: UndoOp,
  inverseOp: UndoOp
): Promise<Bullet> {
  let updated: Bullet | undefined;
  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set(set as Parameters<ReturnType<typeof tx.update>['set']>[0])
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;
    await recordUndoEvent(tx, userId, actionName, forwardOp, inverseOp);
  });
  return updated!;
}

// ─── Public API ─────────────────────────────────────────────────────────────

/**
 * Compute FLOAT8 midpoint insert position for a bullet among siblings.
 */
export async function computeBulletInsertPosition(
  dbInstance: DB,
  documentId: string,
  parentId: string | null,
  afterId: string | null
): Promise<number> {
  const siblings = await dbInstance
    .select({ id: bullets.id, position: bullets.position })
    .from(bullets)
    .where(
      and(
        eq(bullets.documentId, documentId),
        parentId != null ? eq(bullets.parentId, parentId) : isNull(bullets.parentId),
        isNull(bullets.deletedAt)
      )
    )
    .orderBy(asc(bullets.position));

  if (siblings.length === 0) return 1.0;

  if (afterId === null) {
    return siblings[0].position / 2;
  }

  const afterIdx = siblings.findIndex(s => s.id === afterId);
  if (afterIdx === -1) {
    return siblings[siblings.length - 1].position + 1.0;
  }

  const prev = siblings[afterIdx].position;
  const next = siblings[afterIdx + 1]?.position;
  return next !== undefined ? (prev + next) / 2 : prev + 1.0;
}

/**
 * Return all non-deleted bullets for a document, ordered by position.
 */
export async function getDocumentBullets(
  userId: string,
  documentId: string,
  dbInstance: DB = defaultDb
): Promise<Bullet[]> {
  return dbInstance
    .select()
    .from(bullets)
    .where(
      and(
        eq(bullets.documentId, documentId),
        eq(bullets.userId, userId),
        isNull(bullets.deletedAt)
      )
    )
    .orderBy(asc(bullets.position)) as Promise<Bullet[]>;
}

/**
 * Create a new bullet at the computed position, recording an undo event.
 */
export async function createBullet(
  userId: string,
  {
    documentId,
    parentId,
    afterId,
    content = '',
  }: {
    documentId: string;
    parentId?: string | null;
    afterId?: string | null;
    content?: string;
  },
  dbInstance: DB = defaultDb
): Promise<Bullet> {
  const resolvedParentId = parentId ?? null;
  const resolvedAfterId = afterId ?? null;

  const position = await computeBulletInsertPosition(
    dbInstance,
    documentId,
    resolvedParentId,
    resolvedAfterId
  );

  const id = randomUUID();
  let created: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const inserted = await tx
      .insert(bullets)
      .values({
        id,
        userId,
        documentId,
        parentId: resolvedParentId,
        content,
        position,
        isComplete: false,
        isCollapsed: false,
      })
      .returning();

    created = inserted[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'create_bullet',
      { type: 'restore_bullet', id },
      { type: 'restore_bullet_delete', id }
    );
  });

  return created!;
}

/**
 * Indent a bullet: makes it the last child of its previous sibling.
 * No-op if the bullet is already the first sibling.
 */
export async function indentBullet(
  userId: string,
  bulletId: string,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId, { requireActive: true });
  if (!bullet) return null;

  // Get siblings to find the previous sibling
  const siblings = await dbInstance
    .select({ id: bullets.id, position: bullets.position })
    .from(bullets)
    .where(
      and(
        eq(bullets.documentId, bullet.documentId),
        bullet.parentId != null
          ? eq(bullets.parentId, bullet.parentId)
          : isNull(bullets.parentId),
        isNull(bullets.deletedAt)
      )
    )
    .orderBy(asc(bullets.position));

  const index = siblings.findIndex(s => s.id === bulletId);
  if (index === 0) return bullet; // Already first sibling — no-op

  const previousSibling = siblings[index - 1];

  // Find last child of previousSibling to append after it
  const prevSiblingChildren = await dbInstance
    .select({ id: bullets.id, position: bullets.position })
    .from(bullets)
    .where(
      and(
        eq(bullets.documentId, bullet.documentId),
        eq(bullets.parentId, previousSibling.id),
        isNull(bullets.deletedAt)
      )
    )
    .orderBy(asc(bullets.position));

  const lastChildId =
    prevSiblingChildren.length > 0
      ? prevSiblingChildren[prevSiblingChildren.length - 1].id
      : null;

  const appendPosition = await computeBulletInsertPosition(
    dbInstance,
    bullet.documentId,
    previousSibling.id,
    lastChildId
  );

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { parentId: previousSibling.id, position: appendPosition },
    'indent_bullet',
    { type: 'move_bullet', id: bulletId, parentId: previousSibling.id, position: appendPosition },
    { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
  );
}

/**
 * Outdent a bullet: makes it a sibling of its parent, positioned after parent.
 * No-op if the bullet is already at root level.
 */
export async function outdentBullet(
  userId: string,
  bulletId: string,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId, { requireActive: true });
  if (!bullet) return null;
  if (!bullet.parentId) return bullet; // Already root level — no-op

  const parent = await dbInstance.query.bullets.findFirst({
    where: eq(bullets.id, bullet.parentId),
  });
  if (!parent) return bullet;

  const newPosition = await computeBulletInsertPosition(
    dbInstance,
    bullet.documentId,
    parent.parentId,
    parent.id
  );

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { parentId: parent.parentId, position: newPosition },
    'outdent_bullet',
    { type: 'move_bullet', id: bulletId, parentId: parent.parentId, position: newPosition },
    { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
  );
}

/**
 * Load all descendants of a bullet (recursive via parentId chain).
 * Uses a Map for O(n) traversal instead of repeated filtering.
 */
async function getDescendantIds(
  dbInstance: DB,
  bulletId: string,
  documentId: string
): Promise<Set<string>> {
  const all = await dbInstance
    .select({ id: bullets.id, parentId: bullets.parentId })
    .from(bullets)
    .where(and(eq(bullets.documentId, documentId), isNull(bullets.deletedAt)));

  // Build parent→children index for O(n) walk
  const childrenOf = new Map<string | null, string[]>();
  for (const b of all) {
    const key = b.parentId;
    let arr = childrenOf.get(key);
    if (!arr) { arr = []; childrenOf.set(key, arr); }
    arr.push(b.id);
  }

  const result = new Set<string>();
  const queue = [bulletId];
  while (queue.length > 0) {
    const current = queue.shift()!;
    const children = childrenOf.get(current) ?? [];
    for (const childId of children) {
      result.add(childId);
      queue.push(childId);
    }
  }

  return result;
}

/**
 * Move a bullet and all its descendants to a new parent/position.
 * Throws if the move would create a cycle (moving under own descendant).
 */
export async function moveBullet(
  userId: string,
  bulletId: string,
  { newParentId, afterId }: { newParentId: string | null; afterId: string | null },
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId, { requireActive: true });
  if (!bullet) return null;

  if (newParentId !== null) {
    const descendants = await getDescendantIds(dbInstance, bulletId, bullet.documentId);
    if (descendants.has(newParentId)) {
      throw new Error('Cannot move a bullet under one of its own descendants');
    }
  }

  const newPosition = await computeBulletInsertPosition(
    dbInstance,
    bullet.documentId,
    newParentId,
    afterId
  );

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { parentId: newParentId, position: newPosition },
    'move_bullet',
    { type: 'move_bullet', id: bulletId, parentId: newParentId, position: newPosition },
    { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
  );
}

/**
 * Soft-delete a bullet by setting deletedAt = now().
 */
export async function softDeleteBullet(
  userId: string,
  bulletId: string,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId, { requireActive: true });
  if (!bullet) return null;

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { deletedAt: new Date() },
    'delete_bullet',
    { type: 'delete_bullet', id: bulletId },
    { type: 'restore_bullet', id: bulletId }
  );
}

/**
 * Mark or unmark a bullet complete.
 */
export async function markComplete(
  userId: string,
  bulletId: string,
  isComplete: boolean,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId);
  if (!bullet) return null;

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { isComplete },
    'mark_complete',
    { type: 'update_bullet', id: bulletId, fields: { isComplete } },
    { type: 'update_bullet', id: bulletId, fields: { isComplete: bullet.isComplete } }
  );
}

/**
 * Collapse or expand a bullet.
 */
export async function setCollapsed(
  userId: string,
  bulletId: string,
  isCollapsed: boolean,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId);
  if (!bullet) return null;

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { isCollapsed },
    'set_collapsed',
    { type: 'update_bullet', id: bulletId, fields: { isCollapsed } },
    { type: 'update_bullet', id: bulletId, fields: { isCollapsed: bullet.isCollapsed } }
  );
}

/**
 * Patch a bullet's note field.
 */
export async function patchBullet(
  userId: string,
  bulletId: string,
  fields: { note?: string | null },
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await loadBullet(dbInstance, bulletId, userId);
  if (!bullet) return null;

  const note = fields.note !== undefined ? (fields.note ?? null) : undefined;
  if (note === undefined) return bullet; // nothing to update

  return updateWithUndo(
    dbInstance, userId, bulletId,
    { note, updatedAt: new Date() },
    'update_note',
    { type: 'update_bullet', id: bulletId, fields: { note } as unknown as Partial<BulletRow> },
    { type: 'update_bullet', id: bulletId, fields: { note: bullet.note ?? null } as unknown as Partial<BulletRow> }
  );
}
