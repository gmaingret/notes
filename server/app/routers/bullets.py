"""
Bullet endpoints.

GET  /documents/:id/bullets  — fetch all bullets for a document
POST /bullets                — create a bullet
PATCH /bullets/:id           — update a bullet
DELETE /bullets/:id          — soft-delete a bullet (cascade to children)
"""

import aiosqlite
from fastapi import APIRouter, Depends, HTTPException, status

from app.db.database import get_db
from app.models.bullet import BulletCreate, BulletResponse, BulletUpdate
from app.services.bullet_service import (
    create_bullet,
    delete_bullet,
    get_document_bullets,
    update_bullet,
)
from app.utils.jwt_utils import get_current_user

router = APIRouter(tags=["bullets"])


@router.get("/documents/{doc_id}/bullets", response_model=list[BulletResponse])
async def get_bullets(
    doc_id: str,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> list[BulletResponse]:
    """Fetch all non-deleted bullets for a document as a flat list."""
    result = await get_document_bullets(db, doc_id)
    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Document not found")
    return result


@router.post("/bullets", response_model=BulletResponse, status_code=status.HTTP_201_CREATED)
async def post_bullet(
    body: BulletCreate,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> BulletResponse:
    """Create a new bullet."""
    return await create_bullet(
        db=db,
        bullet_id=body.id,
        document_id=body.document_id,
        parent_id=body.parent_id,
        content=body.content,
        position=body.position,
        is_complete=body.is_complete,
    )


@router.patch("/bullets/{bullet_id}", response_model=BulletResponse)
async def patch_bullet(
    bullet_id: str,
    body: BulletUpdate,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> BulletResponse:
    """Update content, position, parent, or completion state of a bullet."""
    # Detect if parent_id is being explicitly set to null vs not provided.
    # FastAPI/Pydantic: if the field is not in the request body, it stays None.
    # We use model_fields_set to distinguish "not sent" from "sent as null".
    clear_parent = "parent_id" in body.model_fields_set and body.parent_id is None

    result = await update_bullet(
        db=db,
        bullet_id=bullet_id,
        content=body.content,
        position=body.position,
        parent_id=body.parent_id,
        is_complete=body.is_complete,
        clear_parent=clear_parent,
    )
    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bullet not found")
    return result


@router.delete("/bullets/{bullet_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_bullet_endpoint(
    bullet_id: str,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> None:
    """Soft-delete a bullet and all its descendants."""
    deleted = await delete_bullet(db, bullet_id)
    if not deleted:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bullet not found")
