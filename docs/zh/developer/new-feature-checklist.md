# 新功能开发决策清单

每次在 TeXTech 中新增功能或子系统前，按本清单自检，避免类数膨胀与重复样板代码。

## 架构与复用

- [ ] 能否用现有 Item / Block / TE 模式扩展，而非新建平行子系统包？
- [ ] AE 相关逻辑是否只通过 `compat/ae/AeCompat` 门面（禁止直接引用 `legacy/` / `native_/`）？
- [ ] 新编织元件是否继承 `AbstractDataLoomItemCell` / `AbstractDataLoomFluidCell`？
- [ ] 口袋升级卡是否继承 `AbstractPocketUpgradeCard`？

## GUI

- [ ] GUI 类型是否选对？
  - 数据配置 / 设置屏 → `ADM_GuiScreen` 或 `AdmItemConfigScreen`
  - 监视器绑定子页 → `AbstractMonitorSubGui`
  - 有槽位容器（新）→ `ADM_UiContainer` + `gui/framework/`
- [ ] 纹理是否使用 `AdmGuiTextures` 常量，而非复制 `ResourceLocation`？
- [ ] 是否避免复制 `GuiSub*` 整段样板？

## 网络与服务端

- [ ] 服务端 Handler 是否使用 `PacketHandlers.runOnServer` / `runOnServerThread`？
- [ ] 涉及方块 / TE 的包是否调用 `NetworkValidationUtil`（距离 + `IOwnableTile` 权限）？
- [ ] 是否在 `loader/LoaderNetwork` 注册固定 packet ID？

## 注册与 Handler

- [ ] Block / Item / TE / Render / Gui 是否只在对应 `Loader*` 中注册？
- [ ] 事件逻辑是否为薄 Handler + `handler/` 领域类，并在 `LoaderHandler` 注册？

## 文档与本地化

- [ ] 是否同步更新 `assets/textech/lang/en_US.lang` + `zh_CN.lang`？
- [ ] 玩家向功能是否更新 `manual/` JSON 与 `docs/`？
- [ ] 新增 Java 文件是否更新 `project-structure.mdc` / `project-structure-details.mdc`？

## 体量预期

理想新增类数（按功能类型）：

| 功能类型 | 典型新增类 |
|----------|------------|
| 简单物品 | 1 Item + lang |
| 编织元件 | 1 Item + 1 Config + lang + 手册 |
| 手持配置窗 | 0（继承 `AdmItemConfigScreen`）+ 1 Packet |
| 监视器绑定页 | 0（继承 `AbstractMonitorSubGui`） |
| 新容器 GUI | 1 Container + 1 Gui（`ADM_UiContainer`）+ 0~1 Packet |

若预估一次性 +5 个 700+ 行 GUI 类，应先抽取基类再实现。

## 参考

- UI 框架：`docs/zh/developer/ui-framework.md`
- GUI 规范：`.cursor/rules/gui-guidelines.mdc`
- 项目结构：`.cursor/rules/project-structure.mdc`
