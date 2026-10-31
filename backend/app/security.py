"""Password hashing, JWT issuance/verification and key encryption helpers.

Password hashing uses PBKDF2-HMAC-SHA256 from the standard library with a
per-password salt, so the backend installs without any native wheels.
``cryptography`` (already required for Fernet key encryption) is used when
available to upgrade the KDF, keeping the deployment dependency-light.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Literal

import jwt
from cryptography.fernet import Fernet, InvalidToken

from .config import settings

PBKDF2_ROUNDS = 260_000
_ALGO = "pbkdf2_sha256"
TokenKind = Literal["access", "refresh"]


# ---------------------------------------------------------------- passwords
def hash_password(password: str) -> str:
    """Return ``pbkdf2_sha256$rounds$salt_b64$hash_b64``."""
    if not password:
        raise ValueError("password must not be empty")
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ROUNDS)
    return "{algo}${rounds}${salt}${digest}".format(
        algo=_ALGO,
        rounds=PBKDF2_ROUNDS,
        salt=base64.b64encode(salt).decode(),
        digest=base64.b64encode(digest).decode(),
    )


def verify_password(password: str, stored: str | None) -> bool:
    """Constant-time verification against a stored hash."""
    if not password or not stored:
        return False
    try:
        algo, rounds_s, salt_b64, digest_b64 = stored.split("$", 3)
        if algo != _ALGO:
            return False
        rounds = int(rounds_s)
        salt = base64.b64decode(salt_b64)
        expected = base64.b64decode(digest_b64)
    except (ValueError, TypeError):
        return False
    candidate = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, rounds)
    return hmac.compare_digest(candidate, expected)


# --------------------------------------------------------------------- JWT
def _now() -> datetime:
    return datetime.now(timezone.utc)


def create_token(subject: str, kind: TokenKind = "access") -> str:
    """Sign a JWT for ``subject`` (the user id)."""
    if kind == "access":
        expires = _now() + timedelta(minutes=settings.access_token_minutes)
    else:
        expires = _now() + timedelta(days=settings.refresh_token_days)
    payload: dict[str, Any] = {
        "sub": subject,
        "iat": int(_now().timestamp()),
        "exp": int(expires.timestamp()),
        "typ": kind,
        "jti": secrets.token_urlsafe(12),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


class TokenError(Exception):
    """Raised when a JWT is missing, malformed, expired or of wrong type."""


def decode_token(token: str, kind: TokenKind = "access") -> dict[str, Any]:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
            options={"require": ["exp", "sub", "typ"]},
        )
    except jwt.ExpiredSignatureError as exc:
        raise TokenError("token expired") from exc
    except jwt.InvalidTokenError as exc:
        raise TokenError("invalid token") from exc
    if payload.get("typ") != kind:
        raise TokenError("wrong token type")
    return payload


def new_refresh_pair(user_id: str) -> tuple[str, str]:
    return create_token(user_id, "access"), create_token(user_id, "refresh")


# ------------------------------------------------------- symmetric encryption
def _fernet() -> Fernet:
    """Build the Fernet used to encrypt provider API keys at rest."""
    configured = settings.token_encryption_key
    if configured:
        key = configured.encode()
        # Accept either a raw Fernet key or an arbitrary passphrase.
        try:
            return Fernet(key)
        except (ValueError, TypeError):
            derived = base64.urlsafe_b64encode(
                hashlib.sha256(key).digest()
            )
            return Fernet(derived)
    derived = base64.urlsafe_b64encode(
        hashlib.sha256(settings.jwt_secret.encode()).digest()
    )
    return Fernet(derived)


def encrypt_secret(plaintext: str) -> str:
    return _fernet().encrypt(plaintext.encode("utf-8")).decode("ascii")


def decrypt_secret(token: str) -> str:
    try:
        return _fernet().decrypt(token.encode("ascii")).decode("utf-8")
    except InvalidToken as exc:
        raise TokenError("stored secret cannot be decrypted with this key") from exc


def mask_key(key: str) -> str:
    """Return a display hint such as ``sk-...4f2a`` that cannot be reused."""
    if not key:
        return ""
    if len(key) <= 8:
        return "*" * len(key)
    return f"{key[:3]}...{key[-4:]}"
