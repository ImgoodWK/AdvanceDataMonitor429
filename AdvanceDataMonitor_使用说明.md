# AdvanceDataMonitor 模组详细介绍与使用教程


本文档面向玩家、整合包作者和服务器管理员，介绍 `AdvanceDataMonitor`（高级数据监视器）模组的功能、安装、使用流程、AI 助手、语音助手、配置文件和常见问题。

当前代码目标环境为 Minecraft `1.7.10`，Forge `10.13.4.1614`，项目使用 GTNH Convention Gradle 构建。模组 ID 为 `advancedatamonitor`。

## 1. 模组定位

`AdvanceDataMonitor` 是一个以“世界内可视化数据面板 + AE2 网络监控 + AI 助手”为核心的工具模组。它可以把机器、AE2 网络、AE2 合成 CPU、AE2 存储项目等数据采集出来，在游戏世界中渲染成图表、文字或物品数量展示，并提供一个 AI 对话窗口，让玩家用自然语言查询 AE2 存储、查看样板、提交合成订单、批量下单和管理简单计划。

模组目前主要包含以下能力：

- 高级数据监视器方块：在世界中显示图表、标题、文字、AE2 存储项和合成状态。
- 数据织取器：绑定方块并查看目标 TileEntity 的 NBT，辅助找出可监控字段。
- AE 网络链接方块：接入 AE2 网络，统计物品/流体存储字节与类型数量。
- AE 合成链接方块：接入 AE2 网络，统计合成 CPU 数量、忙碌状态、存储、协处理器和当前任务。
- AE 存储链接方块：接入 AE2 网络，根据专用“高级存储链接元件”的分区配置显示指定物品数量。
- AI 对话与助手：支持 OpenAI-compatible Chat API，既可普通聊天，也可转换自然语言为 AE2 查询、合成操作和取出物品到背包。
- 语音助手：录音后调用 OpenAI-compatible STT 接口转文字，再进入同一 AI 助手流程。
- 命令与配置：提供 `/admai`、`/admassistant` 等命令和 Forge config 文件。

## 2. 环境与安装

### 2.1 运行环境

代码中标明的目标版本如下：

- Minecraft：`1.7.10`
- Minecraft Forge：`10.13.4.1614`
- Mod ID：`advancedatamonitor`
- Mod 名称：`AdvanceDataMonitor`
- Java 运行目标：JVM 8；项目本身通过 Jabel 允许使用部分现代 Java 语法。

### 2.2 依赖说明

从代码使用的 API 可以看出，本模组依赖或面向以下环境：

- Forge 1.7.10。
- Applied Energistics 2（AE2），因为多个 Link 方块继承/使用 AE2 网络 API。
- GTNH 相关开发环境与依赖集合。
- 流体统计部分使用了 ExtraCells/AE Fluid 相关 API（如 `FluidCellInventoryHandler`、`IFluidCellInventory`）。
- 部分 NBT 辅助支持 GregTech TileEntity 名称读取。

如果安装到普通客户端或服务器，建议放在 GTNH 或包含 AE2/GTNH 相关依赖的 1.7.10 整合包中使用。

### 2.3 安装步骤

1. 确认客户端和服务器都安装 Forge 1.7.10。
2. 将编译好的 `AdvanceDataMonitor` jar 放入客户端与服务器的 `mods/` 目录。
3. 确保 AE2、GTNH/相关依赖已经存在。
4. 启动一次游戏或服务器，让 Forge 生成配置文件。
5. 如果要使用 AI 或语音功能，编辑配置文件或在游戏内设置 API Key。

### 2.4 配方说明

当前代码中没有发现通过 `GameRegistry.addRecipe` 或类似方式注册合成配方。也就是说，在当前代码状态下，方块和物品可能需要通过创造模式、NEI/脚本、整合包 Minetweaker/CraftTweaker 配方或服务器管理员发放来获得。

建议整合包作者为以下物品和方块补充配方：

- `advDataMonitor`：高级数据监视器。
- `advNetworkLinkBlock`：高级网络链接。
- `advStorageLink`：高级存储链接。
- `advCraftingLink`：高级合成链接。
- `data_weave`：数据织取器。
- `advance_storage_link_cell`：高级存储链接元件。

## 3. 物品与方块总览

### 3.1 高级数据监视器

注册名：`advancedatamonitor:advDataMonitor`

用途：主显示方块。玩家右键打开主界面，可以添加数据绑定、切换显示方向、隐藏/显示外壳或屏幕、打开 AI 对话界面。数据会保存在方块 TileEntity NBT 中，并同步给客户端渲染。

特点：

- 放置时自动记录自身坐标作为默认绑定目标。
- 默认启用一个“演示模式”显示项，采集 `testRandomData`。
- 支持多个显示项，每个显示项拥有独立坐标、数据字段、图表样式、采样间隔和渲染变换。
- 可渲染折线图、柱状图、3D 柱状图、瀑布流、势差图、合成监控文本和存储物品列表。
- 主界面提供 AI 按钮，用于打开 AI 对话。

### 3.2 数据织取器

注册名：`advancedatamonitor:data_weave`

