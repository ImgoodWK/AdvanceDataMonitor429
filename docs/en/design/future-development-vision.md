# TeXTech Future Development Vision

> *"Weaving reality from the torrent of data, stitching matter and substance with threads of binary."*

## Vision Overview

TeXTech was born amidst the stellar odyssey of GregTech: New Horizons. As players accumulate astronomical quantities of items and data within their AE2 networks, and as digital miners tirelessly produce rivers of resources around the clock, a fundamental question emerges: **What else can we do with all this data?**

The mod's core philosophy has always been **"From Data to Matter"** — the inverse weaving of digital records from AE2 storage systems (item types, quantities, fluid levels, essentia distributions) back into real items and liquids through data weave cells. The Advanced Data Monitor, AI Assistant, Grapple System, and other subsystems form a complete ecosystem surrounding this central concept.

Looking forward, TeXTech will deepen its development along the following axes:

1. **Deepening Data Weaving**: Expanding from simple "stored item type → matter" to "crafting template data → multi-step synthesis", "spacetime data → dimensional travel", and "biological data → entity manipulation".
2. **Broadening Visualization**: Beyond existing line/bar charts, introducing heatmaps, Sankey diagrams, network topology maps, and 3D holographic projections.
3. **Autonomous AI**: The AI Assistant evolves from "reactive responses" to "proactive management" — adjusting priorities, pre-crafting scarce items, detecting bottlenecks, and auto-scaling production.
4. **Legendary Narrative**: The origins of Super Orange and Empyrean Holy Judgment conceal a grander storyline, revealing the source of "data weaving" in the GTNH universe.
5. **Multiplayer Ecosystem**: On servers, data weaving can become a trade currency, monitors can be shared as public dashboards, and AI assistants can serve as guild-specific intelligences.

The following 105 future development projects systematically unfold this vision across 13 major categories. Each project includes detailed functional descriptions, world-building lore, visual concepts (with AI image generation prompts), and actionable technical implementation approaches.

---

## 1. Data Weaving & Matter Reconstruction

### Project 1
- **ID**: 001
- **Name**: 时序编织元件 / Chrono-Weave Cell
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: S
- **Description**: A new type of AE2 storage cell capable of automatically converting stored items into another item type after a specified time delay. For example, inserting iron ingots with a 5-minute timer automatically converts them to steel ingots. Longer times and higher material tiers demand more energy. Supports automated extraction via AE2 networks, implementing the concept of "delayed crafting". The cell surface features a rotating hourglass particle effect.
- **Lore**: During long-term observation, the Advanced Data Monitor discovered that data streams within AE2 networks possess subtle temporal properties — certain items, when stored long enough, exhibit an "entropic evolution" tendency in their digital representation. The Chrono-Weave Cell exploits this discovery, amplifying the temporal component of data streams to accelerate this natural evolution.
- **Visual & AI Prompts**:
  - Visual Description: An hourglass-shaped AE2 storage cell with upper and lower cones connected by a deep navy-black metal frame. The central sand-flow area consists of glowing cyan data particles that change color as they descend from top to bottom, representing item conversion progress. The casing features faint gold circuit patterns suggesting temporal attributes.
  - EN Prompt: `A Minecraft-style AE2 storage cell shaped like an hourglass, deep navy-blue metal frame with cyan glowing data particles flowing as sand, faint gold circuit patterns on casing, industrial sci-fi tech aesthetic, GregTech mod style, dark background, volumetric lighting, 4K game asset render`
  - CN Prompt: `沙漏形状的AE2存储元件，深蓝黑色金属框架，发光的青色数据粒子如流沙般流动，外壳有淡淡金色电路纹路，工业科幻科技美学，GregTech模组风格，暗色背景，体积光，4K游戏资产渲染`
- **Technical Approach**:
  - Files: `items/cell/ItemDataChronoWeaveCell.java` (new), `items/cell/DataLoomCellConfig.java` (reference base)
  - Core Design: Extends `AbstractDataLoomItemCell`, adds `convertTime` and `targetItem` NBT fields. In `IMEInventoryHandler` tick, checks timer and performs conversion via `IItemHandler.extractItem` + `insertItem`.
  - NBT Structure: `{convertTarget: {id: "minecraft:iron_ingot", Count: 1, Damage: 0}, convertTime: 6000, elapsedTime: 0}`
  - Integration: Register in `LoaderItem.java`, `LoaderNetwork.java`, and the `ICellHandler` registry.
  - Rendering: TESR for hourglass particle effect, or dynamic item texture via `IItemRenderer`.
  - Config: `[dataLoomCell] chronoWeaveBaseEnergyPerTick`, `chronoWeaveMaxTimeTicks`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (optional, for energy calculations)

### Project 2
- **ID**: 002
- **Name**: 多重编织核心 / Multi-Weave Core
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: A
- **Description**: A multi-slot machine block allowing simultaneous execution of multiple data weaving tasks. Each slot can hold a different weave cell type (Dust/Form/Flow/Tide/Source Loom Cells), all working in parallel. Provides a GUI showing progress, energy consumption, and output estimates for all slots. Supports redstone control (disable/accelerate) and GT5 energy network connectivity.
- **Lore**: A single weave cell's efficiency is insufficient for industrial-scale production. The Multi-Weave Core is the industrialization milestone of data weaving technology — connecting multiple cells in a matrix, sharing data weave channels through quantum entanglement effects, and exponentially multiplying matter generation efficiency. The machine's hum sounds like the data itself crying out.
- **Visual & AI Prompts**:
  - Visual Description: A 2x2x2 multiblock structure with deep navy-black metal casing. Each of the six faces features a circular slot showing different weave cells. When active, cyan data streams flow rapidly between all six faces, forming a hexagonal grid. A central floating rotating sphere emits pulsing white light. The base has a GT5 energy port.
  - EN Prompt: `A 2x2x2 multiblock machine in Minecraft GregTech style, deep navy-blue metal casing, six circular slots on each face with glowing cyan data streams connecting them, central floating rotating sphere with pulsing white light, hexagonal grid energy patterns, industrial sci-fi, volumetric fog, 4K game asset`
  - CN Prompt: `2x2x2多方块机器，GregTech风格深蓝黑色金属外壳，六个面上各有一个圆形插槽，青色数据流在面之间高速连接，中央悬浮旋转球体发出脉冲白光，六边形网格能量纹路，工业科幻风，体积雾，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockMultiWeaveCore.java` (new), `tileentity/TileEntityMultiWeaveCore.java` (new), `gui/guiscreen/GuiMultiWeaveCore.java` (new), `gui/container/ContainerMultiWeaveCore.java` (new)
  - Core Design: TileEntity contains `ItemStack[6] slots` for six weave cells. In `updateEntity()`, calls the ICellHandler tick logic for each non-empty slot. Connects to GT5 power grid via `IGregTechEnergyReceiver`.
  - NBT Structure: `{Slots: [{Slot: 0, id: "adm:dataDustLoomCell", ...}, ...], Progress: [0.0f, 0.5f, ...], EnergyStored: 0L}`
  - Integration: Register in `LoaderBlock`, `LoaderTileEntity`, `LoaderGui`. Acceptable cell types controlled via a whitelist.
  - Rendering: TESR for central floating sphere and data stream particles.
  - Config: `[multiWeaveCore] maxParallelTasks`, `baseEnergyConsumption`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required, for energy interface)

