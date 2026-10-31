"""Transparent report share links.

Design rules enforced here:
* A link is created only by its owner, for content the owner supplied.
* Expiry is mandatory and bounded; links can be revoked at any time.
* Optional password protection uses the same PBKDF2 hashing as accounts.
* Access logging stores a *truncated* IP so the log supports abuse
  detection without becoming a location record.
* Nothing is collected covertly: the public page states what is logged.
"""

from __future__ import annotations

import secrets
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, HTTPException, Request, Response, status
from sqlalchemy import select

from ..config import settings
from ..deps import CurrentUser, DbSession, general_limiter, enforce
from ..models import LinkAccessLog, ReportLink
from ..schemas import LinkAccessOut, ReportLinkIn, ReportLinkOut
from ..security import hash_password, verify_password

router = APIRouter(prefix="/links", tags=["links"])


def truncate_ip(raw: str) -> str:
    """Zero the host portion so the stored value is not a precise location."""
    if not raw:
        return ""
    if ":" in raw:  # IPv6 -> keep the /48 network
        parts = raw.split(":")
        return ":".join(parts[:3]) + "::"
    parts = raw.split(".")
    if len(parts) == 4:
        return ".".join(parts[:3] + ["0"])
    return raw


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else ""


def _to_out(link: ReportLink) -> ReportLinkOut:
    out = ReportLinkOut.model_validate(link)
    out.url = f"{settings.public_base_url.rstrip('/')}/r/{link.id}"
    return out


@router.post("", response_model=ReportLinkOut, status_code=status.HTTP_201_CREATED)
async def create_link(
    payload: ReportLinkIn, request: Request, user: CurrentUser, db: DbSession
) -> ReportLinkOut:
    enforce(general_limiter, request)
    request.state.user_id = user.id
    link = ReportLink(
        user_id=user.id,
        title=payload.title.strip() or "RUK AI report",
        content=payload.content,
        content_type=payload.content_type,
        password_hash=hash_password(payload.password) if payload.password else None,
        expires_at=datetime.now(timezone.utc) + timedelta(hours=payload.expires_in_hours),
    )
    db.add(link)
    await db.commit()
    await db.refresh(link)
    return _to_out(link)


@router.get("", response_model=list[ReportLinkOut])
async def list_links(user: CurrentUser, db: DbSession) -> list[ReportLinkOut]:
    result = await db.execute(
        select(ReportLink)
        .where(ReportLink.user_id == user.id)
        .order_by(ReportLink.created_at.desc())
        .limit(100)
    )
    return [_to_out(row) for row in result.scalars()]


@router.post("/{link_id}/revoke", response_model=ReportLinkOut)
async def revoke(link_id: str, user: CurrentUser, db: DbSession) -> ReportLinkOut:
    link = await db.get(ReportLink, link_id)
    if link is None or link.user_id != user.id:
        raise HTTPException(status_code=404, detail="Link not found")
    link.revoked = True
    await db.commit()
    await db.refresh(link)
    return _to_out(link)


@router.delete("/{link_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def delete_link(link_id: str, user: CurrentUser, db: DbSession) -> Response:
    link = await db.get(ReportLink, link_id)
    if link is None or link.user_id != user.id:
        raise HTTPException(status_code=404, detail="Link not found")
    await db.delete(link)
    await db.commit()


@router.get("/{link_id}/access", response_model=list[LinkAccessOut])
async def access_log(link_id: str, user: CurrentUser, db: DbSession) -> list[LinkAccessOut]:
    link = await db.get(ReportLink, link_id)
    if link is None or link.user_id != user.id:
        raise HTTPException(status_code=404, detail="Link not found")
    result = await db.execute(
        select(LinkAccessLog)
        .where(LinkAccessLog.link_id == link_id)
        .order_by(LinkAccessLog.created_at.desc())
        .limit(200)
    )
    return [LinkAccessOut.model_validate(row) for row in result.scalars()]


# ------------------------------------------------------------- public access


public_router = APIRouter(prefix="/r", tags=["public"])


@public_router.get("/{link_id}", response_model=dict)
async def open_link(
    link_id: str, request: Request, db: DbSession, password: str | None = None
) -> dict:
    """Public read of a shared report.

    Returns 404 for unknown, revoked or expired links so their existence is
    not disclosed. Every attempt is logged with a truncated IP.
    """
    enforce(general_limiter, request)
    link = await db.get(ReportLink, link_id)
    now = datetime.now(timezone.utc)

    def log(outcome: str) -> None:
        db.add(
            LinkAccessLog(
                link_id=link_id,
                truncated_ip=truncate_ip(_client_ip(request)),
                user_agent=(request.headers.get("user-agent") or "")[:255],
                outcome=outcome,
            )
        )

    if link is None:
        raise HTTPException(status_code=404, detail="This link does not exist.")

    expired = link.expires_at is not None and link.expires_at.replace(
        tzinfo=link.expires_at.tzinfo or timezone.utc
    ) < now
    if link.revoked or expired:
        log("expired" if expired else "revoked")
        await db.commit()
        raise HTTPException(
            status_code=404,
            detail="This link has expired or was revoked by its owner.",
        )

    if link.password_hash:
        if not password:
            log("password_required")
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="This report is password protected.",
            )
        if not verify_password(password, link.password_hash):
            log("bad_password")
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect password."
            )

    link.view_count += 1
    log("ok")
    await db.commit()
    return {
        "id": link.id,
        "title": link.title,
        "content": link.content,
        "content_type": link.content_type,
        "created_at": link.created_at,
        "expires_at": link.expires_at,
        "notice": (
            "This report was shared deliberately by its owner. "
            "Access is logged with a truncated IP address for abuse prevention."
        ),
    }
