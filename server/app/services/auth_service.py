"""Auth service — Google ID token verification and user upsert."""

import time

import aiosqlite
from google.auth.exceptions import GoogleAuthError
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token

from app.config import settings


class AuthError(Exception):
    """Raised when Google token verification fails."""


async def verify_google_token(token: str) -> dict:
    """
    Verify a Google ID token and return the decoded claims.

    Returns a dict with keys: sub, email, name (may be absent).
    Raises AuthError on any failure.
    """
    try:
        id_info = google_id_token.verify_oauth2_token(
            token,
            google_requests.Request(),
            settings.google_client_id,
        )
    except (GoogleAuthError, ValueError) as exc:
        raise AuthError(f"Invalid Google token: {exc}") from exc

    return {
        "sub": id_info["sub"],
        "email": id_info["email"],
        "name": id_info.get("name", ""),
        "avatar_url": id_info.get("picture", ""),
    }


async def upsert_user(db: aiosqlite.Connection, user_info: dict) -> None:
    """
    Insert or update the user row derived from a Google ID token payload.
    Uses INSERT OR REPLACE to handle first login and subsequent logins.
    """
    now = int(time.time() * 1000)
    await db.execute(
        """
        INSERT INTO users (id, email, name, avatar_url, created_at)
        VALUES (:sub, :email, :name, :avatar_url, :now)
        ON CONFLICT(id) DO UPDATE SET
            email      = excluded.email,
            name       = excluded.name,
            avatar_url = excluded.avatar_url
        """,
        {
            "sub": user_info["sub"],
            "email": user_info["email"],
            "name": user_info.get("name", ""),
            "avatar_url": user_info.get("avatar_url", ""),
            "now": now,
        },
    )
    await db.commit()
