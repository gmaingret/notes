"""Pydantic request/response models for auth endpoints."""

from pydantic import BaseModel


class GoogleAuthRequest(BaseModel):
    id_token: str


class TokenResponse(BaseModel):
    access_token: str
    expires_at: int  # Unix milliseconds


class RefreshRequest(BaseModel):
    access_token: str
