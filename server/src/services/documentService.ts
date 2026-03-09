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
      lines.push(`${indent}- ${bullet.content}`);
      renderBullets(bullet.id, depth + 1);
    }
  }

  renderBullets(null, 0);
  return lines.join('\n');
}
