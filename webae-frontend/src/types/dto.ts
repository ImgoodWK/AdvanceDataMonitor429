// TypeScript interfaces mirroring the Java DTOs in src/main/java/com/imgood/textech/webae/dto/

export interface NetworkInfo {
  networkId: number;
  name: string;
}

export interface StorageItem {
  itemId: string;
  displayName: string;
  registryName: string;
  meta: number;
  amount: number;
  nbtHash: string;
}

export interface StorageFluid {
  fluidName: string;
  amount: number;
}

export interface StorageEssentia {
  aspect: string;
  amount: number;
}

export interface StorageCpu {
  name: string;
  storedItems: number;
  maxItems: number;
  craftingProgress: number;
  isBusy: boolean;
  availableStorage: number;
  usedStorage: number;
  coProcessors: number;
  finalOutputName: string;
  finalOutputAmount: number;
  elapsedTime: number;
  /** Crafting link block coordinates (from backend, optional). */
  x?: number;
  y?: number;
  z?: number;
  dim?: number;
  /** Data monitor coordinates for the network group. */
  monitorX?: number;
  monitorY?: number;
  monitorZ?: number;
  monitorDim?: number;
  /** Set when merging CPUs across networks on the client. */
  networkId?: number;
  remainingItems?: number;
  startItems?: number;
}

export interface StorageDto {
  networkId: number;
  timestamp: number;
  items: StorageItem[];
  fluids: StorageFluid[];
  essentia: StorageEssentia[];
  bytesUsed: number;
  bytesMax: number;
  cpus: StorageCpu[];
}

export interface StorageResponse {
  success: boolean;
  data: StorageDto | null;
  cached: boolean;
  timestamp: number;
}

export interface StorageBatchResult {
  networkId: number;
  data: StorageDto | null;
  cached: boolean;
  timestamp: number;
}

export interface StorageBatchResponse {
  success: boolean;
  results: StorageBatchResult[];
}

export interface StoragePagedResponse {
  success: boolean;
  items?: StorageItem[];
  fluids?: StorageFluid[];
  essentia?: StorageEssentia[];
  nextCursor?: string | null;
  totalEstimate: number;
  fromCache: boolean;
  cacheAgeMs: number;
  snapshotVersion: number;
  networkId?: number;
  bytesUsed?: number;
  bytesMax?: number;
  cpus?: StorageCpu[];
  totalAmountSum?: number;
}

export interface NetworksResponse {
  success: boolean;
  networks: NetworkInfo[];
}

export interface PowerDto {
  networkId: number;
  timestamp: number;
  euStored: number;
  euMax: number;
  euInRate: number;
  euOutRate: number;
  steamStored: number;
  steamMax: number;
  steamInRate: number;
  steamOutRate: number;
  euHistory: number[];
  steamHistory: number[];
  euHistoryTimestamps?: number[];
  steamHistoryTimestamps?: number[];
}

export interface PowerResponse {
  success: boolean;
  data: PowerDto | null;
  cached: boolean;
  timestamp: number;
}

export interface PowerBatchResult {
  networkId: number;
  data: PowerDto | null;
  cached: boolean;
  timestamp: number;
}

export interface PowerBatchResponse {
  success: boolean;
  results: PowerBatchResult[];
}

export interface GtMachineDto {
  x: number;
  y: number;
  z: number;
  dim: number;
  isActive: boolean;
  errorId: number;
  problemId: number;
  progressTime: number;
  maxProgressTime: number;
  progressPercent: number;
  storedEU: number;
  euCapacity: number;
  inputVoltage: number;
  outputVoltage: number;
  recipeMapName: string;
  machineMode: string;
  repairStatus: number;
  parallelCount: number;
  currentOutput: string;
  statusText: string;
}

export interface GtMachineListDto {
  networkId: number;
  timestamp: number;
  machines: GtMachineDto[];
}

export interface GtMachineResponse {
  success: boolean;
  data: GtMachineListDto | null;
  cached: boolean;
  timestamp: number;
}

export interface GtMachineBatchResult {
  networkId: number;
  data: GtMachineListDto | null;
  cached: boolean;
  timestamp: number;
}

export interface GtMachineBatchResponse {
  success: boolean;
  results: GtMachineBatchResult[];
}

export interface RecipeItemEntry {
  itemId?: string;
  displayName: string;
  registryName: string;
  meta: number;
  stackSize: number;
}

export interface RecipeGridSlot {
  col: number;
  row: number;
  item: RecipeItemEntry;
}

export interface RecipeDto {
  handlerId: string;
  recipeIndex: number;
  handlerName: string;
  inputs: RecipeItemEntry[];
  outputs: RecipeItemEntry[];
  rawJson?: string;
  gridWidth?: number;
  gridHeight?: number;
  gridSlots?: RecipeGridSlot[];
  euPerTick?: number;
  durationTicks?: number;
  voltageTier?: string;
  requiresCleanroom?: boolean;
  powerConsumption?: number;
  recipeType?: string;
}

export interface RecipeHandlerInfo {
  handlerId: string;
  handlerName: string;
  recipeCount: number;
}

