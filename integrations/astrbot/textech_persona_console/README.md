# TeXTech Persona Console 2.4

## Image Gallery 2.4

- 图片库展示 Private Companion 生成/修改的成品、最终实际提示词、时间、后端、来源与生成人，支持关键词、生成/改图、后端、用户、提示词留存状态、日期和收藏筛选。
- 卡片只在接近可视区域时加载 512px WebP 缩略图，详情才加载原图；带文件版本指纹的缩略图和原图使用一年期浏览器私有缓存，文件变化会自动换 URL，避免重复消耗服务器图片带宽。
- 收藏按控制台账号隔离；`images.view` / `images.favorite` 默认授予预设只读和编辑角色，强制重扫 `images.manage` 默认仅管理员拥有。
- 新成品由 `ImageGalleryStoreMixin` 在公共成功出口旁路写入 `plugin_data/astrbot_plugin_private_companion/image_gallery.sqlite3`，不改变生图、额度或投递链。
- 首次扫描会从 `companions.json` 补录最近 12 条完整提示词，并为更早的图片按文件时间建立历史记录；没有原始提示词/操作类型的历史图片明确显示“未留存”/“历史未知”，不会猜测补写。

## Persona Studio 2.3

- The message center can generate a web-only persona answer without a QQ target.
- Operators select either the main Bot persona or a shared Persona Lib entry through a redacted persona key.
- Web preview jobs never carry a UMO and cannot be submitted to the `SEND` delivery flow; loading a preview answer into review still requires an allowed real target.

AstrBot 人设、稳定身份、私密记忆、Bot 行为、受控网页消息与运维管理台的源码权威。

生产入口为 `https://textech.top:8444/`，宿主端口只绑定 `127.0.0.1:6186`。从 TeXTech 总入口携带 `?portal_sso=1` 时，前端强制调用 `POST /api/auth/portal`；后端把入口 Cookie 单独交给内网 Meowa 验证端且拒绝重定向，成功后选择现有管理员并设置原生安全 Cookie。管理员密码不会被读取、复制或返回。前端统一为 Meowa / Meme Studio 的深青绿、青色、荧光黄主题。

## 数据边界

- 共享身份/人设：`plugin_data/astrbot_plugin_persona_lib/personas.json`
- Private Companion 用户与记忆：`plugin_data/astrbot_plugin_private_companion/`
- Console Bridge 共享任务队列：`plugin_data/astrbot_plugin_console_bridge/queue.json`；跨进程锁 + 原子替换
- 插件配置：`config/*.json`；API 默认脱敏，只有独立权限可查看敏感值
- 控制台账号、角色、API Token 哈希与脱敏写操作审计：`/data/console.db`
- 生图元数据与账号级收藏：`plugin_data/astrbot_plugin_private_companion/image_gallery.sqlite3`；成品仍在同目录 `generated_photos/`
- Console 缩略图缓存：`/data/image-thumbnails/`（宿主机 `/opt/textech-console/data/image-thumbnails/`）；可安全删除，访问时按原图版本自动重建
- 每次 JSON 写入前自动生成唯一快照到 AstrBot `data/backups/console_*`；管理台支持逐文件或整快照回滚，回滚前再次备份当前文件

## 部署

服务器权威目录为 `/opt/textech-console`。首次部署先复制 `.env.example` 为 `.env`，填写随机 `SESSION_SECRET` 与初始管理员密码；两者不会进入镜像或 Git。

`CONSOLE_BOOTSTRAP_PASSWORD` 的 12 位最小长度只在空数据库创建首个管理员时校验；已有 `console.db` 的后续重建不再因已废弃的 bootstrap 值阻断启动，现有账号密码不会被覆盖。


```bash
cd /opt/textech-console
docker compose up -d --build
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:6186/
```

`.env`、数据库和真实凭据不得复制回仓库。6186 当前直接暴露时应由安全组限制来源；生产推荐放在 TLS 反向代理之后。

## 核心页面

- Bot 行为：统一人格、`tt` 路由、自动回复强度、联网/识图、生图合法化
- 人设库：稳定 ID、共享别名、标签、任意属性、导入/导出
- Bot 用户/记忆库：Private Companion 用户、主动消息边界与记忆
- 图片库：Bot 生图/改图成品、完整提示词、个人收藏、详情与多条件筛选；可见区域懒加载缩略图，原图按需加载并长期缓存
- 消息中心：只对已知且允许的会话生成人格草稿；人工审核后由管理员锁定目标并输入 `SEND` 才排队投递
- 高级配置/运维/权限：原始配置、日志、重启和 RBAC；运维文档统一迁移到独立 `D:\gtnhcode\TeXTech` 中枢
- 审计与备份：查询所有已认证写操作的操作者/动作/结果；按独立权限查看快照或执行需快照名确认的回滚

## 网页消息安全边界

- `messages.view` 查看脱敏目标和任务，`messages.compose` 生成人格草稿，`messages.send` 才能确认投递；预设 editor 只有前两项，admin 才有发送权限。
- API 不返回原始 UMO 或完整 QQ/群 ID，不接受手工填写目标；群聊继续服从 Private Companion whitelist/blacklist。
- 草稿与发送分离；发送请求必须同时提交匹配的派生目标 key 与固定短语 `SEND`。正文疑似含密码、Key、Token 或 Bearer 凭据时拒绝。
- 投递只调用 Private Companion `_send_chain_confirmed`，沿用装饰钩子、QQ 官方平台适配与平台历史；确认中断会标记 `uncertain`，绝不自动重发。
- 默认同目标冷却 30 秒、全局 600 秒最多 5 条。部署回归只生成无害草稿并测试错误确认，不得用正确 `SEND` 向真实 QQ 发测试消息。
