# AdvanceDataMonitor 开发者技术文档

## 项目概述

**AdvanceDataMonitor** 是一个专为 **GTNH（GregTech New Horizons）** 整合包设计的 Minecraft 1.7.10 Forge 模组，为 Applied Energistics 2（AE2）ME 网络提供数据监控和可视化功能。

### 技术栈

| 技术 | 版本/说明 |
|---|---|
| **Minecraft** | 1.7.10 |
| **Forge** | 10.13.4.1614 |
| **Java** | 8+ (支持现代Java语法 via Jabel) |
| **构建系统** | GTNH Convention (RetroFuturaGradle) |
| **构建脚本** | build.gradle.kts |
| **设置脚本** | settings.gradle.kts (gtnhsettingsconvention 1.0.33) |

---

## 项目结构

```
AdvanceDataMonitor429/
├── build.gradle.kts                    # 构建脚本
├── settings.gradle.kts                 # 设置脚本
├── gradle.properties                   # 项目配置
├── dependencies.gradle                 # 依赖配置
├── repositories.gradle                 # 仓库配置
├── gtnhShared/spotless.gradle          # Spotless 配置
├── libs/                               # 本地依赖库
│   ├── Chisel-2.14.1-GTNH-dev.jar
│   ├── Galacticraft-3.3.12-GTNH-dev.jar
│   └── IC2NuclearControl-2.7.8-dev.jar
├── src/main/java/com/imgood/advancedatamonitor/
│   ├── blocks/                         # 方块实现
│   ├── items/                          # 物品实现
│   ├── tileentity/                     # TileEntity 实现
│   ├── gui/                            # GUI 界面
│   │   └── guiscreen/                  # GUI 屏幕实现
│   ├── network/                        # 网络通信
│   │   └── packet/                     # 数据包实现
│   ├── renders/                        # 渲染器
│   ├── utils/                          # 工具类
│   └── loader/                         # 加载器
└── src/main/resources/
    └── assets/advancedatamonitor/
        └── textures/gui/               # GUI 纹理资源
```

---

## 核心架构

### 1. 包结构

```
com.imgood.advancedatamonitor/
├── blocks/                          # 方块定义
│   ├── BlockAdvDataMonitor.java     # 基础数据监控器方块
│   ├── BlockAdvanceDataMonitor.java # 高级数据监控器方块
│   ├── BlockAdvanceCraftingLink.java# 合成链接方块
│   ├── BlockAdvancedatamonitorController.java # 控制器方块
│   └── BlockAdvanceStorageLink.java # 存储链接方块
├── items/                           # 物品定义
│   └── ItemAdvanceStorageLinkCell.java # 存储链接单元物品
├── tileentity/                      # TileEntity 定义
│   ├── TileEntityAdvanceCraftingLink.java # 合成链接 TileEntity
│   └── TileEntityAdvanceStorageLink.java  # 存储链接 TileEntity
├── gui/                             # GUI 界面
│   └── guiscreen/                   # GUI 屏幕实现
│       ├── GuiSubAdvanceCraftingLink.java # 合成链接 GUI
│       ├── GuiSubAEAdvanceCraftingLink.java # AE 合成链接 GUI
│       ├── GuiSubAEAdvanceNetworkLink.java # AE 网络链接 GUI
│       ├── GuiSubBind.java          # 绑定配置 GUI
│       └── GuiSubColorConfig.java   # 颜色配置 GUI
├── network/                         # 网络通信
│   └── packet/                      # 数据包定义
│       ├── PacketItemCountSync.java # 物品数量同步包
│       └── PacketRequestItemCountSync.java # 请求物品数量同步包
├── renders/                         # 渲染器
│   └── CraftingInfoRenderer.java    # 合成信息渲染器
├── utils/                           # 工具类
│   └── CraftingTemplateParser.java  # 合成模板解析器
└── loader/                          # 加载器
    └── LoaderGui.java               # GUI 加载器
```

---

## 详细模块分析

### 1. 方块模块 (blocks)

#### BlockAdvDataMonitor.java
- **功能**：基础数据监控器方块
- **状态**：已修改（M）
- **推测功能**：显示 AE2 网络中的物品数量等基础信息

#### BlockAdvanceDataMonitor.java
- **功能**：高级数据监控器方块
- **状态**：新增（A）
- **推测功能**：提供更丰富的监控功能，可能支持多种数据可视化

