# RUK AI backend

Requires Python 3.10 or newer.

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

## Deploy to Render (recommended)

1. Push this branch to GitHub and open the repository in Render.
2. Choose **New → Blueprint**, connect `Janrahat/-RUK-AI-CODING-AGENT`, and select the `feature/ruk-ai-all-in-one` branch while testing this pull request. Render reads the root `render.yaml`.
3. Create a managed PostgreSQL database in Render. Copy its **Internal Database URL** into the web service's `DATABASE_URL` environment variable. The backend normalizes Render's `postgres://` URL to SQLAlchemy's async PostgreSQL driver.
4. Generate a Fernet key locally and set it as `TOKEN_ENCRYPTION_KEY` in the Render service environment:

   ```bash
   python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
   ```

5. Add provider keys only if you want server-side fallback providers (for example `OPENROUTER_API_KEY` or `OPENAI_API_KEY`). Users can also store their own keys through the app.
6. Deploy and wait until the service is **Live**. Test `https://YOUR-SERVICE.onrender.com/health`; it should return JSON with `"status":"ok"`.
7. In the Android app, open **Profile → Backend URL**, enter `https://YOUR-SERVICE.onrender.com` (without a trailing slash), and sign in or register again.

The root `render.yaml` intentionally leaves `DATABASE_URL` and `TOKEN_ENCRYPTION_KEY` as dashboard secrets. Do not put passwords, API keys, or encryption keys in GitHub. Keep the encryption key backed up securely: losing it makes previously stored encrypted provider keys unreadable.

**Production notes:** Render's free web services can spin down when idle and may take time to wake. Choose a paid always-on service for a production app. Use a paid/appropriate PostgreSQL plan with backups for persistent user data. Code execution is disabled in the Render Blueprint by default because the included subprocess runner is not a strong multi-tenant security boundary; enable it only after moving execution into a dedicated isolated sandbox. Video generation requires a valid `FAL_API_KEY`; image generation requires a supported provider key. Provider use can incur charges.

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
