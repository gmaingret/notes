"""Pydantic request/response models for the sync endpoint."""

from typing import Any, Literal

from pydantic import BaseModel


class SyncOperation(BaseModel):
    id: str  # UUID v4
    operation_type: Literal["upsert", "delete"]
    entity_type: Literal["document", "bullet", "attachment"]
    entity_id: str
    payload: dict[str, Any]
    client_timestamp: int  # Unix milliseconds


class SyncRequest(BaseModel):
    device_id: str
    last_sync_at: int  # Unix milliseconds (0 = first sync)
    operations: list[SyncOperation]


class SyncResponse(BaseModel):
    server_timestamp: int  # Unix milliseconds
    applied: list[str]  # operation IDs that were applied (including duplicates)
    server_delta: list[dict[str, Any]]  # always empty in Phase 1
