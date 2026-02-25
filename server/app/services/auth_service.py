"""Auth service — Google ID token verification and user upsert."""

import time

import aiosqlite
import requests as http_requests
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


async def verify_access_token(access_token: str) -> dict:
    """
    Verify a Google OAuth access token by calling the userinfo endpoint.
    Used on web where google_sign_in returns an access token instead of an ID token.
    Raises AuthError on any failure.
    """
    try:
        response = http_requests.get(
            "https://www.googleapis.com/oauth2/v3/userinfo",
            headers={"Authorization": f"Bearer {access_token}"},
            timeout=10,
        )
    except http_requests.RequestException as exc:
        raise AuthError(f"Failed to reach Google userinfo endpoint: {exc}") from exc

    if response.status_code != 200:
        raise AuthError(f"Invalid access token: HTTP {response.status_code}")

    data = response.json()
    if "sub" not in data or "email" not in data:
        raise AuthError("Access token missing required user fields")

    return {
        "sub": data["sub"],
        "email": data["email"],
        "name": data.get("name", ""),
        "avatar_url": data.get("picture", ""),
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
