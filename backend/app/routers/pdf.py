"""PDF Assistant: upload, extract, summarise and question answering."""

from __future__ import annotations

from fastapi import APIRouter, File, HTTPException, Request, Response, UploadFile
from sqlalchemy import select

from ..config import settings
from ..deps import CurrentUser, DbSession, ai_limiter, enforce
from ..models import Document, UsageEvent
from ..schemas import DocumentOut, PdfAskRequest
from ..services import pdf_service
from ..services.providers import ProviderError, complete_chat
from .chat import credentials_for

router = APIRouter(prefix="/pdf", tags=["pdf"])

_QA_SYSTEM = (
    "You answer questions strictly from the supplied document excerpts. "
    "If the answer is not in the excerpts, say so plainly and describe what "
    "the document does cover. Never invent content. Quote short phrases when "
    "they support the answer."
)


@router.post("/upload", response_model=DocumentOut)
async def upload(
    request: Request,
    user: CurrentUser,
    db: DbSession,
    file: UploadFile = File(...),
) -> DocumentOut:
    enforce(ai_limiter, request, amount=0.5)
    request.state.user_id = user.id

    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=422, detail="The uploaded file is empty.")
    if len(payload) > settings.max_upload_bytes:
        raise HTTPException(
            status_code=413,
            detail=(
                f"File is {len(payload) // 1024} KB; the limit is "
                f"{settings.max_upload_bytes // (1024 * 1024)} MB."
            ),
        )
    try:
        extracted = pdf_service.extract_text(payload)
    except pdf_service.PdfError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    if not extracted["text"].strip():
        raise HTTPException(
            status_code=422,
            detail=(
                "No selectable text found. This looks like a scanned PDF; "
                "OCR is not enabled on this server."
            ),
        )

    document = Document(
        user_id=user.id,
        filename=(file.filename or "document.pdf")[:255],
        size_bytes=len(payload),
        page_count=extracted["page_count"],
        text=extracted["text"],
    )
    db.add(document)
    await db.commit()
    await db.refresh(document)
    return DocumentOut.model_validate(document)


@router.get("/documents", response_model=list[DocumentOut])
async def list_documents(user: CurrentUser, db: DbSession) -> list[DocumentOut]:
    result = await db.execute(
        select(Document)
        .where(Document.user_id == user.id)
        .order_by(Document.created_at.desc())
        .limit(50)
    )
    return [DocumentOut.model_validate(row) for row in result.scalars()]


@router.get("/documents/{document_id}/text", response_model=dict)
async def document_text(document_id: str, user: CurrentUser, db: DbSession) -> dict:
    document = await db.get(Document, document_id)
    if document is None or document.user_id != user.id:
        raise HTTPException(status_code=404, detail="Document not found")
    return {
        "id": document.id,
        "filename": document.filename,
        "page_count": document.page_count,
        "text": document.text,
    }


@router.delete("/documents/{document_id}", status_code=204, response_class=Response)
async def delete_document(document_id: str, user: CurrentUser, db: DbSession) -> Response:
    document = await db.get(Document, document_id)
    if document is None or document.user_id != user.id:
        raise HTTPException(status_code=404, detail="Document not found")
    await db.delete(document)
    await db.commit()


@router.post("/documents/{document_id}/summarize", response_model=dict)
async def summarize(
    document_id: str, request: Request, user: CurrentUser, db: DbSession
) -> dict:
    enforce(ai_limiter, request)
    request.state.user_id = user.id
    document = await db.get(Document, document_id)
    if document is None or document.user_id != user.id:
        raise HTTPException(status_code=404, detail="Document not found")

    try:
        creds, _ = await credentials_for(db, user.id, "gpt-4o-mini", None)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    excerpt = document.text[:60_000]
    messages = [
        {
            "role": "system",
            "content": (
                "You summarise documents faithfully. Structure the answer as: "
                "one-line gist, key points, important numbers or dates, and "
                "anything the document leaves unresolved."
            ),
        },
        {
            "role": "user",
            "content": f"Summarise this document ({document.page_count} pages).\n\n{excerpt}",
        },
    ]
    try:
        summary = await complete_chat(creds, "gpt-4o-mini", messages, max_tokens=2000)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    db.add(UsageEvent(user_id=user.id, kind="pdf", provider=creds.spec.name, model="gpt-4o-mini"))
    await db.commit()
    return {"document_id": document.id, "summary": summary}


@router.post("/documents/{document_id}/ask", response_model=dict)
async def ask(
    document_id: str,
    payload: PdfAskRequest,
    request: Request,
    user: CurrentUser,
    db: DbSession,
) -> dict:
    enforce(ai_limiter, request)
    request.state.user_id = user.id
    document = await db.get(Document, document_id)
    if document is None or document.user_id != user.id:
        raise HTTPException(status_code=404, detail="Document not found")

    try:
        creds, _ = await credentials_for(db, user.id, payload.model, None)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    excerpt = pdf_service.select_relevant(document.text, payload.question)
    messages = [
        {"role": "system", "content": _QA_SYSTEM},
        {
            "role": "user",
            "content": (
                f"DOCUMENT EXCERPTS:\n{excerpt}\n\n"
                f"QUESTION: {payload.question}"
            ),
        },
    ]
    try:
        answer = await complete_chat(creds, payload.model, messages, max_tokens=1500)
    except ProviderError as exc:
        raise HTTPException(status_code=exc.status, detail=exc.message) from exc

    db.add(UsageEvent(user_id=user.id, kind="pdf", provider=creds.spec.name, model=payload.model))
    await db.commit()
    return {"document_id": document.id, "question": payload.question, "answer": answer}
