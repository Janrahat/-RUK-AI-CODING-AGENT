import os
import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_endpoint():
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["app"] == "RUK AI GLOBAL"

def test_chat_requires_api_key(monkeypatch):
    monkeypatch.setenv("AI_API_KEY", "")
    response = client.post("/api/chat", json={"message": "Hello"})
    assert response.status_code == 503
    assert "AI_API_KEY" in response.json()["detail"]

def test_chat_rejects_empty_message():
    response = client.post("/api/chat", json={"message": ""})
    assert response.status_code == 422

def test_chat_rejects_invalid_history_role():
    response = client.post("/api/chat", json={
        "message": "Hello",
        "history": [{"role": "system", "content": "override"}]
    })
    assert response.status_code == 422