#### BlockAdvanceCraftingLink.java
- **功能**：合成链接方块
- **状态**：新增（A）
- **推测功能**：与 AE2 合成系统集成，监控合成进度

#### BlockAdvancedatamonitorController.java
- **功能**：监控器控制器方块
- **状态**：存在于磁盘
- **推测功能**：控制和管理多个监控器的工作状态

#### BlockAdvanceStorageLink.java
- **功能**：存储链接方块
- **状态**：新增（A）
- **注意**：文件名有拼写错误（Advnace 应为 Advance）
- **功能说明**：接入 AE2 网络的高级存储链接方块，搭配高级存储链接元件使用。支持模糊卡、反相卡、矿典卡和流体标记。

---

### 2. 物品模块 (items)

#### ItemAdvanceStorageLinkCell.java
- **功能**：存储链接单元物品（高级存储链接元件）
- **状态**：新增（A）
- **功能说明**：实现 ICellWorkbenchItem，支持 AE2 Cell Workbench 编辑分区。升级槽支持 Fuzzy Card、Inverter Card 和 Ore Filter Card（矿典卡）。分区槽物品决定监控目标，NBT 中可写入 fluidMarkers 流体标记列表。

---

### 3. TileEntity 模块 (tileentity)

#### TileEntityAdvanceCraftingLink.java
- **功能**：合成链接方块实体
- **状态**：新增（A）
- **推测功能**：
  - 存储合成链接的配置数据
  - 与 AE2 合成系统交互
  - 处理合成进度的更新和同步

#### TileEntityAdvanceStorageLink.java
- **功能**：存储链接方块实体
- **状态**：新增（A）
- **功能说明**：
  - AE2 AENetworkTile，实现 IInventory，36 个槽位仅接受 ItemAdvanceStorageLinkCell
  - 支持普通模式（精确匹配）、Fuzzy 模式（模糊卡）、Inverter 模式（反相卡）、Ore Filter 模式（矿典卡）和 Fluid Marker 模式（流体标记）
  - createStorageItemsSnapshot() 查询 AE2 IStorageGrid 组装 NBTTagList 供渲染使用
  - 每 20 tick 采样计算增量（countDelta），通过 PacketItemCountSync 同步客户端缓存

---

### 4. GUI 模块 (gui)

#### GuiSubAdvanceCraftingLink.java
- **功能**：合成链接配置界面
- **状态**：新增（A）
- **推测功能**：配置合成链接的参数和显示选项

#### GuiSubAEAdvanceCraftingLink.java
- **功能**：AE 合成链接配置界面
- **状态**：新增（A）
- **推测功能**：与 AE2 原生合成系统集成的配置界面

#### GuiSubAEAdvanceNetworkLink.java
- **功能**：AE 网络链接配置界面
- **状态**：新增（A）
- **推测功能**：配置与 AE2 网络的连接参数

#### GuiSubBind.java
- **功能**：绑定配置界面
- **状态**：新增（A）
- **推测功能**：设置监控器与 AE2 网络的绑定关系

#### GuiSubColorConfig.java
- **功能**：颜色配置界面
- **状态**：新增（A）
- **推测功能**：自定义监控器的显示颜色和样式

---

### 5. 网络模块 (network)

#### PacketItemCountSync.java
- **功能**：物品数量同步数据包
- **状态**：新增（A）
- **推测功能**：
  - 从服务端同步物品数量到客户端
  - 包含物品ID、数量、位置等信息
  - 用于实时更新监控器显示

#### PacketRequestItemCountSync.java
- **功能**：请求物品数量同步数据包
- **状态**：新增（A）
- **推测功能**：
  - 客户端向服务端请求物品数量数据
  - 包含请求的物品类型或网络位置
  - 触发服务端发送 PacketItemCountSync

---

### 6. 渲染模块 (renders)

#### CraftingInfoRenderer.java
- **功能**：合成信息渲染器
- **状态**：新增（A）
- **推测功能**：
  - 在游戏世界中渲染合成进度信息
  - 可能使用 HUD 或世界内文本显示
  - 支持自定义渲染样式和颜色

---

### 7. 工具模块 (utils)

#### CraftingTemplateParser.java
- **功能**：合成模板解析器
- **状态**：新增（A）
- **推测功能**：
  - 解析 AE2 合成模板数据
  - 提取合成配方和材料信息
  - 支持多种合成模板格式

