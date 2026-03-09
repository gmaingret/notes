import { Router } from 'express';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth.js';
import {
  getDocumentBullets,
  createBullet,
  indentBullet,
  outdentBullet,
  moveBullet,
  softDeleteBullet,
  markComplete,
  setCollapsed,
} from '../services/bulletService.js';
import { recordUndoEvent } from '../services/undoService.js';
import { db } from '../../db/index.js';
import { bullets } from '../../db/schema.js';
import { eq, and, isNull } from 'drizzle-orm';

export const bulletsRouter = Router();

// All routes require auth
bulletsRouter.use(requireAuth);

// BULL-01: Get all bullets for a document
// GET /api/bullets/documents/:docId/bullets
bulletsRouter.get('/documents/:docId/bullets', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const result = await getDocumentBullets(user.id, req.params.docId);
    return res.json(result);
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-02: Create a bullet
// POST /api/bullets
const createBulletSchema = z.object({
  documentId: z.string().uuid(),
  parentId: z.string().uuid().nullable().optional(),
  afterId: z.string().uuid().nullable().optional(),
  content: z.string().default(''),
});

bulletsRouter.post('/', async (req, res) => {
  const user = req.user as { id: string };
  const result = createBulletSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  try {
    const bullet = await createBullet(user.id, result.data);
    return res.status(201).json(bullet);
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-03/11/12/13: Patch bullet (content, isComplete, isCollapsed)
// PATCH /api/bullets/:id
const patchBulletSchema = z.object({
  content: z.string().optional(),
  isComplete: z.boolean().optional(),
  isCollapsed: z.boolean().optional(),
});

bulletsRouter.patch('/:id', async (req, res) => {
  const user = req.user as { id: string };
  const result = patchBulletSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  const { content, isComplete, isCollapsed } = result.data;

  try {
    if (isCollapsed !== undefined) {
      const bullet = await setCollapsed(user.id, req.params.id, isCollapsed);
      if (!bullet) return res.status(404).json({ error: 'Not found' });
      return res.json(bullet);
    }

    if (isComplete !== undefined) {
      const bullet = await markComplete(user.id, req.params.id, isComplete);
      if (!bullet) return res.status(404).json({ error: 'Not found' });
      return res.json(bullet);
    }

    if (content !== undefined) {
      // Direct DB update for content — no undo recording at route level
      // (content undo handled via POST /:id/undo-checkpoint after debounce)
      const rows = await db
        .update(bullets)
        .set({ content, updatedAt: new Date() })
        .where(and(eq(bullets.id, req.params.id), eq(bullets.userId, user.id)))
        .returning();
      if (rows.length === 0) return res.status(404).json({ error: 'Not found' });
      return res.json(rows[0]);
    }

    return res.status(400).json({ error: 'No valid field to update' });
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-05: Soft delete a bullet
// DELETE /api/bullets/:id
bulletsRouter.delete('/:id', async (req, res) => {
  const user = req.user as { id: string };
  try {
    await softDeleteBullet(user.id, req.params.id);
    return res.json({ ok: true });
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-06: Indent bullet
// POST /api/bullets/:id/indent
bulletsRouter.post('/:id/indent', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const bullet = await indentBullet(user.id, req.params.id);
    if (!bullet) return res.status(404).json({ error: 'Not found' });
    return res.json(bullet);
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-06: Outdent bullet
// POST /api/bullets/:id/outdent
bulletsRouter.post('/:id/outdent', async (req, res) => {
  const user = req.user as { id: string };
  try {
    const bullet = await outdentBullet(user.id, req.params.id);
    if (!bullet) return res.status(404).json({ error: 'Not found' });
    return res.json(bullet);
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-04: Move bullet
// POST /api/bullets/:id/move
const moveBulletSchema = z.object({
  newParentId: z.string().uuid().nullable(),
  afterId: z.string().uuid().nullable().optional(),
});

bulletsRouter.post('/:id/move', async (req, res) => {
  const user = req.user as { id: string };
  const result = moveBulletSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  try {
    const bullet = await moveBullet(user.id, req.params.id, {
      newParentId: result.data.newParentId,
      afterId: result.data.afterId ?? null,
    });
    if (!bullet) return res.status(404).json({ error: 'Not found' });
    return res.json(bullet);
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('cannot move') || msg.includes('descendant')) {
      return res.status(400).json({ error: 'Cannot move a bullet under its own descendant' });
    }
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});

// BULL-14: Hard-delete all completed bullets in a document (no undo)
// DELETE /api/bullets/documents/:docId/completed
// NOTE: Must be defined BEFORE /:id to avoid Express param collision
bulletsRouter.delete('/documents/:docId/completed', async (req, res) => {
  const user = req.user as { id: string };
  try {
    await db
      .delete(bullets)
      .where(
        and(
          eq(bullets.documentId, req.params.docId),
          eq(bullets.userId, user.id),
          eq(bullets.isComplete, true),
          isNull(bullets.deletedAt)
        )
      );
    return res.json({ ok: true });
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    throw err;
  }
});

// BULL-15: Undo checkpoint for text content changes (called by client after debounce)
// POST /api/bullets/:id/undo-checkpoint
const undoCheckpointSchema = z.object({
  content: z.string(),
  previousContent: z.string().optional(),
});

bulletsRouter.post('/:id/undo-checkpoint', async (req, res) => {
  const user = req.user as { id: string };
  const result = undoCheckpointSchema.safeParse(req.body);
  if (!result.success) {
    return res.status(400).json({ errors: result.error.flatten().fieldErrors });
  }

  const { content, previousContent } = result.data;
  const bulletId = req.params.id;

  try {
    // Record undo event for text change — standalone (not inside a bullet mutation tx)
    await recordUndoEvent(
      db,
      user.id,
      'update_content',
      { type: 'update_bullet', id: bulletId, fields: { content } },
      { type: 'update_bullet', id: bulletId, fields: { content: previousContent ?? '' } }
    );
    return res.json({ ok: true });
  } catch (err) {
    const msg = err instanceof Error ? err.message.toLowerCase() : '';
    if (msg.includes('not found')) return res.status(404).json({ error: 'Not found' });
    if (msg.includes('unauthorized')) return res.status(403).json({ error: 'Forbidden' });
    throw err;
  }
});
