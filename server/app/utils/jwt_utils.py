"""JWT issuance and validation utilities."""

import time

from jose import JWTError, jwt

from app.config import settings


def create_access_token(data: dict) -> str:
    """Issue a signed JWT with a 24h expiry."""
    payload = dict(data)
    payload["iat"] = int(time.time())
    payload["exp"] = int(time.time()) + settings.jwt_expiry_hours * 3600
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict:
    """Decode and validate a JWT. Raises JWTError on failure."""
    return jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])


def is_within_refresh_grace(token: str) -> bool:
    """
    Return True if the token is expired but within the refresh grace period.
    The grace period allows silent refresh without forcing a new login.
    """
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
            options={"verify_exp": False},
        )
    except JWTError:
        return False

    exp = payload.get("exp", 0)
    grace_deadline = exp + settings.jwt_refresh_grace_hours * 3600
    return exp < time.time() <= grace_deadline
