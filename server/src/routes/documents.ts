import { Router } from 'express';
import { z } from 'zod';
import { db } from '../../db/index.js';
import { documents } from '../../db/schema.js';
import { eq, and, asc } from 'drizzle-orm';
import { requireAuth } from '../middleware/auth.js';
import {
  computeDocumentInsertPosition,
  getDocumentWithBullets,
  getAllDocumentsWithBullets,
  renderDocumentAsMarkdown,
} from '../services/documentService.js';
import archiver from 'archiver';

export const documentsRouter = Router();

// All routes require auth
documentsRouter.use(requireAuth);

// DOC-05: List documents sorted by position
documentsRouter.get('/', async (req, res) => {
  const user = req.user as { id: string };
  const docs = await db
    .select()
    .from(documents)
    .where(eq(documents.userId, user.id))
    .orderBy(asc(documents.position));
  return res.json(docs);
});

// DOC-07: Export all documents as ZIP — MUST come before /:id routes to avoid param conflict
documentsRouter.get('/export-all', async (req, res) => {
  const user = req.user as { id: string };
  const docs = await getAllDocumentsWithBullets(db, user.id);

  res.setHeader('Content-Type', 'application/zip');
  res.setHeader('Content-Disposition', 'attachment; filename="notes-export.zip"');

  const archive = archiver('zip', { zlib: { level: 6 } });
  archive.pipe(res);

  for (const doc of docs) {
    const md = renderDocumentAsMarkdown(doc);
    // Sanitize filename: replace characters invalid in filenames
    const filename = doc.title.replace(/[<>:"/\|?*]/g, '_') || 'Untitled';
    archive.append(md, { name: `${filename}.md` });
  }

  await archive.finalize();
});

// DOC-01: Create document
documentsRouter.post('/', async (req, res) => {
  const user = req.user as { id: string };
  const title = z.string().min(1).max(200).optional().parse(req.body.title) ?? 'Untitled';

  // Place at end of list
  const existing = await db
    .select({ position: documents.position })
    .from(documents)
    .where(eq(documents.userId, user.id))
    .orderBy(asc(documents.position));
  const position = existing.length === 0 ? 1.0 : existing[existing.length - 1].position + 1.0;

  const [doc] = await db
    .insert(documents)
    .values({ userId: user.id, title, position })
    .returning();

  return res.status(201).json(doc);
});

// DOC-02: Rename document
documentsRouter.patch('/:id', async (req, res) => {
  const user = req.user as { id: string };
  const title = z.string().min(1).max(200).parse(req.body.title);

  const existing = await db.query.documents.findFirst({
    where: eq(documents.id, req.params.id),
  });
  if (!existing) return res.status(404).json({ error: 'Not found' });
  if (existing.userId !== user.id) return res.status(403).json({ error: 'Forbidden' });

  const [updated] = await db
    .update(documents)
    .set({ title, updatedAt: new Date() })
    .where(eq(documents.id, req.params.id))
    .returning();

  return res.json(updated);
});

// DOC-04: Reorder — client sends afterId (UUID or null), server computes FLOAT8 midpoint
documentsRouter.patch('/:id/position', async (req, res) => {
  const user = req.user as { id: string };
  const afterIdResult = z.string().uuid().nullable().safeParse(req.body.afterId ?? null);
  if (!afterIdResult.success) return res.status(400).json({ error: 'afterId must be a UUID or null' });
  const afterId = afterIdResult.data;

  const existing = await db.query.documents.findFirst({
    where: eq(documents.id, req.params.id),
  });
  if (!existing) return res.status(404).json({ error: 'Not found' });
  if (existing.userId !== user.id) return res.status(403).json({ error: 'Forbidden' });

  const position = await computeDocumentInsertPosition(db, user.id, afterId);

  const [updated] = await db
    .update(documents)
    .set({ position, updatedAt: new Date() })
    .where(eq(documents.id, req.params.id))
    .returning();

  return res.json(updated);
});

// DOC-05: Track last opened
documentsRouter.post('/:id/open', async (req, res) => {
  const user = req.user as { id: string };

  const existing = await db.query.documents.findFirst({
    where: and(eq(documents.id, req.params.id), eq(documents.userId, user.id)),
  });
  if (!existing) return res.status(404).json({ error: 'Not found' });

  await db
    .update(documents)
    .set({ lastOpenedAt: new Date() })
    .where(eq(documents.id, req.params.id));

  return res.status(204).send();
});

// DOC-03: Delete document
documentsRouter.delete('/:id', async (req, res) => {
  const user = req.user as { id: string };

  const existing = await db.query.documents.findFirst({
    where: eq(documents.id, req.params.id),
  });
  if (!existing) return res.status(404).json({ error: 'Not found' });
  if (existing.userId !== user.id) return res.status(403).json({ error: 'Forbidden' });

  await db.delete(documents).where(eq(documents.id, req.params.id));

  return res.status(204).send();
});

// DOC-06: Export single document as Markdown
documentsRouter.get('/:id/export', async (req, res) => {
  const user = req.user as { id: string };
  const doc = await getDocumentWithBullets(db, req.params.id, user.id);

  if (!doc) return res.status(404).json({ error: 'Not found' });

  const markdown = renderDocumentAsMarkdown(doc);
  const filename = doc.title.replace(/[<>:"/\|?*]/g, '_') || 'Untitled';

  res.setHeader('Content-Type', 'text/markdown; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="${filename}.md"`);
  return res.send(markdown);
});