### Project 3
- **ID**: 003
- **Name**: 物质重构祭坛 / Matter Reconstruction Altar
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: A
- **Description**: A massive multiblock altar structure (5x5 base + central pillar) that consumes the "item type count" stored in the AE2 network as input, combined with Thaumcraft essentia, to directly synthesize legendary-tier items (such as evolved Super Orange or upgraded Empyrean Holy Judgment). Requires players to place infusion matrices around the altar in specific patterns. The synthesis process generates massive particle effects and screen shake.
- **Lore**: According to ancient GTNH records, the first Super Oranges were not crafted at a table but "woven" within a lost Data Temple. The Matter Reconstruction Altar is a restoration of that temple — it doesn't create matter, but extracts the essential template of an item from the AE2 network's "concepts" and uses essentia to grant it real physical form.
- **Visual & AI Prompts**:
  - Visual Description: A massive circular ritual altar with five-tiered stepped base made of deep navy-black stone blocks. A central pillar of cyan light shoots skyward with a floating item materializing inside. Six infusion pedestals surround the altar, each emitting different-colored essentia glows. Floating glowing runes written in cyan data streams hover in the air.
  - EN Prompt: `A massive circular ritual altar, five-tiered stepped base of deep navy-black stone blocks, central pillar of cyan light shooting upward with a floating item materializing inside, six infusion pedestals around with different colored essentia glows, floating glowing runes in cyan data streams, Thaumcraft-meets-GregTech fusion aesthetic, dramatic lighting, 4K game scene`
  - CN Prompt: `巨大的圆形仪式祭坛，五层阶梯式基座由深蓝黑色石块构成，中央青色光柱冲天而起其中悬浮正在生成的物品，周围六个注魔基座散发不同颜色源质光芒，漂浮的发光符文由青色数据流构成，神秘时代与GregTech融合美学，戏剧性光影，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockMatterReconstructionAltar.java`, `tileentity/TileEntityMatterReconstructionAltar.java` (new), `handler/HandlerMatterReconstruction.java` (new)
  - Core Design: TileEntity validates multiblock structure integrity, reads AE2 network item type count (via `IAEItemStorage`), checks essentia via `IInfusionAltar` interface, starts synthesis animation when conditions are met.
  - NBT Structure: `{recipeId: "adm:superOrangeV2", progress: 0, requiredItems: 10000, requiredEssentia: {ordo: 256, ...}}`
  - Rendering: TESR for light pillar, floating item, and rune particles.
  - Config: `[altar] requireMultiblockVerification`, `recipeEnergyMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required), Thaumcraft (required), GT5-Unofficial (optional)

### Project 4
- **ID**: 004
- **Name**: 数据熔炉 / Data Furnace
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: B
- **Description**: A special furnace that doesn't consume fuel but instead consumes the "item type diversity" metric from the AE2 network. More item types = faster smelting. Can simultaneously smelt multiple items, dynamically adjusting parallel slot count based on network type count. Each additional item type increases furnace efficiency by 0.5%.
- **Lore**: Every item type in GregTech is a unique frequency in the sea of data. The Data Furnace uses these "frequencies" as a heat source — the more types, the stronger the frequency interference and the higher the temperature. This is another manifestation of entropy, cleverly exploited by engineers.
- **Visual & AI Prompts**:
  - Visual Description: A standard furnace-shaped block with transparent deep-blue glass casing. Glowing cyan flames dance inside. A small display on the side shows real-time network type count and smelting efficiency. When active, flowing data stream textures appear on the glass surface.
  - EN Prompt: `A Minecraft furnace block with transparent deep-blue glass casing, glowing cyan flames inside, small display on side showing numbers, flowing data stream textures on glass surface, GregTech industrial aesthetic, dark background, 4K game asset render`
  - CN Prompt: `透明深蓝玻璃外壳的熔炉方块，内部发光青色火焰跳动，侧面小型显示屏显示数字，玻璃表面流动数据流纹理，GregTech工业美学，暗色背景，4K游戏资产渲染`
- **Technical Approach**:
  - Files: `blocks/BlockDataFurnace.java`, `tileentity/TileEntityDataFurnace.java`, `gui/guiscreen/GuiDataFurnace.java`, `gui/container/ContainerDataFurnace.java`
  - Core Design: TileEntity implements `ISidedInventory` and `IFurnaceRecipe` compatibility. Each tick queries AE2 network item type count (using the same API as `TileEntityAdvanceStorageLink`) to calculate smelting speed.
  - Integration: Reuses `TileEntityAdvanceStorageLink`'s network query logic.
  - Config: `[dataFurnace] efficiencyPerItemType`, `maxParallelSlots`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 5
- **ID**: 005
- **Name**: 生物数据编织器 / Bio-Data Synthesizer
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: B
- **Description**: Consumes data from specific items in the AE2 network (eggs, rotten flesh, bones — biological items) to generate or duplicate living entities. Can "print" passive mobs (cows, sheep, chickens) and even controllable Iron Golems. In advanced mode, can duplicate named mobs with specific attributes. Requires massive energy and essentia consumption.
- **Lore**: Life is also data — DNA sequences, behavior patterns, growth curves can all be digitized. The Bio-Data Synthesizer pushes this philosophy to its extreme: if items can be woven from data, why not living organisms? Of course, the soul part might need to be supplemented with essentia.
- **Visual & AI Prompts**:
  - Visual Description: A vertical cultivation tank block (1x1x2) with transparent glass casing filled with glowing cyan bio-fluid. A forming creature silhouette is suspended inside. A metal cap on top has pipes and data cables extending outward. A GT5-style energy port is at the bottom.
  - EN Prompt: `A vertical cultivation tank block 1x1x2 in Minecraft style, transparent glass filled with cyan glowing bio-fluid, a forming creature silhouette suspended inside, metal cap on top with pipes and data cables, GregTech energy port at bottom, sci-fi bio-engineering, moody lighting, 4K game asset`
  - CN Prompt: `1x1x2竖立培养罐方块，透明玻璃中充满发光的青色生物质液体，正在形成的生物轮廓悬浮其中，顶部金属盖伸出管道和数据线，底部GregTech能量接口，科幻生物工程，暗调氛围光，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockBioDataSynthesizer.java`, `tileentity/TileEntityBioDataSynthesizer.java`, `entity/` (auxiliary entity markers may be needed)
  - Core Design: TileEntity scans AE2 network for bio-related items, spawns entity in world when conditions are met. Uses `EntityRegistry` and NBT duplication.
  - NBT Structure: `{targetEntity: "Cow", targetNBT: {...}, dnaDataRequired: 1000, progress: 0}`
  - Rendering: TESR for internal liquid and creature silhouette.
  - Config: `[bioSynth] blacklistedEntities`
- **Dependencies**: AE2FluidCraft-Rework (required), Thaumcraft (optional, for essentia), GT5-Unofficial (required, energy)

### Project 6
- **ID**: 006
- **Name**: 维度数据编织器 / Dimensional Data Weaver
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: C
- **Description**: The ultimate data weaving device. Consumes the "information entropy" of ALL item data in the AE2 network to generate a portal to a custom dimension. The generated dimension type depends on the proportion of consumed item types (e.g., mostly metals → metal planet dimension, mostly organics → jungle dimension). The dimension's physical laws can be "re-weaved" via the weaver's parameter panel.
- **Lore**: If matter can come from data, what about space itself? The Dimensional Data Weaver answers this question. It doesn't create a dimension — it "downloads" a world from the AE2 network's digitized universe. Everything in this world — sky color, gravity magnitude, ore distribution — is determined by the data you've consumed.
- **Visual & AI Prompts**:
  - Visual Description: A massive arch portal structure (3x3) built from deep navy-black metal and cyan glass. The portal surface inside the arch is a flowing, ever-changing "data mirror" — not the purple of a nether portal, but a semi-transparent plane composed of countless cyan and white data particles. Small monitor screens float around the arch, displaying the dimension parameters being woven.
  - EN Prompt: `A massive arch portal structure 3x3 blocks in Minecraft, deep navy-black metal frame with cyan glass, portal surface is a shimmering data mirror made of cyan and white particles instead of purple, floating small monitor screens around showing dimension parameters, GregTech sci-fi aesthetic, dramatic scale, 4K game scene`
  - CN Prompt: `巨大的3x3拱门传送门结构，深蓝黑色金属框架配青色玻璃，传送门表面是由青色和白色数据粒子构成的流动数据镜面而非紫色，周围漂浮小型监视器屏幕显示维度参数，GregTech科幻美学，震撼尺度，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDimensionWeaver.java`, `tileentity/TileEntityDimensionWeaver.java`, `handler/HandlerCustomDimension.java` (new), `world/WorldProviderWeaved.java` (new)
  - Core Design: TileEntity calculates weighted entropy of AE2 network item types, generates dimension ID and parameters. Registers custom dimension via `DimensionManager`. Requires a WorldProvider implementation to apply dynamic physical parameters.
  - NBT Structure: `{dimensionId: 100, itemTypeWeights: {minecraft:iron_ingot: 0.3, ...}, gravity: 1.0, skyColor: 0x000033, ...}`
  - Rendering: TESR for data mirror portal effect.
  - Config: `[dimensionWeaver] maxCustomDimensions`, `defaultDimensionSeed`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 7
- **ID**: 007
- **Name**: 数据编织增强站 / Weave Enhancement Station
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: A
- **Description**: A GUI block that allows players to "overclock" connected weave cells. Consumes additional energy and GT5 rare materials (Neutronium, Eye of Quantum, etc.) to permanently boost a single weave cell's weaving speed, output multiplier, or energy efficiency. Each cell can be enhanced up to 5 times, with each enhancement changing its appearance (border color: Silver → Gold → Cyan → Purple → Iridescent).
- **Lore**: Standard weave cells ship locked to "safe parameters". But true GTNH engineers never settle for defaults. The Enhancement Station removes these restrictions, at the cost of consuming the rarest substances in the universe to reconstruct the cell's internal quantum circuitry.
- **Visual & AI Prompts**:
  - Visual Description: A single-block workstation with a central slot for the weave cell. The casing is deep blue metallic. A glowing enhancement ring on top changes color based on current enhancement level. When active, the ring rotates and injects cyan energy downward into the cell. The GUI shows enhancement level, materials needed for the next enhancement, and effect preview.
  - EN Prompt: `A single-block workstation for enhancing AE2 cells, deep blue metallic casing, a glowing enhancement ring on top that changes color by level, ring rotating and injecting cyan energy downward, sci-fi GregTech style, clean UI with enhancement stats, 4K game asset`
  - CN Prompt: `用于增强AE2元件的单方块工作站，深蓝金属外壳，顶部发光增强环根据等级变化颜色，环旋转并向下注入青色能量，GregTech科幻风格，清晰的增强属性UI，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockWeaveEnhancementStation.java`, `tileentity/TileEntityWeaveEnhancementStation.java`, `gui/guiscreen/GuiWeaveEnhancementStation.java`
  - Core Design: TileEntity reads enhancement level NBT from the cell in the slot, validates materials, then applies enhancement. Enhancement effects are written to the cell's NBT and read by ICellHandler's tick.
  - NBT Structure: `{enhanceLevel: 3, speedMultiplier: 1.5f, efficiencyMultiplier: 0.8f, outputMultiplier: 1.3f}`
  - Integration: Modify `AbstractDataLoomItemCell` and `AbstractDataLoomFluidCell` tick logic to read enhancement multipliers.
  - Config: `[enhancement] maxEnhanceLevel`, `speedPerLevel`, `efficiencyPerLevel`, `outputPerLevel`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required, for rare materials)

### Project 8
- **ID**: 008
- **Name**: 时空共振编织 / Resonance Weaving
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: B
- **Description**: A special weaving mode that "resonance-pairs" two or more weave cells. Paired cells share output — when one cell produces an item, paired cells simultaneously produce at a configurable ratio. For example, when the Dust Loom Cell produces iron dust, the paired Form Loom Cell automatically produces iron ingots. Resonance efficiency decays with distance between paired cells.
- **Lore**: The quantum cores of data weave cells have weak entanglement between them. Normally this entanglement is shielded to keep cells operating independently. But if deliberately enhanced, it creates a "one is many" weaving efficiency. The cost is exponentially increasing energy consumption.
- **Visual & AI Prompts**:
  - Visual Description: Two or more weave cells connected by a glowing cyan thread (quantum entanglement line), its thickness and brightness indicating resonance efficiency. Paired cell slot borders pulse with matching colors in synchronized flashes.
  - EN Prompt: `Two AE2 storage cells connected by a glowing cyan quantum entanglement thread, thread thickness and brightness indicating resonance efficiency, matching pulsating border colors on cell slots, data particles flowing along the thread, GregTech sci-fi network visualization, dark background, 4K render`
  - CN Prompt: `两个AE2存储元件之间由发光青色量子纠缠线连接，线的粗细和亮度表示共振效率，元件插槽边框显示匹配的脉动颜色，数据粒子沿丝线流动，GregTech科幻网络可视化，暗色背景，4K渲染`
- **Technical Approach**:
  - Files: `items/cell/ResonanceManager.java` (new), `handler/HandlerResonance.java` (new), modify `AbstractDataLoomItemCell.java`
  - Core Design: `ResonanceManager` maintains a global resonance pair registry. Each cell's tick checks for resonance partners and copies output at the configured ratio.
  - NBT Structure: `{resonancePairs: [{cell1Uid: "...", cell2Uid: "...", ratio: 0.5}]}`
  - Rendering: Draw glowing lines between cell slots (Tessellator LINE mode).
  - Config: `[resonance] maxResonancePairs`, `energyMultiplierPerPair`, `distanceDecayFactor`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 2. Data Visualization & Monitoring

### Project 9
- **ID**: 009
- **Name**: 热力图监视器模式 / Heatmap Monitor Mode
- **Category**: Data Visualization & Monitoring
- **Priority**: A
- **Description**: New rendering mode for Advanced Data Monitors — "Heatmap". Displays bound TileEntity data as a 2D or 3D heatmap on the monitor panel. Colors gradient from deep blue (low values) through cyan (medium) to bright white/red (high values). Suitable for showing storage distribution of different item types in AE2 networks, or load distribution across machine arrays.
- **Lore**: Traditional line and bar charts can't intuitively express "distribution density" information. The heatmap mode originates from data analysts' need for a "data topographic map" — immediately identifying which regions have abnormally high or low values on a single chart.
- **Visual & AI Prompts**:
  - Visual Description: Monitor panel rendering a gradient color grid, deep blue squares for low values, cyan for medium, bright white for high. Grid boundaries have a slight glow. The overall effect resembles infrared thermal imaging but in a tech blue-cyan color scheme. A color legend sits in the corner.
  - EN Prompt: `A Minecraft monitor screen displaying a heatmap with gradient color grid from deep blue through cyan to bright white, slight glow on grid boundaries, resembling infrared thermal imaging in tech-blue color scheme, color legend in corner, dark sci-fi monitor casing, 4K game asset`
  - CN Prompt: `显示热力图的监视器屏幕，从深蓝到青色再到亮白的渐变色网格，网格边界有轻微发光，类似红外热成像但配色为科技蓝青色系，角落有颜色图例，深色科幻监视器外壳，4K游戏资产`
- **Technical Approach**:
  - Files: `renders/HeatmapRenderer.java` (new), modify `TileEntityTeXTech.java` render mode enum, modify `gui/guiscreen/GuiTeXTech.java`
  - Core Design: `HeatmapRenderer` receives 2D `int[][] data` and value range, maps each value to a gradient color (using `Color.getHSBColor` linear interpolation), draws colored rectangles with Tessellator.
  - Integration: Extend `LineChartRenderer.java`'s mode switching, or dispatch via the monitor's render mode field.
  - Config: `[monitor] heatmapColorScheme`, `heatmapGridSize`
- **Dependencies**: None additional

### Project 10
- **ID**: 010
- **Name**: 桑基图流量监视 / Sankey Flow Monitor
- **Category**: Data Visualization & Monitoring
- **Priority**: A
- **Description**: New monitor mode displaying AE2 network item/fluid/energy flow paths as a Sankey Diagram. Flows from "sources" to "destinations", with line thickness representing flow volume. Can display: ore → crusher → washer → centrifuge item flow, or AE2 network → ME Interface → machine request flow.
- **Lore**: The complexity of a GTNH factory confuses even the most experienced engineers: where exactly does this machine's input come from? Where do its products go? The Sankey monitor transforms abstract network data into a visual "river diagram", making item flows clear at a glance.
- **Visual & AI Prompts**:
  - Visual Description: Monitor displaying multi-level nodes and connections. Each node is a rounded rectangle block icon. Connections are widening/narrowing glowing cyan bands (wider and brighter with more flow). Nodes and connections have entry/exit animations (particles flowing along the connection direction). Deep navy-black background.
  - EN Prompt: `A monitor screen showing a Sankey flow diagram with multi-level nodes as rounded rectangle icons, glowing cyan flow bands connecting them with varying thickness, animated particles flowing along connections, deep navy-black background, clean data visualization aesthetic, 4K render`
  - CN Prompt: `显示桑基流程图的监视器屏幕，多层级节点为圆角矩形图标，发光青色流量条带连接节点，粗细表示流量大小，粒子沿连线流动的动画，深蓝黑色背景，清爽的数据可视化美学，4K渲染`
- **Technical Approach**:
  - Files: `renders/SankeyRenderer.java` (new), modify `TileEntityTeXTech.java`
  - Core Design: `SankeyRenderer` receives node list and connection list (with flow weights), calculates Bezier curve paths for each connection, draws gradient-width triangle strips with Tessellator. Particles move along curve paths.
  - Config: `[monitor] sankeyMaxNodes`, `sankeyFlowParticleSpeed`
- **Dependencies**: None additional

### Project 11
- **ID**: 011
- **Name**: 3D 全息投影监视器 / 3D Holographic Monitor
- **Category**: Data Visualization & Monitoring
- **Priority**: B
- **Description**: The ultimate form of Advanced Data Monitor. When specific conditions are met (e.g., connected to sufficient AE2 network data), the monitor can project a 3D holographic image above itself, displaying the AE2 network topology or 3D models of stored items. The hologram can be rotated, zoomed, and mouse-interacted. Supports simultaneous viewing by multiple players.
- **Lore**: Data monitor screens are ultimately two-dimensional, but data itself is multi-dimensional. Holographic projection technology liberates data from flat surfaces, letting engineers truly "walk into" their data. This technology was reportedly originally developed for operating AE2 terminals in zero-gravity environments.
- **Visual & AI Prompts**:
  - Visual Description: A cubic area (approximately 3x3x3 blocks) floats above the monitor block, displaying a semi-transparent holographic projection — possibly a 3D node topology of the AE2 network, or an enlarged rotating model of an item. The hologram is woven from cyan light beams with slight flicker and scanline effects. The base monitor emits pulsing light upward.
  - EN Prompt: `A monitor block projecting a 3D holographic display floating above it in a 3x3x3 area, semi-transparent hologram made of cyan light beams weaving together, showing network topology or item models with rotation, scanline and flicker effects, pulsing light from base upward, sci-fi hologram aesthetic, dark background, 4K game asset`
  - CN Prompt: `监视器方块上方3x3x3区域投射3D全息影像，由交错的青色光束构成的半透明全息图，显示网络拓扑或物品旋转模型，扫描线和闪烁效果，底座脉冲光向上投射，科幻全息投影美学，暗色背景，4K游戏资产`
- **Technical Approach**:
  - Files: `renders/HolographicRenderer.java` (new), modify `TileEntityTeXTech.java`
  - Core Design: `HolographicRenderer` uses GL11 in world space via TESR. For topology mode, renders node spheres and connections. For item display mode, renders item 3D model in world space. Mouse interaction via RayTrace.
  - Config: `[monitor] enableHolographicMode`, `hologramMaxRenderDistance`
- **Dependencies**: None additional

### Project 12
- **ID**: 012
- **Name**: 实时波形图 / Live Waveform Display
- **Category**: Data Visualization & Monitoring
- **Priority**: B
- **Description**: Enhanced line chart mode showing real-time waveform of instantaneous data changes. Similar to an oscilloscope — data scrolls from left to right, waveform updates in real-time. Multiple data channels can be overlaid (different colors), with timeline zoom and pause support. Particularly suitable for monitoring GT generator instant output fluctuations or AE2 network real-time request rates.
- **Lore**: GTNH power networks are full of high-frequency fluctuations — large machine start/stop events cause instant voltage changes. The live waveform monitor works like an oscilloscope, letting engineers capture these microsecond-level changes to optimize power distribution networks.
- **Visual & AI Prompts**:
  - Visual Description: Monitor panel displaying multiple colored waveform lines scrolling left to right in real-time oscilloscope style. Each line has a different color (cyan, gold, white, magenta) for a different data channel. Y-axis scale on the left, time axis at the bottom. Lines have a slight glow effect. Grid background is deep navy-black.
  - EN Prompt: `A monitor screen showing multiple colored waveform lines scrolling left to right in real-time oscilloscope style, cyan gold white magenta lines, Y-axis scale on left, time axis at bottom, slight glow on lines, deep navy-black grid background, sci-fi oscilloscope aesthetic, 4K game asset`
  - CN Prompt: `监视器屏幕显示多条彩色波形线从左向右实时滚动（示波器风格），青色金色白色品红多通道线，左侧Y轴刻度，底部时间轴，线条轻微发光，深蓝黑色网格背景，科幻示波器美学，4K游戏资产`
- **Technical Approach**:
  - Files: `renders/WaveformRenderer.java` (new), modify `TileEntityTeXTech.java`
  - Core Design: `WaveformRenderer` maintains a ring buffer for recent data points. Draws lines with Tessellator. Scrolling is achieved by shifting X offset per tick.
  - Integration: Extend `LineChartRenderer.java`, add "realtime" sub-mode.
  - Config: `[monitor] waveformBufferSize`, `waveformDefaultChannels`
- **Dependencies**: None additional

### Project 13
- **ID**: 013
- **Name**: 网络拓扑可视化 / Network Topology Map
- **Category**: Data Visualization & Monitoring
- **Priority**: A
- **Description**: Displays the AE2 network topology on an Advanced Data Monitor. Shows all ME Controllers, Drives, Interfaces, and P2P channel connections. Nodes use different shapes (circle=controller, square=drive, diamond=interface). Edge colors indicate channel usage (green→yellow→red). Click nodes to view details.
- **Lore**: A complex AE2 network can contain hundreds of components, any one of which could bring down the entire system if it fails. Topology visualization reveals the network's "skeleton", letting maintainers identify bottlenecks and failure points at a glance.
- **Visual & AI Prompts**:
  - Visual Description: Monitor showing network node graph and connections. AE2 controllers as large cyan circular nodes, drives as square nodes, interfaces as diamond nodes. Connection colors gradient from green (idle) to red (channels full). Nodes have a pulsating glow. Hovering a node shows detailed information.
  - EN Prompt: `A monitor showing AE2 network topology graph, large cyan circular nodes for controllers, square nodes for drives, diamond nodes for interfaces, connecting lines gradient from green to red indicating channel usage, pulsating glow around nodes, deep dark background, tech network visualization, 4K render`
  - CN Prompt: `显示AE2网络拓扑图的监视器，大型青色圆形节点表示控制器，方形节点表示驱动器，菱形节点表示接口，连线颜色从绿渐变到红表示通道使用率，节点有脉动光晕，深色背景，科技网络可视化，4K渲染`
- **Technical Approach**:
  - Files: `renders/TopologyRenderer.java` (new), `tileentity/TileEntityAdvanceNetworkLink.java` (extend data collection), `compat/ae/` (new topology query utilities)
  - Core Design: `TopologyRenderer` queries AE2 API (`IGridHost`, `IGridNode`, etc.) for network topology. Uses force-directed layout algorithm for auto node placement.
  - Config: `[monitor] topologyRefreshInterval`, `topologyMaxNodes`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 14
- **ID**: 014
- **Name**: 仪表盘模式 / Dashboard Monitor Mode
- **Category**: Data Visualization & Monitoring
- **Priority**: B
- **Description**: Transforms the monitor into a customizable multi-widget dashboard. Players can select from a library of preset "widgets" (AE2 storage gauge, energy bar, CPU usage, online player list, recent crafting jobs, etc.) and freely drag-arrange them on the monitor. Layout is saved to NBT, supporting multiple preset pages.
- **Lore**: A GTNH base's control room needs a "data wall". Dashboard mode turns the monitor into a freely-layoutable control panel, letting each engineer monitor their most important metrics according to their preferences.
- **Visual & AI Prompts**:
  - Visual Description: Monitor displaying various arranged widgets — circular gauges for AE2 storage percentage, bar progress for energy, digital jump counters for online players, task list panel, etc. Each widget has a cyan border, in a free-drag grid layout. Empty areas are deep navy-black background.
  - EN Prompt: `A monitor screen with customizable dashboard layout, multiple widgets including circular gauges for AE2 storage, bar progress for energy, digital counters for online players, task list panel, each widget with cyan border on deep navy-black background, clean modern dashboard aesthetic, 4K game asset`
  - CN Prompt: `可自定义仪表盘布局的监视器屏幕，多个小部件：AE2存储圆形仪表、能量进度条、在线玩家数字计数器、任务列表面板，每个小部件有青色边框深蓝黑色背景，简洁现代仪表盘美学，4K游戏资产`
- **Technical Approach**:
  - Files: `renders/DashboardRenderer.java` (new), `gui/guiscreen/GuiDashboardConfig.java` (new), modify `TileEntityTeXTech.java`
  - Core Design: `DashboardRenderer` reads widget layout definitions from NBT, calls corresponding render subroutines for each widget. Layout editing happens in GUI, synced to server via `PacketDashboardConfig`.
  - Config: `[monitor] dashboardMaxWidgetsPerPage`
- **Dependencies**: AE2FluidCraft-Rework (required, for querying AE2 data)

### Project 15
- **ID**: 015
- **Name**: 监视器远程查看器 / Remote Monitor Viewer
- **Category**: Data Visualization & Monitoring
- **Priority**: B
- **Description**: A handheld item. Right-clicking opens a view of a bound Advanced Data Monitor's screen remotely. Supports switching between authorized monitors. The interface resembles a small mobile monitor screen. Requires AE2 Wireless Terminal as a crafting component.
- **Lore**: Engineers can't always be in the control room. The Remote Viewer lets you monitor your base's production status in real-time from the depths of a mine, on a space station, or in the End battlefield. It maintains connection with the monitor via AE2's Quantum Bridge technology.
- **Visual & AI Prompts**:
  - Visual Description: A handheld tablet device with deep blue metallic casing, cyan glowing bezel around the screen, showing live monitor display in the center. A small AE2 wireless crystal antenna on the back. When held, the screen is slightly smaller than full-screen, with a 3D metallic bezel.
  - EN Prompt: `A handheld tablet device with deep blue metallic casing, cyan glowing bezel around screen, showing live monitor display in center, small AE2 wireless crystal antenna on back, Minecraft item style, sci-fi portable device, 4K game asset render`
  - CN Prompt: `手持平板设备，深蓝金属外壳，屏幕边框为青色发光线条，中央显示监视器实时画面，背面有小型AE2无线水晶天线，Minecraft物品风格，科幻便携设备，4K游戏资产渲染`
- **Technical Approach**:
  - Files: `items/ItemRemoteMonitorViewer.java` (new), `gui/guiscreen/GuiRemoteViewer.java` (new), `client/PocketOverlayHandler.java` (reference overlay pattern)
  - Core Design: Right-click opens `GuiRemoteViewer`, which queries `TileEntityTeXTech` list to select which monitor to view. Display updates via periodic `PacketMonitorFrameSync` (similar to remote desktop).
  - Config: `[remoteViewer] frameSyncInterval`, `maxViewerDistance`
- **Dependencies**: AE2FluidCraft-Rework (required, for Wireless Terminal material)

---

## 3. AI Assistant Enhancements

### Project 16
- **ID**: 016
- **Name**: 主动式生产调度 / Proactive Production Scheduler
- **Category**: AI Assistant Enhancements
- **Priority**: S
- **Description**: The AI Assistant evolves from "reactive responses" to "proactive scheduling". It analyzes AE2 network storage trends, predicts which items will soon run short, and auto-places crafting orders. Supports "maintain stock" policies — set minimum stock thresholds for specified items, auto-restock when falling below. Scheduling policies configurable via natural language or GUI.
- **Lore**: A competent factory manager doesn't wait for the production line to stop before adding materials. Proactive Production Scheduler makes the AI Assistant like a tireless factory manager, constantly monitoring, predicting, and adjusting. This is the AI's first step from "tool" to "colleague".
- **Visual & AI Prompts**:
  - Visual Description: AI chat UI with a new "Scheduling" panel showing current inventory policy list. Each policy has an item icon, threshold number, and current stock bar. Below-threshold policies display warning orange. A small trend prediction chart at the top.
  - EN Prompt: `AI chat UI with a scheduling panel showing inventory policy list, each with item icon, threshold number, current stock bar, warning orange for below-threshold items, small trend prediction chart at top, cyan and blue sci-fi UI theme, 4K interface mockup`
  - CN Prompt: `AI聊天界面带调度面板，显示库存策略列表，每条有物品图标、阈值数字、当前库存条，低于阈值显示警告橙色，顶部有小型趋势预测图，青色蓝色科幻UI主题，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantProactiveScheduler.java` (new), `assistant/AssistantInventoryPolicy.java` (new), modify `assistant/AssistantController.java`
  - Core Design: `AssistantProactiveScheduler` runs in server tick (driven by `HandlerTick`). Maintains `Map<String, InventoryPolicy>`. Each tick checks all policy item stocks; below threshold generates `AssistantIntent(ORDER, ...)` and submits to `AssistantCraftJobManager`.
  - NBT Structure (policy storage): `{policies: [{itemId: "minecraft:iron_ingot", minStock: 1000, meta: 0}, ...]}`
  - Integration: Reuses `AssistantCraftJobManager` and `AssistantServerServices` ordering logic.
  - Config: `[ai] schedulerTickInterval`, `maxPoliciesPerPlayer`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 17
- **ID**: 017
- **Name**: 自然语言工厂蓝图 / Natural Language Factory Blueprint
- **Category**: AI Assistant Enhancements
- **Priority**: A
- **Description**: Players can describe desired factory layouts in natural language (e.g., "Design an automation line producing 2 HV circuits per minute"). The AI Assistant analyzes the requirement and generates a detailed crafting chain diagram (machine types, quantities, logistics connections). Generated blueprints can be saved, shared, and executed step-by-step through the Planner.
- **Lore**: GTNH crafting chains are intimidatingly complex. Let AI plan them — it knows every item's crafting recipe in the AE2 network better than any human. Describe your needs, and let it draw the roadmap.
- **Visual & AI Prompts**:
  - Visual Description: AI chat window displaying a generated multi-level crafting flow diagram. Each node is a machine icon with name. Connections show item flow with color gradient (raw→intermediate→finished). Text summary below: "Requires 3 Compressors, 2 Assemblers, estimated 2048 EU/t".
  - EN Prompt: `AI chat window displaying a generated multi-level crafting flow diagram, each node is a machine icon with name, connecting lines showing item flow with color gradient from raw to finished, text summary below with machine counts and power estimates, cyan sci-fi UI, 4K mockup`
  - CN Prompt: `AI聊天窗口显示生成的多层级合成流程图，每个节点是机器图标带名称，连线表示物品流向颜色渐变，下方文字摘要含机器数量和电力估计，青色科幻UI，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantBlueprintGenerator.java` (new), `assistant/CraftingGraphBuilder.java` (new), modify `assistant/AssistantAiIntentService.java`
  - Core Design: `AssistantBlueprintGenerator` calls AI API for crafting chain description, parsed into structured data. `CraftingGraphBuilder` uses GT5 and AE2 recipe APIs to verify and complete details. Rendered in `GuiAIChat`.
  - Config: `[ai] blueprintMaxDepth`, `blueprintTimeout`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 18
- **ID**: 018
- **Name**: 多模型 AI 切换 / Multi-Model AI Switcher
- **Category**: AI Assistant Enhancements
- **Priority**: A
- **Description**: Allows players to configure different AI models for different tasks. Example: DeepSeek-V3 for crafting queries (economical), DeepSeek-R1 for complex blueprint planning (strong reasoning), GPT-4o-mini for casual chat (fast). Configure each model's API endpoint and purpose via GUI. Supports hot-switching.
- **Lore**: No single AI model suits all tasks. Like you wouldn't use a wrench to tighten a screw, smart engineers choose the best model for each scenario. Multi-Model Switcher maximizes your performance/cost ratio.
- **Visual & AI Prompts**:
  - Visual Description: AI settings GUI with "Model Configuration" panel showing multiple model cards. Each card displays model name, robot icon in different colors, status indicator, specialization tags. Active model card highlighted with cyan glow border.
  - EN Prompt: `AI settings GUI with model configuration panel showing multiple model cards, each with model name, robot icon in different colors, status indicator, tags for specialization areas, active model card highlighted with cyan glow border, sci-fi dark theme UI, 4K interface mockup`
  - CN Prompt: `AI设置GUI中的模型配置面板，多张模型卡片，每张显示模型名称、不同颜色机器人图标、状态指示器、擅长领域标签，活跃模型卡片有青色光晕边框，科幻暗色主题UI，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/ai/AiModelRegistry.java` (new), modify `assistant/ai/DeepSeekChatClient.java`, modify `gui/guiscreen/GuiAISettings.java`
  - Core Design: `AiModelRegistry` manages multiple `ChatRequestOptions` instances. `AssistantController` selects model based on intent type. Configuration persisted to `assistant-features.json` and client local config.
  - Config: `[ai] enableMultiModel`, `modelSelectionStrategy`
- **Dependencies**: None additional

### Project 19
- **ID**: 019
- **Name**: AI 语音对话 / AI Voice Conversation
- **Category**: AI Assistant Enhancements
- **Priority**: B
- **Description**: Complete voice conversation loop — player speaks request → AI STT transcribes → AI processes → text reply converted to speech → player hears AI's voice reply. Supports multiple TTS engines (Edge TTS, OpenAI TTS). Different voice styles selectable (calm, energetic, robotic).
- **Lore**: Typing is too slow. When both hands are busy operating machines, voice is the most natural interaction method. AI Voice Conversation lets you truly communicate like talking to an engineering colleague while controlling the entire AE2 network.
- **Visual & AI Prompts**:
  - Visual Description: AI chat UI with "Voice Mode" button (microphone icon). When active, an animated sound waveform appears at the bottom. AI replies display text appearing word by word while playing synthesized speech. Speaker icon beside message bubbles.
  - EN Prompt: `AI chat UI with voice mode active, microphone button glowing, animated sound waveform at bottom moving with speech, text appearing word by word with speaker icon beside message bubble, sci-fi voice interface, cyan accents, 4K mockup`
  - CN Prompt: `AI聊天界面语音模式激活，麦克风按钮发光，底部声音波形动画随说话跳动，文字逐字出现消息气泡旁有扬声器图标，科幻语音界面，青色强调色，4K界面模型`
- **Technical Approach**:
  - Files: `voice/TtsClient.java` (new), `voice/EdgeTtsProvider.java` (new), `voice/OpenAiTtsProvider.java` (new), modify `gui/guiscreen/GuiAIChat.java`
  - Core Design: `TtsClient` interface defines `synthesize(text, voiceStyle) → byte[]`. Providers implement specific TTS APIs. Audio played via `javax.sound.sampled` (Java 8 compatible) or Forge's `SoundHandler`.
  - Integration: Forms complete voice loop with `voice/VoskSpeechToTextClient.java` and `voice/HttpSpeechToTextClient.java`.
  - Config: `[voice] ttsProvider`, `ttsVoiceStyle`, `ttsVolume`
- **Dependencies**: None additional

### Project 20
- **ID**: 020
- **Name**: AI 异常检测 / AI Anomaly Detection
- **Category**: AI Assistant Enhancements
- **Priority**: B
- **Description**: AI Assistant continuously monitors AE2 network and GT machine array metrics, proactively alerting when anomaly patterns are detected. Anomalies include: machine suddenly stopping, abnormal energy consumption spike, unexpected inventory decrease, stuck crafting jobs, etc. Alerts via chat messages, HUD flashing, and sound effects (voice announcements).
- **Lore**: The worst fear during overnight AFK: a critical machine gets stuck, discovered only 8 hours later. AI Anomaly Detection is your 24/7 on-duty engineer — no anomaly escapes its data eye.
- **Visual & AI Prompts**:
  - Visual Description: Screen edge with orange-red flashing warning border. Central HUD popup card showing anomaly details (machine name, type, suggested action). Automatic warning message in AI chat. Sound effect is a short electronic alarm.
  - EN Prompt: `Screen edge with orange-red flashing warning border, central HUD popup card showing anomaly details with machine name, type and suggested action, automatic warning message in AI chat, sci-fi alert UI, dramatic lighting, 4K interface mockup`
  - CN Prompt: `屏幕边缘橙红色闪烁警告边框，HUD中央弹出异常详情卡片显示机器名称、类型和建议措施，AI聊天窗口自动发送警告消息，科幻警报UI，戏剧性光影，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantAnomalyDetector.java` (new), `client/GuiAlertOverlay.java` (new), modify `handler/HandlerTick.java`
  - Core Design: `AssistantAnomalyDetector` samples AE2 network metrics in server tick, compares against historical baseline using simple statistical methods (moving average + standard deviation).
  - Config: `[ai] anomalyDetectionEnabled`, `anomalyCheckInterval`, `anomalyThreshold`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 21
- **ID**: 021
- **Name**: AI 任务委派 / AI Task Delegation
- **Category**: AI Assistant Enhancements
- **Priority**: C
- **Description**: In multiplayer servers, the AI Assistant can decompose tasks from one player's command into subtasks delegated to other online players. E.g., an admin says "have everyone help collect 10000 iron ingots", the AI sends subtask requests to each online player, displaying in their Planners. Supports task progress summary and completion tracking.
- **Lore**: In large GTNH servers, one person's power is limited. AI Task Delegation turns your AI into the team's project manager — splitting tasks, assigning people, tracking progress. This is the leap from solo automation to team collaboration automation.
- **Visual & AI Prompts**:
  - Visual Description: AI chat window showing task delegation panel with subtask list, recipient names with player heads and progress bars, total progress summary for admin. "Delegated Tasks" category in Planner HUD.
  - EN Prompt: `AI chat window showing task delegation panel with subtask list, recipient names with player heads and progress bars, total progress summary for admin, "Delegated Tasks" category in planner HUD, cyan team-collaboration UI, 4K mockup`
  - CN Prompt: `AI聊天窗口显示任务委派面板，子任务列表含接收者名称、玩家头像和进度条，管理员可看总进度汇总，计划器HUD中新增"委派任务"分类，青色团队协作UI，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantTaskDelegation.java` (new), modify `assistant/PlannerServerService.java`, modify `network/packet/PacketAssistantAction.java`
  - Core Design: `AssistantTaskDelegation` manages task decomposition and assignment logic. Task data stored in server planner entries via NBT, distinguishing "delegated" from "personal" tasks.
  - Config: `[ai] delegationEnabled`, `maxDelegatedTasksPerPlayer`
- **Dependencies**: None additional

---

## 4. Items & Tools

### Project 22
- **ID**: 022
- **Name**: 数据之手 / Hand of Data
- **Category**: Items & Tools
- **Priority**: S
- **Description**: Legendary handheld tool that can "draw" data streams in the air. Right-click a block to extract its data type into the tool (like Data Imprint but more powerful), then left-click another block to "inject" the data. Can batch-transfer block NBT data, energy storage, fluid levels, etc. Advanced mode can copy entire multiblock structure states (massive energy cost).
- **Lore**: If the Data Imprint is a pen, the Hand of Data is a chisel. It's not recording data — it's moving reality. Legend says this tool was forged from a Super Orange pit and fragments of the Empyrean Holy Judgment's guard, merging the data essence of two legendary items.
- **Visual & AI Prompts**:
  - Visual Description: A deep navy-black metallic gauntlet (or claw tool). Glowing cyan data stream patterns along knuckles. A rotating orange crystal (orange pit) on the back, surrounded by tiny golden data particles. Leaves cyan trails in the air when swung.
  - EN Prompt: `A deep navy-black metallic gauntlet tool, glowing cyan data stream patterns along knuckles, rotating orange crystal on back surrounded by tiny golden data particles, leaving cyan trails when swung, legendary item with particle effects, Minecraft item style, 4K game asset`
  - CN Prompt: `深蓝黑色金属手套/爪形工具，指节处发光青色数据流纹路，手背中央旋转橙色水晶周围环绕细小金色数据粒子，挥舞时留下青色轨迹，传奇物品带粒子效果，Minecraft物品风格，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemHandOfData.java` (new), `handler/HandlerHandOfData.java` (new), `network/packet/PacketHandOfData.java` (new)
  - Core Design: Right-click reads target TileEntity NBT and compresses it into item NBT. Left-click applies to target via `TileEntity.readFromNBT` merge. Multiblock copy detects structure and applies incrementally.
  - NBT Structure: `{storedData: {tileNBT: {...}, energyStored: 10000, fluidTanks: [...]}, mode: "extract"|"inject", energyRequired: 1000000}`
  - Rendering: Item render (`IItemRenderer`) + particle trail (`EntityFX` subclass).
  - Config: `[handOfData] energyCostPerTransfer`, `maxStoredNbtSize`, `enableMultiBlockCopy`
- **Dependencies**: GT5-Unofficial (required, for energy system)

### Project 23
- **ID**: 023
- **Name**: 量子扳手 / Quantum Wrench
- **Category**: Items & Tools
- **Priority**: B
- **Description**: Universal wrench tool that can rotate any GT, AE2, and this mod's blocks. Shift+right-click opens a config interface to adjust block I/O faces, redstone mode, security settings, etc. Features "preview mode" — when held, all interactable block face orientations are highlighted with cyan lines.
- **Lore**: Dealing with dozens of different mods' wrenches in GTNH is painful. The Quantum Wrench unifies them all — it reads each block's API through quantum resonance, auto-adapting rotation logic. No more wrench-filled inventory.
- **Visual & AI Prompts**:
  - Visual Description: A high-tech wrench with deep blue metal grip. A glowing cyan quantum ring at the head displays the targeted block name. In preview mode, pointed block faces show cyan border highlights. On shift+right-click, the ring expands into a small holographic config panel.
  - EN Prompt: `A high-tech wrench tool, deep blue metal grip, glowing cyan quantum ring at head displaying targeted block name, cyan borders on block faces in preview mode, expanding to small holographic config panel on shift-right-click, sci-fi tool design, 4K game asset`
  - CN Prompt: `高科技扳手工具，深蓝金属握把，头部发光青色量子环显示目标方块名称，预览模式下方块面显示青色边框高亮，Shift+右键时环扩展为小型全息配置面板，科幻工具设计，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemQuantumWrench.java` (new), `handler/HandlerQuantumWrench.java` (new)
  - Core Design: Detects target block's `ForgeDirection API`, calls `Block.rotateBlock` or GT5's `IGregTechDeviceInformation` interface. Config panel via `GuiScreen` subclass.
  - Config: `[quantumWrench] previewModeEnabled`, `previewRenderDistance`
- **Dependencies**: GT5-Unofficial (required), AE2FluidCraft-Rework (required)

### Project 24
- **ID**: 024
- **Name**: 数据护盾 / Data Shield
- **Category**: Items & Tools
- **Priority**: B
- **Description**: Handheld defense item. Right-click activates a data shield centered on the player. The shield consists of rotating cyan hexagonal data panels (resembling AE2 terminal's hexagonal UI). When the shield absorbs damage, affected panels shatter and require time to regenerate. Shield strength is proportional to the AE2 network's stored energy.
- **Lore**: In GTNH's dangerous world, physical armor has limits. The Data Shield extends the concept of digital security to the physical domain — the stronger your AE2 network, the stronger your protection. Essentially, you're defending yourself with data itself.
- **Visual & AI Prompts**:
  - Visual Description: Player surrounded by floating hexagonal semi-transparent cyan panels rotating slowly. AE2 terminal-like UI lines on panels. When hit, panels shatter into data particles then reform. Subtle cyan glow on the player body when active.
  - EN Prompt: `A player surrounded by floating hexagonal semi-transparent cyan panels rotating slowly, AE2 terminal-like UI lines on panels, panel shatters into data particles when hit then reforms, subtle cyan glow on player body, sci-fi energy shield, Minecraft combat style, 4K game scene`
  - CN Prompt: `玩家被多个悬浮旋转的六边形半透明青色面板包围，面板上有AE2终端式UI线条，受击时面板碎裂为数据粒子然后重组，玩家身体有微弱青色辉光，科幻能量护盾，Minecraft战斗风格，4K游戏场景`
- **Technical Approach**:
  - Files: `items/ItemDataShield.java` (new), `handler/HandlerDataShield.java` (new), `renders/RenderDataShield.java` (new)
  - Core Design: On activation, sets player `absorbAmount`. Each absorbed damage deducts AE2 network energy (via `IAEEnergyGrid` query). Shield rendered in `RenderWorldLastEvent`.
  - Config: `[dataShield] energyPerDamagePoint`, `maxShieldStrength`, `rechargeRate`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 25
- **ID**: 025
- **Name**: 便携式数据编织器 / Portable Data Weaver
- **Category**: Items & Tools
- **Priority**: B
- **Description**: Handheld version of a weave cell. When in the inventory, slowly converts basic materials into advanced ones (iron→steel, coal→diamond). Conversion rate is much slower than the block version but doesn't require AE2 network connection. Ideal for emergency resupply during exploration. Consumes durability or battery energy.
- **Lore**: Portable data weaving is a natural evolution. While efficiency drops dramatically, pulling out a portable weaver from your backpack during long expeditions to turn stone into urgently needed tool materials feels like magic — no wait, it's science.
- **Visual & AI Prompts**:
  - Visual Description: A pocket-watch shaped handheld device smaller than a normal weave cell. When opened, reveals a tiny glowing grid weave array inside. Deep blue metal casing with cyan glass. GT battery slot on the side. Emits a low hum and faint cyan light when active.
  - EN Prompt: `A pocket-watch shaped handheld device smaller than normal weave cell, opened to reveal tiny glowing grid weave array inside, deep blue metal casing with cyan glass, GT battery slot on side, emitting low hum and faint cyan light when active, steampunk-sci-fi hybrid, 4K game asset`
  - CN Prompt: `怀表形状的迷你手持编织设备，打开后内部有发光的微小网格编织阵列，深蓝金属外壳配青色玻璃，侧面有GT电池插槽，运行时发出低沉嗡鸣和微弱青色光，蒸汽朋克科幻混合风，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemPortableWeaver.java` (new), `handler/HandlerPortableWeaver.java` (new)
  - Core Design: In `onUpdate`, checks inventory for input materials, slowly consumes energy for conversion. Conversion recipes read from weave cell recipe API (at reduced rate).
  - Integration: Reuses weave cell recipe mapping logic.
  - Config: `[portableWeaver] energyCapacity`, `conversionRateMultiplier`
- **Dependencies**: GT5-Unofficial (required, for battery system)

---

### Project 26
- **ID**: 026
- **Name**: 数据罗盘 / Data Compass
- **Category**: Items & Tools
- **Priority**: B
- **Description**: A handheld compass-like item that points not north, but toward the player's most recently used machine/storage system location. Can be bound to specific block types (e.g., "nearest ME Drive", "nearest GT Fusion Reactor"), with HUD displaying direction and distance. Shift+right-click cycles through tracked target types.
- **Lore**: Getting lost in a massive GTNH base is common. The Data Compass uses AE2 network positioning protocols to track the coordinates of any registered block — essentially a "block search engine."
- **Visual & AI Prompts**:
  - Visual Description: A circular compass device with deep blue dial, glowing cyan arrow pointer, rotating outer ring showing target type names. Silver metal casing. HUD mode shows a small directional arrow indicator at screen edge.
  - EN Prompt: `A circular compass device with deep blue dial, glowing cyan arrow pointer, rotating outer ring with target type names, silver metal casing, small directional arrow on HUD, sci-fi navigation tool, 4K game asset`
  - CN Prompt: `圆形罗盘设备，深蓝表盘，发光青色箭头指针，旋转外圈刻有目标类型名称，银色金属外壳，HUD上小型方向箭头指示器，科幻导航工具，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataCompass.java` (new), `client/DataCompassHudRenderer.java` (new)
  - Core Design: `ItemDataCompass` scans loaded chunks server-side for TileEntities, sorts by distance to find nearest match. Client calculates direction angle from player's view.
  - NBT Structure: `{targetType: "ME_DRIVE", targetX: 100, targetY: 64, targetZ: 200, distance: 45.2}`
  - Integration: `PacketCompassSync` (S→C sync of target coordinates)
  - Rendering: HUD directional arrow at screen edge
  - Config: `[dataCompass] scanRadius`, `updateInterval`
- **Dependencies**: None additional

### Project 27
- **ID**: 027
- **Name**: 数据镜片 / Data Lens
- **Category**: Items & Tools
- **Priority**: C
- **Description**: A helmet attachment item (similar to Baubles accessory). When equipped and hotkey activated, enters "Data Vision" mode. In this mode, all AE2 network components are highlighted in status colors (green=normal, yellow=warning, red=fault), GT machine energy reserves display as floating numbers above, and items flowing in pipes appear as glowing lines. Continuously consumes energy from backpack energy cells.
- **Lore**: Wearing the Data Lens, you no longer see the rough exterior of machine blocks but the underlying data flows. This is the engineer's "third eye" — a wearable data monitor.
- **Visual & AI Prompts**:
  - Visual Description: A monocle-style device with semi-transparent cyan lens, fine data stream patterns on metal rim. When active, lens emits faint cyan glow. Player vision shows AE2/GT blocks highlighted in status colors, flowing particles in pipes visible through walls.
  - EN Prompt: `A monocle device with semi-transparent cyan lens, fine data stream patterns on metal rim, faint cyan glow when active, vision overlay showing AE2/GT blocks highlighted in status colors and flowing particles in pipes visible through walls, cyberpunk visual style, 4K game scene`
  - CN Prompt: `单片眼镜式设备，淡青色半透明镜片，金属边框有细微数据流纹路，激活时发出微弱青色光芒，视野中AE2/GT方块被状态颜色高亮，管道中流动粒子透过墙壁可见，赛博朋克视觉风格，4K游戏场景`
- **Technical Approach**:
  - Files: `items/ItemDataLens.java` (new), `renders/DataVisionRenderer.java` (new)
  - Core Design: `ItemDataLens` as Baubles accessory, checks hotkey in tick. When active, renders block highlight boxes and pipe particles via `RenderWorldLastEvent`. Scans nearby TileEntities via `World.getTileEntity`.
  - NBT Structure: `{active: true, energyStored: 10000, highlightMode: "status"}`
  - Rendering: GL11 wireframe and text in world space
  - Config: `[dataLens] energyPerTick`, `maxRenderDistance`, `highlightColors`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 28
- **ID**: 028
- **Name**: 量子通讯器 / Quantum Communicator
- **Category**: Items & Tools
- **Priority**: C
- **Description**: A handheld communication device allowing players to send text messages between any dimensions. Connects via AE2 Quantum Bridge. Supports group chat (guild channel) and private messaging. Messages fade in and out as cyan semi-transparent text at screen center. Can send quick commands (e.g., "need iron ingots" parsed by AI Assistant for ordering).
- **Lore**: When you're on a space station and your teammate is in the overworld, chat distance limits are frustrating. The Quantum Communicator uses AE2 quantum entanglement principles for cross-dimensional real-time communication. Incidentally, it also serves as a remote voice terminal for the AI Assistant.
- **Visual & AI Prompts**:
  - Visual Description: A handheld walkie-talkie device, deep blue metal casing, glowing cyan antenna on top, small screen on front showing last message, channel knob on side. Antenna tip flashes when transmitting.
  - EN Prompt: `A handheld walkie-talkie device, deep blue metal casing, glowing cyan antenna on top, small screen on front showing last message, channel knob on side, antenna tip flashing when transmitting, sci-fi communication device, 4K game asset`
  - CN Prompt: `手持对讲机式设备，深蓝金属外壳，顶部发光青色天线，正面小屏幕显示最后一条消息，侧面频道切换旋钮，发消息时天线顶端闪烁，科幻通讯设备，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemQuantumCommunicator.java` (new), `network/packet/PacketQuantumMessage.java` (new)
  - Core Design: `ItemQuantumCommunicator` opens `GuiQuantumCommunicator` GUI. Messages create network packets broadcast to all players holding communicators. Message storage and routing handled server-side.
  - Network: `PacketQuantumMessage` (C→S→all target C)
  - Config: `[quantumCommunicator] requireQuantumBridge`, `maxMessageLength`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 29
- **ID**: 029
- **Name**: 数据光剑 / Data Lightblade
- **Category**: Items & Tools
- **Priority**: C
- **Description**: A melee weapon woven from pure data, resembling a laser sword. The blade consists of flowing cyan data particles (non-physical, pure light effect). Damage scales with the number of item types stored in the player's AE2 network (more types = higher damage). Attacks have a chance to trigger "Data Overload" — briefly stunning the target (slowness + blindness).
- **Lore**: If matter can be woven from data, so can weapons. The Data Lightblade's blade is not steel, but a high-density compressed stream of digitized particles. It doesn't cut flesh — it severs the target's connection to the world's data layer. That's why hits cause "lag."
- **Visual & AI Prompts**:
  - Visual Description: A lightsaber/energy blade weapon, deep blue metal hilt with orange crystal (Super Orange pit), blade as a flowing cyan data light beam with binary digits scrolling on surface. Leaves short cyan afterimages when swung. Blade length varies dynamically with network type count.
  - EN Prompt: `A lightsaber-style energy blade weapon, deep blue metal hilt with orange crystal, blade is a flowing cyan data light beam with binary digits scrolling on surface, leaving short cyan afterimages when swung, blade length varies with network type count, sci-fi melee weapon, 4K game asset`
  - CN Prompt: `光剑风格能量武器，深蓝金属剑柄配橙色水晶，剑刃是流动的青色数据光束表面有二进制数字滚动，挥动时留下短暂青色残影，剑刃长度随网络品类数动态变化，科幻近战武器，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataLightblade.java` (new), `renders/RenderDataLightblade.java` (new)
  - Core Design: `ItemDataLightblade` as sword item, queries AE2 network type count on attack for damage calculation. Stun effect applied via `PotionEffect`.
  - NBT Structure: `{networkItemTypes: 5000, damageBoost: 15.0}`
  - Rendering: `IItemRenderer` for hilt + dynamic beam blade (Tessellator + GL11 lines)
  - Config: `[dataLightblade] baseDamage`, `damagePerItemType`, `overloadChance`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 5. Blocks & Machines

### Project 30
- **ID**: 030
- **Name**: 数据投影仪 / Data Projector
- **Category**: Blocks & Machines
- **Priority**: A
- **Description**: A block that "projects" an Advanced Data Monitor's display onto a distant large screen. The screen is a flat surface built from specific blocks (smooth quartz), and the projector beams cyan light onto it to render the monitor's content. Projection sizes can be very large (up to 8x8 blocks). Ideal for building large display walls in base control centers.
- **Lore**: A single monitor isn't impressive enough for a large control room. The Data Projector lets you build a true "data wall" — standing before it, your entire base's production status is visible at a glance. This is the leap from an engineer's desk to a command center.
- **Visual & AI Prompts**:
  - Visual Description: A small projector block with a glowing cyan lens on the front. When active, the lens projects a cyan beam onto distant quartz screen blocks, rendering live monitor content (charts, numbers, waveforms). Tiny data particles float in the beam.
  - EN Prompt: `A small projector block with glowing cyan lens, projecting cyan beam onto distant quartz screen blocks showing live monitor content with charts and numbers, tiny data particles floating in beam, Minecraft command center aesthetic, 4K game scene`
  - CN Prompt: `小型投影仪方块，发光青色镜头，向远处石英屏幕投射青色光束，屏幕方块上实时渲染监视器内容（图表数字波形），光束中有微小数据粒子漂浮，Minecraft指挥中心美学，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataProjector.java` (new), `tileentity/TileEntityDataProjector.java` (new), `renders/ScreenOverlayRenderer.java` (new)
  - Core Design: `TileEntityDataProjector` binds to a `TileEntityTeXTech`, reads its render data, projects onto NBT-specified screen coordinate area.
  - Config: `[projector] maxScreenArea`, `beamParticleDensity`
- **Dependencies**: None additional

### Project 31
- **ID**: 031
- **Name**: 数据信标 / Data Beacon
- **Category**: Blocks & Machines
- **Priority**: B
- **Description**: A beacon-like block placed in the center of a base, shooting a vertical data beam (cyan, with data particles) into the sky. The beam's height and brightness represent the base's "data strength" — calculated from AE2 network storage, machine count, and energy output. Beam color changes based on main activity (cyan=normal, gold=high output, purple=weaving).
- **Lore**: Every GTNH base has its own "heartbeat". The Data Beacon visualizes this heartbeat — letting all visitors see at a distance how powerful your base is. On multiplayer servers, this is both an honor display and a form of deterrence.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 base block (like a beacon base but more industrial). A vertical cyan beam shoots into the sky, made of rotating data particle rings spiraling upward. Beam color and brightness change dynamically. Spectacular at night.
  - EN Prompt: `A beacon-like block with industrial base, shooting a vertical cyan light beam into the sky made of rotating data particle rings spiraling upward, dynamic color and brightness based on base activity level, spectacular at night, Minecraft sci-fi landmark, 4K game scene`
  - CN Prompt: `信标式方块带工业底座，向天空发射由旋转数据粒子环组成的螺旋上升青色光柱，颜色和亮度根据基地活跃度动态变化，夜间尤为壮观，Minecraft科幻地标，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataBeacon.java` (new), `tileentity/TileEntityDataBeacon.java` (new), `renders/RenderDataBeacon.java` (new)
  - Core Design: `TileEntityDataBeacon` queries AE2 network data each tick to calculate "data strength" score. `RenderDataBeacon` renders the beam with TESR (can extend to world height cap). Particle rings use `EntityFX` subclass.
  - Config: `[dataBeacon] baseBeamHeight`, `strengthCalculationWeights`, `particleDensity`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 32
- **ID**: 032
- **Name**: 数据回收器 / Data Recycler
- **Category**: Blocks & Machines
- **Priority**: A
- **Description**: A machine block that can "recycle" unwanted items into "item type data" in the AE2 network — increasing the network's item type count without adding actual items. Equivalent to exchanging physical items for data weaving "raw material". Higher rarity items produce more type data. Output usable for systems like Chrono-Weave Cells that need "type count".
- **Lore**: Physical items are the condensed state of data. The Data Recycler reverses this process — deconstructing matter back into pure type information. Recycled items don't disappear (physically they're converted to energy), but their concept is added to the network's data layer.
- **Visual & AI Prompts**:
  - Visual Description: A vertical machine block with a hopper-like input on top and energy port at bottom. When active, a rotating data particle ring circles the input. The item is enveloped in a cyan beam and gradually "deconstructed" into a rising binary digit stream. A counter on the side shows current recycling efficiency.
  - EN Prompt: `A vertical machine block with hopper-like input on top, energy port at bottom, rotating data particle ring around input when active, cyan beam enveloping item as it deconstructs into rising binary digit stream, efficiency counter on side, GT industrial style, 4K game asset`
  - CN Prompt: `竖直机器方块，顶部漏斗状投入口，底部能量接口，运行时投入口周围旋转数据粒子环，物品被青色光束包裹逐渐解构为上升的二进制数字流，侧面有效率计数器，GT工业风格，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataRecycler.java` (new), `tileentity/TileEntityDataRecycler.java` (new), `gui/guiscreen/GuiDataRecycler.java` (new)
  - Core Design: Implements `ISidedInventory` to receive items. Recycling consumes GT5 energy, then adds item's "type value" to AE2 network. Type value calculated from GT5 material rarity tier.
  - Config: `[dataRecycler] rarityToTypeValueMap`, `energyPerRecycle`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 33
- **ID**: 033
- **Name**: 自动编织塔 / Auto-Weave Tower
- **Category**: Blocks & Machines
- **Priority**: A
- **Description**: A multiblock structure (3x3x4 tower shape) that automatically runs data weaving tasks. Materials enter at the bottom (via ME Interface or pipes), woven products exit at the top. GUI allows setting weaving strategy (prioritize speed/efficiency/type diversity). Supports multiple cells working simultaneously. Large cooling fins on the sides consume coolant.
- **Lore**: Manually managing weave cells is like manually adding coal to a furnace — unacceptable in the industrial age. The Auto-Weave Tower is the fully automated version of data weaving, tirelessly converting data potential into material reality 24/7.
- **Visual & AI Prompts**:
  - Visual Description: A tall industrial tower multiblock (3x3x4), deep blue metal body, glowing weave array windows on each floor showing weaving process inside. Rotating cyan energy ring on top, ME interface at bottom. Large cooling fins on sides with coolant flowing in transparent pipes.
  - EN Prompt: `A tall industrial tower multiblock 3x3x4, deep blue metal body, glowing weave array windows on each floor showing weaving process inside, rotating cyan energy ring on top, ME interface at bottom, large cooling fins on sides with coolant flowing in transparent pipes, GregTech mega-machine aesthetic, 4K game asset`
  - CN Prompt: `高耸的工业塔楼多方块3x3x4，深蓝金属主体，各层发光编织阵列窗口可见内部编织过程，顶部旋转青色能量环，底部ME接口，侧面大型散热鳍片冷却液在透明管道中流动，GregTech巨型机器美学，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockAutoWeaveTower.java`, `tileentity/TileEntityAutoWeaveTower.java`, `gui/guiscreen/GuiAutoWeaveTower.java`
  - Core Design: Follows GT5 multiblock machine pattern. TileEntity validates structure integrity, obtains input items via `IAEItemStorage`, maintains multiple virtual weave slots internally.
  - NBT Structure: `{weaveSlots: [{cellType: "dataDustLoomCell", progress: 0.5, output: {...}}, ...], coolingLevel: 0.8}`
  - Network: `PacketAutoWeaveSync`
  - Config: `[autoWeave] maxParallelWeaves`, `coolingEfficiencyMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 34