用途：辅助绑定和查看目标方块 NBT，帮助玩家找到可监控字段名。

使用方法：

- 潜行右键方块：绑定该方块，保存坐标、方块 ID、meta 和 TileEntity NBT。
- 普通右键空气：如果已绑定 TileEntity，打开 NBT 查看界面。
- 潜行右键空气：清除该物品上的绑定数据。

注意事项：

- 不能绑定高级数据监视器自身。
- 绑定的是当时的 TileEntity NBT 快照，不一定实时刷新。
- 如果目标方块没有 TileEntity 或没有 NBT，打开查看器时会提示无数据。

### 3.3 高级网络链接

注册名：`advancedatamonitor:advNetworkLinkBlock`

用途：接入 AE2 网络，统计整个 AE 网络中物品/流体存储单元的容量与使用情况。

特点：

- 是 AE 网络 TileEntity，会请求 AE2 频道。
- 使用 Smart Cable 连接类型。
- 每 20 tick 自动刷新一次，也会响应 AE 存储事件刷新。
- 右键方块会在聊天栏显示当前 AE2 网络状态。

可采集指标：

- `ItemTotalBytes`：物品存储总字节。
- `ItemUsedBytes`：物品存储已用字节。
- `ItemTotalTypes`：物品存储总类型数。
- `ItemUsedTypes`：物品存储已用类型数。
- `FluidTotalBytes`：流体存储总字节。
- `FluidUsedBytes`：流体存储已用字节。
- `FluidTotalTypes`：流体存储总类型数。
- `FluidUsedTypes`：流体存储已用类型数。

在高级数据监视器中绑定该方块后，可以选择显示数值或百分比。

### 3.4 高级合成链接

注册名：`advancedatamonitor:advCraftingLink`

用途：接入 AE2 网络，统计合成 CPU 状态，并为主监视器和 AI 助手提供 AE2 自动合成相关能力。

特点：

- 是 AE 网络 TileEntity，会请求 AE2 频道。
- 每 20 tick 自动刷新一次，也会响应 AE 合成 CPU 事件刷新。
- 右键方块会在聊天栏输出当前合成 CPU 统计。
- AI 助手查找附近 `32` 格内的该方块来执行合成候选、提交合成、取消合成等动作。

全局指标：

- `totalCpus`：网络中合成 CPU 数量。
- `busyCpus`：忙碌 CPU 数量。
- `cpuTotalBytes`：合成 CPU 总存储。
- `cpuUsedBytes`：合成 CPU 已用存储。
- `totalCoProcessors`：协处理器总数。

单 CPU 指标支持按 CPU 名称或序号查询，常用字段包括：

- `busyCpus:CPU#1`：指定 CPU 是否忙碌，返回 1 或 0。
- `usedStorage:CPU#1`：指定 CPU 已用存储。
- `availableStorage:CPU#1`：指定 CPU 可用存储。
- `coProcessors:CPU#1`：指定 CPU 协处理器数量。
- `finalOutputName:CPU#1`：当前任务最终产物名称。
- `finalOutputAmount:CPU#1`：当前任务最终产物数量。
- `remainingItems:CPU#1`：剩余物品数。
- `startItems:CPU#1`：开始物品数。
- `elapsedTime:CPU#1`：任务已消耗时间，单位毫秒。

### 3.5 高级存储链接

注册名：`advancedatamonitor:advStorageLink`

用途：接入 AE2 网络，按照放入方块内部的“高级存储链接元件”配置，统计并显示指定物品数量。

特点：

- 是 AE 网络 TileEntity。
- 自带 36 个槽位，只能放入高级存储链接元件。
- 右键打开存储链接 GUI。
- 每个高级存储链接元件通过 AE2 Cell Workbench 的分区槽定义要统计的物品。
- 支持 AE2 模糊卡、反向卡、矿典卡和流体标记。

工作方式：

1. 先准备一个或多个“高级存储链接元件”。
2. 用 AE2 Cell Workbench 编辑元件分区槽，把要显示的物品放入分区配置。
3. 可选：给元件安装 Fuzzy Card，让匹配使用 AE2 模糊模式。
4. 可选：给元件安装 Inverter Card，显示“除了分区物品之外”的网络物品。
5. 可选：给元件安装 Ore Filter Card（矿典卡），按矿典名称（如 `ingotIron`）匹配所有同类物品。
6. 可选：写入流体标记 NBT（`fluidMarkers` 列表），监控 AE2 流体网络中指定流体的存量（单位 mB）。
7. 将配置好的元件放进高级存储链接方块 GUI。
8. 在高级数据监视器中绑定该存储链接，选择存储显示配置。

> 精确匹配说明：未安装模糊卡时，元件按物品精确匹配（`isItemEqual`），避免将 AE2FC 的不同液滴混淆为同一物品。

### 3.6 高级存储链接元件

注册名：`advancedatamonitor:advance_storage_link_cell`

用途：不是实际存储单元，而是高级存储链接的“过滤/标记配置单元”。

特点：

