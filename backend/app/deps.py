"""Shared FastAPI dependencies: current user, DB session, rate limits."""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import settings
from .database import get_db
from .models import User
from .ratelimit import RateLimiter
from .security import TokenError, decode_token

bearer = HTTPBearer(auto_error=False)

general_limiter = RateLimiter(settings.rate_limit_per_minute, settings.rate_limit_burst)
ai_limiter = RateLimiter(settings.ai_rate_limit_per_minute)
auth_limiter = RateLimiter(10, 10)


class AuthError(HTTPException):
    def __init__(self, detail: str = "Not authenticated") -> None:
        super().__init__(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=detail,
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    if credentials is None or not credentials.credentials:
        raise AuthError("Missing bearer token")
    try:
        payload = decode_token(credentials.credentials, "access")
    except TokenError as exc:
        raise AuthError(str(exc)) from exc
    user_id = payload.get("sub")
    if not user_id:
        raise AuthError("Token has no subject")
    user = await db.get(User, user_id)
    if user is None or not user.is_active:
        raise AuthError("Account not found or disabled")
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
DbSession = Annotated[AsyncSession, Depends(get_db)]


def client_key(request: Request) -> str:
    """Rate-limit key: the authenticated user when known, else the client IP."""
    user_id = getattr(request.state, "user_id", None)
    if user_id:
        return f"user:{user_id}"
    forwarded = request.headers.get("x-forwarded-for", "")
    ip = forwarded.split(",")[0].strip() if forwarded else ""
    if not ip and request.client:
        ip = request.client.host
    return f"ip:{ip or 'unknown'}"


def enforce(limiter: RateLimiter, request: Request, amount: float = 1.0) -> None:
    allowed, retry_after = limiter.consume(client_key(request), amount)
    if not allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Rate limit exceeded. Please slow down.",
            headers={"Retry-After": str(max(1, int(retry_after) + 1))},
        )


async def find_user_by_email(db: AsyncSession, email: str) -> User | None:
    result = await db.execute(select(User).where(User.email == email.lower().strip()))
    return result.scalar_one_or_none()
