from __future__ import annotations

import importlib.util
import json
import sqlite3
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from PIL import Image


ASTRBOT_ROOT = Path(__file__).resolve().parents[1]
CONSOLE_ROOT = ASTRBOT_ROOT / "textech_persona_console"


def _load_store_module():
    path = ASTRBOT_ROOT / "astrbot_plugin_private_companion_overlay" / "image_gallery_store.py"
    spec = importlib.util.spec_from_file_location("image_gallery_store_test", path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class ImageGalleryConsoleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        sys.path.insert(0, str(CONSOLE_ROOT))
        from app.services import image_gallery

        cls.gallery = image_gallery

    @classmethod
    def tearDownClass(cls) -> None:
        if sys.path and sys.path[0] == str(CONSOLE_ROOT):
            sys.path.pop(0)

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.plugin_data = self.root / "plugin_data" / "astrbot_plugin_private_companion"
        self.images = self.plugin_data / "generated_photos"
        self.images.mkdir(parents=True)
        self.gallery._last_sync.clear()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _write_companions(self, recent: list[dict] | None = None) -> None:
        payload = {
            "users": {
                "USER0123456789": {
                    "umo": "textech-qq:FriendMessage:USER0123456789",
                    "last_display_name": "测试用户",
                }
            },
            "recent_photo_generations": recent or [],
        }
        (self.plugin_data / "companions.json").write_text(
            json.dumps(payload, ensure_ascii=False), encoding="utf-8"
        )

    def test_recent_prompt_backfill_filters_and_account_favorites(self) -> None:
        image = self.images / "natural_photo_USER0123456789_20260730_120000_aabbccdd.png"
        image.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 64)
        self._write_companions(
            [
                {
                    "ok": True,
                    "path": str(image),
                    "ts": 1785384000,
                    "session": "natural_photo_USER0123456789",
                    "kind": "selfie",
                    "backend": "Image2",
                    "prompt": "final prompt with a starry sky",
                    "prompt_format": "natural_language",
                    "reference": True,
                    "trace": "trace-test",
                }
            ]
        )

        summary = self.gallery.ensure_index(force=True, root=self.root)
        self.assertEqual(summary["total"], 1)
        result = self.gallery.list_images(
            11,
            q="starry sky",
            operation="edit",
            backend="Image2",
            has_prompt=True,
            root=self.root,
        )
        self.assertEqual(result["total"], 1)
        item = result["items"][0]
        self.assertEqual(item["producer_name"], "测试用户")
        self.assertTrue(item["has_prompt"])
        self.assertFalse(item["legacy"])
        self.assertEqual(item["trigger"], "natural_language")
        self.assertNotIn("path", item)
        self.assertNotIn("session", item)

        self.gallery.set_favorite(item["id"], 11, True, root=self.root)
        self.assertEqual(
            self.gallery.list_images(11, favorite=True, root=self.root)["total"], 1
        )
        self.assertEqual(
            self.gallery.list_images(12, favorite=True, root=self.root)["total"], 0
        )
        facets = self.gallery.facets(11, root=self.root)
        self.assertEqual(facets["total"], 1)
        self.assertEqual(facets["favorites"], 1)
        self.assertEqual(facets["operations"][0]["value"], "edit")
        self.assertIsNotNone(self.gallery.image_file(item["id"], root=self.root))

    def test_older_file_is_marked_as_historical_without_fake_prompt(self) -> None:
        image = self.images / "natural_photo_UNKNOWN_20260720_120000_aabbccdd.webp"
        image.write_bytes(b"RIFF" + b"x" * 40)
        self._write_companions()
        self.gallery.ensure_index(force=True, root=self.root)
        result = self.gallery.list_images(7, has_prompt=False, root=self.root)
        self.assertEqual(result["total"], 1)
        self.assertTrue(result["items"][0]["legacy"])
        self.assertEqual(result["items"][0]["prompt"], "")
        self.assertEqual(result["items"][0]["operation"], "unknown")

    def test_thumbnail_is_bounded_persistent_and_content_versioned(self) -> None:
        image = self.images / "generated_20260730_130000_cache.png"
        Image.new("RGB", (1200, 800), (30, 90, 160)).save(image)
        self._write_companions()
        self.gallery.ensure_index(force=True, root=self.root)

        item = self.gallery.list_images(7, root=self.root)["items"][0]
        self.assertTrue(item["cache_version"])
        self.assertTrue(item["thumbnail_version"].startswith("webp-v1-"))
        thumbnail_root = self.root / "thumbnails"
        info = self.gallery.thumbnail_file(
            item["id"], root=self.root, thumbnail_root=thumbnail_root
        )
        self.assertIsNotNone(info)
        thumbnail, thumbnail_version = info
        self.assertEqual(thumbnail_version, item["thumbnail_version"])
        self.assertEqual(thumbnail.suffix, ".webp")
        with Image.open(thumbnail) as rendered:
            self.assertLessEqual(max(rendered.size), 512)
        self.assertEqual(
            self.gallery.thumbnail_file(
                item["id"], root=self.root, thumbnail_root=thumbnail_root
            )[0],
            thumbnail,
        )

        Image.new("RGB", (640, 640), (160, 40, 80)).save(image)
        changed = self.gallery.list_images(7, root=self.root)["items"][0]
        self.assertNotEqual(changed["cache_version"], item["cache_version"])
        new_thumbnail, new_version = self.gallery.thumbnail_file(
            item["id"], root=self.root, thumbnail_root=thumbnail_root
        )
        self.assertEqual(new_version, changed["thumbnail_version"])
        self.assertNotEqual(new_thumbnail, thumbnail)
        self.assertFalse(thumbnail.exists())


