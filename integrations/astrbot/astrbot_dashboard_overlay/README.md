# AstrBot Dashboard TeXTech Overlay

这个小型覆盖层不复制 AstrBot Dashboard 源码，只为线上运行容器的 `/AstrBot/astrbot/dashboard/dist/index.html` 注入两个静态资源：

- `portal-sso.js` 从 URL fragment 读取入口签发的短期 Dashboard JWT，写入 AstrBot 原生 `localStorage.token` / `localStorage.user` 和 Dashboard Cookie，然后清理 fragment；Token 不进入 Caddy/AstrBot 访问日志。
- `textech-theme.css` 把 Dashboard 的 Vuetify 表面统一为 Meowa / Meme Studio 的深青绿、荧光黄风格。

宿主 `/opt/astrbot/data` 只挂载到容器 `/AstrBot/data`，主 Dashboard dist 不持久化在该目录。生产部署必须走 TeXTech 中枢登记的 `inspect`、`backup`、`sync` 和 `deploy`：先列出挂载/候选 index，用 `docker cp` 保存原始 index，再在随机宿主暂存目录 dry-run/应用，最后把临时文件复制进容器并原子换名。

脚本本身仍可在隔离 dist 目录使用 `python apply_overlay.py <dist>` 和 `--apply` 做测试；修改前会生成同目录时间戳备份，重复执行不会重复注入。生产容器升级或重建后覆盖层会消失，必须重新运行受控 deploy，并验证登录、API 请求和 WebSocket。
