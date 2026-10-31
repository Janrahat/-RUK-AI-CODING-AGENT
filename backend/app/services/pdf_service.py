"""PDF text extraction and question answering.

Extraction uses ``pypdf`` when installed and falls back to a minimal
stdlib reader that pulls text objects out of uncompressed streams, so the
assistant degrades instead of failing.
"""

from __future__ import annotations

import io
import re
from typing import Any

try:  # pragma: no cover - exercised when pypdf is absent
    from pypdf import PdfReader
except ImportError:  # pragma: no cover
    PdfReader = None  # type: ignore[assignment]

MAX_CHARS = 200_000
CHUNK_CHARS = 6_000


class PdfError(Exception):
    pass


def extract_text(payload: bytes) -> dict[str, Any]:
    """Return ``{text, page_count, truncated, engine}``."""
    if not payload.startswith(b"%PDF"):
        raise PdfError("That file is not a PDF (missing %PDF header).")

    if PdfReader is not None:
        try:
            reader = PdfReader(io.BytesIO(payload))
        except Exception as exc:  # noqa: BLE001 - pypdf raises many types
            raise PdfError(f"Could not read the PDF: {exc}") from exc
        pages: list[str] = []
        for index, page in enumerate(reader.pages):
            try:
                pages.append(page.extract_text() or "")
            except Exception:  # noqa: BLE001
                pages.append(f"[page {index + 1}: text could not be extracted]")
        text = "\n\n".join(pages)
        engine = "pypdf"
        page_count = len(reader.pages)
    else:  # pragma: no cover
        text = _stdlib_extract(payload)
        engine = "stdlib"
        page_count = len(re.findall(rb"/Type\s*/Page[^s]", payload)) or 1

    text = re.sub(r"[ \t]+\n", "\n", text).strip()
    truncated = len(text) > MAX_CHARS
    if truncated:
        text = text[:MAX_CHARS]
    return {
        "text": text,
        "page_count": page_count,
        "truncated": truncated,
        "engine": engine,
    }


def _stdlib_extract(payload: bytes) -> str:
    """Best-effort extraction of literal strings from content streams."""
    chunks: list[str] = []
    for match in re.finditer(rb"stream\r?\n(.*?)endstream", payload, re.S):
        stream = match.group(1)
        for literal in re.finditer(rb"\((.*?)(?<!\\)\)", stream, re.S):
            raw = literal.group(1)
            raw = raw.replace(b"\\(", b"(").replace(b"\\)", b")").replace(b"\\\\", b"\\")
            try:
                chunks.append(raw.decode("latin-1"))
            except UnicodeDecodeError:
                continue
    return "\n".join(chunks)


def chunk_text(text: str, size: int = CHUNK_CHARS) -> list[str]:
    """Split text on paragraph boundaries near ``size`` characters."""
    if len(text) <= size:
        return [text] if text else []
    out: list[str] = []
    current: list[str] = []
    length = 0
    for paragraph in text.split("\n\n"):
        if length + len(paragraph) > size and current:
            out.append("\n\n".join(current))
            current, length = [], 0
        current.append(paragraph)
        length += len(paragraph) + 2
    if current:
        out.append("\n\n".join(current))
    return out


def select_relevant(text: str, question: str, chunks: int = 3) -> str:
    """Pick the chunks most likely to answer ``question``.

    A keyword-overlap score — deliberately simple, deterministic and free
    of any embedding dependency.
    """
    parts = chunk_text(text)
    if len(parts) <= chunks:
        return "\n\n".join(parts)
    terms = {
        term
        for term in re.findall(r"[a-zA-Z0-9]{3,}", question.lower())
        if term not in {"the", "and", "for", "with", "that", "this", "what", "how", "why"}
    }
    scored = []
    for index, part in enumerate(parts):
        low = part.lower()
        score = sum(low.count(term) for term in terms)
        scored.append((score, index, part))
    scored.sort(key=lambda item: (-item[0], item[1]))
    chosen = sorted(scored[:chunks], key=lambda item: item[1])
    return "\n\n".join(part for _, _, part in chosen)
