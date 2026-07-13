---
name: textech-network-packet
description: >-
  Adds or updates TeXTech Forge network packets with fixed IDs, C→S/S→C sync
  links, LoaderNetwork registration, and network-packets.mdc table updates.
  Use when the user @-mentions this skill or explicitly asks to add/change a
  network packet, IMessage, or ADMCHANEL message.
disable-model-invocation: true
---

# TeXTech Network Packet

按需 `@textech-network-packet`。权威步骤与 ID 表：`.cursor/rules/network-packets.mdc`。

## 清单

```
Network Packet:
- [ ] 1. packet 类 + toBytes/fromBytes 对称
- [ ] 2. Handler + 校验 + 主线程调度
- [ ] 3. LoaderNetwork 用下一个可用 ID 注册
- [ ] 4. C→S 必有对应 S→C（若需同步）
- [ ] 5. 更新 network-packets.mdc ID 表与「下一个可用 ID」
- [ ] 6. 必要时跑 doc-check
```

## 步骤摘要

1. 在 `network/packet/` 新建类，实现 `IMessage`（无参构造 + `toBytes`/`fromBytes` 严格对称）
2. Handler：null / `instanceof` / 距离（`NetworkValidationUtil`）；世界状态用 `PacketHandlers.runOnServer`
3. `LoaderNetwork.registerNetWorks()` 注册；客户端 Handler 用 `isClient()` 包裹
4. NBT 用 `ByteBufUtils.writeTag`/`readTag`；大 payload 参考 `PacketPocketSync` 分页
5. **更新** `network-packets.mdc` ID 表，与 `LoaderNetwork.java` 一致
6. 发送：`ADMCHANEL.sendToServer()` / `sendTo()` / `sendToAll()` / `sendToDimension()`

## 禁止

- 随意占用 ID 或跳号不改 mdc
- 只写 C→S 不补客户端同步（需要回包时）
- 在服务端路径加载纯客户端 Handler 类

## 完成后

可 `@textech-doc-sync-pr` 或直接跑 `python tools/doc-check/doc-consistency-check.py` 校验 ID 表。
