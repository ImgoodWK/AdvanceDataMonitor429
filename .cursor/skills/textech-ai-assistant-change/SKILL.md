---
name: textech-ai-assistant-change
description: >-
  Changes TeXTech AI assistant features with intent/lexicon/features JSON,
  bilingual lang, GuiAIChat capability text, and ai-assistant docs sync. Use
  when the user @-mentions this skill or edits assistant/, voice/, or AI chat.
disable-model-invocation: true
---

# TeXTech AI Assistant Change

按需 `@textech-ai-assistant-change`。权威规则：`ai-assistant.mdc` + `ai-assistant-docs-sync.mdc`。

## 七步清单

```
AI Assistant Change:
- [ ] 1. 意图 / 服务逻辑（AssistantController / Intent* / ServerServices）
- [ ] 2. assistant-features.json（功能开关/菜单）
- [ ] 3. assistant-lexicon.json（词库；热重载 /admassistant）
- [ ] 4. en_US.lang + zh_CN.lang（adm.ai.* 等）
- [ ] 5. GuiAIChat.assistantCapabilityInstruction()（若能力对外可见）
- [ ] 6. docs/zh|en/ai-assistant/ + 手册 ai_assistant（若玩家可见）
- [ ] 7. project-structure*.mdc（新增/重命名 Java 时）
```

## 速查

| 改什么 | 看哪里 |
|--------|--------|
| 意图解析 | `AssistantAiIntentService` / `AssistantIntentService` |
| AE 查询/下单 | `AssistantServerServices` / `AssistantCraftJobManager` |
| 词库 | `assets/textech/config/assistant-lexicon.json` |
| 功能菜单 | `assets/textech/config/assistant-features.json` |
| 语音 | `voice/` + `/admai` 客户端配置 |
| 文档 | `docs/zh/ai-assistant/开发指南.md` |

## 禁止

- 只改 Java 不改 features/lexicon/lang/docs（`ai-assistant-docs-sync.mdc` 必同步）
- 直接引用 AE `legacy/` / `native_/`（经 `compat/ae/AeCompat`）

## 完成后

跑 `@textech-doc-sync-pr`；玩家可见变更同步手册 `ai_assistant` 章。