- 最大堆叠 1。
- 实现 AE2 `ICellWorkbenchItem`，可以放入 AE2 Cell Workbench 编辑配置。
- 有 2 个升级槽。
- 允许安装 AE2 Fuzzy Card、Inverter Card 和 Ore Filter Card。
- 分区配置槽中的物品决定高级存储链接显示哪些物品。
- 支持 NBT 流体标记（`fluidMarkers`），可存储流体目标以监控 AE2 流体存量。

## 4. 高级数据监视器使用教程

### 4.1 基础搭建

1. 放置一个高级数据监视器。
2. 右键打开主界面。
3. 点击“新增绑定”。
4. 输入目标方块坐标 `x,y,z`，保存。
5. 界面会根据目标方块类型打开不同配置页面：普通 TileEntity、高级网络链接、高级合成链接或高级存储链接。
6. 设置标题、显示类型、偏移、旋转、缩放、采样间隔等参数。
7. 点击保存后返回主界面，世界中的监视器会开始显示数据。

### 4.2 主界面按钮

- 方向按钮：循环切换显示朝向，支持南/西/北/东。
- 新增绑定：输入坐标并新增一个显示项。
- 添加：新增一个通用显示项入口。
- 隐藏/显示：切换部分显示组件可见性。
- 单面/双面：切换显示面。
- AI：打开 AI 对话窗口。
- 数据项小按钮：每个已有显示项会有对应按钮，可进入该项配置。
- 颜色配置：部分数据项可进入颜色/图表类型配置。

### 4.3 坐标绑定

坐标格式必须是英文逗号分隔的整数：

```text
123,64,-123
```

常见错误包括使用中文逗号、目标方块不存在、目标方块不是 TileEntity。

### 4.4 通用图表配置

通用图表用于监控普通 TileEntity NBT 数值，或一些 AE 方块数值。核心字段如下：

- `XYZ`：目标方块坐标。
- `name`：要读取的 NBT 字段名或 AE 网络指标名。
- `displayName`：图表标题。
- `dataType`：显示类型。
- `dataLimit`：保留/显示的数据点数量。
- `interval`：采样间隔，单位 tick。20 tick 约等于 1 秒。
- `xRange`：X 轴显示长度。
- `yRange`：Y 轴显示高度/范围。
- `yMin`、`yMax`：Y 轴上下限。
- `isValue`：对部分 AE 网络指标可切换为百分比含义。

图表类型：`line` 折线图、`bar` 柱状图、`bar3d` 3D 柱状图、`waterfall` 瀑布流、`diffrence` 势差图、`crafting` 合成监控文本、`storage` 存储物品数量展示。

### 4.5 显示位置、旋转和缩放

每个显示项都有独立的空间变换：

- `xOffset`、`yOffset`、`zOffset`：相对监视器的位置偏移，单位约为方块。
- `rotationX`、`rotationY`、`rotationZ`：三轴旋转角度。
- `scale`：整体缩放。

如果图表看不见，先把 `scale` 调大，例如 `0.5` 或 `1.0`；如果图表嵌进方块，调整 `zOffset` 或 `yOffset`；如果方向不对，先切换主界面方向，再微调 `rotationY`。

### 4.6 颜色和透明度

颜色字段使用 16 进制 RGB，不需要 `#`，例如：

```text
FFFFFF
00FFFF
FF00FF
```

常见字段：`lineColor`、`axisLineColor`、`axisFontColor`、`displayNameColor`、`gridLineColor`。

透明度字段范围通常是 `0.0` 到 `1.0`，例如 `nameAlpha`、`axisLineAlpha`、`axisFontAlpha`、`lineAlpha`、`gridLineAlpha`。

### 4.7 采样间隔与性能

`interval` 是每个显示项的采样间隔，单位 tick。值越小刷新越频繁，服务器和客户端负担越高。

建议快速变化数据用 `20`，普通机器状态用 `40` 到 `100`，长期趋势提高 `interval` 并适当提高 `dataLimit`。不要在服务器上让大量显示项全部使用 `interval=1`。

## 5. AE2 网络监控教程

1. 将高级网络链接方块连接到 AE2 网络。
2. 确保 AE2 网络有可用频道。
3. 右键高级网络链接，聊天栏应显示网络容量和类型数。
4. 在高级数据监视器中新增绑定，坐标填高级网络链接方块坐标。
5. 在 AE 网络数据配置界面选择要显示的指标。
6. 保存后，监视器会以图表形式显示网络变化。

常用示例：

- 物品存储已用百分比：数据名 `ItemUsedBytes`，显示模式选百分比，图表类型选折线图。
- 流体类型占用：数据名 `FluidUsedTypes`，显示模式选数值或百分比。

## 6. AE2 合成监控教程

### 6.1 搭建合成链接

1. 将高级合成链接方块接入 AE2 网络。
2. 确保 AE2 网络有可用频道。
3. 右键高级合成链接，聊天栏应显示 CPU 统计。
4. 在高级数据监视器中新增绑定，坐标填高级合成链接方块坐标。
5. 进入合成处理器配置界面。
6. 选择“全网络”或“处理器”模式。
7. 设置文字缩放、透明度、对齐、模板等内容。
8. 保存后显示合成 CPU 状态。

