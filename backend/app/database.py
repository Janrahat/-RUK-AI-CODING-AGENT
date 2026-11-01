"""Async SQLAlchemy engine, session factory and declarative base."""

from __future__ import annotations

from typing import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.pool import NullPool

from .config import settings


class Base(DeclarativeBase):
    """Declarative base for every ORM model."""


def _engine_kwargs() -> dict:
    """Engine options, tuned per backend.

    SQLite gets ``NullPool``: a pooled aiosqlite connection is bound to the
    event loop that created it, so reusing one from a different loop (which
    happens in tests and in any app that runs more than one loop) raises
    "attached to a different loop". Opening a connection per checkout is
    cheap for a local file and removes that whole class of failure.
    """
    if settings.database_url.startswith("sqlite"):
        return {"poolclass": NullPool}
    return {"pool_pre_ping": True, "pool_size": 10, "max_overflow": 20}


# Render and several managed PostgreSQL providers expose a generic postgres://
# or postgresql:// URL. This app uses SQLAlchemy's async driver explicitly.
database_url = settings.database_url
if database_url.startswith("postgres://"):
    database_url = database_url.replace("postgres://", "postgresql+asyncpg://", 1)
elif database_url.startswith("postgresql://"):
    database_url = database_url.replace("postgresql://", "postgresql+asyncpg://", 1)

engine = create_async_engine(
    database_url,
    echo=settings.db_echo,
    future=True,
    **_engine_kwargs(),
)

SessionFactory = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False,
)


async def get_db() -> AsyncIterator[AsyncSession]:
    """FastAPI dependency yielding a request-scoped session."""
    async with SessionFactory() as session:
        try:
            yield session
        except Exception:  # pragma: no cover - defensive rollback
            await session.rollback()
            raise


async def init_db() -> None:
    """Create tables for fresh installs.

    For PostgreSQL deployments prefer the checked-in ``schema.sql``; this
    helper keeps SQLite development and the test-suite self-contained.
    """
    from . import models  # noqa: F401  (registers mappers)

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
