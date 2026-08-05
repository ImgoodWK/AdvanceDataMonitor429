from __future__ import annotations

import asyncio
import importlib.util
import sys
import types
import unittest
from pathlib import Path


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]


def _load_module():
    astrbot = types.ModuleType("astrbot")
    api = types.ModuleType("astrbot.api")
    event_module = types.ModuleType("astrbot.api.event")
    star_module = types.ModuleType("astrbot.api.star")

    def decorator(*_args, **_kwargs):
        return lambda target: target

    api.logger = types.SimpleNamespace(info=lambda *_args, **_kwargs: None)
    event_module.AstrMessageEvent = object
    event_module.filter = types.SimpleNamespace(
        EventMessageType=types.SimpleNamespace(ALL="all"),
        event_message_type=decorator,
    )
    star_module.Context = object

    class Star:
        def __init__(self, context):
            self.context = context

    star_module.Star = Star
    star_module.register = decorator
    sys.modules.update(
        {
            "astrbot": astrbot,
            "astrbot.api": api,
            "astrbot.api.event": event_module,
            "astrbot.api.star": star_module,
        }
    )
    path = ASTRBOT_ROOT / "astrbot_plugin_textech_intent" / "main.py"
    spec = importlib.util.spec_from_file_location("textech_intent_routing_test", path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class Event:
    def __init__(self, text: str, *, messages=None, raw_message=None):
        self.message_str = text
        self._messages = messages
        self.message_obj = types.SimpleNamespace(
            message_str=text,
            message=messages,
            raw_message=raw_message,
        )
        self.stopped = False
        self.call_llm = True

    def get_messages(self):
        return self._messages

    def stop_event(self):
        self.stopped = True

    def should_call_llm(self, value):
        self.call_llm = value


class TextechIntentRoutingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.module = _load_module()
        self.plugin = self.module.TextechIntentPlugin(object(), {})

    def _classify(self, text: str):
        return self.module.classify(
            text,
            webae_prefixes=list(self.module.DEFAULT_WEBAE_PREFIXES),
            astr_prefixes=list(self.module.DEFAULT_ASTRBOT_PREFIXES),
            keywords=list(self.module.DEFAULT_WEBAE_KEYWORDS),
            command_prefix="/",
        )

    def test_https_bilibili_url_is_not_misclassified_as_webae(self) -> None:
        url = "https://www.bilibili.com/video/BV187GV6HE65/?spm_id_from=333.1007.tianma.1-2-2.click"
        self.assertEqual(self._classify(url), ("astrbot", url, "default_astrbot"))

    def test_webae_keywords_inside_url_are_ignored(self) -> None:
        url = "https://example.com/tps?panel=adm"
        self.assertEqual(self._classify(url), ("astrbot", url, "default_astrbot"))

    def test_explicit_webae_prefix_still_owns_url(self) -> None:
        url = "https://example.com/video"
        self.assertEqual(
            self._classify("webae " + url),
            ("webae", url, "explicit_webae:webae"),
        )

        event = Event("webae " + url)
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")

    def test_webae_keyword_outside_url_still_owns_message(self) -> None:
        url = "https://example.com/video"
        text = "check tps status " + url
        self.assertEqual(self._classify(text), ("webae", text, "webae_keyword:tps"))

    def test_link_with_group_card_title_keyword_is_reserved_for_summary(self) -> None:
        text = "服务器状态 https://example.com/article"
        event = Event(text)
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertTrue(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_structured_group_card_beats_implicit_webae_command_title(self) -> None:
        url = "https://www.bilibili.com/video/BV187GV6HE65"
        event = Event(
            "TPS 在线报告",
            messages=[{"type": "json", "data": {"jumpUrl": url}}],
        )
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_serialized_navigation_card_is_reserved_for_summary(self) -> None:
        url = "https://www.bilibili.com/video/BV187GV6HE65"
        event = Event(
            "TPS 鍦ㄧ嚎鎶ュ憡",
            messages=[{"type": "json", "data": '{"meta":{"jumpUrl":"' + url + '"}}'}],
        )
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_media_only_json_card_does_not_override_webae_route(self) -> None:
        event = Event(
            "tps status",
            messages=[
                {
                    "type": "json",
                    "data": {
                        "preview": "https://qq.example/preview.jpg",
                        "cover": "https://qq.example/cover.jpg",
                        "attachment": {
                            "type": "image",
                            "url": "https://qq.example/image.jpg",
                        },
                    },
                }
            ],
        )
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")
        self.assertEqual(event.textech_route["reason"], "webae_command:tps")

    def test_qqofficial_serialized_media_card_does_not_override_webae_route(self) -> None:
        raw_message = types.SimpleNamespace(
            raw_data={
                "content": '{"preview":"https://qq.example/preview.jpg",'
                '"attachment":{"type":"image","url":"https://qq.example/image.jpg"}}',
                "attachments": [],
            },
            msg_elements=[],
        )
        event = Event("tps status", raw_message=raw_message)
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")

    def test_share_without_url_does_not_override_webae_route(self) -> None:
        event = Event(
            "tps status",
            messages=[{"type": "share", "data": {"title": "no navigation"}}],
        )
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")

    def test_forward_title_keyword_is_reserved_for_summary(self) -> None:
        event = Event(
            "服务器 在线",
            messages=[{"type": "forward", "data": {"id": "opaque-forward"}}],
        )
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_qqofficial_forward_marker_is_reserved_for_summary(self) -> None:
        raw_message = types.SimpleNamespace(
            raw_data={"message_type": 103, "msg_elements": []},
            msg_elements=[],
        )
        event = Event("服务器 在线", raw_message=raw_message)
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_message_chain_failure_still_checks_qqofficial_forward(self) -> None:
        raw_message = types.SimpleNamespace(
            raw_data={"message_type": 103, "msg_elements": []},
            msg_elements=[],
        )
        event = Event("tps online", raw_message=raw_message)

        def fail_get_messages():
            raise RuntimeError("adapter message chain unavailable")

        event.get_messages = fail_get_messages
        asyncio.run(self.plugin.on_message(event))
        self.assertFalse(event.stopped)
        self.assertTrue(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertEqual(event.textech_route["reason"], "link_summary_request")

    def test_explicit_prefixed_webae_command_with_url_remains_webae(self) -> None:
        event = Event("/tps https://example.com/status")
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")
        self.assertEqual(event.textech_route["reason"], "webae_command:tps")

    def test_non_link_webae_message_is_unchanged(self) -> None:
        event = Event("服务器 状态")
        asyncio.run(self.plugin.on_message(event))
        self.assertTrue(event.stopped)
        self.assertFalse(event.call_llm)
        self.assertEqual(event.textech_route["owner"], "webae")

    def test_explicit_tt_route_keeps_original_text_metadata(self) -> None:
        event = Event("tt 把背景改成星空")
        asyncio.run(self.plugin.on_message(event))
        self.assertEqual(event.message_str, "把背景改成星空")
        self.assertEqual(event.message_obj.message_str, "把背景改成星空")
        self.assertEqual(event.textech_route["owner"], "astrbot")
        self.assertTrue(event.textech_route["explicit"])
        self.assertEqual(event.textech_route["prefix"], "tt")
        self.assertEqual(event.textech_route["original_text"], "tt 把背景改成星空")
        self.assertEqual(event.textech_route["routed_text"], "把背景改成星空")

    def test_compact_tt_route_is_preserved_for_downstream_plugins(self) -> None:
        event = Event("tt生图 一只狐狸")
        asyncio.run(self.plugin.on_message(event))
        self.assertEqual(event.message_str, "生图 一只狐狸")
        self.assertEqual(event.textech_route["original_text"], "tt生图 一只狐狸")
        self.assertTrue(event.textech_route["explicit"])

    def test_ttl_is_not_an_explicit_tt_route(self) -> None:
        event = Event("ttl report")
        asyncio.run(self.plugin.on_message(event))
        self.assertEqual(event.message_str, "ttl report")
        self.assertFalse(event.textech_route["explicit"])


if __name__ == "__main__":
    unittest.main()