### 6.2 全网络模式

全网络模式直接显示高级合成链接的整体统计文本，适合做总览看板。

### 6.3 单处理器/模板模式

模板中支持 `{br}` 换行、`{变量}` 插入值、`{变量:CPU名称}` 读取指定 CPU、`{表达式 ? "成立时显示" : "不成立时显示"}` 条件显示。

示例：

```text
{br}合成监控{br}总数：{totalCpus} 忙碌：{busyCpus}{br}{busyCpus:CPU#1 == 1 ? "§cCPU#1 正在工作" : "§aCPU#1 空闲"} 已用存储：{usedStorage:CPU#1}
```

CPU 名称必须和 AE2 网络中 CPU 的名称完全一致，否则显示可能为 `??` 或空值。

## 7. AE2 存储链接教程

### 7.1 配置高级存储链接元件

1. 打开 AE2 Cell Workbench。
2. 将高级存储链接元件放入工作台。
3. 在分区/配置槽放入要监控的物品，例如铱锭、锡锭、处理器等。
4. 可选：安装 Fuzzy Card，使匹配使用 AE2 模糊模式（损坏值/百分比容差）。
5. 可选：安装 Inverter Card，让该元件显示“除配置项外”的其他网络物品。
6. 可选：安装 Ore Filter Card，在元件工作台中设置矿典名称（如 `ingotIron`），元件将匹配所有同类物品。
7. 可选：写入流体标记 NBT（`fluidMarkers`），监控 AE2 流体网络中指定流体的存量（单位 mB）。
8. 取出元件。

### 7.2 配置高级存储链接方块

1. 将高级存储链接方块接入 AE2 网络。
2. 右键打开 GUI。
3. 将配置好的高级存储链接元件放入 36 个槽位之一。
4. 方块会根据每个元件的分区配置统计 AE 网络中对应物品数量。

### 7.3 在监视器上显示存储项

1. 右键高级数据监视器。
2. 点击“新增绑定”。
3. 输入高级存储链接方块坐标。
4. 进入存储链接显示配置。
5. 设置标题、位置、旋转、缩放。
6. 设置 `storageColumns`、`storageSpacing`、`storageIconScale` 和数量颜色。
7. 可选择开启/关闭 数量、进出（吞吐量变化）、名称 三种文字显示，先启用的会排在上排。
8. 保存后，监视器会显示元件配置的物品图标与数量。

## 8. AI 对话与助手

### 8.1 功能概览

AI 窗口既支持普通聊天，也支持“助手工具流”。玩家在高级数据监视器主界面点击 `AI` 按钮即可打开。

AI 助手可以查询 AE2 存储、查询 AE2 可合成样板和配方详情、提交 AE2 合成订单、批量提交合成订单、按序号确认候选项、取消当前助手操作或服务端合成任务、创建/列出/完成简单计划、将 AE2 存储中的物品取出到玩家背包以及普通聊天。

### 8.2 AI 可用条件

AI 结构化助手主路径可用需要满足：

- `ai.networkEnabled = true`
- 已配置 `ai.apiKey`，或环境变量 `DEEPSEEK_API_KEY` 不为空。

如果 AI 不可用，助手会退回本地规则解析器。规则解析器能处理部分中文/英文命令，但自然语言理解能力弱于 AI。

### 8.3 隐私提示

AI 对话会把你的输入和最近聊天历史发送给配置的模型供应商。首次使用时需要确认隐私提示。确认状态保存在 `ai.privacyConfirmed=true`。语音助手也有独立的 `voice.privacyConfirmed=true`。

### 8.4 查询 AE2 存储

前提：玩家附近 32 格内需要有高级存储链接方块。

示例：

```text
查一下 AE 里有多少锡锭
看看流体存储里有多少熔融焊锡
查询所有存储里有没有铱锭
```

存储范围包括：

- `all`：物品和流体都查。
- `items`：只查物品。
- `fluids`：只查流体。

AI 会尽量从自然语言中判断范围，例如“流体”“液体”倾向 `fluids`，“物品”“材料”倾向 `items`。

### 8.5 查询可合成样板

前提：玩家附近 32 格内需要有高级合成链接方块，且该方块接入有样板的 AE2 网络。

示例：

```text
查询铱板怎么合成
列出 AE 里能合成的处理器
查看第 2 个样板详情
```

如果查询目标为空或很宽泛，助手会返回一组可合成候选项。玩家输入序号后，会查看该候选项的配方详情。

### 8.6 提交合成订单

示例：

```text
帮我合成 64 个锡齿轮
下单 4 个量子处理器
```

流程：

1. 助手搜索 AE2 合成候选项。
2. 如果有多个候选项，会显示序号列表。
3. 玩家输入序号确认。
4. 服务端提交 AE2 合成任务。

单行订单数量受 `assistant.maxOrderAmount` 限制，默认 `4096`。

### 8.7 批量下单

示例：

```text
帮我合成 64 个锡齿轮，32 个铜线，4 个量子处理器
```

确认示例：

```text
确认
继续下单
```

