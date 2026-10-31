"""Prompt templates and post-processing for the AI tools library."""

from __future__ import annotations

import re

SYSTEM_TOOL = (
    "You are RUK AI, a precise and practical assistant inside a mobile app. "
    "Answer directly and completely. Use Markdown. Never invent facts; say so "
    "when information is missing. Keep formatting tight enough to read on a phone."
)

TOOL_SPECS: dict[str, dict[str, str]] = {
    "writer": {
        "label": "AI Writing Assistant",
        "system": (
            "You are an expert writer and editor. Produce polished, well-structured "
            "prose. Match the requested tone and audience. Output only the finished "
            "text unless the user asks for commentary."
        ),
        "template": (
            "Write the following. Tone: {tone}. Audience: {audience}. "
            "Format: {format}.\n\nREQUEST:\n{input}"
        ),
        "defaults": {"tone": "professional", "audience": "general", "format": "short article"},
    },
    "summarizer": {
        "label": "AI Summarizer",
        "system": (
            "You summarise accurately. Preserve the author's meaning. Never add "
            "facts that are not in the source. If the source is too thin to "
            "summarise well, say exactly what is missing."
        ),
        "template": (
            "Summarise the following in {length} as bullet points, then add one "
            "sentence of key takeaway.\n\nSOURCE:\n{input}"
        ),
        "defaults": {"length": "5 bullets"},
    },
    "translator": {
        "label": "AI Translator",
        "system": (
            "You are a professional translator. Translate faithfully, preserving "
            "tone, idiom and formatting. Do not add explanations unless asked. "
            "If a phrase is ambiguous, choose the most natural rendering."
        ),
        "template": "Translate the following into {target_language}.\n\nTEXT:\n{input}",
        "defaults": {"target_language": "Urdu"},
    },
    "math": {
        "label": "AI Math Solver",
        "system": (
            "You are a mathematics tutor. Solve step by step and show every "
            "intermediate step. State the final answer clearly on its own line. "
            "If the problem is ambiguous, state the interpretation you used."
        ),
        "template": "Solve this. Show full working, then the final answer.\n\nPROBLEM:\n{input}",
        "defaults": {},
    },
    "seo": {
        "label": "AI SEO Tools",
        "system": (
            "You are an SEO specialist. Give concrete, checkable improvements. "
            "Never promise rankings. Distinguish what is verifiable now from "
            "what needs testing."
        ),
        "template": (
            "Analyse the following content and return: (1) a title tag under 60 "
            "characters, (2) a meta description under 155 characters, (3) five "
            "target keywords with intent, (4) three structural improvements, "
            "(5) internal-linking ideas.\n\nCONTENT:\n{input}"
        ),
        "defaults": {},
    },
    "research": {
        "label": "AI Research Assistant",
        "system": (
            "You are a research analyst. Separate established consensus from "
            "uncertain or contested claims. Flag anything you cannot verify. "
            "Never fabricate citations, DOIs, URLs or study names."
        ),
        "template": (
            "Produce a structured research brief on the topic below with: "
            "overview, key findings, competing views, open questions, and a "
            "reading plan. Mark every claim you are unsure about.\n\nTOPIC:\n{input}"
        ),
        "defaults": {},
    },
    "voice": {
        "label": "AI Voice Generator",
        "system": (
            "You prepare text to be read aloud by a speech synthesiser. Write "
            "for the ear: short sentences, no Markdown syntax, no bullet "
            "characters, no code fences. Expand abbreviations and symbols into "
            "spoken words. Return only the text to be spoken."
        ),
        "template": (
            "Rewrite the following as natural spoken narration for a "
            "{voice_style} voice, then return only the narration.\n\nTEXT:\n{input}"
        ),
        "defaults": {"voice_style": "clear and neutral"},
    },
}


_SECRET_PATTERNS = (
    re.compile(r"\bsk-[A-Za-z0-9_\-]{16,}\b"),
    re.compile(r"\bsk-proj-[A-Za-z0-9_\-]{16,}\b"),
    re.compile(r"\bAIza[0-9A-Za-z_\-]{30,}\b"),
    re.compile(r"\bghp_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bxox[baprs]-[A-Za-z0-9\-]{10,}\b"),
    re.compile(r"\bBearer\s+[A-Za-z0-9\-._~+/]{20,}=*", re.I),
    re.compile(r"\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b"),
)


def redact(text: str) -> str:
    """Remove anything that looks like a credential before it is stored or shown."""
    if not text:
        return text
    cleaned = text
    for pattern in _SECRET_PATTERNS:
        cleaned = pattern.sub("[REDACTED]", cleaned)
    return cleaned


class ToolError(Exception):
    pass


def build_messages(tool: str, user_input: str, options: dict[str, str] | None = None) -> list[dict[str, str]]:
    """Render the tool template into a provider-ready message list."""
    spec = TOOL_SPECS.get(tool)
    if spec is None:
        raise ToolError(
            f"Unknown tool '{tool}'. Available: {', '.join(sorted(TOOL_SPECS))}"
        )
    values = dict(spec["defaults"])
    for key, value in (options or {}).items():
        if isinstance(value, str) and value.strip():
            values[key] = value.strip()[:200]
    try:
        prompt = spec["template"].format(**values, input=user_input)
    except KeyError as exc:
        raise ToolError(f"missing option {exc} for tool '{tool}'") from exc
    return [
        {"role": "system", "content": SYSTEM_TOOL + "\n\n" + spec["system"]},
        {"role": "user", "content": prompt},
    ]
