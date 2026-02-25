"""Pydantic request/response models for attachment endpoints."""

from pydantic import BaseModel


class AttachmentResponse(BaseModel):
    id: str
    bullet_id: str
    type: str  # 'image' | 'audio' | 'file'
    filename: str
    mime_type: str
    size_bytes: int
    created_at: int  # Unix milliseconds