---

### 8. 加载器模块 (loader)

#### LoaderGui.java
- **功能**：GUI 加载器
- **状态**：新增（A）
- **推测功能**：
  - 注册所有 GUI 界面
  - 处理 GUI 的打开和关闭
  - 管理 GUI 的生命周期

---

## 构建系统

### 1. 构建配置

#### gradle.properties
```properties
# 模组基本信息
modName = AdvanceDataMonitor.
modId = advancedatamonitor.
modGroup = com.imgood.advancedatamonitor

# Minecraft 和 Forge 版本
minecraftVersion = 1.7.10
forgeVersion = 10.13.4.1614

# 版本 Token
generateGradleTokenClass = com.imgood.advancedatamonitor.Tags
gradleTokenVersion = VERSION

# 代理设置
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=10809
```

#### dependencies.gradle
```gradle
dependencies {
    // 运行时依赖
    runtimeOnlyNonPublishable("com.github.GTNewHorizons:NotEnoughItems:2.5.4-GTNH:dev")
    
    // 本地依赖
    implementation files('libs/Chisel-2.14.1-GTNH-dev.jar')
    implementation files('libs/Galacticraft-3.3.12-GTNH-dev.jar')
    implementation files('libs/IC2NuclearControl-2.7.8-dev.jar')
    
    // API 依赖
    api('com.github.GTNewHorizons:GT5-Unofficial:5.09.51.470:dev')
    
    // 实现依赖
    implementation('com.github.GTNewHorizons:AE2FluidCraft-Rework:1.3.7-gtnh:dev')
    implementation('com.github.GTNewHorizons:ArchitectureCraft:1.10.2:dev')
    implementation('com.github.GTNewHorizons:BlockRenderer6343:1.2.12:dev')
    implementation('com.github.GTNewHorizons:NewHorizonsCoreMod:2.7.260:dev')
    implementation('com.github.GTNewHorizons:Avaritia:1.61:dev')
}
```

### 2. 构建命令

```bash
# 清理构建
./gradlew clean

# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer

# 生成 IDE 项目文件
./gradlew idea  # IntelliJ IDEA
./gradlew eclipse  # Eclipse
```

---

## 依赖关系图

```
AdvanceDataMonitor
├── GT5-Unofficial (API)
│   └── GregTech 5 核心功能
├── AE2FluidCraft-Rework (Implementation)
│   └── AE2 流体合成支持
├── NewHorizonsCoreMod (Implementation)
│   └── GTNH 核心模组
├── ArchitectureCraft (Implementation)
│   └── 建筑工艺支持
├── BlockRenderer6343 (Implementation)
│   └── 方块渲染支持
├── Avaritia (Implementation)
│   └── 无尽贪婪支持
├── Chisel (Local)
│   └── 凿子模组支持
├── Galacticraft (Local)
│   └── 星系模组支持
└── IC2NuclearControl (Local)
    └── IC2 核控制支持
```

---

## 开发指南

### 1. 环境搭建

#### 前置要求
- JDK 8 或更高版本
- IntelliJ IDEA 或 Eclipse
- Git

