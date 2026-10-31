"""Image and video generation jobs."""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException, Request, Response
from sqlalchemy import select

from ..deps import CurrentUser, DbSession, ai_limiter, enforce
from ..models import Generation, UsageEvent
from ..schemas import GenerationOut, ImageRequest, VideoRequest
from ..services import generation as gen_service
from ..services.providers import Credentials, ProviderError
from .chat import credentials_for

router = APIRouter(prefix="/generations", tags=["generations"])
log = logging.getLogger("rukai.generations")


@router.get("", response_model=list[GenerationOut])
async def list_generations(
    user: CurrentUser, db: DbSession, kind: str | None = None, limit: int = 30
) -> list[GenerationOut]:
    query = select(Generation).where(Generation.user_id == user.id)
    if kind in {"image", "video"}:
        query = query.where(Generation.kind == kind)
    query = query.order_by(Generation.created_at.desc()).limit(min(limit, 100))
    result = await db.execute(query)
    return [GenerationOut.model_validate(row) for row in result.scalars()]


@router.post("/image", response_model=GenerationOut)
async def create_image(
    payload: ImageRequest, request: Request, user: CurrentUser, db: DbSession
) -> GenerationOut:
    enforce(ai_limiter, request)
    request.state.user_id = user.id

    try:
        creds, stored = await credentials_for(db, user.id, payload.model, None)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    record = Generation(
        user_id=user.id,
        kind="image",
        prompt=payload.prompt,
        provider=creds.spec.name,
        model=payload.model,
        aspect_ratio=payload.aspect_ratio,
        quality=payload.quality,
        status="running",
    )
    db.add(record)
    await db.flush()

    try:
        result = await gen_service.generate_image(
            creds,
            prompt=payload.prompt,
            aspect_ratio=payload.aspect_ratio,
            quality=payload.quality,
            model=payload.model,
        )
        record.status = result["status"]
        record.asset_url = result.get("asset_url")
    except ProviderError as exc:
        record.status = "failed"
        record.error = exc.message
        await db.commit()
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc
    except Exception as exc:  # noqa: BLE001
        record.status = "failed"
        record.error = str(exc)[:400]
        await db.commit()
        log.exception("image generation failed")
        raise HTTPException(status_code=502, detail=f"Image generation failed: {exc}") from exc

    db.add(UsageEvent(user_id=user.id, kind="image", provider=creds.spec.name, model=payload.model))
    if stored is not None:
        from datetime import datetime, timezone

        stored.last_used_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(record)
    return GenerationOut.model_validate(record)


@router.post("/video", response_model=GenerationOut)
async def create_video(
    payload: VideoRequest, request: Request, user: CurrentUser, db: DbSession
) -> GenerationOut:
    enforce(ai_limiter, request)
    request.state.user_id = user.id
    model = "fal-ai/ltx-video"

    try:
        creds, stored = await credentials_for(db, user.id, model, "fal")
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    record = Generation(
        user_id=user.id,
        kind="video",
        prompt=payload.prompt,
        provider=creds.spec.name,
        model=model,
        aspect_ratio=payload.aspect_ratio,
        quality="standard",
        status="pending",
    )
    db.add(record)
    await db.flush()

    try:
        result = await gen_service.submit_video(
            creds,
            prompt=payload.prompt,
            aspect_ratio=payload.aspect_ratio,
            duration_seconds=payload.duration_seconds,
            image_url=payload.image_url,
            model=model,
        )
        record.status = result["status"]
        record.provider_job_id = result.get("job_id")
        record.asset_url = result.get("asset_url")
    except ProviderError as exc:
        record.status = "failed"
        record.error = exc.message
        await db.commit()
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    db.add(UsageEvent(user_id=user.id, kind="video", provider=creds.spec.name, model=model))
    await db.commit()
    await db.refresh(record)
    return GenerationOut.model_validate(record)


@router.get("/{generation_id}", response_model=GenerationOut)
async def get_generation(
    generation_id: str, user: CurrentUser, db: DbSession, refresh: bool = False
) -> GenerationOut:
    record = await db.get(Generation, generation_id)
    if record is None or record.user_id != user.id:
        raise HTTPException(status_code=404, detail="Generation not found")

    # Poll the provider for in-flight asynchronous video jobs.
    if (
        refresh
        and record.kind == "video"
        and record.status in {"pending", "running"}
        and record.provider_job_id
    ):
        try:
            creds, _ = await credentials_for(db, user.id, record.model, record.provider)
            state = await gen_service.poll_video(creds, record.provider_job_id, record.model)
            record.status = state["status"]
            record.asset_url = state.get("asset_url") or record.asset_url
            record.error = state.get("error")
            await db.commit()
            await db.refresh(record)
        except ProviderError as exc:
            record.error = exc.message
            await db.commit()
    return GenerationOut.model_validate(record)


@router.delete("/{generation_id}", status_code=204, response_class=Response)
async def delete_generation(generation_id: str, user: CurrentUser, db: DbSession) -> Response:
    record = await db.get(Generation, generation_id)
    if record is None or record.user_id != user.id:
        raise HTTPException(status_code=404, detail="Generation not found")
    await db.delete(record)
    await db.commit()
