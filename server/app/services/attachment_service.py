"""Attachment service — save, serve, and delete user file uploads."""

import logging
import re
import time
import uuid
from pathlib import Path

import aiosqlite

from app.config import settings
from app.models.attachment import AttachmentResponse

logger = logging.getLogger(__name__)

# Permitted MIME type prefixes — anything outside this list is rejected.
_ALLOWED_PREFIXES = ("image/", "audio/", "video/", "text/")

# Permitted specific application/* MIME types.
_ALLOWED_TYPES: frozenset[str] = frozenset(
    {
        "application/pdf",
        "application/json",
        "application/zip",
        "application/x-zip-compressed",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
    }
)

# Map common MIME types to a file extension when the original filename has none.
_MIME_TO_EXT: dict[str, str] = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/gif": ".gif",
    "image/webp": ".webp",
    "image/svg+xml": ".svg",
    "audio/mpeg": ".mp3",
    "audio/ogg": ".ogg",
    "audio/wav": ".wav",
    "audio/webm": ".webm",
    "video/mp4": ".mp4",
    "video/webm": ".webm",
    "text/plain": ".txt",
    "text/csv": ".csv",
    "application/pdf": ".pdf",
    "application/json": ".json",
    "application/zip": ".zip",
}


class InvalidMimeTypeError(ValueError):
    """Raised when the uploaded file has a disallowed MIME type."""


def _is_allowed_mime(mime: str) -> bool:
    if any(mime.startswith(p) for p in _ALLOWED_PREFIXES):
        return True
    return mime in _ALLOWED_TYPES


def _classify_type(mime: str) -> str:
    """Map a MIME type to one of the DB enum values: 'image', 'audio', 'file'."""
    if mime.startswith("image/"):
        return "image"
    if mime.startswith("audio/"):
        return "audio"
    return "file"


def _sanitize_filename(name: str) -> str:
    """Strip path components and characters unsafe for filenames."""
    # Take basename only — guard against path traversal attacks.
    name = Path(name).name
    # Replace anything that isn't alphanumeric, dash, underscore, or dot.
    name = re.sub(r"[^\w.\-]", "_", name)
    # Collapse consecutive underscores.
    name = re.sub(r"_+", "_", name)
    return name or "upload"


async def save_attachment(
    db: aiosqlite.Connection,
    bullet_id: str,
    user_id: str,
    filename: str,
    mime_type: str,
    data: bytes,
) -> AttachmentResponse:
    """
    Persist an uploaded file to disk and record it in the database.

    Raises:
        InvalidMimeTypeError: if the MIME type is not in the allow-list.
    """
    if not _is_allowed_mime(mime_type):
        raise InvalidMimeTypeError(f"Unsupported MIME type: {mime_type}")

    safe_name = _sanitize_filename(filename)
    attachment_id = str(uuid.uuid4())

    ext = Path(safe_name).suffix or _MIME_TO_EXT.get(mime_type, "")
    storage_filename = f"{attachment_id}{ext}"

    base_path = Path(settings.attachments_path)
    base_path.mkdir(parents=True, exist_ok=True)
    (base_path / storage_filename).write_bytes(data)

    file_type = _classify_type(mime_type)
    now = int(time.time() * 1000)

    await db.execute(
        "INSERT INTO attachments "
        "(id, bullet_id, user_id, type, filename, mime_type, size_bytes, storage_path, created_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            attachment_id, bullet_id, user_id, file_type,
            safe_name, mime_type, len(data), storage_filename, now,
        ),
    )
    await db.commit()

    return AttachmentResponse(
        id=attachment_id,
        bullet_id=bullet_id,
        type=file_type,
        filename=safe_name,
        mime_type=mime_type,
        size_bytes=len(data),
        created_at=now,
    )


async def get_attachment(
    db: aiosqlite.Connection,
    attachment_id: str,
    user_id: str,
) -> tuple[AttachmentResponse, Path] | None:
    """
    Fetch attachment metadata and the resolved on-disk path.

    Returns None if not found.
    Raises PermissionError if the attachment belongs to a different user.
    """
    rows = await db.execute_fetchall(
        "SELECT id, bullet_id, user_id, type, filename, mime_type, size_bytes, "
        "storage_path, created_at "
        "FROM attachments WHERE id = ? AND deleted_at IS NULL",
        (attachment_id,),
    )
    if not rows:
        return None

    row = rows[0]
    if row["user_id"] != user_id:
        raise PermissionError("Attachment belongs to another user")

    meta = AttachmentResponse(
        id=row["id"],
        bullet_id=row["bullet_id"],
        type=row["type"],
        filename=row["filename"],
        mime_type=row["mime_type"],
        size_bytes=row["size_bytes"],
        created_at=row["created_at"],
    )
    file_path = Path(settings.attachments_path) / row["storage_path"]
    return meta, file_path


async def delete_attachment(
    db: aiosqlite.Connection,
    attachment_id: str,
    user_id: str,
) -> bool:
    """
    Soft-delete the DB record and remove the file from disk.

    Returns False if not found.
    Raises PermissionError if the attachment belongs to a different user.
    A missing file on disk is handled gracefully (logged, not raised).
    """
    rows = await db.execute_fetchall(
        "SELECT id, user_id, storage_path FROM attachments "
        "WHERE id = ? AND deleted_at IS NULL",
        (attachment_id,),
    )
    if not rows:
        return False

    row = rows[0]
    if row["user_id"] != user_id:
        raise PermissionError("Attachment belongs to another user")

    now = int(time.time() * 1000)
    await db.execute(
        "UPDATE attachments SET deleted_at = ? WHERE id = ?",
        (now, attachment_id),
    )
    await db.commit()

    file_path = Path(settings.attachments_path) / row["storage_path"]
    try:
        file_path.unlink()
    except FileNotFoundError:
        logger.warning("Attachment file missing on delete: %s", file_path)

    return True
