from __future__ import annotations

import importlib.util
import sys
import types
import unittest
from pathlib import Path


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]


def _load_module():
    path = (
        ASTRBOT_ROOT
        / "astrbot_plugin_private_companion_overlay"
        / "textech_photo_route.py"
    )
    spec = importlib.util.spec_from_file_location("textech_photo_route_test", path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class TextechReferenceGenerationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.module = _load_module()

    def test_compact_tt_matches_but_english_word_does_not(self) -> None:
        self.assertTrue(self.module.starts_with_tt("tt生图 一只狐狸"))
        self.assertTrue(self.module.starts_with_tt("TT：把背景改成星空"))
        self.assertFalse(self.module.starts_with_tt("ttl report"))

    def test_route_metadata_survives_stripped_message_text(self) -> None:
        event = types.SimpleNamespace(
            textech_route={
                "owner": "astrbot",
                "explicit": True,
                "prefix": "tt",
                "original_text": "tt 把背景改成星空",
                "routed_text": "把背景改成星空",
            },
            message_obj=types.SimpleNamespace(message_str="把背景改成星空"),
        )
        self.assertTrue(
            self.module.explicit_tt_request(event, "把背景改成星空")
        )

    def test_reference_image_forces_edit_with_explicit_tt(self) -> None:
        intent = {"kind": "text2img", "prompt": "tt画一张赛博朋克风格的图"}
        routed = self.module.resolve_reference_generation_intent(
            intent,
            text="tt画一张赛博朋克风格的图",
            has_reference=True,
            explicit_tt=True,
        )
        self.assertEqual(routed["kind"], "edit")
        self.assertTrue(routed["textech_reference_generation"])
        self.assertEqual(routed["prompt"], "画一张赛博朋克风格的图")
        self.assertEqual(intent["kind"], "text2img")

    def test_reference_image_without_tt_is_not_forced(self) -> None:
        intent = {"kind": "text2img", "prompt": "画一张赛博朋克风格的图"}
        routed = self.module.resolve_reference_generation_intent(
            intent,
            text="画一张赛博朋克风格的图",
            has_reference=True,
            explicit_tt=False,
        )
        self.assertEqual(routed, intent)

    def test_reference_style_prompt_is_recognized_when_host_intent_misses(self) -> None:
        routed = self.module.resolve_reference_generation_intent(
            {},
            text="赛博朋克风格，背景换成雨夜街道",
            has_reference=True,
            explicit_tt=True,
        )
        self.assertEqual(routed["kind"], "edit")
        self.assertIn("赛博朋克", routed["prompt"])

    def test_reference_content_question_is_not_generation_intent(self) -> None:
        routed = self.module.resolve_reference_generation_intent(
            {},
            text="这张图里有什么",
            has_reference=True,
            explicit_tt=True,
        )
        self.assertEqual(routed, {})


if __name__ == "__main__":
    unittest.main()