追加示例：

```text
再加 16 个钢板
```

合并示例：

```text
把刚刚的加起来一起下单
```

限制：

- 单次 AI 结构化解析最多处理 8 个任务。
- 批量提交要求每一行都有可用候选项。
- 批量提交前会检查可用 AE2 计算槽数量，如果可用槽少于订单行数，则不会提交任何任务。
- 单行数量仍受 `assistant.maxOrderAmount` 限制。

### 8.8 取出 AE2 物品到背包

不同于"合成下单"（让 AE2 制作新物品），"取出"功能直接从 AE2 存储中转移已有的物品到玩家背包。

前提：玩家附近 32 格内需要有高级存储链接方块。

示例：

```text
取出 64 个锡锭到背包
从 AE 里拿 32 个铜板
给我 16 个铱锭
```

流程：

1. 助手搜索 AE2 存储网络中的匹配物品。
2. 如果有多个候选项，会显示序号列表。
3. 玩家输入序号确认。
4. 服务端从 AE2 存储中将物品取出并转入玩家背包。

单次取出数量受 `assistant.maxWithdrawAmount` 限制，默认 `4096`。

**部分取出确认**：当玩家背包空间不足以容纳请求的全部数量时，助手会提示"背包最多还能放 X 个，是否取出 X 个？"。玩家输入"确认"后，助手会取出背包能容纳的数量。

### 8.9 批量取出物品

示例：

```text
取出 64 个锡锭，32 个铜板，16 个铱锭
从 AE 仓库给我拿 64 个红石和 32 个萤石粉
```

流程与批量合成下单类似：

1. AI 结构化解析为多个取出任务。
2. 服务端逐一搜索候选项。
3. 玩家确认后，服务端逐行执行取出操作。

批量取出的限制：

- 单次 AI 解析最多 8 个任务。
- 某一行背包空间不足时，批量操作会暂停并提示玩家先单项确认。
- 某一行失败时，后续行不会继续取出。
- 单行数量受 `assistant.maxWithdrawAmount` 限制。

追加和合并操作（"再加 X 个""把刚刚的合并取出"）同样支持。

### 8.10 取消操作

输入 `取消` 会清空客户端待确认候选项，并向服务端发送取消助手合成任务请求。普通 AI 聊天 HTTP 请求的取消按钮只取消当前聊天请求，不等同于取消 AE2 合成任务。

### 8.11 计划功能

示例：

```text
创建计划 明天检查铱生产线
列出计划
完成第 1 个计划
```

该计划功能是轻量级助手功能，不是复杂任务系统。

## 9. 语音助手

语音助手使用本地录音，把音频编码为 WAV 后发送到 OpenAI-compatible `/v1/audio/transcriptions` 接口。转写出的文字会提交到 AI 窗口，并复用同一助手流程。

需要设置：

```text
voice.enabled=true
```

STT API Key 来源优先级：

1. `voice.sttApiKey`
2. 环境变量 `VOICE_STT_API_KEY`
3. `ai.apiKey`
4. 环境变量 `DEEPSEEK_API_KEY`

STT 模式由 `voice.sttMode` 控制：

- `embedded-vosk`：默认离线模式。Windows 64-bit Java 下直接使用模组内置 Vosk 中文模型，不需要 Python、本地服务或 API Key。
- `http`：OpenAI-compatible `/v1/audio/transcriptions` 模式，适合接 Whisper 服务或云端 STT。

HTTP 模式下，STT Base URL 来源：`voice.sttBaseUrl`，为空则使用 `ai.apiBaseUrl`。HTTP 默认 STT 模型为 `whisper-1`。

默认语音推荐使用内置 Vosk 模式。它会把随模组携带的中文小模型解包到配置目录，不需要玩家安装 Python 或启动额外服务。

```text
voice {
    B:enabled=true
    B:privacyConfirmed=true
    S:sttMode=embedded-vosk
    S:sttBaseUrl=
    S:sttApiKey=
    S:sttModel=zh-small
    I:sttTimeoutSeconds=60
}
```

如果使用带 voice 的发布包，`zh-small` 模型会随模组自动解包到配置目录；如果使用普通包，也可以把 `voice.sttModel` 设置为本地 Vosk 模型目录。

如果要改用 HTTP/Whisper 模式，可以设置：

```text
voice {
    B:enabled=true
    B:privacyConfirmed=true
    S:sttMode=http
    S:sttBaseUrl=http://127.0.0.1:8000
    S:sttApiKey=
    S:sttModel=whisper-1
    I:sttTimeoutSeconds=120
}
```


使用流程：

1. 在 AI 设置界面或配置文件中启用语音助手。
2. 确认语音隐私提示。
3. 使用客户端注册的语音快捷键开始录音。
4. 停止录音后等待 STT 返回文字。
5. 如果当前没有打开 AI 界面，客户端会打开 AI 窗口并提交转写文本。
6. 后续流程与文本助手相同。

注意：语音会发送给配置的 STT 服务商，请勿朗读敏感信息。

## 10. 命令

### 10.1 `/admai`

别名：`/adm-ai`、`/aicfg`。

