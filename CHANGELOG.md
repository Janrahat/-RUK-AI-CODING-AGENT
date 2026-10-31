# Changelog

All notable changes to RUK AI are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-11-01

First release. The Android app and the FastAPI backend are both functional;
no screen is a stub and no AI response is hardcoded.

### Added — Android app

- **Design system** — black / dark-navy / purple palette, gradient buttons,
  glowing card borders, rounded surfaces, custom typography scale, and a
  branded splash screen using the RUK AI atom-and-chat logo.
- **Dashboard** — logo and title, Premium Pro badge, AI search bar, and cards
  for AI Chat, Image Generator, Video Generator, Code Assistant, PDF Chat,
  AI Tools, Voice Assistant, History and Profile.
- **AI Chat** — streaming responses over SSE, model picker, conversation
  history, new conversation, copy, regenerate, Markdown rendering with
  syntax-highlighted code blocks, and file attachments.
- **AI Tools library** — twelve tools with search, category filters and
  per-tool pages: Writing Assistant, Image Generator, Video Generator, Code
  Assistant, Summarizer, Translator, PDF Assistant, Voice Generator, Math
  Solver, SEO Tools, Research Assistant, Chatbot.
- **Image Generator** — text-to-image, aspect-ratio and quality selection,
  regenerate, history, download.
- **Video Generator** — text-to-video and image-to-video, job polling with
  live status, preview and download.
- **Code Assistant** — 14 languages, syntax highlighting, explain / generate /
  debug / improve, and execution through the backend sandbox.
- **PDF Assistant** — document upload, text extraction, summarisation and
  question answering.
- **Profile** — account, plan, usage statistics, saved items, API key
  management and app settings.
- **Auth** — email/password registration and sign-in, optional Google
  Sign-In, and automatic token refresh.
- **Networking** — Retrofit + OkHttp, a dedicated SSE client, and a token
  store backed by `EncryptedSharedPreferences`.
- **Unit tests** — tool-catalogue integrity and wire-DTO contract tests.

### Added — Backend

- **Auth** — registration, login, Google ID-token exchange, refresh, logout
  and profile, with PBKDF2-HMAC-SHA256 password hashing and JWT access tokens.
- **Chat** — conversations, message history, SSE streaming, non-streaming
  completion, regeneration and a provider-aware model list.
- **Providers** — OpenAI, Google Gemini, OpenRouter, Together and a custom
  OpenAI-compatible endpoint, with streaming passthrough.
- **Generations** — image jobs (OpenAI, Stability) and video jobs (Replicate,
  Runway) with polling.
- **Code sandbox** — subprocess execution with a wall-clock timeout, output
  cap, language allowlist and scrubbed environment.
- **PDF service** — text extraction and document question answering.
- **Tools** — the twelve-tool catalogue with per-tool prompt templates.
- **API key management** — per-user provider keys encrypted at rest with
  Fernet and returned masked.
- **Share links** — expiring, revocable, optionally password-protected report
  links with access logging and truncated IPs.
- **Usage metering** — per-user statistics for the profile screen.
- **Rate limiting** — token-bucket limits per user for general and AI routes,
  per IP for auth routes.
- **Redaction** — API keys, bearer tokens, cookies and `Authorization` headers
  are stripped from logs and error payloads.
- **PostgreSQL schema** — `schema.sql` mirroring the ORM models.
- **Test suite** — 109 pytest tests covering security primitives, the rate
  limiter, the code sandbox and end-to-end API flows.

### Added — Project

- GitHub Actions workflows: backend tests on Python 3.9–3.12, and an Android
  debug-APK build with unit tests.
- README with setup for both platforms, a configuration reference, an API
  reference, a security model, troubleshooting and known limitations.
- MIT license, `.gitignore`, `.env.example` and this changelog.

### Security

- Provider API keys are never embedded in the Android app; the APK contains
  only the backend URL and an optional public Google client id.
- The code sandbox is documented as a convenience sandbox, not a security
  boundary, and is disabled by a single environment flag.

[1.0.0]: https://github.com/Janrahat/-RUK-AI-CODING-AGENT/releases/tag/v1.0.0
