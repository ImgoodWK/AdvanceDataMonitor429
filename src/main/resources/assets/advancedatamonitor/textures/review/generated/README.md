# ADM 编织元件 / 增幅卡 — 待审核材质

本目录由 `tools/generate_loom_textures.py` 基于 AE2 官方贴图参考生成，供审核后再迁入正式路径。

## AE2 参考来源（已解压到 `../reference_ae2/`）

| 参考文件 | 用途 |
|---------|------|
| ItemMaterial.CardSpeed.png | 编织增幅卡外形 |
| ItemMaterial.CardSuperSpeed.png | 超级编织增幅卡外形 |
| ItemBasicStorageCell.64k.png | 物品编织元件外壳 |
| fluid_storage.64.png | 流体编织元件外壳 |
| ItemExtremeStorageCell.Universe.png | 动态帧条布局（16×320，20 帧） |

## 生成文件

| 文件 | 类型 | 说明 |
|------|------|------|
| weave_amplifier.png + .mcmeta | 动态 16×320（20 帧） | 编织增幅卡；环状能量追逐 + 拖尾，首尾无缝衔接 |
| super_weave_amplifier.png | 静态 16×16 | 超级编织增幅卡，额外橙色高亮 |
| data_dust_loom_cell.png + .mcmeta | 动态 16×320 | 织尘元件，中心粉尘粒子动画 |
| data_form_loom_cell.png + .mcmeta | 动态 16×320 | 织形元件，线框立方旋转动画 |
| data_flow_cell.png + .mcmeta | 动态 16×320 | 涌流元件，液面涨落动画 |
| data_source_loom_cell.png + .mcmeta | 动态 16×320 | 织源元件，源质漩涡脉冲动画 |

## 审核通过后迁入路径

```
textures/items/weave_amplifier.png (+ .mcmeta)
textures/items/super_weave_amplifier.png
textures/items/data_dust_loom_cell.png (+ .mcmeta)
textures/items/data_form_loom_cell.png (+ .mcmeta)
textures/items/data_flow_cell.png (+ .mcmeta)
textures/items/data_source_loom_cell.png (+ .mcmeta)
```

并在 `LoaderItem.java` 中为 4 种元件分别设置独立 `setTextureName`。

## 重新生成

```bash
python tools/generate_loom_textures.py
```

可在脚本中调整 `FRAME_COUNT`、颜色表、或改用 AE2 Universe 条带作为底板进一步手工精修。