export interface RecipeHandlersResponse {
  success: boolean;
  handlers: RecipeHandlerInfo[];
}

export interface RecipeCacheStatus {
  recipeCount: number;
  handlerCount: number;
  totalIngested?: number;
  lastUpdateTime?: number;
  diskCacheSize: number;
  lastDiskSave: number;
}

export interface RecipeBrowseResponse {
  success: boolean;
  results: RecipeDto[];
  count: number;
  total: number;
  offset: number;
  limit: number;
}

export interface RecipeStatusResponse {
  success: boolean;
  status: RecipeCacheStatus;
}

export interface RecipeSearchResponse {
  success: boolean;
  results: RecipeDto[];
  count: number;
  total?: number;
  offset?: number;
  limit?: number;
}

export interface RecipeSuggestEntry {
  registryName: string;
  displayName: string;
  itemId?: string;
}

export interface RecipeSuggestResponse {
  success: boolean;
  suggestions: RecipeSuggestEntry[];
  count: number;
}

export interface PatternItemEntry {
  registryName: string;
  displayName: string;
  meta: number;
  stackSize: number;
  isFluid: boolean;
}

export interface PatternDto {
  patternId: string;
  crafting: boolean;
  substitute: boolean;
  beSubstitute: boolean;
  author: string;
  inputs: (PatternItemEntry | null)[];
  outputs: PatternItemEntry[];
  encodedNbt: string;
}

export interface PatternSlotState {
  index: number;
  occupied: boolean;
  patternSummary: string;
}

export interface InterfaceExistingPattern {
  slotIndex: number;
  patternId: string;
  outputs: PatternItemEntry[];
  crafting: boolean;
}

export interface InterfaceDto {
  name: string;
  x: number;
  y: number;
  z: number;
  dim: number;
  capacityUpgrades: number;
  activeSlots: number;
  slots: PatternSlotState[];
  targetMachineName: string;
  targetRecipePool: string;
  /** Phase 6: machine name + recipe pool label for UI. */
  machineRecipeType?: string;
  /** Phase 6: occupied patterns with outputs. */
  existingPatterns?: InterfaceExistingPattern[];
}

export interface InterfacesResponse {
  success: boolean;
  interfaces: InterfaceDto[];
}

export interface PatternEncodeResponse {
  success: boolean;
  /** 后端 {@code /api/pattern/encode} 返回 {@code {success, data:<PatternDto>}}，encodedNbt 在 data 内。 */
  data?: PatternDto;
  /** 旧字段兼容（部分旧路径可能直接返回 encodedNbt）。 */
  encodedNbt?: string;
  pattern?: PatternDto;
  message?: string;
  code?: string;
}

export interface PatternInjectResult {
  success: boolean;
  message: string;
  updatedInterface: InterfaceDto | null;
}

/** 后端 {@code /api/pattern/inject} 返回 {@code {success, result:<PatternInjectResult>}}。 */
export interface PatternInjectResponse {
  success: boolean;
  result?: PatternInjectResult;
  message?: string;
}

/** 网络样板总览条目（GET /api/patterns 返回数组项）。 */
export interface PatternListEntryDto {
  patternId: string;
  /** 来源接口坐标编码，格式 `<x>:<y>:<z>:<dim>`。 */
  sourceInterface: string;
  /** 来源接口显示名。 */
  sourceInterfaceName: string;
  /** 接口槽位索引（0-based）。 */
  slotIndex: number;
  crafting: boolean;
  substitute: boolean;
  beSubstitute: boolean;
  author: string;
  inputs: (PatternItemEntry | null)[];
  outputs: PatternItemEntry[];
  /** 编码后 NBT JSON 字符串（用于回写/导出）。 */
  encodedNbt?: string;
  /** browse API：`grid` | `interface` */
  source?: 'grid' | 'interface';
  /** browse API：Grid 条目唯一键 */
  gridKey?: string;
  displayName?: string;
  registryName?: string;
  meta?: number;
  amount?: number;
  inputsCount?: number;
  outputsCount?: number;
}

export interface PatternBrowseResponse {
  success: boolean;
  entries: PatternListEntryDto[];
  total: number;
  offset: number;
  limit: number;
  truncated: boolean;
  sources: { grid: number; interface: number };
  cached?: boolean;
  timestamp?: number;
  message?: string;
}

export interface PatternListResponse {
  success: boolean;
  patterns: PatternListEntryDto[];
  message?: string;
}

export interface PatternDetailResponse {
  success: boolean;
  pattern: PatternListEntryDto | null;
  message?: string;
}

/** PUT /api/patterns/<id> 编辑回写请求体。 */
export interface PatternUpdateRequest {
  encodedNbt: string;
  /** 可选：覆盖来源接口坐标（移动到新槽位/接口）。 */
  interfaceX?: number;
  interfaceY?: number;
  interfaceZ?: number;
  interfaceDim?: number;
  slotIndex?: number;
}

export interface PatternUpdateResponse {
  success: boolean;
  message: string;
  pattern?: PatternListEntryDto;
}

export interface PatternDeleteResponse {
  success: boolean;
  message: string;
}

