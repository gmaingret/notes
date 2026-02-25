"""
Attachment endpoints.

POST   /attachments              — upload a file (multipart/form-data: bullet_id + file)
GET    /attachments/:id/file     — stream the file bytes
DELETE /attachments/:id          — remove attachment record and file from disk
"""

import aiosqlite
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import FileResponse

from app.db.database import get_db
from app.models.attachment import AttachmentResponse
from app.services.attachment_service import (
    InvalidMimeTypeError,
    delete_attachment,
    get_attachment,
    save_attachment,
)
from app.utils.jwt_utils import get_current_user

router = APIRouter(tags=["attachments"])


@router.post("/attachments", response_model=AttachmentResponse, status_code=status.HTTP_201_CREATED)
async def upload_attachment(
    bullet_id: str = Form(...),
    file: UploadFile = File(...),
    db: aiosqlite.Connection = Depends(get_db),
    user: dict = Depends(get_current_user),
) -> AttachmentResponse:
    """Upload a file and attach it to a bullet."""
    data = await file.read()
    try:
        return await save_attachment(
            db=db,
            bullet_id=bullet_id,
            user_id=user["sub"],
            filename=file.filename or "upload",
            mime_type=file.content_type or "application/octet-stream",
            data=data,
        )
    except InvalidMimeTypeError as exc:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=str(exc),
        ) from exc


@router.get("/attachments/{attachment_id}/file")
async def serve_attachment(
    attachment_id: str,
    db: aiosqlite.Connection = Depends(get_db),
    user: dict = Depends(get_current_user),
) -> FileResponse:
    """Stream the file associated with an attachment."""
    try:
        result = await get_attachment(db, attachment_id, user["sub"])
    except PermissionError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Attachment not found")

    meta, file_path = result
    if not file_path.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="File not found on disk")

    return FileResponse(
        path=str(file_path),
        media_type=meta.mime_type,
        filename=meta.filename,
    )


@router.delete("/attachments/{attachment_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_attachment(
    attachment_id: str,
    db: aiosqlite.Connection = Depends(get_db),
    user: dict = Depends(get_current_user),
) -> None:
    """Delete an attachment record and its file from disk."""
    try:
        deleted = await delete_attachment(db, attachment_id, user["sub"])
    except PermissionError:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied")

    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Attachment not found")