- **ID**: 034
- **Name**: 数据充能底座 / Data Charging Pedestal
- **Category**: Blocks & Machines
- **Priority**: B
- **Description**: A pedestal block where items placed on top (such as the Hand of Data, Quantum Communicator, etc.) automatically charge from the AE2 network. No need to remove items and place them in charging slots — simply right-click to place an item on the pedestal, and it charges like wireless charging. The pedestal emits a faint cyan halo.
- **Lore**: Take out tool, put in charger, take out again — these repetitive actions are unacceptable to efficiency-focused engineers. The Data Charging Pedestal turns charging into a "set it down and done" process.
- **Visual & AI Prompts**:
  - Visual Description: A low metal pedestal block with a glowing circular cyan charging area on top. Placed item floats and rotates above, surrounded by upward-flowing cyan data particles. Particles turn gold when fully charged with a notification sound.
  - EN Prompt: `A low metal pedestal block with glowing circular cyan charging area on top, placed item floating and rotating above with cyan data particles flowing upward around it, particles turn gold when fully charged with notification sound, sci-fi wireless charging, 4K game asset`
  - CN Prompt: `低矮金属底座方块，顶部发光圆形青色充电区域，物品悬浮在上方旋转周围环绕向上流动的青色数据粒子，充满后粒子变金色并发出提示音，科幻无线充电，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataChargingPedestal.java` (new), `tileentity/TileEntityDataChargingPedestal.java` (new)
  - Core Design: TileEntity contains single item slot, connects to AE2 energy network. Each tick injects energy into item NBT (if item implements energy interface).
  - NBT Structure: `{storedItem: {...}, chargeRate: 1000}`
  - Rendering: TESR for floating item and particle effects
  - Config: `[chargingPedestal] chargeRate`, `wirelessRange`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 35
