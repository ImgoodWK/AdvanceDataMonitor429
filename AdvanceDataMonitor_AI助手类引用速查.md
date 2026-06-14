# AI 助手类引用速查

> 本文档帮助 Agent 在修改 AI 助手功能时快速定位需要修改的文件，避免大量阅读代码浪费 token。

## 意图体系

| 意图 | 类型枚举 | 涉及的类 |
|------|---------|---------|
| 查询 AE2 合成配方/样板 | `QUERY_RECIPE` | `AssistantIntentType`, `AssistantServerServices.recipeSummary()`, `PatternDetailFormatter` |
| 查询 AE2 存储概览（缩略图） | `QUERY_STORAGE` | `AssistantIntentType`, `PacketAssistantAction` handler, `AssistantServerServices.queryStorageCandidates()`, `AssistantSessionKind.STORAGE_CANDIDATES`, `AssistantController.handleCandidates()` |
| 查询物品/流体库存数量 | `QUERY_ITEM_COUNT` | `AssistantIntentType`, `PacketAssistantAction` handler, `AssistantServerServices.queryItemCount()`, `AssistantSessionKind.ITEM_COUNT_CANDIDATES` |
| 查询字节占用/容量/无限元件 | `QUERY_BYTES` | `AssistantIntentType`, `AssistantIntentService`, `AssistantAiIntentService`, `AssistantAiIntentJsonParser`, `PacketAssistantAction` handler, `AssistantServerServices.bytesSummary()` / `scanNetworkCellsForInfinite()` / `classifyCell()` / `isInfiniteCell()` |
| 查询无线能源 | `QUERY_POWER` | `WirelessPowerQuery` |
| 查询无线蒸汽 | `QUERY_STEAM` | `WirelessSteamQuery` |
| 查询天气/时间/位置/群系 | `QUERY_WEATHER` 等 | `AssistantServerServices` (weatherSummary 等) |
| 查询背包/网络/合成任务 | `QUERY_INVENTORY` 等 | `AssistantServerServices` |
| 下单合成 | `ORDER_ITEM` | `AssistantIntentType`, `AssistantServerServices.craftingCandidates()` / `submitCraft()` |
| 取出物品 | `WITHDRAW_ITEM` | `AssistantIntentType`, `AssistantServerServices.withdrawCandidates()` / `submitWithdraw()` |
| 确认选项 | `CONFIRM_OPTION` | `AssistantController.confirmOption()`, `AssistantSession` |
| 取消操作 | `CANCEL` | `AssistantController`, `AssistantServerServices.cancelPendingJobs()` |
| 计划管理 | `PLAN_ADD/LIST/COMPLETE/DELETE/MODIFY` | `PlannerServerService`, `PlanStore` |

## 每个功能涉及的文件清单

### 新增一个查询意图（以 QUERY_BYTES 为例）

```
必改文件：
├─ AssistantIntentType.java          ← 添加枚举值
├─ AssistantIntentService.java       ← 添加本地关键词匹配
├─ AssistantAiIntentService.java     ← 添加 AI prompt 描述
├─ AssistantAiIntentJsonParser.java  ← parseType() 接受新类型
├─ AssistantServerServices.java      ← query() switch + 实现方法
├─ PacketAssistantAction.java        ← handler 路由逻辑
├─ AssistantController.java          ← executeIntent() 路由
├─ assistant-features.json           ← 功能菜单配置
├─ en_US.lang + zh_CN.lang           ← 双语文本

可选文件：
├─ AssistantSessionKind.java         ← 如需新的会话状态
├─ GuiAIChat.assistantCapabilityInstruction() ← 功能列表更新
└─ assistant-lexicon.json            ← 如需新词表
```

### 新增一个缩略图候选返回（以 STORAGE_CANDIDATES 为例）

```
必改文件：
├─ AssistantSessionKind.java         ← 添加新枚举值
├─ AssistantServerServices.java      ← 实现 queryXxxCandidates()
├─ PacketAssistantAction.java        ← handler 调用 candidates() 返回
├─ AssistantController.java          ← handleCandidates() 设置标题
│                                      confirmOption() 支持新 kind
│                                      tryPendingCandidateRuleFallback() 添加 kind
│                                      executeIntent() 路由
├─ en_US.lang + zh_CN.lang           ← 标题文本
```

