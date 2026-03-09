import { db } from '../../db/index.js';
import { bookmarks, bullets, documents } from '../../db/schema.js';
import { eq, and, isNull } from 'drizzle-orm';

/**
 * Add a bookmark for a bullet. Idempotent — silently ignores duplicate (userId, bulletId).
 */
export async function addBookmark(userId: string, bulletId: string): Promise<void> {
  await db
    .insert(bookmarks)
    .values({ userId, bulletId })
    .onConflictDoNothing();
}

/**
 * Remove a bookmark. No-op if the bookmark doesn't exist.
 */
export async function removeBookmark(userId: string, bulletId: string): Promise<void> {
  await db
    .delete(bookmarks)
    .where(and(eq(bookmarks.userId, userId), eq(bookmarks.bulletId, bulletId)));
}

/**
 * Return all bookmarked bullets for a user, joined with bullet content and document title.
 * Excludes soft-deleted bullets.
 */
export async function getUserBookmarks(
  userId: string
): Promise<Array<{ id: string; content: string; documentId: string; documentTitle: string }>> {
  return db
    .select({
      id: bullets.id,
      content: bullets.content,
      documentId: bullets.documentId,
      documentTitle: documents.title,
    })
    .from(bookmarks)
    .innerJoin(bullets, eq(bookmarks.bulletId, bullets.id))
    .innerJoin(documents, eq(bullets.documentId, documents.id))
    .where(and(eq(bookmarks.userId, userId), isNull(bullets.deletedAt)));
}
