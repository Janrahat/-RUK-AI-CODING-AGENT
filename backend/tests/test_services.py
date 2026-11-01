"""Unit tests for the rate limiter and the code-execution sandbox."""

from __future__ import annotations

import asyncio

import pytest

from app.ratelimit import RateLimiter
from app.services import code_runner, providers
from app.services.code_runner import ExecutionError, available_languages, run_code


class TestRateLimiter:
    def test_allows_up_to_capacity(self):
        limiter = RateLimiter(per_minute=60, burst=5)
        results = [limiter.consume("k")[0] for _ in range(5)]
        assert all(results)

    def test_blocks_past_capacity(self):
        limiter = RateLimiter(per_minute=60, burst=3)
        for _ in range(3):
            limiter.consume("k")
        allowed, retry_after = limiter.consume("k")
        assert allowed is False
        assert retry_after > 0

    def test_keys_are_independent(self):
        limiter = RateLimiter(per_minute=60, burst=1)
        assert limiter.consume("a")[0] is True
        assert limiter.consume("b")[0] is True
        assert limiter.consume("a")[0] is False

    def test_refills_over_time(self):
        limiter = RateLimiter(per_minute=6000, burst=1)  # 100 tokens/second
        assert limiter.consume("k")[0] is True
        assert limiter.consume("k")[0] is False
        import time

        time.sleep(0.05)
        assert limiter.consume("k")[0] is True

    def test_reset_single_key(self):
        limiter = RateLimiter(per_minute=60, burst=1)
        limiter.consume("k")
        limiter.reset("k")
        assert limiter.consume("k")[0] is True

    def test_reset_all(self):
        limiter = RateLimiter(per_minute=60, burst=1)
        limiter.consume("a")
        limiter.consume("b")
        limiter.reset()
        assert limiter.consume("a")[0] is True
        assert limiter.consume("b")[0] is True

    def test_prune_removes_idle_buckets(self):
        limiter = RateLimiter(per_minute=60, burst=1)
        limiter.consume("stale")
        assert limiter.prune(older_than_seconds=-1) == 1

    def test_capacity_never_exceeds_burst(self):
        limiter = RateLimiter(per_minute=60000, burst=2)
        import time

        time.sleep(0.05)
        assert limiter.consume("k")[0] is True
        assert limiter.consume("k")[0] is True
        assert limiter.consume("k")[0] is False


class TestCodeRunner:
    def test_language_map_reports_availability(self):
        languages = available_languages()
        assert "python" in languages
        assert languages["python"] is True  # the test host runs Python
        assert languages["rust"] is False   # assist-only, never executable

    def test_python_stdout_is_captured(self):
        result = asyncio.run(run_code("python", "print('hello from ruk ai')"))
        assert result["status"] == "completed"
        assert result["exit_code"] == 0
        assert "hello from ruk ai" in result["stdout"]

    def test_stdin_is_passed_through(self):
        code = "import sys\nprint(sys.stdin.read().strip().upper())"
        result = asyncio.run(run_code("python", code, stdin="abc"))
        assert "ABC" in result["stdout"]

    def test_nonzero_exit_is_reported(self):
        result = asyncio.run(run_code("python", "raise SystemExit(3)"))
        assert result["status"] == "error"
        assert result["exit_code"] == 3

    def test_stderr_is_captured(self):
        result = asyncio.run(run_code("python", "import sys; sys.stderr.write('boom')"))
        assert "boom" in result["stderr"]

    def test_timeout_is_enforced(self):
        from app.config import settings

        original = settings.code_exec_timeout_seconds
        settings.code_exec_timeout_seconds = 1
        try:
            result = asyncio.run(run_code("python", "import time; time.sleep(30)"))
            assert result["status"] == "timeout"
            assert "limit" in result["stderr"].lower()
        finally:
            settings.code_exec_timeout_seconds = original

    def test_output_is_truncated(self):
        from app.config import settings

        original = settings.code_exec_max_output_bytes
        settings.code_exec_max_output_bytes = 100
        try:
            result = asyncio.run(run_code("python", "print('x' * 5000)"))
            assert result["truncated"] is True
            assert len(result["stdout"]) <= 100
        finally:
            settings.code_exec_max_output_bytes = original

    def test_unsupported_language_is_refused(self):
        with pytest.raises(ExecutionError) as excinfo:
            asyncio.run(run_code("rust", "fn main() {}"))
        assert "not executable" in str(excinfo.value)

    def test_disabled_execution_is_refused(self):
        from app.config import settings

        original = settings.code_exec_enabled
        settings.code_exec_enabled = False
        try:
            with pytest.raises(ExecutionError) as excinfo:
                asyncio.run(run_code("python", "print(1)"))
            assert "disabled" in str(excinfo.value)
        finally:
            settings.code_exec_enabled = original

    def test_host_secrets_are_not_visible_to_submitted_code(self):
        import os

        os.environ["RUKAI_TEST_SECRET"] = "must-not-leak"
        try:
            result = asyncio.run(
                run_code("python", "import os; print(os.environ.get('RUKAI_TEST_SECRET', 'ABSENT'))")
            )
            assert "ABSENT" in result["stdout"]
            assert "must-not-leak" not in result["stdout"]
        finally:
            del os.environ["RUKAI_TEST_SECRET"]

    def test_working_directory_is_cleaned_up(self):
        import glob
        import tempfile

        before = set(glob.glob(f"{tempfile.gettempdir()}/rukai-run-*"))
        asyncio.run(run_code("python", "print('x')"))
        after = set(glob.glob(f"{tempfile.gettempdir()}/rukai-run-*"))
        assert after == before


class TestProviderRouting:
    def test_openrouter_qualified_default_model_uses_openrouter(self):
        spec = providers.resolve_provider("openai/gpt-4o-mini")
        assert spec.name == "openrouter"
        assert "openai/gpt-4o-mini" in spec.chat_models

    def test_unqualified_openai_model_does_not_silently_use_openrouter(self):
        spec = providers.resolve_provider("gpt-4o-mini")
        assert spec.name == "openai"
