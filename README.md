# RUK AI

**All-in-one AI assistant for Android** — chat, image and video generation, a code
assistant with a real execution sandbox, PDF question answering, a twelve-tool AI
library, and a FastAPI backend that keeps every provider key off the device.

This repository also ships an independent **Termux terminal agent** (`agent.py`),
covered in [Termux agent](#termux-agent). The two share a name and nothing else:
the terminal agent is a separate, single-file tool for coding on-device.

<p align="left">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="FastAPI" src="https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white">
  <img alt="Python" src="https://img.shields.io/badge/Python-3.9%2B-3776AB?logo=python&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg">
</p>

---

## Table of contents

- [What this is](#what-this-is)
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Backend setup](#backend-setup)
- [Android app setup](#android-app-setup)
- [Configuration reference](#configuration-reference)
- [API reference](#api-reference)
- [Testing](#testing)
- [Security model](#security-model)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)
- [Termux agent](#termux-agent)
- [License](#license)

---

## What this is

RUK AI is two cooperating pieces:

| Piece | Stack | Responsibility |
|---|---|---|
| **Android app** | Kotlin, Jetpack Compose, Material 3, MVVM | All UI. Holds **no** provider API keys. |
| **Backend** | Python 3.9+, FastAPI, SQLAlchemy, PostgreSQL | Auth, AI provider calls, key storage, rate limiting, usage metering. |

The app talks to the backend over REST plus one Server-Sent Events stream for
token-by-token chat. Provider credentials live only in the backend database,
encrypted at rest — a decompiled APK yields nothing usable.

### Features

**Dashboard** — logo, plan badge, AI search bar, and six cards: AI Chat, Image
Gen, Video Gen, Code AI, PDF Chat and AI Tools. History and Profile are bottom
navigation tabs, not dashboard cards.

**AI Chat** — multiple providers and models, streaming responses, conversation
history, copy, regenerate, and Markdown rendering with syntax-highlighted code
blocks. Attachments and voice input are not implemented yet; the tool library
covers translation and summarisation.

**AI Tools library** — twelve tools across Writing, Media, Code and Utility
categories, with search, category filters and per-tool pages: Writing Assistant,
Image Generator, Video Generator, Code Assistant, Summarizer, Translator, PDF
Assistant, Voice Generator, Math Solver, SEO Tools, Research Assistant, Chatbot.

**Image Generator** — text-to-image, aspect-ratio and quality selection,
regenerate, history, download.

**Video Generator** — text-to-video and image-to-video with job polling, status
display, preview and download.

**Code Assistant** — 14 languages with syntax highlighting, explain / generate /
debug / improve, and execution through a resource-limited backend sandbox.
Execution is available for **python, javascript, bash, php and go**; the other
nine are assisted only, and the language picker marks which are runnable on the
host so nobody is surprised by "not executable here".

**PDF Assistant** — upload, text extraction, summarisation and document Q&A.

**Profile** — account, plan, usage statistics, API key management and app
settings. "Saved Items" currently opens History; there is no separate saved
collection yet.

---

## Architecture

```
┌──────────────────────────────┐        ┌───────────────────────────────────┐
│  Android app (Kotlin)        │        │  Backend (FastAPI)                │
│                              │        │                                   │
│  Compose UI  ──► ViewModel   │  REST  │  routers/  ──► services/          │
│       │            │         │ ─────► │      │             │              │
│       │            ▼         │  SSE   │      ▼             ▼              │
│       │      Repository      │        │   deps.py     providers.py        │
│       │            │         │        │  (auth,       (OpenAI, Gemini,    │
│       └────► RukApi /        │        │   limits)      OpenRouter, …)     │
│              ChatStreamClient│        │      │                            │
│                              │        │      ▼                            │
│  TokenStore (EncryptedShared │        │  SQLAlchemy ──► PostgreSQL        │
│  Preferences)                │        │                                   │
└──────────────────────────────┘        └───────────────────────────────────┘
```

The app never sees a provider key. It authenticates with a JWT, and the backend
resolves which provider credential to use — the user's own stored key if they
added one, otherwise the server-side key.

---

## Repository layout

```
.
├── app/                          Android app module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/ruk/ai/
│       │   │   ├── MainActivity.kt
│       │   │   ├── RukAiApplication.kt
│       │   │   ├── data/
│       │   │   │   ├── model/Models.kt          wire DTOs
│       │   │   │   ├── remote/                  Retrofit, SSE, token store
│       │   │   │   └── repository/              one repository per feature
│       │   │   ├── di/ServiceLocator.kt
│       │   │   ├── navigation/                  routes, tool catalogue, nav graph
│       │   │   └── ui/
│       │   │       ├── components/              shared widgets, Markdown renderer
│       │   │       ├── screens/                 one file per screen
│       │   │       └── theme/                   colour, type, shape, theme
│       │   └── res/                             icons, strings, themes, XML config
│       └── test/java/com/ruk/ai/                JVM unit tests
├── backend/
│   ├── app/
│   │   ├── main.py               app factory, middleware, error handlers
│   │   ├── config.py             environment-driven settings
│   │   ├── database.py           async engine and session factory
│   │   ├── models.py             ORM tables
│   │   ├── schemas.py            Pydantic request/response models
│   │   ├── security.py           hashing, JWT, encryption, redaction
│   │   ├── ratelimit.py          token-bucket limiter
│   │   ├── deps.py               shared FastAPI dependencies
│   │   ├── routers/              auth, chat, generations, code, pdf, tools,│   │   │                       keys, links, usage
│   │   └── services/             providers, generation, code_runner,
│   │                             pdf_service, tools
│   ├── tests/                    pytest suite
│   ├── schema.sql                PostgreSQL DDC
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   └── .env.example
├── .github/workflows/
│   ├── backend-tests.yml         pytest on Python 3.9-3.12
│   └── android-build.yml         assembleDebug APK + unit tests
├── CHANGELOG.md
├── LICENSE
└── README.md
```

---

## Backend setup

### 1. Requirements

- Python 3.9 or newer
- PostgreSQL 13+ (SQLite works for local development and the test suite)

### 2. Install

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Configure

```bash
cp .env.example .env
```

Then edit `.env`. At minimum, set these three:

```bash
# JWT_SECRET — any long random string:
#   python -c "import secrets; print(secrets.token_urlsafe(48))"
JWT_SECRET=<random-48-byte-string>

# TOKEN_ENCRYPTION_KEY — must be a valid Fernet key, NOT a plain random string.
# A wrong format raises at the first key operation. Generate it with:
#   python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
TOKEN_ENCRYPTION_KEY=<fernet-key>

# PostgreSQL
DATABASE_URL=postgresql+asyncpg://ruk:ruk@localhost:5432/ruk_ai
```

For a quick local run without PostgreSQL:

```bash
DATABASE_URL=sqlite+aiosqlite:///./ruk_ai.db
```

### 4. Create the schema

The app creates tables on startup. To do it explicitly, or to inspect the DDL:

```bash
psql "$DATABASE_URL" -f schema.sql
```

### 5. Add at least one AI provider key

Server-side keys are what the app falls back to when a user has not supplied
their own. Set whichever you have:

```bash
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=...
OPENROUTER_API_KEY=...
```

### 6. Run

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Interactive docs: <http://localhost:8000/docs>
- Health check: <http://localhost:8000/health>

---

## Android app setup

### 1. Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35 (compileSdk 35, minSdk 24)

### 2. Open and configure

Open the repository root in Android Studio. The backend URL is a Gradle
property, so nothing needs editing in source:

```bash
# Emulator reaching a backend on the host machine
./gradlew assembleDebug -PrukApiBaseUrl=http://10.0.2.2:8000/

# Physical device on the same network
./gradlew assembleDebug -PrukApiBaseUrl=http://192.168.1.50:8000/

# Production
./gradlew assembleDebug -PrukApiBaseUrl=https://api.yourdomain.com/
```

Google Sign-In is optional. To enable it, pass your **web** client id:

```bash
./gradlew assembleDebug -PrukGoogleClientId=1234567890-abc.apps.googleusercontent.com
```

Without it the Google button reports that sign-in is not configured, and
email/password still works.

### 3. Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or press **Run** in Android Studio.

### 4. Prebuilt APK

The `Android APK` workflow builds a debug APK on every push to `main` and
uploads it as the `ruk-ai-debug-apk` artifact. Download it from the run summary
on the **Actions** tab.

---

## Configuration reference

All backend settings come from environment variables (see `backend/.env.example`).

Every value below is read by `backend/app/config.py`. The authoritative,
commented list is `backend/.env.example`.

| Variable | Default | Purpose |
|---|---|---|
| `ENVIRONMENT` | `development` | `development`, `staging` or `production` |
| `DEBUG` | `false` | Verbose logging and debug error output |
| `PUBLIC_BASE_URL` | `http://localhost:8000` | Base URL used when building share links |
| `CORS_ORIGINS` | `*` | Comma-separated allowed origins |
| `DATABASE_URL` | `sqlite+aiosqlite:///./rukai.db` | Async SQLAlchemy URL |
| `DB_ECHO` | `false` | Log every SQL statement |
| `JWT_SECRET` | random per boot | **Set this.** Signs access and refresh tokens |
| `ACCESS_TOKEN_MINUTES` | `720` | Access token lifetime |
| `REFRESH_TOKEN_DAYS` | `30` | Refresh token lifetime |
| `TOKEN_ENCRYPTION_KEY` | derived from `JWT_SECRET` | Fernet key encrypting stored provider keys |
| `RATE_LIMIT_PER_MINUTE` | `60` | General request budget per user |
| `RATE_LIMIT_BURST` | `20` | Token-bucket burst for general routes |
| `AI_RATE_LIMIT_PER_MINUTE` | `20` | AI endpoint budget per user |
| `OPENAI_API_KEY` | — | Server-side OpenAI key |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Override for a compatible endpoint |
| `GEMINI_API_KEY` | — | Server-side Google Gemini key |
| `GEMINI_BASE_URL` | `…/v1beta` | Gemini API base |
| `OPENROUTER_API_KEY` | — | Server-side OpenRouter key |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | OpenRouter API base |
| `TOGETHER_API_KEY` | — | Server-side Together key |
| `TOGETHER_BASE_URL` | `https://api.together.xyz/v1` | Together API base |
| `STABILITY_API_KEY` | — | Image generation via Stability |
| `STABILITY_BASE_URL` | `https://api.stability.ai` | Stability API base |
| `FAL_API_KEY` | — | Video generation via fal.ai |
| `FAL_BASE_URL` | `https://queue.fal.run` | fal.ai queue base |
| `GOOGLE_CLIENT_ID` | — | Audience checked on Google ID tokens |
| `MAX_UPLOAD_BYTES` | `20971520` | PDF upload limit (20 MB) |
| `REQUEST_TIMEOUT_SECONDS` | `120` | Upstream provider timeout |
| `FREE_DAILY_MESSAGES` | `40` | Daily chat budget on the Free plan |
| `PRO_DAILY_MESSAGES` | `2000` | Daily chat budget on the Pro plan |
| `CODE_EXEC_ENABLED` | `true` | Master switch for the code sandbox |
| `CODE_EXEC_TIMEOUT_SECONDS` | `10` | Wall-clock limit per run |
| `CODE_EXEC_MAX_OUTPUT_BYTES` | `65536` | Captured stdout/stderr cap |

> `JWT_SECRET` has a random per-boot default so the app starts without
> configuration. That default invalidates every token on restart — always set
> it explicitly outside local development.

---

## API reference

Base path: `/api/v1`. All routes except `/health`, `/api/v1/auth/*` and `/r/{id}`
require `Authorization: Bearer <access_token>`.

### Auth

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create an account, returns tokens |
| `POST` | `/auth/login` | Email + password sign-in |
| `POST` | `/auth/google` | Exchange a Google ID token for app tokens |
| `POST` | `/auth/refresh` | Rotate an expired access token |
| `POST` | `/auth/logout` | Revoke the current session |
| `GET` | `/auth/me` | Current user profile |
| `POST` | `/auth/upgrade` | Move the account to the Pro plan |

### Chat

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/chat/models` | Models available given configured providers |
| `GET` | `/chat/conversations` | List conversations with message counts |
| `GET` | `/chat/conversations/{id}` | Conversation with its messages |
| `DELETE` | `/chat/conversations/{id}` | Delete a conversation |
| `POST` | `/chat/completions` | **SSE** token stream — creates the conversation if none is supplied |
| `POST` | `/chat/regenerate/{id}` | Drop the last assistant turn so the next send regenerates it |

### Generations

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/generations/image` | Submit an image job |
| `POST` | `/generations/video` | Submit a video job |
| `GET` | `/generations` | List jobs, optionally filtered by `kind` |
| `GET` | `/generations/{id}` | Poll one job |
| `DELETE` | `/generations/{id}` | Delete a job |

### Code, PDF, tools, keys, links, usage

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/code/assist` | explain / generate / debug / improve |
| `POST` | `/code/run` | Execute code in the sandbox |
| `GET` | `/code/languages` | Supported languages, and which are runnable here |
| `POST` | `/pdf/upload` | Upload a PDF and extract its text |
| `GET` | `/pdf/documents` | List uploaded documents |
| `GET` | `/pdf/documents/{id}/text` | Extracted text for one document |
| `POST` | `/pdf/documents/{id}/summarize` | Summarise a document |
| `POST` | `/pdf/documents/{id}/ask` | Ask a question about a document |
| `DELETE` | `/pdf/documents/{id}` | Delete a document |
| `GET` | `/tools` | Tool catalogue |
| `POST` | `/tools/run` | Run a tool by id |
| `GET` | `/keys` | List stored provider keys (masked) |
| `PUT` | `/keys` | Store or replace a provider key |
| `DELETE` | `/keys/{provider}` | Remove a provider key |
| `POST` | `/keys/{provider}/test` | Check a stored key against the provider |
| `POST` | `/links` | Create a shareable report link |
| `GET` | `/links` | List your links |
| `POST` | `/links/{id}/revoke` | Revoke a link |
| `DELETE` | `/links/{id}` | Delete a link |
| `GET` | `/links/{id}/access` | Access log for a link |
| `GET` | `/usage/summary` | Usage statistics for the profile screen |
| `GET` | `/usage/events` | Raw metering events |
| `GET` | `/health` | Liveness probe (unauthenticated) |

### Streaming example

```bash
curl -N -X POST http://localhost:8000/api/v1/chat/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hello"}]}'
```

The stream is a sequence of named SSE events, each a `event:` line followed by
one `data:` line of JSON. A blank line ends each frame:

```
event: meta
data: {"conversation_id":"...","model":"gpt-4o-mini","provider":"openai"}

event: delta
data: {"text":"Hello"}

event: delta
data: {"text":" there"}

event: done
data: {"conversation_id":"...","chars":12}
```

A provider failure arrives as `event: error` with a `message` and upstream
`status`, after which the stream ends — it is not retried for you. There is no
`[DONE]` sentinel; `done` or `error` is the terminal frame.

---

## Testing

### Backend

```bash
cd backend
pip install -r requirements-dev.txt
python -m pytest tests -q
```

The suite runs against SQLite in a temporary directory and needs no network
access — provider calls are stubbed at the HTTP layer, so it is safe in CI.

Coverage includes password hashing and verification, JWT issue/verify/expiry,
Fernet key encryption round-trips, credential redaction, the token-bucket rate
limiter, the code sandbox (timeouts, output caps, language allowlist), and
end-to-end API flows for auth, chat, generations, PDF, tools, keys, links and
usage.

### Android

```bash
./gradlew testDebugUnitTest
```

JVM unit tests cover the tool catalogue (every advertised tool maps to a real
backend id, no duplicates, all categories populated) and the wire DTOs
(snake_case field mapping, unknown-field tolerance, status helpers).

### CI

| Workflow | Trigger | What it does |
|---|---|---|
| `backend-tests.yml` | push/PR touching `backend/**` | pytest on Python 3.9, 3.10, 3.11, 3.12 + a `/health` smoke check |
| `android-build.yml` | push/PR touching `app/**` | `assembleDebug`, unit tests, uploads the APK artifact |

---

## Security model

**Provider keys never ship in the APK.** The app stores only a JWT. Provider
credentials live in the backend database, encrypted with Fernet using
`TOKEN_ENCRYPTION_KEY`, and are returned to the client masked (`sk-…4f2a`).

**Passwords** are hashed with PBKDF2-HMAC-SHA256 (600,000 iterations, per-user
salt) from the standard library, so there is no native build dependency.

**Tokens** are short-lived JWTs with a separate refresh token. The Android
client keeps them in `EncryptedSharedPreferences` (AES-256-GCM, key in the
Android Keystore).

**Rate limiting** is a token bucket applied per user for general and AI routes,
and per IP for auth routes, so a stolen token cannot be used to burn through
provider quota.

**The code sandbox** runs submitted code as a subprocess with a wall-clock
timeout, an output cap, a language allowlist, and a scrubbed environment. It is
a *convenience* sandbox, not a security boundary — see
[Known limitations](#known-limitations).

**Redaction** strips API keys, bearer tokens, cookies and `Authorization`
headers from anything written to logs or returned in error payloads.

**Share links** are opt-in, expiring, revocable, optionally password-protected,
and log access with a truncated IP. They are never created silently.

---

## Troubleshooting

**`JWT_SECRET` warning** — the backend does start without it, because the config
falls back to a random per-boot value. That fallback silently invalidates every
issued token on each restart (and logs everyone out), so set it explicitly for
anything beyond throwaway local runs.

**`TOKEN_ENCRYPTION_KEY` invalid** — the value must be a real Fernet key. Setting
it to a random token_urlsafe string (the obvious thing to do) raises on the first
key operation. Generate it with `Fernet.generate_key()`, as shown in
[Backend setup](#3-configure).

**`RuntimeError: Task got Future attached to a different loop`** — you are
reusing an async engine across event loops. The test suite disposes the engine
per test; if you add fixtures, do the same.

**App shows "Cannot reach the server"** — the emulator cannot see `localhost`.
Use `-PrukApiBaseUrl=http://10.0.2.2:8000/`. On a physical device use the host's
LAN IP and make sure the backend binds `0.0.0.0`.

**Cleartext HTTP is blocked** — Android blocks plain HTTP by default. The debug
build allows it for `10.0.2.2` and `localhost` only, via
`res/xml/network_security_config.xml`. Production must use HTTPS.

**`401 Unauthorized` on every call** — the access token expired. The client
refreshes automatically; if refresh also fails, sign in again.

**`429 Too Many Requests`** — you hit a rate limit. The response carries
`Retry-After`. Raise `AI_RATE_LIMIT_PER_MINUTE` for local development.

**`424` from an AI route** — no provider key is configured for that model. Either
set a server-side key or add your own under Profile → API Keys. (`424 Failed
Dependency` is used rather than `503` because the missing piece is a credential
the user can supply, not a server fault.)

**Gradle: `Unsupported class file major version`** — you are on a JDK other
than 17. Set `JAVA_HOME` to a JDK 17 install.

**`./gradlew: Permission denied`** — `chmod +x gradlew`.

---

## Known limitations

- **The code sandbox is not a security boundary.** It uses subprocess limits,
  not containers or a VM. Do not expose `CODE_EXEC_ENABLED=true` on a host you
  do not control, and never run untrusted code from anonymous users.
- **Rate limiting is per process.** Running multiple backend workers multiplies
  the effective limit. Move the limiter to Redis before scaling out.
- **Video generation is asynchronous and provider-dependent.** Jobs can take
  minutes, and a provider outage surfaces as a failed job rather than a retry.
- **PDF extraction is text-only.** Scanned PDFs need OCR, which is not bundled.
- **Google Sign-In requires your own OAuth client id.** It is not preconfigured.
- **The APK is built by CI, not committed.** This repository ships source; grab
  the artifact from the Actions tab or build locally.
- **No billing integration.** The Free/Pro distinction is a database field and a
  UI badge; no payment provider is wired up.

---

## Termux agent

`agent.py` is a separate, single-file coding assistant for Android/Termux. It
talks to any OpenAI-compatible Chat Completions API with tool calling, reads and
writes files inside `workspace/`, and asks before every write or command.

### Install

```bash
pkg update -y && pkg install -y git python
git clone https://github.com/Janrahat/-RUK-AI-CODING-AGENT.git
cd -- -RUK-AI-CODING-AGENT
bash install.sh
```

### Configure

```bash
export RUK_API_KEY="YOUR_API_KEY"
export RUK_BASE_URL="https://api.openai.com/v1"
export RUK_MODEL="gpt-4.1-mini"
```

Use the exact base URL and model your provider documents; `gpt-4.1-mini` is only
an example. Never paste a key into source or commit it.

### Run

```bash
python agent.py                      # uses ./workspace
export RUK_WORKSPACE="$HOME/my-project"
python agent.py                      # uses another directory
```

`/clear` resets the conversation, `/exit` quits.

### Safety

This is a starter agent, not an unrestricted autonomous operator. It blocks
shell chaining, pipes, redirection and substitution, and refuses commands
outside a small allowlist. Every file write and command needs your confirmation.
Do not point it at a repository you have not inspected.

### Troubleshooting

| Symptom | Cause |
|---|---|
| `401` / invalid API key | Check `RUK_API_KEY` |
| `404` model or endpoint | Check the provider's base URL and model id |
| Tool calls unsupported | Pick a model/provider that supports tool calling |
| Quota or billing error | Check the provider account; installing this project grants no credits |
| `ModuleNotFoundError` | `python -m pip install -r requirements.txt` |

---

## License

MIT — see [LICENSE](LICENSE).