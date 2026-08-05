from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "astrbot_dashboard_overlay"


def _module():
    spec = importlib.util.spec_from_file_location("astrbot_dashboard_overlay", OVERLAY / "apply_overlay.py")
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_dashboard_overlay_is_idempotent(tmp_path):
    module = _module()
    dist = tmp_path / "dist"
    dist.mkdir()
    (dist / "index.html").write_text("<html><head></head><body></body></html>", encoding="utf-8")

    assert module.install(dist, apply=True) is True
    first = (dist / "index.html").read_text(encoding="utf-8")
    assert first.count(module.START) == 1
    assert (dist / "textech-portal-sso.js").is_file()
    assert (dist / "textech-theme.css").is_file()

    assert module.install(dist, apply=True) is False
    assert (dist / "index.html").read_text(encoding="utf-8") == first


def test_portal_bootstrap_uses_fragment_and_native_astrbot_storage():
    source = (OVERLAY / "portal-sso.js").read_text(encoding="utf-8")
    assert "window.location.hash" in source
    assert 'localStorage.setItem("token"' in source
    assert 'localStorage.setItem("user"' in source
    assert "astrbot_dashboard_jwt" in source
    assert '<script src="/textech-portal-sso.js"></script>' in _module().BLOCK
    assert "defer" not in _module().BLOCK
