# RUK AI GLOBAL — Multi-AI Starter v0.3.0

Responsive Next.js dashboard + FastAPI backend with a provider selector. Supports multiple API providers through their OpenAI-compatible Chat Completions endpoint, plus a native Anthropic Messages adapter.

## Providers
OpenAI, Google Gemini (OpenAI-compatible endpoint), OpenRouter, Anthropic Claude (native Messages API), DeepSeek, Groq, Mistral, Together AI, Fireworks AI, Cerebras, xAI, and a custom OpenAI-compatible endpoint.

Provider/model availability, exact model IDs, API access, account credits, rate limits and endpoint compatibility depend on each provider. This does not mean every model on earth is automatically available; add a valid key and use a model ID enabled for your account.

## Configure keys securely
From `ruk-ai-global/backend`, copy `.env.example` to `.env` and fill only the keys for providers you use. Example:

```env
OPENAI_API_KEY=your_key
GOOGLE_API_KEY=your_key
OPENROUTER_API_KEY=your_key
ANTHROPIC_API_KEY=your_key
DEEPSEEK_API_KEY=your_key
GROQ_API_KEY=your_key
```

Restart FastAPI after editing `.env`. Never put real keys in GitHub, screenshots, frontend variables, or chat messages. The `/api/providers` endpoint reports configured status only and never returns key values.

Optional custom OpenAI-compatible provider:

```env
CUSTOM_AI_API_KEY=your_key
CUSTOM_AI_BASE_URL=https://provider.example/v1
CUSTOM_AI_MODEL=model-id
```

Use the provider's documented base URL and exact model ID. Custom providers must support `POST /chat/completions`.

## Run backend

```bash
cd ruk-ai-global/backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# macOS/Linux/Termux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Run tests: `pytest`. Health: http://localhost:8000/api/health. Provider status: http://localhost:8000/api/providers.

## Run frontend

```bash
cd ruk-ai-global/frontend
npm install
npm run dev
```

Open http://localhost:3000. Choose a provider in the AI Model Router, optionally enter an exact model ID, then chat. Only configured status is shown; API keys remain on the backend.

## Android
For same-Wi-Fi access, set `NEXT_PUBLIC_API_URL=http://YOUR_PC_LAN_IP:8000` in `frontend/.env.local`, add `http://YOUR_PC_LAN_IP:3000` to backend `ALLOWED_ORIGINS`, restart both, then open the PC LAN URL in Android Chrome. Add to Home screen for a PWA-like install.

## Important limitations and safety
- API usage is generally billed by each provider; this project does not include API credits.
- Gemini is accessed using Google's OpenAI-compatible endpoint. Anthropic uses its native Messages API. Other listed providers use OpenAI-compatible chat completions; verify the provider's current API docs and model ID.
- Do not expose this development backend publicly. It has no user authentication, encrypted key vault, per-user access control, or rate limiting yet.
- Never commit real API keys. The backend .env file is gitignored.
- Native Android packaging, persistent user profiles, encrypted provider-key management and a permission-gated desktop agent remain future work.
