# Private Companion Persona Overlay

只维护服务器第三方 Private Companion 的小型 TeXTech 覆盖层，不复制整套插件。

这份适配器在现有“生图前合法化提示词”链路上补充：

- 按图片请求中出现的共享别名扫描整个 Persona Lib，而不是只读生图发起者自己的画像；
- 把 `appearance`、`personality`、`tags` 与任意 `attributes` 一起写入最终生图上下文；
- 私密 persona 不参与跨用户别名解析；没有命中共享别名时保持原有发起者画像回退。
- `textech_photo_route.py` 读取 `textech_intent` 保留的显式 `tt` 路由元数据；即使前缀已从 `message_str` 剥离，也能稳定识别 `tt 生图` / `tt生图`，且不会误判 `ttl`。
- `apply_command_handlers_overlay.py` 对服务器当前 `command_handlers.py` 做带校验、可重复执行的小型补丁：带图的显式 `tt` 生图意图会强制进入 `edit`，当前消息或引用消息中的参考图会和用户提示词一起交给后端；未使用 `tt` 的参考图生图会被拦截并提示正确写法。
- `image_gallery_store.py` 通过 Mixin 旁路记录所有成功生图/改图的最终实际提示词、时间、后端、来源和生成人到插件数据目录 `image_gallery.sqlite3`；不修改生图、额度或发送逻辑。
- `apply_image_gallery_overlay.py` 只在服务器 `main.py` 注入上述 Mixin，带锚点校验、自动备份且可重复执行；Persona Console 2.4 使用该索引提供图片库、个人收藏和多条件筛选。

部署目标：

- `soulmap_photo_adapter.py`、`textech_photo_route.py`、`image_gallery_store.py` → `/opt/astrbot/data/plugins/astrbot_plugin_private_companion/`
- `apply_command_handlers_overlay.py` 先不带 `--apply` 检查，再对服务器当前 `command_handlers.py` 执行；脚本兼容宿主机旧版 Python 3，并会创建带时间戳的同目录备份。也可把脚本临时复制进插件目录后用 AstrBot 容器内的 Python 执行。
- `apply_image_gallery_overlay.py <插件目录>` 对 `main.py` 注入图库 Mixin；脚本兼容宿主机旧版 Python 3、自动备份且重复执行安全。完成后在容器内 `py_compile` 并重启 AstrBot。

升级第三方插件后需重新对比补丁上下文；部署前备份原文件。
