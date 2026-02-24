"""
Shared test fixtures for backend tests.

Key fixtures:
  db       — In-memory SQLite with migrations applied (async, function-scoped)
  client   — httpx.AsyncClient wired to the FastAPI app with DB dependency overridden
  auth_headers — Pre-issued test JWT that bypasses Google OAuth
"""

import time
from typing import AsyncGenerator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app.config import settings
from app.db.database import get_db, get_test_db
from app.main import app


@pytest_asyncio.fixture
async def db():
    """In-memory SQLite database with schema applied. Rolled back after each test."""
    async with get_test_db() as database:
        yield database


@pytest_asyncio.fixture
async def client(db) -> AsyncGenerator[AsyncClient, None]:
    """httpx AsyncClient connected to the FastAPI app with the test DB injected."""
    app.dependency_overrides[get_db] = lambda: db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest.fixture
def auth_headers() -> dict[str, str]:
    """Pre-issued JWT for testing — bypasses Google OAuth entirely."""
    from app.utils.jwt_utils import create_access_token

    token = create_access_token(
        {"sub": "test-user-001", "email": "test@example.com", "name": "Test User"}
    )
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def mock_google_token() -> str:
    """Fake Google ID token string (not validated in tests — Google auth is mocked)."""
    return "mock-google-id-token"
