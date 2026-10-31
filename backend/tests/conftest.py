"""Shared pytest fixtures.

The suite runs against SQLite in a temporary directory so it needs no
external services. Environment variables are set before the app package is
imported, because ``app.config`` reads them at import time.
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

import pytest

# --------------------------------------------------------------- test env
_TMP = Path(tempfile.mkdtemp(prefix="rukai-tests-"))
os.environ.setdefault("DATABASE_URL", f"sqlite+aiosqlite:///{_TMP / 'test.db'}")
os.environ.setdefault("JWT_SECRET", "test-secret-not-for-production-use-only")
os.environ.setdefault("TOKEN_ENCRYPTION_KEY", "test-encryption-passphrase")
os.environ.setdefault("ENVIRONMENT", "test")
os.environ.setdefault("RATE_LIMIT_PER_MINUTE", "10000")
os.environ.setdefault("AI_RATE_LIMIT_PER_MINUTE", "10000")
os.environ.setdefault("CODE_EXEC_ENABLED", "true")
os.environ.setdefault("CODE_EXEC_TIMEOUT_SECONDS", "10")


@pytest.fixture(scope="session")
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture()
def client():
    """A TestClient with a freshly created schema."""
    import asyncio

    from fastapi.testclient import TestClient

    from app.database import Base, engine
    from app.main import create_app

    async def _reset() -> None:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)
            await conn.run_sync(Base.metadata.create_all)

    asyncio.run(_reset())

    # The rate limiters are process-global, so without this a long test
    # session would start returning 429s partway through.
    from app import deps
    from app.main import _global_limiter

    for limiter in (deps.general_limiter, deps.ai_limiter, deps.auth_limiter, _global_limiter):
        limiter.reset()

    with TestClient(create_app()) as test_client:
        yield test_client

    # Release any connection still bound to this test's event loop.
    asyncio.run(engine.dispose())


@pytest.fixture()
def auth_client(client):
    """A TestClient already signed in as a fresh user."""
    response = client.post(
        "/api/v1/auth/register",
        json={
            "email": "tester@example.com",
            "password": "correct-horse-battery",
            "display_name": "Tester",
        },
    )
    assert response.status_code == 201, response.text
    token = response.json()["access_token"]
    client.headers.update({"Authorization": f"Bearer {token}"})
    return client