export interface OrderRequest {
  networkId: number;
  itemName: string;
  amount: number;
  rawText: string;
  locale: string;
  /** 可选：指定 AE2 合成处理器名称。 */
  cpuName?: string;
  /** 可选：按样板 ID 下单（优先于 itemName）。 */
  patternId?: string;
}

export interface OrderBatchItem {
  itemName: string;
  amount: number;
  /** 可选：按样板 ID 下单（优先于 itemName）。 */
  patternId?: string;
}

export interface OrderBatchRequest {
  networkId: number;
  items: OrderBatchItem[];
  /** 可选：批量下单共用的 CPU 名称。 */
  cpuName?: string;
}

export interface OrderTemplateItem {
  itemName: string;
  amount: number;
  patternId?: string | null;
}

export interface OrderTemplate {
  id: string;
  name: string;
  cpuName?: string;
  networkId: number;
  items: OrderTemplateItem[];
  updatedAt: number;
}

export interface OrderTemplatesResponse {
  success: boolean;
  count?: number;
  templates?: OrderTemplate[];
  message?: string;
  code?: string;
}

export interface OrderResult {
  success: boolean;
  craftJobId: string;
  message: string;
  estimatedTime: number;
}

export interface OrderCpuInfo {
  coProcessors: number;
  storage: number;
  parallelism: number;
}

export interface OrderStatus {
  craftJobId: string;
  status: 'pending' | 'crafting' | 'completed' | 'cancelled' | 'failed';
  progressPercent: number;
  message: string;
  submittedAt: number;
  completedAt: number;
  cpuName?: string;
  cpuInfo?: OrderCpuInfo;
  finalProgress?: number;
  /** 下单物品显示名（用于再次下单）。 */
  itemName?: string;
  /** 下单数量。 */
  amount?: number;
  /** 按样板下单时的 patternId。 */
  patternId?: string;
  networkId?: number;
  craftingId?: string;
  startItems?: number;
  remainingItems?: number;
  elapsedMs?: number;
  failReason?: string;
  cancelReason?: string;
  /** AE2 craft-tree step progress (not final-output count). */
  progressKind?: 'steps';
}

export interface OrderListResponse {
  success: boolean;
  orders: OrderStatus[];
  history?: OrderStatus[];
}

export interface ChatMessageDto {
  id: number;
  senderUuid: string;
  senderName: string;
  content: string;
  timestamp: number;
  source: 'game' | 'web' | 'system';
}

export interface ChatHistoryResponse {
  success: boolean;
  messages: ChatMessageDto[];
}

export interface ChatSinceResponse {
  success: boolean;
  messages: ChatMessageDto[];
}

export interface ChatSendResponse {
  success: boolean;
  message: string;
}

export interface PlayerDto {
  uuid: string;
  name: string;
  online: boolean;
  onlineMs: number;
  lastLogin: number;
  lastLogout: number;
  skinUrl: string | null;
}

/**
 * 后端 {@code /api/players} 返回 {@code {online:[...], offline:[...]}}；
 * {@code /api/players/since} 返回 {@code {players:[...]}}。
 * 前端合并 online+offline 为 players 数组使用，同时保留兼容字段。
 */
export interface PlayersResponse {
  success: boolean;
  players?: PlayerDto[];
  online?: PlayerDto[];
  offline?: PlayerDto[];
}

/** GET /api/players/locations (Phase 6.1). */
export interface PlayerLocationDto {
  uuid: string;
  name: string;
  x: number;
  y: number;
  z: number;
  dim: number;
  online: boolean;
}

export interface PlayerLocationsResponse {
  success: boolean;
  locations: PlayerLocationDto[];
}

/** POST /api/auth/guest-invite (Phase 6.2). */
export interface GuestInviteResponse {
  success: boolean;
  token: string;
  url: string;
  tokenType: string;
  message?: string;
}

/** 后端 {@code /api/players/online/history} 返回的在线人数趋势点。 */
export interface PlayerOnlineHistoryPoint {
  ts: number;
  count: number;
}

export interface PlayerOnlineHistoryResponse {
  success: boolean;
  history: PlayerOnlineHistoryPoint[];
}

/**
 * Network-wide scalar metric history (rolling window), returned by
 * GET /api/network/metrics?network=<id>. All history arrays share the same
 * length and align by index with {@link timestamps}.
 */
export interface NetworkMetricHistory {
  networkId: number;
  timestamps: number[];
  itemCountHistory: number[];
  fluidCountHistory: number[];
  essentiaCountHistory: number[];
  bytesUsedHistory: number[];
  bytesMaxHistory: number[];
  bytesPercentHistory: number[];
  itemTotalHistory: number[];
  fluidTotalHistory: number[];
  activeCpuHistory: number[];
  busyCpuHistory: number[];
  cpuBusyRatioHistory: number[];
  gtMachineCountHistory: number[];
  gtActiveCountHistory: number[];
}

export interface NetworkMetricHistoryResponse {
  success: boolean;
  history: NetworkMetricHistory;
}

export interface NetworkMetricFluidSeries {
  timestamps: number[];
  amounts: number[];
}

