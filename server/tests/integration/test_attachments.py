"""Integration tests for attachment endpoints."""

import uuid

import pytest

from app.config import settings
from app.utils.jwt_utils import create_access_token


@pytest.fixture
def attachments_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "attachments_path", str(tmp_path))
    return tmp_path


@pytest.fixture
def other_auth_headers() -> dict[str, str]:
    """JWT for a second user — used to verify 403 on cross-user access."""
    token = create_access_token(
        {"sub": "test-user-002", "email": "other@example.com", "name": "Other User"}
    )
    return {"Authorization": f"Bearer {token}"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


async def _create_doc(client, headers, title="Test", position="n"):
    r = await client.post(
        "/documents", json={"title": title, "position": position}, headers=headers
    )
    assert r.status_code == 201
    return r.json()["id"]


async def _create_bullet(client, headers, doc_id, position="n"):
    bid = str(uuid.uuid4())
    r = await client.post(
        "/bullets",
        json={
            "id": bid,
            "document_id": doc_id,
            "parent_id": None,
            "content": "",
            "position": position,
            "is_complete": False,
        },
        headers=headers,
    )
    assert r.status_code == 201
    return r.json()["id"]


async def _upload(client, headers, bullet_id, filename, content_type, data):
    return await client.post(
        "/attachments",
        files={"file": (filename, data, content_type)},
        data={"bullet_id": bullet_id},
        headers=headers,
    )


# ---------------------------------------------------------------------------
# Upload + retrieve
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_upload_image_retrieve_bytes_match(client, auth_headers, attachments_dir):
    """Upload a PNG image, retrieve it, and verify the bytes are identical."""
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    image_bytes = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64  # minimal fake PNG header

    r = await _upload(client, auth_headers, bullet_id, "photo.png", "image/png", image_bytes)
    assert r.status_code == 201
    body = r.json()
    assert body["type"] == "image"
    assert body["filename"] == "photo.png"
    assert body["mime_type"] == "image/png"
    assert body["size_bytes"] == len(image_bytes)

    r2 = await client.get(f"/attachments/{body['id']}/file", headers=auth_headers)
    assert r2.status_code == 200
    assert r2.content == image_bytes


@pytest.mark.asyncio
async def test_upload_audio_retrieve(client, auth_headers, attachments_dir):
    """Upload an MP3 file and retrieve it successfully."""
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    audio_bytes = b"ID3" + b"\x00" * 32  # fake ID3 header

    r = await _upload(client, auth_headers, bullet_id, "note.mp3", "audio/mpeg", audio_bytes)
    assert r.status_code == 201
    body = r.json()
    assert body["type"] == "audio"

    r2 = await client.get(f"/attachments/{body['id']}/file", headers=auth_headers)
    assert r2.status_code == 200
    assert r2.content == audio_bytes


# ---------------------------------------------------------------------------
# Delete → 404
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_delete_then_404_on_subsequent_get(client, auth_headers, attachments_dir):
    """Deleting an attachment makes subsequent file GET return 404."""
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    r = await _upload(client, auth_headers, bullet_id, "temp.txt", "text/plain", b"hello")
    assert r.status_code == 201
    attachment_id = r.json()["id"]

    r2 = await client.delete(f"/attachments/{attachment_id}", headers=auth_headers)
    assert r2.status_code == 204

    r3 = await client.get(f"/attachments/{attachment_id}/file", headers=auth_headers)
    assert r3.status_code == 404


# ---------------------------------------------------------------------------
# Cross-user access → 403
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_access_other_user_attachment_returns_403(
    client, auth_headers, other_auth_headers, attachments_dir
):
    """A different authenticated user cannot retrieve another user's attachment."""
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    r = await _upload(
        client, auth_headers, bullet_id, "private.png", "image/png", b"\x89PNG data"
    )
    assert r.status_code == 201
    attachment_id = r.json()["id"]

    r2 = await client.get(f"/attachments/{attachment_id}/file", headers=other_auth_headers)
    assert r2.status_code == 403


@pytest.mark.asyncio
async def test_delete_other_user_attachment_returns_403(
    client, auth_headers, other_auth_headers, attachments_dir
):
    """A different user cannot delete another user's attachment."""
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    r = await _upload(client, auth_headers, bullet_id, "file.txt", "text/plain", b"data")
    assert r.status_code == 201
    attachment_id = r.json()["id"]

    r2 = await client.delete(f"/attachments/{attachment_id}", headers=other_auth_headers)
    assert r2.status_code == 403


# ---------------------------------------------------------------------------
# Auth required
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_upload_requires_auth(client):
    r = await client.post(
        "/attachments",
        files={"file": ("x.txt", b"x", "text/plain")},
        data={"bullet_id": "any"},
    )
    assert r.status_code == 401


@pytest.mark.asyncio
async def test_get_attachment_requires_auth(client):
    r = await client.get("/attachments/any-id/file")
    assert r.status_code == 401


@pytest.mark.asyncio
async def test_delete_attachment_requires_auth(client):
    r = await client.delete("/attachments/any-id")
    assert r.status_code == 401


# ---------------------------------------------------------------------------
# 404 on unknown IDs
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_get_nonexistent_attachment_returns_404(client, auth_headers):
    r = await client.get("/attachments/nonexistent-id/file", headers=auth_headers)
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_delete_nonexistent_attachment_returns_404(client, auth_headers):
    r = await client.delete("/attachments/nonexistent-id", headers=auth_headers)
    assert r.status_code == 404


# ---------------------------------------------------------------------------
# Unsupported MIME type → 415
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_upload_invalid_mime_type_returns_415(client, auth_headers, attachments_dir):
    doc_id = await _create_doc(client, auth_headers)
    bullet_id = await _create_bullet(client, auth_headers, doc_id)

    r = await _upload(
        client, auth_headers, bullet_id, "bad.exe", "application/x-msdownload", b"MZ"
    )
    assert r.status_code == 415
