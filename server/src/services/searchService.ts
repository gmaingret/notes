import { db } from '../../db/index.js';
import { bullets, documents } from '../../db/schema.js';
import { eq, and, isNull, ilike } from 'drizzle-orm';

/**
 * Full-text search across a user's non-deleted bullets using case-insensitive ILIKE.
 * Strips chip prefixes (#, @, !) before matching so "#milk" searches for "milk".
 * Returns up to 50 results joined with document title.
 */
export async function searchBullets(
  userId: string,
  query: string
): Promise<Array<{ id: string; content: string; documentId: string; documentTitle: string }>> {
  const normalized = query.replace(/^[#@!]+/, '').trim();
  if (normalized === '') return [];

  const pattern = `%${normalized}%`;

  return db
    .select({
      id: bullets.id,
      content: bullets.content,
      documentId: bullets.documentId,
      documentTitle: documents.title,
    })
    .from(bullets)
    .innerJoin(documents, eq(bullets.documentId, documents.id))
    .where(and(eq(bullets.userId, userId), isNull(bullets.deletedAt), ilike(bullets.content, pattern)))
    .limit(50);
}