export interface NetworkMetricFluidHistory {
  networkId: number;
  fluids: Record<string, NetworkMetricFluidSeries>;
}

export interface NetworkMetricFluidHistoryResponse {
  success: boolean;
  history: NetworkMetricFluidHistory;
}

export interface NetworkMetricItemSeries {
  timestamps: number[];
  amounts: number[];
}

export interface NetworkMetricItemHistory {
  networkId: number;
  items: Record<string, NetworkMetricItemSeries>;
}

export interface NetworkMetricItemHistoryResponse {
  success: boolean;
  history: NetworkMetricItemHistory;
  message?: string;
}

export interface NetworkMetricEntitySeries {
  field?: string;
  timestamps: number[];
  values: number[];
}

export interface NetworkMetricEntityHistory {
  networkId: number;
  entities: Record<string, NetworkMetricEntitySeries>;
}

export interface NetworkMetricEntityHistoryResponse {
  success: boolean;
  history: NetworkMetricEntityHistory;
  message?: string;
}

export interface IconRenderModeInfo {
  id: string;
  labelKey: string;
  descriptionKey: string;
  implemented: boolean;
}

export interface ServerConfig {
  refreshIntervalMs: number;
  gtRefreshIntervalMs: number;
  maxNetworksDisplayed: number;
  tokenLifetimeHours: number;
  themePresets: string[];
  themeColors: string[];
  themeLayouts: string[];
  iconCacheEnabled: boolean;
  iconUploadEnabled: boolean;
  iconPackEnabled: boolean;
  iconRenderModes?: IconRenderModeInfo[];
  iconRenderPerTick?: number;
  iconRenderPerTickAll?: number;
  /** Phase 3.3: per-feature server debug switch mirror (read-only display). */
  debugFlags?: {
    icons: boolean;
    chat: boolean;
    dashboard: boolean;
    synthesis: boolean;
    patterns: boolean;
  };
  /** Phase 1 topology API gate + cache TTL mirror from [webConsole]. */
  topologyEnabled?: boolean;
  topologyCacheTtlMs?: number;
  worldMapSnapshotCooldownMs?: number;
  alertsEnabled?: boolean;
  alertsPollIntervalSeconds?: number;
  /** Phase 6.1: optional Dynmap base URL from [webConsole] dynmapBaseUrl. */
  dynmapBaseUrl?: string;
  /** World map overlay API enabled (requires topologyEnabled). */
  worldMapEnabled?: boolean;
  worldMapMaxQualityTier?: string;
  worldMapDefaultQualityTier?: string;
  questEnabled?: boolean;
  questSubmitEnabled?: boolean;
  questChainSubmitEnabled?: boolean;
  dashboardMaxTracksPerWidget?: number;
  dashboardMaxTracksGlobal?: number;
  dashboardMaxItemTracks?: number;
  dashboardMaxFluidTracks?: number;
  dashboardMaxEntityTracks?: number;
}

export interface QuestMetaDto {
  questsAvailable: boolean;
  questEnabled: boolean;
  questSubmitEnabled: boolean;
  questChainSubmitEnabled?: boolean;
  modVersion: string;
  lineCount: number;
  standardExpansionLoaded: boolean;
}

export interface QuestLineSummaryDto {
  lineId: string;
  name: string;
  description: string;
  iconItemId?: string;
  iconMeta?: number;
  questCount: number;
  order: number;
}

export interface QuestLineNodeDto {
  questId: string;
  name: string;
  x: number;
  y: number;
  sizeX: number;
  sizeY: number;
  state: string;
  mainQuest?: boolean;
  canSubmit?: boolean;
  iconItemId?: string;
  iconMeta?: number;
  ghost?: boolean;
  sourceLineId?: string;
}

export interface QuestLineEdgeDto {
  fromQuestId: string;
  toQuestId: string;
  requirementType: string;
}

export interface QuestLineGraphDto {
  lineId: string;
  name: string;
  nodes: QuestLineNodeDto[];
  edges: QuestLineEdgeDto[];
}

export interface QuestTaskDto {
  index: number;
  taskId: string;
  factoryId: string;
  name: string;
  description?: string;
  webAction: string;
  reasonKey?: string;
  complete: boolean;
  itemId?: string;
  registryName?: string;
  meta?: number;
  required: number;
  progress: number;
  fluidName?: string;
  fluidRequired?: number;
  fluidProgress?: number;
  extraItemCount?: number;
}

export interface QuestRewardDto {
  index: number;
  rewardId: string;
  factoryId: string;
  name: string;
  description?: string;
  itemId?: string;
  registryName?: string;
  meta?: number;
  amount: number;
}

export interface QuestRelationDto {
  questId: string;
  name: string;
  lineId?: string;
  state: string;
  requirementType?: string;
}

export interface QuestDetailDto {
  questId: string;
  name: string;
  description: string;
  state: string;
  canSubmit: boolean;
  canClaim?: boolean;
  hasClaimed?: boolean;
  mainQuest?: boolean;
  silent?: boolean;
  repeatable?: boolean;
  iconItemId?: string;
  iconMeta?: number;
  requirementQuestIds: string[];
  prerequisites?: QuestRelationDto[];
  dependents?: QuestRelationDto[];
  tasks: QuestTaskDto[];
  rewards: QuestRewardDto[];
}

