# TeXTech 公开溯源与取证说明

> 规范源：本仓库 `docs/` · 最后同步：2026-08

TeXTech 使用可见、可核验且不干扰正常阅读与运行的方式记录项目先后关系。来源证明 ID 为：

`TT-GTNH-PROVENANCE-2025-04-29-E04BDE7`

它与最早可达提交 `e04bde7`（2025-04-29）、签名提交、签名 annotated Tag、Release commit SHA、`SHA256SUMS` 和 GitHub Artifact Attestations 一起构成公开验证链。

## 公开监测如何工作

`.github/workflows/provenance-monitor.yml` 每周一 01:00 UTC（北京时间 09:00）运行，也可手动触发。查询、官方仓库和 allowlist 全部公开保存在 `.github/provenance-monitor.json`。脚本只请求 GitHub 公开代码索引，并再次丢弃任何标为 private 或非 public 的结果。

查询对象包括来源证明 ID、中英文 Slogan 和少量独特符号组合。发现未在 allowlist 中的公开结果时，工作流生成 Markdown 与 JSON artifact，然后以失败状态通知维护者。它不会自动创建 Issue、联系仓库作者、公开指控、读取私人仓库或收集邮箱、Token 等身份数据。

## 固有局限

- GitHub 公开索引可能延迟、缺失或暂时不可用。
- 私有仓库和未进入公开索引的内容不可见。
- 改名、翻译、人工重写或 AI 改写通常无法通过字面查询匹配。
- 搜索命中只是需要人工核对的线索，不是抄袭或侵权证明。
- MIT 允许在保留许可与版权声明的前提下复制、修改和分发；不能把许可证允许的正常派生开发直接表述为违法。

## 人工取证清单

发现可疑公开结果后，先保存事实，不先下结论：

1. 记录仓库、文件和页面的完整公开 URL 与访问日期/时区。
2. 记录可疑文件所在的 commit SHA、父提交和默认分支。
3. 保存相关 Tag、Release、提交和页面显示的时间戳。
4. 保存带日期的页面快照、原始文件和对应下载哈希。
5. 针对具体代码、文档或结构制作逐项、可复现的 side-by-side diff。
6. 保存双方当时显示的许可证、版权声明和 NOTICE。
7. 对照 TeXTech 的最早提交、签名 Tag、Release commit、校验和与 Attestation。
8. 区分相同依赖、通用方案、兼容性必需结构与真正独特表达；只陈述可核验事实。
9. 由维护者人工决定是加入 allowlist、继续观察，还是寻求平台/法律渠道建议。

本流程不替代法律意见，也不授权秘密跟踪、攻击、外呼或收集个人信息。