常用命令：

```text
/admai status
/admai key sk-xxxx
/admai clearKey
/admai model deepseek-chat
/admai base https://api.deepseek.com
/admai provider openrouter
/admai network on
/admai network off
/admai network toggle
/admai search on
/admai search off
/admai search auto
/admai search openrouter
```

支持的供应商 ID 包括 `deepseek`、`openai`、`openrouter`、`dashscope`、`zhipu`、`kimi`、`volcengine`、`siliconflow`、`minimax`、`groq`、`mistral`、`gemini`、`anthropic`。

### 10.2 `/admassistant`

别名：`/adm-assistant`、`/admast`。

```text
/admassistant lexicon
/admassistant reloadLexicon
```

`reloadLexicon` 需要权限等级 2。

## 11. 配置文件详解

### 11.1 配置文件位置

Forge 通常会生成：

```text
.minecraft/config/advancedatamonitor.cfg
```

服务器则位于服务端实例的：

```text
config/advancedatamonitor.cfg
```

具体文件名由 Forge 的 `event.getSuggestedConfigurationFile()` 决定。

### 11.2 `general` 分类

```text
general {
    S:greeting=Hello World
}
```

`greeting` 是启动日志中的问候文本，对游戏功能基本无影响。

### 11.3 `ai` 分类

```text
ai {
    S:apiBaseUrl=https://api.deepseek.com
    S:apiKey=
    S:model=deepseek-chat
    B:networkEnabled=true
    B:webSearchEnabled=false
    S:webSearchMode=auto
    B:debugLogging=false
    B:streamingEnabled=false
    B:privacyConfirmed=false
    S:recentModels=
    I:timeoutSeconds=60
    I:maxTokens=1024
    D:temperature=0.7
}
```

字段说明：

- `apiBaseUrl`：OpenAI-compatible Chat API 地址。默认 `https://api.deepseek.com`。
- `apiKey`：Chat API Key，也可用环境变量 `DEEPSEEK_API_KEY`。
- `model`：模型名，默认 `deepseek-chat`。
- `networkEnabled`：是否允许 AI 发送网络请求。关闭后普通 AI 和 AI intent 主路径不可用。
- `webSearchEnabled`：是否请求供应商网页搜索能力。
- `webSearchMode`：可选 `auto`、`openai`、`openrouter`、`dashscope`、`zhipu`、`generic-tools`、`off`。
- `debugLogging`：是否写入脱敏后的 AI 请求诊断日志。
- `streamingEnabled`：普通聊天是否使用流式响应。AI intent 解析会强制使用非流式。
- `privacyConfirmed`：是否已确认 AI 隐私提示。
- `recentModels`：最近使用模型列表，逗号分隔，最多保留 8 个。
- `timeoutSeconds`：HTTP 超时秒数，范围 `5` 到 `300`。
- `maxTokens`：模型最大返回 token，范围 `1` 到 `8192`。
- `temperature`：采样温度，范围 `0.0` 到 `2.0`。

### 11.4 `voice` 分类

```text
voice {
    B:enabled=false
    B:privacyConfirmed=false
    S:sttMode=embedded-vosk
    S:sttBaseUrl=
    S:sttApiKey=
    S:sttModel=zh-small
    I:sttTimeoutSeconds=60
}
```

字段说明：

- `enabled`：是否启用语音助手热键和 STT 流程。
- `privacyConfirmed`：是否已确认语音隐私提示。
- `sttMode`：语音识别模式，默认 `embedded-vosk`。可选 `embedded-vosk` 或 `http`。
- `sttBaseUrl`：OpenAI-compatible STT API 地址，仅 `http` 模式使用。为空时使用 `ai.apiBaseUrl`。
- `sttApiKey`：HTTP STT API Key。为空时依次尝试 `VOICE_STT_API_KEY`、`ai.apiKey`、`DEEPSEEK_API_KEY`；`embedded-vosk` 模式不需要。
- `sttModel`：语音转文字模型。`embedded-vosk` 默认 `zh-small`，也可填本地 Vosk 模型目录；`http` 常用 `whisper-1`。
- `sttTimeoutSeconds`：STT HTTP 超时秒数，范围 `5` 到 `300`。

### 11.5 `assistant` 分类

```text
assistant {
    I:maxOrderAmount=4096
    I:maxWithdrawAmount=4096
    I:craftJobTimeoutSeconds=30
    I:maxConcurrentCraftJobs=2
}
```

字段说明：

- `maxOrderAmount`：助手单行合成订单允许的最大数量，默认 `4096`，范围 `1` 到 `1000000`。
- `maxWithdrawAmount`：助手单次取出物品允许的最大数量，默认 `4096`，范围 `1` 到 `1000000`。
- `craftJobTimeoutSeconds`：AE2 合成计算等待超时秒数，默认 `30`，范围 `1` 到 `300`。
- `maxConcurrentCraftJobs`：每名玩家最多同时进行的助手 AE2 合成计算数量，默认 `2`，范围 `1` 到 `16`。