export interface QuestProgressEntryDto {
  questId: string;
  state: string;
  canSubmit: boolean;
}

export interface QuestAnalysisStepDto {
  index: number;
  webAction: string;
  reasonKey?: string;
  complete: boolean;
  webCapable: boolean;
  itemId?: string;
  registryName?: string;
  meta?: number;
  required: number;
  available: number;
  craftable: number;
  missing: number;
  fluidName?: string;
  fluidRequired?: number;
  fluidAvailable?: number;
  fluidMissing?: number;
}

export interface QuestAnalysisDto {
  questId: string;
  networkId: number;
  state: string;
  canSubmit: boolean;
  steps: QuestAnalysisStepDto[];
}

export interface QuestSubmitStepResultDto {
  index: number;
  success: boolean;
  message: string;
  itemId?: string;
  amount?: number;
  fluidName?: string;
  fluidAmount?: number;
}

export interface QuestSubmitResultDto {
  success: boolean;
  dryRun: boolean;
  message: string;
  questId: string;
  newState?: string;
  steps: QuestSubmitStepResultDto[];
}

export interface QuestCraftJobDto {
  jobId: string;
  questId: string;
  phase: string;
  complete: boolean;
  success: boolean;
  message: string;
  ordersTotal: number;
  ordersDone: number;
  submitResult?: QuestSubmitResultDto;
}

export interface QuestChainStepDto {
  questId: string;
  name: string;
  state: string;
  canSubmit: boolean;
  target?: boolean;
  skipped?: boolean;
  skipReason?: string;
  fullySatisfied?: boolean;
  craftable?: boolean;
  missingItemKinds?: number;
  analysis?: QuestAnalysisDto;
}

export interface QuestChainPlanDto {
  targetQuestId: string;
  networkId: number;
  chainEnabled: boolean;
  steps: QuestChainStepDto[];
}

export interface QuestChainStepResultDto {
  questId: string;
  name: string;
  action: string;
  message: string;
  submitResult?: QuestSubmitResultDto;
}

export interface QuestChainSubmitResultDto {
  success: boolean;
  dryRun: boolean;
  message: string;
  targetQuestId: string;
  jobId?: string;
  complete?: boolean;
  phase?: string;
  steps: QuestChainStepResultDto[];
}

export interface ConfigResponse {
  success: boolean;
  config: ServerConfig;
}

export interface AuthLoginResponse {
  status: string;
  message: string;
  playerUuid: string;
}

export interface IconPackInfo {
  packName: string;
  iconCount: number;
  modeCounts?: Record<string, number>;
  availableModes?: string[];
}

export interface IconPacksResponse {
  success: boolean;
  packs: IconPackInfo[];
  defaultPack: string | null;
}

export interface ApiError {
  status: string;
  code: string;
  message: string;
}

/** GET /api/network/topology response envelope. */
export interface TopologyResponse {
  success: boolean;
  hasSnapshot?: boolean;
  cached: boolean;
  persisted?: boolean;
  timestamp?: number;
  cooldownRemainingMs?: number;
  cooldownMs?: number;
  canForceSnapshot?: boolean;
  data?: TopologySnapshotDto;
  message?: string;
  code?: string;
}

export interface TopologySnapshotDto {
  networkId: number;
  mode: 'logical' | 'spatial' | string;
  timestamp: number;
  meta: TopologyMetaDto;
  nodes: TopologyNodeDto[];
  edges: TopologyEdgeDto[];
  aePlacements?: WorldMapAePlacementDto[];
}

export interface WorldMapAePlacementDto {
  x: number;
  y: number;
  z: number;
  dim: number;
  kind?: string;
  className?: string;
  iconItemId?: string;
  displayName?: string;
}

export interface TopologyMetaDto {
  layout?: string;
  hubGroup?: string;
  spatialBinSize?: number;
  showOccupiedChannels?: boolean;
  channelsSimulated?: TopologyChannelInfoDto;
  channelsReal?: TopologyChannelInfoDto;
  renderLayout?: string;
  channelTierHint?: string;
  layoutUnitPx?: number;
}

export interface TopologyChannelInfoDto {
  used: number;
  max: number;
  available: boolean;
}

export interface TopologyNodeDto {
  id: string;
  type: string;
  subtype?: string;
  displayName: string;
  count: number;
  channelCost: number;
  iconItemId?: string;
  role?: string;
  layoutX: number;
  layoutY: number;
  layoutSector?: string;
  branchIndex?: number;
  patternCount?: number;
  simGridX?: number;
  simGridY?: number;
  simKind?: string;
  cellSlots?: TopologyCellSlotDto[];
  patternSlots?: TopologyPatternSlotDto[];
  devices?: TopologyDeviceRecordDto[];
  cpuSummary?: TopologyCpuSummaryDto;
  dim?: number;
  binX?: number;
  binZ?: number;
}

export interface TopologyPatternSlotDto {
  slot: number;
  displayName?: string;
  itemId?: string;
}

