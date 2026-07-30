from __future__ import annotations

import asyncio
import importlib.util
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]
if str(ASTRBOT_ROOT) not in sys.path:
    sys.path.insert(0, str(ASTRBOT_ROOT))

try:
    import pydantic_settings  # noqa: F401
except ModuleNotFoundError:
    pydantic_settings = types.ModuleType("pydantic_settings")

    class BaseSettings:
        def __init__(self, **kwargs) -> None:
            for key, value in kwargs.items():
                setattr(self, key, value)

    class SettingsConfigDict(dict):
        pass

    pydantic_settings.BaseSettings = BaseSettings
    pydantic_settings.SettingsConfigDict = SettingsConfigDict
    sys.modules["pydantic_settings"] = pydantic_settings

from textech_persona_console.app import json_store
from textech_persona_console.app.config import settings
from textech_persona_console.app.services import message_queue


class PersonaStudioQueueTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.data_dir = Path(self.temp_dir.name)
        self.original_data_dir = settings.astrbot_data
        settings.astrbot_data = self.data_dir
        json_store.write_json(
            "plugin_data/astrbot_plugin_persona_lib/personas.json",
            {
                "shared-subject": {
                    "kind": "persona",
                    "scope": "shared",
                    "names": ["Ada"],
                    "tags": ["guide"],
                    "personality": "concise",
                },
                "private-subject": {
                    "kind": "persona",
                    "scope": "private",
                    "names": ["Private Ada"],
                },
            },
        )

    def tearDown(self) -> None:
        settings.astrbot_data = self.original_data_dir
        self.temp_dir.cleanup()

    def test_persona_options_and_preview_queue_are_redacted(self) -> None:
        items = message_queue.list_personas()
        self.assertEqual(items[0]["persona_key"], message_queue.DEFAULT_PERSONA_KEY)
        self.assertIn("Ada", [item["display_name"] for item in items])
        rendered = json.dumps(items, ensure_ascii=True)
        self.assertNotIn("shared-subject", rendered)
        self.assertNotIn("private-subject", rendered)
        self.assertNotIn("store_key", rendered)

        selected = next(item["persona_key"] for item in items if item["display_name"] == "Ada")
        created = message_queue.create_draft_job(
            target_key=message_queue.PREVIEW_TARGET_KEY,
            persona_key=selected,
            prompt="Answer the operator.",
            requester="tester",
        )
        self.assertEqual(created["target_kind"], "preview")
        self.assertEqual(created["persona_key"], selected)
        self.assertNotIn("target_umo", created)

        queue = json_store.read_json("plugin_data/astrbot_plugin_console_bridge/queue.json", {})
        raw = queue["jobs"][0]
        self.assertEqual(raw["target_key"], message_queue.PREVIEW_TARGET_KEY)
        self.assertEqual(raw["target_umo"], "")

        with self.assertRaises(ValueError):
            message_queue.create_draft_job(
                target_key=message_queue.PREVIEW_TARGET_KEY,
                persona_key="persona:unknown",
                prompt="Answer the operator.",
                requester="tester",
            )
        with self.assertRaises(ValueError):
            message_queue.create_send_job(
                target_key=message_queue.PREVIEW_TARGET_KEY,
                message="Never send this.",
                requester="tester",
            )


def _install_astrbot_stubs() -> None:
    astrbot = types.ModuleType("astrbot")
    api = types.ModuleType("astrbot.api")
    star = types.ModuleType("astrbot.api.star")
    core = types.ModuleType("astrbot.core")
    message = types.ModuleType("astrbot.core.message")
    components = types.ModuleType("astrbot.core.message.components")

    class Star:
        def __init__(self, context, config=None):
            self.context = context
            self.config = config or {}

    class Plain:
        def __init__(self, text: str):
            self.text = text

    def register(*_args, **_kwargs):
        return lambda cls: cls

    api.logger = types.SimpleNamespace(info=lambda *_args, **_kwargs: None, warning=lambda *_args, **_kwargs: None)
    star.Context = object
    star.Star = Star
    star.register = register
    components.Plain = Plain
    sys.modules.update(
        {
            "astrbot": astrbot,
            "astrbot.api": api,
            "astrbot.api.star": star,
            "astrbot.core": core,
            "astrbot.core.message": message,
            "astrbot.core.message.components": components,
        }
    )