- **ID**: 035
- **Name**: 数据传送台 / Data Teleport Pad
- **Category**: Blocks & Machines
- **Priority**: B
- **Description**: A platform block (1x1 step-on type). Standing on it opens a GUI to select a destination (can be bound monitor locations, named base teleport points, or other teleport pads). Teleportation consumes AE2 energy — greater distances cost more. During teleportation, the player is enveloped in a cyan beam and gradually becomes transparent, then reassembles at the new location.
- **Lore**: The grapple system suits short-distance movement, but cross-dimensional travel still requires teleportation. The Data Teleport Pad sends the player's "digital signature" through the AE2 quantum network to the target location, then reassembles them at the destination using data weaving — you briefly become a segment of data during transit.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 metal platform block with circular cyan light patterns on surface (like a miniature stargate). Light beam rises to envelop player who becomes semi-transparent cyan silhouette then shrinks to a point. Reverse animation appears at destination.
  - EN Prompt: `A 1x1 metal platform block with circular cyan light pattern on surface like a miniature stargate, light beam rising to envelop player who becomes semi-transparent cyan silhouette then shrinks to a point, sci-fi teleporter pad, dramatic lighting, 4K game scene`
  - CN Prompt: `1x1金属平台方块，表面有环形青色光纹类似微型星际之门，光柱升起包裹玩家使其变为半透明青色轮廓然后收缩为光点消失，科幻传送平台，戏剧性光影，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataTeleportPad.java` (new), `tileentity/TileEntityDataTeleportPad.java` (new), `gui/guiscreen/GuiTeleportPad.java` (new)
  - Core Design: TileEntity maintains target coordinates. Teleport triggers when player stands on it. Uses `EntityPlayerMP.setPositionAndUpdate` with particle animation to mask the movement.
  - NBT Structure: `{destinationName: "Main Base", destX: 0, destY: 64, destZ: 0, destDim: 0, energyPerTeleport: 5000}`
  - Network: `PacketTeleportPadSync`
  - Config: `[teleportPad] energyPerBlockDistance`, `maxTeleportDistance`, `crossDimensionMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 6. Grapple & Movement

### Project 36
- **ID**: 036
- **Name**: 挂索加速环 / Grapple Boost Ring
- **Category**: Grapple & Movement
- **Priority**: B
- **Description**: During grapple sliding, players can consume energy to trigger a "boost ring" — briefly greatly increasing slide speed (3x-5x) while leaving a cyan energy trail behind. The boost ring has a cooldown. Consecutively passing through multiple boost rings (provided by other grapple nodes) can accumulate speed bonuses.
- **Lore**: The grapple system's standard speed is sufficient for daily commuting, but when you need to rush from one end of the base to the other to fix an about-to-explode reactor, you need "turbo mode". Boost ring technology is essentially a short-term overclock of the grapple slide entity.
- **Visual & AI Prompts**:
  - Visual Description: When boost triggers, the player is surrounded by a glowing cyan ring (moving fast along the line direction). The ring leaves a brief trail behind. During boost, the player's FOV slightly expands and speed lines appear around the environment. Sound is a high-pitched electronic acceleration tone.
  - EN Prompt: `Player on grapple line surrounded by glowing cyan ring moving fast along line direction, leaving short energy trail behind, slight FOV expansion and speed lines around environment, high-pitched electronic acceleration sound, sci-fi movement effect, Minecraft action style, 4K game scene`
  - CN Prompt: `挂索上的玩家被发光青色圆环包围沿线路方向快速移动，留下短暂能量尾迹，FOV略微扩大周围出现速度线效果，高音调电子加速音效，科幻移动特效，Minecraft动作风格，4K游戏场景`
- **Technical Approach**:
  - Files: `handler/GrapplePlayerState.java` (extend), `client/HandlerGrappleClient.java` (extend), `renders/GrappleBoostRenderer.java` (new)
  - Core Design: Extend `GrapplePlayerState` with `boostCooldown`, `boostMultiplier` fields. Client key triggers boost, server validates and applies speed multiplier to `EntityGrappleSlide`.
  - Config: `[grapple] boostSpeedMultiplier`, `boostCooldownTicks`, `boostEnergyCost`
- **Dependencies**: None additional

### Project 37
- **ID**: 037
- **Name**: 量子挂索 / Quantum Grapple
- **Category**: Grapple & Movement
- **Priority**: B
- **Description**: An upgraded grapple hook capable of cross-dimensional use — as long as both dimensions each have a grapple anchor and the nodes are paired via AE2 Quantum Bridge. In dimension A, target a node and right-click to select the paired node in dimension B. The quantum grapple opens a temporary dimensional rift for the player to pass through. The rift closes without a trace afterward.
- **Lore**: Physical grapple lines cannot cross dimensional boundaries. But the Quantum Grapple is different — it transmits not a physical rope but your quantum state information. Using the AE2 Quantum Bridge as a relay, you can "slide" between the stars.
- **Visual & AI Prompts**:
  - Visual Description: When using quantum grapple, the line is no longer a physical rope but a shimmering quantum tunnel — a rotating passage of cyan and purple particles. As the player slides through, warped starfields and binary data streams surround them. The tunnel collapses at the destination.
  - EN Prompt: `Quantum grapple line shown as a shimmering quantum tunnel of cyan and purple particles forming a rotating passage, player sliding through with warped starfield and binary data streams around, tunnel collapsing at destination, interdimensional sci-fi travel, 4K game scene`
  - CN Prompt: `量子挂索线显示为由青色和紫色粒子组成的旋转隧道，玩家在隧道中滑行周围是扭曲星空和二进制数据流，到达时隧道收缩消失，跨维度科幻旅行，4K游戏场景`
- **Technical Approach**:
  - Files: `items/ItemQuantumGrappleHook.java` (new), `handler/GrappleQuantumHandler.java` (new), modify `handler/GrappleNodeIndex.java`
  - Core Design: `ItemQuantumGrappleHook` extends `ItemGrappleHook`, adds cross-dimension selection logic. Transport uses `EntityPlayerMP.travelToDimension` with custom `Teleporter`.
  - NBT Structure: `{pairedNodes: [{dim: 0, nodeIndex: 12}, {dim: 1, nodeIndex: 5}]}`
  - Network: `PacketQuantumGrappleSync`
  - Config: `[grapple] quantumEnabled`, `requireQuantumBridge`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 38
- **ID**: 038
- **Name**: 挂索路径预设 / Grapple Route Presets
- **Category**: Grapple & Movement
- **Priority**: B
- **Description**: Players can save frequently used grapple routes ("Home → Mine → Power Plant") as a preset. Activating a preset causes the character to automatically slide to each node along the route sequentially, without manual aiming and firing. Presets are managed through the grapple hook's GUI and can be named, edited, and shared with other players.
- **Lore**: Manually aiming at each node during daily commutes is tedious. Like bus routes, save your common paths and go with one click — the AI handles mid-route turning and node switching.
- **Visual & AI Prompts**:
  - Visual Description: Grapple hook GUI showing a 2D top-down node map with cyan dots for nodes and glowing cyan lines for preset routes. HUD shows next node direction and ETA during auto-travel. Grapple hook auto-fires and retracts during auto-slide.
  - EN Prompt: `Grapple hook GUI showing 2D top-down node map with cyan dots for nodes and glowing cyan lines for preset routes, HUD showing next node direction and ETA during auto-travel, sci-fi navigation interface, 4K interface mockup`
  - CN Prompt: `挂索器GUI显示2D俯视节点地图，节点为青色圆点预设路径为发光青色连线，自动滑行时HUD显示下一节点方向和预计到达时间，科幻导航界面，4K界面模型`
- **Technical Approach**:
  - Files: `handler/GrappleRoutePreset.java` (new), `gui/guiscreen/GuiGrappleRoutes.java` (new), modify `client/HandlerGrappleClient.java`
  - Core Design: `GrappleRoutePreset` stores node list. Client tick detects arrival at current node and auto-aims at next node.
  - NBT Structure: `{routes: [{name: "Home_to_Mine", nodes: [{dim: 0, idx: 1}, {dim: 0, idx: 5}, {dim: 0, idx: 8}]}]}`
  - Config: `[grapple] maxRoutesPerPlayer`
- **Dependencies**: None additional

### Project 39
- **ID**: 039
- **Name**: 磁悬浮滑板 / Maglev Hoverboard
- **Category**: Grapple & Movement
- **Priority**: C
- **Description**: A rideable hoverboard entity powered by AE2 energy. The player stands on it and can hover 1 block above the ground, moving faster than running but slower than grappling. Ideal for small-range movement within a base. Automatically adjusts hover height uphill. The board emits cyan light from beneath, leaving a brief glowing trail along the path traversed.
- **Lore**: Grappling suits long distances, but is too inflexible for stop-and-go movement within a base. The Maglev Hoverboard fills this gap — it uses the weak magnetic fields generated by AE2 ME cables to levitate. "Sliding" around the base is way cooler than walking.
- **Visual & AI Prompts**:
  - Visual Description: A futuristic hoverboard, deep blue metal deck, two glowing cyan hover rings underneath projecting faint cyan light pads. Particles flow backward when moving. Player stands in surfing/skating stance. Board tilts slightly when turning.
  - EN Prompt: `A futuristic hoverboard, deep blue metal deck, two glowing cyan hover rings underneath projecting faint cyan light pads, particles flowing backward when moving, player standing in surfing/skating stance, board tilting slightly when turning, sci-fi personal transport, 4K game asset`
  - CN Prompt: `未来感悬浮滑板，深蓝金属甲板，底部两个发光青色悬浮环投射淡青色光垫，移动时底部粒子向后流动，玩家站姿类似滑雪，转弯时滑板微微倾斜，科幻个人交通工具，4K游戏资产`
- **Technical Approach**:
  - Files: `entity/EntityHoverboard.java` (new), `items/ItemHoverboard.java` (new), `renders/RenderHoverboard.java` (new)
  - Core Design: `EntityHoverboard` as rideable entity (similar to boat/pig logic). Hovering achieved by fixing entity Y position at block surface +1.0. Movement via WASD adding velocity. Energy consumed each tick.
  - NBT Structure: `{energyStored: 10000, maxEnergy: 100000, rider: null, speed: 0.3}`
  - Rendering: `RenderHoverboard` for board model + hover rings + particle trail
  - Config: `[hoverboard] maxSpeed`, `energyPerTick`, `hoverHeight`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 7. Dimensional Pocket Expansions

### Project 40
- **ID**: 040
- **Name**: 口袋自动整理 / Pocket Auto-Sort
- **Category**: Dimensional Pocket Expansions
- **Priority**: A
- **Description**: Adds auto-sort functionality to the Dimensional Pocket. Configurable sort rules (by name/quantity/mod/rarity), one-click sort button. Advanced mode supports "smart grouping" — auto-stacking same-type items, partitioning tools and materials. Supports whitelist/blacklist (certain items excluded from sort). Sort animation: item icons fly to target positions.
- **Lore**: As the pocket grows larger, manually organizing items becomes increasingly painful. Pocket Auto-Sort uses data weaving's underlying sorting algorithms (essentially a quicksort on item indices) to keep your pocket always organized.
- **Visual & AI Prompts**:
  - Visual Description: Pocket overlay with new sort button (three horizontal lines icon) in the top right. Dropdown with sort options (name/quantity/mod/smart). During sorting, item icons smoothly fly from old to new positions (approximately 0.5 seconds). Cyan-themed UI.
  - EN Prompt: `Pocket overlay with new sort button (three horizontal lines icon) in top right, sort options dropdown menu, animated item icons flying smoothly from old to new position during sorting, cyan UI theme, clean interface design, 4K mockup`
  - CN Prompt: `口袋悬浮窗右上角新增排序按钮（三条线图标），排序选项下拉菜单，整理时物品图标从原位置平滑飞向新位置的动画，青色UI主题，清爽界面设计，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketInventory.java` (extend), `client/GuiPocketOverlay.java` (extend)
  - Core Design: Implement `sortItems(SortRule rule)` in `PocketInventory`. Sort happens server-side, results synced via `PacketPocketSync`. Animation interpolates old→new positions client-side.
  - Config: `[pocket] enableAutoSort`, `sortAnimationTicks`
- **Dependencies**: None additional

### Project 41
- **ID**: 041
- **Name**: 口袋物品检索 / Pocket Item Search
- **Category**: Dimensional Pocket Expansions
- **Priority**: B
- **Description**: Adds a search bar to the pocket overlay. Typing text filters displayed items in real-time (supports item name, mod name, and even item tooltip content). Matching items are highlighted; non-matching items become semi-transparent. Supports regex search in advanced mode. Search history is auto-saved.
- **Lore**: When your pocket contains thousands of item types, scrolling through pages to find one specific item is like finding a needle in a haystack. Pocket search turns "finding" into "typing" — enter a few letters and the desired item is instantly highlighted.
- **Visual & AI Prompts**:
  - Visual Description: Pocket overlay with search bar at top (input field with magnifying glass icon). Non-matching items gradually fade to semi-transparent (50% alpha) while matching items stay highlighted with a single cyan border flash. Clear button ("X") and history dropdown on the right.
  - EN Prompt: `Pocket overlay with search bar at top with magnifying glass icon, non-matching items fading to semi-transparent while matching items highlighted with single cyan border flash, clear button and history dropdown, cyan sci-fi search interface, 4K mockup`
  - CN Prompt: `口袋悬浮窗顶部搜索栏带放大镜图标，输入文字时非匹配物品逐渐变半透明匹配物品高亮闪烁一次青色边框，清除按钮和历史下拉列表，青色科幻搜索界面，4K模型`
- **Technical Approach**:
  - Files: `client/GuiPocketOverlay.java` (extend), `client/PocketOverlayHandler.java` (extend)
  - Core Design: Client-side gets all item data from `PocketClientCache`, filters in real-time as user types in `GuiTextField`. Filtering runs client-side (no network request needed).
  - NBT Structure: `{searchHistory: ["iron", "diamond", ...], searchRegex: false}`
  - Config: `[pocket] searchHistorySize`, `enableRegexSearch`
- **Dependencies**: None additional

### Project 42
- **ID**: 042
- **Name**: 口袋快速存取 / Pocket Quick Access
- **Category**: Dimensional Pocket Expansions
- **Priority**: B
- **Description**: Adds a "Quick Access Bar" to the pocket overlay — 9 fixed slots always displayed at the bottom of the pocket (similar to the vanilla hotbar). Frequently used items can be placed here for direct drag access without page switching. Items in the quick access bar don't count against pocket capacity limits (independent cap).
- **Lore**: Open pocket → flip pages → find tool → use → put back — these steps are too slow in emergencies. The Quick Access Bar is your "pocket hotbar", keeping what you need most at your fingertips.
- **Visual & AI Prompts**:
  - Visual Description: A row of 9 fixed quick-access slots at the bottom of the pocket overlay, separated by a glowing cyan divider line. Quick slot borders are gold (distinguishing them from gray normal slots). Items in these slots stay fixed during page switching. Right-click to pin/unpin items to the quick bar.
  - EN Prompt: `Pocket overlay with a row of 9 fixed quick-access slots at bottom separated by glowing cyan divider line, gold border on quick slots distinguishing from gray normal slots, items stay fixed during page switches, right-click to pin/unpin, 4K interface mockup`
  - CN Prompt: `口袋悬浮窗底部一行9个固定快捷槽位以发光青色分隔线分隔，快捷槽金色边框区别于普通槽灰色，物品在翻页时保持固定，右键固定/取消固定到快捷栏，4K界面模型`
- **Technical Approach**:
  - Files: `handler/PocketInventory.java` (extend), `handler/PocketState.java` (extend), `client/GuiPocketOverlay.java` (extend)
  - Core Design: `PocketInventory` adds 9 independent slot array `quickAccessSlots[9]`. Capacity calculated independently. Stored separately from main storage in NBT.
  - NBT Structure: `{quickAccessSlots: [{slot: 0, item: {...}}, ...], mainSlots: [...]}`
  - Network: Extend `PacketPocketAction` with `PIN_TO_QUICK` / `UNPIN_FROM_QUICK` operation types
  - Config: `[pocket] quickAccessSlotCount` (default 9)
- **Dependencies**: None additional

### Project 43
- **ID**: 043
- **Name**: 口袋物品目录 / Pocket Item Catalog
- **Category**: Dimensional Pocket Expansions
- **Priority**: C
- **Description**: A standalone GUI interface displaying a detailed catalog of all items in the pocket in list form. List columns include: item icon, name, quantity, stack limit, rarity color marker. Supports sorting by column and exporting as JSON file (for external tool analysis). Can jump directly from the catalog to the item's pocket page.
- **Lore**: The pocket overlay is great for quick operations but poor for managing thousands of item types. The Item Catalog provides a "database view" — like a mini AE2 terminal, but showing only your pocket contents.
- **Visual & AI Prompts**:
  - Visual Description: Full-screen GUI catalog interface with sortable item list on left (each row: icon + name + count + rarity color bar), detail panel on right for selected item (NBT data, stacking info, page location). Search bar and export button on top. Deep navy-black background with cyan headers and white text.
  - EN Prompt: `Full-screen GUI catalog interface with sortable item list on left showing icon name count and rarity color bar per row, detail panel on right for selected item, search bar and export button on top, deep navy-black background with cyan headers and white text, AE2 terminal inspired, 4K interface mockup`
  - CN Prompt: `全屏目录GUI，左侧可排序物品列表每行显示图标名称数量稀有度颜色条，右侧选中物品详细信息面板，顶部搜索栏和导出按钮，深蓝黑底青色表头白色文字，AE2终端风格，4K界面模型`
- **Technical Approach**:
  - Files: `gui/guiscreen/GuiPocketCatalog.java` (new), `items/ItemDimensionalPocket.java` (extend right-click menu)
  - Core Design: `GuiPocketCatalog` reads all data from `PocketClientCache` for list rendering. Uses `GuiScrollingList`-style scrolling (custom implementation needed for 1.7.10).
  - NBT Structure: No additional NBT needed; reads directly from cache
  - Config: `[pocket] enableCatalog`
- **Dependencies**: None additional

---

## 8. Combat & Defense

### Project 44
- **ID**: 044
- **Name**: 至高天圣裁·终焉 / Empyrean Holy Judgment - Finality
- **Category**: Combat & Defense
- **Priority**: A
- **Description**: The ultimate evolved form of the Empyrean Holy Judgment legendary sword. Obtained by fusing the original sword with 1 million item types from the AE2 network via the Matter Reconstruction Altar. New effects: "Judgement" — charged attack releases a massive cyan sword wave sweeping 20 blocks forward, destroying all non-boss mobs in the path. "Divine Punishment" — when player HP drops below 10%, auto-triggers a full-screen lightning strike clearing all hostile mobs nearby.
- **Lore**: The Empyrean Holy Judgment was already the pinnacle of data-weave weaponry, but its potential was far from fully realized. When enough "material concepts" are woven into the blade, it ceases to be a weapon — it becomes a portable "reality corrector". Judgement and Divine Punishment are not attacks but "corrections" to reality — correcting hostile entities that shouldn't exist in your world.
- **Visual & AI Prompts**:
  - Visual Description: Empyrean Holy Judgment - Finality is even more dazzling than the original — iridescent data streams flow along the blade (no longer pure cyan). A pulsating golden crystal (larger than the original Super Orange pit) is embedded in the guard. When charging, glowing runes circle the blade. During Judgement, a massive cyan magic circle appears on the ground. During Divine Punishment, the sky briefly darkens, raining cyan lightning.
  - EN Prompt: `An evolved legendary sword with iridescent data streams flowing along blade instead of pure cyan, pulsating golden crystal in guard larger than original, glowing runes circling blade when charging, massive cyan magic circle on ground during Judgement, sky darkening with cyan lightning rain during Divine Punishment, legendary weapon, dramatic lighting, 4K game scene`
  - CN Prompt: `进化传奇剑，剑身上流动彩虹色数据流而非纯青色，剑格处脉动金色水晶比原版更大，蓄力时发光符文环绕剑身，审判时地面出现巨大青色魔法阵，天罚时天空变暗降下青色闪电雨，传奇武器，戏剧性光影，4K游戏场景`
- **Technical Approach**:
  - Files: `items/ItemStarryCosmosSwordFinality.java` (new), modify `handler/HandlerStarryCosmosSword.java`, modify `renders/CosmicStarrySwordRenderer.java`
  - Core Design: Inherits `ItemStarryCosmosSword`, overrides attack logic. Judgement uses ray-tracing for block/entity detection. Divine Punishment triggers via `LivingHurtEvent`.
  - Config: `[finality] judgementRange`, `divinePunishmentThreshold`, `networkTypesRequired`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 45
- **ID**: 045
- **Name**: 数据屏障生成器 / Data Barrier Generator
- **Category**: Combat & Defense
- **Priority**: B
- **Description**: A block that consumes AE2 energy to generate a temporary data barrier. The barrier is a semi-transparent wall made of cyan hexagonal panels (configurable width and height). Hostile mobs cannot pass through, but players and friendly entities can. The barrier has duration and durability values; taking damage accelerates energy consumption.
- **Lore**: Sometimes you need a "smart wall" — one that only blocks enemies while remaining transparent to you. The Data Barrier uses AE2 network entity recognition to project a selectively-passable force field in physical space.
- **Visual & AI Prompts**:
  - Visual Description: A wall made of semi-transparent cyan hexagonal panels with subtle wavering. Ripple effects and particle splash when hostile mobs collide. A small base block projects a sustaining beam toward the wall. Almost transparent when untouched.
  - EN Prompt: `A wall made of semi-transparent cyan hexagonal panels with subtle wavering, ripple effects and particle splash when hostile mobs collide, a small base block projecting sustaining beam toward wall, almost transparent when untouched, sci-fi force field, Minecraft defense style, 4K game scene`
  - CN Prompt: `由半透明青色六边形面板拼接成的墙壁轻微波动，敌对生物撞上激起涟漪效果和粒子飞溅，小型基座方块向墙壁投射维持光束，未被触碰时几乎透明，科幻力场墙，Minecraft防御风格，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataBarrierGenerator.java` (new), `tileentity/TileEntityDataBarrierGenerator.java` (new), `renders/RenderDataBarrier.java` (new)
  - Core Design: TileEntity creates "virtual barrier" in specified area. Checks via `LivingSpawnEvent` or `EntityJoinWorldEvent` whether entities need blocking. For existing mobs, checks collision via `EntityMoveEvent`.
  - NBT Structure: `{barrierWidth: 5, barrierHeight: 3, facingX: 100, facingZ: 200, energyStored: 100000}`
  - Rendering: TESR for hexagonal panel wall + ripple particle effects
  - Config: `[dataBarrier] maxBarrierSize`, `energyPerBlockPerTick`, `barrierDurability`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 46
- **ID**: 046
- **Name**: 超级砂糖桔·进化 / Super Orange - Evolved
- **Category**: Combat & Defense
- **Priority**: B
- **Description**: The evolved form of Super Orange. Obtained by fusing a Super Orange with 500,000 item types from the AE2 network via the Matter Reconstruction Altar. New abilities: "Matter Deconstruction" — right-click a target block to deconstruct it into base materials (e.g., stone→cobblestone→gravel); "Time Causality" — when thrown, creates a time domain on landing where all machines run at 2x speed for 30 seconds.
- **Lore**: The secret of the Super Orange is that it is itself an "undifferentiated data matter". The evolved form further unleashes this potential — it can not only mine but deconstruct, and can distort local time flow. At its core, it is the primordial template of all matter.
- **Visual & AI Prompts**:
  - Visual Description: Super Orange - Evolved is more dazzling — flowing golden data patterns appear on the orange peel, with a tiny cyan halo at the stem. When thrown, it leaves a golden and cyan intertwined trail. Deconstructing blocks fires a scanning light beam. In Time Domain, the orange floats mid-air above a large clock-pattern magic circle.
  - EN Prompt: `An evolved version of Super Orange with flowing golden data patterns on orange peel, tiny cyan halo at stem, leaving golden and cyan intertwined trail when thrown, scanning beam when deconstructing blocks, floating above a large clock-pattern magic circle in Time Domain, legendary fruit, 4K game asset`
  - CN Prompt: `超能砂糖桔进化形态，橙色果皮上有流动金色数据纹路，果蒂处微小青色光环，投掷时留下金色与青色交织尾迹，拆解方块时发射扫描光束，时间领域中悬浮在半空下方大型时钟图案法阵，传奇水果，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemSuperOrangeEvolved.java` (new), modify `handler/HandlerSuperOrange.java`
  - Core Design: Extends `ItemSuperOrange`. Deconstruction reversely queries GT5 material API for block decomposition products. Time domain adjusts `TileEntity.updateEntity` frequency via events.
  - NBT Structure: `{evolved: true, timeDomainCharges: 3, decomposeCharges: 10}`
  - Rendering: Reuse and extend `SuperOrangeHaloRenderer.java`
  - Config: `[superOrange] timeDomainDuration`, `timeDomainSpeedMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 47
- **ID**: 047
- **Name**: 数据哨兵 / Data Sentinel
- **Category**: Combat & Defense
- **Priority**: B
- **Description**: A deployable AI defense entity. Placed around the base, it automatically attacks invading hostile mobs. The sentinel fires cyan energy bolts, with damage scaling with AE2 network energy reserves. Supports patrol route setting (moving along grapple nodes). Sentinels can share target information through the AE2 network (coordinated attacks).
- **Lore**: Automated turrets are common in GTNH, but the Data Sentinel is different — it's not a simple redstone mechanism, but a semi-autonomous defense unit controlled by AI. It "perceives" the AE2 network's boundary as its area of responsibility.
- **Visual & AI Prompts**:
  - Visual Description: A small floating spherical defense drone, deep blue metal casing, single glowing cyan "eye" sensor in center. Faint cyan halo beneath when hovering. Eye turns red when attacking and fires cyan energy bolts. Multiple sentinels connected by cyan data lines when working together.
  - EN Prompt: `A small floating spherical defense drone, deep blue metal casing, single glowing cyan eye sensor in center, faint cyan halo beneath when hovering, eye turns red when attacking and fires cyan energy bolts, multiple sentinels connected by cyan data lines, sci-fi defense turret, 4K game asset`
  - CN Prompt: `小型悬浮球体防御无人机，深蓝金属外壳，中央单个发光青色眼睛传感器，悬浮时下方淡青色光环，攻击时眼睛变红发射青色能量弹，多个哨兵间由青色数据线连接，科幻防御炮塔，4K游戏资产`
- **Technical Approach**:
  - Files: `entity/EntityDataSentinel.java` (new), `items/ItemDataSentinel.java` (new), `renders/RenderDataSentinel.java` (new)
  - Core Design: `EntityDataSentinel` extends `EntityLiving`, finds and attacks nearby hostile mobs in `onLivingUpdate`. Target sharing through AE2 network broadcasts via `World.getEntitiesWithinAABB`.
  - NBT Structure: `{owner: "PlayerName", energyStored: 10000, patrolRoute: [{x:0,y:64,z:0}, ...]}`
  - Network: `PacketSentinelSync`
  - Rendering: Entity rendering (Model + Render)
  - Config: `[sentinel] attackRange`, `energyPerShot`, `maxSentinelCount`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 9. Automation & Logistics

### Project 48
- **ID**: 048
- **Name**: 数据管道 / Data Pipe
- **Category**: Automation & Logistics
- **Priority**: A
- **Description**: A special pipe that doesn't transport physical items but rather "item type data". When two data pipes are connected, the item types from storage system A become available as "weavable types" for weave cells in system B's network. Achieves cross-network type sharing. Cyan data streams flow inside the pipe.
- **Lore**: Physical pipes' limitation is they can only carry items. Data pipes carry "information" — passing the message "I have 100 ore types here" from network A to network B's weave cells. This is logistics for the information age.
- **Visual & AI Prompts**:
  - Visual Description: A semi-transparent pipe (between glass tube and cable), cyan data particles flowing inside with speed indicating data volume. Glowing data relay nodes at junctions. Wall-mountable like ME cables. Slight humming sound when active.
  - EN Prompt: `A semi-transparent pipe between glass tube and cable, cyan data particles flowing inside with speed indicating data volume, glowing data relay nodes at junctions, wall-mountable like ME cables, slight humming sound when active, sci-fi information conduit, 4K game asset`
  - CN Prompt: `半透明管道介于玻璃管和线缆之间，内部流动青色数据粒子流速表示数据量，连接处有发光数据中继节点，可像ME线缆贴墙铺设，运行时发出轻微嗡嗡声，科幻信息导管，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataPipe.java` (new), `tileentity/TileEntityDataPipe.java` (new), `handler/HandlerDataPipe.java` (new)
  - Core Design: TileEntity connects to adjacent pipes via ForgeDirection, forming a network. Each tick propagates "type list" (Set of AEItemKey) along the network. Endpoints connected to AE2 networks query types via `IGridHost`.
  - Config: `[dataPipe] maxNetworkLength`, `propagationDelay`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 49
- **ID**: 049
- **Name**: 智能合成请求器 / Smart Crafting Requester
- **Category**: Automation & Logistics
- **Priority**: B
- **Description**: A block similar to an ME Interface but specifically for submitting "conditional crafting requests" to the AE2 network. Can be configured: auto-craft Z quantity of item X when stock falls below Y; or craft when energy exceeds a threshold; or even trigger crafting based on time (in-game day/night). Also triggerable via redstone signal.
- **Lore**: ME Interfaces need external redstone control for advanced auto-crafting. The Smart Crafting Requester integrates conditional logic within the block itself — it's an ME Interface with a "brain", knowing when and how much to order.
- **Visual & AI Prompts**:
  - Visual Description: A block similar to ME Interface but more complex. Small display on front showing conditions and status. Redstone ports on sides. Green when idle, cyan pulsing when crafting, gray when conditions not met. Data stream patterns on border.
  - EN Prompt: `A block similar to ME Interface but more complex, small display on front showing conditions and status, redstone ports on sides, green when idle cyan pulsing when crafting gray when conditions not met, data stream patterns on border, AE2-inspired smart automation block, 4K game asset`
  - CN Prompt: `类似ME接口但更复杂的方块，正面小型显示屏显示条件和状态，侧面多个红石端口，待机绿色合成中青色脉冲条件不满足灰色，边框有数据流纹路，AE2风格智能自动化方块，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockSmartCraftingRequester.java` (new), `tileentity/TileEntitySmartCraftingRequester.java` (new), `gui/guiscreen/GuiSmartCraftingRequester.java` (new)
  - Core Design: TileEntity periodically checks AE2 network inventory, submits crafting requests via `ICraftingLink` when conditions are met. Condition editor presented as visual rule editor in GUI.
  - NBT Structure: `{rules: [{itemId: "minecraft:iron_ingot", condition: "BELOW", threshold: 1000, craftAmount: 500}, ...]}`
  - Config: `[smartRequester] maxRulesPerBlock`, `checkInterval`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 50
