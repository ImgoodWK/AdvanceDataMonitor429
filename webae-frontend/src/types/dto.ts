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
  /** 可选：指定 AE2 合成 CPU 名称。 */
  cpuName?: string;
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
  alertsEnabled?: boolean;
  alertsPollIntervalSeconds?: number;
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
  timestamp?: number;
  cooldownRemainingMs?: number;
  cooldownMs?: number;
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
}

export interface TopologyMetaDto {
  layout?: string;
  hubGroup?: string;
  spatialBinSize?: number;
  showOccupiedChannels?: boolean;
  channelsSimulated?: TopologyChannelInfoDto;
  channelsReal?: TopologyChannelInfoDto;
}

export interface TopologyChannelInfoDto {
  used: number;
  max: number;
  available: boolean;
}

export interface TopologyNodeDto {
  id: string;
  type: string;
  displayName: string;
  count: number;
  channelCost: number;
  iconItemId?: string;
  role?: string;
  layoutX: number;
  layoutY: number;
  devices?: TopologyDeviceRecordDto[];
  dim?: number;
  binX?: number;
  binZ?: number;
}

export interface TopologyDeviceRecordDto {
  className?: string;
  displayName?: string;
  x: number;
  y: number;
  z: number;
  dim: number;
  channelCost?: number;
}

export interface TopologyEdgeDto {
  from: string;
  to: string;
  cableType?: 'smart' | 'covered' | 'dense' | string;
  channelsSimulated?: TopologyChannelInfoDto;
  channelsReal?: TopologyChannelInfoDto;
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
  inventoryThresholds?: Array<{
    itemId?: string;
    fluidName?: string;
    minAmount: number;
    networkId: number;
    label?: string;
  }>;
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

export type GlobalSearchResultType = 'storage' | 'recipe' | 'gt' | 'pattern';

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

/** GET /api/craft/tree node (Phase 6). */
export interface CraftTreeNodeDto {
  itemId: string;
  registryName: string;
  displayName: string;
  meta: number;
  required: number;
  available: number;
  missing: number;
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

export interface P2pMapSnapshotDto {
  networkId: number;
  timestamp: number;
  tunnelCount: number;
  frequencyCount: number;
  groups: P2pFrequencyGroupDto[];
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
