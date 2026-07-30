from __future__ import annotations

import asyncio
import importlib.util
import json
import re
import sys
import tempfile
import types
import unittest
from pathlib import Path


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]


def _single_line(value, limit: int) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()[:limit]


def _load_adapter_module():
    astrbot = types.ModuleType("astrbot")
    api = types.ModuleType("astrbot.api")
    event = types.ModuleType("astrbot.api.event")
    api.logger = types.SimpleNamespace(
        debug=lambda *_args, **_kwargs: None,
        info=lambda *_args, **_kwargs: None,
        warning=lambda *_args, **_kwargs: None,
    )
    event.AstrMessageEvent = object
    sys.modules.update({"astrbot": astrbot, "astrbot.api": api, "astrbot.api.event": event})

    package_name = "photo_overlay_test"
    package = types.ModuleType(package_name)
    package.__path__ = []
    helpers = types.ModuleType(f"{package_name}.helpers")
    helpers._single_line = _single_line
    sys.modules[package_name] = package
    sys.modules[f"{package_name}.helpers"] = helpers

    name = f"{package_name}.soulmap_photo_adapter"
    path = ASTRBOT_ROOT / "astrbot_plugin_private_companion_overlay" / "soulmap_photo_adapter.py"
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class PhotoPersonaAdapterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.module = _load_adapter_module()
        self.temp_dir = tempfile.TemporaryDirectory()
        root = Path(self.temp_dir.name)
        personas = root / "personas.json"
        personas.write_text(
            json.dumps(
                {
                    "stable-user-42": {
                        "kind": "persona",
                        "scope": "shared",
                        "names": ["Mika", "Fox Engineer"],
                        "appearance": "silver hair and amber eyes",
                        "personality": "gentle and precise",
                        "tags": ["foxgirl"],
                        "attributes": {"species": "foxgirl", "tail": "nine"},
                    },
                    "private-user": {
                        "kind": "persona",
                        "scope": "private",
                        "names": ["Secret Persona"],
                        "appearance": "must stay private",
                    },
                }
            ),
            encoding="utf-8",
        )
        self.module._PERSONA_PROFILES = personas
        self.module._SOULMAP_PROFILES = root / "missing-soulmap.json"
        self.module._SOULMAP_CACHE = {"ts": 0.0, "data": None, "path": ""}

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_named_shared_persona_is_resolved_across_requesting_users(self) -> None:
        adapter = self.module.SoulmapPhotoAdapterMixin()

        context = adapter._soulmap_compose_photo_visual_context(
            "different-requesting-user",
            "Generate an image using Mika's persona.",
        )
        private_context = adapter._persona_lib_named_visual_context(
            "Generate an image using Secret Persona."
        )

        self.assertIn("Mika", context)
        self.assertIn("silver hair", context)
        self.assertIn("foxgirl", context)
        self.assertIn("tail=nine", context)
        self.assertNotIn("must stay private", context)
        self.assertEqual(private_context, "")

    def test_persona_context_is_preserved_during_safety_rewrite(self) -> None:
        module = self.module

        class Host(module.SoulmapPhotoAdapterMixin):
            enable_photo_prompt_llm_rewrite = True
            photo_prompt_llm_rewrite_system_prompt = ""
            photo_prompt_llm_rewrite_timeout_seconds = 10
            response_review_provider_id = ""
            mai_style_provider_id = ""
            llm_provider_id = "default-model"

            def __init__(self) -> None:
                self.calls: list[dict] = []

            def _task_provider(self, *_ids):
                return "default-model"

            def _build_natural_language_photo_prompt(self, **kwargs):
                return "Draft prompt with policy-sensitive slang; " + kwargs.get("soulmap_visual", "")

            async def _llm_call(self, prompt, **kwargs):
                self.calls.append({"prompt": prompt, **kwargs})
                return "Safe neutral artistic prompt preserving Mika, silver hair, amber eyes, and nine tails."

        host = Host()
        final_prompt, visual = asyncio.run(
            host._prepare_photo_prompt_with_soulmap(
                user_id="different-requesting-user",
                user_prompt="Use Mika's persona and rewrite it into a legal review-safe prompt before generation.",
                kind="text2img",
                has_reference=False,
            )
        )

        self.assertIn("Mika", visual)
        self.assertIn("tail=nine", visual)
        self.assertEqual(len(host.calls), 1)
        self.assertIn("Character visual notes", host.calls[0]["prompt"])
        self.assertIn("tail=nine", host.calls[0]["prompt"])
        self.assertIn("upstream T2I APIs", host.calls[0]["system_prompt"])
        self.assertIn("Safe neutral artistic prompt", final_prompt)


if __name__ == "__main__":
    unittest.main()
