"""Chat: conversations, streaming completions, regeneration and models."""

from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Query, Request, Response, status
from fastapi.responses import StreamingResponse
from sqlalchemy import delete, func, select

from ..config import settings
from ..deps import CurrentUser, DbSession, ai_limiter, enforce
from ..models import ApiKey, Conversation, Message, UsageEvent
from ..schemas import (
    ChatRequest,
    ConversationDetail,
    ConversationOut,
    MessageOut,
    ModelInfo,
)
from ..security import decrypt_secret
from ..services import providers
from ..services.providers import Credentials, ProviderError
from ..services.tools import redact

router = APIRouter(prefix="/chat", tags=["chat"])
log = logging.getLogger("rukai.chat")

SYSTEM_PROMPT = (
    "You are RUK AI, a capable, friendly assistant running inside a mobile app. "
    "Answer precisely and completely. Use Markdown; keep code in fenced blocks "
    "with a language tag. Never claim to have performed an action you did not "
    "perform. If a request is ambiguous, state the assumption you made and "
    "continue rather than stalling."
)


async def _user_key(db, user_id: str, provider: str) -> ApiKey | None:
    result = await db.execute(
        select(ApiKey).where(ApiKey.user_id == user_id, ApiKey.provider == provider)
    )
    return result.scalar_one_or_none()


async def credentials_for(db, user_id: str, model: str, provider: str | None):
    spec = providers.resolve_provider(model, provider)
    stored = await _user_key(db, user_id, spec.name)
    if stored is not None:
        try:
            api_key = decrypt_secret(stored.encrypted_key)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=(
                    f"Your stored {spec.label} key could not be decrypted "
                    "(the server encryption key changed). Please re-add it."
                ),
            ) from exc
        return Credentials(spec, api_key, stored.base_url or spec.base_url), stored
    return Credentials.for_provider(spec), None


async def daily_message_count(db, user_id: str) -> int:
    start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    result = await db.execute(
        select(func.count(Message.id))
        .join(Conversation, Conversation.id == Message.conversation_id)
        .where(
            Conversation.user_id == user_id,
            Message.role == "assistant",
            Message.created_at >= start,
        )
    )
    return int(result.scalar() or 0)


async def enforce_quota(db, user) -> None:
    used = await daily_message_count(db, user.id)
    limit = (
        settings.pro_daily_messages if user.plan == "pro" else settings.free_daily_messages
    )
    if used >= limit:
        raise HTTPException(
            status_code=status.HTTP_402_PAYMENT_REQUIRED,
            detail=(
                f"Daily message limit reached ({used}/{limit}) on the "
                f"{user.plan} plan. Upgrade to Pro for a higher limit."
            ),
        )


@router.get("/models", response_model=list[ModelInfo])
async def models(kind: str = Query(default="chat", pattern="^(chat|image|video)$")) -> list[ModelInfo]:
    return [ModelInfo(**item) for item in providers.list_models(kind)]


@router.get("/conversations", response_model=list[ConversationOut])
async def list_conversations(user: CurrentUser, db: DbSession) -> list[ConversationOut]:
    result = await db.execute(
        select(Conversation)
        .where(Conversation.user_id == user.id)
        .order_by(Conversation.updated_at.desc())
        .limit(100)
    )
    conversations = list(result.scalars())
    if not conversations:
        return []

    # One grouped count instead of a query per conversation.
    counts = await db.execute(
        select(Message.conversation_id, func.count(Message.id))
        .where(Message.conversation_id.in_([c.id for c in conversations]))
        .group_by(Message.conversation_id)
    )
    tally = {conversation_id: total for conversation_id, total in counts.all()}

    out: list[ConversationOut] = []
    for conversation in conversations:
        item = ConversationOut.model_validate(conversation)
        item.message_count = int(tally.get(conversation.id, 0))
        out.append(item)
    return out


@router.get("/conversations/{conversation_id}", response_model=ConversationDetail)
async def get_conversation(
    conversation_id: str, user: CurrentUser, db: DbSession
) -> ConversationDetail:
    conversation = await db.get(Conversation, conversation_id)
    if conversation is None or conversation.user_id != user.id:
        raise HTTPException(status_code=404, detail="Conversation not found")
    result = await db.execute(
        select(Message)
        .where(Message.conversation_id == conversation_id)
        .order_by(Message.created_at)
    )
    detail = ConversationDetail.model_validate(conversation)
    detail.messages = [MessageOut.model_validate(row) for row in result.scalars()]
    return detail


