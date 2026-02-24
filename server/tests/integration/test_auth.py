"""Integration tests for auth endpoints."""

import time
from unittest.mock import patch

import pytest
from jose import jwt

from app.config import settings
from app.utils.jwt_utils import create_access_token


@pytest.mark.asyncio
async def test_google_auth_valid_token_issues_jwt(client, mock_google_token):
    """Valid Google ID token → 200 with access_token and expires_at."""
    mock_user = {
        "sub": "google-user-001",
        "email": "user@example.com",
        "name": "Test User",
        "avatar_url": "https://example.com/photo.jpg",
    }
    with patch("app.routers.auth.verify_google_token", return_value=mock_user):
        response = await client.post("/auth/google", json={"id_token": mock_google_token})

    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert "expires_at" in data
    assert isinstance(data["access_token"], str)
    assert len(data["access_token"]) > 0
    # expires_at should be ~24h from now in Unix ms
    now_ms = int(time.time() * 1000)
    assert data["expires_at"] > now_ms
    assert data["expires_at"] < now_ms + 25 * 3600 * 1000


@pytest.mark.asyncio
async def test_google_auth_valid_token_upserts_user(client, mock_google_token, db):
    """Valid Google token → user is upserted in the database."""
    mock_user = {
        "sub": "google-user-002",
        "email": "user2@example.com",
        "name": "Another User",
        "avatar_url": "",
    }
    with patch("app.routers.auth.verify_google_token", return_value=mock_user):
        response = await client.post("/auth/google", json={"id_token": mock_google_token})

    assert response.status_code == 200

    row = await db.execute_fetchall(
        "SELECT id, email, name FROM users WHERE id = ?", ("google-user-002",)
    )
    assert len(row) == 1
    assert row[0]["email"] == "user2@example.com"
    assert row[0]["name"] == "Another User"


@pytest.mark.asyncio
async def test_google_auth_invalid_token_returns_401(client):
    """Invalid Google ID token → 401."""
    from app.services.auth_service import AuthError

    with patch("app.routers.auth.verify_google_token", side_effect=AuthError("bad token")):
        response = await client.post("/auth/google", json={"id_token": "invalid-token"})

    assert response.status_code == 401
    assert "detail" in response.json()


@pytest.mark.asyncio
async def test_google_auth_jwt_payload_has_required_claims(client, mock_google_token):
    """Issued JWT contains sub, email, name, iat, exp claims."""
    mock_user = {
        "sub": "google-user-003",
        "email": "user3@example.com",
        "name": "Third User",
        "avatar_url": "",
    }
    with patch("app.routers.auth.verify_google_token", return_value=mock_user):
        response = await client.post("/auth/google", json={"id_token": mock_google_token})

    assert response.status_code == 200
    token = response.json()["access_token"]
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    assert payload["sub"] == "google-user-003"
    assert payload["email"] == "user3@example.com"
    assert payload["name"] == "Third User"
    assert "iat" in payload
    assert "exp" in payload


@pytest.mark.asyncio
async def test_refresh_valid_expired_token_within_grace(client):
    """Expired JWT within 48h grace → 200 with new token."""
    # Create a token that expired 1 second ago
    payload = {
        "sub": "user-grace",
        "email": "grace@example.com",
        "name": "Grace User",
        "iat": int(time.time()) - 100,
        "exp": int(time.time()) - 1,
    }
    expired_token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)

    response = await client.post("/auth/refresh", json={"access_token": expired_token})

    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["access_token"] != expired_token  # new token issued
    assert "expires_at" in data


@pytest.mark.asyncio
async def test_refresh_valid_token_not_yet_expired_returns_401(client):
    """Non-expired JWT → 401 (only tokens in the grace window can be refreshed)."""
    token = create_access_token({"sub": "u", "email": "u@x.com", "name": "U"})
    response = await client.post("/auth/refresh", json={"access_token": token})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_refresh_expired_beyond_grace_returns_401(client):
    """JWT expired beyond 48h grace → 401."""
    grace_hours = settings.jwt_refresh_grace_hours
    payload = {
        "sub": "user-old",
        "email": "old@example.com",
        "name": "Old User",
        "iat": int(time.time()) - (grace_hours + 2) * 3600,
        "exp": int(time.time()) - (grace_hours + 1) * 3600,
    }
    old_token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)

    response = await client.post("/auth/refresh", json={"access_token": old_token})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_refresh_tampered_token_returns_401(client):
    """Tampered JWT → 401."""
    token = create_access_token({"sub": "u", "email": "u@x.com", "name": "U"})
    parts = token.split(".")
    tampered = parts[0] + "." + parts[1] + ".badsignature"

    response = await client.post("/auth/refresh", json={"access_token": tampered})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_protected_endpoint_rejects_expired_jwt(client):
    """Expired JWT on protected route → 401."""
    payload = {
        "sub": "u",
        "email": "u@x.com",
        "name": "U",
        "iat": int(time.time()) - 7200,
        "exp": int(time.time()) - 3600,
    }
    expired_token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    headers = {"Authorization": f"Bearer {expired_token}"}

    response = await client.get("/documents", headers=headers)
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_protected_endpoint_rejects_missing_token(client):
    """Missing Authorization header → 401."""
    response = await client.get("/documents")
    assert response.status_code == 401
