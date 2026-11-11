import os
from typing import Optional
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="RUK AI GLOBAL API", version="0.1.0")
allowed_origins = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[x.strip() for x in allowed_origins.split(",") if x.strip()],
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=12000)
    model: Optional[str] = None

class ChatResponse(BaseModel):
    reply: str
    model: str

@app.get("/api/health")
async def health():
    return {"status": "ok", "app": "RUK AI GLOBAL", "version": "0.1.0"}

@app.post("/api/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    api_key = os.getenv("AI_API_KEY", "").strip()
    base_url = os.getenv("AI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = body.model or os.getenv("AI_MODEL", "gpt-4.1-mini")
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="AI is not configured yet. Add AI_API_KEY to backend/.env."
        )

    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "You are RUK AI GLOBAL, a helpful, honest, multilingual assistant. Reply in the user's language."},
            {"role": "user", "content": body.message},
        ],
        "temperature": 0.7,
    }
    try:
        async with httpx.AsyncClient(timeout=60) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=payload,
            )
        response.raise_for_status()
        data = response.json()
        reply = data["choices"][0]["message"]["content"]
        return ChatResponse(reply=reply, model=model)
    except httpx.HTTPStatusError as exc:
        detail = exc.response.text[:1000]
        raise HTTPException(status_code=502, detail=f"AI provider error: {detail}")
    except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
        raise HTTPException(status_code=502, detail=f"Could not reach AI provider: {str(exc)}")
