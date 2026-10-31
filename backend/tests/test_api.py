"""End-to-end API tests against the real FastAPI app.

These exercise the actual routes with a real (SQLite) database. Provider
calls are not made: every test either uses a route that needs no upstream
API, or asserts the correct error when no provider key is configured.
"""

from __future__ import annotations


class TestMeta:
    def test_health(self, client):
        response = client.get("/health")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "ok"
        assert body["version"] == "1.0.0"

    def test_root(self, client):
        response = client.get("/")
        assert response.status_code == 200
        assert response.json()["name"] == "RUK AI API"

    def test_security_headers_are_present(self, client):
        response = client.get("/health")
        assert response.headers["X-Content-Type-Options"] == "nosniff"
        assert response.headers["Referrer-Policy"] == "no-referrer"

    def test_openapi_schema_builds(self, client):
        response = client.get("/openapi.json")
        assert response.status_code == 200
        paths = response.json()["paths"]
        for expected in (
            "/api/v1/auth/register",
            "/api/v1/chat/completions",
            "/api/v1/generations/image",
            "/api/v1/code/run",
            "/api/v1/pdf/upload",
            "/api/v1/tools/run",
            "/api/v1/keys",
            "/api/v1/usage/summary",
        ):
            assert expected in paths, f"{expected} missing from the OpenAPI schema"


class TestAuth:
    def test_register_returns_tokens_and_user(self, client):
        response = client.post(
            "/api/v1/auth/register",
            json={
                "email": "new@example.com",
                "password": "a-strong-password",
                "display_name": "New User",
            },
        )
        assert response.status_code == 201, response.text
        body = response.json()
        assert body["access_token"]
        assert body["refresh_token"]
        assert body["user"]["email"] == "new@example.com"
        assert body["user"]["plan"] == "free"

    def test_duplicate_email_is_rejected(self, client):
        payload = {"email": "dupe@example.com", "password": "a-strong-password"}
        assert client.post("/api/v1/auth/register", json=payload).status_code == 201
        assert client.post("/api/v1/auth/register", json=payload).status_code == 409

    def test_short_password_is_rejected(self, client):
        response = client.post(
            "/api/v1/auth/register",
            json={"email": "short@example.com", "password": "abc"},
        )
        assert response.status_code == 422

    def test_invalid_email_is_rejected(self, client):
        response = client.post(
            "/api/v1/auth/register",
            json={"email": "not-an-email", "password": "a-strong-password"},
        )
        assert response.status_code == 422

    def test_login_succeeds_with_correct_password(self, client):
        client.post(
            "/api/v1/auth/register",
            json={"email": "login@example.com", "password": "a-strong-password"},
        )
        response = client.post(
            "/api/v1/auth/login",
            json={"email": "login@example.com", "password": "a-strong-password"},
        )
        assert response.status_code == 200
        assert response.json()["access_token"]

    def test_login_fails_with_wrong_password(self, client):
        client.post(
            "/api/v1/auth/register",
            json={"email": "wrong@example.com", "password": "a-strong-password"},
        )
        response = client.post(
            "/api/v1/auth/login",
            json={"email": "wrong@example.com", "password": "not-the-password"},
        )
        assert response.status_code == 401

    def test_login_fails_for_unknown_user(self, client):
        response = client.post(
            "/api/v1/auth/login",
            json={"email": "ghost@example.com", "password": "a-strong-password"},
        )
        assert response.status_code == 401

    def test_me_requires_a_token(self, client):
        assert client.get("/api/v1/auth/me").status_code == 401

    def test_me_rejects_a_garbage_token(self, client):
        response = client.get(
            "/api/v1/auth/me", headers={"Authorization": "Bearer not-a-real-token"}
        )
        assert response.status_code == 401

    def test_me_returns_the_signed_in_user(self, auth_client):
        response = auth_client.get("/api/v1/auth/me")
        assert response.status_code == 200
        assert response.json()["email"] == "tester@example.com"

    def test_refresh_issues_a_new_access_token(self, client):
        registered = client.post(
            "/api/v1/auth/register",
            json={"email": "refresh@example.com", "password": "a-strong-password"},
        ).json()
        response = client.post(
            "/api/v1/auth/refresh",
            json={"refresh_token": registered["refresh_token"]},
        )
        assert response.status_code == 200
        assert response.json()["access_token"]

    def test_refresh_rejects_an_access_token(self, client):
        registered = client.post(
            "/api/v1/auth/register",
            json={"email": "refresh2@example.com", "password": "a-strong-password"},
        ).json()
        response = client.post(
            "/api/v1/auth/refresh",
            json={"refresh_token": registered["access_token"]},
        )
        assert response.status_code == 401

    def test_upgrade_switches_the_plan_to_pro(self, auth_client):
        response = auth_client.post("/api/v1/auth/upgrade")
        assert response.status_code == 200
        assert response.json()["plan"] == "pro"

    def test_password_is_never_returned(self, client):
        response = client.post(
            "/api/v1/auth/register",
            json={"email": "leak@example.com", "password": "a-strong-password"},
        )
        assert "a-strong-password" not in response.text
        assert "password" not in response.json()["user"]


