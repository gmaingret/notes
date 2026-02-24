"""Document endpoints: GET/POST /documents, PATCH/DELETE /documents/:id."""

import aiosqlite
from fastapi import APIRouter, Depends, HTTPException, status

from app.db.database import get_db
from app.models.document import DocumentCreate, DocumentResponse, DocumentUpdate
from app.services.document_service import (
    create_document,
    delete_document,
    list_documents,
    update_document,
)
from app.utils.jwt_utils import get_current_user

router = APIRouter(prefix="/documents", tags=["documents"])


@router.get("", response_model=list[DocumentResponse])
async def get_documents(
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> list[DocumentResponse]:
    """List all non-deleted documents ordered by position."""
    return await list_documents(db)


@router.post("", response_model=DocumentResponse, status_code=status.HTTP_201_CREATED)
async def post_document(
    body: DocumentCreate,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> DocumentResponse:
    """Create a new document."""
    return await create_document(db, body.title, body.position)


@router.patch("/{doc_id}", response_model=DocumentResponse)
async def patch_document(
    doc_id: str,
    body: DocumentUpdate,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> DocumentResponse:
    """Rename or reorder a document."""
    result = await update_document(db, doc_id, body.title, body.position)
    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Document not found")
    return result


@router.delete("/{doc_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_document_endpoint(
    doc_id: str,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> None:
    """Soft-delete a document."""
    deleted = await delete_document(db, doc_id)
    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Document not found")
