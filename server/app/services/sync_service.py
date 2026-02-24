"""
Sync service — apply client operations to the server database.

Phase 1: push-only sync.
- Operations are applied in client_timestamp order.
- Duplicate operation IDs (already in sync_operations table) are silently skipped.
- server_delta is always empty (full delta pull is Phase 3).
"""

import json
import time

import aiosqlite

from app.models.sync import SyncOperation, SyncRequest, SyncResponse


async def process_sync(db: aiosqlite.Connection, request: SyncRequest) -> SyncResponse:
    """
    Apply all client operations (in client_timestamp order) and return a
    SyncResponse.  Duplicate operation IDs are idempotently re-included in
    the `applied` list without re-applying the operation.
    """
    server_timestamp = int(time.time() * 1000)
    applied: list[str] = []

    # Sort operations by client_timestamp ascending (last-write-wins order).
    sorted_ops = sorted(request.operations, key=lambda op: op.client_timestamp)

    for op in sorted_ops:
        already_applied = await _is_duplicate(db, op.id)
        if already_applied:
            # Idempotent: include in applied list but do not re-apply.
            applied.append(op.id)
            continue

        await _record_and_apply(db, request.device_id, op, server_timestamp)
        applied.append(op.id)

    await db.commit()

    return SyncResponse(
        server_timestamp=server_timestamp,
        applied=applied,
        server_delta=[],
    )


async def _is_duplicate(db: aiosqlite.Connection, operation_id: str) -> bool:
    """Return True if this operation_id is already in the sync_operations table."""
    rows = await db.execute_fetchall(
        "SELECT id FROM sync_operations WHERE id = ?",
        (operation_id,),
    )
    return len(rows) > 0


async def _record_and_apply(
    db: aiosqlite.Connection,
    device_id: str,
    op: SyncOperation,
    server_timestamp: int,
) -> None:
    """
    Record the operation in sync_operations and attempt to apply it.

    Recording happens first so that even if application fails (e.g. entity
    already deleted), the operation is not re-applied on future syncs.
    """
    payload_json = json.dumps(op.payload)

    await db.execute(
        """
        INSERT OR IGNORE INTO sync_operations
            (id, device_id, operation_type, entity_type, entity_id,
             payload, client_timestamp, server_timestamp, applied)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
        """,
        (
            op.id,
            device_id,
            op.operation_type,
            op.entity_type,
            op.entity_id,
            payload_json,
            op.client_timestamp,
            server_timestamp,
        ),
    )

    # Best-effort entity application.  Errors are swallowed so a bad payload
    # does not break the entire sync batch.
    try:
        await _apply_operation(db, op, server_timestamp)
    except Exception:
        # In Phase 1 we just absorb failures; Phase 3 will add error reporting.
        pass


async def _apply_operation(
    db: aiosqlite.Connection,
    op: SyncOperation,
    server_timestamp: int,
) -> None:
    """Apply a single sync operation to the appropriate entity table."""
    if op.entity_type == "document":
        await _apply_document_op(db, op, server_timestamp)
    elif op.entity_type == "bullet":
        await _apply_bullet_op(db, op, server_timestamp)
    # attachment ops are no-ops in Phase 1


async def _apply_document_op(
    db: aiosqlite.Connection,
    op: SyncOperation,
    server_timestamp: int,
) -> None:
    p = op.payload
    if op.operation_type == "upsert":
        await db.execute(
            """
            INSERT INTO documents (id, title, position, created_at, updated_at)
            VALUES (:id, :title, :position, :created_at, :updated_at)
            ON CONFLICT(id) DO UPDATE SET
                title      = excluded.title,
                position   = excluded.position,
                updated_at = excluded.updated_at
            WHERE excluded.updated_at >= documents.updated_at
            """,
            {
                "id": op.entity_id,
                "title": p.get("title", "Untitled"),
                "position": p.get("position", "n"),
                "created_at": p.get("created_at", server_timestamp),
                "updated_at": p.get("updated_at", server_timestamp),
            },
        )
    elif op.operation_type == "delete":
        await db.execute(
            "UPDATE documents SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL",
            (server_timestamp, op.entity_id),
        )


async def _apply_bullet_op(
    db: aiosqlite.Connection,
    op: SyncOperation,
    server_timestamp: int,
) -> None:
    p = op.payload
    if op.operation_type == "upsert":
        await db.execute(
            """
            INSERT INTO bullets
                (id, document_id, parent_id, content, position, is_complete,
                 created_at, updated_at)
            VALUES
                (:id, :document_id, :parent_id, :content, :position,
                 :is_complete, :created_at, :updated_at)
            ON CONFLICT(id) DO UPDATE SET
                content     = excluded.content,
                position    = excluded.position,
                parent_id   = excluded.parent_id,
                is_complete = excluded.is_complete,
                updated_at  = excluded.updated_at
            WHERE excluded.updated_at >= bullets.updated_at
            """,
            {
                "id": op.entity_id,
                "document_id": p.get("document_id", ""),
                "parent_id": p.get("parent_id"),
                "content": p.get("content", ""),
                "position": p.get("position", "n"),
                "is_complete": int(p.get("is_complete", False)),
                "created_at": p.get("created_at", server_timestamp),
                "updated_at": p.get("updated_at", server_timestamp),
            },
        )
    elif op.operation_type == "delete":
        await db.execute(
            "UPDATE bullets SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL",
            (server_timestamp, op.entity_id),
        )
