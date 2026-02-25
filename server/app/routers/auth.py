"""Auth endpoints: POST /auth/google and POST /auth/refresh."""

import time

import aiosqlite
from fastapi import APIRouter, Depends, HTTPException, status
from jose import JWTError
from jose import jwt as jose_jwt

from app.config import settings
from app.db.database import get_db
from app.models.auth import GoogleAuthRequest, RefreshRequest, TokenResponse
from app.services.auth_service import (
    AuthError,
    upsert_user,
    verify_access_token,
    verify_google_token,
)
from app.utils.jwt_utils import create_access_token, is_within_refresh_grace

router = APIRouter(prefix="/auth", tags=["auth"])


def _build_token_response(sub: str, email: str, name: str) -> TokenResponse:
    """Issue a JWT and return the TokenResponse with expires_at in Unix ms."""
    token = create_access_token({"sub": sub, "email": email, "name": name})
    expires_at_ms = int((time.time() + 24 * 3600) * 1000)
    return TokenResponse(access_token=token, expires_at=expires_at_ms)


@router.post("/google", response_model=TokenResponse, status_code=status.HTTP_200_OK)
async def auth_google(
    body: GoogleAuthRequest,
    db: aiosqlite.Connection = Depends(get_db),
) -> TokenResponse:
    """
    Exchange a Google ID token for a server-issued JWT.
    Upserts the user into the database on first login.
    """
    try:
        if body.id_token:
            user_info = await verify_google_token(body.id_token)
        elif body.access_token:
            user_info = await verify_access_token(body.access_token)
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Either id_token or access_token is required",
            )
    except AuthError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc))

    await upsert_user(db, user_info)
    return _build_token_response(
        sub=user_info["sub"],
        email=user_info["email"],
        name=user_info.get("name", ""),
    )


@router.post("/refresh", response_model=TokenResponse, status_code=status.HTTP_200_OK)
async def auth_refresh(body: RefreshRequest) -> TokenResponse:
    """
    Refresh a server JWT if it is within the 48h grace period after expiry.
    Returns 401 if the token is tampered or outside the grace window.
    """
    if not is_within_refresh_grace(body.access_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired beyond refresh grace period or is invalid",
        )

    try:
        payload = jose_jwt.decode(
            body.access_token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
            options={"verify_exp": False},
        )
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
        )

    return _build_token_response(
        sub=payload["sub"],
        email=payload.get("email", ""),
        name=payload.get("name", ""),
    )