#### 搭建步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/ImgoodWK/AdvanceDataMonitor429.git
   cd AdvanceDataMonitor429
   ```

2. **生成 IDE 项目**
   ```bash
   # IntelliJ IDEA
   ./gradlew idea
   
   # Eclipse
   ./gradlew eclipse
   ```

3. **导入项目**
   - IntelliJ IDEA: File -> Open -> 选择项目目录
   - Eclipse: File -> Import -> Existing Projects into Workspace

4. **配置代理**（如需要）
   在 `gradle.properties` 中配置代理：
   ```properties
   systemProp.https.proxyHost=127.0.0.1
   systemProp.https.proxyPort=10809
   ```

### 2. 代码规范

#### 命名规范
- **类名**：PascalCase，如 `BlockAdvanceCraftingLink`
- **方法名**：camelCase，如 `getCraftingProgress()`
- **变量名**：camelCase，如 `itemCount`
- **常量名**：UPPER_SNAKE_CASE，如 `MAX_STORAGE_SIZE`

#### 包结构
- **blocks/**：方块相关类
- **items/**：物品相关类
- **tileentity/**：TileEntity 相关类
- **gui/**：GUI 相关类
- **network/**：网络通信相关类
- **renders/**：渲染相关类
- **utils/**：工具类
- **loader/**：加载器类

### 3. 添加新功能

#### 添加新方块

1. **创建方块类**
   ```java
   package com.imgood.advancedatamonitor.blocks;
   
   import net.minecraft.block.Block;
   import net.minecraft.block.material.Material;
   
   public class BlockNewFeature extends Block {
       public BlockNewFeature() {
           super(Material.iron);
           this.setBlockName("newFeature");
           this.setHardness(5.0F);
           this.setResistance(10.0F);
       }
   }
   ```

2. **创建 TileEntity 类**
   ```java
   package com.imgood.advancedatamonitor.tileentity;
   
   import net.minecraft.tileentity.TileEntity;
   
   public class TileEntityNewFeature extends TileEntity {
       // TileEntity 实现
   }
   ```

3. **注册方块**
   在相应的加载器中注册：
   ```java
   GameRegistry.registerBlock(new BlockNewFeature(), "newFeature");
   GameRegistry.registerTileEntity(TileEntityNewFeature.class, "newFeature");
   ```

#### 添加新 GUI

1. **创建 GUI 类**
   ```java
   package com.imgood.advancedatamonitor.gui.guiscreen;
   
   import net.minecraft.client.gui.inventory.GuiContainer;
   
   public class GuiNewFeature extends GuiContainer {
       public GuiNewFeature() {
           super(new ContainerNewFeature());
           this.xSize = 176;
           this.ySize = 166;
       }
       
       @Override
       protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
           // 绘制背景
       }
   }
   ```

2. **创建 Container 类**
   ```java
   package com.imgood.advancedatamonitor.gui.container;
   
   import net.minecraft.inventory.Container;
   
   public class ContainerNewFeature extends Container {
       @Override
       public boolean canInteractWith(EntityPlayer player) {
           return true;
       }
   }
   ```

3. **注册 GUI**
   在 `LoaderGui.java` 中注册：
   ```java
   NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
   ```

#### 添加新网络包

1. **创建数据包类**
   ```java
   package com.imgood.advancedatamonitor.network.packet;
   
   import cpw.mods.fml.common.network.simpleimpl.IMessage;
   import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
   import cpw.mods.fml.common.network.simpleimpl.MessageContext;
   
   public class PacketNewFeature implements IMessage {
       private int data;
       
       public PacketNewFeature() {}
       
       public PacketNewFeature(int data) {
           this.data = data;
       }
       
       @Override
       public void fromBytes(ByteBuf buf) {
           this.data = buf.readInt();
       }
       
       @Override
       public void toBytes(ByteBuf buf) {
           buf.writeInt(this.data);
       }
       
       public static class Handler implements IMessageHandler<PacketNewFeature, IMessage> {
           @Override
           public IMessage onMessage(PacketNewFeature message, MessageContext ctx) {
               // 处理消息
               return null;
           }
       }
   }
   ```

2. **注册数据包**
   在网络初始化时注册：
   ```java
   INSTANCE.registerMessage(PacketNewFeature.Handler.class, PacketNewFeature.class, 0, Side.CLIENT);
   ```

---

## API 参考

### 1. 方块 API

#### BlockAdvDataMonitor
```java
// 获取监控器状态
public int getMonitorState(World world, int x, int y, int z)

// 设置监控器状态
public void setMonitorState(World world, int x, int y, int z, int state)

// 获取连接的 AE2 网络
public IGrid getConnectedGrid(World world, int x, int y, int z)
```

#### BlockAdvanceCraftingLink
```java
// 获取合成进度
public float getCraftingProgress(World world, int x, int y, int z)

// 获取合成任务列表
public List<CraftingTask> getCraftingTasks(World world, int x, int y, int z)

// 设置合成任务
public void setCraftingTask(World world, int x, int y, int z, CraftingTask task)
```

### 2. TileEntity API

#### TileEntityAdvanceCraftingLink
```java
// 合成进度
private float craftingProgress;

// 合成任务列表
private List<CraftingTask> craftingTasks;

// 更新合成进度
public void updateCraftingProgress()

// 获取合成任务
public List<CraftingTask> getCraftingTasks()

// 添加合成任务
public void addCraftingTask(CraftingTask task)
```

#### TileEntityAdvanceStorageLink
```java
// 36 个元件槽位，仅接受 ItemAdvanceStorageLinkCell
private final ItemStack[] cellItems = new ItemStack[36];

