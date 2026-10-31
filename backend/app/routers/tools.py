"""The AI tools library: writing, summarising, translation, math, SEO, research."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request

from ..deps import CurrentUser, DbSession, ai_limiter, enforce
from ..models import UsageEvent
from ..schemas import ToolRequest, ToolResponse
from ..services.providers import ProviderError, complete_chat
from ..services.tools import TOOL_SPECS, ToolError, build_messages
from .chat import credentials_for

router = APIRouter(prefix="/tools", tags=["tools"])


@router.get("", response_model=list[dict])
async def list_tools() -> list[dict]:
    return [
        {
            "id": key,
            "label": spec["label"],
            "description": spec["system"].split(".")[0] + ".",
            "options": sorted(spec["defaults"].keys()),
        }
        for key, spec in TOOL_SPECS.items()
    ]


@router.post("/run", response_model=ToolResponse)
async def run_tool(
    payload: ToolRequest, request: Request, user: CurrentUser, db: DbSession
) -> ToolResponse:
    enforce(ai_limiter, request)
    request.state.user_id = user.id

    try:
        messages = build_messages(payload.tool, payload.input, payload.options)
    except ToolError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    try:
        creds, _ = await credentials_for(db, user.id, payload.model, None)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    try:
        output = await complete_chat(creds, payload.model, messages, max_tokens=3000)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    db.add(
        UsageEvent(
            user_id=user.id, kind=f"tool:{payload.tool}", provider=creds.spec.name, model=payload.model
        )
    )
    await db.commit()
    return ToolResponse(tool=payload.tool, output=output, model=payload.model)
