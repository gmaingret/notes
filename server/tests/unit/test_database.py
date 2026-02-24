"""Unit tests for database module — covers branches missed by integration tests."""

import pytest

import app.db.database as db_module
from app.db.database import close_db, get_db, init_db


@pytest.mark.asyncio
async def test_get_db_raises_when_not_initialized():
    """get_db() raises RuntimeError if init_db() has not been called."""
    original = db_module._db
    db_module._db = None
    try:
        with pytest.raises(RuntimeError, match="Database not initialized"):
            await get_db()
    finally:
        db_module._db = original


@pytest.mark.asyncio
async def test_init_db_creates_parent_directories(tmp_path):
    """init_db() creates missing parent directories when db_path is a real path."""
    from unittest.mock import patch

    db_file = str(tmp_path / "subdir" / "notes.db")
    try:
        with patch("app.db.database.settings") as mock_settings:
            mock_settings.db_path = db_file
            await init_db()
    finally:
        await close_db()

    assert (tmp_path / "subdir").is_dir()
