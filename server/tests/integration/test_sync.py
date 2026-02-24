"""Integration tests for the POST /sync endpoint."""

import uuid

import pytest


def _make_op(
    entity_type: str = "document",
    operation_type: str = "upsert",
    entity_id: str | None = None,
    client_timestamp: int = 1700000000000,
    payload: dict | None = None,
) -> dict:
    return {
        "id": str(uuid.uuid4()),
        "operation_type": operation_type,
        "entity_type": entity_type,
        "entity_id": entity_id or str(uuid.uuid4()),
        "payload": payload or {},
        "client_timestamp": client_timestamp,
    }


@pytest.mark.asyncio
async def test_sync_unauthenticated_returns_401(client):
    """POST /sync without a token → 401."""
    response = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": []},
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_sync_empty_operations_returns_ok(client, auth_headers):
    """POST /sync with empty operations → 200, empty applied list."""
    response = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": []},
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["applied"] == []
    assert data["server_delta"] == []
    assert isinstance(data["server_timestamp"], int)
    assert data["server_timestamp"] > 0


@pytest.mark.asyncio
async def test_sync_push_five_operations_all_applied(client, auth_headers):
    """Push 5 operations → all 5 operation IDs returned in applied."""
    ops = [_make_op(client_timestamp=1700000000000 + i * 1000) for i in range(5)]

    response = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": ops},
        headers=auth_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert len(data["applied"]) == 5
    op_ids = {op["id"] for op in ops}
    assert set(data["applied"]) == op_ids


@pytest.mark.asyncio
async def test_sync_duplicate_operation_id_is_idempotent(client, auth_headers):
    """Sending the same operation_id twice → included in applied once per request."""
    op = _make_op()

    # First sync
    r1 = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": [op]},
        headers=auth_headers,
    )
    assert r1.status_code == 200
    assert op["id"] in r1.json()["applied"]

    # Second sync with the same operation — should not cause an error
    r2 = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": [op]},
        headers=auth_headers,
    )
    assert r2.status_code == 200
    assert op["id"] in r2.json()["applied"]


@pytest.mark.asyncio
async def test_sync_operations_applied_in_timestamp_order(client, auth_headers, db):
    """Operations are applied in ascending client_timestamp order."""
    entity_id = str(uuid.uuid4())

    # Create a document first so the bullet operations don't fail FK checks.
    doc_id = str(uuid.uuid4())
    await db.execute(
        "INSERT INTO documents (id, title, position, created_at, updated_at) "
        "VALUES (?, ?, ?, ?, ?)",
        (doc_id, "Sync Doc", "n", 1700000000000, 1700000000000),
    )
    await db.commit()

    # Two upsert operations for the same bullet entity; later timestamp wins.
    op_early = {
        "id": str(uuid.uuid4()),
        "operation_type": "upsert",
        "entity_type": "bullet",
        "entity_id": entity_id,
        "payload": {
            "document_id": doc_id,
            "parent_id": None,
            "content": "early content",
            "position": "a",
            "is_complete": False,
            "created_at": 1700000001000,
            "updated_at": 1700000001000,
        },
        "client_timestamp": 1700000001000,
    }
    op_late = {
        "id": str(uuid.uuid4()),
        "operation_type": "upsert",
        "entity_type": "bullet",
        "entity_id": entity_id,
        "payload": {
            "document_id": doc_id,
            "parent_id": None,
            "content": "late content",
            "position": "b",
            "is_complete": False,
            "created_at": 1700000001000,
            "updated_at": 1700000002000,
        },
        "client_timestamp": 1700000002000,
    }

    # Send in reversed order — server must sort by client_timestamp.
    response = await client.post(
        "/sync",
        json={
            "device_id": "dev-1",
            "last_sync_at": 0,
            "operations": [op_late, op_early],
        },
        headers=auth_headers,
    )
    assert response.status_code == 200
    assert len(response.json()["applied"]) == 2

    # The later timestamp (op_late) should win because of the upsert WHERE clause.
    rows = await db.execute_fetchall(
        "SELECT content FROM bullets WHERE id = ?", (entity_id,)
    )
    assert rows[0]["content"] == "late content"


@pytest.mark.asyncio
async def test_sync_server_delta_always_empty_in_phase1(client, auth_headers):
    """server_delta is always an empty list in Phase 1."""
    response = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": []},
        headers=auth_headers,
    )
    assert response.json()["server_delta"] == []


@pytest.mark.asyncio
async def test_sync_server_timestamp_in_response(client, auth_headers):
    """Response includes a server_timestamp (Unix ms)."""
    import time

    before = int(time.time() * 1000)
    response = await client.post(
        "/sync",
        json={"device_id": "dev-1", "last_sync_at": 0, "operations": []},
        headers=auth_headers,
    )
    after = int(time.time() * 1000)
    ts = response.json()["server_timestamp"]
    assert before <= ts <= after
