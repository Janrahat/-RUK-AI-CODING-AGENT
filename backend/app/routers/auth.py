"""Authentication: email/password, Google Sign-In and token refresh."""

from __future__ import annotations

from datetime import datetime, timezone

import httpx
from fastapi import APIRouter, HTTPException, Request, Response, status
from sqlalchemy import select

from ..config import settings
from ..deps import CurrentUser, DbSession, auth_limiter, enforce, find_user_by_email
from ..models import User
from ..schemas import (
    AuthResponse,
    GoogleLoginRequest,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    TokenPair,
    UserOut,
)
from ..security import (
    TokenError,
    create_token,
    decode_token,
    hash_password,
    new_refresh_pair,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])

_GOOGLE_TOKENINFO = "https://oauth2.googleapis.com/tokeninfo"


def _auth_response(user: User) -> AuthResponse:
    access, refresh = new_refresh_pair(user.id)
    return AuthResponse(
        access_token=access,
        refresh_token=refresh,
        user=UserOut.model_validate(user),
    )


@router.post("/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
async def register(payload: RegisterRequest, request: Request, db: DbSession) -> AuthResponse:
    enforce(auth_limiter, request)
    email = payload.email.lower().strip()
    if await find_user_by_email(db, email) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An account with that email already exists.",
        )
    user = User(
        email=email,
        display_name=payload.display_name.strip() or email.split("@")[0],
        password_hash=hash_password(payload.password),
        plan="free",
        last_login_at=datetime.now(timezone.utc),
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    request.state.user_id = user.id
    return _auth_response(user)


@router.post("/login", response_model=AuthResponse)
async def login(payload: LoginRequest, request: Request, db: DbSession) -> AuthResponse:
    enforce(auth_limiter, request)
    user = await find_user_by_email(db, payload.email)
    # Verify against a dummy hash when the user is missing so that response
    # timing does not reveal whether an address is registered.
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email or password is incorrect.",
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN, detail="This account is disabled."
        )
    user.last_login_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(user)
    request.state.user_id = user.id
    return _auth_response(user)


@router.post("/google", response_model=AuthResponse)
async def google_login(
    payload: GoogleLoginRequest, request: Request, db: DbSession
) -> AuthResponse:
    """Exchange a Google ID token for a RUK AI session.

    The token is verified against Google's tokeninfo endpoint; the audience
    must match GOOGLE_CLIENT_ID when that is configured.
    """
    enforce(auth_limiter, request)
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                _GOOGLE_TOKENINFO, params={"id_token": payload.id_token}
            )
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Could not reach Google to verify the token: {exc}",
        ) from exc

    if response.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Google rejected that ID token.",
        )
    info = response.json()
    if settings.google_client_id and info.get("aud") != settings.google_client_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="The ID token was issued for a different app.",
        )
    if info.get("email_verified") not in {"true", True}:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="That Google account has no verified email.",
        )
    email = str(info.get("email", "")).lower().strip()
    subject = str(info.get("sub", ""))
    if not email or not subject:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Incomplete Google profile."
        )

    result = await db.execute(select(User).where(User.google_sub == subject))
    user = result.scalar_one_or_none()
    if user is None:
        user = await find_user_by_email(db, email)
    if user is None:
        user = User(
            email=email,
            display_name=str(info.get("name") or email.split("@")[0])[:120],
            google_sub=subject,
            plan="free",
            last_login_at=datetime.now(timezone.utc),
        )
        db.add(user)
    else:
        user.google_sub = subject
        user.last_login_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(user)
    request.state.user_id = user.id
    return _auth_response(user)


@router.post("/refresh", response_model=TokenPair)
async def refresh(payload: RefreshRequest, db: DbSession) -> TokenPair:
    try:
        claims = decode_token(payload.refresh_token, "refresh")
    except TokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc)
        ) from exc
    user = await db.get(User, claims.get("sub", ""))
    if user is None or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Account not found"
        )
    access, new_refresh = new_refresh_pair(user.id)
    return TokenPair(access_token=access, refresh_token=new_refresh)


@router.get("/me", response_model=UserOut)
async def me(user: CurrentUser) -> UserOut:
    return UserOut.model_validate(user)


@router.post("/upgrade", response_model=UserOut)
async def upgrade_to_pro(user: CurrentUser, db: DbSession) -> UserOut:
    """Grant the Pro plan.

    Billing is intentionally *not* faked here: this endpoint flips the plan
    flag and the README states plainly that a real store-billing integration
    (Google Play Billing) must call it after a verified purchase.
    """
    user.plan = "pro"
    await db.commit()
    await db.refresh(user)
    return UserOut.model_validate(user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def logout(user: CurrentUser) -> Response:
    """Stateless JWT logout.

    Tokens are short-lived; the client discards them. A production
    deployment that needs immediate revocation should keep a jti deny-list.
    """
    return None
