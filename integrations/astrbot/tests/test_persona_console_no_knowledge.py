from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "textech_persona_console"


def test_persona_console_knowledge_site_is_retired():
    text = "\n".join(
        (ROOT / relative).read_text(encoding="utf-8")
        for relative in (
            "app/main.py",
            "app/config.py",
            "app/routers/ops.py",
            "static/app.js",
            "static/index.html",
            "Dockerfile",
            "docker-compose.yml",
        )
    )
    assert "/api/knowledge" not in text
    assert "KNOWLEDGE_DIR" not in text
    assert 'data-view="knowledge"' not in text
    assert not (ROOT / "app/services/knowledge.py").exists()
    assert not (ROOT / "knowledge").exists()
