from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    astrbot_data: Path = Path("/astrbot-data")
    console_db: Path = Path("/data/console.db")
    image_thumbnail_dir: Path = Path("/data/image-thumbnails")
    session_secret: str = ""
    console_bootstrap_password: str = ""
    astrbot_compose_dir: Path = Path("/opt/astrbot")
    astrbot_container: str = "astrbot"
    cookie_name: str = "textech_session"
    session_max_age: int = 60 * 60 * 24 * 7
    cookie_secure: bool = False
    portal_auth_url: str = "http://meowa-test-controller:8080/auth/check"
    portal_auth_timeout_seconds: float = 5.0


settings = Settings()

SOULMAP_PROFILES = "plugin_data/astrbot_plugin_soulmap/user_profiles.json"
SOULMAP_CONFIG = "config/astrbot_plugin_soulmap_config.json"
COMPANIONS = "plugin_data/astrbot_plugin_private_companion/companions.json"
CONSOLE_BRIDGE_QUEUE = "plugin_data/astrbot_plugin_console_bridge/queue.json"
CONSOLE_BRIDGE_CONFIG = "config/astrbot_plugin_console_bridge_config.json"
PC_CONFIG = "config/astrbot_plugin_private_companion_config.json"
CMD_CONFIG = "cmd_config.json"
PERSONA_LIB = "plugin_data/astrbot_plugin_persona_lib/personas.json"
PERSONA_LIB_ALT = "plugin_data/persona_lib/personas.json"
GENERATED_PHOTOS = "plugin_data/astrbot_plugin_private_companion/generated_photos"
PHOTO_GALLERY_DB = "plugin_data/astrbot_plugin_private_companion/image_gallery.sqlite3"

MEMORY_PLUGIN_DIRS = [
    "plugin_data/astrbot_plugin_memory_companion",
    "plugin_data/astrbot_plugin_remember_you",
]

SOULMAP_FIELDS = [
    "对用户的称呼",
    "性别",
    "年龄",
    "所在地",
    "生日",
    "爱吃",
    "忌口",
    "爱好",
    "职业",
    "重要节日",
    "恐惧/弱点",
    "作息规律",
    "技能水平",
    "健康状况",
    "宠物",
    "备注",
]

COMPANION_EDITABLE = {
    "enabled",
    "manual_enabled",
    "relationship_role",
    "nickname",
    "style",
    "umo",
    "proactive_daily_limit",
    "proactive_idle_minutes",
    "proactive_min_interval_minutes",
    "photo_daily_limit",
    "screen_peek_daily_limit",
    "poke_daily_limit",
    "proactive_boundary_note",
    "suspended_proactive",
    "simulation_mode",
}

# Fine-grained console permissions
ALL_PERMISSIONS: dict[str, str] = {
    "personas.view": "查看人设库",
    "personas.edit": "编辑人设库",
    "bot_users.view": "查看 Bot 用户",
    "bot_users.edit": "编辑 Bot 用户 / Companion 权限",
    "memories.view": "查看记忆库",
    "memories.edit": "添加/修改/删除记忆",
    "config.view": "查看 Bot 配置",
    "config.edit": "编辑 Bot 配置",
    "config.secrets": "查看明文密钥",
    "ops.view": "查看运维信息",
    "ops.restart": "重启 AstrBot",
    "audit.view": "查看写操作审计日志",
    "backups.view": "查看配置备份",
    "backups.restore": "回滚配置备份",
    "console.manage": "管理控制台账号与权限组",
    "account.password": "修改自己的密码",
    "messages.view": "查看网页消息草稿与投递状态",
    "messages.compose": "生成人格消息草稿",
    "messages.send": "确认并投递网页消息",
    "images.view": "查看 Bot 生成图片与提示词",
    "images.favorite": "维护自己的图片收藏",
    "images.manage": "重扫图片图库索引",
}

VIEWER_PERMS = [
    "personas.view",
    "bot_users.view",
    "memories.view",
    "config.view",
    "ops.view",
    "audit.view",
    "backups.view",
    "account.password",
    "images.view",
    "images.favorite",
]

EDITOR_PERMS = VIEWER_PERMS + [
    "personas.edit",
    "bot_users.edit",
    "memories.edit",
    "config.edit",
    "messages.view",
    "messages.compose",
]

ADMIN_PERMS = list(ALL_PERMISSIONS.keys())

PRESET_ROLES: dict[str, dict] = {
    "admin": {"label": "管理员", "permissions": ADMIN_PERMS, "is_system": True},
    "editor": {"label": "编辑", "permissions": EDITOR_PERMS, "is_system": True},
    "viewer": {"label": "只读", "permissions": VIEWER_PERMS, "is_system": True},
}

SENSITIVE_KEY_RE = (
    "key",
    "secret",
    "token",
    "password",
    "api_key",
    "apikey",
    "access_key",
)