export interface TopologyCellSlotDto {
  slot: number;
  empty: boolean;
  displayName?: string;
  itemId?: string;
  itemBytes?: number;
  fluidBytes?: number;
}

export interface TopologyDeviceRecordDto {
  className?: string;
  displayName?: string;
  iconItemId?: string;
  x: number;
  y: number;
  z: number;
  dim: number;
  channelCost?: number;
}

export interface TopologyCpuSummaryDto {
  name?: string;
  coProcessors: number;
  availableStorage: number;
  usedStorage: number;
  busy: boolean;
  unitCount: number;
  storageUnits: number;
  acceleratorUnits: number;
  monitorUnits: number;
}

export interface TopologyEdgeDto {
  from: string;
  to: string;
  cableType?: 'smart' | 'covered' | 'dense' | string;
  branchIndex?: number;
  emptyBranch?: boolean;
  channelsSimulated?: TopologyChannelInfoDto;
  channelsReal?: TopologyChannelInfoDto;
  pathPoints?: { x: number; y: number }[];
}

export interface WorldMapMarkerDto {
  id: string;
  nodeId: string;
  type: string;
  subtype?: string;
  displayName: string;
  iconItemId: string;
  x: number;
  y: number;
  z: number;
  dim: number;
  channelCost: number;
}

export interface WorldMapDimensionDto {
  dim: number;
  name: string;
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
  minChunkX?: number;
  maxChunkX?: number;
  minChunkZ?: number;
  maxChunkZ?: number;
  /** Compact "cx,cz" list when chunk count <= 256; omitted when only bbox applies. */
  allowedChunks?: string[] | null;
  markerCount: number;
  chunkCount: number;
}

export interface WorldMapViewDto {
  id: string;
  labelKey: string;
}

export interface WorldMapMetaDto {
  success: boolean;
  hasLogicalSnapshot: boolean;
  timestamp?: number;
  dimensions: WorldMapDimensionDto[];
  tilePx?: number;
  pxPerBlock?: number;
  paddingChunks?: number;
  maxChunks?: number;
  boundsTooLarge?: boolean;
  markerCount?: number;
  worldMapEnabled?: boolean;
  cooldownRemainingMs?: number;
  cooldownMs?: number;
  message?: string;
  views?: WorldMapViewDto[];
  obliqueDirections?: WorldMapViewDto[];
  hdAvailable?: boolean;
  qualityTiers?: WorldMapQualityTierDto[];
  maxQualityTier?: string;
  defaultQualityTier?: string;
  flatRenderEngine?: string;
  obliqueRenderEngine?: string;
  zoomLevels?: WorldMapZoomLevelDto[];
  recommendedZoom?: number;
  blockPatchesEnabled?: boolean;
  aeQualityBoost?: boolean;
  aeOverlayQualityTier?: string;
  serverAtlasEnabled?: boolean;
  blockPatchEntries?: number;
  serverAtlasSlots?: number;
  /** Terrain source in use: "dynmap" or "self". */
  terrainSource?: string;
  /** Whether a Dynmap (or GWM/GTNH-Web-Map) is available on this server. */
  dynmapAvailable?: boolean;
  /** Dynmap world name when terrainSource=dynmap (e.g. "world"). */
  dynmapWorldName?: string;
  /** URL template for Dynmap tiles served through WebAE auth proxy. */
  dynmapTileUrlTemplate?: string;
  /** Highest native Dynmap/GWM zoom (single-resolution tile fetch). */
  dynmapMaxZoom?: number;
  /** Client GL capture mode: off | ultra_only | when_online. */
  clientCaptureMode?: string;
  /** Progressive lower-tier / Dynmap crop fallback while target tier renders. */
  progressiveFallback?: boolean;
  /** Snapshot mode: client_only or legacy. */
  snapshotMode?: string;
  /** Current snapshot version (0 = none). */
  snapshotVersion?: number;
  /** Previous snapshot version for tile fallback during refresh (0 = none). */
  previousSnapshotVersion?: number;
  /** Snapshot capture source: journeymap, client_gl, mixed, etc. */
  snapshotSource?: string;
  journeyMapPreferred?: boolean;
  /** Configured snapshot terrain capture priority (read-only). */
  snapshotSourcePriority?: string[];
  /** Last snapshot per-source chunk counts. */
  snapshotSourceStats?: Record<string, number>;
  /** Integrated SP direct tile serve enabled. */
  spDirectServe?: boolean;
}

export interface WorldMapSnapshotStatusDto {
  success?: boolean;
  networkId?: number;
  currentVersion?: number;
  timestamp?: number;
  source?: string;
  tilePx?: number;
  captureState?: string;
  requestId?: string;
  acceptPlayerName?: string;
  totalChunks?: number;
  completedChunks?: number;
  missingChunks?: number;
  expiresAtMs?: number;
  message?: string;
}

export interface WorldMapZoomLevelDto {
  level: number;
  chunkSpan: number;
  tilePx: number;
  pxPerBlock: number;
}

export interface WorldMapQualityTierDto {
  id: string;
  labelKey: string;
  tilePx: number;
  pxPerBlock: number;
  hdCapable?: boolean;
}

