"""Unit tests for attachment service."""

import uuid
from pathlib import Path

import pytest
import pytest_asyncio

from app.config import settings
from app.db.database import get_test_db
from app.services.attachment_service import (
    InvalidMimeTypeError,
    _sanitize_filename,
    delete_attachment,
    get_attachment,
    save_attachment,
)
from app.services.bullet_service import create_bullet
from app.services.document_service import create_document


@pytest_asyncio.fixture
async def db():
    async with get_test_db() as database:
        yield database


@pytest_asyncio.fixture
async def bullet_id(db):
    doc = await create_document(db, title="Test", position="n")
    bid = str(uuid.uuid4())
    bullet = await create_bullet(
        db,
        bullet_id=bid,
        document_id=doc.id,
        parent_id=None,
        content="",
        position="n",
        is_complete=False,
    )
    return bullet.id


@pytest.fixture
def attachments_dir(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "attachments_path", str(tmp_path))
    return tmp_path


# ---------------------------------------------------------------------------
# Filename sanitisation
# ---------------------------------------------------------------------------


def test_sanitize_filename_strips_path_separators():
    result = _sanitize_filename("../../etc/passwd")
    assert "/" not in result
    assert ".." not in result


def test_sanitize_filename_removes_spaces_and_parens():
    result = _sanitize_filename("my file (1).jpg")
    assert " " not in result
    assert "(" not in result
    assert result.endswith(".jpg")


def test_sanitize_filename_empty_string_returns_upload():
    assert _sanitize_filename("") == "upload"


def test_sanitize_filename_preserves_extension():
    result = _sanitize_filename("photo.png")
    assert result.endswith(".png")


# ---------------------------------------------------------------------------
# MIME type validation
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_invalid_mime_type_raises(db, bullet_id, attachments_dir):
    with pytest.raises(InvalidMimeTypeError):
        await save_attachment(
            db=db,
            bullet_id=bullet_id,
            user_id="user1",
            filename="bad.exe",
            mime_type="application/x-msdownload",
            data=b"MZ",
        )


@pytest.mark.asyncio
async def test_image_mime_type_accepted(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="photo.jpg",
        mime_type="image/jpeg",
        data=b"\xff\xd8\xff",
    )
    assert result.type == "image"


@pytest.mark.asyncio
async def test_audio_mime_type_accepted(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="note.mp3",
        mime_type="audio/mpeg",
        data=b"ID3data",
    )
    assert result.type == "audio"


@pytest.mark.asyncio
async def test_pdf_mime_type_accepted(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="doc.pdf",
        mime_type="application/pdf",
        data=b"%PDF-1.4",
    )
    assert result.type == "file"


# ---------------------------------------------------------------------------
# File saved to correct path
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_file_saved_to_correct_path(db, bullet_id, attachments_dir):
    data = b"hello world"
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="test.txt",
        mime_type="text/plain",
        data=data,
    )
    expected = Path(settings.attachments_path) / f"{result.id}.txt"
    assert expected.exists()
    assert expected.read_bytes() == data


@pytest.mark.asyncio
async def test_file_saved_with_mime_derived_extension_when_no_filename_ext(
    db, bullet_id, attachments_dir
):
    data = b"\x89PNG\r\n\x1a\n"
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="image_no_ext",
        mime_type="image/png",
        data=data,
    )
    expected = Path(settings.attachments_path) / f"{result.id}.png"
    assert expected.exists()


# ---------------------------------------------------------------------------
# Record inserted into DB
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_record_inserted_in_db(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="doc.pdf",
        mime_type="application/pdf",
        data=b"%PDF content",
    )
    rows = await db.execute_fetchall(
        "SELECT id, user_id, size_bytes FROM attachments WHERE id = ? AND deleted_at IS NULL",
        (result.id,),
    )
    assert len(rows) == 1
    assert rows[0]["user_id"] == "user1"
    assert rows[0]["size_bytes"] == len(b"%PDF content")


# ---------------------------------------------------------------------------
# Delete removes both DB row and file
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_delete_removes_db_row_and_file(db, bullet_id, attachments_dir):
    data = b"to be deleted"
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="delete_me.txt",
        mime_type="text/plain",
        data=data,
    )

    saved_files = list(Path(settings.attachments_path).iterdir())
    assert len(saved_files) == 1

    deleted = await delete_attachment(db, result.id, "user1")
    assert deleted is True

    # DB row soft-deleted
    rows = await db.execute_fetchall(
        "SELECT id FROM attachments WHERE id = ? AND deleted_at IS NULL",
        (result.id,),
    )
    assert len(rows) == 0

    # File removed from disk
    assert not saved_files[0].exists()


@pytest.mark.asyncio
async def test_delete_nonexistent_attachment_returns_false(db, attachments_dir):
    result = await delete_attachment(db, str(uuid.uuid4()), "user1")
    assert result is False


# ---------------------------------------------------------------------------
# Missing file on delete does not raise
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_delete_missing_file_does_not_raise(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="will_be_gone.txt",
        mime_type="text/plain",
        data=b"data",
    )
    # Remove file from disk before calling delete
    (Path(settings.attachments_path) / f"{result.id}.txt").unlink()

    # Must not raise even though the file is missing
    deleted = await delete_attachment(db, result.id, "user1")
    assert deleted is True


# ---------------------------------------------------------------------------
# get_attachment
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_get_attachment_returns_meta_and_path(db, bullet_id, attachments_dir):
    data = b"content"
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="get_me.txt",
        mime_type="text/plain",
        data=data,
    )
    fetched = await get_attachment(db, result.id, "user1")
    assert fetched is not None
    meta, file_path = fetched
    assert meta.id == result.id
    assert file_path.exists()
    assert file_path.read_bytes() == data


@pytest.mark.asyncio
async def test_get_attachment_wrong_user_raises(db, bullet_id, attachments_dir):
    result = await save_attachment(
        db=db,
        bullet_id=bullet_id,
        user_id="user1",
        filename="private.txt",
        mime_type="text/plain",
        data=b"secret",
    )
    with pytest.raises(PermissionError):
        await get_attachment(db, result.id, "user2")


@pytest.mark.asyncio
async def test_get_attachment_not_found_returns_none(db, attachments_dir):
    result = await get_attachment(db, str(uuid.uuid4()), "user1")
    assert result is None
