---
name: textech-icon-import
description: >-
  Converts IconExporter/blockexporter PNG folders into WebAE icon cache filenames
  via tools/icon-import/convert_icon_exporter.py. Use when the user @-mentions
  this skill or asks to import NESQL/IconExporter icons into WebAE packs.
disable-model-invocation: true
---

# TeXTech Icon Import

按需 `@textech-icon-import`。脚本：`tools/icon-import/convert_icon_exporter.py`。

## 清单

```
Icon Import:
- [ ] 1. 确认输入目录为 IconExporter PNG（*.png）
- [ ] 2. 输出目录放在 .workspace/ 或实例 TeXTech/WebAE/icons 准备区
- [ ] 3. 运行 convert_icon_exporter.py
- [ ] 4. 游戏内 /admweb icons … 导入或拷到 icons/<pack>/nei/
```

## 命令

```powershell
py -3 tools/icon-import/convert_icon_exporter.py --input <src_folder> --output <dst_folder>
```

或：

```powershell
.cursor/skills/textech-icon-import/scripts/run-convert.ps1 -InputDir <src> -OutputDir <dst>
```

## 命名规则（摘要）

- 输入：`namespace_item_path.png` 或带 meta 后缀
- 输出：WebAE 磁盘键（`:` → `_`）；详见脚本头注释
- **禁止**把大量 PNG 提交进 `src/`；临时产物用 `.workspace/assets/` 或实例 `TeXTech/WebAE/`

## 相关

- 服务端存储：`webae/icon/IconStore.java`
- 导入命令：`/admweb icons import-nesql` 等（见 `CommandWebConsole`）
- 前端性能：`webae-icon-performance.mdc`（默认不 bulk 同步）
