"""Code Assistant: explain / generate / debug / improve, plus sandboxed runs."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request

from ..config import settings
from ..deps import CurrentUser, DbSession, ai_limiter, enforce
from ..models import UsageEvent
from ..schemas import (
    CodeAssistRequest,
    CodeAssistResponse,
    CodeRequest,
    CodeRunResult,
)
from ..services import code_runner
from ..services.providers import ProviderError, complete_chat
from .chat import credentials_for

router = APIRouter(prefix="/code", tags=["code"])

LANGUAGES = [
    "python", "javascript", "typescript", "java", "kotlin", "c", "cpp",
    "php", "html", "css", "go", "rust", "sql", "bash",
]

_ACTION_INSTRUCTIONS = {
    "explain": (
        "Explain this {language} code for a developer. Cover: what it does, "
        "how the data flows, any non-obvious behaviour, and any bug or risk "
        "you can see. Use short sections and reference line numbers."
    ),
    "generate": (
        "Write complete, runnable {language} code for the request below. "
        "Include only the code plus a brief note on how to run it. "
        "No placeholders, no TODOs, no omitted bodies."
    ),
    "debug": (
        "Find and fix the bugs in this {language} code. For each bug give: "
        "the line, why it is wrong, and the corrected code. Then give the "
        "complete fixed file."
    ),
    "improve": (
        "Refactor this {language} code for clarity, correctness and "
        "performance. Keep behaviour identical. Explain each change briefly, "
        "then output the full improved file."
    ),
}


@router.get("/languages", response_model=dict)
async def languages() -> dict:
    return {
        "editor": LANGUAGES,
        "executable": code_runner.available_languages(),
        "exec_enabled": settings.code_exec_enabled,
    }


@router.post("/run", response_model=CodeRunResult)
async def run(payload: CodeRequest, request: Request, user: CurrentUser) -> CodeRunResult:
    enforce(ai_limiter, request, amount=0.5)
    request.state.user_id = user.id
    try:
        result = await code_runner.run_code(payload.language, payload.code, payload.stdin)
    except code_runner.ExecutionError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return CodeRunResult(**result)


@router.post("/assist", response_model=CodeAssistResponse)
async def assist(
    payload: CodeAssistRequest, request: Request, user: CurrentUser, db: DbSession
) -> CodeAssistResponse:
    enforce(ai_limiter, request)
    request.state.user_id = user.id

    if payload.action in {"explain", "debug", "improve"} and not payload.code.strip():
        raise HTTPException(
            status_code=422, detail=f"'{payload.action}' needs code to work on."
        )
    if payload.action == "generate" and not payload.instruction.strip():
        raise HTTPException(
            status_code=422, detail="Describe what the code should do."
        )

    try:
        creds, _ = await credentials_for(db, user.id, "gpt-4o-mini", None)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    instruction = _ACTION_INSTRUCTIONS[payload.action].format(language=payload.language)
    parts = [instruction]
    if payload.instruction.strip():
        parts.append(f"REQUEST:\n{payload.instruction.strip()}")
    if payload.code.strip():
        parts.append(f"```{payload.language}\n{payload.code}\n```")

    messages = [
        {
            "role": "system",
            "content": (
                "You are RUK AI's code assistant. Be exact. Output code in fenced "
                "blocks tagged with the language. Never invent library APIs that "
                "do not exist; if unsure, say so."
            ),
        },
        {"role": "user", "content": "\n\n".join(parts)},
    ]
    try:
        answer = await complete_chat(creds, "gpt-4o-mini", messages, max_tokens=3000)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    db.add(UsageEvent(user_id=user.id, kind="code", provider=creds.spec.name, model="gpt-4o-mini"))
    await db.commit()
    return CodeAssistResponse(
        action=payload.action,
        language=payload.language,
        answer=answer,
        model="gpt-4o-mini",
    )
