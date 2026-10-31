"""Per-user provider API key management.

Keys are encrypted with Fernet before they touch the database and are never
returned in plaintext. The API only ever exposes a masked hint.
"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Response, status
from sqlalchemy import select

from ..deps import CurrentUser, DbSession
from ..models import ApiKey
from ..schemas import ApiKeyIn, ApiKeyOut
from ..security import encrypt_secret, mask_key

router = APIRouter(prefix="/keys", tags=["keys"])

SUPPORTED = ("openai", "gemini", "openrouter", "together", "stability", "fal")


@router.get("", response_model=list[ApiKeyOut])
async def list_keys(user: CurrentUser, db: DbSession) -> list[ApiKeyOut]:
    result = await db.execute(
        select(ApiKey).where(ApiKey.user_id == user.id).order_by(ApiKey.provider)
    )
    return [
        ApiKeyOut(
            provider=row.provider,
            key_hint=row.key_hint,
            base_url=row.base_url,
            created_at=row.created_at,
            last_used_at=row.last_used_at,
        )
        for row in result.scalars()
    ]


@router.put("", response_model=ApiKeyOut)
async def upsert_key(payload: ApiKeyIn, user: CurrentUser, db: DbSession) -> ApiKeyOut:
    if payload.provider not in SUPPORTED:
        raise HTTPException(
            status_code=422,
            detail=f"Unsupported provider. Choose one of: {', '.join(SUPPORTED)}",
        )
    key = payload.api_key.strip()
    if len(key) < 8:
        raise HTTPException(status_code=422, detail="That key looks too short.")

    result = await db.execute(
        select(ApiKey).where(
            ApiKey.user_id == user.id, ApiKey.provider == payload.provider
        )
    )
    record = result.scalar_one_or_none()
    if record is None:
        record = ApiKey(user_id=user.id, provider=payload.provider)
        db.add(record)

    record.encrypted_key = encrypt_secret(key)
    record.key_hint = mask_key(key)
    record.base_url = (payload.base_url or "").strip() or None
    await db.commit()
    await db.refresh(record)
    return ApiKeyOut(
        provider=record.provider,
        key_hint=record.key_hint,
        base_url=record.base_url,
        created_at=record.created_at,
        last_used_at=record.last_used_at,
    )


@router.delete("/{provider}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
async def delete_key(provider: str, user: CurrentUser, db: DbSession) -> Response:
    result = await db.execute(
        select(ApiKey).where(ApiKey.user_id == user.id, ApiKey.provider == provider)
    )
    record = result.scalar_one_or_none()
    if record is None:
        raise HTTPException(status_code=404, detail="No key stored for that provider.")
    await db.delete(record)
    await db.commit()


@router.post("/{provider}/test", response_model=dict)
async def test_key(provider: str, user: CurrentUser, db: DbSession) -> dict:
    """Make one cheap real call to confirm the stored key works."""
    from ..services.providers import Credentials, ProviderError, SPECS, complete_chat
    from ..security import decrypt_secret

    spec = SPECS.get(provider)
    if spec is None:
        raise HTTPException(status_code=404, detail="Unknown provider.")
    result = await db.execute(
        select(ApiKey).where(ApiKey.user_id == user.id, ApiKey.provider == provider)
    )
    record = result.scalar_one_or_none()
    if record is None:
        raise HTTPException(status_code=404, detail="No key stored for that provider.")

    try:
        creds = Credentials(spec, decrypt_secret(record.encrypted_key), record.base_url or spec.base_url)
        model = spec.chat_models[0] if spec.chat_models else "gpt-4o-mini"
        reply = await complete_chat(
            creds,
            model,
            [{"role": "user", "content": "Reply with the single word: ok"}],
            max_tokens=8,
        )
    except ProviderError as exc:
        return {"ok": False, "provider": provider, "detail": exc.message}
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "provider": provider, "detail": str(exc)[:300]}

    record.last_used_at = datetime.now(timezone.utc)
    await db.commit()
    return {"ok": True, "provider": provider, "model": model, "reply": reply[:80]}
