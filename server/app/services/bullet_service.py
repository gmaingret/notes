"""Bullet service — CRUD operations for bullets."""

import time

import aiosqlite

from app.models.bullet import BulletResponse
from app.services.tag_service import sync_tags


def _row_to_bullet(row: aiosqlite.Row) -> BulletResponse:
    return BulletResponse(
        id=row["id"],
        document_id=row["document_id"],
        parent_id=row["parent_id"],
        content=row["content"],
        position=row["position"],
        is_complete=bool(row["is_complete"]),
        created_at=row["created_at"],
        updated_at=row["updated_at"],
    )


async def get_document_bullets(
    db: aiosqlite.Connection,
    document_id: str,
) -> list[BulletResponse] | None:
    """
    Return a flat list of all non-deleted bullets for a document, ordered by position.
    Returns None if the document does not exist.
    """
    doc_rows = await db.execute_fetchall(
        "SELECT id FROM documents WHERE id = ? AND deleted_at IS NULL",
        (document_id,),
    )
    if not doc_rows:
        return None

    rows = await db.execute_fetchall(
        "SELECT id, document_id, parent_id, content, position, is_complete, "
        "created_at, updated_at "
        "FROM bullets "
        "WHERE document_id = ? AND deleted_at IS NULL "
        "ORDER BY position",
        (document_id,),
    )
    return [_row_to_bullet(r) for r in rows]


async def create_bullet(
    db: aiosqlite.Connection,
    bullet_id: str,
    document_id: str,
    parent_id: str | None,
    content: str,
    position: str,
    is_complete: bool,
) -> BulletResponse:
    """
    Create a new bullet.  The bullet_id is client-supplied (UUID v4) to
    support idempotency.  Uses INSERT OR IGNORE so a duplicate call is
    silently a no-op, and then fetches the existing row.
    """
    now = int(time.time() * 1000)

    await db.execute(
        "INSERT OR IGNORE INTO bullets "
        "(id, document_id, parent_id, content, position, is_complete, created_at, updated_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (bullet_id, document_id, parent_id, content, position, int(is_complete), now, now),
    )
    await sync_tags(db, bullet_id, content)
    await db.commit()

    rows = await db.execute_fetchall(
        "SELECT id, document_id, parent_id, content, position, is_complete, "
        "created_at, updated_at FROM bullets WHERE id = ?",
        (bullet_id,),
    )
    return _row_to_bullet(rows[0])


async def update_bullet(
    db: aiosqlite.Connection,
    bullet_id: str,
    content: str | None,
    position: str | None,
    parent_id: str | None,  # None means "don't change"; use a sentinel for explicit null
    is_complete: bool | None,
    clear_parent: bool = False,
) -> BulletResponse | None:
    """
    Update one or more fields of a bullet.

    `clear_parent=True` sets parent_id to NULL (move to root level).
    Returns None if bullet not found.
    """
    rows = await db.execute_fetchall(
        "SELECT id, document_id, parent_id, content, position, is_complete, "
        "created_at, updated_at FROM bullets WHERE id = ? AND deleted_at IS NULL",
        (bullet_id,),
    )
    if not rows:
        return None

    current = rows[0]
    new_content = content if content is not None else current["content"]
    new_position = position if position is not None else current["position"]
    new_is_complete = int(is_complete) if is_complete is not None else current["is_complete"]
    now = int(time.time() * 1000)

    if clear_parent:
        new_parent_id = None
    elif parent_id is not None:
        new_parent_id = parent_id
    else:
        new_parent_id = current["parent_id"]

    await db.execute(
        "UPDATE bullets SET content = ?, position = ?, parent_id = ?, "
        "is_complete = ?, updated_at = ? WHERE id = ?",
        (new_content, new_position, new_parent_id, new_is_complete, now, bullet_id),
    )
    await sync_tags(db, bullet_id, new_content)
    await db.commit()

    updated = await db.execute_fetchall(
        "SELECT id, document_id, parent_id, content, position, is_complete, "
        "created_at, updated_at FROM bullets WHERE id = ?",
        (bullet_id,),
    )
    return _row_to_bullet(updated[0])


async def delete_bullet(db: aiosqlite.Connection, bullet_id: str) -> bool:
    """
    Soft-delete a bullet and cascade to all descendants by recursively
    soft-deleting children.  Returns False if the bullet was not found.
    """
    rows = await db.execute_fetchall(
        "SELECT id FROM bullets WHERE id = ? AND deleted_at IS NULL",
        (bullet_id,),
    )
    if not rows:
        return False

    now = int(time.time() * 1000)
    await _soft_delete_recursive(db, bullet_id, now)
    await db.commit()
    return True


async def _soft_delete_recursive(
    db: aiosqlite.Connection, bullet_id: str, now: int
) -> None:
    """Recursively soft-delete a bullet and all its non-deleted descendants."""
    # Soft-delete the bullet itself.
    await db.execute(
        "UPDATE bullets SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL",
        (now, bullet_id),
    )

    # Find direct children and recurse.
    children = await db.execute_fetchall(
        "SELECT id FROM bullets WHERE parent_id = ? AND deleted_at IS NULL",
        (bullet_id,),
    )
    for child in children:
        await _soft_delete_recursive(db, child["id"], now)
