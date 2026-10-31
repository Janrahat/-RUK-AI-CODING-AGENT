"""RUK AI backend — FastAPI application factory."""

from __future__ import annotations

import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from . import __version__
from .config import settings
from .database import init_db
from .deps import general_limiter
from .ratelimit import RateLimiter
from .routers import auth, chat, code, generations, keys, links, pdf, tools, usage

logging.basicConfig(
    level=logging.DEBUG if settings.debug else logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
)
log = logging.getLogger("rukai")

# A coarse per-IP guard applied to every request, independent of the
# per-route limiters.
_global_limiter = RateLimiter(settings.rate_limit_per_minute * 3, 120)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    log.info(
        "RUK AI backend %s ready (env=%s, db=%s)",
        __version__,
        settings.environment,
        settings.database_url.split("://", 1)[0],
    )
    yield
    log.info("RUK AI backend shutting down")


def create_app() -> FastAPI:
    app = FastAPI(
        title="RUK AI API",
        version=__version__,
        description=(
            "Backend for the RUK AI Android app: chat, image and video "
            "generation, code assistance, PDF analysis, AI tools and "
            "transparent report sharing."
        ),
        lifespan=lifespan,
        docs_url="/docs",
        redoc_url=None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.middleware("http")
    async def guard(request: Request, call_next):
        started = time.perf_counter()
        allowed, retry_after = _global_limiter.consume(
            request.client.host if request.client else "unknown"
        )
        if not allowed:
            return JSONResponse(
                status_code=429,
                content={"detail": "Too many requests. Please slow down."},
                headers={"Retry-After": str(max(1, int(retry_after) + 1))},
            )
        response = await call_next(request)
        response.headers["X-Process-Time-ms"] = f"{(time.perf_counter() - started) * 1000:.1f}"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):  # pragma: no cover
        log.exception("unhandled error on %s %s", request.method, request.url.path)
        return JSONResponse(
            status_code=500,
            content={"detail": "Internal server error. The failure has been logged."},
        )

    prefix = settings.api_prefix
    app.include_router(auth.router, prefix=prefix)
    app.include_router(chat.router, prefix=prefix)
    app.include_router(generations.router, prefix=prefix)
    app.include_router(code.router, prefix=prefix)
    app.include_router(pdf.router, prefix=prefix)
    app.include_router(tools.router, prefix=prefix)
    app.include_router(keys.router, prefix=prefix)
    app.include_router(links.router, prefix=prefix)
    app.include_router(links.public_router)
    app.include_router(usage.router, prefix=prefix)

    @app.get("/health", tags=["meta"])
    async def health() -> dict:
        return {
            "status": "ok",
            "version": __version__,
            "environment": settings.environment,
        }

    @app.get("/", tags=["meta"])
    async def root() -> dict:
        return {
            "name": "RUK AI API",
            "version": __version__,
            "docs": "/docs",
            "health": "/health",
        }

    return app


app = create_app()
