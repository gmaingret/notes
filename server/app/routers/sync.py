"""Sync endpoint: POST /sync."""

import aiosqlite
from fastapi import APIRouter, Depends

from app.db.database import get_db
from app.models.sync import SyncRequest, SyncResponse
from app.services.sync_service import process_sync
from app.utils.jwt_utils import get_current_user

router = APIRouter(prefix="/sync", tags=["sync"])


@router.post("", response_model=SyncResponse)
async def post_sync(
    body: SyncRequest,
    db: aiosqlite.Connection = Depends(get_db),
    _user: dict = Depends(get_current_user),
) -> SyncResponse:
    """
    Push client operations and receive server delta.
    In Phase 1, server_delta is always empty.
    """
    return await process_sync(db, body)