class ImageGalleryPluginMixinTests(unittest.TestCase):
    def test_mixin_records_after_parent_without_changing_flow(self) -> None:
        module = _load_store_module()

        class Parent:
            def _record_recent_photo_generation(self, **_kwargs):
                self.parent_recorded = True

            def _annotate_recent_photo_generation(self, **_kwargs):
                self.parent_annotated = True

        class Plugin(module.ImageGalleryStoreMixin, Parent):
            pass

        with tempfile.TemporaryDirectory() as temp:
            plugin = Plugin()
            plugin.data_dir = temp
            plugin.data = {
                "users": {
                    "U123456789012": {
                        "last_display_name": "图像用户",
                        "umo": "textech-qq:FriendMessage:U123456789012",
                    }
                }
            }
            plugin._photo_generation_prompt_format_mode = lambda: "traditional"
            image_dir = Path(temp) / "generated_photos"
            image_dir.mkdir()
            image = image_dir / "natural_photo_U123456789012_20260730_120000_abcd.png"
            image.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 32)

            plugin._record_recent_photo_generation(
                trace_id="trace-1",
                session_key="natural_photo_U123456789012",
                workflow_kind="selfie",
                backend="Image2",
                ok=True,
                prompt_text="Positive prompt: stars",
                image_path=str(image),
                reference_image_path="reference.png",
                presets=["头像特写"],
            )
            self.assertTrue(plugin.parent_recorded)
            with closing(sqlite3.connect(str(Path(temp) / "image_gallery.sqlite3"))) as conn:
                row = conn.execute(
                    "SELECT prompt, operation, producer_name, metadata_quality FROM images"
                ).fetchone()
            self.assertEqual(row, ("Positive prompt: stars", "edit", "图像用户", "complete"))

            plugin._annotate_recent_photo_generation(
                image_path=str(image),
                session_key="natural_photo_U123456789012",
                trigger="llm_tool",
                intent_kind="edit",
                sent=True,
                caption="完成",
            )
            self.assertTrue(plugin.parent_annotated)
            with closing(sqlite3.connect(str(Path(temp) / "image_gallery.sqlite3"))) as conn:
                row = conn.execute(
                    "SELECT trigger, intent_kind, sent, caption FROM images"
                ).fetchone()
            self.assertEqual(row, ("llm_tool", "edit", 1, "完成"))


class ConsoleBootstrapValidationTests(unittest.TestCase):
    def test_short_bootstrap_only_blocks_an_empty_database(self) -> None:
        if str(CONSOLE_ROOT) not in sys.path:
            sys.path.insert(0, str(CONSOLE_ROOT))
        from app import db as dbmod
        from app.config import settings

        old_db = settings.console_db
        old_password = settings.console_bootstrap_password
        try:
            with tempfile.TemporaryDirectory() as temp:
                settings.console_db = Path(temp) / "console.db"
                settings.console_bootstrap_password = "short"
                with self.assertRaises(RuntimeError):
                    dbmod.init_db()

                settings.console_bootstrap_password = "long-enough-bootstrap"
                dbmod.init_db()
                settings.console_bootstrap_password = "short"
                dbmod.init_db()
                self.assertEqual(len(dbmod.list_users()), 1)
        finally:
            settings.console_db = old_db
            settings.console_bootstrap_password = old_password


if __name__ == "__main__":
    unittest.main()