@router.delete("/conversations/{conversation_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def delete_conversation(conversation_id: str, user: CurrentUser, db: DbSession) -> Response:
    conversation = await db.get(Conversation, conversation_id)
    if conversation is None or conversation.user_id != user.id:
        raise HTTPException(status_code=404, detail="Conversation not found")
    await db.execute(delete(Message).where(Message.conversation_id == conversation_id))
    await db.delete(conversation)
    await db.commit()


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/completions")
async def completions(payload: ChatRequest, request: Request, user: CurrentUser, db: DbSession):
    """Stream a completion as Server-Sent Events.

    Events: ``meta`` (ids/model), ``delta`` (text), ``done`` (usage),
    ``error`` (message). The assistant message is persisted on completion.
    """
    enforce(ai_limiter, request)
    request.state.user_id = user.id
    await enforce_quota(db, user)

    # Resolve credentials before opening the stream so failures return a
    # normal 4xx/5xx instead of a broken event stream.
    try:
        creds, stored_key = await credentials_for(db, user.id, payload.model, payload.provider)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    conversation = None
    if payload.conversation_id:
        conversation = await db.get(Conversation, payload.conversation_id)
        if conversation is None or conversation.user_id != user.id:
            raise HTTPException(status_code=404, detail="Conversation not found")
    if conversation is None:
        first_user = next(
            (m.content for m in payload.messages if m.role == "user"), "New chat"
        )
        conversation = Conversation(
            user_id=user.id,
            title=redact(first_user.strip())[:80] or "New chat",
            model=payload.model,
        )
        db.add(conversation)
        await db.flush()

    history: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    if payload.messages:
        for message in payload.messages[-40:]:
            history.append({"role": message.role, "content": redact(message.content)})
    else:
        result = await db.execute(
            select(Message)
            .where(Message.conversation_id == conversation.id)
            .order_by(Message.created_at)
            .limit(settings.max_conversation_messages)
        )
        for row in result.scalars():
            history.append({"role": row.role, "content": row.content})

    last_user = next(
        (m["content"] for m in reversed(history) if m["role"] == "user"), ""
    )
    if last_user:
        db.add(
            Message(
                conversation_id=conversation.id,
                role="user",
                content=last_user,
                model=payload.model,
            )
        )
        conversation.updated_at = datetime.now(timezone.utc)
        await db.commit()

    conv_id = conversation.id
    model = payload.model

    async def event_stream():
        buffer: list[str] = []
        yield _sse("meta", {"conversation_id": conv_id, "model": model, "provider": creds.spec.name})
        try:
            async for delta in providers.stream_chat(
                creds,
                model,
                history,
                temperature=payload.temperature,
                max_tokens=payload.max_tokens,
            ):
                buffer.append(delta)
                yield _sse("delta", {"text": delta})
        except ProviderError as exc:
            log.warning("provider error: %s", exc.message)
            yield _sse("error", {"message": exc.message, "status": exc.status})
            return
        except Exception as exc:  # noqa: BLE001
            log.exception("unexpected streaming failure")
            yield _sse("error", {"message": f"Unexpected error: {exc}", "status": 500})
            return

        text = redact("".join(buffer)).strip()
        if not text:
            yield _sse("error", {"message": "The model returned an empty response.", "status": 502})
            return

        # Persist in a fresh session: the request session may already be
        # closing by the time the generator finishes.
        from ..database import SessionFactory

        async with SessionFactory() as session:
            session.add(
                Message(
                    conversation_id=conv_id,
                    role="assistant",
                    content=text,
                    model=model,
                )
            )
            session.add(
                UsageEvent(
                    user_id=user.id,
                    kind="chat",
                    provider=creds.spec.name,
                    model=model,
                    units=1,
                )
            )
            conv = await session.get(Conversation, conv_id)
            if conv is not None:
                conv.updated_at = datetime.now(timezone.utc)
            if stored_key is not None:
                stored = await session.get(ApiKey, stored_key.id)
                if stored is not None:
                    stored.last_used_at = datetime.now(timezone.utc)
            await session.commit()

        yield _sse("done", {"conversation_id": conv_id, "chars": len(text)})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@router.post("/regenerate/{conversation_id}", response_model=dict)
async def regenerate(
    conversation_id: str, user: CurrentUser, db: DbSession, model: str | None = None
) -> dict:
    """Drop the last assistant turn so the client can re-request it.

    The client calls ``/completions`` again with the same conversation id;
    this endpoint just removes the answer that is being replaced so history
    does not accumulate duplicates.
    """
    conversation = await db.get(Conversation, conversation_id)
    if conversation is None or conversation.user_id != user.id:
        raise HTTPException(status_code=404, detail="Conversation not found")
    result = await db.execute(
        select(Message)
        .where(Message.conversation_id == conversation_id, Message.role == "assistant")
        .order_by(Message.created_at.desc())
        .limit(1)
    )
    last = result.scalar_one_or_none()
    if last is not None:
        await db.delete(last)
        await db.commit()
    return {"conversation_id": conversation_id, "removed": last is not None, "model": model or conversation.model}
