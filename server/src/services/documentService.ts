import { db as defaultDb } from '../../db/index.js';
import { documents, bullets } from '../../db/schema.js';
import { eq, and, isNull, asc } from 'drizzle-orm';
import type { DB } from '../../db/index.js';

// FLOAT8 midpoint positioning for document sidebar order
// Client passes afterId (UUID of the document to insert after), not the position float
// Position computed server-side inside transaction to prevent race conditions
export async function computeDocumentInsertPosition(
  dbInstance: DB,
  userId: string,
  afterId: string | null // null = insert at beginning
): Promise<number> {
  const siblings = await dbInstance
    .select({ id: documents.id, position: documents.position })
    .from(documents)
    .where(eq(documents.userId, userId))
    .orderBy(asc(documents.position));

  if (siblings.length === 0) return 1.0;

  if (afterId === null) {
    // Insert before the first item
    return siblings[0].position / 2;
  }

  const afterIdx = siblings.findIndex(s => s.id === afterId);
  if (afterIdx === -1) return (siblings[siblings.length - 1].position) + 1.0;

  const prev = siblings[afterIdx].position;
  const next = siblings[afterIdx + 1]?.position;

  return next !== undefined ? (prev + next) / 2 : prev + 1.0;
}

// Document with its bullets (for export)
export type BulletRow = {
  id: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  deletedAt: Date | null;
};

export type DocWithBullets = {
  id: string;
  title: string;
  bullets: BulletRow[];
};

export async function getDocumentWithBullets(
  dbInstance: DB,
  docId: string,
  userId: string
): Promise<DocWithBullets | null> {
  const doc = await dbInstance.query.documents.findFirst({
    where: and(eq(documents.id, docId), eq(documents.userId, userId)),
  });
  if (!doc) return null;

  const bulletRows = await dbInstance
    .select({
      id: bullets.id,
      parentId: bullets.parentId,
      content: bullets.content,
      position: bullets.position,
      isComplete: bullets.isComplete,
      deletedAt: bullets.deletedAt,
    })
    .from(bullets)
    .where(and(eq(bullets.documentId, docId), isNull(bullets.deletedAt)))
    .orderBy(asc(bullets.position));

  return { id: doc.id, title: doc.title, bullets: bulletRows };
}

export async function getAllDocumentsWithBullets(
  dbInstance: DB,
  userId: string
): Promise<DocWithBullets[]> {
  const docs = await dbInstance
    .select()
    .from(documents)
    .where(eq(documents.userId, userId))
    .orderBy(asc(documents.position));

  return Promise.all(
    docs.map(doc => getDocumentWithBullets(dbInstance, doc.id, userId).then(d => d!))
  );
}

// Render document as Markdown with 2-space indent per level (locked UX decision)
export function renderDocumentAsMarkdown(doc: DocWithBullets): string {
  const lines: string[] = [`# ${doc.title}`, ''];

  function renderBullets(parentId: string | null, depth: number) {
    const children = doc.bullets
      .filter(b => b.parentId === parentId && !b.deletedAt)
      .sort((a, b) => a.position - b.position);

    for (const bullet of children) {
      const indent = '  '.repeat(depth); // 2-space per level — locked UX decision from CONTEXT.md
      const text = bullet.isComplete ? `~~${bullet.content}~~` : bullet.content;
      lines.push(`${indent}- ${text}`);
      renderBullets(bullet.id, depth + 1);
    }
  }

  renderBullets(null, 0);
  return lines.join('\n');
}

type ParsedBullet = { content: string; depth: number; isComplete: boolean };

/**
 * Parse a markdown string (exported by renderDocumentAsMarkdown) back into
 * a title and a flat list of bullets with depth info.
 */
export function parseMarkdownImport(markdown: string): { title: string; bullets: ParsedBullet[] } {
  const lines = markdown.split(/\r?\n/);
  let title = 'Imported';

  // First non-empty line starting with # is the title
  const titleIdx = lines.findIndex(l => /^#\s+/.test(l));
  if (titleIdx !== -1) {
    title = lines[titleIdx].replace(/^#\s+/, '').trim() || 'Imported';
  }

  const bulletLines = lines.filter(l => /^\s*- /.test(l));
  const parsedBullets: ParsedBullet[] = bulletLines.map(line => {
    const match = line.match(/^(\s*)- (.*)$/);
    if (!match) return { content: '', depth: 0, isComplete: false };
    const spaces = match[1].length;
    const depth = Math.floor(spaces / 2); // 2-space indent per level
    let content = match[2];
    // Detect ~~strikethrough~~ for completed bullets
    const isComplete = /^~~.*~~$/.test(content);
    if (isComplete) {
      content = content.slice(2, -2); // strip ~~ wrapper
    }
    return { content, depth, isComplete };
  });

  return { title, bullets: parsedBullets };
}

/**
 * Import a markdown document: create a document and its bullet tree.
 */
export async function importDocument(
  dbInstance: DB,
  userId: string,
  markdown: string
): Promise<{ id: string; title: string }> {
  const { title, bullets: parsed } = parseMarkdownImport(markdown);

  // Compute position at end of document list
  const existing = await dbInstance
    .select({ position: documents.position })
    .from(documents)
    .where(eq(documents.userId, userId))
    .orderBy(asc(documents.position));
  const position = existing.length === 0 ? 1.0 : existing[existing.length - 1].position + 1.0;

  const { randomUUID } = await import('node:crypto');
  const docId = randomUUID();

  await dbInstance.transaction(async (tx) => {
    await tx.insert(documents).values({ id: docId, userId, title, position });

    if (parsed.length === 0) return;

    // Walk parsed bullets and maintain a parent stack by depth
    const parentStack: { id: string; depth: number }[] = [];
    let posCounter = 0;

    for (const item of parsed) {
      // Pop stack to find the parent at depth - 1
      while (parentStack.length > 0 && parentStack[parentStack.length - 1].depth >= item.depth) {
        parentStack.pop();
      }
      const parentId = parentStack.length > 0 ? parentStack[parentStack.length - 1].id : null;

      const bulletId = randomUUID();
      posCounter += 1;

      await tx.insert(bullets).values({
        id: bulletId,
        userId,
        documentId: docId,
        parentId,
        content: item.content,
        position: posCounter,
        isComplete: item.isComplete,
        isCollapsed: false,
      });

      parentStack.push({ id: bulletId, depth: item.depth });
    }
  });

  return { id: docId, title };
}