export interface WorldMapProgressDto {
  success: boolean;
  networkId?: number;
  quality?: string;
  view?: string;
  dim?: number;
  total?: number;
  completed?: number;
  chunks?: Record<string, { terrain?: string; ae?: string }>;
}

export interface WorldMapMarkersResponse {
  success: boolean;
  hasLogicalSnapshot?: boolean;
  markers?: WorldMapMarkerDto[];
  message?: string;
  code?: string;
}

// Phase 3 integration DTOs
export interface LinkScannerBlockDto {
  dimension: number;
  x: number;
  y: number;
  z: number;
  blockTypeId: string;
  blockTypeLabelKey?: string;
  owner: string;
  alias?: string;
  locationKey: string;
}

export interface LinkScannerResponse {
  success: boolean;
  count: number;
  blocks: LinkScannerBlockDto[];
}

export interface MonitorDataBindingDto {
  slotIndex: number;
  dataType: string;
  displayName: string;
  bindDim: number;
  bindX: number;
  bindY: number;
  bindZ: number;
  enabled: boolean;
  networkWide?: boolean;
}

export interface MonitorGtBindingDto {
  dim: number;
  x: number;
  y: number;
  z: number;
}

export interface MonitorBindingDto {
  monitorDim: number;
  monitorX: number;
  monitorY: number;
  monitorZ: number;
  owner: string;
  dataBindings: MonitorDataBindingDto[];
  gtBindings: MonitorGtBindingDto[];
}

export interface MonitorBindingsResponse {
  success: boolean;
  count: number;
  monitors: MonitorBindingDto[];
}

export interface PlanEntryDto {
  id: number;
  owner: string;
  rawText: string;
  title: string;
  status: string;
  createdAt: number;
  dueAt: number;
  completedAt?: number;
  completed: boolean;
  reminded?: boolean;
}

export interface PlannerPlansResponse {
  success: boolean;
  count: number;
  plans: PlanEntryDto[];
}

export interface WebFavoritesDto {
  recipes: string[];
  patterns: string[];
  items: string[];
}

export interface PlannerExportFlowRequest {
  networkId?: number;
  format?: 'gtnh-flow-v1' | 'factory-flow-v1';
  roots: { itemId: string; amount: number }[];
}

export interface PlannerExportFlowResponse {
  success: boolean;
  format?: string;
  export?: Record<string, unknown>;
  message?: string;
}

export interface WebAssistantResponse {
  success: boolean;
  message: string;
  code?: string;
  intentType?: string;
  intentTarget?: string;
  cooldownMs?: number;
}

export interface NetworkCellSummaryDto {
  networkId: number;
  timestamp: number;
  hasInfiniteItemCells: boolean;
  hasInfiniteFluidCells: boolean;
  itemUsedBytes: number;
  itemTotalBytes: number;
  fluidUsedBytes: number;
  fluidTotalBytes: number;
  itemUsagePercent: number;
  fluidUsagePercent: number;
}

export interface NetworkCellSummaryResponse {
  success: boolean;
  data: NetworkCellSummaryDto;
}

export interface WebAlertDto {
  id: string;
  type: string;
  severity: string;
  title: string;
  message: string;
  timestamp: number;
  networkId: number;
  sourceKey?: string;
}

export interface WebAlertsConfigDto {
  version: number;
  enabled: boolean;
  pollIntervalSeconds: number;
  cpuStuckMinutes: number;
  gtErrorEnabled: boolean;
  orderCompleteEnabled: boolean;
  channelThresholdPercent: number;
  channelThresholdAbsolute: number;
  serverTpsBelowEnabled?: boolean;
  serverTpsThreshold?: number;
  serverTpsDurationSeconds?: number;
  webhooks?: WebhookRuleDto[];
  inventoryThresholds?: Array<{
    itemId?: string;
    fluidName?: string;
    minAmount: number;
    networkId: number;
    label?: string;
  }>;
  automationRules?: AutomationRuleDto[];
}

export interface AutomationRuleDto {
  id: string;
  enabled: boolean;
  type: string;
  itemId: string;
  threshold: number;
  craftAmount?: number;
  patternId?: string;
  cpuName?: string;
  networkId: number;
  cooldownSeconds: number;
  requireCpuIdle?: boolean;
  maxTriggersPerHour?: number;
}

export interface WebhookRuleDto {
  id: string;
  url?: string;
  urlConfigured?: boolean;
  enabled: boolean;
  events?: string[];
  mention?: string;
}

export interface ServerHealthResponse {
  success: boolean;
  tps: number;
  mspt: number;
  onlinePlayers: number;
  uptimeSeconds: number;
  history?: {
    tps: number[];
    mspt: number[];
    timestamps: number[];
  };
}

export interface PerfPhaseView {
  lastMs: number;
  avgMs: number;
  maxMs: number;
  count: number;
}

export interface PerfRouteView {
  route: string;
  count: number;
  totalMs: number;
  maxMs: number;
  avgMs: number;
}

export interface PerfSlowHttpEntry {
  ts: number;
  route: string;
  durationMs: number;
}

