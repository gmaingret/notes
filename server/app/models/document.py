"""Pydantic request/response models for document endpoints."""

from pydantic import BaseModel


class DocumentCreate(BaseModel):
    title: str
    position: str | None = None


class DocumentUpdate(BaseModel):
    title: str | None = None
    position: str | None = None


class DocumentResponse(BaseModel):
    id: str
    title: str
    position: str
    created_at: int  # Unix milliseconds
    updated_at: int  # Unix milliseconds
