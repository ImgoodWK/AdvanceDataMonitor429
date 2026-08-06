from __future__ import annotations

import sys
from pathlib import Path

from fastapi import Response
from starlette.requests import Request


ROOT = Path(__file__).resolve().parents[1]
CONSOLE = ROOT / "textech_persona_console"
sys.path.insert(0, str(CONSOLE))

from app.config import settings
from app.routers import auth_routes


def _request(cookie: str) -> Request:
    return Request({
        "type": "http",
        "method": "POST",
        "path": "/api/auth/portal",
        "headers": [(b"cookie", f"textech_portal_session={cookie}".encode())],
    })


def test_portal_login_uses_existing_admin_and_secure_native_cookie(monkeypatch):
    user = {"id": 7, "username": "admin", "role": "admin", "grants": [], "denies": []}
    monkeypatch.setattr(auth_routes, "_verify_portal_session", lambda value: value == "opaque_portal_session_123")
    monkeypatch.setattr(auth_routes.dbmod, "get_portal_admin", lambda: user)
    monkeypatch.setattr(auth_routes, "enrich_user", lambda value: value)
    monkeypatch.setattr(settings, "cookie_secure", True)
    response = Response()

    result = auth_routes.portal_login(_request("opaque_portal_session_123"), response)

    assert result["role"] == "admin"
    cookie = response.headers["set-cookie"]
    assert settings.cookie_name in cookie
    assert "HttpOnly" in cookie
    assert "Secure" in cookie


def test_portal_cookie_validation_rejects_header_injection():
    assert auth_routes._verify_portal_session("bad\r\nInjected: value") is False


def test_portal_verifier_disables_redirects(monkeypatch):
    captured = {}

    class FakeOpener:
        def open(self, request, timeout):
            captured["request"] = request
            captured["timeout"] = timeout
            raise auth_routes.urllib.error.HTTPError(
                request.full_url,
                302,
                "redirect rejected",
                {},
                None,
            )

    def fake_build_opener(handler):
        captured["handler"] = handler
        return FakeOpener()

    monkeypatch.setattr(auth_routes.urllib.request, "build_opener", fake_build_opener)
    monkeypatch.setattr(settings, "portal_auth_url", "http://portal.internal/auth/check")

    assert auth_routes._verify_portal_session("opaque_portal_session_123") is False
    assert isinstance(captured["handler"], auth_routes._RejectRedirects)
    assert captured["request"].get_header("Cookie") == "textech_portal_session=opaque_portal_session_123"
