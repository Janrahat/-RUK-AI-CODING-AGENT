"""Sandboxed code execution for the Code Assistant.

Security posture
----------------
* Opt-in via ``CODE_EXEC_ENABLED`` (default on for local/dev, operators in
  production should run this behind a dedicated worker).
* Strict language allowlist — no arbitrary argv.
* Runs in a fresh temporary directory that is deleted afterwards.
* Wall-clock timeout enforced with ``subprocess``; the child is killed.
* Output is truncated to a fixed byte budget.
* Environment is scrubbed down to a minimal allowlist so host secrets
  cannot be read by submitted code.
* ``RLIMIT_NPROC``/``RLIMIT_FSIZE``/``RLIMIT_AS`` where the platform allows.

This is a convenience sandbox, not a security boundary against a determined
attacker. It is documented as such in the README.
"""

from __future__ import annotations

import asyncio
import os
import resource
import shutil
import tempfile
import time
from typing import Any

from ..config import settings

# language -> (interpreter argv, source filename)
LANGUAGES: dict[str, tuple[list[str], str]] = {
    "python": (["python3", "-I", "-B"], "main.py"),
    "javascript": (["node", "--no-warnings"], "main.js"),
    "bash": (["bash", "--noprofile", "--norc"], "main.sh"),
    "php": (["php"], "main.php"),
    "ruby": (["ruby"], "main.rb"),
    "go": (["go", "run"], "main.go"),
}

# Languages the app offers in the editor. Compile-only or unsupported-here
# languages are still assistant-supported; they just cannot be executed.
ASSIST_ONLY_LANGUAGES = (
    "typescript",
    "java",
    "kotlin",
    "c",
    "cpp",
    "rust",
    "sql",
    "html",
    "css",
)


class ExecutionError(Exception):
    pass


def available_languages() -> dict[str, bool]:
    """Report which languages can actually run on this host."""
    out: dict[str, bool] = {}
    for name, (argv, _) in LANGUAGES.items():
        out[name] = shutil.which(argv[0]) is not None
    for name in ASSIST_ONLY_LANGUAGES:
        out[name] = False
    return out


def _limits() -> None:  # pragma: no cover - runs in the child process
    """Apply resource limits in the child before exec."""
    try:
        resource.setrlimit(resource.RLIMIT_CPU, (settings.code_exec_timeout_seconds, settings.code_exec_timeout_seconds))
        resource.setrlimit(resource.RLIMIT_FSIZE, (8 * 1024 * 1024, 8 * 1024 * 1024))
        resource.setrlimit(resource.RLIMIT_NPROC, (64, 64))
        resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024, 512 * 1024 * 1024))
    except (ValueError, OSError):
        # Some platforms (notably Android/Termux, some container runtimes)
        # refuse certain limits; execution still proceeds with a timeout.
        pass


_SAFE_ENV = {
    "PATH": "/usr/local/bin:/usr/bin:/bin",
    "LANG": "C.UTF-8",
    "LC_ALL": "C.UTF-8",
    "HOME": "/tmp",
    "PYTHONDONTWRITEBYTECODE": "1",
    "PYTHONIOENCODING": "utf-8",
    "NO_COLOR": "1",
}


async def run_code(language: str, code: str, stdin: str = "") -> dict[str, Any]:
    """Execute ``code`` in ``language`` and capture the result."""
    if not settings.code_exec_enabled:
        raise ExecutionError(
            "Code execution is disabled on this server. "
            "The assistant can still explain, generate and debug code."
        )
    key = language.lower().strip()
    if key not in LANGUAGES:
        raise ExecutionError(
            f"'{language}' is not executable here. "
            f"Runnable: {', '.join(sorted(LANGUAGES))}. "
            f"Assistant-only: {', '.join(ASSIST_ONLY_LANGUAGES)}."
        )
    argv, filename = LANGUAGES[key]
    if shutil.which(argv[0]) is None:
        raise ExecutionError(f"The {argv[0]} runtime is not installed on this server.")

    workdir = tempfile.mkdtemp(prefix="rukai-run-")
    started = time.monotonic()
    try:
        source = os.path.join(workdir, filename)
        with open(source, "w", encoding="utf-8") as handle:
            handle.write(code)

        process = await asyncio.create_subprocess_exec(
            *argv,
            source if key != "go" else workdir,
            cwd=workdir,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=_SAFE_ENV,
            preexec_fn=_limits if os.name == "posix" else None,
        )
        try:
            stdout_b, stderr_b = await asyncio.wait_for(
                process.communicate(stdin.encode("utf-8")),
                timeout=settings.code_exec_timeout_seconds + 1,
            )
            status = "completed" if process.returncode == 0 else "error"
        except asyncio.TimeoutError:
            process.kill()
            await process.wait()
            stdout_b, stderr_b = b"", b""
            status = "timeout"

        duration_ms = int((time.monotonic() - started) * 1000)
        budget = settings.code_exec_max_output_bytes
        stdout, trunc_out = _clip(stdout_b, budget)
        stderr, trunc_err = _clip(stderr_b, budget)
        if status == "timeout":
            stderr = (
                f"Execution exceeded the {settings.code_exec_timeout_seconds}s limit "
                "and was stopped."
            )
        return {
            "status": status,
            "exit_code": process.returncode,
            "stdout": stdout,
            "stderr": stderr,
            "duration_ms": duration_ms,
            "truncated": trunc_out or trunc_err,
        }
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def _clip(payload: bytes, budget: int) -> tuple[str, bool]:
    if payload is None:
        return "", False
    truncated = len(payload) > budget
    text = payload[:budget].decode("utf-8", "replace")
    return text, truncated