class TestChat:
    def test_models_are_listed(self, auth_client):
        response = auth_client.get("/api/v1/chat/models")
        assert response.status_code == 200
        models = response.json()
        assert len(models) > 0
        assert all({"id", "label", "provider"} <= set(m) for m in models)

    def test_models_can_be_filtered_by_kind(self, auth_client):
        response = auth_client.get("/api/v1/chat/models?kind=image")
        assert response.status_code == 200
        assert all(m["kind"] == "image" for m in response.json())

    def test_invalid_kind_is_rejected(self, auth_client):
        assert auth_client.get("/api/v1/chat/models?kind=bogus").status_code == 422

    def test_conversations_start_empty(self, auth_client):
        response = auth_client.get("/api/v1/chat/conversations")
        assert response.status_code == 200
        assert response.json() == []

    def test_completions_requires_auth(self, client):
        response = client.post(
            "/api/v1/chat/completions",
            json={"model": "gpt-4o-mini", "messages": [{"role": "user", "content": "hi"}]},
        )
        assert response.status_code == 401

    def test_completions_without_a_provider_key_reports_clearly(self, auth_client):
        response = auth_client.post(
            "/api/v1/chat/completions",
            json={
                "model": "gpt-4o-mini",
                "messages": [{"role": "user", "content": "hello"}],
                "stream": True,
            },
        )
        # Either a clean 4xx explaining the missing key, or an SSE stream that
        # reports the error as an event. Both are acceptable; a 500 is not.
        assert response.status_code != 500
        if response.status_code == 200:
            assert "event:" in response.text
            assert "error" in response.text or "delta" in response.text

    def test_empty_message_list_is_rejected(self, auth_client):
        response = auth_client.post(
            "/api/v1/chat/completions",
            json={"model": "gpt-4o-mini", "messages": []},
        )
        assert response.status_code == 422

    def test_unknown_conversation_returns_404(self, auth_client):
        response = auth_client.get("/api/v1/chat/conversations/does-not-exist")
        assert response.status_code == 404

    def test_deleting_an_unknown_conversation_returns_404(self, auth_client):
        assert auth_client.delete("/api/v1/chat/conversations/nope").status_code == 404


class TestCode:
    def test_languages_are_reported(self, auth_client):
        response = auth_client.get("/api/v1/code/languages")
        assert response.status_code == 200
        body = response.json()
        assert "python" in body["executable"]
        assert body["exec_enabled"] is True

    def test_python_runs_and_returns_stdout(self, auth_client):
        response = auth_client.post(
            "/api/v1/code/run",
            json={"language": "python", "code": "print(6 * 7)"},
        )
        assert response.status_code == 200, response.text
        body = response.json()
        assert body["status"] == "completed"
        assert "42" in body["stdout"]

    def test_unsupported_language_returns_400(self, auth_client):
        response = auth_client.post(
            "/api/v1/code/run",
            json={"language": "rust", "code": "fn main() {}"},
        )
        assert response.status_code == 400

    def test_run_requires_auth(self, client):
        response = client.post(
            "/api/v1/code/run", json={"language": "python", "code": "print(1)"}
        )
        assert response.status_code == 401


class TestTools:
    def test_tool_catalogue_is_listed(self, auth_client):
        response = auth_client.get("/api/v1/tools")
        assert response.status_code == 200
        ids = {tool["id"] for tool in response.json()}
        assert {"writer", "summarizer", "translator", "math", "seo", "research"} <= ids

    def test_unknown_tool_is_rejected(self, auth_client):
        response = auth_client.post(
            "/api/v1/tools/run", json={"tool": "not-a-tool", "input": "x"}
        )
        assert response.status_code == 422

    def test_empty_input_is_rejected(self, auth_client):
        response = auth_client.post(
            "/api/v1/tools/run", json={"tool": "summarizer", "input": ""}
        )
        assert response.status_code == 422


