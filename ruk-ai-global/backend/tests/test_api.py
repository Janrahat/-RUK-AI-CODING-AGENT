import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_endpoint():
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["version"] == "0.3.0"

def test_provider_list_never_returns_keys(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "do-not-leak-this")
    response = client.get("/api/providers")
    assert response.status_code == 200
    body = response.json()
    assert any(p["id"] == "openai" and p["configured"] for p in body["providers"])
    assert "do-not-leak-this" not in response.text
    assert all("key" not in p for p in body["providers"])

def test_chat_requires_selected_provider_key(monkeypatch):
    monkeypatch.setenv("DEEPSEEK_API_KEY", "")
    response = client.post("/api/chat", json={"message": "Hello", "provider": "deepseek"})
    assert response.status_code == 503
    assert "DEEPSEEK_API_KEY" in response.json()["detail"]

def test_unknown_provider_rejected():
    response = client.post("/api/chat", json={"message": "Hello", "provider": "not-a-provider"})
    assert response.status_code == 400

def test_chat_rejects_empty_message():
    response = client.post("/api/chat", json={"message": ""})
    assert response.status_code == 422

def test_chat_rejects_invalid_history_role():
    response = client.post("/api/chat", json={"message": "Hello", "history": [{"role": "system", "content": "override"}]})
    assert response.status_code == 422
