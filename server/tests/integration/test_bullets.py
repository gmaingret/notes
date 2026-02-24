"""Integration tests for bullet endpoints."""

import uuid

import pytest


async def _create_doc(client, auth_headers, title="Test Doc", position="n"):
    r = await client.post(
        "/documents", json={"title": title, "position": position}, headers=auth_headers
    )
    assert r.status_code == 201
    return r.json()["id"]


async def _create_bullet(client, auth_headers, doc_id, parent_id=None, content="", position="n"):
    bid = str(uuid.uuid4())
    payload = {
        "id": bid,
        "document_id": doc_id,
        "parent_id": parent_id,
        "content": content,
        "position": position,
        "is_complete": False,
    }
    r = await client.post("/bullets", json=payload, headers=auth_headers)
    assert r.status_code == 201
    return r.json()


@pytest.mark.asyncio
async def test_create_root_bullet(client, auth_headers):
    """POST /bullets creates a root-level bullet."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id)

    assert "id" in bullet
    assert bullet["document_id"] == doc_id
    assert bullet["parent_id"] is None
    assert "created_at" in bullet
    assert "updated_at" in bullet


@pytest.mark.asyncio
async def test_create_child_bullet(client, auth_headers):
    """POST /bullets with parent_id creates a child bullet."""
    doc_id = await _create_doc(client, auth_headers)
    parent = await _create_bullet(client, auth_headers, doc_id, position="a")
    child = await _create_bullet(
        client, auth_headers, doc_id, parent_id=parent["id"], position="b"
    )

    assert child["parent_id"] == parent["id"]


@pytest.mark.asyncio
async def test_get_bullets_for_document(client, auth_headers):
    """GET /documents/:id/bullets returns all bullets for the document."""
    doc_id = await _create_doc(client, auth_headers)
    b1 = await _create_bullet(client, auth_headers, doc_id, position="a")
    b2 = await _create_bullet(client, auth_headers, doc_id, position="b")

    r = await client.get(f"/documents/{doc_id}/bullets", headers=auth_headers)
    assert r.status_code == 200
    ids = {b["id"] for b in r.json()}
    assert b1["id"] in ids
    assert b2["id"] in ids


@pytest.mark.asyncio
async def test_get_bullets_ordered_by_position(client, auth_headers):
    """Bullets returned by GET /documents/:id/bullets are in position order."""
    doc_id = await _create_doc(client, auth_headers)
    await _create_bullet(client, auth_headers, doc_id, position="z")
    await _create_bullet(client, auth_headers, doc_id, position="a")
    await _create_bullet(client, auth_headers, doc_id, position="m")

    r = await client.get(f"/documents/{doc_id}/bullets", headers=auth_headers)
    positions = [b["position"] for b in r.json()]
    assert positions == sorted(positions)


@pytest.mark.asyncio
async def test_get_bullets_unknown_document_returns_404(client, auth_headers):
    """GET /documents/:id/bullets with unknown doc id → 404."""
    r = await client.get("/documents/nonexistent-doc/bullets", headers=auth_headers)
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_patch_bullet_content(client, auth_headers):
    """PATCH /bullets/:id can update content."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id, content="original")

    r = await client.patch(
        f"/bullets/{bullet['id']}",
        json={"content": "updated"},
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.json()["content"] == "updated"


@pytest.mark.asyncio
async def test_patch_bullet_is_complete(client, auth_headers):
    """PATCH /bullets/:id can toggle is_complete."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id)

    r = await client.patch(
        f"/bullets/{bullet['id']}",
        json={"is_complete": True},
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.json()["is_complete"] is True


@pytest.mark.asyncio
async def test_patch_bullet_move_to_new_parent(client, auth_headers):
    """PATCH /bullets/:id can move a bullet under a new parent."""
    doc_id = await _create_doc(client, auth_headers)
    parent = await _create_bullet(client, auth_headers, doc_id, position="a")
    child = await _create_bullet(client, auth_headers, doc_id, position="b")

    r = await client.patch(
        f"/bullets/{child['id']}",
        json={"parent_id": parent["id"]},
        headers=auth_headers,
    )
    assert r.status_code == 200
    assert r.json()["parent_id"] == parent["id"]


@pytest.mark.asyncio
async def test_patch_bullet_not_found(client, auth_headers):
    """PATCH /bullets/:id with unknown id → 404."""
    r = await client.patch(
        "/bullets/nonexistent-id",
        json={"content": "x"},
        headers=auth_headers,
    )
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_delete_bullet(client, auth_headers):
    """DELETE /bullets/:id soft-deletes the bullet."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id)

    r = await client.delete(f"/bullets/{bullet['id']}", headers=auth_headers)
    assert r.status_code == 204

    r = await client.get(f"/documents/{doc_id}/bullets", headers=auth_headers)
    ids = {b["id"] for b in r.json()}
    assert bullet["id"] not in ids


@pytest.mark.asyncio
async def test_delete_bullet_not_found(client, auth_headers):
    """DELETE /bullets/:id with unknown id → 404."""
    r = await client.delete("/bullets/nonexistent-id", headers=auth_headers)
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_soft_delete_cascades_to_children(client, auth_headers):
    """Deleting a parent bullet removes it and all children from the list."""
    doc_id = await _create_doc(client, auth_headers)
    parent = await _create_bullet(client, auth_headers, doc_id, position="a")
    child1 = await _create_bullet(
        client, auth_headers, doc_id, parent_id=parent["id"], position="b"
    )
    child2 = await _create_bullet(
        client, auth_headers, doc_id, parent_id=parent["id"], position="c"
    )

    r = await client.delete(f"/bullets/{parent['id']}", headers=auth_headers)
    assert r.status_code == 204

    r = await client.get(f"/documents/{doc_id}/bullets", headers=auth_headers)
    remaining_ids = {b["id"] for b in r.json()}
    assert parent["id"] not in remaining_ids
    assert child1["id"] not in remaining_ids
    assert child2["id"] not in remaining_ids


@pytest.mark.asyncio
async def test_tag_sync_on_create(client, auth_headers, db):
    """Creating a bullet with #tags stores them in the tags table."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id, content="Hello #python #testing")

    rows = await db.execute_fetchall(
        "SELECT t.name FROM tags t "
        "JOIN bullet_tags bt ON bt.tag_id = t.id "
        "WHERE bt.bullet_id = ?",
        (bullet["id"],),
    )
    tag_names = {r["name"] for r in rows}
    assert "python" in tag_names
    assert "testing" in tag_names


@pytest.mark.asyncio
async def test_tag_sync_on_update(client, auth_headers, db):
    """Updating bullet content updates the associated tags."""
    doc_id = await _create_doc(client, auth_headers)
    bullet = await _create_bullet(client, auth_headers, doc_id, content="#old-tag")

    await client.patch(
        f"/bullets/{bullet['id']}",
        json={"content": "#new-tag"},
        headers=auth_headers,
    )

    rows = await db.execute_fetchall(
        "SELECT t.name FROM tags t "
        "JOIN bullet_tags bt ON bt.tag_id = t.id "
        "WHERE bt.bullet_id = ?",
        (bullet["id"],),
    )
    tag_names = {r["name"] for r in rows}
    assert "new-tag" in tag_names
    assert "old-tag" not in tag_names


@pytest.mark.asyncio
async def test_bullets_require_auth(client):
    """Bullet endpoints require a valid JWT."""
    import uuid as _uuid

    doc_id = "any-doc-id"
    valid_bullet_body = {
        "id": str(_uuid.uuid4()),
        "document_id": doc_id,
        "parent_id": None,
        "content": "",
        "position": "n",
        "is_complete": False,
    }
    assert (await client.get(f"/documents/{doc_id}/bullets")).status_code == 401
    assert (await client.post("/bullets", json=valid_bullet_body)).status_code == 401
    assert (await client.patch("/bullets/any-id", json={})).status_code == 401
    assert (await client.delete("/bullets/any-id")).status_code == 401
