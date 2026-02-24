"""Unit tests for JWT utility functions."""

import time

import pytest
from jose import JWTError, jwt as jose_jwt

from app.config import settings
from app.utils.jwt_utils import (
    create_access_token,
    decode_access_token,
    is_within_refresh_grace,
)


def test_create_access_token_returns_string():
    token = create_access_token({"sub": "user-123"})
    assert isinstance(token, str)
    assert len(token) > 0


def test_create_access_token_payload_round_trips():
    data = {"sub": "user-123", "email": "a@b.com"}
    token = create_access_token(data)
    decoded = decode_access_token(token)
    assert decoded["sub"] == "user-123"
    assert decoded["email"] == "a@b.com"


def test_create_access_token_sets_iat_and_exp():
    before = int(time.time())
    token = create_access_token({"sub": "u"})
    after = int(time.time())
    decoded = decode_access_token(token)
    assert before <= decoded["iat"] <= after
    assert decoded["exp"] > before


def test_create_access_token_exp_is_24h_from_now():
    before = int(time.time())
    token = create_access_token({"sub": "u"})
    decoded = decode_access_token(token)
    expected_exp = before + settings.jwt_expiry_hours * 3600
    assert abs(decoded["exp"] - expected_exp) <= 2


def test_decode_access_token_raises_on_invalid_token():
    with pytest.raises(JWTError):
        decode_access_token("not.a.valid.token")


def test_decode_access_token_raises_on_tampered_signature():
    token = create_access_token({"sub": "u"})
    parts = token.split(".")
    tampered = parts[0] + "." + parts[1] + ".badsig"
    with pytest.raises(JWTError):
        decode_access_token(tampered)


def test_decode_access_token_raises_on_expired_token():
    payload = {
        "sub": "u",
        "iat": int(time.time()) - 7200,
        "exp": int(time.time()) - 3600,
    }
    expired_token = jose_jwt.encode(
        payload, settings.jwt_secret, algorithm=settings.jwt_algorithm
    )
    with pytest.raises(JWTError):
        decode_access_token(expired_token)


def test_is_within_refresh_grace_returns_true_when_expired_within_grace():
    """Token expired 1 second ago — within the grace window."""
    payload = {
        "sub": "u",
        "iat": int(time.time()) - 100,
        "exp": int(time.time()) - 1,
    }
    token = jose_jwt.encode(
        payload, settings.jwt_secret, algorithm=settings.jwt_algorithm
    )
    assert is_within_refresh_grace(token) is True


def test_is_within_refresh_grace_returns_false_when_not_expired():
    """Token still valid — not in the grace period."""
    token = create_access_token({"sub": "u"})
    assert is_within_refresh_grace(token) is False


def test_is_within_refresh_grace_returns_false_when_beyond_grace():
    """Token expired well past the grace window — returns False."""
    grace_hours = settings.jwt_refresh_grace_hours
    payload = {
        "sub": "u",
        "iat": int(time.time()) - (grace_hours + 2) * 3600,
        "exp": int(time.time()) - (grace_hours + 1) * 3600,
    }
    token = jose_jwt.encode(
        payload, settings.jwt_secret, algorithm=settings.jwt_algorithm
    )
    assert is_within_refresh_grace(token) is False


def test_is_within_refresh_grace_returns_false_on_invalid_token():
    """Completely invalid token — returns False without raising."""
    assert is_within_refresh_grace("garbage.token.value") is False
