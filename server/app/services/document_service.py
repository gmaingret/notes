"""Document service — CRUD operations for documents."""

import time
import uuid

import aiosqlite

from app.models.document import DocumentResponse
from app.utils.fractional_index import after, first


def _row_to_doc(row: aiosqlite.Row) -> DocumentResponse:
    return DocumentResponse(
        id=row["id"],
        title=row["title"],
        position=row["position"],
        created_at=row["created_at"],
        updated_at=row["updated_at"],
    )


async def list_documents(db: aiosqlite.Connection) -> list[DocumentResponse]:
    """Return all non-deleted documents ordered by position."""
    rows = await db.execute_fetchall(
        "SELECT id, title, position, created_at, updated_at "
        "FROM documents WHERE deleted_at IS NULL ORDER BY position",
    )
    return [_row_to_doc(r) for r in rows]


async def create_document(
    db: aiosqlite.Connection,
    title: str,
    position: str | None,
) -> DocumentResponse:
    """
    Create a new document.  If position is not supplied, generate one that
    sorts after the current last document.
    """
    if not position:
        rows = await db.execute_fetchall(
            "SELECT position FROM documents WHERE deleted_at IS NULL ORDER BY position DESC LIMIT 1"
        )
        if rows:
            position = after(rows[0]["position"])
        else:
            position = first()

    doc_id = str(uuid.uuid4())
    now = int(time.time() * 1000)

    await db.execute(
        "INSERT INTO documents (id, title, position, created_at, updated_at) "
        "VALUES (?, ?, ?, ?, ?)",
        (doc_id, title, position, now, now),
    )
    await db.commit()

    row = await db.execute_fetchall(
        "SELECT id, title, position, created_at, updated_at FROM documents WHERE id = ?",
        (doc_id,),
    )
    return _row_to_doc(row[0])


async def update_document(
    db: aiosqlite.Connection,
    doc_id: str,
    title: str | None,
    position: str | None,
) -> DocumentResponse | None:
    """
    Rename or reorder a document.  Returns None if the document is not found.
    """
    rows = await db.execute_fetchall(
        "SELECT id, title, position, created_at, updated_at "
        "FROM documents WHERE id = ? AND deleted_at IS NULL",
        (doc_id,),
    )
    if not rows:
        return None

    current = rows[0]
    new_title = title if title is not None else current["title"]
    new_position = position if position is not None else current["position"]
    now = int(time.time() * 1000)

    await db.execute(
        "UPDATE documents SET title = ?, position = ?, updated_at = ? WHERE id = ?",
        (new_title, new_position, now, doc_id),
    )
    await db.commit()

    updated = await db.execute_fetchall(
        "SELECT id, title, position, created_at, updated_at FROM documents WHERE id = ?",
        (doc_id,),
    )
    return _row_to_doc(updated[0])


async def delete_document(db: aiosqlite.Connection, doc_id: str) -> bool:
    """
    Soft-delete a document by setting deleted_at.
    Returns False if the document was not found or already deleted.
    """
    rows = await db.execute_fetchall(
        "SELECT id FROM documents WHERE id = ? AND deleted_at IS NULL",
        (doc_id,),
    )
    if not rows:
        return False

    now = int(time.time() * 1000)
    await db.execute(
        "UPDATE documents SET deleted_at = ? WHERE id = ?",
        (now, doc_id),
    )
    await db.commit()
    return True
