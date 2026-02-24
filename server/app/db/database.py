import os
from contextlib import asynccontextmanager
from pathlib import Path

import aiosqlite

from app.config import settings

_db: aiosqlite.Connection | None = None


async def init_db() -> None:
    """Open the database connection and run migrations."""
    global _db

    db_path = settings.db_path
    if db_path != ":memory:":
        os.makedirs(Path(db_path).parent, exist_ok=True)

    _db = await aiosqlite.connect(db_path)
    _db.row_factory = aiosqlite.Row

    await _db.execute("PRAGMA journal_mode=WAL")
    await _db.execute("PRAGMA foreign_keys=ON")

    await _run_migrations(_db)


async def close_db() -> None:
    """Close the database connection."""
    global _db
    if _db is not None:
        await _db.close()
        _db = None


async def get_db() -> aiosqlite.Connection:
    """FastAPI dependency — yields the open database connection."""
    if _db is None:
        raise RuntimeError("Database not initialized. Call init_db() first.")
    return _db


async def _run_migrations(db: aiosqlite.Connection) -> None:
    """Apply all SQL migration files in order, skipping already-applied ones."""
    await db.execute(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version TEXT PRIMARY KEY,
            applied_at INTEGER NOT NULL
        )
        """
    )
    await db.commit()

    migrations_dir = Path(__file__).parent / "migrations"
    sql_files = sorted(migrations_dir.glob("*.sql"))

    for sql_file in sql_files:
        version = sql_file.stem
        row = await db.execute_fetchall(
            "SELECT version FROM schema_migrations WHERE version = ?", (version,)
        )
        if row:
            continue

        sql = sql_file.read_text(encoding="utf-8")
        await db.executescript(sql)

        import time

        await db.execute(
            "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)",
            (version, int(time.time() * 1000)),
        )
        await db.commit()


@asynccontextmanager
async def get_test_db():
    """In-memory database for tests — runs migrations and yields the connection."""
    db = await aiosqlite.connect(":memory:")
    db.row_factory = aiosqlite.Row
    await db.execute("PRAGMA foreign_keys=ON")
    await _run_migrations(db)
    try:
        yield db
    finally:
        await db.close()