// AE2 存储查询 — 支持模糊/精确/矿典/流体模式
public long getItemCountInNetwork(ItemStack stack, FuzzyMode fuzzyMode, String oreFilter)
public long getFluidAmountInNetwork(FluidStack fluidStack)

// 生成渲染用 NBT 快照
public NBTTagList createStorageItemsSnapshot()

// 增量统计（每 20 tick 采样计数变化）
private void sampleStorageDeltasIfNeeded()

// 网络同步
public void handleItemCountSyncRequest()
public void updateClientCache(Map<Integer, Long> newCache)
```

### 3. 网络 API

#### PacketItemCountSync
```java
// 物品数量数据包
public class PacketItemCountSync implements IMessage {
    private int itemID;
    private int count;
    private int x, y, z;
    
    // 构造函数
    public PacketItemCountSync(int itemID, int count, int x, int y, int z)
    
    // 序列化
    public void toBytes(ByteBuf buf)
    
    // 反序列化
    public void fromBytes(ByteBuf buf)
}
```

#### PacketRequestItemCountSync
```java
// 请求物品数量数据包
public class PacketRequestItemCountSync implements IMessage {
    private int x, y, z;
    
    // 构造函数
    public PacketRequestItemCountSync(int x, int y, int z)
    
    // 序列化
    public void toBytes(ByteBuf buf)
    
    // 反序列化
    public void fromBytes(ByteBuf buf)
}
```

---

## 调试和测试

### 1. 调试技巧

#### 启用调试模式
在 `gradle.properties` 中添加：
```properties
# 启用调试
org.gradle.jvmargs=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005
```

#### 使用日志
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

private static final Logger logger = LogManager.getLogger("AdvanceDataMonitor");

// 记录信息
logger.info("Debug message: {}", variable);

// 记录警告
logger.warn("Warning message");

// 记录错误
logger.error("Error message", exception);
```

### 2. 测试方法

#### 单元测试
```java
package com.imgood.advancedatamonitor.test;

import org.junit.Test;
import static org.junit.Assert.*;

public class CraftingTemplateParserTest {
    @Test
    public void testParseTemplate() {
        CraftingTemplateParser parser = new CraftingTemplateParser();
        // 测试解析逻辑
    }
}
```

#### 集成测试
```java
package com.imgood.advancedatamonitor.test;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockIntegrationTest {
    @Test
    public void testBlockPlacement() {
        // 测试方块放置逻辑
    }
}
```

---

## 性能优化

### 1. 渲染优化

- 使用显示列表（Display List）缓存静态几何体
- 实现视锥体剔除（Frustum Culling）
- 使用 LOD（Level of Detail）技术

### 2. 网络优化

- 批量发送数据包减少网络开销
- 使用增量更新减少数据传输量
- 实现数据压缩

### 3. 内存优化

- 使用对象池减少对象创建
- 实现缓存机制减少重复计算
- 及时释放不用的资源

---

## 已知问题和限制

### 1. 已知问题

- **重构进行中**：项目正在大规模重构，部分功能可能不稳定

### 2. 限制

- 仅支持 Minecraft 1.7.10
- 强依赖 GTNH 整合包环境
- 需要 AE2 和 GT5 等前置模组

---

## 版本历史

### 版本 429

- 重构模组架构
- 新增合成链接和存储链接功能
- 优化 GUI 界面
- 改进网络同步机制

### 早期版本

- 基础数据监控功能
- 自定义 GUI 组件
- OBJ 模型渲染
- 折线图数据可视化

---

## 贡献指南

### 1. 代码贡献

1. Fork 项目仓库
2. 创建功能分支：`git checkout -b feature/new-feature`
3. 提交更改：`git commit -m 'Add new feature'`
4. 推送分支：`git push origin feature/new-feature`
5. 创建 Pull Request

### 2. 问题报告

在 GitHub 上提交 Issue，包含：
- 问题描述
- 复现步骤
- 期望行为
- 实际行为
- 环境信息

### 3. 代码审查

- 遵循代码规范
- 添加必要的注释
- 编写单元测试
- 更新文档

---

## 许可证

本模组遵循 GTNH 整合包的许可证协议。

---

## 联系方式

- **GitHub 仓库**：https://github.com/ImgoodWK/AdvanceDataMonitor429
- **问题反馈**：在 GitHub 上提交 Issue

---

*最后更新：2026年6月8日*
