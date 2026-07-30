from __future__ import annotations

import importlib.util
import sys
import tempfile
import types
import unittest
from pathlib import Path


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]


def _install_astrbot_stubs() -> None:
    astrbot = types.ModuleType("astrbot")
    api = types.ModuleType("astrbot.api")
    event = types.ModuleType("astrbot.api.event")
    provider = types.ModuleType("astrbot.api.provider")
    star = types.ModuleType("astrbot.api.star")
    core = types.ModuleType("astrbot.core")
    message = types.ModuleType("astrbot.core.message")
    components = types.ModuleType("astrbot.core.message.components")

    def decorator(*_args, **_kwargs):
        return lambda value: value

    event.AstrMessageEvent = object
    event.filter = types.SimpleNamespace(
        EventMessageType=types.SimpleNamespace(ALL="all"),
        event_message_type=decorator,
        on_llm_request=decorator,
        on_llm_response=decorator,
        on_decorating_result=decorator,
        command=decorator,
    )
    provider.LLMResponse = object
    provider.ProviderRequest = object

    class Star:
        def __init__(self, context=None):
            self.context = context

    class StarTools:
        @staticmethod
        def get_data_dir():
            return Path(tempfile.gettempdir()) / "persona-lib-test"

    class Plain:
        def __init__(self, text: str = ""):
            self.text = text

    star.Context = object
    star.Star = Star
    star.StarTools = StarTools
    star.register = decorator
    components.At = object
    components.Plain = Plain
    api.logger = types.SimpleNamespace(
        debug=lambda *_args, **_kwargs: None,
        info=lambda *_args, **_kwargs: None,
        warning=lambda *_args, **_kwargs: None,
        error=lambda *_args, **_kwargs: None,
    )
    sys.modules.update(
        {
            "astrbot": astrbot,
            "astrbot.api": api,
            "astrbot.api.event": event,
            "astrbot.api.provider": provider,
            "astrbot.api.star": star,
            "astrbot.core": core,
            "astrbot.core.message": message,
            "astrbot.core.message.components": components,
        }
    )


def _load_persona_module():
    _install_astrbot_stubs()
    name = "persona_lib_v31_test"
    sys.modules.pop(name, None)
    path = ASTRBOT_ROOT / "astrbot_plugin_persona_lib" / "main.py"
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class PersonaLibV31Tests(unittest.TestCase):
    def setUp(self) -> None:
        self.module = _load_persona_module()
        self.temp_dir = tempfile.TemporaryDirectory()
        plugin = self.module.PersonaLibPlugin.__new__(self.module.PersonaLibPlugin)
        plugin.config = {"allow_self_persona": True, "allow_shared_edits": True}
        plugin.store = self.module.PersonaStore(Path(self.temp_dir.name))
        self.plugin = plugin

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_stale_instruction_is_upgraded_to_full_protocol(self) -> None:
        self.plugin.config["collect_instruction"] = '[PersonaOp: {"action":"upsert"}]'

        instruction = self.plugin._collect_instruction()

        for token in ("target_id", "tags", "attributes"):
            self.assertIn(token, instruction)

    def test_nested_attributes_and_stable_target_id_round_trip(self) -> None:
        text = (
            'Answer[PersonaOp: {"action":"upsert","kind":"persona",'
            '"target_id":"stable-user-42","target":"Mika","names":["Mika"],'
            '"tags":["foxgirl"],"attributes":{"species":"foxgirl","hair":"silver"}}]'
        )
        operations = self.module.extract_persona_operations(text)
        self.assertEqual(len(operations), 1)
        operation = operations[0][0]
        self.assertEqual(operation["attributes"]["hair"], "silver")
        self.assertNotIn("PersonaOp", self.module.strip_persona_blocks(text))

        changed = self.plugin._apply_operation(
            operation,
            contributor="actor-1",
            mentioned_ids=["stable-user-42"],
            mention_labels={"stable-user-42": "Platform Name"},
        )

        self.assertTrue(changed)
        entry = self.plugin.store.get("stable-user-42", "actor-1")
        self.assertEqual(entry["subject_id"], "stable-user-42")
        self.assertIn("Mika", entry["names"])
        self.assertIn("Platform Name", entry["names"])
        self.assertIn("foxgirl", entry["tags"])
        self.assertEqual(entry["attributes"], {"species": "foxgirl", "hair": "silver"})

        updated = self.plugin._apply_operation(
            {
                "action": "upsert",
                "kind": "persona",
                "target": "Mika",
                "attributes": {"role": "engineer"},
            },
            contributor="actor-2",
            mentioned_ids=[],
        )
        self.assertTrue(updated)
        entry = self.plugin.store.get("stable-user-42", "actor-2")
        self.assertEqual(entry["attributes"]["role"], "engineer")
        self.assertEqual(entry["subject_id"], "stable-user-42")


if __name__ == "__main__":
    unittest.main()
