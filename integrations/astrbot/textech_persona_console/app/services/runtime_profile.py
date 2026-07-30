from typing import Any

from .. import json_store
from ..config import CMD_CONFIG, PC_CONFIG

INTENT_CONFIG = "config/astrbot_plugin_textech_intent_config.json"
SEARCH_CONFIG = "config/astrbot_plugin_web_search_config.json"
PERSONA_CONFIG = "config/astrbot_plugin_persona_lib_config.json"

_COMPANION_TEXT_FIELDS = {
    "bot_name",
    "plugin_specific_persona_id",
    "reply_style_prompt",
    "persona_conversation_voice_prompt",
    "persona_creative_voice_prompt",
    "photo_prompt_llm_rewrite_system_prompt",
}
_COMPANION_BOOL_FIELDS = {
    "enable_group_conversation_followup",
    "enable_group_interjection",
    "enable_context_image_captioning",
    "enable_photo_prompt_llm_rewrite",
}
_COMPANION_INT_FIELDS = {
    "group_conversation_followup_seconds",
    "group_conversation_followup_max_turns",
    "group_interject_min_interval_minutes",
    "group_interject_max_daily",
}


def _read_all() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any]]:
    return (
        json_store.read_json(CMD_CONFIG, {}),
        json_store.read_json(PC_CONFIG, {}),
        json_store.read_json(INTENT_CONFIG, {}),
        json_store.read_json(SEARCH_CONFIG, {}),
        json_store.read_json(PERSONA_CONFIG, {}),
    )


def get_runtime_profile() -> dict[str, Any]:
    cmd, companion, intent, search, persona = _read_all()
    provider_settings = cmd.get("provider_settings") if isinstance(cmd.get("provider_settings"), dict) else {}
    ltm = cmd.get("provider_ltm_settings") if isinstance(cmd.get("provider_ltm_settings"), dict) else {}
    active = ltm.get("active_reply") if isinstance(ltm.get("active_reply"), dict) else {}
    return {
        "routing": {
            "wake_prefix": cmd.get("wake_prefix") or [],
            "astrbot_prefixes": intent.get("astrbot_explicit_prefixes") or ["tt"],
            "webae_prefixes": intent.get("webae_explicit_prefixes") or [],
            "compact_tt": bool(intent.get("allow_compact_astrbot_prefix", True)),
        },
        "search": {
            "enabled": bool(search.get("enabled", True)),
            "only_explicit": bool(search.get("only_explicit", True)),
            "require_astrbot_prefix": bool(search.get("require_astrbot_prefix", True)),
            "mode": str(search.get("mode") or "auto"),
            "max_results": int(search.get("max_results") or 5),
        },
        "persona": {
            "default_personality": str(provider_settings.get("default_personality") or ""),
            "bot_name": str(companion.get("bot_name") or ""),
            "persona_id": str(companion.get("plugin_specific_persona_id") or ""),
            "reply_style_prompt": str(companion.get("reply_style_prompt") or ""),
            "conversation_prompt": str(companion.get("persona_conversation_voice_prompt") or ""),
            "creative_prompt": str(companion.get("persona_creative_voice_prompt") or ""),
            "allow_shared_edits": bool(persona.get("allow_shared_edits", True)),
            "allow_self_persona": bool(persona.get("allow_self_persona", True)),
        },
        "automatic_reply": {
            "astrbot_active_enabled": bool(active.get("enable", False)),
            "astrbot_probability": float(active.get("possibility_reply") or 0),
            "group_followup_enabled": bool(companion.get("enable_group_conversation_followup", True)),
            "group_followup_seconds": int(companion.get("group_conversation_followup_seconds") or 500),
            "group_followup_max_turns": int(companion.get("group_conversation_followup_max_turns") or 1),
            "group_interjection_enabled": bool(companion.get("enable_group_interjection", False)),
            "group_interject_min_interval_minutes": int(companion.get("group_interject_min_interval_minutes") or 180),
            "group_interject_max_daily": int(companion.get("group_interject_max_daily") or 2),
        },
        "image": {
            "vision_enabled": bool(companion.get("enable_context_image_captioning", True)),
            "prompt_rewrite_enabled": bool(companion.get("enable_photo_prompt_llm_rewrite", True)),
            "prompt_rewrite_system_prompt": str(companion.get("photo_prompt_llm_rewrite_system_prompt") or ""),
            "persona_store_path": str(companion.get("soulmap_profiles_path") or ""),
        },
        "requires_restart": True,
    }


