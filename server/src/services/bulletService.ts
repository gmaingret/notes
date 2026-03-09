import { db as defaultDb } from '../../db/index.js';
import { bullets } from '../../db/schema.js';
import { eq, and, isNull, asc } from 'drizzle-orm';
import { randomUUID } from 'node:crypto';
import { recordUndoEvent } from './undoService.js';
import type { DB } from '../../db/index.js';

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
  deletedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
};

/**
 * Compute FLOAT8 midpoint insert position for a bullet among siblings.
 * Mirrors computeDocumentInsertPosition from documentService.ts.
 *
 * @param dbInstance   Drizzle DB or transaction handle
 * @param documentId   Document containing the bullet
 * @param parentId     Parent bullet id (null = root level)
 * @param afterId      Insert after this sibling id (null = insert at beginning)
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
    // Insert before the first sibling
    return siblings[0].position / 2;
  }

  const afterIdx = siblings.findIndex(s => s.id === afterId);
  if (afterIdx === -1) {
    // afterId not found among siblings — append at end
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
      // forwardOp: re-create this bullet (if redo after an undo of the create)
      { type: 'restore_bullet', id },
      // inverseOp: soft-delete this bullet (undo the creation)
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
  // Load the target bullet
  const bullet = await dbInstance.query.bullets.findFirst({
    where: and(eq(bullets.id, bulletId), eq(bullets.userId, userId), isNull(bullets.deletedAt)),
  });
  if (!bullet) return null;

  // Get all siblings (bullets with same parent, same document)
  const siblings = await dbInstance
    .select()
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
  if (index === 0) return bullet as Bullet; // Already first sibling — no-op

  const previousSibling = siblings[index - 1];

  // Compute position as last child of previousSibling
  const newPosition = await computeBulletInsertPosition(
    dbInstance,
    bullet.documentId,
    previousSibling.id,
    null // afterId=null means find the tail position among previousSibling's children
  );

  // Actually append as last child — we need to pass the last child id, not null
  // Let's get the last child of previousSibling and pass its id as afterId
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

  let updated: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set({ parentId: previousSibling.id, position: appendPosition })
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'indent_bullet',
      // forwardOp: re-indent (move to new parent)
      { type: 'move_bullet', id: bulletId, parentId: previousSibling.id, position: appendPosition },
      // inverseOp: restore original parent + position
      { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
    );
  });

  return updated ?? null;
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
  const bullet = await dbInstance.query.bullets.findFirst({
    where: and(eq(bullets.id, bulletId), eq(bullets.userId, userId), isNull(bullets.deletedAt)),
  });
  if (!bullet) return null;
  if (!bullet.parentId) return bullet as Bullet; // Already root level — no-op

  // Load the parent bullet
  const parent = await dbInstance.query.bullets.findFirst({
    where: eq(bullets.id, bullet.parentId),
  });
  if (!parent) return bullet as Bullet;

  // New position: after the parent among parent's siblings
  const newPosition = await computeBulletInsertPosition(
    dbInstance,
    bullet.documentId,
    parent.parentId,
    parent.id
  );

  let updated: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set({ parentId: parent.parentId, position: newPosition })
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'outdent_bullet',
      { type: 'move_bullet', id: bulletId, parentId: parent.parentId, position: newPosition },
      { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
    );
  });

  return updated ?? null;
}

/**
 * Load all descendants of a bullet (recursive via parentId chain).
 * Returns Set of descendant ids.
 */
async function getDescendantIds(
  dbInstance: DB,
  bulletId: string,
  documentId: string
): Promise<Set<string>> {
  // Load all bullets in document and walk the tree client-side
  const all = await dbInstance
    .select({ id: bullets.id, parentId: bullets.parentId })
    .from(bullets)
    .where(and(eq(bullets.documentId, documentId), isNull(bullets.deletedAt)));

  const result = new Set<string>();
  const queue = [bulletId];

  while (queue.length > 0) {
    const current = queue.shift()!;
    const children = all.filter(b => b.parentId === current);
    for (const child of children) {
      result.add(child.id);
      queue.push(child.id);
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
  const bullet = await dbInstance.query.bullets.findFirst({
    where: and(eq(bullets.id, bulletId), eq(bullets.userId, userId), isNull(bullets.deletedAt)),
  });
  if (!bullet) return null;

  // Cycle guard: cannot move under own descendant
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

  let updated: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set({ parentId: newParentId, position: newPosition })
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'move_bullet',
      { type: 'move_bullet', id: bulletId, parentId: newParentId, position: newPosition },
      { type: 'move_bullet', id: bulletId, parentId: bullet.parentId, position: bullet.position }
    );
  });

  return updated ?? null;
}

/**
 * Soft-delete a bullet by setting deletedAt = now().
 * Undo restores it (sets deletedAt = null).
 */
export async function softDeleteBullet(
  userId: string,
  bulletId: string,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await dbInstance.query.bullets.findFirst({
    where: and(eq(bullets.id, bulletId), eq(bullets.userId, userId), isNull(bullets.deletedAt)),
  });
  if (!bullet) return null;

  let updated: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set({ deletedAt: new Date() })
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'delete_bullet',
      { type: 'delete_bullet', id: bulletId },
      { type: 'restore_bullet', id: bulletId }
    );
  });

  return updated ?? null;
}

/**
 * Mark or unmark a bullet complete.
 * No undo recording per BULL-12 — markComplete is not listed in undo requirements.
 */
export async function markComplete(
  userId: string,
  bulletId: string,
  isComplete: boolean,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const rows = await dbInstance
    .update(bullets)
    .set({ isComplete })
    .where(and(eq(bullets.id, bulletId), eq(bullets.userId, userId)))
    .returning();

  return (rows[0] as Bullet) ?? null;
}

/**
 * Collapse or expand a bullet.
 * Records undo event — collapse toggle is a structural operation per CONTEXT.md.
 */
export async function setCollapsed(
  userId: string,
  bulletId: string,
  isCollapsed: boolean,
  dbInstance: DB = defaultDb
): Promise<Bullet | null> {
  const bullet = await dbInstance.query.bullets.findFirst({
    where: and(eq(bullets.id, bulletId), eq(bullets.userId, userId)),
  });
  if (!bullet) return null;

  let updated: Bullet | undefined;

  await dbInstance.transaction(async (tx) => {
    const rows = await tx
      .update(bullets)
      .set({ isCollapsed })
      .where(eq(bullets.id, bulletId))
      .returning();
    updated = rows[0] as Bullet;

    await recordUndoEvent(
      tx,
      userId,
      'set_collapsed',
      { type: 'update_bullet', id: bulletId, fields: { isCollapsed } },
      { type: 'update_bullet', id: bulletId, fields: { isCollapsed: bullet.isCollapsed } }
    );
  });

  return updated ?? null;
}
