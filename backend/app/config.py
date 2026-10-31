"""Application configuration loaded from environment variables.

Nothing secret is ever hard-coded here. Every value either has a safe
default or is required and must be supplied via the environment / .env file.
"""

from __future__ import annotations

import secrets
from functools import lru_cache
from typing import List

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings for the RUK AI backend."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ------------------------------------------------------------------ app
    app_name: str = "RUK AI"
    environment: str = "development"
    debug: bool = False
    api_prefix: str = "/api/v1"
    public_base_url: str = "http://localhost:8000"

    # ------------------------------------------------------------- database
    # Default is SQLite so the project runs with zero external services.
    # Production should point this at PostgreSQL, e.g.
    #   postgresql+asyncpg://user:pass@host:5432/rukai
    database_url: str = "sqlite+aiosqlite:///./rukai.db"
    db_echo: bool = False

    # ----------------------------------------------------------------- auth
    jwt_secret: str = Field(default_factory=lambda: secrets.token_urlsafe(48))
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 60 * 12
    refresh_token_days: int = 30

    # -------------------------------------------------- encryption at rest
    # Used to encrypt stored provider API keys (Fernet). If unset, a key is
    # derived from jwt_secret so the app still boots; production should set
    # a dedicated TOKEN_ENCRYPTION_KEY.
    token_encryption_key: str | None = None

    # ------------------------------------------------------ rate limiting
    rate_limit_per_minute: int = 60
    rate_limit_burst: int = 20
    ai_rate_limit_per_minute: int = 20

    # ---------------------------------------------------- AI provider keys
    # Server-side fallback keys. Per-user keys take precedence.
    openai_api_key: str | None = None
    openai_base_url: str = "https://api.openai.com/v1"
    gemini_api_key: str | None = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    openrouter_api_key: str | None = None
    openrouter_base_url: str = "https://openrouter.ai/api/v1"
    together_api_key: str | None = None
    together_base_url: str = "https://api.together.xyz/v1"
    stability_api_key: str | None = None
    stability_base_url: str = "https://api.stability.ai"
    fal_api_key: str | None = None
    fal_base_url: str = "https://queue.fal.run"

    # --------------------------------------------------------------- google
    google_client_id: str | None = None

    # ----------------------------------------------------------------- cors
    cors_origins: List[str] = ["*"]

    # --------------------------------------------------------------- limits
    max_upload_bytes: int = 20 * 1024 * 1024
    request_timeout_seconds: int = 120
    max_conversation_messages: int = 400

    # ------------------------------------------------------------ execution
    code_exec_enabled: bool = True
    code_exec_timeout_seconds: int = 10
    code_exec_max_output_bytes: int = 64 * 1024

    # --------------------------------------------------------------- plans
    free_daily_messages: int = 40
    pro_daily_messages: int = 2000

    @field_validator("cors_origins", mode="before")
    @classmethod
    def _split_origins(cls, value):  # noqa: ANN001
        if isinstance(value, str):
            return [item.strip() for item in value.split(",") if item.strip()]
        return value

    @property
    def is_production(self) -> bool:
        return self.environment.lower() in {"production", "prod"}


@lru_cache
def get_settings() -> Settings:
    """Return the process-wide settings singleton."""
    return Settings()


settings = get_settings()
