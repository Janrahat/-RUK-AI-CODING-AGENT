"""Pydantic request/response schemas shared by the API routers."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

# --------------------------------------------------------------------- auth


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=200)
    display_name: str = Field(default="", max_length=120)

    @field_validator("password")
    @classmethod
    def _strength(cls, value: str) -> str:
        if value.isdigit() or value.isalpha():
            raise ValueError("password must mix letters and digits")
        return value


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=200)


class GoogleLoginRequest(BaseModel):
    """An ID token produced by Google Sign-In on the device."""

    id_token: str = Field(min_length=10)


class RefreshRequest(BaseModel):
    refresh_token: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    email: str
    display_name: str
    plan: str
    created_at: datetime


class AuthResponse(TokenPair):
    user: UserOut


# --------------------------------------------------------------------- chat


class ChatMessageIn(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str = Field(min_length=1, max_length=100_000)


class ChatRequest(BaseModel):
    conversation_id: str | None = None
    model: str = Field(default="gpt-4o-mini", max_length=120)
    provider: str | None = Field(default=None, max_length=32)
    messages: list[ChatMessageIn] = Field(default_factory=list, min_length=1)
    stream: bool = True
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: int = Field(default=2048, ge=1, le=32000)


class MessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    role: str
    content: str
    model: str
    created_at: datetime


class ConversationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    model: str
    message_count: int = 0
    created_at: datetime
    updated_at: datetime


class ConversationDetail(ConversationOut):
    messages: list[MessageOut] = Field(default_factory=list)


class ModelInfo(BaseModel):
    id: str
    label: str
    provider: str
    kind: Literal["chat", "image", "video"] = "chat"
    pro_only: bool = False


# ---------------------------------------------------------------- generation


class ImageRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    aspect_ratio: str = Field(default="1:1", max_length=12)
    quality: Literal["standard", "hd"] = "standard"
    model: str = Field(default="gpt-image-1", max_length=120)
    n: int = Field(default=1, ge=1, le=4)


class VideoRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=2000)
    aspect_ratio: str = Field(default="16:9", max_length=12)
    duration_seconds: int = Field(default=5, ge=1, le=30)
    image_url: str | None = None


class GenerationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    kind: str
    status: str
    prompt: str
    aspect_ratio: str
    quality: str
    asset_url: str | None
    error: str | None
    created_at: datetime


# ---------------------------------------------------------------------- code


class CodeRequest(BaseModel):
    language: str = Field(default="python", max_length=24)
    code: str = Field(min_length=1, max_length=100_000)
    stdin: str = Field(default="", max_length=10_000)


class CodeRunResult(BaseModel):
    status: str
    exit_code: int | None
    stdout: str
    stderr: str
    duration_ms: int
    truncated: bool = False


class CodeAssistRequest(BaseModel):
    language: str = Field(default="python", max_length=24)
    code: str = Field(default="", max_length=100_000)
    instruction: str = Field(default="", max_length=4000)
    action: Literal["explain", "generate", "debug", "improve"] = "explain"


class CodeAssistResponse(BaseModel):
    action: str
    language: str
    answer: str
    model: str


# ----------------------------------------------------------------------- pdf


class DocumentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    filename: str
    size_bytes: int
    page_count: int
    created_at: datetime


class PdfAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=2000)
    model: str = Field(default="gpt-4o-mini", max_length=120)


# --------------------------------------------------------------------- tools

ToolId = Literal[
    "writer",
    "summarizer",
    "translator",
    "math",
    "seo",
    "research",
    "voice",
]


class ToolRequest(BaseModel):
    tool: ToolId
    input: str = Field(min_length=1, max_length=20_000)
    options: dict[str, Any] = Field(default_factory=dict)
    model: str = Field(default="gpt-4o-mini", max_length=120)


class ToolResponse(BaseModel):
    tool: str
    output: str
    model: str


# ------------------------------------------------------------------ api keys


class ApiKeyIn(BaseModel):
    provider: Literal["openai", "gemini", "openrouter", "together", "stability", "fal"]
    api_key: str = Field(min_length=8, max_length=400)
    base_url: str | None = Field(default=None, max_length=255)


class ApiKeyOut(BaseModel):
    provider: str
    key_hint: str
    base_url: str | None
    created_at: datetime
    last_used_at: datetime | None


# --------------------------------------------------------------- report links


class ReportLinkIn(BaseModel):
    title: str = Field(default="RUK AI report", max_length=200)
    content: str = Field(min_length=1, max_length=2_000_000)
    content_type: str = Field(default="text/markdown", max_length=64)
    expires_in_hours: int = Field(default=72, ge=1, le=24 * 365)
    password: str | None = Field(default=None, min_length=4, max_length=200)


class ReportLinkOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    title: str
    content_type: str
    expires_at: datetime | None
    revoked: bool
    view_count: int
    created_at: datetime
    url: str = ""


class LinkAccessOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    truncated_ip: str
    user_agent: str
    outcome: str
    created_at: datetime


# --------------------------------------------------------------------- usage


class UsageSummary(BaseModel):
    plan: str
    messages_today: int
    daily_limit: int
    images_this_month: int
    videos_this_month: int
    total_tokens_in: int
    total_tokens_out: int
    remaining_today: int


class SavedItemIn(BaseModel):
    kind: Literal["chat", "image", "video", "report"]
    ref_id: str
    title: str = ""
