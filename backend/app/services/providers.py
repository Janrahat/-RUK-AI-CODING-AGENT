"""AI provider registry.

Every provider speaks a real HTTP API; there are no canned responses. When
a user has stored their own key it is used, otherwise a server-side key is
used. If neither exists the request fails with a clear, actionable error
rather than returning fabricated output.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import AsyncIterator, Iterable

import httpx

from ..config import settings


class ProviderError(Exception):
    """Raised for missing credentials, upstream failures and bad responses."""

    def __init__(self, message: str, *, status: int = 502) -> None:
        super().__init__(message)
        self.message = message
        self.status = status


@dataclass(frozen=True)
class ProviderSpec:
    name: str
    label: str
    base_url: str
    server_key: str | None
    chat_models: tuple[str, ...] = ()
    image_models: tuple[str, ...] = ()
    video_models: tuple[str, ...] = ()
    style: str = "openai"  # openai | gemini
    headers: dict[str, str] = field(default_factory=dict)


def _specs() -> dict[str, ProviderSpec]:
    return {
        "openai": ProviderSpec(
            name="openai",
            label="OpenAI",
            base_url=settings.openai_base_url,
            server_key=settings.openai_api_key,
            chat_models=("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o4-mini"),
            image_models=("gpt-image-1", "dall-e-3"),
            style="openai",
        ),
        "gemini": ProviderSpec(
            name="gemini",
            label="Google Gemini",
            base_url=settings.gemini_base_url,
            server_key=settings.gemini_api_key,
            chat_models=("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
            style="gemini",
        ),
        "openrouter": ProviderSpec(
            name="openrouter",
            label="OpenRouter",
            base_url=settings.openrouter_base_url,
            server_key=settings.openrouter_api_key,
            chat_models=(
                "openai/gpt-4o",
                "openai/gpt-4o-mini",
                "anthropic/claude-3.5-sonnet",
                "google/gemini-2.0-flash-001",
                "meta-llama/llama-3.3-70b-instruct",
            ),
            style="openai",
            headers={"HTTP-Referer": "https://github.com/Janrahat/-RUK-AI-CODING-AGENT"},
        ),
        "together": ProviderSpec(
            name="together",
            label="Together AI",
            base_url=settings.together_base_url,
            server_key=settings.together_api_key,
            chat_models=(
                "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "Qwen/Qwen2.5-72B-Instruct-Turbo",
            ),
            image_models=("black-forest-labs/FLUX.1-schnell",),
            style="openai",
        ),
        "stability": ProviderSpec(
            name="stability",
            label="Stability AI",
            base_url=settings.stability_base_url,
            server_key=settings.stability_api_key,
            image_models=("sd3.5-large", "sd3.5-medium"),
            style="openai",
        ),
        "fal": ProviderSpec(
            name="fal",
            label="fal.ai",
            base_url=settings.fal_base_url,
            server_key=settings.fal_api_key,
            image_models=("fal-ai/flux/schnell",),
            video_models=("fal-ai/ltx-video",),
            style="openai",
        ),
    }


SPECS = _specs()

# Default routing: model id -> provider name.
MODEL_ROUTES: dict[str, str] = {
    model: spec.name for spec in SPECS.values() for model in spec.chat_models
}


def list_models(kind: str = "chat") -> list[dict[str, object]]:
    """Return every model the deployment can serve, with availability."""
    out: list[dict[str, object]] = []
    for spec in SPECS.values():
        models = {
            "chat": spec.chat_models,
            "image": spec.image_models,
            "video": spec.video_models,
        }[kind]
        for model in models:
            out.append(
                {
                    "id": model,
                    "label": model,
                    "provider": spec.name,
                    "kind": kind,
                    # Availability is advisory: a user may supply their own key.
                    "pro_only": kind != "chat",
                }
            )
    return out


def resolve_provider(model: str, explicit: str | None = None) -> ProviderSpec:
    """Pick the provider serving ``model``."""
    if explicit:
        spec = SPECS.get(explicit)
        if spec is None:
            raise ProviderError(f"unknown provider: {explicit}", status=400)
        return spec
    name = MODEL_ROUTES.get(model)
    if name:
        return SPECS[name]
    # Unknown model ids (e.g. a local vLLM deployment) fall back to OpenAI's
    # wire format, which is the de-facto standard.
    return SPECS["openai"]


class Credentials:
    """Resolved credentials for one call: user key first, server key second."""

    __slots__ = ("spec", "api_key", "base_url")

    def __init__(self, spec: ProviderSpec, api_key: str, base_url: str) -> None:
        self.spec = spec
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")

    @classmethod
    def for_provider(
        cls,
        spec: ProviderSpec,
        user_key: str | None = None,
        user_base_url: str | None = None,
    ) -> "Credentials":
        key = user_key or spec.server_key
        if not key:
            raise ProviderError(
                f"No API key configured for {spec.label}. "
                f"Add one in Profile → API Keys, or set the server-side key.",
                status=424,
            )
        return cls(spec, key, user_base_url or spec.base_url)

    @property
    def auth_headers(self) -> dict[str, str]:
        headers = {"Authorization": f"Bearer {self.api_key}"}
        headers.update(self.spec.headers)
        return headers


# ------------------------------------------------------------------ chat


def _openai_payload(
    model: str,
    messages: Iterable[dict[str, str]],
    *,
    temperature: float,
    max_tokens: int,
    stream: bool,
) -> dict[str, object]:
    return {
        "model": model,
        "messages": list(messages),
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": stream,
    }


def _gemini_payload(
    model: str,
    messages: Iterable[dict[str, str]],
    *,
    temperature: float,
    max_tokens: int,
) -> dict[str, object]:
    contents = []
    system_parts: list[str] = []
    for message in messages:
        role = message.get("role", "user")
        text = message.get("content", "")
        if role == "system":
            system_parts.append(text)
            continue
        contents.append(
            {"role": "model" if role == "assistant" else "user", "parts": [{"text": text}]}
        )
    payload: dict[str, object] = {
        "contents": contents,
        "generationConfig": {
            "temperature": temperature,
            "maxOutputTokens": max_tokens,
        },
    }
    if system_parts:
        payload["systemInstruction"] = {"parts": [{"text": "\n\n".join(system_parts)}]}
    return payload


async def stream_chat(
    creds: Credentials,
    model: str,
    messages: list[dict[str, str]],
    *,
    temperature: float = 0.7,
    max_tokens: int = 2048,
) -> AsyncIterator[str]:
    """Yield text deltas from a real streaming completion."""
    timeout = httpx.Timeout(settings.request_timeout_seconds, connect=15.0)

    if creds.spec.style == "gemini":
        url = f"{creds.base_url}/models/{model}:streamGenerateContent?alt=sse"
        payload = _gemini_payload(
            model, messages, temperature=temperature, max_tokens=max_tokens
        )
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream(
                "POST",
                url,
                headers={"x-goog-api-key": creds.api_key, "Content-Type": "application/json"},
                json=payload,
            ) as response:
                if response.status_code >= 400:
                    body = (await response.aread()).decode("utf-8", "replace")
                    raise ProviderError(
                        _upstream_message(response.status_code, body),
                        status=response.status_code,
                    )
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    chunk = line[5:].strip()
                    if not chunk or chunk == "[DONE]":
                        continue
                    try:
                        data = json.loads(chunk)
                    except json.JSONDecodeError:
                        continue
                    for candidate in data.get("candidates", []):
                        for part in candidate.get("content", {}).get("parts", []):
                            text = part.get("text")
                            if text:
                                yield text
        return

    # OpenAI-compatible SSE.
    url = f"{creds.base_url}/chat/completions"
    payload = _openai_payload(
        model, messages, temperature=temperature, max_tokens=max_tokens, stream=True
    )
    async with httpx.AsyncClient(timeout=timeout) as client:
        async with client.stream(
            "POST", url, headers=creds.auth_headers, json=payload
        ) as response:
            if response.status_code >= 400:
                body = (await response.aread()).decode("utf-8", "replace")
                raise ProviderError(
                    _upstream_message(response.status_code, body),
                    status=response.status_code,
                )
            async for line in response.aiter_lines():
                if not line.startswith("data:"):
                    continue
                chunk = line[5:].strip()
                if not chunk or chunk == "[DONE]":
                    continue
                try:
                    data = json.loads(chunk)
                except json.JSONDecodeError:
                    continue
                for choice in data.get("choices", []):
                    delta = choice.get("delta") or {}
                    text = delta.get("content")
                    if text:
                        yield text


async def complete_chat(
    creds: Credentials,
    model: str,
    messages: list[dict[str, str]],
    *,
    temperature: float = 0.7,
    max_tokens: int = 2048,
) -> str:
    """Non-streaming completion, used by the one-shot tools."""
    parts: list[str] = []
    async for delta in stream_chat(
        creds, model, messages, temperature=temperature, max_tokens=max_tokens
    ):
        parts.append(delta)
    text = "".join(parts).strip()
    if not text:
        raise ProviderError("provider returned an empty response", status=502)
    return text


def _upstream_message(status: int, body: str) -> str:
    detail = body.strip()
    try:
        parsed = json.loads(body)
        error = parsed.get("error")
        if isinstance(error, dict) and error.get("message"):
            detail = str(error["message"])
        elif isinstance(error, str):
            detail = error
    except (json.JSONDecodeError, AttributeError):
        pass
    detail = detail[:500]
    if status == 401:
        return f"Provider rejected the API key (401). {detail}"
    if status == 429:
        return f"Provider rate limit reached (429). {detail}"
    return f"Provider error {status}: {detail}"
