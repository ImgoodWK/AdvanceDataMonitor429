# Change Log

## 2026-07-27 — Bridge 1.2 与 Persona 协议同步

- Console Bridge 1.2 仅为网页回答/草稿复用 AstrBot `fallback_chat_models`，真实投递继续零自动重发。
- 线上 Persona Lib `collect_instruction` 已刷新为含 `target_id`、`tags`、`attributes` 的当前协议模板。
- 回归只创建网页预览并使用错误 `SEND` 确认；没有向真实 QQ 会话发测试消息。
- 已知服务器仍未发现 WebAE/Minecraft 实例；AstrBot 侧让出已验证，但联合路由未标记完成。

## 2026-07-27 — Persona Console 2.2 网页消息中心

- 新增 Console Bridge 1.0.0 与共享文件队列，不增加 HTTP 端口或共享密钥
- 网页可按主 Bot 人格、Private Companion 风格和 Persona Lib 人设生成草稿；草稿必须人工审核
- 目标限定为已知且允许的会话；API 只返回派生 key 和脱敏 ID
- 管理员投递需锁定目标并输入 `SEND`；确认异常为 `uncertain`，不自动重发
- 增加同目标冷却、全局窗口限流、凭据拦截和脱敏写操作审计

## 2026-07-27 — tt / 身份人设 / 生图 / 管理台统一

- Persona Lib 升级为稳定身份、共享别名、标签与任意属性的唯一人设源
- tt 显式路由、仅显式联网；Private Companion 独占自动回复，避免双回
- 生图按共享别名跨用户解析人设，并保留既有提示词合法化链
- 管理台新增统一 Bot 行为、人设导入导出和自动备份
- 清除资料站明文凭据做法；SoulMap 降为禁用的兼容数据



## 2026-07-23 — 管理台升级为人设/记忆库后台

- author: cursor
- 人设库：默认全量展示 SoulMap，搜索仅筛选；按真实字段结构编辑
- Bot 用户：对齐 QQ OpenID / QQ 昵称 / 人设称呼 / Companion 昵称
- 记忆库：读取 companion_memory，支持列表筛选与增删改
- 权限：自定义权限组 + 用户级 grants/denies；我的账号可改密码

## 2026-07-23 — 上线 TeXTech 管理台与资料站

- author: bootstrap
- 新增 `:6186` 管理台（人设/记忆/用户权限/配置/资料站）
- 资料站路径：`/opt/textech-console/data/knowledge`
