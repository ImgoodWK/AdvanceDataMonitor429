# TeXTech 2.0

> Mod ID: `textech` · Minecraft 1.7.10 · GregTech: New Horizons 2.9.0-beta-2+

TeXTech 是面向 GTNH 的社区模组，提供 AE2 网络监控与操作、WebAE 浏览器控制台、数据编织与高级存储、计划器、AI/语音助手、挂索移动、游戏内显示器等功能。

2.0 将 WebAE 网页资源从主模组 JAR 中拆出。不使用 WebAE 的客户端和服务器只需下载主 JAR，无需额外下载网页资源。

## 下载与安装

请从 [GitHub Releases](https://github.com/ImgoodWK/AdvanceDataMonitor429/releases) 下载同一版本的附件：

| 附件 | 是否必需 | 安装方式 |
|------|----------|----------|
| `textech-*.jar` | **必需** | 放入客户端和服务端的 `mods/`。主 JAR 不包含 WebAE 网页资源，也不包含约 65 MB 的 Vosk 离线语音模型。 |
| `textech-*-webae.zip` | 可选 | 仅使用 WebAE 时下载。解压到服务端实例根目录，最终确认存在 `TeXTech/WebAE/ui/index.html`。 |
| `textech-*-voice.jar` | 可选 | 需要内置中文离线语音识别时放入客户端 `mods/`，与主 JAR 版本保持一致。 |

WebAE 默认关闭。安装资源包后，在 `config/textech/textech.cfg` 的 `[webConsole]` 中设置 `enabled=true`，重启服务端，再在游戏内执行 `/textech web issue` 获取登录 Token。没有安装 WebAE ZIP 时，相关 HTTP API 仍可启动，但网页入口会显示资源未安装提示。

目标运行环境是 GTNH `2.9.0-beta-2` 及以上；TeXTech 2.0 不再支持 GTNH 2.8.x。完整说明见[版本兼容文档](docs/zh/developer/GTNH版本兼容说明.md)。

## WebAE 实机页面

截图由本仓库的真实 React 前端配合本地演示 API 数据生成，不包含服务器 Token、密钥或玩家隐私数据。

### 自定义仪表盘

![WebAE 仪表盘](docs/assets/webae/dashboard.png)

### AE 存储浏览

![WebAE 存储页面](docs/assets/webae/storage.png)

### 样板工作台

![WebAE 样板页面](docs/assets/webae/patterns.png)

### 网络拓扑

![WebAE 网络拓扑](docs/assets/webae/topology.png)

### 服务诊断

![WebAE 诊断页面](docs/assets/webae/diagnostics.png)

WebAE 还包含配方搜索、合成下单、GT 机器、电力、流体与源质、任务书、链接扫描、玩家与聊天、计划器、告警、Spark 性能分析、管理控制台、AI 助手和 QQ 机器人等页面。详见 [WebAE 用户手册](docs/zh/webae/用户手册.md)。

## Card Battle

卡牌游戏的前端、Node 服务、规则与资产已经迁移到独立仓库：[TeXTech: Overclocked Arcana](https://github.com/ImgoodWK/TeXTech-Overclocked-Arcana)。ADM 仓库只保留 Minecraft 桥接；奖励物品发放在白名单与世界侧幂等账本完成前保持禁用。

## 从源码构建

```bash
./gradlew build
```

构建输出位于 `build/libs/`，包含主 JAR、可选 voice JAR 和独立 WebAE ZIP。前端源码位于 `webae-frontend/`；修改前端后先在该目录执行 `npm run build`，再执行 Gradle 构建。

## 文档

| 入口 | 中文 | English |
|------|------|---------|
| 玩家手册 | [docs/zh/player/用户手册.md](docs/zh/player/用户手册.md) | [docs/en/player/player-guide.md](docs/en/player/player-guide.md) |
| WebAE | [用户手册](docs/zh/webae/用户手册.md) · [开发者手册](docs/zh/webae/开发者手册.md) | [User guide](docs/en/webae/user-guide.md) · [Developer guide](docs/en/webae/developer-guide.md) |
| 文档总索引 | [docs/zh/README.md](docs/zh/README.md) | [docs/en/README.md](docs/en/README.md) |

## License

See the repository license file. GTNH mod development conventions apply.
