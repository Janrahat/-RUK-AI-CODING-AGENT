# RUK AI GLOBAL — Starter v0.1.0

A cross-device starter: Next.js web/PWA interface + FastAPI backend. The same web UI can run on PC and Android browsers; install it on Android from Chrome's **Add to Home screen**. Native Android and a PC desktop agent are later phases.

## Requirements
- Python 3.10+
- Node.js 20.9+
- An API key from an AI provider that supports the OpenAI-compatible `/chat/completions` API

## 1) Backend

```bash
cd backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux/Termux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Edit `backend/.env` and add your key to `AI_API_KEY`. Keep this file private; never commit it.

Start API:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Health check: http://localhost:8000/api/health

## 2) Frontend (new terminal)

```bash
cd frontend
npm install
```

Optional: create `frontend/.env.local` and set `NEXT_PUBLIC_API_URL=http://localhost:8000` (default already uses this).

```bash
npm run dev
```

Open http://localhost:3000 on PC. On Android, the phone and PC must be on the same Wi-Fi; replace `localhost` with the PC's LAN IP in `frontend/.env.local` and in backend `ALLOWED_ORIGINS`, then restart both servers. For Android-only development, run both services in Termux and use `localhost` in the browser.

## Voice
Voice input uses the browser's Web Speech API and may require Chrome plus microphone permission. Speech output uses browser text-to-speech. Voice support and Urdu recognition vary by browser/device.

## Security status
This is a local development starter, not production-ready. It does not yet include login, rate limiting, persistent chat history, or permission-gated computer control. Do not expose the backend publicly. API keys stay in backend `.env`, never in frontend code.

## Planned phases
1. Core API + chat UI (this starter)
2. Auth + database + chat history
3. Multiple provider/model routing
4. Better multilingual voice and streaming responses
5. Android app package
6. Permission-gated PC desktop agent
7. Deployment, monitoring, and security hardening
