"""Pydantic request/response models for bullet endpoints."""

from pydantic import BaseModel


class BulletCreate(BaseModel):
    id: str  # client-generated UUID v4 — required for idempotency
    document_id: str
    parent_id: str | None = None
    content: str = ""
    position: str
    is_complete: bool = False


class BulletUpdate(BaseModel):
    content: str | None = None
    position: str | None = None
    parent_id: str | None = None
    is_complete: bool | None = None


class BulletResponse(BaseModel):
    id: str
    document_id: str
    parent_id: str | None
    content: str
    position: str
    is_complete: bool
    created_at: int  # Unix milliseconds
    updated_at: int  # Unix milliseconds