def _load_bridge_module():
    _install_astrbot_stubs()
    name = "console_bridge_persona_studio_test"
    sys.modules.pop(name, None)
    path = ASTRBOT_ROOT / "astrbot_plugin_console_bridge" / "main.py"
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class PersonaStudioBridgeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.data_dir = Path(self.temp_dir.name)
        self.bridge_module = _load_bridge_module()
        self.bridge_module.DATA_ROOT = self.data_dir
        self.bridge_module.PERSONAS_PATH = self.data_dir / "plugin_data/astrbot_plugin_persona_lib/personas.json"
        self.bridge_module.CMD_CONFIG_PATH = self.data_dir / "cmd_config.json"
        self.bridge_module.PC_CONFIG_PATH = self.data_dir / "config/astrbot_plugin_private_companion_config.json"
        self.bridge_module.COMPANIONS_PATH = self.data_dir / "companions.json"
        self.bridge_module.PERSONAS_PATH.parent.mkdir(parents=True, exist_ok=True)
        self.bridge_module.PERSONAS_PATH.write_text(
            json.dumps(
                {
                    "shared-subject": {
                        "kind": "persona",
                        "scope": "shared",
                        "names": ["Ada"],
                        "personality": "concise",
                    }
                }
            ),
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_preview_draft_uses_selected_persona_and_preview_send_fails(self) -> None:
        class Provider:
            def __init__(self) -> None:
                self.calls: list[dict] = []

            async def text_chat(self, **kwargs):
                self.calls.append(kwargs)
                return types.SimpleNamespace(completion_text="Preview answer")

        class Context:
            def __init__(self, provider: Provider) -> None:
                self.provider = provider
                self.umos: list[str | None] = []

            def get_using_provider(self, umo=None):
                self.umos.append(umo)
                return self.provider

        provider = Provider()
        context = Context(provider)
        bridge = self.bridge_module.ConsoleBridgePlugin(context, config={})
        queue_path = self.data_dir / "queue.json"
        bridge.queue_path = queue_path
        selected = bridge._persona_key("shared-subject")
        preview_job = {
            "id": "preview-job",
            "type": "draft",
            "status": "processing",
            "target_key": "preview:local",
            "target_kind": "preview",
            "target_display_name": "web preview",
            "target_umo": "",
            "persona_key": selected,
            "prompt": "What is the plan?",
        }
        queue_path.write_text(json.dumps({"version": 1, "jobs": [preview_job]}), encoding="utf-8")

        asyncio.run(bridge._process_job(dict(preview_job)))

        finished = json.loads(queue_path.read_text(encoding="utf-8"))["jobs"][0]
        self.assertEqual(finished["status"], "draft_ready")
        self.assertEqual(finished["draft"], "Preview answer")
        self.assertEqual(context.umos, [None])
        self.assertIn("Ada", provider.calls[0]["system_prompt"])

        send_job = {
            "id": "preview-send-job",
            "type": "send",
            "status": "sending",
            "target_key": "preview:local",
            "target_kind": "preview",
            "target_umo": "",
            "message": "Never send this.",
        }
        queue_path.write_text(json.dumps({"version": 1, "jobs": [send_job]}), encoding="utf-8")
        asyncio.run(bridge._process_job(dict(send_job)))
        rejected = json.loads(queue_path.read_text(encoding="utf-8"))["jobs"][0]
        self.assertEqual(rejected["status"], "failed")
        self.assertEqual(len(provider.calls), 1)

    def test_draft_uses_astrbot_fallback_provider_order(self) -> None:
        self.bridge_module.CMD_CONFIG_PATH.write_text(
            json.dumps(
                {
                    "provider_settings": {
                        "fallback_chat_models": ["fallback-model"],
                    }
                }
            ),
            encoding="utf-8",
        )

        class Provider:
            def __init__(self, provider_id: str, *, fail: bool = False) -> None:
                self.provider_config = {"id": provider_id}
                self.fail = fail
                self.calls: list[dict] = []

            async def text_chat(self, **kwargs):
                self.calls.append(kwargs)
                if self.fail:
                    raise TimeoutError("primary timed out")
                return types.SimpleNamespace(completion_text="Fallback answer")

        class Context:
            def __init__(self, primary: Provider, fallback: Provider) -> None:
                self.primary = primary
                self.fallback = fallback
                self.resolved: list[str] = []

            def get_using_provider(self, umo=None):
                return self.primary

            def get_provider_by_id(self, provider_id: str):
                self.resolved.append(provider_id)
                return self.fallback if provider_id == "fallback-model" else None

        primary = Provider("primary-model", fail=True)
        fallback = Provider("fallback-model")
        context = Context(primary, fallback)
        bridge = self.bridge_module.ConsoleBridgePlugin(
            context,
            config={"draft_max_provider_attempts": 2, "draft_total_timeout_seconds": 30},
        )
        job = {
            "target_key": "preview:local",
            "target_kind": "preview",
            "target_display_name": "web preview",
            "persona_key": bridge._persona_key("shared-subject"),
            "prompt": "Answer with fallback.",
        }

        result = asyncio.run(bridge._generate_draft(job, ""))

        self.assertEqual(result, "Fallback answer")
        self.assertEqual(context.resolved, ["fallback-model"])
        self.assertEqual(len(primary.calls), 1)
        self.assertEqual(len(fallback.calls), 1)
        self.assertIn("Ada", fallback.calls[0]["system_prompt"])


if __name__ == "__main__":
    unittest.main()
