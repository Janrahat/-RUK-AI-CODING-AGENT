"""Unit tests for security primitives: hashing, JWT, encryption, redaction."""

from __future__ import annotations

import pytest

from app.security import (
    TokenError,
    create_token,
    decode_token,
    decrypt_secret,
    encrypt_secret,
    hash_password,
    mask_key,
    verify_password,
)
from app.services.tools import build_messages, redact


class TestPasswordHashing:
    def test_hash_is_not_the_plaintext(self):
        stored = hash_password("hunter2hunter2")
        assert "hunter2hunter2" not in stored
        assert stored.startswith("pbkdf2_sha256$")

    def test_round_trip(self):
        stored = hash_password("correct-horse-battery")
        assert verify_password("correct-horse-battery", stored) is True

    def test_wrong_password_rejected(self):
        stored = hash_password("correct-horse-battery")
        assert verify_password("wrong-password", stored) is False

    def test_salt_makes_hashes_unique(self):
        assert hash_password("same-password") != hash_password("same-password")

    def test_empty_password_rejected(self):
        with pytest.raises(ValueError):
            hash_password("")

    @pytest.mark.parametrize(
        "stored",
        ["", "not-a-hash", "pbkdf2_sha256$abc$def$ghi", "md5$1$2$3"],
    )
    def test_malformed_hash_returns_false(self, stored):
        assert verify_password("anything", stored) is False

    def test_none_hash_returns_false(self):
        assert verify_password("anything", None) is False


class TestJwt:
    def test_access_token_round_trip(self):
        token = create_token("user-123", "access")
        payload = decode_token(token, "access")
        assert payload["sub"] == "user-123"
        assert payload["typ"] == "access"

    def test_refresh_token_round_trip(self):
        token = create_token("user-123", "refresh")
        assert decode_token(token, "refresh")["typ"] == "refresh"

    def test_wrong_kind_is_rejected(self):
        token = create_token("user-123", "access")
        with pytest.raises(TokenError):
            decode_token(token, "refresh")

    def test_tampered_token_is_rejected(self):
        token = create_token("user-123", "access")
        with pytest.raises(TokenError):
            decode_token(token[:-3] + "abc", "access")

    def test_garbage_token_is_rejected(self):
        with pytest.raises(TokenError):
            decode_token("not.a.jwt", "access")

    def test_tokens_are_unique(self):
        assert create_token("u", "access") != create_token("u", "access")


class TestSecretEncryption:
    def test_round_trip(self):
        secret = "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789"
        token = encrypt_secret(secret)
        assert token != secret
        assert decrypt_secret(token) == secret

    def test_ciphertext_differs_each_time(self):
        assert encrypt_secret("same") != encrypt_secret("same")

    def test_tampered_ciphertext_raises(self):
        token = encrypt_secret("value")
        with pytest.raises(TokenError):
            decrypt_secret(token[:-4] + "AAAA")

    def test_mask_key_never_reveals_the_middle(self):
        masked = mask_key("sk-proj-abcdefghijklmnop")
        assert masked.startswith("sk-")
        assert masked.endswith("mnop")
        assert "abcdefghij" not in masked

    def test_mask_short_key_is_all_stars(self):
        assert mask_key("abc") == "***"

    def test_mask_empty_key(self):
        assert mask_key("") == ""


class TestRedaction:
    @pytest.mark.parametrize(
        "secret",
        [
            "sk-proj-abcdefghijklmnopqrstuvwxyz123456",
            "sk-abcdefghijklmnopqrstuvwxyz",
            "AIzaSyA1234567890abcdefghijklmnopqrstu",
            "ghp_abcdefghijklmnopqrstuvwxyz0123456789",
            "github_pat_abcdefghijklmnopqrstuv",
            "xoxb-1234567890-abcdefghijkl",
        ],
    )
    def test_known_key_shapes_are_redacted(self, secret):
        cleaned = redact(f"here is the key {secret} please use it")
        assert secret not in cleaned
        assert "[REDACTED]" in cleaned

    def test_bearer_header_is_redacted(self):
        cleaned = redact("Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345")
        assert "abcdefghijklmnopqrstuvwxyz012345" not in cleaned

    def test_jwt_is_redacted(self):
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        assert jwt not in redact(f"token={jwt}")

    def test_ordinary_text_is_untouched(self):
        text = "The quick brown fox jumps over the lazy dog."
        assert redact(text) == text

    def test_empty_input(self):
        assert redact("") == ""


class TestToolPrompts:
    def test_known_tool_builds_two_messages(self):
        messages = build_messages("summarizer", "Some long article text.")
        assert len(messages) == 2
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "Some long article text." in messages[1]["content"]

    def test_defaults_are_applied(self):
        messages = build_messages("translator", "Hello")
        assert "Urdu" in messages[1]["content"]

    def test_options_override_defaults(self):
        messages = build_messages("translator", "Hello", {"target_language": "French"})
        assert "French" in messages[1]["content"]

    def test_unknown_tool_raises(self):
        from app.services.tools import ToolError

        with pytest.raises(ToolError):
            build_messages("does-not-exist", "x")

    def test_every_declared_tool_renders(self):
        from app.services.tools import TOOL_SPECS

        for tool in TOOL_SPECS:
            messages = build_messages(tool, "sample input")
            assert messages[1]["content"].strip()
