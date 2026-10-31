"""Usage metering and the profile statistics endpoint."""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter
from sqlalchemy import func, select

from ..config import settings
from ..deps import CurrentUser, DbSession
from ..models import Conversation, Generation, Message, UsageEvent
from ..schemas import UsageSummary

router = APIRouter(prefix="/usage", tags=["usage"])


@router.get("/summary", response_model=UsageSummary)
async def summary(user: CurrentUser, db: DbSession) -> UsageSummary:
    day_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    month_start = day_start.replace(day=1)

    messages_today = int(
        (
            await db.execute(
                select(func.count(Message.id))
                .join(Conversation, Conversation.id == Message.conversation_id)
                .where(
                    Conversation.user_id == user.id,
                    Message.role == "assistant",
                    Message.created_at >= day_start,
                )
            )
        ).scalar()
        or 0
    )
    images = int(
        (
            await db.execute(
                select(func.count(Generation.id)).where(
                    Generation.user_id == user.id,
                    Generation.kind == "image",
                    Generation.created_at >= month_start,
                )
            )
        ).scalar()
        or 0
    )
    videos = int(
        (
            await db.execute(
                select(func.count(Generation.id)).where(
                    Generation.user_id == user.id,
                    Generation.kind == "video",
                    Generation.created_at >= month_start,
                )
            )
        ).scalar()
        or 0
    )
    tokens = (
        await db.execute(
            select(
                func.coalesce(func.sum(Message.tokens_in), 0),
                func.coalesce(func.sum(Message.tokens_out), 0),
            )
            .join(Conversation, Conversation.id == Message.conversation_id)
            .where(Conversation.user_id == user.id)
        )
    ).one()

    limit = (
        settings.pro_daily_messages if user.plan == "pro" else settings.free_daily_messages
    )
    return UsageSummary(
        plan=user.plan,
        messages_today=messages_today,
        daily_limit=limit,
        images_this_month=images,
        videos_this_month=videos,
        total_tokens_in=int(tokens[0] or 0),
        total_tokens_out=int(tokens[1] or 0),
        remaining_today=max(0, limit - messages_today),
    )


@router.get("/events", response_model=list[dict])
async def events(user: CurrentUser, db: DbSession, limit: int = 50) -> list[dict]:
    result = await db.execute(
        select(UsageEvent)
        .where(UsageEvent.user_id == user.id)
        .order_by(UsageEvent.created_at.desc())
        .limit(min(limit, 200))
    )
    return [
        {
            "kind": row.kind,
            "provider": row.provider,
            "model": row.model,
            "units": row.units,
            "created_at": row.created_at,
        }
        for row in result.scalars()
    ]