注意：当前保存 AI 设置的代码只显式保存了 `assistant.maxOrderAmount` 和 `assistant.maxWithdrawAmount`，另外两个 assistant 字段会在配置同步读取时生成和读取，但某些界面保存操作可能不会重写它们。服务器管理员建议直接编辑 cfg 并重启或重新加载配置。

### 11.6 推荐配置示例

DeepSeek 普通聊天/助手：

```text
ai {
    S:apiBaseUrl=https://api.deepseek.com
    S:apiKey=你的 key
    S:model=deepseek-chat
    B:networkEnabled=true
    B:webSearchEnabled=false
    S:webSearchMode=off
    B:streamingEnabled=true
    I:timeoutSeconds=60
    I:maxTokens=1024
    D:temperature=0.7
}
```

OpenRouter：

```text
ai {
    S:apiBaseUrl=https://openrouter.ai/api
    S:apiKey=你的 key
    S:model=openai/gpt-4o-mini
    B:networkEnabled=true
    B:webSearchEnabled=true
    S:webSearchMode=openrouter
}
```

语音助手：

```text
voice {
    B:enabled=true
    B:privacyConfirmed=true
    S:sttBaseUrl=https://api.openai.com
    S:sttApiKey=你的 STT key
    S:sttModel=whisper-1
    I:sttTimeoutSeconds=60
}
```

## 12. 助手词表配置

助手词表资源位于：

```text
src/main/resources/assets/advancedatamonitor/config/assistant-lexicon.json
```

运行时可通过命令查看实际配置文件路径：

```text
/admassistant lexicon
```

词表主要用于 AI 不可用时的规则解析 fallback、pending batch 的追加/合并/确认等本地上下文修复、中文/英文关键词识别、时间和数量解析辅助。修改词表后执行 `/admassistant reloadLexicon` 重新加载。

## 13. AI 供应商与搜索模式

内置供应商预设包括 DeepSeek、OpenAI、OpenRouter、DashScope、Zhipu GLM、Kimi、Volcengine、SiliconFlow、MiniMax、Groq、Mistral、Gemini、Anthropic。

搜索模式：

- `auto`：按供应商自动选择默认模式。
- `off`：关闭搜索。
- `openai`：OpenAI 风格搜索请求。
- `openrouter`：OpenRouter 风格搜索请求。
- `dashscope`：通义千问 DashScope 风格搜索请求。
- `zhipu`：智谱风格搜索请求。
- `generic-tools`：通用 tools 格式。

实际能否搜索取决于供应商 API 是否支持对应参数。

## 14. 常见使用场景

### 14.1 做一个 AE 存储占用趋势图

1. 接入高级网络链接到 AE 网络。
2. 在监视器新增绑定，坐标填高级网络链接。
3. 数据名选择 `ItemUsedBytes`。
4. 显示方式选择百分比。
5. 图表类型选择折线图。
6. `interval` 设置为 `100`，约 5 秒刷新一次。
7. `dataLimit` 设置为 `200`，观察较长趋势。

### 14.2 做一个合成 CPU 总览屏

1. 接入高级合成链接。
2. 在监视器新增绑定，坐标填高级合成链接。
3. 选择合成处理器配置。
4. 监控范围选择“全网络”。
5. 调整文字缩放和位置，让文字悬浮在监视器前方。
6. 保存。

### 14.3 做一个关键材料库存墙

1. 给多个高级存储链接元件配置分区槽，例如铱锭、钨钢、锡锭、电路。
2. 把元件放入高级存储链接方块。
3. 在监视器新增绑定，坐标填高级存储链接。
4. 设置 `storageColumns=4`、`storageSpacing=0.45`、`storageIconScale=1.0`。
5. 保存后监视器显示关键材料库存。

### 14.4 用 AI 帮忙下单

1. 确保附近 32 格内有高级合成链接。
2. 打开高级数据监视器的 AI 界面。
3. 输入 `帮我合成 64 个锡齿轮和 32 个铜线`。
4. 等待候选项列表。
5. 如果候选正确，输入 `确认`；如果候选不唯一，输入对应序号。

### 14.5 用 AI 取出 AE2 物品到背包

1. 确保附近 32 格内有高级存储链接。
2. 打开高级数据监视器的 AI 界面。
3. 输入 `取出 64 个锡锭到背包`。
4. 等待候选项列表。
5. 如果候选正确，输入 `确认`；如果候选不唯一，输入对应序号。
6. 物品会自动转入玩家背包。如果背包空间不足，助手会提示可放入的数量，输入"确认"即可取出部分。

## 15. 常见问题排查

### 15.1 监视器没有显示

检查显示项是否启用、主界面是否隐藏了屏幕或本体、`scale` 是否太小、偏移是否不合适、目标坐标是否正确、目标方块是否为 TileEntity、`interval` 是否过大。

### 15.2 图表一直是 0

检查 `name` 字段是否正确；用数据织取器查看目标 TileEntity NBT；如果是 AE 网络链接，确认数据名是否是 `ItemUsedBytes` 等支持字段；确认目标区块已加载。

### 15.3 AE 网络链接没有数据