### 修改 AI prompt / 意图解析

```
必看文件：
├─ AssistantAiIntentService.java     ← buildSystemPrompt(), buildPendingSessionContext()
├─ AssistantAiIntentJsonParser.java  ← parse(), parseType(), parseTask()
├─ AssistantFeatureConfig.java       ← buildFeaturesInstruction() 自动从 JSON 生成
├─ assistant-features.json           ← function list source of truth

间接影响：
└─ AssistantController.java          ← executePlan(), executeIntent()
```

### 修改网络包

```
必改文件：
├─ PacketAssistantAction.java        ← 客户端→服务端包定义 + Handler
├─ PacketAssistantResponse.java      ← 服务端→客户端包定义 + Handler
├─ HandlerNetWork.java               ← 注册处理器
└─ LoaderNetWork.java                ← 包 ID 注册（如需新 ID）
```

### 修改 GUI / 聊天界面

```
必改文件：
├─ GuiAIChat.java                    ← 聊天窗口、缩略图渲染、功能菜单按钮
├─ GuiAISettings.java                ← AI 配置界面
└─ gui/costom/                       ← 自定义 GUI 基类组件
```

### 修改 AE2 交互

```
必看文件：
├─ AssistantServerServices.java      ← 所有 AE2 查询/操作入口
├─ AssistantCraftJobManager.java     ← 合成任务管理（异步+超时）
├─ TileEntityAdvanceCraftingLink.java ← 合成网络连接器
├─ TileEntityAdvanceStorageLink.java  ← 存储网络连接器
└─ TileEntityAdvanceNetworkLink.java  ← 全网统计连接器
```

### 修改语音识别

```
必改文件：
├─ VoiceCaptureService.java          ← 录音入口
├─ SpeechToTextClient.java           ← STT 接口
├─ VoskSpeechToTextClient.java       ← 离线 Vosk 识别
├─ HttpSpeechToTextClient.java       ← HTTP STT（OpenAI 兼容）
└─ VoiceAssistantKeyHandler.java     ← 热键触发
```

### 修改多语言文本

```
必改文件：
├─ en_US.lang                        ← 英文翻译
└─ zh_CN.lang                        ← 中文翻译

Key 命名规范（详见 gtnh-mod-context.mdc）:
  adm.error.xxx     — 错误提示
  adm.label.xxx     — 标签文本
  adm.button.xxx    — 按钮文本
  adm.hint.xxx      — 输入框提示
  adm.tooltip.xxx   — 悬浮提示
  adm.ai.xxx        — AI 助手相关
  adm.planner.xxx   — 计划器相关
```

## 文件路径速查（assistant/ 核心文件）

| 文件 | 完整路径 |
|------|---------|
| IntentType | `src/main/java/.../assistant/AssistantIntentType.java` |
| IntentService | `src/main/java/.../assistant/AssistantIntentService.java` |
| AiIntentService | `src/main/java/.../assistant/AssistantAiIntentService.java` |
| AiIntentJsonParser | `src/main/java/.../assistant/AssistantAiIntentJsonParser.java` |
| ServerServices | `src/main/java/.../assistant/AssistantServerServices.java` |
| Controller | `src/main/java/.../assistant/AssistantController.java` |
| Session | `src/main/java/.../assistant/AssistantSession.java` |
| SessionKind | `src/main/java/.../assistant/AssistantSessionKind.java` |
| Formatter | `src/main/java/.../assistant/AssistantFormatter.java` |
| Candidate | `src/main/java/.../assistant/CraftingCandidate.java` |
| FeatureConfig | `src/main/java/.../assistant/AssistantFeatureConfig.java` |
| features.json | `src/main/resources/.../config/assistant-features.json` |
| PacketAction | `src/main/java/.../network/packet/PacketAssistantAction.java` |
| PacketResponse | `src/main/java/.../network/packet/PacketAssistantResponse.java` |
| GuiAIChat | `src/main/java/.../gui/guiscreen/GuiAIChat.java` |
| lang | `src/main/resources/.../lang/zh_CN.lang` + `en_US.lang` |

> 根包 = `com.imgood.advancedatamonitor`