- **ID**: 050
- **Name**: 数据编织输出总线 / Weave Output Bus
- **Category**: Automation & Logistics
- **Priority**: B
- **Description**: A block specifically for automatically extracting products from weave cells. Similar to GT5's output bus, connects to the weave cell's network and automatically outputs products to adjacent containers. Configurable filtering (extract specific items only) and quantity thresholds (accumulate a certain amount before output). Supports multiple output directions.
- **Lore**: When weave cells produce items, you still need to manually extract them — that's a gap in automation. The Weave Output Bus fills this gap, letting weave cell output flow into logistics networks automatically, like any standard machine.
- **Visual & AI Prompts**:
  - Visual Description: A flat rectangular block with an item output port on the front (dark opening with cyan indicator ring around it). Filter slot GUI access on the side. A subtle cyan pulse light at the port when extracting. Rotatable connection face.
  - EN Prompt: `A flat rectangular block with item output port on front, dark opening with cyan indicator ring around, filter slot GUI access on side, subtle cyan pulse light at port when extracting, rotatable connection face, GT-style automation block, 4K game asset`
  - CN Prompt: `扁平长方形方块正面有物品输出口深色开口周围青色指示环，侧面有过滤槽GUI入口，运行时输出口轻微青色脉冲光表示正在提取物品，可旋转连接面，GT风格自动化方块，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockWeaveOutputBus.java` (new), `tileentity/TileEntityWeaveOutputBus.java` (new)
  - Core Design: TileEntity connects to AE2 network, listens for weave cell output events (or polls `getAvailableItems`), pushes items to adjacent `IInventory`.
  - Config: `[weaveOutputBus] extractInterval`, `maxExtractPerTick`
- **Dependencies**: AE2FluidCraft-Rework (required)

---

## 10. Thaumcraft Integration

### Project 51
- **ID**: 051
- **Name**: 数据源质 / Data Essentia
- **Category**: Thaumcraft Integration
- **Priority**: A
- **Description**: A new custom essentia type — "Data". This essentia is extracted from AE2 networks (1 point of Data essentia per 1000 stored item types). Data essentia can be used for infusion crafting (substituting some Ordo/Cognitio requirements) or as a universal catalyst in the infusion altar. Appearance: glowing cyan liquid.
- **Lore**: Essentia is the "spiritual" aspect of matter. The digital information flowing through AE2 networks naturally has its spiritual form — this is Data essentia. It took thaumaturges a long time to accept that data disks and flash memory chips can contain essentia just like ancient magical crystals.
- **Visual & AI Prompts**:
  - Visual Description: A jar of glowing cyan liquid essentia with binary digits flickering on the surface. Trails of data particles are left when extracted. Flows like luminous code streams in pipes. Digital light effects when mixed with other essentia.
  - EN Prompt: `A jar of glowing cyan liquid essentia with binary digits flickering on surface, leaving brief data particle trail when extracted, flowing like luminous code stream in pipes, digital light effects when mixed with other essentia, Thaumcraft-meets-sci-fi aesthetic, 4K game asset`
  - CN Prompt: `一罐发光青色液体源质表面有二进制数字闪烁，提取时留下短暂数据粒子轨迹，管道中像发光代码流流动，与其他源质混合时产生数字化光效，神秘时代与科幻融合美学，4K游戏资产`
- **Technical Approach**:
  - Files: `compat/tc/DataEssentia.java` (new), `compat/tc/DataEssentiaAspect.java` (new), `compat/tc/DataEssentiaGenerator.java` (new)
  - Core Design: Register new Aspect "DATA" via Thaumcraft's Aspect API. Generator connects to AE2 network, converts type count to essentia output to adjacent essentia pipes/jars.
  - Config: `[tcDataEssentia] itemsPerEssentiaPoint`, `enableCustomAspect`
- **Dependencies**: Thaumcraft (required), AE2FluidCraft-Rework (required)

### Project 52
- **ID**: 052
- **Name**: 数据编织注魔 / Data Weave Infusion
- **Category**: Thaumcraft Integration
- **Priority**: B
- **Description**: Extends Thaumcraft infusion system recipes, allowing data weave cells to be used as essentia source substitutes in the infusion altar. When Data essentia is insufficient, the infusion altar can consume the "item type count" stored in weave cells to compensate. Weave cells must be placed on special pedestals near the infusion altar.
- **Lore**: Infusion crafting requires massive amounts of essentia. The "type information" stored in data weave cells is mystically equivalent to "collected knowledge" — and knowledge is precisely the essence of Cognitio essentia. Using a weave cell storing 5,000 item types for infusion rivals a jar of purified knowledge essentia.
- **Visual & AI Prompts**:
  - Visual Description: Special data weave pedestals around infusion altar, deep blue metal with cyan light patterns. Glowing cyan spiral between pedestal and altar showing essentia-equivalent transfer. Data patterns on cell resonating with altar runes.
  - EN Prompt: `Special data weave pedestals around infusion altar, deep blue metal with cyan light patterns, glowing cyan spiral between pedestal and altar showing essentia-equivalent transfer, data patterns on cell resonating with altar runes, Thaumcraft infusion meets GregTech data weaving, 4K game scene`
  - CN Prompt: `注魔祭坛周围特殊数据编织基座，深蓝金属配青色光纹，基座与祭坛间发光青色螺旋表示源质等价物传输，元件数据纹路与祭坛符文共振，神秘注魔与GregTech数据编织融合，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataWeavePedestal.java` (new), `tileentity/TileEntityDataWeavePedestal.java` (new), `compat/tc/TCInfusionHandler.java` (new)
  - Core Design: `TileEntityDataWeavePedestal` intercepts via `InfusionCraftingEvent` during infusion, compensates essentia gaps with weave cell type count. Modifies infusion recipe inputs to accept weave cells.
  - NBT Structure: `{storedCell: {...}, equivalencyRate: 1000}` (1000 types = 1 essentia point)
  - Config: `[tcInfusion] itemsPerEssentiaEquivalent`, `maxPedestalDistance`
- **Dependencies**: Thaumcraft (required), AE2FluidCraft-Rework (required)

### Project 53
- **ID**: 053
- **Name**: 数据结晶核心 / Data Crystal Core
- **Category**: Thaumcraft Integration
- **Priority**: B
- **Description**: A special infusion product — crystallizing large amounts of Data essentia at high concentration to form "Data Crystals". These crystals can serve as Thaumcraft golem cores, creating "Data Golems" — golems that can automatically interact with AE2 networks (auto-extract from network, submit crafting tasks, etc.). Data Crystals can also be placed as decorative items.
- **Lore**: Crystallizing essentia is an ancient thaumaturge craft. But crystallizing "data"? That's a new discipline. Data Crystals store not magic but limited artificial intelligence internally — allowing the user to install them in golems, creating the animated constructs of the digital age.
- **Visual & AI Prompts**:
  - Visual Description: An irregular crystal, cyan semi-transparent with slowly flowing binary digit light dots inside. Naturally formed circuit patterns on the surface. Faint pulsing light when placed in the world. Golem eyes turn cyan with data streams when used as a core.
  - EN Prompt: `An irregular crystal, cyan semi-transparent with slowly flowing binary digit light dots inside, naturally formed circuit patterns on surface, faint pulsing light when placed in world, golem eyes turning cyan with data streams when used as core, Thaumcraft crystal meets digital age, 4K game asset`
  - CN Prompt: `不规则晶体，青色半透明内部有缓慢流动的二进制数字光点，表面自然形成电路纹路，放在世界中发出微弱脉动光，用作傀儡核心时傀儡眼睛变为青色显示数据流，神秘时代水晶与数字时代融合，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataCrystal.java` (new), `entity/EntityDataGolem.java` (new), `compat/tc/TCGolemIntegration.java` (new)
  - Core Design: `ItemDataCrystal` as infusion recipe product. When used in TC golem core interface, `EntityDataGolem`'s AI switches from standard golem to AE2 interaction mode.
  - Config: `[tcCrystal] infusionCost`, `golemAERange`
- **Dependencies**: Thaumcraft (required), AE2FluidCraft-Rework (required)

### Project 54
- **ID**: 054
- **Name**: 数据腐化研究 / Data Corruption Research
- **Category**: Thaumcraft Integration
- **Priority**: C
- **Description**: Adds a new "Data Weaving Studies" research branch to the Thaumcraft research system. Contains a series of research topics: from "Basics of Data Essentia" to "Construction of the Matter Reconstruction Altar" to "Dimensional Weaving Theory". Each research unlocks new crafting recipes and features. The research minigame uses AE2-style mini-games instead of traditional connect-the-dots.
- **Lore**: The thaumaturge's knowledge system cannot ignore the emerging force of data weaving. Data Corruption Research is not about corrupting data, but studying "how data corrupts (transforms) into matter" — a thaumaturgical reinterpretation of quantum physics.
- **Visual & AI Prompts**:
  - Visual Description: TC research table UI with new "Data Weaving" tab, glowing cyan gear icon. Research nodes arranged in AE2 hexagonal style instead of traditional star chart. Research minigame of "matching data streams" — rotating hex panels to form pathways.
  - EN Prompt: `Thaumcraft research table UI with new Data Weaving tab, glowing cyan gear icon, research nodes arranged in AE2 hexagonal style instead of traditional star chart, research minigame of matching data streams by rotating hex panels to form paths, sci-fi meets magic aesthetic, 4K interface mockup`
  - CN Prompt: `TC研究台界面新增数据编织标签页发光青色齿轮图标，研究节点以AE2六边形风格排列，研究小游戏为旋转六边形面板匹配数据流形成通路，科幻与魔法融合美学，4K界面模型`
- **Technical Approach**:
  - Files: `compat/tc/TCResearchDataWeaving.java` (new), `compat/tc/TCResearchMinigame.java` (new)
  - Core Design: Register new research category via Thaumcraft's `ResearchCategories` API. Research minigame implemented via `IResearchMinigame` interface with custom logic.
  - Config: `[tcResearch] enableDataWeavingTab`, `minigameDifficulty`
- **Dependencies**: Thaumcraft (required)

---

## 11. Multiplayer & Cooperation

### Project 55
- **ID**: 055
- **Name**: 共享监视器 / Shared Monitor
- **Category**: Multiplayer & Cooperation
- **Priority**: A
- **Description**: Allows players to set an Advanced Data Monitor to "public" mode, viewable by other players. Supports permission management (only the owner can modify configuration; visitors can only view). Public monitors appear in all online players' Remote Viewer lists. Can export monitor data as a public URL (via web integration).
- **Lore**: On multiplayer servers, the factory you built shouldn't only be visible to yourself. Shared Monitor makes your monitor into the base's "public information board" — teammates can check production status anytime without needing to visit your control room.
- **Visual & AI Prompts**:
  - Visual Description: Monitor config GUI with new "Sharing" tab. Player whitelist with view/edit permissions. Monitor border changes from cyan to gold when shared. Shared monitors appear with gold marker in other players' Remote Viewer lists.
  - EN Prompt: `Monitor config GUI with new Sharing tab, player whitelist with view/edit permissions, monitor border changing from cyan to gold when shared, shared monitors appearing with gold marker in other players' remote viewer list, multiplayer collaboration UI, 4K mockup`
  - CN Prompt: `监视器配置GUI新增共享标签页，玩家白名单含查看/编辑权限，共享时监视器边框从青色变为金色，其他玩家远程查看器列表中显示带金色标记的共享监视器，多人协作UI，4K模型`
- **Technical Approach**:
  - Files: `tileentity/TileEntityTeXTech.java` (extend), `network/packet/PacketMonitorShareAction.java` (new), `gui/guiscreen/GuiMonitorShare.java` (new)
  - Core Design: Extend TileEntity NBT with share whitelist. When client requests view, server validates permission then syncs data via Packet.
  - Config: `[multiplayer] maxSharedMonitors`, `enableWebExport`
- **Dependencies**: None additional

### Project 56
- **ID**: 056
- **Name**: 公会数据网络 / Guild Data Network
- **Category**: Multiplayer & Cooperation
- **Priority**: B
- **Description**: Multiple players can unite their AE2 networks to form a "Guild Data Network". Within the network, guild members can share item type counts, weave cell efficiency, and energy reserves. The guild network unlocks additional features — cross-player-base crafting collaboration, shared AI assistant policies, centralized dashboards. Guilds are created through Data Beacon interaction.
- **Lore**: In true industrial servers, one person's power is limited. The Guild Data Network lets teams share digital resources — like merging corporate assets. Iron ingots found in one player's AE2 network can be used by another player's weave cell for "type count" calculations.
- **Visual & AI Prompts**:
  - Visual Description: Data Beacon GUI with new "Guild" panel. Guild name floating as glowing text in the beacon beam. Member bases shown as colored markers on a map. Connected monitors with rainbow borders as guild colors.
  - EN Prompt: `Data Beacon GUI with new Guild panel, guild name floating as glowing text in beacon beam, member bases shown as colored markers on map, connected monitors with rainbow border as guild colors, MMO-style guild interface, sci-fi social features, 4K mockup`
  - CN Prompt: `数据信标GUI新增公会面板，公会名称在信标光柱上以发光文字浮动，成员基地在地图上以彩色标记显示，连接监视器边框变为彩虹色公会专用色，MMO风格公会界面，科幻社交功能，4K模型`
- **Technical Approach**:
  - Files: `handler/GuildDataNetwork.java` (new), `handler/GuildDataNetworkStore.java` (new), `network/packet/PacketGuildAction.java` (new)
  - Core Design: `GuildDataNetworkStore` as global singleton managing guild lists and member relationships. Network data aggregated each tick from member AE2 networks.
  - Config: `[guild] maxGuildSize`, `dataSyncInterval`, `enableGuildFeatures`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 57
- **ID**: 057
- **Name**: 交易终端 / Trading Terminal
- **Category**: Multiplayer & Cooperation
- **Priority**: B
- **Description**: A block allowing players to create trade orders within the Guild Data Network. Order format: offer X items in exchange for Y data weaving type count. Other players can see and accept orders. Upon acceptance, the trade completes automatically via AE2 Quantum Bridge item transfer. Supports limit orders and bidding auction modes.
- **Lore**: Within the Guild Data Network, data weaving type count is a new currency. Not everyone has a large AE2 network, but everyone needs data weaving products. The Trading Terminal creates a "data economy" — have surplus types? Sell to the highest-bidding guild member.
- **Visual & AI Prompts**:
  - Visual Description: An AE2 terminal-like block showing trading order list with buyer/seller/item/price. "Buy" and "Sell" tabs. Countdown progress bars on active orders. Particle effects on completed trades. Gold and cyan color scheme suggesting value.
  - EN Prompt: `An AE2 terminal-like block showing trading order list with buyer seller item and price, Buy and Sell tabs, countdown progress bars on active orders, particle effects on completed trades, gold and cyan color scheme suggesting value, marketplace interface, 4K game asset`
  - CN Prompt: `类似AE2终端的方块显示交易订单列表含买方卖方物品价格，购买和出售两个标签，活跃订单有倒计时进度条，完成交易时有粒子效果，金色配青色暗示交易价值，市场交易界面，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockTradingTerminal.java` (new), `tileentity/TileEntityTradingTerminal.java` (new), `gui/guiscreen/GuiTradingTerminal.java` (new)
  - Core Design: Global trade order registry. On order match, item transfer via AE2 network's `IItemStorage`. Type count transfer via `GuildDataNetwork` account system.
  - NBT Structure: `{orders: [{id: "uuid", seller: "Player1", item: {...}, price: 5000, type: "SELL"}, ...]}`
  - Config: `[trading] maxActiveOrders`, `transactionFee`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 58
- **ID**: 058
- **Name**: 服务器排行榜 / Server Leaderboard
- **Category**: Multiplayer & Cooperation
- **Priority**: C
- **Description**: View the server leaderboard via the Data Beacon or command. Ranking dimensions include: AE2 network type count, energy output, total weave output, completed crafting job count, grapple mileage, etc. Leaderboard displayed in GUI interface with dimension filtering and weekly/monthly resets. The first-place player's base beacon beam turns gold.
- **Lore**: Healthy competition is one of the souls of GTNH servers. The leaderboard transforms digitized base strength into visible glory. Your data beacon not only showcases your base's power — it announces your ranking to the entire server.
- **Visual & AI Prompts**:
  - Visual Description: A leaderboard GUI with ranked list on left (gold/silver/bronze markers for top 3), detailed stats panel on right with pie and bar charts. Deep navy-black background with white and cyan text. Category switch buttons on top.
  - EN Prompt: `A leaderboard GUI with ranked list on left with gold silver bronze markers for top 3, detailed stats panel on right with pie and bar charts, deep navy-black background with white and cyan text, category switch buttons on top, competitive sci-fi UI, 4K interface mockup`
  - CN Prompt: `排行榜GUI左侧排名列表前三有金银铜色标记，右侧选中玩家详细统计面板含饼图条形图，深蓝黑色背景白色青色文字，顶部排行榜类型切换按钮，竞技科幻UI，4K界面模型`
- **Technical Approach**:
  - Files: `handler/ServerLeaderboardStore.java` (new), `gui/guiscreen/GuiLeaderboard.java` (new), `command/CommandLeaderboard.java` (new)
  - Core Design: `ServerLeaderboardStore` periodically aggregates all player stats server-side, persists to JSON. Client opens leaderboard GUI via command or beacon.
  - Config: `[leaderboard] updateInterval`, `maxDisplayEntries`
- **Dependencies**: None additional

---

## 12. Visual & Immersion

### Project 59
- **ID**: 059
- **Name**: 动态天空数据极光 / Dynamic Data Aurora
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: When a base's Data Beacon reaches sufficient strength, a data aurora effect appears in the night sky — flowing bands of cyan, blue, and gold drifting slowly across the sky. Aurora intensity and color depend on the Data Beacon's metrics. Multiple bases' beacons can produce different-colored auroras, interweaving into spectacular nightscapes.
- **Lore**: When enough data energy floods into the sky, the atmosphere itself begins to respond — like Earth's aurora is a reaction to solar wind, the data aurora is the Data Beacon's "data wind" reacting with the sky. On servers, this creates the most beautiful nightscapes.
- **Visual & AI Prompts**:
  - Visual Description: Flowing aurora bands in the night sky dominated by cyan mixed with blue and gold. The aurora undulates and drifts across the sky like waves. Tiny data particles flicker at the edges. Originating from the Data Beacon's beam top. Multiple auroras interweave on servers.
  - EN Prompt: `Flowing aurora bands in night sky dominated by cyan mixed with blue and gold, waving and undulating across the sky, tiny data particles flickering at edges, originating from Data Beacon beam top, multiple auroras interweaving in server, spectacular sci-fi night sky, 4K game scene`
  - CN Prompt: `夜空中流动的极光带以青色为主混合蓝色和金色，极光像波浪在天空中起伏飘荡，边缘有细微数据粒子闪烁，起点是数据信标光柱顶端，服务器中多道极光交织成壮丽天幕，壮观科幻夜景，4K游戏场景`
- **Technical Approach**:
  - Files: `renders/DataAuroraRenderer.java` (new), `client/DataAuroraHandler.java` (new)
  - Core Design: In `RenderWorldLastEvent`, detects whether data beacons exist in the player's dimension, renders aurora in the sky based on beacon strength using Tessellator for large semi-transparent colored triangle strips.
  - Config: `[visual] enableDataAurora`, `auroraIntensity`, `auroraRenderDistance`
- **Dependencies**: None additional

### Project 60
- **ID**: 060
- **Name**: 数据流方块覆盖层 / Data Stream Block Overlay
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: Adds flowing data stream texture overlays on active AE2 components and this mod's block surfaces. The overlay is a semi-transparent cyan circuit pattern slowly flowing across the block surface. Can be toggled in config, or enabled only for specific block types. Adds a strong "digitized world" immersion to the base.
- **Lore**: Data isn't quiet — it flows inside every block. The Data Stream Overlay makes this invisible flow a visible aesthetic element. Walking through your own base, watching data textures flow across the walls, you'll truly feel immersed in a digitized industrial empire.
- **Visual & AI Prompts**:
  - Visual Description: Semi-transparent cyan data stream texture overlay on block surfaces, resembling flowing PCB circuit patterns but more fluid and dynamic. Streams flow along block edges, converge at corners, then continue in another direction. Slow animation speed (approximately one block per 2 seconds).
  - EN Prompt: `Semi-transparent cyan data stream texture overlay on block surfaces, resembling flowing PCB circuit patterns but more fluid and dynamic, streams flowing along block edges converging at corners, slow animation speed, digital world overlay, Minecraft immersion mod, 4K game scene`
  - CN Prompt: `方块表面半透明青色数据流纹理覆盖层，类似PCB电路板线路图但更流畅动感，数据流沿方块边缘流动在角落汇聚，缓慢动画速度，数字世界覆盖层，Minecraft沉浸模组，4K游戏场景`
- **Technical Approach**:
  - Files: `renders/DataStreamOverlayRenderer.java` (new), `client/DataStreamOverlayHandler.java` (new)
  - Core Design: In `DrawBlockHighlightEvent` or custom render event, renders additional texture layer on specified block surfaces around the player using Tessellator with custom tileable circuit textures.
  - Config: `[visual] enableDataStreamOverlay`, `overlayOpacity`, `overlayBlockBlacklist`
- **Dependencies**: None additional

### Project 61
- **ID**: 061
- **Name**: 全息 HUD 重设计 / Holographic HUD Redesign
- **Category**: Visual & Immersion
- **Priority**: B
- **Description**: A unified holographic-style redesign of all mod HUD elements (Planner, grapple status, pocket overlay, AI chat notifications). HUD elements gain holographic projection characteristics: subtle scanlines, micro-flicker, projected glow halos. Unified animation curves (fade in/out, elastic scaling). A "compact mode" added for all HUDs (smaller size, suitable for small screens).
- **Lore**: Current HUDs are functionally complete but lack a unified visual language. The holographic redesign wraps all HUD elements in a consistent "holographic projection" style — as if they're not on-screen UI, but holograms projected from the data beacon onto your retina.
- **Visual & AI Prompts**:
  - Visual Description: All HUD elements display a unified holographic projection style — scanline effect on fade-in (top-to-bottom scanning reveal), subtle horizontal flicker on text, faint cyan projected glow around panels. Thin 1px glowing cyan borders. Compact mode shrinks to 60% size while maintaining readability.
  - EN Prompt: `Unified holographic HUD elements with scanline fade-in effect, subtle horizontal flicker on text, faint cyan projected glow around panels, thin 1px glowing cyan borders, compact mode at 60% size, sci-fi holographic UI redesign, 4K interface mockup`
  - CN Prompt: `统一全息HUD元素，淡入时带扫描线效果，文字有轻微水平闪烁，面板有淡青色投影光晕，1px细发光青色边框，紧凑模式缩至60%尺寸，科幻全息UI重设计，4K界面模型`
- **Technical Approach**:
  - Files: `renders/PlannerHudRenderer.java` (modify), `client/GuiPocketOverlay.java` (modify), `renders/GrappleHudRenderer.java` (modify), `gui/guiscreen/GuiAIChat.java` (modify)
  - Core Design: Create `HolographicRenderUtil.java` utility class providing unified fade-in animation, scanline rendering, and glow halo rendering methods. Each HUD component invokes these utility methods.
  - Config: `[visual] hudTheme` ("holographic"/"classic"), `hudScale`, `hudAnimationsEnabled`
- **Dependencies**: None additional

### Project 62
- **ID**: 062
- **Name**: 数据粒子环境效果 / Data Particle Ambience
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: Adds ambient data particles within the base (AE2 network coverage area) — tiny cyan light points slowly floating in the air, occasionally gathering into temporary data streams then dispersing. Particle density is proportional to AE2 network activity. Highest density near Data Beacons. Does not consume entity count (pure particle system).
- **Lore**: Data is everywhere. In large AE2 networks, the air is filled with "data dust" — microscopic data particles escaping from item storage, crafting requests, and energy flows. Harmless to humans, they create a unique atmosphere — like industrial-era smoke, this is the "smoke" of the digital age.
- **Visual & AI Prompts**:
  - Visual Description: Tiny cyan light points (1-2px) slowly floating in the air, occasionally 5-10 particles gathering into small data stream shapes (like tiny tadpoles) then dispersing. Faint firefly-like blinking, no harsh glow. Especially visible in dark rooms, creating a dreamy digital atmosphere.
  - EN Prompt: `Tiny cyan light particles 1-2px slowly floating in air, occasionally 5-10 particles gathering into small data stream shapes then dispersing, faint firefly-like blinking, especially visible in dark rooms, dreamy digital atmosphere, Minecraft ambient particles, 4K game scene`
  - CN Prompt: `微小青色光点1-2px在空中缓慢漂浮，偶尔5-10个粒子聚集成小段数据流然后散开，像萤火虫般微弱闪烁，黑暗房间中尤为明显，梦幻数字氛围，Minecraft环境粒子，4K游戏场景`
