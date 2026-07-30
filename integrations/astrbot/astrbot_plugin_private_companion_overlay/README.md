# Private Companion Persona Overlay

只覆盖服务器第三方插件的 `soulmap_photo_adapter.py`，不复制整套 Private Companion。

这份适配器在现有“生图前合法化提示词”链路上补充：

- 按图片请求中出现的共享别名扫描整个 Persona Lib，而不是只读生图发起者自己的画像；
- 把 `appearance`、`personality`、`tags` 与任意 `attributes` 一起写入最终生图上下文；
- 私密 persona 不参与跨用户别名解析；没有命中共享别名时保持原有发起者画像回退。

部署目标：`/opt/astrbot/data/plugins/astrbot_plugin_private_companion/soulmap_photo_adapter.py`。升级第三方插件后需重新对比并覆盖；部署前备份原文件。
