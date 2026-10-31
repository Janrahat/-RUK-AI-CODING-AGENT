"""In-process token-bucket rate limiting.

A single-process implementation is deliberate: it needs no Redis and is
correct for one worker. The README explains how to swap in a shared store
for multi-worker deployments.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field


@dataclass
class _Bucket:
    tokens: float
    updated: float = field(default_factory=time.monotonic)


class RateLimiter:
    """Per-key token bucket with a refill rate expressed per minute."""

    def __init__(self, per_minute: int, burst: int | None = None) -> None:
        self.per_minute = max(1, per_minute)
        self.capacity = float(max(1, burst if burst is not None else self.per_minute))
        self._buckets: dict[str, _Bucket] = {}
        self._lock = threading.Lock()

    def consume(self, key: str, amount: float = 1.0) -> tuple[bool, float]:
        """Attempt to take ``amount`` tokens.

        Returns ``(allowed, retry_after_seconds)``.
        """
        now = time.monotonic()
        rate = self.per_minute / 60.0
        with self._lock:
            bucket = self._buckets.get(key)
            if bucket is None:
                # Anchor the bucket to the same clock reading used below.
                # Letting the dataclass default to its own time.monotonic()
                # call makes `elapsed` slightly negative, which would shave
                # tokens off a brand-new bucket and reject the first request
                # whenever capacity == amount (e.g. burst=1).
                bucket = _Bucket(tokens=self.capacity, updated=now)
                self._buckets[key] = bucket
            elapsed = max(0.0, now - bucket.updated)
            bucket.tokens = min(self.capacity, bucket.tokens + elapsed * rate)
            bucket.updated = now
            if bucket.tokens >= amount:
                bucket.tokens -= amount
                return True, 0.0
            deficit = amount - bucket.tokens
            return False, max(0.0, deficit / rate)

    def reset(self, key: str | None = None) -> None:
        with self._lock:
            if key is None:
                self._buckets.clear()
            else:
                self._buckets.pop(key, None)

    def prune(self, older_than_seconds: float = 3600.0) -> int:
        """Drop idle buckets so memory does not grow without bound."""
        cutoff = time.monotonic() - older_than_seconds
        with self._lock:
            stale = [k for k, b in self._buckets.items() if b.updated < cutoff]
            for key in stale:
                del self._buckets[key]
        return len(stale)
