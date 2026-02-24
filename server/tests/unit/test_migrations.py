"""Verify that migrations run cleanly and create all expected tables."""

import pytest

EXPECTED_TABLES = {
    "users",
    "documents",
    "bullets",
    "tags",
    "bullet_tags",
    "attachments",
    "sync_operations",
    "schema_migrations",
}


@pytest.mark.asyncio
async def test_migrations_create_all_tables(db):
    rows = await db.execute_fetchall(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    )
    tables = {row["name"] for row in rows}
    assert EXPECTED_TABLES.issubset(tables)


@pytest.mark.asyncio
async def test_fts_table_exists(db):
    rows = await db.execute_fetchall(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='bullets_fts'"
    )
    assert rows, "bullets_fts virtual table should exist"


@pytest.mark.asyncio
async def test_fts_triggers_exist(db):
    rows = await db.execute_fetchall(
        "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'bullets_fts_%'"
    )
    trigger_names = {row["name"] for row in rows}
    assert "bullets_fts_insert" in trigger_names
    assert "bullets_fts_update" in trigger_names
    assert "bullets_fts_delete" in trigger_names
