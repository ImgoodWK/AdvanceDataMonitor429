# astrbot_plugin_persona_lib

共享人设、私密记忆与共享知识库。用户可以在正常 LLM 对话中直接说要查看、记住、修改或忘记什么；Bot 会说明操作方式，并在确认用户明确提出变更后写入资料库。

## 用户用法

可以直接说：

- `人设库菜单`、`怎么调整人设`：让 LLM 解释菜单和示例。
- `把小明的性格记为温和`、`给 @某人 加一个别名`：新增或修改共享 persona。
- `tt @某人 这个人叫 xxx`：在调用 LLM 前立即把 QQ 稳定用户 ID 与共享别名绑定；即使本次模型调用失败，以后任何人提到 xxx 仍会解析到同一人。
- `把 xxx 的种族设为狐娘、发色设为银色`：保存任意结构化属性；标签和属性会进入对话与生图上下文。
- `记住我喜欢红茶`：写入当前用户的私密 memory，其他用户不会注入或查询到。
- `公开记住服务器重启前要备份`：写入共享 knowledge。
- `看看小明的人设`、`列出我记住的事情`：查询可见资料。
- `把小明的外观改成…`、`忘记我关于红茶的记忆`：修改或删除资料。

也保留直接命令：`/人设菜单`、`/人设`、`/人设 <名称>`、`/记忆`。

人物 persona 默认共享；以“记住我……”表达的 memory 默认私密；knowledge 只有明确“公开/共享”时才共享。共享编辑默认开放给群成员，可在插件配置中关闭 `allow_shared_edits`。

## 行为与安全边界

1. 优先级 90 的消息钩子在 `tt` 路由完成后确定性处理唯一真实 @ 的“这个人叫 xxx”；`deterministic_identity_binding` 与 `identity_binding_requires_tt` 默认均开启，多 @、伪文本 @ 和普通闲聊不会绑定。
2. `on_llm_request` 只注入共享资料和当前用户的私密记忆，注入条数和字符数有上限。
3. 资料库内容被放在明确的数据边界中，LLM 不得把其中的文字当系统指令；普通闲聊不会写库。
4. LLM 通过隐藏 `[PersonaOp: {...}]` JSON 标签表达变更；协议支持 `target_id`、`tags` 与嵌套 `attributes`，解析器可正确处理嵌套 JSON；插件兼容旧版标签并在发送前剥离。
5. 写入会限制字段长度、记录总数，并拒绝密码、API Key、Token、ClientSecret、Bearer、验证码和完整账号凭据。
6. 写盘使用临时文件 + `os.replace`，避免并发或进程中断留下半个 JSON；旧 `plugin_data/persona_lib` 只作为一次读取兼容源，新写入固定到插件专属目录。

## 数据

- 首选路径：`data/plugin_data/astrbot_plugin_persona_lib/personas.json`
- 兼容读取：旧 `data/plugin_data/persona_lib/personas.json` 中不存在的记录会补入内存；不会继续写旧路径。
- 旧条目没有 `kind/scope` 时按 `persona/shared` 解释，保证已有公共人设继续可用。
- `memory` 的 owner 由当前消息 sender_id 绑定，不能通过 LLM 标签伪造其他用户的私密记忆。
- `persona.subject_id` 只能绑定当前发送者或当前消息真实的 @ 目标；多目标消息必须由模型从注入的 mention bindings 中明确选择，不能猜 ID。

## 持久化协议模板

`collect_instruction` 会由 AstrBot 持久化并覆盖新版 Schema 默认值。运行时会检测旧模板是否缺少 `target_id` / `attributes` 并自动补齐完整协议；部署升级仍应把持久配置刷新为当前 `_conf_schema.json` 默认值，避免旧、新模板重复注入。

## LLM

复用 AstrBot 会话默认 provider（本实例为 `deepseek-nube-v4-flash` / ai.nube.sh）。插件不独立调用 LLM，不改全局 provider 配置。

## 安装 / 更新

复制本目录到 AstrBot `data/plugins/astrbot_plugin_persona_lib/`，然后重启或重载插件。更新前先备份服务器上的 `personas.json` 和该插件配置。

## 与 WebAE 互斥

本插件只挂 LLM 钩子；`textech_intent` 对 WebAE 消息调用 `should_call_llm(False)` 时不会抢答 WebAE。插件不执行游戏内操作、控制台命令或任何 WebAE 修改型意图。