检查方块是否接入 AE2 网络、网络是否有频道、网络中是否有 Drive 或 Chest 存储单元、右键方块是否能输出状态。

### 15.4 合成链接看不到 CPU

检查方块是否接入和合成 CPU 同一个 AE2 网络、网络是否有频道、网络中是否存在合成 CPU、多方块结构是否有效、CPU 名称是否和模板中写的一致。

### 15.5 存储链接不显示物品

检查高级存储链接是否接入 AE2 网络、元件是否已经在 AE2 Cell Workbench 中配置分区物品、元件是否放入高级存储链接 GUI、分区物品在 AE 网络中是否存在、是否安装了 Inverter Card、监视器绑定坐标是否正确。

### 15.6 AI 提示没有 API Key

使用 `/admai key sk-...` 设置，或编辑配置 `ai.apiKey`，或设置环境变量 `DEEPSEEK_API_KEY`，并确认 `ai.networkEnabled=true`。

### 15.7 AI 联网已关闭

使用 `/admai network on`，或在配置文件中设置 `B:networkEnabled=true`。

### 15.8 AI 能聊天但不能执行 AE2 操作

检查附近 32 格内是否有对应 Link 方块。查询存储需要高级存储链接；查询样板和下单需要高级合成链接；Link 方块必须接入 AE2 网络并有频道。

### 15.9 批量下单失败

常见原因：某一行没有匹配到候选项、数量超过 `assistant.maxOrderAmount`、可用 AE2 合成计算槽不足、玩家附近没有高级合成链接、AE2 合成计算超时。

### 15.10 语音助手不可用

检查 `voice.enabled=true`、已确认语音隐私提示、已配置 STT Key、STT Base URL 支持 `/v1/audio/transcriptions`、麦克风设备可用。

### 15.11 AI 取出物品功能不可用

检查附近 32 格内是否有高级存储链接方块、方块是否接入 AE2 网络并有频道、AE2 网络中是否有可匹配的物品、是否使用了正确的关键词（"取出""拿""给我"等）。

### 15.12 取出物品提示数量是 0 或报错

检查 AE2 网络中的物品库存是否充足、背包是否已满、`assistant.maxWithdrawAmount` 是否太小、AE2 存储链接是否正常工作。

## 16. 服务器管理员建议

- 不建议让大量监视器使用 `interval=1` 高频刷新。
- 对公共服务器，建议限制 `assistant.maxOrderAmount`，避免误下超大订单。
- 根据服务器性能调整 `assistant.maxConcurrentCraftJobs` 和 `assistant.craftJobTimeoutSeconds`。
- 如果启用 AI，建议明确告知玩家输入内容会发送到外部模型供应商。
- 不建议把真实 API Key 写入公开发布的整合包配置。
- 如果使用 AI debug logging，注意虽然代码会尽量写脱敏诊断，但仍应避免长期开启。
- 为本模组补配方时，建议让 AE2 Link 方块成本与其服务器价值相匹配。

## 17. 已知限制

- 当前代码没有内置合成配方注册。
- AE2 查询 Link 搜索半径在助手服务中硬编码为附近 32 格。
- AI intent 单次最多处理 8 个 task。
- AI 输出非法时会整体退回规则 parser，而不是跳过单个错误 task。
- 取出物品需要玩家背包有足够空间；空间不足时会触发部分取出确认流程。
- 批量取出时如果某一行需要部分确认，整个批量操作会暂停。
- 普通聊天历史只保存在客户端内存中，关闭游戏后不会持久化。
- 存储和合成查询结果多数是格式化文本，不是结构化 UI 数据。
- 批量订单合并主要按自然语言 target 字符串，不按物品注册名彻底规范化。
- 高级网络链接统计主要遍历 AE Drive/Chest 中的存储单元，特殊存储结构可能不完全覆盖。
- 高级合成链接受 AE2 API 限制，部分处理样板的非物品输入/输出可能无法完整展示。
- 语音助手依赖外部 STT 服务，不提供离线识别。

## 18. 快速参考

核心方块与物品：高级数据监视器、数据织取器、高级网络链接、高级合成链接、高级存储链接、高级存储链接元件。

核心命令：

```text
/admai status
/admai key <apiKey>
/admai provider <provider>
/admai network on|off|toggle
/admai search on|off|auto|openrouter|openai|dashscope|zhipu|generic-tools
/admassistant lexicon
/admassistant reloadLexicon
```

核心配置：

```text
ai.apiBaseUrl
ai.apiKey
ai.model
ai.networkEnabled
ai.webSearchEnabled
ai.webSearchMode
voice.enabled
voice.sttBaseUrl
voice.sttApiKey
voice.sttModel
assistant.maxOrderAmount
assistant.maxWithdrawAmount
assistant.craftJobTimeoutSeconds
assistant.maxConcurrentCraftJobs
```

常用 AI 输入：

```text
查一下 AE 里有多少锡锭
查询铱板怎么合成
帮我合成 64 个锡齿轮
帮我合成 64 个锡齿轮，32 个铜线，4 个量子处理器
取出 64 个锡锭到背包
从 AE 给我拿 32 个铜板
确认
取消
列出计划
```