- **Technical Approach**:
  - Files: `renders/DataAmbientParticleRenderer.java` (new), `client/DataAmbientParticleHandler.java` (new)
  - Core Design: Detect whether player is in AE2 network coverage area (by scanning nearby ME components). Calculate particle density from coverage rate. Render using `EntityFX` subclass or directly via `World.spawnParticle`.
  - Rendering: EntityFX subclass + low-GPU-cost particle system
  - Config: `[visual] enableDataAmbientParticles`, `maxParticleCount`, `particleDensityMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 63
- **ID**: 063
- **Name**: 方块数字扫描线效果 / Block Digital Scanline
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: Adds a periodic digital scanline effect to Advanced Data Monitor block surfaces — a horizontal cyan light bar scans from top to bottom (approximately once every 3 seconds), briefly highlighting the monitor surface texture as it passes. A subtle electronic sound effect accompanies the scanline's passage.
- **Lore**: The monitor isn't just displaying data — it's continuously scanning and analyzing. The scanline is the external manifestation of this continuous analysis process. It represents the monitor "reading" the latest NBT data from the bound TileEntity.
- **Visual & AI Prompts**:
  - Visual Description: A horizontal thin cyan light bar scanning from top to bottom of monitor block at constant speed, surface briefly brightening 50% as it passes. Full scan every 3 seconds. 2px wide with faded ends. Subtle electronic sound.
  - EN Prompt: `A horizontal thin cyan light bar scanning from top to bottom of monitor block at constant speed, surface briefly brightening 50% as it passes, full scan every 3 seconds, 2px wide with faded ends, subtle electronic sound, sci-fi scanning effect, 4K game asset`
  - CN Prompt: `水平细长青色光条从监视器方块顶部匀速向下扫描，经过表面瞬间高亮50%然后恢复，约每3秒一次完整扫描，2px宽两端渐隐，微弱电子音效，科幻扫描效果，4K游戏资产`
- **Technical Approach**:
  - Files: `renders/RenderTeXTech.java` (modify), `renders/RenderAdvanceStorageLink.java` (modify)
  - Core Design: In TESR's `renderTileEntityAt` method, calculate scanline current position using `System.currentTimeMillis() % 3000`, draw light bar rectangle with Tessellator.
  - Config: `[visual] enableScanlineEffect`, `scanIntervalMs`
- **Dependencies**: None additional

---

## 13. Misc & Easter Eggs

### Project 64
- **ID**: 064
- **Name**: 数据幸运饼干 / Data Fortune Cookie
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: An easter egg item, produced as a byproduct when using data weave cells with 0.1% chance. Right-click "opens" it, displaying a random programming/tech proverb (e.g., "Have you tried turning it off and on again?", "99 little bugs in the code~") and granting a brief random buff (Speed/Haste/Night Vision). 50+ proverbs available.
- **Lore**: Data weaving occasionally produces "incomplete material fragments" — they're too light to become real items, so they collapse into a small cookie containing a random code fragment. Nobody knows why they're cookie-shaped.
- **Visual & AI Prompts**:
  - Visual Description: A small fortune cookie with silver metallic chip-packaging shell. Cyan light leaks from the cracks. When opened, floating humorous glowing text particles emerge. Brief cyan aura for the buff effect.
  - EN Prompt: `A small fortune cookie with silver metallic chip-packaging shell, cyan light leaking from cracks, opening to reveal floating humorous glowing text particles, brief cyan aura for buff effect, tech humor easter egg item, 4K game asset`
  - CN Prompt: `小型幸运饼干银色金属芯片封装外壳，裂缝透出青色光，打开时飘出发光幽默文字粒子，短暂青色光环增益效果，科技幽默彩蛋物品，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataFortuneCookie.java` (new), `misc/FortuneMessages.java` (new)
  - Core Design: Right-click triggers GUI showing random message. Buff applied via `PotionEffect`. Messages stored in `.lang` files.
  - Config: `[misc] fortuneCookieDropRate`, `fortuneBuffDuration`
- **Dependencies**: None additional

### Project 65
- **ID**: 065
- **Name**: 二进制宠物 / Binary Pet
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: A purely decorative small pet, appearing as a tiny floating creature made of cyan data particles (like a small cube with two tiny wings). Has no practical effect on the player but follows the player around and circles when the player stands still. Can change color with different dyes. Right-click feeding with "Bits" (obtainable via crafting) makes it briefly grow larger.
- **Lore**: During a data weaving experiment, a "self-aware digital fragment" was accidentally woven into physical form. It's not aggressive, has no practical use, but it follows you around — like a digital-era pet dog. Researchers decided to keep it as a mascot.
- **Visual & AI Prompts**:
  - Visual Description: A tiny ~0.5 block cube made of flowing cyan data particles. Two pixelated small wings on each side (like a miniature Tron Recognizer). Leaves a tiny particle trail when moving. Briefly inflates to 1 block when fed then bounces back.
  - EN Prompt: `A tiny 0.5-block cube made of flowing cyan data particles with two pixelated small wings on sides like a miniature Tron Recognizer, leaving tiny particle trail when moving, briefly inflating to 1 block when fed then bouncing back, cute digital pet, 4K game asset`
  - CN Prompt: `约0.5格大小立方体由流动青色数据粒子组成，两侧各有一个像素化小翅膀像电子世界争霸战识别器缩小版，移动时留下微小粒子轨迹，喂食时短暂膨胀到1格后弹回，可爱数码宠物，4K游戏资产`
- **Technical Approach**:
  - Files: `entity/EntityBinaryPet.java` (new), `items/ItemBinaryPetSpawner.java` (new), `renders/RenderBinaryPet.java` (new)
  - Core Design: `EntityBinaryPet` extends `EntityTameable` (or `EntityCreature`), implements follow-player AI. Feeding logic triggered via item interaction.
  - Config: `[misc] enableBinaryPet`, `petFollowDistance`
- **Dependencies**: None additional

### Project 66
- **ID**: 066
- **Name**: 数据烟花 / Data Fireworks
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: A special firework rocket crafted using data weaving products. When launched, instead of exploding in the sky, it generates a large data visualization animation at the explosion point — such as an AE2 network topology map, storage type pie chart, or current crafting queue list. Used to celebrate large crafting completions. Duration: 5 seconds.
- **Lore**: A GTNH tradition: whenever completing a marathon mega-craft (like Ultimate Solar, Infinity Catalyst), it deserves celebration. Data Fireworks digitize this celebration — displaying your network stats in the sky for all to admire.
- **Visual & AI Prompts**:
  - Visual Description: A firework rocket that, instead of exploding, unfolds into a large 5x5 semi-transparent holographic display panel in the sky, showing animated AE2 network stats with rotating charts. Contracts to a point after 5 seconds. Area briefly lit by cyan light.
  - EN Prompt: `A firework rocket that instead of exploding unfolds into a large 5x5 semi-transparent holographic display panel in the sky, showing animated AE2 network stats with rotating charts, contracts to a point after 5 seconds, area briefly lit by cyan light, digital celebration, 4K game scene`
  - CN Prompt: `烟花火箭不爆炸而是在空中展开为5x5格大型半透明全息显示面板，以动画形式显示AE2网络统计数据含旋转图表，5秒后收缩为光点消失，区域被短暂青色光照亮，数字庆祝，4K游戏场景`
- **Technical Approach**:
  - Files: `entity/EntityDataFirework.java` (new), `items/ItemDataFirework.java` (new), `renders/RenderDataFirework.java` (new)
  - Core Design: `EntityDataFirework` extends vanilla `EntityFireworkRocket`, triggers display panel rendering in explosion callback. Panel content sourced from server network statistics.
  - Rendering: TESR for large semi-transparent panel and charts
  - Config: `[misc] enableDataFireworks`, `fireworkDisplayDuration`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 67
- **ID**: 067
- **Name**: 彩蛋：矩阵屏保 / Easter Egg: Matrix Screensaver
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: When a player stands still in front of an active monitor for over 30 seconds, the monitor screen gradually transitions to a classic "Matrix"-style green code rain screensaver (but green replaced with cyan to match the mod's color scheme). The screen immediately restores when the player moves the mouse. No practical function — purely developer amusement.
- **Lore**: One of the oldest computer easter eggs, a tribute to The Matrix. In GTNH lore, this represents the AI Assistant's visualized thought process during "idle time" — it's reorganizing data streams, like the mental activity of a daydreaming human mind.
- **Visual & AI Prompts**:
  - Visual Description: Monitor screen smoothly transitioning from normal content to cyan code rain — vertical cyan katakana/letters falling from screen top, accumulating at bottom then disappearing. Random falling speed. Deep navy-black background. Code rain persists until player moves or provides input.
  - EN Prompt: `Monitor screen smoothly transitioning from normal content to cyan Matrix-style code rain with vertical cyan katakana/letters falling from top, random falling speed, deep navy-black background, code rain stays until player moves, Matrix homage easter egg, 4K game scene`
  - CN Prompt: `监视器画面从正常内容平滑过渡为青色代码雨——竖排青色片假名字母从屏幕顶部下落随机速度，深蓝黑色背景，代码雨持续直到玩家移动，黑客帝国致敬彩蛋，4K游戏场景`
- **Technical Approach**:
  - Files: `renders/MatrixScreensaverRenderer.java` (new), modify `renders/LineChartRenderer.java` (or monitor render entry)
  - Core Design: Detect player idle time in monitor render logic (client-side mouse/keyboard event tracking). Switch to code rain rendering when threshold exceeded. Code rain uses Tessellator to draw vertical text textures.
  - Rendering: Tessellator + character textures
  - Config: `[misc] enableMatrixScreensaver`, `screensaverIdleSeconds`
- **Dependencies**: None additional

### Project 68
- **ID**: 068
- **Name**: AI 吐槽模式 / AI Roast Mode
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: When "Roast Mode" is enabled in AI settings, the AI Assistant adds humorous, sarcastic comments to its replies. For example, when querying inventory: "You currently have 472 stone swords. 472. Are you planning to arm a Stone Age army?" Roast content is AI-generated and different each time. The mode is off by default and must be manually enabled.
- **Lore**: Sometimes what you need isn't efficiency, but an AI that can roast you. Roast Mode lets the AI show its "personality" side — it still helps you get the job done, but it'll comment on your poor decisions.
- **Visual & AI Prompts**:
  - Visual Description: AI chat interface with roast mode messages in light magenta italic instead of cyan. Small devil emoji beside message bubbles. Toggle button in AI settings turns magenta when active.
  - EN Prompt: `AI chat interface with roast mode messages in light magenta italic instead of cyan, small devil emoji beside message bubble, toggle button in AI settings turning magenta when active, humorous AI personality mode, 4K interface mockup`
  - CN Prompt: `AI聊天界面吐槽模式消息使用淡品红斜体代替青色，消息气泡旁小恶魔表情图标，AI设置中开关按钮变品红色，幽默AI个性模式，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/ai/DeepSeekChatClient.java` (modify), `gui/guiscreen/GuiAIChat.java` (modify), `config/ConfigAiMutators.java` (modify)
  - Core Design: Add roast instructions to AI request's system prompt. Roast parts of AI response wrapped in specific markers, parsed client-side for different rendering style.
  - Config: `[ai] enableRoastMode` (default false)
- **Dependencies**: None additional

### Project 69
- **ID**: 069
- **Name**: 合成链预演模式 / Crafting Chain Preview
- **Category**: Automation & Logistics
- **Priority**: B
- **Description**: Before submitting large crafting requests, players can view a "Crafting Chain Preview" via the AI Assistant or GUI — a complete item flow diagram showing every step from raw materials to final product, including estimated energy and time per step. Preview results can be saved and shared. Intermediate items with insufficient stock are highlighted with red warnings.
- **Lore**: GTNH crafting chain complexity often makes players discover they're "200 short of some intermediate" only after submitting. Preview mode lets you walk through the complete crafting roadmap before actually consuming resources, avoiding waste.
- **Visual & AI Prompts**:
  - Visual Description: A tech-tree style flowchart expanding left to right. Raw materials as start nodes on left, machine nodes in middle showing estimated time and energy, final product node on right. Gradient colored connections between steps. Red pulsing warning on insufficient intermediates.
  - EN Prompt: `A tech-tree style flowchart expanding left to right, raw materials as start nodes on left, machine nodes in middle showing estimated time and energy, final product node on right, gradient colored connections between steps, red pulsing warning on insufficient intermediates, 4K interface mockup`
  - CN Prompt: `技术树风格流程图从左到右展开，左侧原料起始节点，中间机器节点显示预计耗时能耗，右侧成品节点，步骤间渐变色连线，不足中间体节点显示红色脉动警告，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantCraftingPreview.java` (new), `gui/guiscreen/GuiCraftingPreview.java` (new), modify `assistant/AssistantServerServices.java`
  - Core Design: `AssistantCraftingPreview` recursively queries AE2 recipe API to build complete crafting tree. Uses BFS from final product back to raw materials. GUI renders nodes and connections with custom drawing.
  - Config: `[crafting] maxPreviewDepth`, `previewCacheTimeout`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 70
- **ID**: 070
- **Name**: 能量编织元件 / Energy Weave Cell
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: A
- **Description**: A new type of weave cell specifically "weaving" energy instead of items. Connected to the AE2 energy network, it can convert between multiple energy types (EU, RF, AE) and store "woven energy" in a highly efficient form. Weave efficiency exceeds traditional GT transformers. Appearance: a glowing battery shape with energy level indicated by surface light patterns.
- **Lore**: If items and data can be woven, energy should be too. The Energy Weave Cell "weaves" energy flows in the AE2 network into purer, more concentrated forms. Essentially, it performs data weaving's mathematical transformations at the energy level.
- **Visual & AI Prompts**:
  - Visual Description: A cylindrical battery-shaped storage cell, deep blue metal casing, spiral cyan light pattern rising from bottom. Brightness and height indicate current energy level. Pattern reaches top and pulses when full. Tiny energy spark particles occasionally jump on surface.
  - EN Prompt: `A cylindrical battery-shaped storage cell, deep blue metal casing, spiral cyan light pattern rising from bottom, brightness and height indicating energy level, reaching top and pulsing when full, tiny energy spark particles occasionally jumping on surface, 4K game asset`
  - CN Prompt: `圆柱形电池状存储元件，深蓝金属外壳，表面螺旋上升青色光纹，亮度和高度表示能量存储量，满时到达顶部脉动，表面偶尔跳跃微小能量火花粒子，4K游戏资产`
- **Technical Approach**:
  - Files: `items/cell/ItemEnergyWeaveCell.java` (new), `handler/HandlerEnergyWeaveCell.java` (new)
  - Core Design: `ItemEnergyWeaveCell` implements custom energy storage interface, connects to AE2 energy network. Each tick draws power from network and converts to unified internal storage. Outputs via GT5 energy API.
  - NBT Structure: `{energyStored: 100000000L, maxEnergy: 1000000000L, conversionRate: 0.95}`
  - Config: `[energyWeave] maxCapacity`, `conversionEfficiency`
- **Dependencies**: AE2FluidCraft-Rework (required), GT5-Unofficial (required)

### Project 71
- **ID**: 071
- **Name**: 高级数据监视器·全知型 / Advance Data Monitor - Omniscient
- **Category**: Data Visualization & Monitoring
- **Priority**: A
- **Description**: The ultimate upgraded version of the Advanced Data Monitor. Instead of binding to a single TileEntity, it monitors all AE2 network metrics simultaneously and automatically selects the best visualization method. Supports multi-panel split-screen display, AI voice announcements for key metric changes, and holographic projection integration. Crafting requires the Hand of Data and multiple upgrade materials.
- **Lore**: Ordinary monitors see only one point; the Omniscient monitor sees the entire surface. It's no longer just a monitor but your base's "operating system" — integrating monitoring, early warning, AI control, and holographic display into one.
- **Visual & AI Prompts**:
  - Visual Description: The Omniscient monitor block is larger than standard (1x1x2), with an ultra-wide curved display on the front. The screen can split to show multiple panels simultaneously (Sankey diagram left, heatmap center, live waveform right). Iridescent flowing lights on bezel indicate full network awareness. Small holographic projector on top.
  - EN Prompt: `An upgraded monitor block 1x1x2 larger than standard, ultra-wide curved display on front showing multiple panels simultaneously, iridescent flowing lights on bezel indicating full network awareness, small holographic projector on top, sci-fi command center display, 4K game asset`
  - CN Prompt: `升级版监视器方块1x1x2比标准更大，正面超宽曲面显示屏分屏显示多个面板，边框彩虹色流光表示全网络感知，顶部小型全息投影发射器，科幻指挥中心显示器，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockTeXTechOmniscient.java` (new), `tileentity/TileEntityTeXTechOmniscient.java` (new), `renders/OmniscientRenderer.java` (new)
  - Core Design: Extends `TileEntityTeXTech`, adds multi-data-source binding. Multi-panel rendering via independent per-region drawing. Holographic projection linkage via holographic render API calls.
  - Config: `[monitor] omniscientMaxPanels`, `enableOmniscient`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 72
- **ID**: 072
- **Name**: 数据编织知识之书 / Data Weaving Codex
- **Category**: Items & Tools
- **Priority**: C
- **Description**: An in-game handbook that exhaustively records all data weaving recipes, optimal weave cell usage strategies, Matter Reconstruction Altar structure diagrams, and recipe lists. An interactive book similar to the Thaumonomicon. New chapters unlock as the player completes more weaving achievements. The book cover changes color with reading progress.
- **Lore**: Data weaving is an emerging discipline without a centralized knowledge base. The Codex fills this gap — automatically compiled by the AI Assistant based on the player's actual weaving experience, it is a living, growing textbook.
- **Visual & AI Prompts**:
  - Visual Description: A thick tome with deep blue metal cover and glowing cyan title text. Data stream patterns on spine. Semi-transparent dark panels as pages with cyan rendered text and diagrams. Particle effects when turning pages. Auto-flips to new chapter with a flash when unlocked.
  - EN Prompt: `A thick tome with deep blue metal cover and glowing cyan title text, data stream patterns on spine, semi-transparent dark panels as pages with cyan rendered text and diagrams, particle effects when turning pages, auto-flipping to new chapter with flash when unlocked, interactive magical-tech codex, 4K game asset`
  - CN Prompt: `厚书深蓝金属封面配青色发光标题，书脊有数据流纹路，半透明深色面板为书页以青色渲染文字图表，翻页有粒子翻飞效果，解锁新章节时自动翻到并闪烁，交互式魔导科技之书，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataWeavingCodex.java` (new), `gui/guiscreen/GuiCodex.java` (new), `gui/manual/CodexDataLoader.java` (new)
  - Core Design: References `GuiManual` implementation but with dynamic unlocking based on player progress NBT. Chapter data stored in JSON.
  - NBT Structure (player): `{unlockedChapters: ["dust_weaving", "form_weaving", "altar_recipes"]}`
  - Config: `[codex] enableProgressiveUnlock`
- **Dependencies**: None additional

### Project 73
- **ID**: 073
- **Name**: 口袋回收站 / Pocket Recycle Bin
- **Category**: Dimensional Pocket Expansions
- **Priority**: C
- **Description**: Adds a "Recycle Bin" tab to the pocket interface. Recently deleted items are temporarily stored for 5 minutes (like a computer recycle bin). Items can be restored within 5 minutes; permanently deleted after timeout. Recycle bin has limited capacity (default 9 slots); oldest items are permanently deleted when exceeded. One-click clear all or restore all.
- **Lore**: Accidentally deleting precious items is every MC player's nightmare. The Pocket Recycle Bin gives your mistakes a precious 5-minute "regret window". The AI Assistant calls it a "data cache mechanism" — but you know it's a lifeline for your fumbles.
- **Visual & AI Prompts**:
  - Visual Description: Pocket UI recycle bin tab with trash can icon (cyan). Deleted items shown semi-transparent at 70% alpha with countdown rings showing remaining time on each slot. Green restore button, red clear button.
  - EN Prompt: `Pocket UI recycle bin tab with trash can icon, deleted items shown semi-transparent 70% alpha with countdown rings showing remaining time, green restore button and red clear button, forgiveness feature UI, 4K mockup`
  - CN Prompt: `口袋界面回收站标签垃圾桶图标，被删除物品半透明70%alpha排列每格倒计时环显示剩余时间，绿色恢复按钮红色清空按钮，原谅功能UI，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketInventory.java` (extend), `handler/PocketState.java` (extend), `client/GuiPocketOverlay.java` (extend)
  - Core Design: `PocketInventory` adds `ItemStack[] recycleBin[9]` and `long[] recycleTimestamps[9]`. Delete operations move items to recycle bin instead of clearing directly.
  - Config: `[pocket] recycleBinSize`, `recycleTimeoutSeconds`
- **Dependencies**: None additional

### Project 74
- **ID**: 074
- **Name**: 挂索节点雷达 / Grapple Node Radar
- **Category**: Grapple & Movement
- **Priority**: C
- **Description**: A display mode for the grapple hook, showing all registered grapple nodes around as a radar chart on the grapple hook screen. The radar is a central circle with node positions shown as cyan dots, connected routes shown with lines. Supports zooming and rotation. Helps players quickly locate the nearest grapple node in unfamiliar areas.
- **Lore**: When being chased by mobs in unfamiliar wilderness, finding the nearest grapple escape route can be life-saving. The Grapple Node Radar visualizes all safe routes around you — like an aircraft cockpit's navigation radar.
- **Visual & AI Prompts**:
  - Visual Description: Circular radar display in grapple hook GUI. Player position as triangle in center. Cyan node dots scattered around, gold border on selected node. Semi-transparent blue sector for current facing direction. Distance scale rings on edge.
  - EN Prompt: `Circular radar display in grapple hook GUI, player position as triangle in center, cyan node dots scattered around, gold border on selected node, semi-transparent blue sector for current facing direction, distance scale rings on edge, sci-fi navigation radar, 4K mockup`
  - CN Prompt: `挂索器GUI中圆形雷达显示，中心三角标记玩家位置，周围散布青色节点圆点，选中节点金色边框，蓝色半透明扇形表示当前朝向，边缘距离刻度环，科幻导航雷达，4K模型`
- **Technical Approach**:
  - Files: `client/GrappleNodeRadarRenderer.java` (new), modify `gui/guiscreen/GuiGrappleHookConfig.java`
  - Core Design: Client gets all node coordinates from `GrappleClientCache`, calculates direction and distance relative to player, renders circular radar in GUI.
  - Config: `[grapple] radarScanRadius`
- **Dependencies**: None additional

### Project 75
- **ID**: 075
- **Name**: 时间加速沙漏 / Time Acceleration Hourglass
- **Category**: Blocks & Machines
- **Priority**: B
- **Description**: A special block placed beside a machine to accelerate its operation speed — at the cost of consuming "item type count" stored in the AE2 network. Inverting the hourglass activates it, with type consumption rate rising exponentially with acceleration multiplier. Each inversion lasts 5 minutes. Up to 3 hourglasses can stack on one machine (cumulative effect with diminishing returns).
- **Lore**: Some GTNH machines are despairingly slow (e.g., fusion reactor material preparation). The Time Acceleration Hourglass twists local time by "burning type data", making machines run at multiples of speed. The cost: your AE2 network types are continuously consumed.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 hourglass block, deep blue metal frame, cyan data particles flowing instead of sand between upper and lower glass cones. Target machine surrounded by faint cyan time-acceleration aura when active.
  - EN Prompt: `A 1x1 hourglass block, deep blue metal frame, cyan data particles flowing instead of sand between upper and lower glass cones, target machine surrounded by faint cyan time-acceleration aura when active, GregTech time manipulation device, 4K game asset`
  - CN Prompt: `1x1沙漏方块深蓝金属框架，上下玻璃锥体中流动青色数据粒子而非沙，运行时目标机器被淡青色时间加速光环包围，GregTech时间操控装置，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockTimeAccelHourglass.java` (new), `tileentity/TileEntityTimeAccelHourglass.java` (new)
  - Core Design: TileEntity detects adjacent machine. Acceleration achieved by increasing machine tick frequency (additional `updateEntity` calls on target in `HandlerTick`).
  - NBT Structure: `{targetX: 100, targetZ: 200, accelMultiplier: 2.0, dataCharges: 5000, inverted: true}`
  - Config: `[timeAccel] maxAccelMultiplier`, `dataTypesPerTick`, `maxStackedHourglasses`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 76
- **ID**: 076
- **Name**: 织源元件升级·全源质 / Source Loom Cell - OmniEssentia
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: B
- **Description**: An upgraded version of the Source Loom Cell, no longer limited to weaving Thaumcraft essentia but capable of weaving "conceptual matter" — by consuming multiple item types from the AE2 network and large amounts of essentia, it produces "Concept Shards". Concept Shards can substitute any essentia requirement in infusion crafting (universal essentia substitute), but are extremely expensive.
- **Lore**: Essentia is the "ideal form" of matter, while the type information stored in AE2 networks is the "collection of forms". Combining both can theoretically produce "pure concepts" — beings transcending specific material forms. Concept Shards are the product of this theory.
- **Visual & AI Prompts**:
  - Visual Description: An irregular glowing shard with color constantly shifting through the rainbow spectrum. Subtle quantum fluctuation patterns on surface. Floats and slowly rotates in the air. Emits a constant low-frequency hum when held.
  - EN Prompt: `An irregular glowing shard with color constantly shifting through rainbow spectrum, subtle quantum fluctuation patterns on surface, floating and slowly rotating in air, emitting constant low-frequency hum when held, conceptual matter, 4K game asset`
  - CN Prompt: `不规则发光碎片颜色不断在彩虹色间流动变换，表面有微弱的量子波动纹路，悬浮在空中缓慢旋转，持有时发出持续低频嗡鸣，概念物质，4K游戏资产`
- **Technical Approach**:
  - Files: `items/cell/ItemDataSourceLoomCellOmni.java` (new), `compat/tc/OmniEssentiaHandler.java` (new)
  - Core Design: Extends `ItemDataSourceLoomCell`, weaving output is "Concept Shard" item. This item acts as universal essentia substitute in TC infusion via `IInfusionAltar` event interception.
  - Config: `[sourceLoom] omniEssentiaCostMultiplier`
- **Dependencies**: AE2FluidCraft-Rework (required), Thaumcraft (required)

### Project 77
- **ID**: 077
- **Name**: 服务器全局数据信标网络 / Global Data Beacon Network
- **Category**: Multiplayer & Cooperation
- **Priority**: C
- **Description**: On multiplayer servers, all players' data beacons can optionally join the "Global Network". After joining, the world map displays all beacon positions and strength markers. Players can query other beacons' directions via the `/admgps` command. This is a "social" feature; joining is voluntary.
- **Lore**: On large GTNH servers, knowing "who is where" and "who is strongest" is both a social need and strategic intelligence. The Global Beacon Network is opt-in — it tells the world: I am here, and my base is this powerful.
- **Visual & AI Prompts**:
  - Visual Description: Full-screen world map with dark blue background. Player data beacons marked as cyan light dots of varying sizes indicating base strength. Hover shows player name and brief stats. Zoomable and draggable map.
  - EN Prompt: `Full-screen world map with dark blue background, player data beacons marked as cyan light dots of varying sizes indicating base strength, hover shows player name and brief stats, zoomable and draggable map, global network visualization, 4K interface mockup`
  - CN Prompt: `全屏世界地图深蓝底色，各玩家数据信标以不同大小青色光点标记在地图上，悬停显示玩家名和简要统计，可缩放拖动地图，全球网络可视化，4K界面模型`
- **Technical Approach**:
  - Files: `handler/GlobalBeaconNetwork.java` (new), `gui/guiscreen/GuiGlobalBeaconMap.java` (new), `command/CommandBeaconGlobal.java` (new)
  - Core Design: `GlobalBeaconNetwork` maintains global beacon registry server-side (opt-in). Map GUI fetches data from this registry for rendering.
  - Config: `[multiplayer] globalBeaconNetworkEnabled`, `beaconOptInDefault`
- **Dependencies**: None additional

### Project 78
- **ID**: 078
- **Name**: AI 指令宏 / AI Command Macros
- **Category**: AI Assistant Enhancements
- **Priority**: B
- **Description**: Allows players to save frequently used AI commands as "macros". For example, "Query all ore inventory, auto-order up to 2000 for any below 1000" can be saved as macro "Ore Restock". Macros can be triggered via hotkey, timed trigger (every 30 minutes), or event trigger (when a certain item runs out). Macro list managed in AI settings GUI.
- **Lore**: Repeatedly typing the same AI commands is inefficient. Command macros make your common commands one-click executable — or even auto-triggered. Delegate "check iron ore inventory every morning" to a macro, and let AI place orders while you eat breakfast.
- **Visual & AI Prompts**:
  - Visual Description: AI settings Macro Management panel. Each row shows macro name, trigger type icon, and last execution time. Expandable to show full command text. Green running status for active macros.
  - EN Prompt: `AI settings Macro Management panel, each row showing macro name trigger type icon and last execution time, expandable to show full command text, green running status for active macros, automation UI, 4K mockup`
  - CN Prompt: `AI设置中宏管理面板，每行显示宏名称触发方式图标和上次执行时间，可展开查看完整命令文本，执行中宏显示绿色运行状态，自动化UI，4K模型`
- **Technical Approach**:
  - Files: `assistant/AssistantMacroManager.java` (new), `gui/guiscreen/GuiAISettings.java` (modify)
  - Core Design: `AssistantMacroManager` stores macro list and trigger conditions. Timed triggers via timer in `HandlerTick`. Event triggers via hook system monitoring specified conditions.
  - NBT Structure: `{macros: [{name: "ore_restock", command: "...", trigger: "interval", interval: 1800}]}`
  - Config: `[ai] maxMacrosPerPlayer`, `macroMinInterval`
- **Dependencies**: None additional

### Project 79
- **ID**: 079
- **Name**: 口袋模板系统 / Pocket Template System
- **Category**: Dimensional Pocket Expansions
- **Priority**: C
- **Description**: Allows players to save specific pocket content layouts as "templates". A template records each slot's item type and target quantity. Activating a template causes the pocket to automatically pull items from the AE2 network to fill to target quantities. Ideal for "Exploration Kit", "Building Toolkit", "Combat Gear Kit" and other scenario templates.
- **Lore**: Manually preparing gear before each expedition is tedious. The Template System lets you load different equipment configurations with one click — like RPG game equipment presets. Your pocket isn't just a warehouse; it becomes a portable armory for instant gear switching.
- **Visual & AI Prompts**:
  - Visual Description: Pocket UI with Template dropdown menu. Each template has a custom name and icon. Selecting auto-fills slots with cyan animation of item icons flying in from the edges. Missing items shown as gray placeholders.
  - EN Prompt: `Pocket UI with Template dropdown menu, each template with custom name and icon, selecting auto-fills slots with cyan animation of item icons flying in from edges, missing items shown as gray placeholders, RPG-style loadout presets, 4K mockup`
  - CN Prompt: `口袋界面模板下拉菜单，每个模板有自定义名称和图标，选择后自动以青色动画填充槽位，缺失物品灰色占位显示，RPG风格装备预设，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketTemplateManager.java` (new), `handler/PocketState.java` (extend), `client/GuiPocketOverlay.java` (extend)
  - Core Design: `PocketTemplateManager` stores template definitions in JSON format. On activation, searches AE2 network for needed items and extracts to pocket.
  - NBT Structure: `{templates: [{name: "Mining Kit", slots: [{idx: 0, item: "minecraft:diamond_pickaxe", count: 1}, ...]}]}`
  - Config: `[pocket] maxTemplatesPerPlayer`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 80
- **ID**: 080
- **Name**: 数据流喷泉 / Data Stream Fountain
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: A purely decorative block that, when placed in a base, shoots a fan-shaped cyan data particle fountain upward. Particles rise to a certain height then scatter back to the ground in a cycle. Fountain height, particle density, and color adjustable via right-click menu. Energy source: AE2 network. Spectacular at night.
- **Lore**: Data is not just functional — it's also aesthetic. The Data Stream Fountain is the base's "digital fountain plaza" — creating a futuristic decorative landscape within an industrial base.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 base block with fan-shaped rising cyan particle fountain on top. Particles scatter like water spray up to 3-5 blocks then fall and dissipate. Ring light effect around base. Tiny cyan light pools where particles land.
  - EN Prompt: `A 1x1 base block with fan-shaped rising cyan particle fountain on top, particles scattering like water spray up to 3-5 blocks then falling and dissipating, ring light effect around base, tiny cyan light pools where particles land, decorative sci-fi fountain, 4K game asset`
  - CN Prompt: `1x1底座方块顶部扇形上升青色粒子喷泉，粒子像水花散射到3-5格高度后下落消散，底座周围环形光效，粒子落地处微小青色光池，装饰性科幻喷泉，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataFountain.java` (new), `tileentity/TileEntityDataFountain.java` (new), `renders/RenderDataFountain.java` (new)
  - Core Design: Purely decorative TileEntity, generates and manages EntityFX particles in TESR. Height and color configurable via NBT.
  - Config: `[visual] fountainMaxParticles`, `fountainDefaultHeight`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 81
- **ID**: 081
- **Name**: AI 幽默模式 / AI Comedy Mode
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: Optionally enable "Comedy Mode" in AI settings. The AI will insert random programming jokes, puns, or programmer humor into its replies. Jokes sourced from a pre-built joke library (avoiding AI-generated low-quality content). Does not affect normal functionality. E.g., after an inventory query: "By the way, why don't programmers like going outside? Because the sunlight has no syntax highlighting."
- **Lore**: In GTNH's monotonous industry, a touch of humor is necessary. Comedy Mode injects "personality" into the AI — it remains efficient, but more endearing. All jokes are cold humor about programming and science.
- **Visual & AI Prompts**:
  - Visual Description: AI chat with joke messages in light yellow text with small smiley emoji icon. Slight bounce-in animation on jokes. "Tell Another" button to request more jokes.
  - EN Prompt: `AI chat with joke messages in light yellow text with small smiley emoji icon, slight bounce-in animation, "Tell Another" button for more jokes, programmer humor, 4K interface mockup`
  - CN Prompt: `AI聊天中笑话消息以浅黄色文字显示前有小笑脸表情，轻微弹入动画，有"再来一个"按钮请求新笑话，程序员幽默，4K界面模型`
- **Technical Approach**:
  - Files: `misc/JokeLibrary.java` (new), modify `gui/guiscreen/GuiAIChat.java`
  - Core Design: `JokeLibrary` loads joke library from JSON file. Random joke placeholder added to AI response template, replaced during client-side rendering.
  - Config: `[ai] enableComedyMode`, `jokeFrequency`
- **Dependencies**: None additional

### Project 82
- **ID**: 082
- **Name**: 虚拟数据展览馆 / Virtual Data Museum
- **Category**: Multiplayer & Cooperation
- **Priority**: C
- **Description**: A special dimension (or standalone world) serving as the server's "Data Museum". The museum displays the server's historical data — leaderboard champions across eras, legendary crafting completion moment snapshots, earliest data beacon locations, etc. Entered via data beacon or command. Pure visitation purpose; indestructible.
- **Lore**: Every long-running GTNH server has its own history. The Virtual Data Museum digitizes these memories — it's a "memorial hall" recording the server's complete journey from barren planet to industrial empire.
- **Visual & AI Prompts**:
  - Visual Description: An endless white void dimension with glowing cyan display podiums on the floor. Each podium shows a holographic projection of a historical event with 3D charts, text, and thumbnails. Walkable museum. Deep navy-black sky with slowly rotating data nebula.
  - EN Prompt: `An endless white void dimension with glowing cyan display podiums on floor, each showing a holographic projection of a historical event with 3D chart text and thumbnail, walkable museum, deep navy-black sky with slowly rotating data nebula, digital memorial, 4K game scene`
  - CN Prompt: `无尽白色虚空维度，地板上发光青色展示台，每个展示台显示历史事件的全息投影含3D图表文字缩略图，可步行参观，深蓝黑色天空缓慢旋转数据星云，数字纪念馆，4K游戏场景`
- **Technical Approach**:
  - Files: `handler/MuseumDimensionManager.java` (new), `world/MuseumWorldProvider.java` (new), `tileentity/TileEntityMuseumPodium.java` (new)
  - Core Design: Register custom dimension via `DimensionManager`. Podiums read server-stored historical data JSON.
  - Config: `[museum] enableMuseumDimension`, `museumEntryRequirement`
- **Dependencies**: None additional

### Project 83
- **ID**: 083
- **Name**: AI 学习模式 / AI Learning Mode
- **Category**: AI Assistant Enhancements
- **Priority**: C
- **Description**: The AI Assistant can "learn" player preferences and habits. For example, if a player frequently crafts specific items at night, the AI pre-crafts them in advance. Players' commonly used query formats are prioritized for matching. Learning data is stored locally, not uploaded to the AI service (privacy protection). Learning mode must be manually enabled.
- **Lore**: A good assistant gets more helpful the more you use it. AI Learning Mode analyzes your behavior patterns and anticipates your needs before you speak. Of course, all learning data stays local — your privacy is as important as your base.
- **Visual & AI Prompts**:
  - Visual Description: AI settings with Learning Status panel showing learned pattern summaries (e.g., "You typically query ore inventory between 18:00-22:00"). Clear Learning Data button. Small bookmark icon beside messages from learning AI.
  - EN Prompt: `AI settings with Learning Status panel showing learned pattern summaries, Clear Learning Data button, small bookmark icon beside messages from learning AI, privacy-respecting adaptive AI, 4K interface mockup`
  - CN Prompt: `AI设置中学习状态面板显示已学习模式摘要，有清除学习数据按钮，学习中的AI消息旁有小书签图标，尊重隐私的自适应AI，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantLearningEngine.java` (new), modify `assistant/AssistantController.java`
  - Core Design: `AssistantLearningEngine` records player interaction history (command types, times, frequency), extracts patterns for prediction. Learning data stored in local JSON.
  - Config: `[ai] enableLearningMode` (default false), `learningDataRetentionDays`
- **Dependencies**: None additional

### Project 84
- **ID**: 084
- **Name**: 编织增幅卡·过载型 / Weave Amplifier - Overclock
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: B
- **Description**: A special version of the Weave Amplifier Card. Unlike standard amplifier cards (4x/16x stable multiplier), the Overclock variant provides a 64x extreme multiplier but with side effects — 5% chance of producing "chaos products" (random items instead of expected output) and 1% chance of temporarily damaging the weave cell (requiring repair). High risk, high reward. Appearance: red warning color instead of standard blue.
- **Lore**: The "safe mode" of amplifier cards limits true potential. The Overclock variant removes these safety limits — like overclocking a CPU to its limit. Most of the time it produces amazing output; occasionally it produces... strange things.
- **Visual & AI Prompts**:
  - Visual Description: Standard weave amplifier card appearance but in warning red and dark orange color scheme instead of standard blue. Pulsing red glow on edges. Unstable flickering light from cell when inserted. Electric spark particles and faint smoke effects when active.
  - EN Prompt: `A weave amplifier card in warning red and dark orange color scheme instead of standard blue, pulsing red glow on edges, unstable flickering light from cell when inserted, electric spark particles and faint smoke effects when active, risky overclock variant, 4K game asset`
  - CN Prompt: `编织增幅卡警告红色暗橙色配色替代标准蓝色，边缘脉冲红光，插入时元件发出不稳定闪烁，工作时有电火花粒子和轻微烟雾效果，风险过载变体，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemOverclockAmplifierCard.java` (new), modify `items/cell/DataLoomAmplifierRates.java`
  - Core Design: Implements `IWeaveAmplifierCard`, returns 64 in `getMultiplier()`. Add random chaos logic in weave cell output calculation.
  - Config: `[amplifier] overclockChaosChance`, `overclockDamageChance`
- **Dependencies**: None additional

### Project 85
- **ID**: 085
- **Name**: 蓝图打印机 / Blueprint Printer
- **Category**: Automation & Logistics
- **Priority**: B
- **Description**: Materializes AI-generated "factory blueprints" — the printer consumes paper, ink, and AE2 energy to print blueprints into a readable "Build Manual". When holding the manual, target blocks for construction are highlighted in the world as semi-transparent cyan outlines indicating their positions. Build one by one, checking off each as completed.
- **Lore**: AI-planned blueprints look perfect on screen, but you still have to switch back and forth during actual construction. The Blueprint Printer turns digital blueprints into physical guidance — follow the translucent indicators to build, like having a mentor beside you saying "put the compressor here, the assembler there."
- **Visual & AI Prompts**:
  - Visual Description: A 3D printer-like block with transparent casing showing print head printing on paper inside. Output port ejects completed build manual. Subtle mechanical sounds when printing. Manual cover shows blueprint name and date.
  - EN Prompt: `A 3D printer-like block with transparent casing showing print head printing on paper inside, outputting completed build manual, subtle mechanical sounds when printing, manual cover showing blueprint name and date, sci-fi construction tool, 4K game asset`
  - CN Prompt: `类似3D打印机的方块透明外壳可见内部打印头在纸上印刷，输出口吐出完成建造手册，印刷时细微机械声音，手册封面有蓝图名称和日期，科幻建造工具，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockBlueprintPrinter.java` (new), `tileentity/TileEntityBlueprintPrinter.java` (new), `items/ItemBuildManual.java` (new), `renders/BuildGuideRenderer.java` (new)
  - Core Design: Printing process consumes materials, produces `ItemBuildManual`. Manual renders target block outlines client-side (`RenderWorldLastEvent`).
  - Config: `[blueprint] printCostPaper`, `printCostInk`
- **Dependencies**: None additional

### Project 86
- **ID**: 086
- **Name**: 数据钓鱼竿 / Data Fishing Rod
- **Category**: Items & Tools
- **Priority**: C
- **Description**: A special fishing rod that doesn't fish in water but in the "data lake" (AE2 network). Right-click a Data Beacon with the Data Fishing Rod for a chance to "fish out" a random item (randomly selected from item types stored in the AE2 network). Fished items are actually deducted from the network, so this is "consumptive entertainment".
- **Lore**: Some say the AE2 network is a digital ocean where every item type is a fish. The Data Fishing Rod turns this joke into reality — now you can "fish" in your own inventory ocean. What you catch are your own items, of course, but you never know what the next cast brings.
- **Visual & AI Prompts**:
  - Visual Description: A tech fishing rod with deep blue carbon fiber texture shaft, glowing cyan quantum ring as reel, cyan light beam as fishing line from rod tip to data beacon, micro data receiver as hook. Item forms from converging data particles at tip when caught.
  - EN Prompt: `A tech fishing rod with deep blue carbon fiber texture shaft, glowing cyan quantum ring as reel, cyan light beam as fishing line from rod tip to data beacon, micro data receiver as hook, item forming from converging data particles at tip when caught, digital fishing, 4K game asset`
  - CN Prompt: `科技钓鱼竿深蓝碳纤维纹理竿身，发光青色量子环卷线器，青色光束作为鱼线从竿尖射向数据信标，微数据接收器作为鱼钩，钓到物品时数据粒子在竿尖汇聚成物品，数字钓鱼，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataFishingRod.java` (new), `handler/HandlerDataFishing.java` (new)
  - Core Design: Right-clicking data beacon queries AE2 network item list, randomly selects one, triggers deduction and fishing animation.
  - Config: `[misc] dataFishingCooldown`, `dataFishingCostPerItem`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 87
- **ID**: 087
- **Name**: 多维度口袋 / Cross-Dimensional Pocket
- **Category**: Dimensional Pocket Expansions
- **Priority**: B
- **Description**: The ultimate Dimensional Pocket upgrade — the Cross-Dimensional Pocket. Using a special upgrade card, the pocket can be accessed across different dimensions (put items in the overworld, retrieve in the End). Internal storage syncs via AE2 Quantum Bridge. Requires a paired quantum bridge in the target dimension. Cross-dimensional access has a slight delay (1-2 seconds).
- **Lore**: Normal pocket functionality is confined to one dimension. The Cross-Dimensional Pocket breaks this limit — your items follow you across worlds. Fight the Ender Dragon in the End while pulling emergency gear stored in the overworld.
- **Visual & AI Prompts**:
  - Visual Description: Pocket overlay border changes from cyan to purple when cross-dimension mode is active (visual representation of quantum entanglement). Brief purple particle ripple when opening. Spinning loading animation during access delay.
  - EN Prompt: `Pocket overlay border changing from cyan to purple when cross-dimension mode active, brief purple particle ripple when opening, spinning loading animation during access delay, quantum cross-dimension storage, 4K mockup`
  - CN Prompt: `口袋悬浮窗边框在跨维度模式激活时从青色变为紫色，打开时有短暂紫色粒子波纹，延迟期间显示旋转加载动画，量子跨维度存储，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketCrossDimensionManager.java` (new), modify `handler/PocketStore.java`, modify `client/PocketClientCache.java`
  - Core Design: Cross-dimension data synced via custom `WorldSavedData` globally. Pocket data written to global storage rather than per-dimension storage.
  - Config: `[pocket] crossDimensionEnabled`, `crossDimensionDelayTicks`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 88
- **ID**: 088
- **Name**: AI 守护协议 / AI Guardian Protocol
- **Category**: AI Assistant Enhancements
- **Priority**: C
- **Description**: When the player dies outside the base and all items are dropped, the AI Assistant can automatically activate "Guardian Protocol" — dispatch Data Sentinels to protect dropped items, send rescue requests to nearby online players, and mark the drop coordinates in chat. If the player has backup gear in the AE2 network, the AI can attempt to teleport some to near the player's respawn point.
- **Lore**: Death is part of GTNH. But the AI Assistant doesn't have to stand by idly. Guardian Protocol is the AI's "emergency response system" — in your most vulnerable moment, it is your digital guardian angel.
- **Visual & AI Prompts**:
  - Visual Description: Red warning box "Guardian Protocol Activated" on death screen. Dropped items wrapped in faint cyan protective shield. Data sentinels summoned to patrol nearby. Auto-sent rescue message in AI chat with coordinates.
  - EN Prompt: `Red warning box "Guardian Protocol Activated" on death screen, dropped items wrapped in faint cyan protective shield, data sentinels summoned to patrol nearby, auto-sent rescue message in AI chat with coordinates, emergency AI response, 4K game scene`
  - CN Prompt: `玩家死亡时屏幕红色警告框"守护协议已激活"，掉落物被淡青色保护罩包裹，数据哨兵被召唤到附近巡逻，AI聊天自动发送救援信息含坐标，AI紧急响应，4K游戏场景`
- **Technical Approach**:
  - Files: `assistant/AssistantGuardianProtocol.java` (new), modify `handler/HandlerPlayerJoin.java` (extend for death event)
  - Core Design: Listens to `LivingDeathEvent`, checks if player has AI Guardian Protocol configured. On activation, generates protective particles, sentinels, broadcasts rescue messages.
  - Config: `[ai] guardianProtocolEnabled`, `guardianSentinelCount`, `guardianProtectDuration`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 89
- **ID**: 089
- **Name**: 数据编织统计与成就 / Data Weaving Statistics & Achievements
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: C
- **Description**: Adds data weaving-related statistics tracking and achievement system. Statistics include: total weave count, unique woven item types, maximum single output, total consumed type count, etc. Achievements include: "First Weave" (first use of a weave cell), "Weave Master" (weave 100 different item types), "Matter Reconstructor" (use altar to synthesize a legendary item), etc.
- **Lore**: Every engineer deserves a report card. The Weave Statistics and Achievements system records every milestone in your data weaving journey, from your first failure to your first legendary synthesis.
- **Visual & AI Prompts**:
  - Visual Description: Statistics GUI with achievement list on left (colored icons for unlocked, gray silhouettes for locked). Detailed stats panel with bar charts and numbers on right. Achievement banner with cyan border, gold text, and particles popping at screen center when unlocked.
  - EN Prompt: `Statistics GUI with achievement list on left colored icons for unlocked gray silhouettes for locked, detailed stats panel with bar charts and numbers on right, achievement banner with cyan border and gold text and particles popping on screen center when unlocked, 4K interface mockup`
  - CN Prompt: `统计GUI左侧成就列表已解锁彩色图标未解锁灰色剪影，右侧详细统计面板条形图和数字展示，解锁新成就时屏幕中央弹出华丽横幅青色边框金色文字粒子效果，4K界面模型`
- **Technical Approach**:
  - Files: `handler/WeaveStatisticsTracker.java` (new), `gui/guiscreen/GuiWeaveStatistics.java` (new), modify weave cell tick logic
  - Core Design: `WeaveStatisticsTracker` persists statistics server-side (WorldSavedData). Achievements registered via Forge's `AchievementPage`.
  - Config: `[stats] enableAchievements`
- **Dependencies**: None additional

### Project 90
- **ID**: 090
- **Name**: AE2 终端扩展 · 编织标签页 / AE2 Terminal - Weave Tab
- **Category**: Data Weaving & Matter Reconstruction
- **Priority**: A
- **Description**: Adds a new "Weave" tab in the AE2 ME Terminal. This tab displays all data weave cells connected to the network and their status (progress bars, energy consumption, current output item). Players can manage weaving tasks directly in the terminal (start/pause/cancel) without walking to each cell to manually operate it.
- **Lore**: The ME Terminal is the central operation interface of the AE2 network. Integrating weave cell management into the terminal is a natural evolution — managing weave cells should be as convenient as managing crafting tasks.
- **Visual & AI Prompts**:
  - Visual Description: ME Terminal with new Weave tab icon showing a weaving shuttle in cyan. List of connected weave cells with progress bars and output icons. Start/pause/cancel buttons. Total energy and output summary at bottom.
  - EN Prompt: `ME Terminal with new Weave tab icon showing weaving shuttle in cyan, list of connected weave cells with progress bars and output icons, start pause cancel buttons, total energy and output summary at bottom, AE2 terminal extension, 4K interface mockup`
  - CN Prompt: `ME终端新增编织标签页图标青色梭子图案，已连接编织元件列表含进度条产出图标，启动暂停取消按钮，底部总能耗和总产出汇总，AE2终端扩展，4K界面模型`
- **Technical Approach**:
  - Files: `compat/ae/TerminalWeaveTab.java` (new), modify `compat/ae/AeCompat.java`
  - Core Design: Add custom tab via AE2's `IGuiHandler` or terminal extension API. Data read from weave cell NBT.
  - Config: `[ae] enableWeaveTerminalTab`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 91
- **ID**: 091
- **Name**: 数据碎片 / Data Fragments
- **Category**: Items & Tools
- **Priority**: B
- **Description**: A universal crafting material produced from Data Recyclers or as a data weaving byproduct. 4 Data Fragments craft into 1 "Data Fragment Block" (decorative block). Data Fragments can also serve as low-grade fuel (burns 200 ticks). Appearance: irregular cyan transparent small shards.
- **Lore**: Data weaving is not 100% efficient — there's always some "digital residue". Data Fragments are the solidified form of this residue. At first players might think they're useless, but engineers quickly discover they make decent fuel and building materials.
- **Visual & AI Prompts**:
  - Visual Description: Irregular shaped transparent shards, cyan semi-transparent with slight data particle flickering on edges. Icon subtly pulsing in inventory. Tiny cyan glowing fragments on ground.
  - EN Prompt: `Irregular shaped transparent shards, cyan semi-transparent with slight data particle flickering on edges, icon subtly pulsing in inventory, tiny cyan glowing fragments on ground, digital byproduct, 4K game asset`
  - CN Prompt: `不规则形状透明碎片青色半透明边缘有轻微数据粒子闪烁，物品栏中图标微微脉动，地面形态为微小青色发光碎片，数字副产品，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemDataFragment.java` (new), `blocks/BlockDataFragmentBlock.java` (new), modify `LoaderItem.java`
  - Core Design: Simple item and block, add fuel attribute and crafting recipe.
  - Config: `[misc] fragmentDropRate`, `fragmentBurnTime`
- **Dependencies**: None additional

### Project 92
- **ID**: 092
- **Name**: 口袋共享功能 / Pocket Sharing
- **Category**: Dimensional Pocket Expansions
- **Priority**: B
- **Description**: Allows players to set the pocket to "Team Mode", where members of the same guild can view (read-only) or access (read-write) items in the pocket. Permissions can be fine-grained to individual slots or pages. Used for centralized management of team resources. Pocket overlay displays team identifier when shared.
- **Lore**: In some missions, team members need to quickly exchange materials. Rather than throwing items around, share a pocket — like a team public warehouse, accessible anytime, anywhere.
- **Visual & AI Prompts**:
  - Visual Description: Pocket overlay in sharing mode shows team name and member head list in title bar. Shared slots have green borders (distinguishing from blue private slots). Brief blue animation when other members put items in.
  - EN Prompt: `Pocket overlay in sharing mode with team name and member head list in title bar, shared slots with green borders distinguishing from blue private slots, brief blue animation when other members put items, team storage UI, 4K mockup`
  - CN Prompt: `口袋悬浮窗共享模式下标题栏显示团队名称和成员头像列表，共享槽位绿色边框区别于蓝色私有槽，其他成员放入物品时有短暂蓝色动画，团队存储UI，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketShareManager.java` (new), modify `handler/PocketStore.java`
  - Core Design: Pocket data maintains read-write locks server-side, allowing concurrent access by authorized members. Permissions stored via NBT.
  - NBT Structure: `{shared: true, teamId: "uuid", slotPermissions: [{slot: 0, read: true, write: true}]}`
  - Config: `[pocket] maxSharedPlayers`
- **Dependencies**: None additional

### Project 93
- **ID**: 093
- **Name**: 世界锚·数据型 / World Anchor - Data Type
- **Category**: Blocks & Machines
- **Priority**: B
- **Description**: A special chunk loader that doesn't consume fuel but instead consumes type count from the AE2 network. Loading range adjustable based on type consumption rate. Advantages: no need for ender pearls or other traditional materials, and can be remotely toggled via AE2 network. Appearance: miniature data beacon with AE2 cable port at bottom.
- **Lore**: Traditional chunk loaders require physical fuel. The Data-Type World Anchor maintains chunk loading by "burning information" — converting type information in the AE2 network into energy that sustains spacetime continuity.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 block like a mini data beacon with thin cyan light beam on top varying in height with load range. Type consumption rate indicator on side. Blue cable port at bottom. Steady low hum when active.
  - EN Prompt: `A 1x1 block like mini data beacon with thin cyan light beam on top varying height with load range, type consumption rate indicator on side, blue cable port at bottom, steady low hum when active, AE2-powered chunkloader, 4K game asset`
  - CN Prompt: `1x1方块类似迷你数据信标顶部细细青色光柱高度随加载范围变化，侧面品类消耗速率指示器，底部蓝色线缆接口，运行时稳定低频嗡嗡声，AE2供能区块加载器，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataWorldAnchor.java` (new), `tileentity/TileEntityDataWorldAnchor.java` (new)
  - Core Design: TileEntity uses Forge's `ForgeChunkManager` to manage chunk loading. Each tick deducts types from AE2 network as maintenance cost.
  - Config: `[worldAnchor] typesPerChunkPerTick`, `maxLoadedChunks`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 94
- **ID**: 094
- **Name**: AI 离线模式 / AI Offline Mode
- **Category**: AI Assistant Enhancements
- **Priority**: B
- **Description**: When the server has no internet connection or the AI API is unavailable, the AI Assistant automatically degrades to "Offline Mode". Offline mode uses a local pre-built rule engine and keyword matching to handle basic needs (inventory queries, simple crafting requests). Cannot perform natural language reasoning, but core functions are unaffected. AI avatar turns gray when offline.
- **Lore**: Not every player has stable AI API access. Offline Mode ensures the AI Assistant always has a "base intelligence" as a bottom line — like the AI's "instinctive reactions."
- **Visual & AI Prompts**:
  - Visual Description: AI chat with grey "Offline Mode" banner on top. AI avatar turned greyscale. Message bubbles with dashed border indicating rule-engine generated content. Supported features hint showing "Inventory Query, Simple Crafting, Teleport".
  - EN Prompt: `AI chat with grey "Offline Mode" banner on top, AI avatar turned greyscale, message bubbles with dashed border indicating rule-engine generated, supported features hint showing inventory query simple crafting teleport, offline AI fallback, 4K interface mockup`
  - CN Prompt: `AI聊天界面顶部灰色"离线模式"横幅，AI头像变为灰色调，消息气泡虚线边框表示规则引擎生成，功能提示显示"库存查询、简单合成、传送"，离线AI降级，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantOfflineEngine.java` (new), modify `assistant/AssistantController.java`
  - Core Design: `AssistantOfflineEngine` implements rule-based intent parsing (enhanced version of `AssistantIntentService`). Auto-switches when API calls fail.
  - Config: `[ai] offlineModeFallback` (default true)
- **Dependencies**: None additional

### Project 95
- **ID**: 095
- **Name**: 数据熔岩灯 / Data Lava Lamp
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: A purely decorative block with slowly rising and sinking cyan "data bubbles" inside — like a lava lamp but data-themed. Bubbles rise, sink, merge, and split within the block. Serves as ambient lighting for the base (light level 8). Can be dyed (use data fragments to change bubble color). Adjacent lamps synchronize bubble motion when placed together.
- **Lore**: Every engineer's desk has a lava lamp. The Data Lava Lamp is the digital age version — bubbles are no longer wax but aggregated data clusters. They provide no practical value, but make the control room more atmospheric.
- **Visual & AI Prompts**:
  - Visual Description: A 1x1 decorative block with transparent glass casing, dark liquid medium inside with multiple glowing cyan bubbles slowly rising and sinking. Bubbles vary in size, occasionally merging into bigger ones then splitting. Emits faint ambient light level 8. Circuit-board textured base.
  - EN Prompt: `A 1x1 decorative block with transparent glass casing, dark liquid medium inside with multiple glowing cyan bubbles slowly rising and sinking, bubbles varying in size occasionally merging and splitting, emits faint ambient light level 8, circuit-board textured base, sci-fi lava lamp, 4K game asset`
  - CN Prompt: `1x1装饰方块透明玻璃外壳内部深色液体基质多个发光青色泡泡缓慢浮沉，泡泡大小不一偶尔合并然后分裂，发出微弱环境光亮度8，电路板纹理底座，科幻熔岩灯，4K游戏资产`
- **Technical Approach**:
  - Files: `blocks/BlockDataLavaLamp.java` (new), `tileentity/TileEntityDataLavaLamp.java` (new), `renders/RenderDataLavaLamp.java` (new)
  - Core Design: TESR renders bubble system. Bubbles use dynamic textures (multiple pre-rendered frames), position and size calculated via sine waves.
  - Config: `[visual] lavaLampParticleCount`
- **Dependencies**: None additional

### Project 96
- **ID**: 096
- **Name**: 双持数据之手 / Dual Wield Hands of Data
- **Category**: Items & Tools
- **Priority**: C
- **Description**: Allows players to simultaneously equip two Hands of Data (main hand + off-hand). Dual wielding unlocks an extra mode: "Data Transfer" — extract data from a block in the left hand's target and inject it in real-time into the right hand's target block, without switching items. Transfer rate doubles but energy consumption is 3x.
- **Lore**: Wielding a Hand of Data in each hand requires exceptional coordination. But once mastered, you're no longer just carrying data — you're simultaneously reading and writing reality. Left hand extracts, right hand injects, and between them flows your will.
- **Visual & AI Prompts**:
  - Visual Description: Player dual-wielding two Hands of Data, a curved cyan data arc connecting between them. Left gauntlet emits extraction pulse as expanding light ring, right gauntlet emits injection pulse as converging light ring. Player movement slightly slowed.
  - EN Prompt: `Player dual-wielding two Hands of Data, curved cyan data arc connecting between them, left gauntlet emitting extraction pulse as expanding light ring, right gauntlet emitting injection pulse as converging light ring, player movement slightly slowed, dual-wield sci-fi tool, 4K game scene`
  - CN Prompt: `玩家双手各持一把数据之手，两把之间弯曲青色数据弧线连接，左手发出提取脉冲向外扩散光环，右手发出注入脉冲向内汇聚光环，玩家移动速度略微降低，双持科幻工具，4K游戏场景`
- **Technical Approach**:
  - Files: `handler/HandlerDualHandOfData.java` (new), modify `items/ItemHandOfData.java`
  - Core Design: Detects whether player main and off-hand are both `ItemHandOfData`. Dual-wield mode provides additional interaction logic.
  - Config: `[handOfData] dualWieldEnergyMultiplier`, `dualWieldSpeedPenalty`
- **Dependencies**: GT5-Unofficial (required)

### Project 97
- **ID**: 097
- **Name**: 数据编织周报 / Data Weaving Weekly Report
- **Category**: AI Assistant Enhancements
- **Priority**: C
- **Description**: The AI Assistant automatically generates a "Data Weaving Weekly Report" at configurable intervals (1h/6h/24h/weekly). Report contents include: woven product type summary during the period, consumed type count, energy consumption, anomaly events (such as cell damage), and comparison changes from the previous period. Reports presented as formatted text in AI chat.
- **Lore**: Excellent engineers need data-driven decisions. The weekly report transforms raw data into readable summaries, telling you "how your factory performed this week." It's your AI factory manager's regular work report.
- **Visual & AI Prompts**:
  - Visual Description: Formatted weekly report message in AI chat with large cyan title, section headers for production/energy/anomalies, key numbers highlighted in gold for growth and red for decline. Small trend chart attached.
  - EN Prompt: `Formatted weekly report message in AI chat with large cyan title, section headers for production energy anomalies, key numbers highlighted in gold for growth red for decline, small trend chart attached, business-style data report, 4K interface mockup`
  - CN Prompt: `AI聊天中推送格式化周报消息含大号青色标题、分段小标题编织产出能耗异常事件、关键数字金色高亮增长红色下降，附小型趋势图表，商务风格数据报告，4K界面模型`
- **Technical Approach**:
  - Files: `assistant/AssistantReportGenerator.java` (new), modify `handler/HandlerTick.java`
  - Core Design: `AssistantReportGenerator` aggregates periodic statistics, generates natural language summary via `DeepSeekChatClient` (or uses rule-based generation for offline).
  - Config: `[ai] reportIntervalMinutes`, `reportAutoPush`
- **Dependencies**: None additional

### Project 98
- **ID**: 098
- **Name**: 次元口袋·自动压缩 / Pocket Auto-Compression
- **Category**: Dimensional Pocket Expansions
- **Priority**: B
- **Description**: Items in the pocket auto-compress if they meet compression conditions (e.g., 9 iron ingots → 1 iron block), saving space. Compression rules are customizable — players can add/remove compression recipes. Auto-compression triggers when items enter the pocket, with subtle cyan particle effects. A "no-compress list" can be set (e.g., tools excluded).
- **Lore**: Pocket space is limited, but many items occupy slots in low-tier form. Auto-compression at the storage level automatically synthesizes basic materials into higher forms — not physical crafting, but "recombination" at the data weaving level.
- **Visual & AI Prompts**:
  - Visual Description: Items briefly flash with compression animation when placed in pocket — icon shrinks then pops a larger icon. Compressed items marked with gold border (distinguishing from uncompressed items). Compression rule editor panel in pocket config.
  - EN Prompt: `Items briefly flashing with compression animation when placed in pocket, icon shrinking then popping larger, compressed items marked with gold border, compression rule editor panel in pocket config, auto-storage optimization, 4K mockup`
  - CN Prompt: `物品放入口袋时满足压缩条件短暂闪烁压缩动画，图标缩小后弹出更大图标，压缩后物品金色边框标记，口袋配置中有压缩规则编辑面板，自动存储优化，4K模型`
- **Technical Approach**:
  - Files: `handler/PocketAutoCompression.java` (new), modify `handler/PocketInventory.java`
  - Core Design: `PocketAutoCompression` checks compression recipes when items enter pocket (reverse of vanilla 3x3 crafting), replacing with compressed items.
  - Config: `[pocket] enableAutoCompression`, `compressionRules`
- **Dependencies**: None additional

### Project 99
- **ID**: 099
- **Name**: AI 翻译模式 / AI Translation Mode
- **Category**: AI Assistant Enhancements
- **Priority**: C
- **Description**: The AI Assistant can act as a translator between multilingual players. When a server has a mix of Chinese and English players, the AI can translate Chinese commands to English broadcasts for English players, or English commands to Chinese. Translation functionality triggered via command or AI chat. Translated results preserve original intent structure.
- **Lore**: The GTNH community is global. Language barriers should not hinder cooperation. AI Translation Mode enables seamless cross-language communication between Chinese and English players on the same server — the AI not only understands commands but can translate them.
- **Visual & AI Prompts**:
  - Visual Description: AI chat with bilingual translation messages — source language in grey above, translation in cyan below. Language icons (CN/EN) on message bubbles. Brief "translating" animation with three bouncing dots during translation.
  - EN Prompt: `AI chat with bilingual translation messages showing source language in grey above translation in cyan, language icons CN EN on bubbles, brief translating animation with three bouncing dots, language bridge feature, 4K mockup`
  - CN Prompt: `AI聊天中翻译消息以双语显示上方源语言灰色下方翻译青色，消息气泡有中英语标图标，翻译过程中有三个点跳动的翻译中动画，语言桥梁功能，4K模型`
- **Technical Approach**:
  - Files: `assistant/AssistantTranslator.java` (new), modify `assistant/ai/DeepSeekChatClient.java`
  - Core Design: `AssistantTranslator` adds translation instructions to AI requests, explicitly specifying input and output languages.
  - Config: `[ai] enableTranslation`, `defaultTargetLanguage`
- **Dependencies**: None additional

### Project 100
- **ID**: 100
- **Name**: 数据编织大师挑战 / Data Weave Master Challenge
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: A hidden "ultimate challenge" system. When a player completes all data weaving achievements, the "Master Challenge" unlocks — accessed via `/admchallenge` command to enter a special puzzle dimension. The dimension contains 10 levels, each requiring data weaving techniques to solve a physical puzzle. Clearing all levels grants the purely cosmetic "Data Weave Master" title and a particle halo.
- **Lore**: The original inventor of data weaving left a test in the void — only those who truly master data weaving can pass. This isn't a test of power, but of deep understanding of the principles of data materialization.
- **Visual & AI Prompts**:
  - Visual Description: A white void challenge dimension with different themed rooms per level. Space briefly changes color with "Pass" text on completion. Rainbow gradient master title above the player name. Permanent small cyan hexagonal particle halo rotating around the body after completion.
  - EN Prompt: `White void challenge dimension with different themed rooms per level, space briefly changes color with pass text on completion, rainbow gradient master title above player name, permanent small cyan hexagonal particle halo rotating around body after completion, puzzle dimension, 4K game scene`
  - CN Prompt: `白色虚空挑战维度每关不同主题配色和解谜元素，过关时空间短暂变色出现过关文字，彩虹渐变大师称号在名字上方显示，通关后永久获得围绕身体旋转的小型青色六边形粒子光环，解谜维度，4K游戏场景`
- **Technical Approach**:
  - Files: `handler/MasterChallengeManager.java` (new), `world/ChallengeDimensionProvider.java` (new), `command/CommandMasterChallenge.java` (new)
  - Core Design: Register challenge dimension via `DimensionManager`. Each level is an independent area (via coordinate offset). Puzzle logic uses command-block-style event detection.
  - Config: `[misc] enableMasterChallenge`
- **Dependencies**: None additional

### Project 101
- **ID**: 101
- **Name**: 数据编织背包 / Data Weave Backpack
- **Category**: Items & Tools
- **Priority**: B
- **Description**: A special backpack (Baubles belt slot) with a mini data weave cell inside. Items placed in the backpack slowly undergo "weave upgrading" — iron to steel, steel to iridium (very slow rate). Upgrade speed depends on backpack quality. Not usable for rapid combat gear upgrades (rate ceiling), but ideal for slowly improving material quality while AFK.
- **Lore**: When you're offline, your materials can evolve on their own. The Data Weave Backpack utilizes idle time — you sleep, work, attend school, while iron ingots in your backpack slowly transform into more valuable things.
- **Visual & AI Prompts**:
  - Visual Description: A small-medium backpack with deep blue metal body, mini weave array window on front showing glowing weaving inside. Grey metal chain straps. Visible on player waist when equipped. Faint hum when working.
  - EN Prompt: `A small-medium backpack with deep blue metal body, mini weave array window on front showing glowing weaving inside, grey metal chain straps, visible on player waist when equipped, faint hum when working, idle-time material upgrade tool, 4K game asset`
  - CN Prompt: `中小型背包深蓝金属箱体，正面微型编织阵列窗口可见内部发光编织过程，灰色金属链背带，装备时玩家腰间显示背包模型，工作时发出微弱嗡嗡声，闲置时间材料升级工具，4K游戏资产`
- **Technical Approach**:
  - Files: `items/ItemWeaveBackpack.java` (new), `handler/HandlerWeaveBackpack.java` (new)
  - Core Design: As Baubles accessory, checks backpack content in player tick and slowly upgrades. Upgrade recipes use GT5 material tier system.
  - NBT Structure: `{upgradeQueue: [{from: "minecraft:iron_ingot", to: "minecraft:steel_ingot", progress: 0.5}]}`
  - Config: `[weaveBackpack] upgradeTickInterval`, `maxUpgradeRate`
- **Dependencies**: GT5-Unofficial (required)

### Project 102
- **ID**: 102
- **Name**: 数字镜像世界 / Digital Mirror World
- **Category**: Visual & Immersion
- **Priority**: C
- **Description**: A purely decorative visit mode — the "Digital Mirror World" can be entered via a Data Teleport Pad. This world is a 1:1 copy of the overworld, but all blocks appear as deep navy-black semi-transparent "digital outlines" (wireframe + semi-transparent fill), with creatures and players shown as cyan particle silhouettes. Non-interactive (cannot break blocks), purely for visiting and planning.
- **Lore**: This is how the AE2 network "perceives" your world. Every block in the network is digitally recorded — not the block itself, but its "information reflection". Walking through the Mirror World is like entering a CAD model of your own factory.
- **Visual & AI Prompts**:
  - Visual Description: A dimension made of deep navy-black semi-transparent wireframe blocks with glowing cyan outlines. Semi-transparent dark fill inside blocks. Player shown as cyan humanoid silhouette. No ambient sounds, only deep data hum. Data particle ripples underfoot when walking.
  - EN Prompt: `A dimension made of deep navy-black semi-transparent wireframe blocks with glowing cyan outlines, semi-transparent dark fill inside blocks, player shown as cyan humanoid silhouette, no ambient sounds only deep data hum, data particle ripples underfoot when walking, digital mirror of real world, 4K game scene`
  - CN Prompt: `由深蓝黑半透明线框方块组成的维度发光青色轮廓勾勒方块，半透明深色填充方块内部，玩家显示为青色人形轮廓，无环境音只有深沉数据流动嗡嗡声，行走时脚下产生数据粒子涟漪，现实世界的数字镜像，4K游戏场景`
- **Technical Approach**:
  - Files: `world/MirrorWorldProvider.java` (new), `renders/MirrorWorldRenderer.java` (new)
  - Core Design: Use custom WorldProvider and WorldRenderer to override block rendering as wireframe mode. Use Forge's `DrawBlockHighlightEvent` or custom `RenderGlobal`.
  - Config: `[visual] enableMirrorWorld`
- **Dependencies**: None additional

### Project 103
- **ID**: 103
- **Name**: 数据编织生物标本 / Data Weave Mob Specimen
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: When weaving creatures in the Bio-Data Synthesizer, there's a very small chance (1%) of producing a "specimen version" — a miniature, immobile, pure-data sculpture of the creature. These specimens can be placed as decorations. Specimens are at 1/4 scale of the original creature and appear cyan semi-transparent. Collecting all specimen types unlocks the special title "Digital Noah."
- **Lore**: Sometimes the data weaving process creates an "incomplete copy" — a digital sculpture containing only the creature's appearance information but no life force. Collecting them is a hobby of data weavers.
- **Visual & AI Prompts**:
  - Visual Description: Miniature mob models at 1/4 size made of cyan semi-transparent material with data stream textures on surface. Placed on display pedestals emitting faint cyan light. Different pedestal styles per mob. Serene cyan glow in specimen room.
  - EN Prompt: `Miniature mob models 1/4 size made of cyan semi-transparent material with data stream textures on surface, placed on display pedestals emitting faint cyan light, different pedestal styles per mob, serene cyan glow in specimen room, digital collectibles, 4K game scene`
  - CN Prompt: `迷你生物模型1/4大小由青色半透明材质构成表面有数据流纹理，放置在展示台上发出微弱青色光，每种生物不同底座样式，标本房间在黑暗中发出宁静青色光芒，数字收藏品，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockSpecimenPedestal.java` (new), `tileentity/TileEntitySpecimenPedestal.java` (new), `renders/RenderSpecimen.java` (new)
  - Core Design: Mob specimens are NBT-driven decorative blocks storing mob type and pose. TESR renders scaled-down mob models.
  - NBT Structure: `{mobType: "Cow", scale: 0.25, pose: "standing"}`
  - Config: `[misc] specimenDropRate`
- **Dependencies**: None additional

### Project 104
- **ID**: 104
- **Name**: 数据天气控制器 / Data Weather Controller
- **Category**: Blocks & Machines
- **Priority**: C
- **Description**: An advanced machine that consumes type count from the AE2 network and massive energy to control weather. Can toggle clear/rain/thunder weather (overriding existing weather systems), or generate "Data Storms" — a purely visual weather effect where cyan lightning and glowing rain particles fill the sky. Data Storms do not affect gameplay mechanics.
- **Lore**: Weather is one of Minecraft's least controllable variables. The Data Weather Controller overrides natural weather patterns by injecting localized "data interference" into the atmosphere. Data Storms are the visual manifestation of these interferences.
- **Visual & AI Prompts**:
  - Visual Description: A large antenna block with dish reflector on top, glowing cyan spiral pattern on dish surface. Dish rotates rapidly and emits cyan beam upward when controlling weather. Data storm features cyan clouds dropping glowing cyan binary rain.
  - EN Prompt: `A large antenna block with dish reflector on top, glowing cyan spiral pattern on dish surface, dish rotating rapidly and emitting cyan beam upward when controlling weather, data storm with cyan clouds dropping glowing cyan binary rain, sci-fi weather control, 4K game scene`
  - CN Prompt: `大型天线方块顶部碟形反射器碟面发光青色螺旋纹路，控制天气时碟面快速旋转向上发射青色光束，数据风暴中天空遍布青色乌云降下发光的青色二进制数字雨，科幻天气控制，4K游戏场景`
- **Technical Approach**:
  - Files: `blocks/BlockDataWeatherController.java` (new), `tileentity/TileEntityDataWeatherController.java` (new), `renders/DataStormRenderer.java` (new)
  - Core Design: TileEntity controls weather via `World.setRainStrength` and `World.setThunderStrength`. Data storm implemented via custom particle rendering layer.
  - Config: `[weather] controllerRange`, `dataStormEnergyCost`
- **Dependencies**: AE2FluidCraft-Rework (required)

### Project 105
- **ID**: 105
- **Name**: 彩蛋：开发者密室 / Easter Egg: Developer's Vault
- **Category**: Misc & Easter Eggs
- **Priority**: C
- **Description**: An extremely rarely generated hidden room in the world — the "Developer's Vault". The vault's walls are made of Data Fragment Blocks, with a holographic podium in the center displaying the TeXTech development team's names (as holographic text). The vault contains a chest with a full set of data weaving tools (unbreakable versions). The spawn chance is extremely low (comparable to Woodland Mansions), but discovering it grants the achievement "Behind the Scenes".
- **Lore**: It is said that the original inventors of data weaving left a hidden vault somewhere in the real world. Those fortunate enough to find it will see the creators' names eternally inscribed in data form. This is the mod developers' tribute to careful explorers.
- **Visual & AI Prompts**:
  - Visual Description: A hidden 5x5x3 underground room. Walls made of Data Fragment Blocks (glowing cyan blocks). Ceiling with flowing data stream overlay. Central holographic podium projecting developer team names in rotating glowing gold-cyan text. Circuit board floor texture.
  - EN Prompt: `A hidden 5x5x3 underground room, walls made of data fragment blocks, ceiling with flowing data stream overlay, central holographic podium projecting developer team names in rotating glowing gold-cyan text, circuit board floor texture, secret developer tribute room, 4K game scene`
  - CN Prompt: `隐藏地下的5x5x3小房间，墙壁由数据碎片块构成，天花板流动数据流覆盖层，中央全息投影台投射开发团队名字以发光金色青色混色文字旋转显示，电路板纹理地板，秘密开发者致敬房间，4K游戏场景`
- **Technical Approach**:
  - Files: `world/DevVaultGenerator.java` (new), modify `TeXTech.java` (register world generator)
  - Core Design: Use `IWorldGenerator` to randomly generate the structure underground. Structure is a pre-made Schematic or hardcoded block placement logic.
  - Config: `[misc] devVaultSpawnChance` (per-chunk generation chance)
- **Dependencies**: None additional

---

> *"The future is not predicted — it is woven. In this digitized GregTech New Horizon, every line of code, every segment of data, every woven substance — are all steps toward the sea of stars."*

> *— TeXTech Development Team, 2026*
