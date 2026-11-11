import os
from typing import Literal
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from dotenv import load_dotenv

load_dotenv()
app = FastAPI(title="RUK AI GLOBAL API", version="0.3.0")
allowed_origins = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[x.strip() for x in allowed_origins.split(",") if x.strip()],
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)

# Most providers below expose an OpenAI-compatible Chat Completions endpoint.
# Anthropic uses its native Messages API adapter. Add a key in backend/.env.
PROVIDERS = {
    "openai": {"name": "OpenAI", "key": "OPENAI_API_KEY", "base": "https://api.openai.com/v1", "model": "gpt-4.1-mini", "protocol": "openai"},
    "google": {"name": "Google Gemini", "key": "GOOGLE_API_KEY", "base": "https://generativelanguage.googleapis.com/v1beta/openai", "model": "gemini-2.5-flash", "protocol": "openai"},
    "openrouter": {"name": "OpenRouter", "key": "OPENROUTER_API_KEY", "base": "https://openrouter.ai/api/v1", "model": "openai/gpt-4.1-mini", "protocol": "openai"},
    "anthropic": {"name": "Anthropic Claude", "key": "ANTHROPIC_API_KEY", "base": "https://api.anthropic.com", "model": "claude-sonnet-4-20250514", "protocol": "anthropic"},
    "deepseek": {"name": "DeepSeek", "key": "DEEPSEEK_API_KEY", "base": "https://api.deepseek.com/v1", "model": "deepseek-chat", "protocol": "openai"},
    "groq": {"name": "Groq", "key": "GROQ_API_KEY", "base": "https://api.groq.com/openai/v1", "model": "llama-3.3-70b-versatile", "protocol": "openai"},
    "mistral": {"name": "Mistral", "key": "MISTRAL_API_KEY", "base": "https://api.mistral.ai/v1", "model": "mistral-small-latest", "protocol": "openai"},
    "together": {"name": "Together AI", "key": "TOGETHER_API_KEY", "base": "https://api.together.xyz/v1", "model": "meta-llama/Llama-3.3-70B-Instruct-Turbo", "protocol": "openai"},
    "fireworks": {"name": "Fireworks AI", "key": "FIREWORKS_API_KEY", "base": "https://api.fireworks.ai/inference/v1", "model": "accounts/fireworks/models/llama-v3p3-70b-instruct", "protocol": "openai"},
    "cerebras": {"name": "Cerebras", "key": "CEREBRAS_API_KEY", "base": "https://api.cerebras.ai/v1", "model": "llama-3.3-70b", "protocol": "openai"},
    "xai": {"name": "xAI", "key": "XAI_API_KEY", "base": "https://api.x.ai/v1", "model": "grok-3-mini", "protocol": "openai"},
    "custom": {"name": "Custom OpenAI-compatible", "key": "CUSTOM_AI_API_KEY", "base": "CUSTOM_AI_BASE_URL", "model": "CUSTOM_AI_MODEL", "protocol": "custom"},
}

class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=12000)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=12000)
    history: list[ChatTurn] = Field(default_factory=list, max_length=40)
    provider: str = "openai"
    model: str | None = None

class ChatResponse(BaseModel):
    reply: str
    model: str
    provider: str

def provider_config(provider_id: str):
    config = PROVIDERS.get(provider_id)
    if not config:
        raise HTTPException(status_code=400, detail="Unknown AI provider.")
    key = os.getenv(config["key"], "").strip()
    if provider_id == "custom":
        base = os.getenv("CUSTOM_AI_BASE_URL", "").strip().rstrip("/")
        model_default = os.getenv("CUSTOM_AI_MODEL", "").strip()
    else:
        base = config["base"]
        model_default = config["model"]
    if not key:
        raise HTTPException(status_code=503, detail=f"{config['name']} is not configured. Add {config['key']} to backend/.env and restart the API.")
    if not base or not model_default:
        raise HTTPException(status_code=503, detail="Custom provider requires CUSTOM_AI_BASE_URL and CUSTOM_AI_MODEL.")
    return config, key, base, model_default

@app.get("/api/health")
async def health():
    return {"status": "ok", "app": "RUK AI GLOBAL", "version": "0.3.0"}

@app.get("/api/providers")
async def providers():
    # Never return API keys; only reveal whether each provider is configured.
    result = []
    for provider_id, config in PROVIDERS.items():
        configured = bool(os.getenv(config["key"], "").strip())
        if provider_id == "custom":
            configured = configured and bool(os.getenv("CUSTOM_AI_BASE_URL", "").strip()) and bool(os.getenv("CUSTOM_AI_MODEL", "").strip())
        result.append({"id": provider_id, "name": config["name"], "configured": configured, "default_model": config["model"] if provider_id != "custom" else os.getenv("CUSTOM_AI_MODEL", "")})
    return {"providers": result}

@app.post("/api/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    config, api_key, base_url, default_model = provider_config(body.provider)
    model = body.model or default_model
    system_prompt = "You are RUK AI GLOBAL, a helpful, honest, multilingual assistant. Reply in the user's language."
    conversation = [{"role": "system", "content": system_prompt}]
    conversation.extend(turn.model_dump() for turn in body.history)
    conversation.append({"role": "user", "content": body.message})
    try:
        async with httpx.AsyncClient(timeout=90) as client:
            if config["protocol"] == "anthropic":
                user_messages = [{"role": item["role"], "content": item["content"]} for item in conversation if item["role"] != "system"]
                response = await client.post(
                    f"{base_url}/v1/messages",
                    headers={"x-api-key": api_key, "anthropic-version": "2023-06-01", "content-type": "application/json"},
                    json={"model": model, "max_tokens": 2048, "system": system_prompt, "messages": user_messages},
                )
                response.raise_for_status()
                data = response.json()
                reply = "".join(part.get("text", "") for part in data.get("content", []) if part.get("type") == "text")
            else:
                response = await client.post(
                    f"{base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                    json={"model": model, "messages": conversation, "temperature": 0.7},
                )
                response.raise_for_status()
                data = response.json()
                reply = data["choices"][0]["message"]["content"]
        if not isinstance(reply, str) or not reply.strip():
            raise ValueError("AI provider returned an empty response")
        return ChatResponse(reply=reply, model=model, provider=body.provider)
    except httpx.HTTPStatusError as exc:
        # Avoid echoing request headers or secrets in error responses.
        detail = exc.response.text[:700]
        raise HTTPException(status_code=502, detail=f"{config['name']} API error ({exc.response.status_code}): {detail}")
    except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
        raise HTTPException(status_code=502, detail=f"Could not complete AI request: {str(exc)}")
