"""Image and video generation against real provider APIs.

Both generators return a job record immediately and, where the provider is
asynchronous (fal.ai for video), the caller polls ``/generations/{id}``.
Providers that answer synchronously are marked completed before returning.
"""

from __future__ import annotations

import base64
import binascii
from typing import Any

import httpx

from ..config import settings
from .providers import Credentials, ProviderError

_IMAGE_SIZE = {
    "1:1": "1024x1024",
    "16:9": "1536x1024",
    "9:16": "1024x1536",
    "4:3": "1408x1056",
    "3:4": "1056x1408",
}


def _timeout() -> httpx.Timeout:
    return httpx.Timeout(settings.request_timeout_seconds, connect=15.0)


async def generate_image(
    creds: Credentials,
    *,
    prompt: str,
    aspect_ratio: str,
    quality: str,
    model: str,
) -> dict[str, Any]:
    """Create an image and return ``{status, asset_url, provider}``.

    ``asset_url`` is either an https URL or a ``data:`` URI when the
    provider returns inline base64 (OpenAI's gpt-image-1 does).
    """
    size = _IMAGE_SIZE.get(aspect_ratio, "1024x1024")

    if creds.spec.name == "stability":
        return await _stability_image(creds, prompt, aspect_ratio, model)
    if creds.spec.name == "fal":
        return await _fal_image(creds, prompt, model)

    # OpenAI-compatible images endpoint (OpenAI, Together, compatible proxies).
    payload = {
        "model": model,
        "prompt": prompt,
        "size": size,
        "n": 1,
    }
    if quality == "hd":
        payload["quality"] = "hd"

    async with httpx.AsyncClient(timeout=_timeout()) as client:
        response = await client.post(
            f"{creds.base_url}/images/generations",
            headers=creds.auth_headers,
            json=payload,
        )
    if response.status_code >= 400:
        raise ProviderError(
            f"Image provider error {response.status_code}: {response.text[:400]}",
            status=response.status_code,
        )
    data = response.json()
    items = data.get("data") or []
    if not items:
        raise ProviderError("image provider returned no images")
    first = items[0]
    url = first.get("url")
    if not url:
        b64 = first.get("b64_json")
        if not b64:
            raise ProviderError("image provider returned neither url nor b64_json")
        try:
            base64.b64decode(b64[:64], validate=True)
        except (binascii.Error, ValueError) as exc:
            raise ProviderError("image provider returned malformed base64") from exc
        url = f"data:image/png;base64,{b64}"
    return {"status": "completed", "asset_url": url, "provider": creds.spec.name}


async def _stability_image(
    creds: Credentials, prompt: str, aspect_ratio: str, model: str
) -> dict[str, Any]:
    ratio = aspect_ratio.replace(":", ":")
    async with httpx.AsyncClient(timeout=_timeout()) as client:
        response = await client.post(
            f"{creds.base_url}/v2beta/stable-image/generate/sd3",
            headers={
                "Authorization": f"Bearer {creds.api_key}",
                "Accept": "image/*",
            },
            data={
                "prompt": prompt,
                "aspect_ratio": ratio if ratio in _IMAGE_SIZE else "1:1",
                "model": model,
                "output_format": "png",
            },
            files={"none": ("", b"")},
        )
    if response.status_code >= 400:
        raise ProviderError(
            f"Stability error {response.status_code}: {response.text[:400]}",
            status=response.status_code,
        )
    encoded = base64.b64encode(response.content).decode("ascii")
    return {
        "status": "completed",
        "asset_url": f"data:image/png;base64,{encoded}",
        "provider": "stability",
    }


async def _fal_image(creds: Credentials, prompt: str, model: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=_timeout()) as client:
        response = await client.post(
            f"{creds.base_url}/{model}",
            headers={"Authorization": f"Key {creds.api_key}"},
            json={"prompt": prompt},
        )
    if response.status_code >= 400:
        raise ProviderError(
            f"fal.ai error {response.status_code}: {response.text[:400]}",
            status=response.status_code,
        )
    data = response.json()
    images = data.get("images") or []
    if not images or not images[0].get("url"):
        raise ProviderError("fal.ai returned no image URL")
    return {"status": "completed", "asset_url": images[0]["url"], "provider": "fal"}


# --------------------------------------------------------------------- video


async def submit_video(
    creds: Credentials,
    *,
    prompt: str,
    aspect_ratio: str,
    duration_seconds: int,
    image_url: str | None,
    model: str,
) -> dict[str, Any]:
    """Start a video job. Returns ``{status, job_id, asset_url?}``."""
    if creds.spec.name != "fal":
        raise ProviderError(
            "Video generation currently requires a fal.ai key "
            "(Profile → API Keys → fal). Other providers do not expose a "
            "public text-to-video endpoint through this API.",
            status=424,
        )

    payload: dict[str, Any] = {
        "prompt": prompt,
        "aspect_ratio": aspect_ratio,
        "negative_prompt": "blurry, low quality, watermark, distorted",
    }
    if image_url:
        payload["image_url"] = image_url

    async with httpx.AsyncClient(timeout=_timeout()) as client:
        response = await client.post(
            f"{creds.base_url}/{model}",
            headers={"Authorization": f"Key {creds.api_key}"},
            json=payload,
        )
    if response.status_code >= 400:
        raise ProviderError(
            f"Video provider error {response.status_code}: {response.text[:400]}",
            status=response.status_code,
        )
    data = response.json()
    request_id = data.get("request_id") or data.get("id")
    if not request_id:
        raise ProviderError("video provider did not return a request id")
    return {"status": "pending", "job_id": str(request_id), "asset_url": None}


async def poll_video(creds: Credentials, job_id: str, model: str) -> dict[str, Any]:
    """Check a fal.ai queue job and return its current state."""
    async with httpx.AsyncClient(timeout=_timeout()) as client:
        response = await client.get(
            f"{creds.base_url}/{model}/requests/{job_id}/status",
            headers={"Authorization": f"Key {creds.api_key}"},
        )
        if response.status_code >= 400:
            raise ProviderError(
                f"Video status error {response.status_code}: {response.text[:300]}",
                status=response.status_code,
            )
        state = str(response.json().get("status", "")).upper()
        if state in {"IN_QUEUE", "IN_PROGRESS", "QUEUED"}:
            return {"status": "running", "asset_url": None, "error": None}
        if state not in {"COMPLETED", "OK", "SUCCESS"}:
            return {"status": "failed", "asset_url": None, "error": f"provider state {state}"}

        result = await client.get(
            f"{creds.base_url}/{model}/requests/{job_id}",
            headers={"Authorization": f"Key {creds.api_key}"},
        )
    if result.status_code >= 400:
        raise ProviderError(
            f"Video result error {result.status_code}: {result.text[:300]}",
            status=result.status_code,
        )
    payload = result.json()
    video = payload.get("video") or {}
    url = video.get("url") if isinstance(video, dict) else None
    if not url:
        raise ProviderError("video job completed without a video URL")
    return {"status": "completed", "asset_url": url, "error": None}
