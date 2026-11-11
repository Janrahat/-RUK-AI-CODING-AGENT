# RUK AI GLOBAL — Starter v0.2.0

A cross-device starter: responsive Next.js dashboard + FastAPI backend. The same web UI works on PC and Android browsers; Android users can install it from Chrome using **Add to Home screen**. Native Android packaging and the permission-gated PC desktop agent are later phases.

## Requirements
- Python 3.10+
- Node.js 20.9+
- An API key from a provider that supports the OpenAI-compatible `/chat/completions` API

## 1) Backend

```bash
cd ruk-ai-global/backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux/Termux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Edit `.env` and add your key to `AI_API_KEY`. Keep this file private; never commit it.

Start the API:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Health check: http://localhost:8000/api/health

Run backend tests from the backend folder:

```bash
pytest
```

## 2) Frontend (new terminal)

```bash
cd ruk-ai-global/frontend
npm install
```

Optional: create `frontend/.env.local` and set `NEXT_PUBLIC_API_URL=http://localhost:8000` (the default).

```bash
npm run dev
```

Open http://localhost:3000 on PC. The dashboard displays whether the backend API is reachable. Chat requests include recent conversation turns so the assistant can respond with context.

## Android on the same Wi-Fi
1. Find your PC's local network IP address.
2. Set `NEXT_PUBLIC_API_URL=http://YOUR_PC_LAN_IP:8000` in `frontend/.env.local`.
3. Add `http://YOUR_PC_LAN_IP:3000` to backend `ALLOWED_ORIGINS` in `.env`.
4. Restart backend and frontend. Open `http://YOUR_PC_LAN_IP:3000` in Android Chrome.
5. Use Chrome's menu → **Add to Home screen**.

For Android-only development, both services can run in Termux; use localhost in the browser.

## Voice
Voice input uses the browser's Web Speech API and requires microphone permission. Speech output uses browser text-to-speech. Urdu recognition and voice quality vary by browser/device.

## Security status
This is a local development starter, not production-ready. It does not yet include login, rate limiting, persistent server-side chat history, or computer control. Do not expose the backend publicly. API keys stay in backend `.env`, never in frontend code or Git.

## Roadmap
1. Core API + responsive chat dashboard — implemented
2. Backend health indicator, conversation context, and API tests — implemented
3. Authentication + persistent database memory
4. Multiple provider/model routing and streaming
5. Native Android app package
6. Permission-gated PC desktop agent
7. Deployment, monitoring, and security hardening