class TestApiKeys:
    def test_keys_start_empty(self, auth_client):
        response = auth_client.get("/api/v1/keys")
        assert response.status_code == 200
        assert response.json() == []

    def test_saving_a_key_returns_only_a_hint(self, auth_client):
        secret = "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789"
        response = auth_client.put(
            "/api/v1/keys", json={"provider": "openai", "api_key": secret}
        )
        assert response.status_code == 200, response.text
        body = response.json()
        assert body["provider"] == "openai"
        assert body["key_hint"]
        # The full secret must never come back over the API.
        assert secret not in response.text

    def test_saved_key_is_listed(self, auth_client):
        auth_client.put(
            "/api/v1/keys",
            json={"provider": "gemini", "api_key": "AIzaSyA1234567890abcdefghijklmnopqrstu"},
        )
        providers = {k["provider"] for k in auth_client.get("/api/v1/keys").json()}
        assert "gemini" in providers

    def test_upsert_replaces_rather_than_duplicates(self, auth_client):
        auth_client.put(
            "/api/v1/keys", json={"provider": "openai", "api_key": "sk-aaaaaaaaaaaaaaaaaaaa"}
        )
        auth_client.put(
            "/api/v1/keys", json={"provider": "openai", "api_key": "sk-bbbbbbbbbbbbbbbbbbbb"}
        )
        keys = auth_client.get("/api/v1/keys").json()
        assert len([k for k in keys if k["provider"] == "openai"]) == 1

    def test_unknown_provider_is_rejected(self, auth_client):
        response = auth_client.put(
            "/api/v1/keys", json={"provider": "not-a-provider", "api_key": "x" * 20}
        )
        assert response.status_code == 422

    def test_short_key_is_rejected(self, auth_client):
        response = auth_client.put(
            "/api/v1/keys", json={"provider": "openai", "api_key": "short"}
        )
        assert response.status_code == 422

    def test_deleting_a_key_removes_it(self, auth_client):
        auth_client.put(
            "/api/v1/keys", json={"provider": "openai", "api_key": "sk-cccccccccccccccccccc"}
        )
        assert auth_client.delete("/api/v1/keys/openai").status_code == 204
        assert auth_client.get("/api/v1/keys").json() == []

    def test_keys_are_scoped_to_the_owner(self, client):
        first = client.post(
            "/api/v1/auth/register",
            json={"email": "owner1@example.com", "password": "a-strong-password"},
        ).json()
        second = client.post(
            "/api/v1/auth/register",
            json={"email": "owner2@example.com", "password": "a-strong-password"},
        ).json()

        client.put(
            "/api/v1/keys",
            json={"provider": "openai", "api_key": "sk-dddddddddddddddddddd"},
            headers={"Authorization": f"Bearer {first['access_token']}"},
        )
        other = client.get(
            "/api/v1/keys",
            headers={"Authorization": f"Bearer {second['access_token']}"},
        )
        assert other.json() == []


class TestUsage:
    def test_summary_reports_the_free_plan(self, auth_client):
        response = auth_client.get("/api/v1/usage/summary")
        assert response.status_code == 200
        body = response.json()
        assert body["plan"] == "free"
        assert body["daily_limit"] > 0
        assert body["remaining_today"] == body["daily_limit"]

    def test_summary_requires_auth(self, client):
        assert client.get("/api/v1/usage/summary").status_code == 401


class TestPdf:
    def test_documents_start_empty(self, auth_client):
        response = auth_client.get("/api/v1/pdf/documents")
        assert response.status_code == 200
        assert response.json() == []

    def test_non_pdf_upload_is_rejected(self, auth_client):
        response = auth_client.post(
            "/api/v1/pdf/upload",
            files={"file": ("notes.txt", b"just some text", "text/plain")},
        )
        # 422: the request is well-formed but the payload is not a PDF.
        assert response.status_code == 422

    def test_unknown_document_returns_404(self, auth_client):
        assert auth_client.get("/api/v1/pdf/documents/nope/text").status_code == 404


class TestReportLinks:
    def test_links_start_empty(self, auth_client):
        response = auth_client.get("/api/v1/links")
        assert response.status_code == 200
        assert response.json() == []

    def test_creating_a_link_returns_a_public_url(self, auth_client):
        response = auth_client.post(
            "/api/v1/links",
            json={"title": "My report", "content": "Findings go here."},
        )
        assert response.status_code == 201, response.text
        body = response.json()
        assert body["id"]
        assert body["url"].startswith("http")

    def test_a_public_link_can_be_opened_without_a_token(self, client, auth_client):
        created = auth_client.post(
            "/api/v1/links",
            json={"title": "Public", "content": "Anyone with the link can read this."},
        ).json()
        response = client.get(f"/r/{created['id']}")
        assert response.status_code == 200
        assert "Anyone with the link" in response.text

    def test_revoked_link_stops_working(self, client, auth_client):
        created = auth_client.post(
            "/api/v1/links",
            json={"title": "Revocable", "content": "Secret content."},
        ).json()
        auth_client.post(f"/api/v1/links/{created['id']}/revoke")
        # 404 rather than 410: a revoked link must not disclose that it
        # ever existed.
        assert client.get(f"/r/{created['id']}").status_code == 404

    def test_unknown_link_returns_404(self, client):
        assert client.get("/r/does-not-exist").status_code == 404

    def test_access_log_records_opens(self, client, auth_client):
        created = auth_client.post(
            "/api/v1/links",
            json={"title": "Logged", "content": "Tracked content."},
        ).json()
        client.get(f"/r/{created['id']}")
        log = auth_client.get(f"/api/v1/links/{created['id']}/access").json()
        assert len(log) >= 1
        # The log must not store a full IP address.
        assert "truncated_ip" in log[0]