export interface ServerDiagnosticsResponse {
  success: boolean;
  tps: number;
  mspt: number;
  onlinePlayers: number;
  uptimeSeconds: number;
  queueDepth: number;
  tasksProcessedThisTick: number;
  activeNetworks: number;
  snapshotCacheSize: number;
  phases?: Record<string, PerfPhaseView>;
  collects?: Record<string, PerfPhaseView>;
  topRoutes?: PerfRouteView[];
  slowHttp?: PerfSlowHttpEntry[];
  history?: {
    timestamps: number[];
    queueDepth: number[];
    serverTasksMs: number[];
    snapshotSchedulerMs: number[];
  };
  config?: {
    refreshIntervalMs: number;
    gtRefreshIntervalMs: number;
    metricSampleIntervalMs: number;
    patternCacheTtlMs: number;
    topologyCacheTtlMs: number;
    worldMapTileBudgetPerTick: number;
    iconRenderPerTick: number;
    perfDebugEnabled: boolean;
  };
}

export interface AlertsResponse {
  success: boolean;
  count: number;
  canEditRules?: boolean;
  alerts: WebAlertDto[];
  rules: WebAlertsConfigDto;
}

export interface AlertHistoryEntryDto {
  id: string;
  type: string;
  severity: string;
  title: string;
  message: string;
  firstSeenAt: number;
  lastSeenAt: number;
  clearedAt: number;
  networkId: number;
  sourceKey?: string;
  active: boolean;
}

export interface AlertHistoryResponse {
  success: boolean;
  total: number;
  offset: number;
  limit: number;
  history: AlertHistoryEntryDto[];
}

export type GlobalSearchResultType = 'storage' | 'recipe' | 'gt' | 'pattern' | 'quest';

export interface GlobalSearchResultDto {
  type: GlobalSearchResultType;
  id: string;
  label: string;
  subtitle?: string;
  networkId?: number;
  category?: 'item' | 'fluid' | 'essentia';
  itemId?: string;
  registryName?: string;
  meta?: number;
  amount?: number;
  handlerId?: string;
  recipeIndex?: number;
  x?: number;
  y?: number;
  z?: number;
  dim?: number;
  patternId?: string;
  gridKey?: string;
  source?: string;
}

export interface GlobalSearchResponse {
  success: boolean;
  query?: string;
  offset?: number;
  limit?: number;
  total?: number;
  results?: GlobalSearchResultDto[];
  countsByType?: Record<string, number>;
  code?: string;
  cooldownMs?: number;
  message?: string;
}

/** GET /api/network/balance suggestion row (Phase 8). */
export interface NetworkBalanceSuggestionDto {
  resourceType: 'item' | 'fluid' | 'essentia' | string;
  itemId?: string;
  displayName: string;
  needyNetworkId: number;
  needyAmount: number;
  sourceNetworkId: number;
  sourceAmount: number;
  transferable: number;
}

export interface NetworkBalanceResponse {
  success: boolean;
  count?: number;
  timestamp?: number;
  suggestions?: NetworkBalanceSuggestionDto[];
  message?: string;
}

/** GET /api/craft/tree node (Phase 6 / 4.1). */
export interface CraftTreeNodeDto {
  itemId: string;
  registryName: string;
  displayName: string;
  meta: number;
  required: number;
  available: number;
  inStock?: number;
  missing: number;
  toCraft?: number;
  patternId?: string;
  leaf: boolean;
  recipeHandlerId?: string;
  recipeIndex?: number;
  children?: CraftTreeNodeDto[];
}

export interface CraftTreeResponse {
  success: boolean;
  networkId?: number;
  amount?: number;
  tree?: CraftTreeNodeDto;
  message?: string;
}

/** GET /api/network/p2p (Phase 10). */
export interface P2pTunnelDto {
  frequency: number;
  frequencyHex: string;
  type: string;
  displayName: string;
  dim: number;
  x: number;
  y: number;
  z: number;
  inputSide: boolean;
}

export interface P2pFrequencyGroupDto {
  frequency: number;
  frequencyHex: string;
  type: string;
  endpointCount: number;
  endpoints: P2pTunnelDto[];
}

export interface P2pPowerChannelDto {
  frequency: number;
  frequencyHex: string;
  avgEuPerTick: number;
  endpointCount: number;
}

export interface P2pMapSnapshotDto {
  networkId: number;
  timestamp: number;
  tunnelCount: number;
  frequencyCount: number;
  groups: P2pFrequencyGroupDto[];
  powerChannels?: P2pPowerChannelDto[];
}

export interface P2pMapResponse {
  success: boolean;
  data?: P2pMapSnapshotDto;
  message?: string;
}

/** GET /api/monitor/preview (Phase 11). */
export interface MonitorPreviewDto {
  monitorDim: number;
  monitorX: number;
  monitorY: number;
  monitorZ: number;
  slotIndex: number;
  dataType: string;
  displayName: string;
  enabled: boolean;
  values: number[];
  yMin: number;
  yMax: number;
  dataLimit: number;
  timestamp: number;
}

export interface MonitorPreviewResponse {
  success: boolean;
  preview?: MonitorPreviewDto;
  message?: string;
}
