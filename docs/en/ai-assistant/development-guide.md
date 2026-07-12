# TeXTech AI Assistant Development Guide

> Audience: Developers / Cursor Agent · Last synced: 2026-07  
> Player-facing docs: [Player Guide §8](../player/player-guide.md#8-ai-chat--assistant)

---

## Table of Contents

### Part A — Architecture and Data Flow

- [A1. Current Role and Overview](#a1-current-role-and-overview)
- [A2. Key Entry Points and Data Flow](#a2-key-entry-points-and-data-flow)
- [A3. Main Files and Responsibilities](#a3-main-files-and-responsibilities)
- [A4. AI JSON Schema and Parser Behavior](#a4-ai-json-schema-and-parser-behavior)
- [A5. Controller Execution Logic](#a5-controller-execution-logic)
- [A6. Pending Batch Context Repair](#a6-pending-batch-context-repair)
- [A7. GUI State and Chat History](#a7-gui-state-and-chat-history)
- [A8. Packets and Server Execution](#a8-packets-and-server-execution)
- [A9. Configuration and Commands](#a9-configuration-and-commands)
- [A10. Testing and Verification Status](#a10-testing-and-verification-status)
- [A11. Known Limitations and Suggested Follow-ups](#a11-known-limitations-and-suggested-follow-ups)
- [A12. Quick Task Guide for Agents](#a12-quick-task-guide-for-agents)

### Part B — Required-File Quick Reference

- [B1. Intent System](#b1-intent-system)
- [B2. Feature Change File Checklist](#b2-feature-change-file-checklist)
- [B3. assistant/ Core File Paths](#b3-assistant-core-file-paths)

### Part C — Local Speech-to-Text

- [C1. Local STT Service](#c1-local-stt-service)
- [C2. Mod Configuration](#c2-mod-configuration)
- [C3. Environment Variables and Health Check](#c3-environment-variables-and-health-check)

---
## A1. Current Role and Overview

- The AI assistant's current role: extract a structured intent plan from player input, then have the client controller call existing packet/server services to execute tool actions.
- Main path: `AssistantController` prefers `AssistantAiIntentService`, which asks the model to return an `AssistantIntentPlan`.
- Fallback: when AI is unavailable, returns an empty plan, outputs invalid JSON, any task is invalid, or an exception occurs, it falls back to the rule parser `AssistantIntentService.parse()`.
- Normal chat still uses `GuiAIChat` + `DeepSeekChatClient`; it is not the assistant tool flow.
- In-game tool execution still goes through existing packet/server services: the model only outputs structured intents; it does not directly execute AE2 queries, craft submissions, item withdrawals, plan management, or job cancellation.

## A2. Key Entry Points and Data Flow

```mermaid
flowchart TD
    A[GuiAIChat.sendMessage] --> B[AssistantController.handlePrompt(prompt, locale)]
    V[VoiceAssistantKeyHandler] --> W[SpeechToTextClient]
    W --> X[GuiAIChat.submitAssistantPrompt]
    X --> A
    B --> C{pending batch local rule hit?}
    C -->|yes| C1[confirm/append/merge batch]
    C -->|no| D{AI available?}
    D -->|no| E[AssistantIntentService.parse fallback]
    D -->|yes| F[background thread AssistantAiIntentService]
    F --> G[DeepSeekChatClient intent JSON request]
    G --> H[AssistantAiIntentJsonParser -> AssistantIntentPlan]
    H -->|empty/invalid/exception| E
    H -->|CHAT-only| I[GuiAIChat.startNormalAiChatAfterAssistantParse]
    I --> J[normal DeepSeekChatClient chat]
    H -->|Tool tasks| K[AssistantController.executePlan]
    E --> K
    K --> L[PacketAssistantAction]
    L --> M[AssistantServerServices]
    M --> N[PacketAssistantResponse]
    N --> O[AssistantController.handleServerMessage/handleCandidates/handleBatchCandidates]
```

Main flow highlights:

1. `GuiAIChat.sendMessage()` first calls `AssistantController.handlePrompt(prompt, locale)`.
2. If a pending batch exists, `AssistantController.tryHandlePendingBatchPrompt()` handles confirmation, append, merge, and other local context phrases first, avoiding misinterpreting "continue" as a target by the AI.
3. When AI is available, the controller spawns a background thread to call `AssistantAiIntentService.parse(prompt, locale)`, then switches back to the client thread to execute after returning an `AssistantIntentPlan`.
4. When AI is unavailable, the plan is empty, JSON is invalid, any task is invalid, or an exception occurs, it runs `AssistantIntentService.parse()` fallback.
5. A CHAT-only plan calls `GuiAIChat.startNormalAiChatAfterAssistantParse(prompt)` and enters normal chat.
6. Tool tasks are converted to `PacketAssistantAction` by `AssistantController.executePlan/executeTask/executeIntent/executeWithdrawTasks`; the server executes via `AssistantServerServices`, then returns to the client via `PacketAssistantResponse`.
7. The voice entry is `VoiceAssistantKeyHandler`: key press records audio, `SpeechToTextClient` transcribes text, then calls `GuiAIChat.submitAssistantPrompt()`, reusing the same assistant flow afterward.

## A3. Main Files and Responsibilities

### GUI, Entry Points, and Chat

- `src/main/java/com/imgood/textech/gui/guiscreen/GuiAIChat.java`
  - AI chat window, input field, buttons, normal chat HTTP requests, display and scrolling.
  - `sendMessage()` is the text entry point; it hands off to `AssistantController` first; only when the controller returns false does it go directly to normal chat.
  - `sharedHistory` is static and globally shared; closing and reopening the GUI preserves history until the clear button is clicked.
  - `MAX_CONTEXT_MESSAGES=16` controls the context window sent to the model for normal chat.
  - `currentClient.cancel()` only cancels normal AI chat HTTP requests.

- `src/main/java/com/imgood/textech/client/VoiceAssistantKeyHandler.java`
  - Voice hotkey entry. Handles start/stop recording, privacy confirmation, STT invocation, and submits transcribed text to `GuiAIChat`.
  - If the current screen is not `GuiAIChat`, it opens a new `GuiAIChat` before submitting the prompt.

- `src/main/java/com/imgood/textech/voice/SpeechToTextClient.java`
  - OpenAI-compatible `/v1/audio/transcriptions` STT client.
  - Uses `Config.voiceSttBaseUrl` or falls back to `Config.aiApiBaseUrl`; key uses `Config.getVoiceSttApiKey()`.

- `src/main/java/com/imgood/textech/voice/VoiceCaptureService.java`, `WavEncoder.java`, `VoiceStatusListener.java`
  - Handle client-side recording, PCM/WAV encoding, and status callbacks.

### Client Controller and Intent

- `src/main/java/com/imgood/textech/assistant/AssistantController.java`
  - Client-side assistant orchestrator. Handles AI/fallback parse selection, plan execution, multi-order aggregation, pending candidates/batch handling, packet sending, and GUI/session updates after receiving responses.
  - When AI is available, parsing runs on a background thread; execution switches back to the client thread when complete.
  - `executePlan()` executes non-CHAT tasks one by one; multiple `ORDER_ITEM` tasks are aggregated into `AssistantIntent.orderBatch()`.
  - `tryHandlePendingBatchPrompt()` is the key entry for pending batch context repair and runs before AI.

- `src/main/java/com/imgood/textech/assistant/AssistantAiIntentService.java`
  - AI structured intent extraction service.
  - `isAvailable()` requires `Config.aiNetworkEnabled && !Config.getAiApiKey().isEmpty()`.
  - Builds system prompt requiring the model to return only a JSON object; calls `DeepSeekChatClient.chat()` with stream/search disabled, then passes output to `AssistantAiIntentJsonParser`.

- `src/main/java/com/imgood/textech/assistant/AssistantAiIntentJsonParser.java`
  - Extracts the first JSON object from model output and parses the `tasks` array into `AssistantIntentPlan`.
  - Validates type, target, amount, optionNumber, storageScope, confidence, and other fields.
  - Returns an empty plan when any task is invalid, triggering controller fallback.

- `src/main/java/com/imgood/textech/assistant/AssistantIntentTask.java`
  - AI task DTO. Fields: `type`, `target`, `amount`, `optionNumber`, `storageScope`, `confidence`.
  - Provides `toIntent()`, `toOrderLine()`, `storageScopeFromString()`, `isValidStorageScope()`.

- `src/main/java/com/imgood/textech/assistant/AssistantIntentPlan.java`
  - AI task plan container. Provides `empty()`, `isEmpty()`, `size()`, `isChatOnly()`.

- `src/main/java/com/imgood/textech/assistant/AssistantIntentService.java`
  - Legacy rule parser; now the core of fallback and pending batch append auxiliary parsing.
  - Relies on `AssistantLexicon` to recognize queries, orders, confirmations, cancellations, plans, etc.

- `src/main/java/com/imgood/textech/assistant/AssistantIntent.java`
  - Execution-layer intent DTO. Includes `STORAGE_SCOPE_ALL/ITEMS/FLUIDS` and the `orderBatch()` factory.

- `src/main/java/com/imgood/textech/assistant/AssistantIntentType.java`
  - Intent type enum. Currently includes query, order, batch order, confirm, plan, cancel, chat, and related types. Added `QUERY_ITEM_COUNT`, `QUERY_BYTES`.
  - Note: the AI parser does not allow the model to return `ORDER_BATCH` directly; only the controller aggregates multiple `ORDER_ITEM` tasks internally.

- `src/main/java/com/imgood/textech/assistant/AssistantOrderLine.java`
  - Batch order line model. Stores lineIndex, target, amount, candidate list, and selectedCandidate.

- `src/main/java/com/imgood/textech/assistant/AssistantSession.java`
  - Client pending state: candidates, recipe candidates, batch candidates, last user text.

- `src/main/java/com/imgood/textech/assistant/AssistantSessionKind.java`
  - Session types: `ORDER_CANDIDATES`, `RECIPE_CANDIDATES`, `ORDER_BATCH_CANDIDATES`, `WITHDRAW_CANDIDATES`, `WITHDRAW_BATCH_CANDIDATES`, `WITHDRAW_PARTIAL_CONFIRM`,
    `ITEM_COUNT_CANDIDATES` (item stock count query thumbnail results), `STORAGE_CANDIDATES` (storage overview thumbnail results), `TELEPORT_CANDIDATES`, etc.

- `src/main/java/com/imgood/textech/assistant/WithdrawSubmitOutcome.java`
  - Server-side result wrapper for AE2 item withdrawal. Three outcomes: `SUCCESS`, `FAILURE`, `PARTIAL_CONFIRM` (triggered when inventory space is insufficient).

- `src/main/java/com/imgood/textech/assistant/PlayerInventoryUtil.java`
  - Inventory space calculation utility for determining how many items fit in the player inventory and actually inserting items into inventory.

### Packets and Server Services

- `src/main/java/com/imgood/textech/network/packet/PacketAssistantAction.java`
  - Client-to-server assistant request packet.
  - Actions 1–7: craft candidates, submit craft, query, query recipe candidate, batch candidates, submit batch craft, cancel server jobs.
  - `QUERY_STORAGE` can carry `storageScope` in the payload.

- `src/main/java/com/imgood/textech/network/packet/PacketAssistantResponse.java`
  - Server-to-client assistant response packet.
  - Types: message, candidates, batch candidates.
  - `CANDIDATES` payload may include `batchIndex`/`batchCount`/`totalCount`/`append` batch metadata.
  - Client handler calls `AssistantController.handleServerMessage()`, `handleCandidates()`, or `handleBatchCandidates()`.

- `src/main/java/com/imgood/textech/assistant/AssistantCandidateDelivery.java`
  - Server splits candidates by `assistantQueryCandidateBatchSize` and sends multiple consecutive `CANDIDATES` packets via `sendTo`; when exceeding `assistantMaxQueryCandidates`, includes a truncation notice.

- `src/main/java/com/imgood/textech/assistant/AssistantServerServices.java`
  - Server execution layer. Handles AE2 crafting candidates, recipe summary, storage summary, submit craft, batch submit, cancel jobs, plan query.
  - Added `queryStorageCandidates()` — returns storage candidate list with thumbnails (supports items/fluids scope).
  - Added `bytesSummary()` — queries byte usage/capacity/percentage and detects AE2Things infinite storage cells. Internally uses `scanNetworkCellsForInfinite()` + `classifyCell()` (via `compat/ae/AeCompat.cells()`).
  - Searches for `TileEntityAdvanceNetworkLink` (unified linker) within default 32 blocks of the player (configurable via `assistant.linkSearchRadius`, range 4–128).

### Formatting, Helpers, Storage, and Lexicon

- `src/main/java/com/imgood/textech/assistant/AssistantFormatter.java`: formats candidates, batch order lines, etc. for the chat window.
- `src/main/java/com/imgood/textech/assistant/PatternDetailFormatter.java`: AE2 pattern/recipe detail formatting.
- `src/main/java/com/imgood/textech/assistant/CraftingCandidate.java`: AE2 craftable candidate DTO; stores index, displayName, registryName, meta, amount, item NBT.
- `src/main/java/com/imgood/textech/assistant/OrderMemoryStore.java`: user order candidate preference memory for candidate ranking weighting.
- `src/main/java/com/imgood/textech/assistant/PlanStore.java`: simple plan/task storage; supports create/list/complete.
- `src/main/java/com/imgood/textech/assistant/AssistantDebugLog.java`: assistant debug log helper; writes diagnostics when configured.
- `src/main/java/com/imgood/textech/assistant/AssistantLexicon.java`: loads and provides lexicon data; now mainly used for fallback, time/amount/word cleanup, and pending batch append auxiliary parsing.
- `src/main/resources/assets/textech/config/assistant-lexicon.json`: rule parser vocabulary including order/query/confirm/cancel/plan/storage scope keywords.

### AI Client, Configuration, and Settings UI

- `src/main/java/com/imgood/textech/assistant/ai/DeepSeekChatClient.java`
  - OpenAI-compatible chat HTTP client. Reused for both normal chat and AI intent extraction.
  - Supports non-streaming/streaming, request cancellation, debug logging. Normal chat web search is completed by `WebSearchService` before the request; LLM calls always disable provider-native search.

- `src/main/java/com/imgood/textech/assistant/ai/WebSearchService.java`
  - Built-in web search layer: multi-engine adapter (Tavily keyless, DuckDuckGo, Tavily, Brave, Serper, SearXNG), result normalization, automatic fallback, user message injection.

- `src/main/java/com/imgood/textech/ai/ChatRequestOptions.java`
  - Per-request chat options: whether to search, search engine, whether to stream.

- `src/main/java/com/imgood/textech/Config.java`
  - In-memory fields, loading, and saving for AI, voice, and assistant configuration.

- `src/main/java/com/imgood/textech/gui/guiscreen/GuiAISettings.java`
  - In-game AI/voice settings screen.

- `src/main/java/com/imgood/textech/command/CommandAIConfig.java`
  - Command-line configuration entry for setting/viewing AI-related configuration.

## A4. AI JSON Schema and Parser Behavior

`AssistantAiIntentService` requires the model to return a JSON object with the following schema:

```json
{
  "tasks": [
    {
      "type": "QUERY_RECIPE|QUERY_STORAGE|QUERY_POWER|QUERY_ITEM_COUNT|QUERY_BYTES|ORDER_ITEM|WITHDRAW_ITEM|CONFIRM_OPTION|PLAN_CREATE|PLAN_LIST|PLAN_COMPLETE|CANCEL|CHAT",
      "target": "...",
      "amount": 1,
      "optionNumber": -1,
      "storageScope": "all|items|fluids",
      "confidence": 0.9
    }
  ]
}
```

Current parser rules:

- Top level must have a `tasks` array.
- Allowed types: `QUERY_RECIPE`, `QUERY_STORAGE`, `QUERY_ITEM_COUNT`, `QUERY_BYTES`, `QUERY_POWER`, `ORDER_ITEM`, `WITHDRAW_ITEM`, `CONFIRM_OPTION`, `PLAN_CREATE`, `PLAN_LIST`, `PLAN_COMPLETE`, `CANCEL`, `CHAT`.
- `MAX_TASKS=8`; tasks beyond the first 8 are truncated.
- `MIN_CONFIDENCE=0.5`, and confidence must not exceed 1.0.
- Extracts the first complete JSON object from model output; even if wrapped in a markdown code fence, it attempts to extract `{...}`.
- When any task is invalid, the parser returns an empty plan; the controller then falls back entirely to the rule parser rather than skipping individual bad tasks.
- `ORDER_BATCH` is not allowed from AI directly; multiple `ORDER_ITEM` tasks are aggregated into `AssistantIntent.orderBatch()` in the controller.
- Similarly, `WITHDRAW_BATCH` is not allowed from AI directly; multiple `WITHDRAW_ITEM` tasks are aggregated into `AssistantIntent.withdrawBatch()` in the controller.
- `WITHDRAW_ITEM` means withdrawing existing items from AE2 storage to the player inventory, distinct from `ORDER_ITEM` (craft/make new items). The AI prompt convention: craft/order/make/synthesize logic uses `ORDER_ITEM`; take/pull/get/give-me/withdraw logic uses `WITHDRAW_ITEM`.
- `ORDER_ITEM` with empty target is invalid.
- `QUERY_RECIPE` with empty target is valid for browsing AE2 craftable candidates.
- `storageScope` only allows `all`, `items`, `fluids`; empty or missing defaults to `all`.
- For `ORDER_ITEM`, `QUERY_RECIPE`, `QUERY_STORAGE`, amount is normalized to at least 1; for other types, at least 0.

## A5. Controller Execution Logic

- AI availability check: `Config.aiNetworkEnabled && !Config.getAiApiKey().isEmpty()`.
- AI intent parsing runs on a background daemon thread; plan execution is scheduled back to the Minecraft client thread.
- `executePlan()`:
  - When `plan.isChatOnly()`, enters normal chat directly: `GuiAIChat.startNormalAiChatAfterAssistantParse(prompt)`.
  - For multiple tasks, first displays `adm.ai.assistant.split_tasks`.
  - Non-order/withdraw tasks execute one by one via `executeTask()`.
  - Multiple `ORDER_ITEM` tasks are collected and aggregated by `executeOrderTasks()`.
  - Multiple `WITHDRAW_ITEM` tasks are collected and aggregated by `executeWithdrawTasks()`.
- Multiple queries send query packets one by one.
- Multiple `ORDER_ITEM` tasks become `AssistantIntent.orderBatch()`, then request batch candidates.
- Explicit `storageScope` on `QUERY_STORAGE` is written to the packet payload via `PacketAssistantAction.query(..., storageScope)`; the server enters `AssistantServerServices.queryStorageCandidates()` and returns a thumbnail candidate list (`STORAGE_CANDIDATES` session).
- `QUERY_BYTES` is sent via `requestServerQuery()`; the server calls `bytesSummary()` and returns formatted text including byte usage/capacity/percentage and infinite storage cell detection.
  - Single `WITHDRAW_ITEM` task: calls `requestWithdrawCandidates()`; the server searches the AE2 storage network via `AssistantServerServices.withdrawCandidates()` (depends on `TileEntityAdvanceNetworkLink` within default 32 blocks, configurable via `assistant.linkSearchRadius`, range 4–128). After candidates return, `confirmOption()` proceeds to `submitWithdraw()`.
- Multiple `WITHDRAW_ITEM` tasks: aggregated into `WITHDRAW_BATCH`, calls `requestBatchWithdrawCandidates()`; server `AssistantServerServices.batchWithdrawCandidates()` searches candidates line by line. After confirmation, `submitBatchWithdraw()` executes withdrawal line by line into player inventory.
- When inventory space is insufficient for the full quantity during withdrawal, the server returns `WithdrawSubmitOutcome(PARTIAL_CONFIRM, ...)`; the client enters `WITHDRAW_PARTIAL_CONFIRM` session. User confirmation submits partial withdrawal.
- During batch withdrawal, if one line has insufficient space, the entire batch operation pauses and prompts the user to confirm that line individually first.
- `CONFIRM_OPTION` currently routes by session kind:
  - `ORDER_CANDIDATES`: submit craft.
  - `RECIPE_CANDIDATES`: send `QUERY_RECIPE_CANDIDATE` to query recipe details.
  - `ORDER_BATCH_CANDIDATES`: proceed via `confirmBatch()` for batch submit.
- `CANCEL` clears client `AssistantSession`, sends `PacketAssistantAction.cancelServerJobs()`, and shows a cancellation notice. Normal AI chat HTTP cancellation does not go here; the GUI cancel button calls `currentClient.cancel()`.

## A6. Pending Batch Context Repair

All currently supported local context repair lives in `AssistantController.tryHandlePendingBatchPrompt()` and runs before AI. Prerequisite: `AssistantSession.client().getKind() == ORDER_BATCH_CANDIDATES` or `WITHDRAW_BATCH_CANDIDATES`.

Supported behaviors:

- When `ORDER_BATCH_CANDIDATES` or `WITHDRAW_BATCH_CANDIDATES` is active, the following confirmation inputs locally prefer `confirmBatch()` / `confirmWithdrawBatch()`:
  - `确认`, `提交`, `继续`, `继续下单`, `确认下单`, `现在可以继续下单`
  - Plus equivalent English phrases: `confirm`, `submit`, `continue`
- Append inputs parse new order lines and append to the current batch, then re-request batch candidates:
  - `再加`, `加一个`, `添加`, `补一个`, `追加`, `再来`, `再要`
  - Append parse flow: remove these append words first, prepend an "order" prefix, then pass to `AssistantIntentService.parse()`; if it parses `ORDER_ITEM` or `ORDER_BATCH`, append to current pending lines.
- Merge inputs merge quantities by target while preserving order, then re-request batch candidates:
  - Must match both keyword groups: `刚刚/刚才/之前/上次/前面` and `加起来/合并/汇总/一起/总共/加总`.
  - Merge logic uses `LinkedHashMap<String, AssistantOrderLine>` with the original target string as key, preserving first-occurrence order and accumulating amounts.
- These rules run before AI, mainly to avoid "continue" being interpreted as an item target by AI or the rule parser.

Current limitations:

- Shallow keyword rules only; not true long-term memory.
- Only operates on the current pending batch; does not persist across GUI/session or game restarts.
- Append sentences still rely on the rule parser; complex natural-language append may fail.

## A7. GUI State and Chat History

- `GuiAIChat`'s `history` points to static `sharedHistory`; closing and reopening the GUI multiple times preserves chat history until the player clicks clear.
- The clear button empties shared `history`, resets scroll/status, and rebuilds display lines.
- `scrollToBottomRequested` is set on new user/assistant/server messages, streaming deltas, errors, privacy notice/confirm, etc.; `rebuildDisplayLines()` auto-scrolls to bottom afterward.
- Normal chat request context is controlled by `MAX_CONTEXT_MESSAGES=16`; only the most recent 16 history entries plus system prompt are sent.
- `currentClient.cancel()` only cancels normal AI chat HTTP requests.
- Server-side AE2 job cancellation is sent to the server via assistant `CANCEL` intent.
- Multiple GUI instances share history, but each `initGui()` creates a new `AssistantController`.
- Normal chat history lives only in static memory; not persisted to disk.

## A8. Packets and Server Execution

`PacketAssistantAction` action numbers:

1. `REQUEST_CRAFT_CANDIDATES`
2. `SUBMIT_CRAFT`
3. `QUERY`
4. `QUERY_RECIPE_CANDIDATE`
5. `REQUEST_BATCH_CANDIDATES`
6. `SUBMIT_BATCH_CRAFT`
7. `CANCEL_SERVER_JOBS`
8. `REQUEST_WITHDRAW_CANDIDATES` — request AE2 storage withdrawal candidates
9. `SUBMIT_WITHDRAW` — submit single withdrawal (includes partialConfirm flag)
10. `REQUEST_BATCH_WITHDRAW_CANDIDATES` — request batch withdrawal candidates
11. `SUBMIT_BATCH_WITHDRAW` — submit batch withdrawal

Key server behaviors:

- `QUERY_STORAGE` payload may include `storageScope`; when present, handler calls `AssistantServerServices.queryStorageCandidates(player, rawText, target, storageScope, locale)`, returning `PacketAssistantResponse.candidates()` with `STORAGE_CANDIDATES` session kind. Results render as a thumbnail list on the client; users can enter a number to withdraw items.
- `QUERY_BYTES` goes through the standard query flow; handler routes to `AssistantServerServices.query()` → `bytesSummary()`, returning a text message.
- `QUERY_RECIPE` with empty target returns recipe candidates, i.e. `AssistantSessionKind.RECIPE_CANDIDATES`, letting the user confirm a candidate to view details.
- `AssistantServerServices` searches within default 32 blocks (configurable via `assistant.linkSearchRadius`, range 4–128):
  - crafting / storage / network stats: unified `TileEntityAdvanceNetworkLink`
- Server service currently covers:
  - AE2 crafting candidates
  - recipe summary / pattern details
  - storage summary (thumbnail candidate list with item/fluid scope) + byte usage details (with infinite cell detection)
  - submit craft
  - batch candidates / batch submit
  - cancel pending craft jobs
  - plan create/list/complete
  - withdraw candidates / submit withdraw / batch withdraw — transfer items from AE2 storage to player inventory, with inventory space check and partial withdrawal confirmation
  - item stock count query (`QUERY_ITEM_COUNT`, thumbnail list with K/M/T formatting)
  - byte usage query (`QUERY_BYTES`, includes `scanNetworkCellsForInfinite` infinite cell scan)
- Batch submit first validates each line has a usable candidate, valid amount, and does not exceed `Config.assistantMaxOrderAmount`.
- Batch submit also requires `AssistantCraftJobManager.instance().availableSlots(player) >= lines.size()`; otherwise returns "insufficient available AE2 crafting slots" and submits no jobs.

## A9. Configuration and Commands

Relevant `Config` fields:

- AI network and model: `aiNetworkEnabled`, `aiApiKey`, `aiApiBaseUrl`, `aiModel`, `aiRecentModels`.
- Search and streaming: `aiWebSearchEnabled`, `aiWebSearchMode`, `aiStreamingEnabled`.
- Debug and privacy: `aiDebugLogging`, `aiPrivacyConfirmed`.
- HTTP parameters: `aiTimeoutSeconds`, `aiMaxTokens`, `aiTemperature`.
- Voice/STT: `voiceAssistantEnabled`, `voicePrivacyConfirmed`, `voiceSttBaseUrl`, `voiceSttApiKey`, `voiceSttModel`, `voiceSttTimeoutSeconds`.
- Assistant execution limits: `assistantMaxOrderAmount`, `assistantMaxWithdrawAmount`, `assistantCraftJobTimeoutSeconds`, `assistantMaxConcurrentCraftJobs`.
- AE2 candidate queries: `assistantQueryCandidateBatchSize` (entries per network packet batch, default 1000), `assistantMaxQueryCandidates` (total cap per query, default 20000).

Configuration entry points:

- `CommandAIConfig`: command-line view/modify AI configuration; suitable for server or debug environments.
- `GuiAISettings`: in-game settings screen for provider/base/model/search/streaming/debug/privacy/timeout/maxTokens/temperature, plus voice settings.
- `AssistantLexicon` / `assistant-lexicon.json`: now mainly used for fallback and auxiliary parsing/time/amount/word cleanup; no longer the assistant main path.

## A10. Testing and Verification Status

- Historical test entry `src/test/java/test/AssistantIntentParserSuite.java` is **no longer in the repo**; use temporary stubs under `.workspace/assistant-parser-suite/` (not committed) or restore regression tests when dependencies are available.
- Suggested coverage (if tests are restored):
  - Legacy rule parser: storage, recipe, power, order, confirm, cancel, batch order, plan, chat fallback.
  - AI JSON parser: single/multiple tasks, multiple `ORDER_ITEM` for batch order, confirm, chat, empty recipe target, empty order target invalid, low confidence invalid, invalid storageScope, max 8 tasks.
- Current attempt to run `./gradlew.bat test`: Gradle wrapper starts but does not complete full tests; `compileClasspath` dependency resolution fails, missing:
  - `com.github.GTNewHorizons:Avaritiaddons:1.7.1-GTNH`
  - `com.github.GTNewHorizons:Eternal-Singularity:1.2.1`
  - `com.github.GTNewHorizons:Universal-Singularities:8.7.0`
- After fixing dependency sources or local cache, agents should re-run `./gradlew.bat test` and confirm parser regression results from output.

## A11. Known Limitations and Suggested Follow-ups

Known limitations:

- AI parser prompt does not include full structured history; relies on the current sentence plus controller pending batch local rules.
- `再加...` fallback parsing still depends on the rule parser; complex append sentences may fail.
- Pending batch merge uses target strings, not candidate registry/meta normalization; e.g. `木棍` and `minecraft:stick` may not merge.
- Re-requesting batch candidates refreshes the session; old candidates are not preserved.
- Normal chat history is in static memory; not persisted to disk across game restarts.
- Multiple GUI instances share history, but the controller is recreated each time the GUI opens.
- When AI output is invalid, the entire plan falls back; individual bad tasks are not skipped.
- Server responses are mostly formatted strings, not structured results.
- Item matching is still fuzzy contains; short words risk false matches.
- AE2 candidate total cap per query is controlled by `assistantMaxQueryCandidates` (default 20000); exceeding truncates with notice. Batch size is controlled by `assistantQueryCandidateBatchSize` (default 1000).
- Some parameters remain hardcoded, e.g. batch line cap 8; AE2 link search radius is now configurable (`assistant.linkSearchRadius`, default 32, range 4–128).
- Batch submit consumes AE2 crafting slots by line count; duplicate lines that are not merged use more slots.
- Insufficient inventory space during withdrawal triggers partial withdrawal confirmation; rule fallback and pending batch confirmation support Chinese and English keywords (e.g. `确认` / confirm / yes / ok).
- During batch withdrawal, if one line needs partial confirmation, the entire batch pauses; subsequent lines cannot be skipped.
- Withdrawal depends on AE2 `extractItems` API; fails if storage cells do not support extraction (some special storage).
- Infinite storage cell detection uses `AeCompat.cells().readItemCellStats()` / `readFluidCellStats()` (Legacy path includes GlodBlock `FluidCellInventoryHandler` reflection); class name keywords and byte threshold logic see `compat/ae/legacy/LegacyAeCellStatsAdapter`. For GTNH 2.9.0+ Native path, see `docs/en/developer/ae-compat-290.md`.

Suggested next steps:

- Introduce structured conversation state; pass pending batch, recent candidates, recent query targets, etc. as explicit context to the AI intent parser.
- Add batch edit API supporting add/remove/update/merge line instead of only re-requesting candidates.
- Normalize target/candidate identity; merge by registryName + meta or item NBT rather than plain strings.
- Change service-layer results to structured results, then render via client formatter to reduce future UI/automation cost.
- Add mock unit tests covering controller plan execution, pending batch repair, packet payload, server service edge cases.
- Make hardcoded parameters configurable: AE2 query radius, batch line cap, local inventory fallback cap, etc. (candidate total cap and batch size are already configurable).

## A12. Quick Task Guide for Agents

- Change AI intent extraction: start with `AssistantAiIntentService` prompt and `AssistantAiIntentJsonParser` validation logic.
- Change tool execution / multi-task dispatch: see `AssistantController.executePlan()`, `executeOrderTasks()`, `confirmOption()`, `tryHandlePendingBatchPrompt()`.
- Change AE2 behavior: see `AssistantServerServices` and `AssistantCraftJobManager` concurrency/timeout/cancel logic. When adding query/action types, also update the `query()` switch branch and `PacketAssistantAction` handler.
- Change infinite cell detection: see `AssistantServerServices.scanNetworkCellsForInfinite()`, `classifyCell()`, `isInfiniteCell()`.
- Change UI, chat history, scrolling, normal chat: see `GuiAIChat`.
- Change voice entry: see `VoiceAssistantKeyHandler`, `SpeechToTextClient`, `VoiceCaptureService`.
- Change fallback: see `AssistantIntentService`, `AssistantLexicon`, `assistant-lexicon.json`.
- Change packet protocol: see `PacketAssistantAction` and `PacketAssistantResponse`; note client/server NBT read/write compatibility.


---

# Part B — Required-File Quick Reference

## B1. Intent System

| Intent | Type Enum | Classes Involved |
|------|---------|---------|
| Query AE2 craft recipes/patterns | `QUERY_RECIPE` | `AssistantIntentType`, `AssistantServerServices.recipeSummary()`, `PatternDetailFormatter` |
| Query AE2 storage overview (thumbnails) | `QUERY_STORAGE` | `AssistantIntentType`, `PacketAssistantAction` handler, `AssistantServerServices.queryStorageCandidatesResult()`, `AssistantCandidateDelivery`, `AssistantSessionKind.STORAGE_CANDIDATES`, `AssistantController.handleCandidates()` |
| Query item/fluid stock counts | `QUERY_ITEM_COUNT` | `AssistantIntentType`, `PacketAssistantAction` handler, `AssistantServerServices.queryItemCountResult()`, `AssistantCandidateDelivery`, `AssistantSessionKind.ITEM_COUNT_CANDIDATES` |
| Query byte usage/capacity/infinite cells | `QUERY_BYTES` | `AssistantIntentType`, `AssistantIntentService`, `AssistantAiIntentService`, `AssistantAiIntentJsonParser`, `PacketAssistantAction` handler, `AssistantServerServices.bytesSummary()` / `scanNetworkCellsForInfinite()` / `classifyCell()` / `isInfiniteCell()` |
| Query wireless power | `QUERY_POWER` | `WirelessPowerQuery` |
| Query wireless steam | `QUERY_STEAM` | `WirelessSteamQuery` |
| Query weather/time/location/biome | `QUERY_WEATHER` etc. | `AssistantServerServices` (weatherSummary etc.) |
| Query inventory/network/craft jobs | `QUERY_INVENTORY` etc. | `AssistantServerServices` |
| Order craft | `ORDER_ITEM` | `AssistantIntentType`, `AssistantServerServices.queryCraftingCandidates()` / `AssistantCandidateDelivery` / `submitCraft()` |
| Withdraw items | `WITHDRAW_ITEM` | `AssistantIntentType`, `AssistantServerServices.queryWithdrawCandidates()` / `AssistantCandidateDelivery` / `submitWithdraw()` |
| Confirm option | `CONFIRM_OPTION` | `AssistantController.confirmOption()`, `AssistantSession` |
| Cancel operation | `CANCEL` | `AssistantController`, `AssistantServerServices.cancelPendingJobs()` |
| Plan management | `PLAN_ADD/LIST/COMPLETE/DELETE/MODIFY` | `PlannerServerService`, `PlanStore` |

## B2. Feature Change File Checklist

### Add a new query intent (example: QUERY_BYTES)

```
Required files:
├─ AssistantIntentType.java          ← add enum value
├─ AssistantIntentService.java       ← add local keyword matching
├─ AssistantAiIntentService.java     ← add AI prompt description
├─ AssistantAiIntentJsonParser.java  ← parseType() accepts new type
├─ AssistantServerServices.java      ← query() switch + implementation
├─ PacketAssistantAction.java        ← handler routing logic
├─ AssistantController.java          ← executeIntent() routing
├─ assistant-features.json           ← feature menu config
├─ en_US.lang + zh_CN.lang           ← bilingual text

Optional files:
├─ AssistantSessionKind.java         ← if new session state needed
├─ GuiAIChat.assistantCapabilityInstruction() ← feature list update
└─ assistant-lexicon.json            ← if new vocabulary needed
```

### Add a new thumbnail candidate response (example: STORAGE_CANDIDATES)

```
Required files:
├─ AssistantSessionKind.java         ← add new enum value
├─ AssistantServerServices.java      ← implement queryXxxCandidates()
├─ PacketAssistantAction.java        ← handler calls candidates() return
├─ AssistantController.java          ← handleCandidates() sets title
│                                      confirmOption() supports new kind
│                                      tryPendingCandidateRuleFallback() adds kind
│                                      executeIntent() routing
├─ en_US.lang + zh_CN.lang           ← title text
```

### Modify AI prompt / intent parsing

```
Key files:
├─ AssistantAiIntentService.java     ← buildSystemPrompt(), buildPendingSessionContext()
├─ AssistantAiIntentJsonParser.java  ← parse(), parseType(), parseTask()
├─ AssistantFeatureConfig.java       ← buildFeaturesInstruction() auto-generated from JSON
├─ assistant-features.json           ← function list source of truth

Indirect impact:
└─ AssistantController.java          ← executePlan(), executeIntent()
```

### Modify network packets

```
Required files:
├─ PacketAssistantAction.java        ← client→server packet definition + Handler
├─ PacketAssistantResponse.java      ← server→client packet definition + Handler
├─ HandlerNetwork.java               ← register handlers
└─ LoaderNetwork.java                ← packet ID registration (if new ID needed)
```

### Modify GUI / chat interface

```
Required files:
├─ GuiAIChat.java                    ← chat window, thumbnail rendering, feature menu buttons
├─ GuiAISettings.java                ← AI settings screen
└─ gui/custom/                       ← custom GUI base components
```

### Modify AE2 interaction

```
Key files:
├─ AssistantServerServices.java      ← all AE2 query/action entry points
├─ AssistantCraftJobManager.java     ← craft job management (async + timeout)
└─ TileEntityAdvanceNetworkLink.java ← unified Advanced Network Linker (stats / storage / crafting)
```

### Modify voice recognition

```
Required files:
├─ VoiceCaptureService.java          ← recording entry
├─ SpeechToTextClient.java           ← STT interface
├─ VoskSpeechToTextClient.java       ← offline Vosk recognition
├─ HttpSpeechToTextClient.java       ← HTTP STT (OpenAI-compatible)
└─ VoiceAssistantKeyHandler.java     ← hotkey trigger
```

### Modify multilingual text

```
Required files:
├─ en_US.lang                        ← English translation
└─ zh_CN.lang                        ← Chinese translation

Key naming convention (see gtnh-mod-context.mdc):
  adm.error.xxx     — error messages
  adm.label.xxx     — label text
  adm.button.xxx    — button text
  adm.hint.xxx      — input hints
  adm.tooltip.xxx   — tooltips
  adm.ai.xxx        — AI assistant related
  adm.planner.xxx   — planner related
```

## B3. assistant/ Core File Paths

| File | Full Path |
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

> Root package = `com.imgood.textech`

---

# Part C — Local Speech-to-Text

## C1. Local STT Service

`tools/local-stt/` provides an OpenAI-compatible local speech transcription service exposing `POST /v1/audio/transcriptions`; the mod calls it via `Config.voiceSttBaseUrl`.

**Windows quick start**:

1. In PowerShell, enter `tools/local-stt/`
2. Run `.\start-local-stt.bat`
3. First run installs Python dependencies and downloads the Whisper model; keep the window open while playing

## C2. Mod Configuration

Edit `.minecraft/config/textech/textech.cfg`:

```text
voice {
    B:enabled=true
    B:privacyConfirmed=true
    S:sttBaseUrl=http://127.0.0.1:8000
    S:sttApiKey=
    S:sttModel=small
    I:sttTimeoutSeconds=120
}
```

`sttApiKey` can be left empty for `127.0.0.1` / `localhost`.

You can also use the mod's built-in **Vosk offline model** (`assets/textech/voice/vosk/zh-small/`) without a local STT service; see [Player Guide §9](../player/player-guide.md#9-voice-assistant).

## C3. Environment Variables and Health Check

```powershell
$env:ADM_STT_MODEL = "small"          # tiny, base, small, medium, large-v3 or local model path
$env:ADM_STT_LANGUAGE = "zh"          # optional; leave empty for auto-detect
$env:ADM_STT_DEVICE = "auto"          # auto, cpu, cuda
$env:ADM_STT_COMPUTE_TYPE = "int8"    # int8 for CPU; float16 common for CUDA
$env:ADM_STT_PORT = "8000"
.\start-local-stt.bat
```

For Chinese voice commands, start with `small`; if recognition is poor and hardware allows, try `medium`.

Health check: visit <http://127.0.0.1:8000/health>; should return JSON with `ok: true`.
