"""Integration tests for document endpoints."""

import pytest


@pytest.mark.asyncio
async def test_list_documents_empty(client, auth_headers):
    """GET /documents returns empty list when no documents exist."""
    response = await client.get("/documents", headers=auth_headers)
    assert response.status_code == 200
    assert response.json() == []


@pytest.mark.asyncio
async def test_create_document(client, auth_headers):
    """POST /documents creates a document and returns 201."""
    response = await client.post(
        "/documents",
        json={"title": "My First Doc", "position": "n"},
        headers=auth_headers,
    )
    assert response.status_code == 201
    data = response.json()
    assert data["title"] == "My First Doc"
    assert data["position"] == "n"
    assert "id" in data
    assert "created_at" in data
    assert "updated_at" in data
    assert isinstance(data["id"], str)
    assert len(data["id"]) > 0


@pytest.mark.asyncio
async def test_create_document_without_position_generates_one(client, auth_headers):
    """POST /documents without position field — server generates a valid position."""
    response = await client.post(
        "/documents",
        json={"title": "Auto Position Doc"},
        headers=auth_headers,
    )
    assert response.status_code == 201
    data = response.json()
    assert isinstance(data["position"], str)
    assert len(data["position"]) > 0


@pytest.mark.asyncio
async def test_list_documents_ordered_by_position(client, auth_headers):
    """GET /documents returns documents in lexicographic position order."""
    await client.post("/documents", json={"title": "Z Doc", "position": "z"}, headers=auth_headers)
    await client.post("/documents", json={"title": "A Doc", "position": "a"}, headers=auth_headers)
    await client.post("/documents", json={"title": "M Doc", "position": "m"}, headers=auth_headers)

    response = await client.get("/documents", headers=auth_headers)
    assert response.status_code == 200
    docs = response.json()
    positions = [d["position"] for d in docs]
    assert positions == sorted(positions)


@pytest.mark.asyncio
async def test_patch_document_title(client, auth_headers):
    """PATCH /documents/:id can rename a document."""
    create_resp = await client.post(
        "/documents", json={"title": "Original", "position": "n"}, headers=auth_headers
    )
    doc_id = create_resp.json()["id"]

    patch_resp = await client.patch(
        f"/documents/{doc_id}",
        json={"title": "Renamed"},
        headers=auth_headers,
    )
    assert patch_resp.status_code == 200
    assert patch_resp.json()["title"] == "Renamed"
    assert patch_resp.json()["position"] == "n"  # unchanged


@pytest.mark.asyncio
async def test_patch_document_position(client, auth_headers):
    """PATCH /documents/:id can reorder a document."""
    create_resp = await client.post(
        "/documents", json={"title": "Doc", "position": "a"}, headers=auth_headers
    )
    doc_id = create_resp.json()["id"]

    patch_resp = await client.patch(
        f"/documents/{doc_id}",
        json={"position": "z"},
        headers=auth_headers,
    )
    assert patch_resp.status_code == 200
    assert patch_resp.json()["position"] == "z"
    assert patch_resp.json()["title"] == "Doc"  # unchanged


@pytest.mark.asyncio
async def test_patch_document_not_found(client, auth_headers):
    """PATCH /documents/:id with unknown id → 404."""
    response = await client.patch(
        "/documents/nonexistent-id",
        json={"title": "X"},
        headers=auth_headers,
    )
    assert response.status_code == 404
    assert "detail" in response.json()


@pytest.mark.asyncio
async def test_delete_document(client, auth_headers):
    """DELETE /documents/:id soft-deletes the document — it disappears from list."""
    create_resp = await client.post(
        "/documents", json={"title": "To Delete", "position": "n"}, headers=auth_headers
    )
    doc_id = create_resp.json()["id"]

    delete_resp = await client.delete(f"/documents/{doc_id}", headers=auth_headers)
    assert delete_resp.status_code == 204

    list_resp = await client.get("/documents", headers=auth_headers)
    ids = [d["id"] for d in list_resp.json()]
    assert doc_id not in ids


@pytest.mark.asyncio
async def test_delete_document_not_found(client, auth_headers):
    """DELETE /documents/:id with unknown id → 404."""
    response = await client.delete("/documents/nonexistent-id", headers=auth_headers)
    assert response.status_code == 404
    assert "detail" in response.json()


@pytest.mark.asyncio
async def test_documents_require_auth(client):
    """All document endpoints require a valid JWT."""
    assert (await client.get("/documents")).status_code == 401
    resp = await client.post("/documents", json={"title": "x", "position": "n"})
    assert resp.status_code == 401
    assert (await client.patch("/documents/any-id", json={})).status_code == 401
    assert (await client.delete("/documents/any-id")).status_code == 401


@pytest.mark.asyncio
async def test_crud_lifecycle(client, auth_headers):
    """Full create → read → update → delete lifecycle."""
    # Create
    r = await client.post(
        "/documents", json={"title": "Lifecycle Doc", "position": "a"}, headers=auth_headers
    )
    assert r.status_code == 201
    doc_id = r.json()["id"]

    # Read
    r = await client.get("/documents", headers=auth_headers)
    assert any(d["id"] == doc_id for d in r.json())

    # Update
    r = await client.patch(
        f"/documents/{doc_id}", json={"title": "Updated"}, headers=auth_headers
    )
    assert r.status_code == 200
    assert r.json()["title"] == "Updated"

    # Delete
    r = await client.delete(f"/documents/{doc_id}", headers=auth_headers)
    assert r.status_code == 204

    # Verify gone
    r = await client.get("/documents", headers=auth_headers)
    assert not any(d["id"] == doc_id for d in r.json())
