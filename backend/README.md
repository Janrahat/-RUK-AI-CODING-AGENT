# RUK AI backend

FastAPI service used by the RUK AI Android client.

## Quick start

```bash
cd backend
python -m venv .venv
# Linux / macOS / Termux
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Set a strong `JWT_SECRET` and `TOKEN_ENCRYPTION_KEY` in `.env`. Add the provider key(s) you use (for example `OPENROUTER_API_KEY`), or let each user save their own key from Profile → API Keys. Never commit the real `.env` file.

Run locally:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

API docs: `http://localhost:8000/docs`; health check: `/health`.

## PostgreSQL

The backend uses async SQLAlchemy. SQLite is the zero-configuration development default. For PostgreSQL set:

```env
DATABASE_URL=postgresql+asyncpg://rukai:YOUR_PASSWORD@localhost:5432/rukai
```

Create the database before starting the API.

## Android connection

In the Android app open Profile → Backend URL and enter the reachable backend URL. Use an HTTPS URL for a deployed server. The emulator's `10.0.2.2` host only works for local emulator-to-host networking; it is not your phone's public server address.

## Implemented API modules

- Authentication: register, login, Google ID-token verification, refresh, current user, logout
- Chat: provider/model catalogue, stored conversations, SSE streaming, regenerate and delete
- Provider keys: encrypted-at-rest storage, masked key hints, provider test
- Generations: image and asynchronous video jobs
- Code: explain, generate, debug, improve and isolated execution service
- PDF: upload, text extraction, summarize and document Q&A
- Tools: writer, summarizer, translator, math, SEO, research and voice-ready script tools
- Usage quotas, saved items and transparent report links

## Tests

```bash
pip install -r requirements-dev.txt
pytest -q
```

The test suite uses temporary SQLite and does not require live provider credentials. Real image/video/chat generation still requires a supported provider key and may incur provider charges.

## Production checklist

Before public launch, deploy behind HTTPS, use PostgreSQL, set strong unique secrets, restrict CORS, configure backups and monitoring, verify provider limits, and connect Google Play Billing before granting paid Pro access. Code execution should run in an isolated container with resource and network restrictions; do not expose an unrestricted host shell.
