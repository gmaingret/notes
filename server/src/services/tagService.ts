import { db } from '../../db/index.js';
import { bullets, documents } from '../../db/schema.js';
import { eq, and, isNull, ilike, sql } from 'drizzle-orm';

export type ChipType = 'tag' | 'mention' | 'date';

export interface TagCount {
  chipType: ChipType;
  value: string;
  count: number;
}

/**
 * Return all tag/mention/date chip counts for a user across all non-deleted bullets.
 * Uses PostgreSQL regexp_matches to extract chips from content.
 */
export async function getTagCounts(userId: string): Promise<TagCount[]> {
  const result = await db.execute(sql`
    SELECT
      type,
      value,
      COUNT(*) as count
    FROM (
      SELECT 'tag' as type, m[1] as value
      FROM bullets, regexp_matches(content, '(?<![="])#([a-zA-Z0-9_]+)', 'g') AS m
      WHERE user_id = ${userId}::uuid AND deleted_at IS NULL
      UNION ALL
      SELECT 'mention' as type, m[1] as value
      FROM bullets, regexp_matches(content, '(?<![="])@([a-zA-Z0-9_]+)', 'g') AS m
      WHERE user_id = ${userId}::uuid AND deleted_at IS NULL
      UNION ALL
      SELECT 'date' as type, m[1] as value
      FROM bullets, regexp_matches(content, '!!\\[(\\d{4}-\\d{2}-\\d{2})\\]', 'g') AS m
      WHERE user_id = ${userId}::uuid AND deleted_at IS NULL
    ) subq
    GROUP BY type, value
    ORDER BY count DESC
  `);

  return (result.rows as Array<{ type: string; value: string; count: string }>).map(row => ({
    chipType: row.type as ChipType,
    value: row.value,
    count: Number(row.count),
  }));
}

/**
 * Return all non-deleted bullets whose content matches the given chip.
 * Joins with documents to include document title.
 */
export async function getBulletsForTag(
  userId: string,
  chipType: ChipType,
  value: string
): Promise<Array<{ id: string; content: string; documentId: string; documentTitle: string }>> {
  let pattern: string;
  if (chipType === 'tag') {
    pattern = `%#${value}%`;
  } else if (chipType === 'mention') {
    pattern = `%@${value}%`;
  } else {
    pattern = `%!![${value}]%`;
  }

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
    .limit(100);
}
