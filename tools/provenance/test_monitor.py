from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("monitor.py")
SPEC = importlib.util.spec_from_file_location("textech_provenance_monitor", MODULE_PATH)
assert SPEC and SPEC.loader
monitor = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = monitor
SPEC.loader.exec_module(monitor)


class FakeResponse:
    def __init__(self, payload):
        self.payload = json.dumps(payload).encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return self.payload


class ProvenanceMonitorTest(unittest.TestCase):
    def test_public_finding_excludes_official_allowed_and_private_results(self):
        allowlist = {
            "imgoodwk/textech-gtnh",
            "example/allowed",
        }
        base = {"path": "README.md", "html_url": "https://example.invalid/blob", "sha": "abc123"}
        official = {**base, "repository": {"full_name": "ImgoodWK/TeXTech-GTNH", "private": False}}
        allowed = {**base, "repository": {"full_name": "Example/Allowed", "private": False}}
        private = {**base, "repository": {"full_name": "Example/Private", "private": True}}
        public = {**base, "repository": {"full_name": "Example/Public", "private": False, "visibility": "public"}}
        self.assertIsNone(monitor.public_finding(official, "marker", allowlist))
        self.assertIsNone(monitor.public_finding(allowed, "marker", allowlist))
        self.assertIsNone(monitor.public_finding(private, "marker", allowlist))
        self.assertEqual(monitor.public_finding(public, "marker", allowlist).repository, "Example/Public")

    def test_run_writes_reports_and_returns_two_for_unallowed_public_hit(self):
        config = {
            "schema_version": 1,
            "official_repository": "ImgoodWK/TeXTech-GTNH",
            "allowlist": ["ImgoodWK/TeXTech-GTNH"],
            "queries": [{"id": "marker", "label": "Marker", "query": "\"unique\""}],
            "limitations": ["Literal matching is incomplete."],
        }
        payload = {
            "total_count": 2,
            "items": [
                {
                    "path": "NOTICE.md",
                    "html_url": "https://github.com/ImgoodWK/TeXTech-GTNH/blob/a/NOTICE.md",
                    "sha": "a",
                    "repository": {"full_name": "ImgoodWK/TeXTech-GTNH", "private": False},
                },
                {
                    "path": "plan.md",
                    "html_url": "https://github.com/example/public/blob/b/plan.md",
                    "sha": "b",
                    "repository": {"full_name": "example/public", "private": False, "visibility": "public"},
                },
            ],
        }

        def opener(request, timeout=0):
            self.assertIn("is%3Apublic", request.full_url)
            self.assertEqual(timeout, 30)
            return FakeResponse(payload)

        with tempfile.TemporaryDirectory() as temp:
            temp_path = Path(temp)
            config_path = temp_path / "config.json"
            output_path = temp_path / "report"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            self.assertEqual(monitor.run(config_path, output_path, "token", opener), 2)
            report = json.loads((output_path / "provenance-report.json").read_text(encoding="utf-8"))
            self.assertEqual(len(report["unallowed_public_findings"]), 1)
            self.assertEqual(report["unallowed_public_findings"][0]["repository"], "example/public")
            self.assertIn("investigation leads", (output_path / "provenance-report.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