def _set_auto_reply_mode(cmd: dict[str, Any], companion: dict[str, Any], mode: str) -> None:
    if mode not in {"off", "occasional", "frequent"}:
        raise ValueError("auto_reply_mode must be off, occasional or frequent")
    ltm = cmd.setdefault("provider_ltm_settings", {})
    active = ltm.setdefault("active_reply", {})
    # Private Companion is the single automatic-reply owner; disable AstrBot's
    # second random reply loop to avoid duplicate replies.
    active["enable"] = False
    if mode == "off":
        companion.update({"enable_group_interjection": False, "enable_group_conversation_followup": False})
    elif mode == "occasional":
        companion.update(
            {
                "enable_group_interjection": True,
                "group_interject_min_interval_minutes": 240,
                "group_interject_max_daily": 2,
                "enable_group_conversation_followup": True,
                "group_conversation_followup_max_turns": 1,
            }
        )
    else:
        companion.update(
            {
                "enable_group_interjection": True,
                "group_interject_min_interval_minutes": 60,
                "group_interject_max_daily": 6,
                "enable_group_conversation_followup": True,
                "group_conversation_followup_max_turns": 2,
            }
        )


def patch_runtime_profile(fields: dict[str, Any]) -> dict[str, Any]:
    cmd, companion, intent, search, persona = _read_all()
    routing = fields.get("routing") if isinstance(fields.get("routing"), dict) else {}
    if routing:
        prefixes = routing.get("astrbot_prefixes")
        if prefixes is not None:
            if not isinstance(prefixes, list) or not any(str(item).strip().lower() == "tt" for item in prefixes):
                raise ValueError("AstrBot prefixes must contain tt")
            intent["astrbot_explicit_prefixes"] = [str(item).strip() for item in prefixes if str(item).strip()]
        if "compact_tt" in routing:
            intent["allow_compact_astrbot_prefix"] = bool(routing["compact_tt"])
        cmd["wake_prefix"] = ["tt", "TT", "/"]

    search_patch = fields.get("search") if isinstance(fields.get("search"), dict) else {}
    for key in ("enabled", "only_explicit", "require_astrbot_prefix"):
        if key in search_patch:
            search[key] = bool(search_patch[key])
    if "mode" in search_patch:
        search["mode"] = str(search_patch["mode"] or "auto")[:40]
    if "max_results" in search_patch:
        search["max_results"] = max(1, min(int(search_patch["max_results"]), 10))

    persona_patch = fields.get("persona") if isinstance(fields.get("persona"), dict) else {}
    provider_settings = cmd.setdefault("provider_settings", {})
    if "persona_id" in persona_patch:
        value = str(persona_patch["persona_id"] or "")[:120]
        companion["plugin_specific_persona_id"] = value
        provider_settings["default_personality"] = value
    if "default_personality" in persona_patch:
        provider_settings["default_personality"] = str(persona_patch["default_personality"] or "")[:120]
    if "bot_name" in persona_patch:
        companion["bot_name"] = str(persona_patch["bot_name"] or "")[:120]
    mapping = {
        "reply_style_prompt": "reply_style_prompt",
        "conversation_prompt": "persona_conversation_voice_prompt",
        "creative_prompt": "persona_creative_voice_prompt",
    }
    for api_key, config_key in mapping.items():
        if api_key in persona_patch:
            companion[config_key] = str(persona_patch[api_key] or "")[:12000]
    for key in ("allow_shared_edits", "allow_self_persona"):
        if key in persona_patch:
            persona[key] = bool(persona_patch[key])

    automatic = fields.get("automatic_reply") if isinstance(fields.get("automatic_reply"), dict) else {}
    if "mode" in automatic:
        _set_auto_reply_mode(cmd, companion, str(automatic["mode"]))
    for key in _COMPANION_BOOL_FIELDS:
        if key in automatic:
            companion[key] = bool(automatic[key])
    for key in _COMPANION_INT_FIELDS:
        if key in automatic:
            companion[key] = max(0, min(int(automatic[key]), 86400))

    image = fields.get("image") if isinstance(fields.get("image"), dict) else {}
    if "vision_enabled" in image:
        companion["enable_context_image_captioning"] = bool(image["vision_enabled"])
    if "prompt_rewrite_enabled" in image:
        companion["enable_photo_prompt_llm_rewrite"] = bool(image["prompt_rewrite_enabled"])
    if "prompt_rewrite_system_prompt" in image:
        companion["photo_prompt_llm_rewrite_system_prompt"] = str(image["prompt_rewrite_system_prompt"] or "")[:12000]
    companion["soulmap_profiles_path"] = "/AstrBot/data/plugin_data/astrbot_plugin_persona_lib/personas.json"

    json_store.write_json(CMD_CONFIG, cmd)
    json_store.write_json(PC_CONFIG, companion)
    json_store.write_json(INTENT_CONFIG, intent)
    json_store.write_json(SEARCH_CONFIG, search)
    json_store.write_json(PERSONA_CONFIG, persona)
    return get_runtime_profile()
