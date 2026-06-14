# 高级计划器 (Advance Planner) 开发者文档

> AdvanceDataMonitor 模组 — 面向开发者的 API 与架构参考

---

## 目录

1. [架构概览](#1-架构概览)
2. [类关系图](#2-类关系图)
3. [NBT 数据结构详解](#3-nbt-数据结构详解)
4. [PlannerEntry 数据类](#4-plannerentry-数据类)
5. [PlannerMergeMode 枚举](#5-plannermergemode-枚举)
6. [ItemAdvancePlanner 完整公共 API](#6-itemadvanceplanner-完整公共-api)
7. [GUI 系统](#7-gui-系统)
8. [注册流程](#8-注册流程)
9. [调用示例](#9-调用示例)
10. [扩展指南](#10-扩展指南)
11. [语言文件格式](#11-语言文件格式)

---

## 1. 架构概览

高级计划器由以下核心组件构成：

| 层级 | 类 | 职责 |
|------|-----|------|
| **数据层** | `PlannerEntry` | 单条计划条目的数据模型 |
| **数据层** | `PlannerMergeMode` | 合并策略枚举 |
| **业务层** | `ItemAdvancePlanner` | 物品主类，包含所有 NBT 读写 API 和合并逻辑 |
| **表示层** | `GuiAdvancePlanner` | 计划器主 GUI（列表展示、编辑、勾选） |
| **表示层** | `GuiPlannerMergeConfirm` | 整合确认 GUI（模式选择、执行合并） |
| **注册层** | `LoaderItem` | 物品注册到 Forge 游戏注册表 |

### 数据流向

```
玩家操作 (右键/点击)
    │
    ▼
ItemAdvancePlanner.onItemRightClick()
    │
    ├─ 潜行 → setHudEnabled() → 修改 NBT
    │
    └─ 非潜行 → openPlannerGui() → GuiAdvancePlanner
                                         │
                                         ├─ 勾选 → ItemAdvancePlanner.toggleCompleted()
                                         ├─ 编辑 → ItemAdvancePlanner.setEntry()
                                         ├─ 添加 → ItemAdvancePlanner.addEntry()
                                         └─ 整合 → GuiPlannerMergeConfirm
                                                        │
                                                        └─ 确认 → ItemAdvancePlanner.mergeMultiplePlanners()
                                                                      │
                                                                      ├─ 消耗其他计划器物品
                                                                      └─ 替换当前物品为合并结果
```

---

## 2. 类关系图

```
Item (net.minecraft.item.Item)
  └── ItemAdvancePlanner
        ├── 使用 PlannerEntry (数据模型)
        ├── 使用 PlannerMergeMode (枚举)
        ├── 依赖 GuiAdvancePlanner (客户端 GUI)
        └── 静态方法全部为 public，可从任意位置调用

ADM_GuiScreen (自定义 GUI 基类)
  ├── GuiAdvancePlanner
  │     ├── 使用 ItemAdvancePlanner (读写数据)
  │     ├── 使用 PlannerEntry (展示数据)
  │     └── 跳转 → GuiPlannerMergeConfirm
  └── GuiPlannerMergeConfirm
        ├── 使用 ItemAdvancePlanner (合并逻辑)
        ├── 使用 PlannerMergeMode (模式选择)
        └── 跳转 → GuiAdvancePlanner (返回/完成)

LoaderItem
  └── 实例化 ItemAdvancePlanner 并注册到 GameRegistry
```

---

## 3. NBT 数据结构详解

高级计划器的所有数据存储在 `ItemStack` 的 NBT 标签中。

### 3.1 顶层结构

```
ItemStack
  └── TagCompound (root)
        ├── "plannerEntries" : TagList (type=10, 即 TagCompound 列表)
        │     ├── [0] TagCompound → PlannerEntry
        │     ├── [1] TagCompound → PlannerEntry
        │     └── ...
        ├── "nextSlotIndex" : int (下一个可用的 slotIndex)
        ├── "hudEnabled" : boolean (HUD 是否启用)
        └── "hudMaxDisplay" : int (HUD 最大显示条数)
```

### 3.2 单条 PlannerEntry 的 NBT 结构

```
TagCompound (单条条目)
  ├── "slotIndex"   : int     → 条目的唯一编号（全局递增，不因删除而回收）
  ├── "text"        : String  → 条目的文本内容
  ├── "timestamp"   : long    → 创建时间（毫秒级 Unix 时间戳）
  └── "completed"   : boolean → 是否已完成
```

### 3.3 NBT Key 常量定义

| 常量名 | 值 | 类型 | 说明 |
|--------|-----|------|------|
| `NBT_KEY_ENTRIES` | `"plannerEntries"` | TagList | 存储所有条目 |
| `NBT_KEY_NEXT_SLOT` | `"nextSlotIndex"` | int | 下一个可用槽位索引 |
| `NBT_KEY_HUD_ENABLED` | `"hudEnabled"` | boolean | HUD 开关状态 |
| `NBT_KEY_HUD_MAX_DISPLAY` | `"hudMaxDisplay"` | int | HUD 最大显示条数 |
| `DEFAULT_HUD_MAX_DISPLAY` | `5` | int | HUD 默认最大显示数 |

### 3.4 存储特点

- **slotIndex 单调递增：** 删除条目不会回收 slotIndex，新条目始终使用 `nextSlotIndex` 并自增。
- **条目查找方式：** 通过遍历 `plannerEntries` 列表匹配 `slotIndex` 字段，非数组下标随机访问。
- **timestamp 默认值：** 如果 NBT 中没有 `timestamp` 字段（旧数据兼容），`PlannerEntry.fromNBT()` 会使用 `System.currentTimeMillis()`。

---

## 4. PlannerEntry 数据类

**包路径：** `com.imgood.advancedatamonitor.items.PlannerEntry`

### 4.1 字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `slotIndex` | `int` | `0` | 条目的唯一标识编号 |
| `text` | `String` | `""` | 条目文本内容（null 安全，构造时自动转为空串） |
| `timestamp` | `long` | `System.currentTimeMillis()` | 创建时间戳（毫秒） |
| `completed` | `boolean` | `false` | 是否已完成 |

### 4.2 构造方法

```java
// 默认构造（slotIndex=0, text="", timestamp=当前时间, completed=false）
public PlannerEntry()

// 指定编号和文本（timestamp 自动设为当前时间）
public PlannerEntry(int slotIndex, String text)

// 全参数构造
public PlannerEntry(int slotIndex, String text, long timestamp, boolean completed)
```

### 4.3 方法列表

| 方法签名 | 返回值 | 说明 |
|----------|--------|------|
| `getSlotIndex()` | `int` | 获取条目编号 |
| `setSlotIndex(int slotIndex)` | `void` | 设置条目编号 |
| `getText()` | `String` | 获取文本内容 |
| `setText(String text)` | `void` | 设置文本（null 自动转为空串） |
| `getTimestamp()` | `long` | 获取时间戳 |
| `setTimestamp(long timestamp)` | `void` | 设置时间戳 |
| `isCompleted()` | `boolean` | 是否已完成 |
| `setCompleted(boolean completed)` | `void` | 设置完成状态 |
| `toggleCompleted()` | `void` | 翻转完成状态 |
| `getFormattedTime()` | `String` | 返回格式化时间字符串（`yyyy-MM-dd HH:mm`） |
| `toNBT()` | `NBTTagCompound` | 序列化为 NBT |
| `fromNBT(NBTTagCompound tag)` | `PlannerEntry` (static) | 从 NBT 反序列化，tag 为 null 时返回 null |
| `copy()` | `PlannerEntry` | 深拷贝 |
| `toString()` | `String` | 调试用字符串表示 |

---

## 5. PlannerMergeMode 枚举

**包路径：** `com.imgood.advancedatamonitor.items.PlannerMergeMode`

```java
public enum PlannerMergeMode {
    BY_TIME,   // 按时间戳升序排列
    BY_INDEX   // 按 slotIndex 升序排列
}
```

| 枚举值 | 排序依据 | 合并行为 |
|--------|----------|----------|
| `BY_TIME` | `PlannerEntry.timestamp` | 所有条目按时间戳升序合并，重新从 1 开始编号 |
| `BY_INDEX` | `PlannerEntry.slotIndex` | 先保留第一个计划器的全部条目，再追加第二个的（slotIndex 重新分配），按编号排序 |

---

## 6. ItemAdvancePlanner 完整公共 API

**包路径：** `com.imgood.advancedatamonitor.items.ItemAdvancePlanner`

所有数据操作方法均为 **`public static`**，无需实例即可调用。方法的第一个参数始终是 `ItemStack stack`（计划器物品栈）。

### 6.1 核心数据操作

#### `getOrCreatePlannerNBT(ItemStack stack) → NBTTagCompound`

确保 ItemStack 上存在计划器所需的 NBT 结构并返回根 TagCompound。如果 stack 为 null，返回一个新的空 TagCompound（不会修改任何东西）。

```java
public static NBTTagCompound getOrCreatePlannerNBT(ItemStack stack)
```

#### `getEntriesTagList(ItemStack stack) → NBTTagList`

返回 `plannerEntries` 的原始 TagList 引用。

```java
public static NBTTagList getEntriesTagList(ItemStack stack)
```

#### `getAllEntries(ItemStack stack) → List<PlannerEntry>`

反序列化并返回所有条目列表。返回的是新创建的列表，修改不会影响 NBT 数据。

```java
public static List<PlannerEntry> getAllEntries(ItemStack stack)
```

#### `getEntry(ItemStack stack, int slotIndex) → PlannerEntry`

按 slotIndex 查找单条条目。找不到返回 null。

```java
public static PlannerEntry getEntry(ItemStack stack, int slotIndex)
```

#### `getNextSlotIndex(ItemStack stack) → int`

获取下一个可用的 slotIndex 值。

```java
public static int getNextSlotIndex(ItemStack stack)
```

#### `setEntry(ItemStack stack, int slotIndex, String text, boolean completed) → void`

设置（更新或新增）指定 slotIndex 的条目。如果该 slotIndex 已存在，则更新其 text 和 completed；如果不存在，则新增一条。

```java
public static void setEntry(ItemStack stack, int slotIndex, String text, boolean completed)
```

#### `addEntry(ItemStack stack, String text) → int`

新增一条未完成的条目，自动分配 slotIndex。返回被分配的 slotIndex。

```java
public static int addEntry(ItemStack stack, String text)
```

#### `removeEntry(ItemStack stack, int slotIndex) → void`

按 slotIndex 删除条目。注意：不会回收 slotIndex，nextSlotIndex 保持不变。

```java
public static void removeEntry(ItemStack stack, int slotIndex)
```

#### `toggleCompleted(ItemStack stack, int slotIndex) → void`

翻转指定条目的完成状态。

```java
public static void toggleCompleted(ItemStack stack, int slotIndex)
```

#### `clearAllEntries(ItemStack stack) → void`

清空所有条目并将 nextSlotIndex 重置为 1。

```java
public static void clearAllEntries(ItemStack stack)
```

#### `getEntryCount(ItemStack stack) → int`

返回当前条目总数。

```java
public static int getEntryCount(ItemStack stack)
```

#### `hasEntry(ItemStack stack, int slotIndex) → boolean`

检查指定 slotIndex 的条目是否存在。

```java
public static boolean hasEntry(ItemStack stack, int slotIndex)
```

### 6.2 查询 API

#### `getCompletedCount(ItemStack stack) → int`

返回已完成的条目数量。

```java
public static int getCompletedCount(ItemStack stack)
```

#### `getPendingCount(ItemStack stack) → int`

返回未完成的条目数量。

```java
public static int getPendingCount(ItemStack stack)
```

#### `getEntriesSorted(ItemStack stack, PlannerMergeMode mode) → List<PlannerEntry>`

返回按指定模式排序后的条目列表副本。

```java
public static List<PlannerEntry> getEntriesSorted(ItemStack stack, PlannerMergeMode mode)
```

### 6.3 HUD API

#### `isHudEnabled(ItemStack stack) → boolean`

查询 HUD 显示是否启用。

```java
public static boolean isHudEnabled(ItemStack stack)
```

#### `setHudEnabled(ItemStack stack, boolean enabled) → void`

设置 HUD 显示开关。

```java
public static void setHudEnabled(ItemStack stack, boolean enabled)
```

#### `getHudMaxDisplay(ItemStack stack) → int`

获取 HUD 最大显示条数。如果未设置或为 0，返回默认值 5。

```java
public static int getHudMaxDisplay(ItemStack stack)
```

#### `setHudMaxDisplay(ItemStack stack, int maxDisplay) → void`

设置 HUD 最大显示条数，范围限制在 `[1, 20]`。

```java
public static void setHudMaxDisplay(ItemStack stack, int maxDisplay)
```

### 6.4 排序/移动 API

#### `swapEntries(ItemStack stack, int slotA, int slotB) → void`

交换两个条目的 slotIndex（即在列表中互换位置）。如果任一条目不存在则不执行。

```java
public static void swapEntries(ItemStack stack, int slotA, int slotB)
```

#### `moveEntryToTop(ItemStack stack, int slotIndex) → void`

将指定条目移动到列表首位（slotIndex 设为 1），其余条目顺延重新编号。

```java
public static void moveEntryToTop(ItemStack stack, int slotIndex)
```

### 6.5 整合/合并 API

#### `mergePlanners(ItemStack source1, ItemStack source2, PlannerMergeMode mode) → ItemStack`

合并两个计划器，返回一个新的 ItemStack。原始 ItemStack 不会被修改。如果其中一个为 null，返回另一个的副本。

```java
public static ItemStack mergePlanners(ItemStack source1, ItemStack source2, PlannerMergeMode mode)
```

#### `mergeMultiplePlanners(List<ItemStack> stacks, PlannerMergeMode mode) → ItemStack`

合并多个计划器。从第一个开始依次两两合并。如果列表为空返回 null，只有一个元素返回其副本。

```java
public static ItemStack mergeMultiplePlanners(List<ItemStack> stacks, PlannerMergeMode mode)
```

### 6.6 背包查询 API

#### `getPlannerStacksInInventory(EntityPlayer player) → List<ItemStack>`

扫描玩家背包，返回所有 `ItemAdvancePlanner` 类型的 ItemStack 列表。

```java
public static List<ItemStack> getPlannerStacksInInventory(EntityPlayer player)
```

#### `countPlannersInInventory(EntityPlayer player) → int`

返回玩家背包中的计划器物品数量。

```java
public static int countPlannersInInventory(EntityPlayer player)
```

#### `countPlannerEntriesInInventory(EntityPlayer player) → int`

返回玩家背包中所有计划器的条目总数。

```java
public static int countPlannerEntriesInInventory(EntityPlayer player)
```

---

## 7. GUI 系统

### 7.1 GuiAdvancePlanner

**包路径：** `com.imgood.advancedatamonitor.gui.guiscreen.GuiAdvancePlanner`

继承自 `ADM_GuiScreen`（模组自定义的 GUI 基类）。

#### 核心属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `plannerStack` | `ItemStack` | 构造时传入 | 当前操作的计划器物品栈 |
| `player` | `EntityPlayer` | 构造时传入 | 当前玩家 |
| `entries` | `List<PlannerEntry>` | 每次 refreshEntries() 更新 | 条目缓存 |
| `entryMap` | `Map<Integer, PlannerEntry>` | 每次 refreshEntries() 更新 | slotIndex → 条目的映射 |
| `scrollOffset` | `int` | `0` | 当前滚动偏移（行数） |
| `visibleRows` | `int` | `8` | 可见行数 |
| `totalDisplayRows` | `int` | `50` | 总显示行数（含空行） |
| `rowHeight` | `int` | `20` | 每行高度（像素） |
| `editingField` | `ADM_GuiTextField` | `null` | 当前活动的文本输入框 |
| `editingSlotIndex` | `int` | `-1` | 正在编辑的条目 slotIndex（-1 表示无编辑） |
| `isAddingNew` | `boolean` | `false` | 当前编辑是否为新建条目 |

#### 交互流程

1. **初始化 (`initGui`)**：刷新条目缓存，计算布局坐标，创建"整合"和"退出"按钮。
2. **鼠标点击 (`mouseClicked`)**：
   - 点击复选框区域 → `toggleCompleted()`
   - 点击文本区域 → `startEditing()` 创建文本输入框和确认/取消按钮
3. **键盘输入 (`keyTyped`)**：
   - Enter → `commitEdit()` 提交编辑
   - Esc → `cancelEdit()` 取消编辑
   - 其他键 → 传递给文本输入框
4. **滚轮 (`handleMouseInput`)**：调整 `scrollOffset` 实现翻页。
5. **渲染 (`drawScreen`)**：绘制标题、统计信息、列表区域（含复选框、文本、时间戳、滚动条）、悬浮提示。
6. **关闭 (`onGuiClosed`)**：自动提交未保存的编辑，禁用键盘重复事件。

#### 编辑状态机

```
空闲状态
  │ 点击文本区域
  ▼
编辑状态 (editingSlotIndex >= 0, editingField != null)
  │
  ├─ Enter / 点击"添加" → commitEdit() → 保存文本 → 返回空闲
  ├─ Esc / 点击"X"      → cancelEdit() → 丢弃修改 → 返回空闲
  ├─ 点击其他行文本      → commitEdit() 当前行 → startEditing() 新行
  ├─ 点击其他行复选框    → commitEdit() 当前行 → toggleCompleted()
  └─ 点击"整合"/"退出"  → commitEdit() 当前行 → 跳转
```

#### 颜色方案

| 用途 | 色值 | 说明 |
|------|------|------|
| 文本默认 | `0x00FFFF` | 青色 |
| 文本悬停 | `0x0055FF` | 蓝色 |
| 已完成文本 | `0x55FF55` | 绿色 |
| 未完成文本 | `0xFFFFFF` | 白色 |
| 序号/统计 | `0x888888` | 灰色 |
| 时间戳 | `0x666666` | 深灰 |
| 确认按钮 | `0x00FF00` / `0x55FF55` | 绿色 |
| 取消按钮 | `0xFF5555` / `0xFF0000` | 红色 |

### 7.2 GuiPlannerMergeConfirm

**包路径：** `com.imgood.advancedatamonitor.gui.guiscreen.GuiPlannerMergeConfirm`

#### 核心属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `currentStack` | `ItemStack` | 当前打开的计划器 |
| `player` | `EntityPlayer` | 当前玩家 |
| `plannerStacks` | `List<ItemStack>` | 背包中所有计划器（initGui 时扫描） |
| `totalPlanners` | `int` | 计划器数量 |
| `totalEntries` | `int` | 所有计划器的总条目数 |
| `selectedMode` | `PlannerMergeMode` | 当前选中的合并模式（默认 BY_TIME） |

#### 按钮 ID

| ID | 常量名 | 功能 |
|----|--------|------|
| 200 | `buttonTimeId` | 选择"按时间合并"模式 |
| 201 | `buttonIndexId` | 选择"按序号合并"模式 |
| 202 | `buttonConfirmId` | 确认执行合并 |
| 203 | `buttonCancelId` | 取消，返回主 GUI |

#### 合并执行流程 (`executeMerge`)

1. 检查 `totalPlanners >= 2`，否则直接返回。
2. 调用 `ItemAdvancePlanner.mergeMultiplePlanners()` 生成合并结果。
3. 遍历背包，移除**非当前**的计划器物品（`setInventorySlotContents(i, null)`）。
4. 将当前槽位的物品替换为合并结果。
5. 调用 `detectAndSendChanges()` 同步到服务端。
6. 跳转回 `GuiAdvancePlanner`，传入合并后的 ItemStack。

---

## 8. 注册流程

**文件：** `LoaderItem.java`

```java
public class LoaderItem {
    public static Item advancePlanner;

    public static void registerItems() {
        // 1. 实例化物品，设置内部名和贴图路径
        advancePlanner = new ItemAdvancePlanner()
            .setUnlocalizedName("advancePlanner")
            .setTextureName("advancedatamonitor:advance_planner");

        // 2. 注册到 Forge 游戏注册表
        GameRegistry.registerItem(advancePlanner, "advance_planner");
    }
}
```

**注册链路：**

1. `ItemAdvancePlanner` 构造函数设置：
   - `setMaxStackSize(1)` — 不可堆叠
   - `setCreativeTab(CreativeTabs.tabTools)` — 创造模式"工具"标签页
2. `LoaderItem.registerItems()` 设置：
   - `setUnlocalizedName("advancePlanner")` — 用于语言文件查找 `item.advancePlanner.name`
   - `setTextureName("advancedatamonitor:advance_planner")` — 对应 `textures/items/advance_planner.png`
3. `GameRegistry.registerItem()` 完成 Forge 注册。

**访问已注册物品：**

```java
ItemStack plannerStack = new ItemStack(LoaderItem.advancePlanner);
```

---

## 9. 调用示例

### 9.1 创建计划器并添加条目

```java
import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;
import com.imgood.advancedatamonitor.loader.LoaderItem;

// 创建一个新的计划器 ItemStack
ItemStack planner = new ItemStack(LoaderItem.advancePlanner);

// 添加条目
int slot1 = ItemAdvancePlanner.addEntry(planner, "采集 64 个铁锭");
int slot2 = ItemAdvancePlanner.addEntry(planner, "建造仓库");
int slot3 = ItemAdvancePlanner.addEntry(planner, "制作钻石镐");

// 标记第一条为已完成
ItemAdvancePlanner.toggleCompleted(planner, slot1);
```

### 9.2 读取计划器数据

```java
// 获取所有条目
List<PlannerEntry> entries = ItemAdvancePlanner.getAllEntries(planner);
for (PlannerEntry entry : entries) {
    System.out.println(String.format(
        "[%s] #%d: %s",
        entry.isCompleted() ? "✓" : " ",
        entry.getSlotIndex(),
        entry.getText()
    ));
}

// 获取统计
int total = ItemAdvancePlanner.getEntryCount(planner);
int done = ItemAdvancePlanner.getCompletedCount(planner);
int pending = ItemAdvancePlanner.getPendingCount(planner);
```

### 9.3 从玩家背包查找计划器

```java
import net.minecraft.entity.player.EntityPlayer;

EntityPlayer player = /* 获取玩家对象 */;

// 获取背包中所有计划器
List<ItemStack> planners = ItemAdvancePlanner.getPlannerStacksInInventory(player);
System.out.println("背包中有 " + planners.size() + " 个计划器");

// 获取背包中所有计划器的条目总数
int totalEntries = ItemAdvancePlanner.countPlannerEntriesInInventory(player);
```

### 9.4 合并多个计划器

```java
import com.imgood.advancedatamonitor.items.PlannerMergeMode;

List<ItemStack> planners = ItemAdvancePlanner.getPlannerStacksInInventory(player);

if (planners.size() >= 2) {
    // 按时间合并
    ItemStack merged = ItemAdvancePlanner.mergeMultiplePlanners(
        planners, PlannerMergeMode.BY_TIME);

    // merged 是一个全新的 ItemStack，包含所有条目
    System.out.println("合并后条目数: " + ItemAdvancePlanner.getEntryCount(merged));
}
```

### 9.5 HUD 控制

```java
ItemStack planner = /* 获取计划器 */;

// 查询 HUD 状态
boolean hudOn = ItemAdvancePlanner.isHudEnabled(planner);

// 开启 HUD
ItemAdvancePlanner.setHudEnabled(planner, true);

// 设置 HUD 最大显示 10 条
ItemAdvancePlanner.setHudMaxDisplay(planner, 10);
```

### 9.6 排序与移动

```java
// 按时间排序获取条目
List<PlannerEntry> byTime = ItemAdvancePlanner.getEntriesSorted(
    planner, PlannerMergeMode.BY_TIME);

// 交换两个条目的位置
ItemAdvancePlanner.swapEntries(planner, 1, 3);

// 将第 5 条移到最前面
ItemAdvancePlanner.moveEntryToTop(planner, 5);
```

### 9.7 编程式操作计划器（非 GUI 场景）

```java
// 直接通过 NBT 修改条目（不经过 GUI）
ItemAdvancePlanner.setEntry(planner, 99, "通过代码添加的条目", false);

// 检查条目是否存在
boolean exists = ItemAdvancePlanner.hasEntry(planner, 99); // true

// 删除条目
ItemAdvancePlanner.removeEntry(planner, 99);

// 清空所有
ItemAdvancePlanner.clearAllEntries(planner);
```

---

## 10. 扩展指南

### 10.1 添加新的条目字段

如果需要为每条条目添加新属性（如优先级、分类标签等）：

1. **修改 `PlannerEntry`**：
   - 添加新字段（如 `private int priority;`）
   - 更新所有构造方法
   - 更新 `toNBT()` 和 `fromNBT()` 方法
   - 更新 `copy()` 方法
   - 添加 getter/setter

2. **考虑向后兼容**：在 `fromNBT()` 中使用 `tag.hasKey("priority")` 检查，为旧数据提供默认值。

3. **更新 GUI**（如果需要展示）：
   - 修改 `GuiAdvancePlanner.drawListArea()` 中的渲染逻辑
   - 可能需要添加新的交互元素

### 10.2 添加新的合并模式

1. **扩展 `PlannerMergeMode` 枚举**：添加新值（如 `BY_PRIORITY`）。

2. **在 `ItemAdvancePlanner` 中实现合并逻辑**：
   - 添加新的 `private static List<PlannerEntry> mergeByXxx(...)` 方法
   - 在 `mergePlanners()` 的 switch 语句中添加新 case
   - 在 `getEntriesSorted()` 的 switch 语句中添加新 case

3. **更新 `GuiPlannerMergeConfirm`**：
   - 添加新按钮（参考现有按钮的创建方式）
   - 更新 `buttonId` 常量
   - 在 `actionPerformed()` 中处理新按钮
   - 在 `drawScreen()` 中高亮当前选中模式

4. **添加语言文件条目**。

### 10.3 添加 HUD 渲染

目前 HUD 相关的 API（`isHudEnabled`、`setHudEnabled`、`getHudMaxDisplay`、`setHudMaxDisplay`）已就位，但渲染逻辑需要在 HUD 渲染事件中实现：

```java
// 在客户端事件处理器中
@SubscribeEvent
public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
    if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;

    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    ItemStack heldItem = player.getHeldItem();

    if (heldItem != null && heldItem.getItem() instanceof ItemAdvancePlanner
        && ItemAdvancePlanner.isHudEnabled(heldItem)) {

        int maxDisplay = ItemAdvancePlanner.getHudMaxDisplay(heldItem);
        List<PlannerEntry> entries = ItemAdvancePlanner.getAllEntries(heldItem);

        // 渲染逻辑：在屏幕上绘制待办列表
        int y = 10;
        int count = 0;
        for (PlannerEntry entry : entries) {
            if (count >= maxDisplay) break;
            String prefix = entry.isCompleted() ? "[x] " : "[ ] ";
            // 使用 FontRenderer 绘制
            // fontRenderer.drawString(prefix + entry.getText(), 10, y, 0xFFFFFF);
            y += 12;
            count++;
        }
    }
}
```

### 10.4 添加新的交互操作

如果需要添加拖拽排序、批量删除等功能：

1. 在 `GuiAdvancePlanner` 中扩展鼠标事件处理（`mouseClicked`、`mouseClickMove` 等）。
2. 使用 `ItemAdvancePlanner` 的现有 API（如 `swapEntries`、`removeEntry`）实现数据操作。
3. 在 `commitEdit()` / `cancelEdit()` 后调用 `refreshEntries()` 确保缓存同步。

### 10.5 注意事项

- **线程安全：** 所有 NBT 操作都在主线程执行，GUI 在客户端线程。如果从其他线程调用 API，需要注意同步。
- **null 安全：** `getOrCreatePlannerNBT(null)` 返回空 TagCompound 但不会修改 null 的 stack。调用方应确保 stack 非 null。
- **NBT 大小：** Minecraft 的 NBT 没有硬性大小限制，但过大的数据会影响网络同步性能。建议单个计划器的条目控制在合理范围内。
- **slotIndex 不回收：** 删除条目后 slotIndex 不会被重用，这是设计决定，确保外部引用不会意外指向错误的条目。

---

## 11. 语言文件格式

### 11.1 文件位置

- 中文：`src/main/resources/assets/advancedatamonitor/lang/zh_CN.lang`
- 英文：`src/main/resources/assets/advancedatamonitor/lang/en_US.lang`

### 11.2 格式规范

```
key=value
```

- 每行一个键值对，以 `=` 分隔
- `#` 开头的行是注释
- `§` 是 Minecraft 的格式化代码前缀（如 `§l` 表示粗体，`§n` 表示下划线，`§m` 表示删除线）
- `%s`、`%d` 等是 Java `String.format` 占位符，运行时会被实际值替换

### 11.3 高级计划器相关语言条目

```properties
# 物品名称（对应 setUnlocalizedName 设置的 key: item.advancePlanner.name）
item.advancePlanner.name=高级计划器          # zh_CN
item.advancePlanner.name=Advance Planner     # en_US

# GUI 标题
adm.planner.title=§l高级计划器               # zh_CN
adm.planner.title=§lAdvance Planner          # en_US

# 按钮文本
adm.planner.add=添加 / Add
adm.planner.delete=删除 / Delete
adm.planner.merge=§l整合 / §lMerge
adm.planner.confirm_merge=§l确认整合 / §lConfirm Merge
adm.planner.cancel=§l取消 / §lCancel
adm.planner.exit=§l退出 / §lExit

# 整合确认界面
adm.planner.merge_confirm_title=§l整合确认 / §lMerge Confirmation
adm.planner.merge_by_time=§l按时间合并 / §lMerge by Time
adm.planner.merge_by_index=§l按序号合并 / §lMerge by Index
adm.planner.merge_prompt=背包中有 %s 个计划器，共 %s 条数据需要合并
                        / Found %s planner(s) in inventory with %s total entries to merge

# 条目状态
adm.planner.empty_slot=点击添加... / Click to add...
adm.planner.completed=已完成 / Completed
adm.planner.pending=未完成 / Pending
adm.planner.mark_complete=标记为已完成 / Mark as completed
adm.planner.mark_incomplete=标记为未完成 / Mark as incomplete

# 统计信息（%s 依次为：总数、已完成数、未完成数）
adm.planner.stats=共 %s 条 | 完成 %s | 未完成 %s
                  / Total %s | Done %s | Pending %s

# 整合确认界面详情
adm.planner.planner_detail=计划器 #%s: %s 条数据
                          / Planner #%s: %s entries
adm.planner.current=(当前) / (current)
adm.planner.selected_mode=合并模式: / Selected mode:
adm.planner.merge_tooltip=合并背包中所有计划器的数据
                         / Merge all planner items in inventory

# HUD 相关（注意：当前语言文件中尚未定义，需补充）
# adm.planner.hud_enabled=HUD 已启用
# adm.planner.hud_disabled=HUD 已关闭
```

### 11.4 新增语言条目的步骤

1. 在 `zh_CN.lang` 中添加中文键值对。
2. 在 `en_US.lang` 中添加对应的英文键值对。
3. 在代码中使用 `I18n.format("adm.planner.xxx")` 或 `net.minecraft.client.resources.I18n.format(...)` 引用。
4. 如需带参数，使用 `%s` 占位符，在 `format()` 调用时传入参数。

---

> 本文档基于 AdvanceDataMonitor 模组源码编写。如代码发生变更，请以实际源码为准。
