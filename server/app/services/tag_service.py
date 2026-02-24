"""
Tag service — extract #tags from bullet content and sync to the database.

Tags are stored lowercase without the leading '#'.  URLs are excluded so
that http://example.com does not produce a spurious tag 'example'.
"""

import re
import uuid

import aiosqlite

# Match #tag where:
#  - preceded by start-of-string or whitespace (not a URL slash or other word char)
#  - followed by word characters (letters, digits, hyphens, underscores)
#  - not immediately preceded by '/' or ':' (avoids URL fragments)
_TAG_RE = re.compile(r"(?<![/:\w])#([\w][\w-]*)", re.UNICODE)


def extract_tags(content: str) -> set[str]:
    """
    Return the set of normalised tag names (lowercase, no '#') found in content.

    False positives on URLs (e.g. http://example.com/#anchor) are avoided by
    the negative look-behind for '/' and ':'.
    """
    return {m.group(1).lower() for m in _TAG_RE.finditer(content)}


async def sync_tags(db: aiosqlite.Connection, bullet_id: str, content: str) -> None:
    """
    Synchronise the tags and bullet_tags tables for a bullet after an upsert.

    1. Extract new tag names from content.
    2. Ensure each tag exists in `tags` (insert if missing).
    3. Replace all `bullet_tags` rows for this bullet.
    """
    new_tags = extract_tags(content)

    # Upsert each tag into the tags table.
    for tag_name in new_tags:
        existing = await db.execute_fetchall(
            "SELECT id FROM tags WHERE name = ?", (tag_name,)
        )
        if existing:
            tag_id = existing[0]["id"]
        else:
            tag_id = str(uuid.uuid4())
            await db.execute(
                "INSERT OR IGNORE INTO tags (id, name) VALUES (?, ?)",
                (tag_id, tag_name),
            )

    # Get the IDs for all extracted tags.
    tag_ids: list[str] = []
    for tag_name in new_tags:
        rows = await db.execute_fetchall("SELECT id FROM tags WHERE name = ?", (tag_name,))
        if rows:
            tag_ids.append(rows[0]["id"])

    # Remove stale bullet_tags for this bullet.
    await db.execute("DELETE FROM bullet_tags WHERE bullet_id = ?", (bullet_id,))

    # Insert fresh bullet_tags rows.
    for tag_id in tag_ids:
        await db.execute(
            "INSERT OR IGNORE INTO bullet_tags (bullet_id, tag_id) VALUES (?, ?)",
            (bullet_id, tag_id),
        )
