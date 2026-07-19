import type { EffectsLevel, ThemeColor } from './colors';
import type { ThemeLayout } from './layouts';
import type { PageStyle } from './pageStyles';

export type DesignPackCategory =
  | 'terra'
  | 'cyber'
  | 'aerospace'
  | 'printstream'
  | 'gregtech'
  | 'game'
  | 'anime'
  | 'cinema';

export type DesignPackFilterCategory = DesignPackCategory | 'all' | 'featured' | 'favorites';

export interface DesignPack {
  id: string;
  category: DesignPackCategory;
  nameKey: string;
  descriptionKey: string;
  /** Short non-semantic mark rendered as decorative art. */
  mark: string;
  serial: string;
  searchTerms: string;
  themeColor: ThemeColor;
  themeLayout: ThemeLayout;
  pageStyle: PageStyle;
  effectsLevel: EffectsLevel;
  featured?: boolean;
  /** Lower values appear first in the top-tier showcase. */
  featuredRank?: number;
  /** Optional translated attribution / visual-reference note. */
  referenceKey?: string;
  /** Decorative CSS hook for an authored preview-card motif. */
  motif?: string;
}

export const DESIGN_PACK_CATEGORIES: DesignPackCategory[] = [
  'terra',
  'cyber',
  'aerospace',
  'printstream',
  'gregtech',
  'game',
  'anime',
  'cinema',
];

/**
 * Curated, authored combinations. These intentionally do not alter language,
 * dashboards, icon packs, or data settings; a design pack is visual-only.
 */
export const DESIGN_PACKS: DesignPack[] = [
  {
    id: 'hextech-piltover-forge',
    category: 'game',
    nameKey: 'designPack_hextechForge_name',
    descriptionKey: 'designPack_hextechForge_desc',
    referenceKey: 'designPack_hextechForge_ref',
    mark: 'HX',
    serial: 'PILT//HEX-06',
    searchTerms: 'league legends arcane piltover hextech blue gold crystal 海克斯科技 英雄联盟 双城之战 皮尔特沃夫',
    themeColor: 'hextech-blue',
    themeLayout: 'command',
    pageStyle: 'lol-rift',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 1,
    motif: 'hextech',
  },
  {
    id: 'koprulu-terran-bridge',
    category: 'game',
    nameKey: 'designPack_terranBridge_name',
    descriptionKey: 'designPack_terranBridge_desc',
    referenceKey: 'designPack_terranBridge_ref',
    mark: 'TERRAN',
    serial: 'KOPRULU//T-01',
    searchTerms: 'starcraft terran dominion battlecruiser military bridge 星际争霸 人族 帝国 战列巡航舰 舰桥',
    themeColor: 'doom-steel',
    themeLayout: 'engine-room',
    pageStyle: 'doomhud',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 2,
    motif: 'terran',
  },
  {
    id: 'aiur-protoss-nexus',
    category: 'game',
    nameKey: 'designPack_protossNexus_name',
    descriptionKey: 'designPack_protossNexus_desc',
    referenceKey: 'designPack_protossNexus_ref',
    mark: 'KHALA',
    serial: 'AIUR//NEXUS-7',
    searchTerms: 'starcraft protoss aiur khala nexus crystal gold 星际争霸 神族 艾尔 卡拉 枢纽 水晶',
    themeColor: 'eorzea-gold',
    themeLayout: 'orbital-console',
    pageStyle: 'destiny-light',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 3,
    motif: 'protoss',
  },
  {
    id: 'bridges-chiral-network',
    category: 'cinema',
    nameKey: 'designPack_bridgesNetwork_name',
    descriptionKey: 'designPack_bridgesNetwork_desc',
    referenceKey: 'designPack_bridgesNetwork_ref',
    mark: 'BRIDGES',
    serial: 'UCA//Q-PID',
    searchTerms: 'death stranding bridges chiral network uca cargo strand 死亡搁浅 布里吉斯 手性网络 快递 连接',
    themeColor: 'bridges-white',
    themeLayout: 'pipeline',
    pageStyle: 'uber-dispatch',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 4,
    motif: 'bridges',
  },
  {
    id: 'nerv-magi-command',
    category: 'anime',
    nameKey: 'designPack_nervMagi_name',
    descriptionKey: 'designPack_nervMagi_desc',
    referenceKey: 'designPack_nervMagi_ref',
    mark: 'NERV',
    serial: 'MAGI//CASPER-3',
    searchTerms: 'evangelion nerv magi casper terminal alert eva 新世纪福音战士 司令部 三贤人 红色警报',
    themeColor: 'nerv-purple',
    themeLayout: 'pipeline',
    pageStyle: 'evangelion-nerv',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 5,
    motif: 'nerv',
  },
  {
    id: 'section9-cyberbrain-grid',
    category: 'anime',
    nameKey: 'designPack_section9_name',
    descriptionKey: 'designPack_section9_desc',
    referenceKey: 'designPack_section9_ref',
    mark: '9',
    serial: 'CYBERBRAIN//SAC',
    searchTerms: 'ghost shell section 9 cyberbrain tachikoma 公安九课 攻壳机动队 义体 网络 塔奇克马',
    themeColor: 'shell-teal',
    themeLayout: 'right-drawer',
    pageStyle: 'ghost-shell',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 6,
    motif: 'section9',
  },
  {
    id: 'yorha-bunker-command',
    category: 'game',
    nameKey: 'designPack_yorhaBunker_name',
    descriptionKey: 'designPack_yorhaBunker_desc',
    referenceKey: 'designPack_yorhaBunker_ref',
    mark: 'YoRHa',
    serial: 'BUNKER//2B-09S',
    searchTerms: 'nier automata yorha bunker android monochrome 尼尔 自动人形 寄叶 地堡 黑金 人造人',
    themeColor: 'yorha-black',
    themeLayout: 'theater',
    pageStyle: 'yorha',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 7,
    motif: 'yorha',
  },
  {
    id: 'tron-grid-control',
    category: 'cinema',
    nameKey: 'designPack_tronGrid_name',
    descriptionKey: 'designPack_tronGrid_desc',
    referenceKey: 'designPack_tronGrid_ref',
    mark: 'GRID',
    serial: 'CLU//1982-10',
    searchTerms: 'tron legacy grid light cycle clu neon cinema 创战纪 光网格 光轮 电影 科技',
    themeColor: 'reactor-cyan',
    themeLayout: 'quantum-frame',
    pageStyle: 'arc-reactor',
    effectsLevel: 'full',
    featured: true,
    featuredRank: 8,
    motif: 'tron',
  },
  {
    id: 'terra-command-core',
    category: 'terra',
    nameKey: 'designPack_terraCommand_name',
    descriptionKey: 'designPack_terraCommand_desc',
    mark: 'R.I.',
    serial: 'OPS//03-7A',
    searchTerms: 'rhodes arknights tactical amber 罗德岛 明日方舟 战术',
    themeColor: 'terra-amber',
    themeLayout: 'tactical-grid',
    pageStyle: 'terra-command',
    effectsLevel: 'full',
    featured: true,
  },
  {
    id: 'terra-contingency-red',
    category: 'terra',
    nameKey: 'designPack_terraContract_name',
    descriptionKey: 'designPack_terraContract_desc',
    mark: 'C.C.',
    serial: 'RISK//18+',
    searchTerms: 'contingency contract red risk tactical 危机合约 红色',
    themeColor: 'terra-danger',
    themeLayout: 'engine-room',
    pageStyle: 'terra-contract',
    effectsLevel: 'full',
  },
  {
    id: 'terra-originium-field',
    category: 'terra',
    nameKey: 'designPack_terraOriginium_name',
    descriptionKey: 'designPack_terraOriginium_desc',
    mark: 'ORIG',
    serial: 'FIELD//P-09',
    searchTerms: 'originium field rhodes crystal 源石 晶体 罗德岛',
    themeColor: 'rhodes-ink',
    themeLayout: 'mission-control',
    pageStyle: 'terra-originium',
    effectsLevel: 'full',
  },
  {
    id: 'cyber-neon-grid',
    category: 'cyber',
    nameKey: 'designPack_cyberGrid_name',
    descriptionKey: 'designPack_cyberGrid_desc',
    mark: 'NC//',
    serial: 'SECTOR 2077',
    searchTerms: 'cyberpunk neon lime city grid 赛博朋克 夜城 霓虹',
    themeColor: 'cyber-lime',
    themeLayout: 'mission-control',
    pageStyle: 'cyber-grid',
    effectsLevel: 'full',
    featured: true,
  },
  {
    id: 'cyber-redline-chrome',
    category: 'cyber',
    nameKey: 'designPack_cyberChrome_name',
    descriptionKey: 'designPack_cyberChrome_desc',
    mark: 'RED',
    serial: 'TRACE//E9',
    searchTerms: 'cyber chrome red glass implant 赛博 红线 镀铬 义体',
    themeColor: 'cyber-redline',
    themeLayout: 'right-drawer',
    pageStyle: 'cyber-chrome',
    effectsLevel: 'full',
  },
  {
    id: 'cyber-ghost-hologram',
    category: 'cyber',
    nameKey: 'designPack_cyberGhost_name',
    descriptionKey: 'designPack_cyberGhost_desc',
    mark: 'GHOST',
    serial: 'SHELL//09',
    searchTerms: 'hologram ghost shell cyan cyber 赛博 全息 幽灵',
    themeColor: 'hologram',
    themeLayout: 'quantum-frame',
    pageStyle: 'cyber-grid',
    effectsLevel: 'full',
  },
  {
    id: 'ueg-earth-engine',
    category: 'aerospace',
    nameKey: 'designPack_earthEngine_name',
    descriptionKey: 'designPack_earthEngine_desc',
    mark: 'UEG',
    serial: 'ENGINE//550W',
    searchTerms: 'wandering earth engine ueg hard surface 流浪地球 行星发动机 硬表面',
    themeColor: 'ueg-orange',
    themeLayout: 'engine-room',
    pageStyle: 'earth-engine',
    effectsLevel: 'full',
    featured: true,
  },
  {
    id: 'lunar-orbit-control',
    category: 'aerospace',
    nameKey: 'designPack_lunarOrbit_name',
    descriptionKey: 'designPack_lunarOrbit_desc',
    mark: 'L4',
    serial: 'ORBIT//118',
    searchTerms: 'moon lunar orbit aerospace ice 月球 轨道 航天',
    themeColor: 'lunar-ice',
    themeLayout: 'orbital-console',
    pageStyle: 'lunar-orbit',
    effectsLevel: 'full',
  },
  {
    id: 'space-deep-frame',
    category: 'aerospace',
    nameKey: 'designPack_deepSpace_name',
    descriptionKey: 'designPack_deepSpace_desc',
    mark: 'DSN',
    serial: 'LAGRANGE//02',
    searchTerms: 'deep space lagrange frame mission control 深空 拉格朗日 指挥',
    themeColor: 'lunar-ice',
    themeLayout: 'quantum-frame',
    pageStyle: 'earth-engine',
    effectsLevel: 'full',
  },
  {
    id: 'printstream-prime',
    category: 'printstream',
    nameKey: 'designPack_printstreamPrime_name',
    descriptionKey: 'designPack_printstreamPrime_desc',
    mark: 'PS',
    serial: '01//PEARL',
    searchTerms: 'csgo printstream pearl black white 印花集 珠光 黑白',
    themeColor: 'printstream',
    themeLayout: 'floating',
    pageStyle: 'printstream-panel',
    effectsLevel: 'full',
    featured: true,
  },
  {
    id: 'printstream-spectrum-pro',
    category: 'printstream',
    nameKey: 'designPack_printstreamSpectrum_name',
    descriptionKey: 'designPack_printstreamSpectrum_desc',
    mark: 'PS+',
    serial: 'RGB//PHASE',
    searchTerms: 'printstream spectrum pantone rainbow 光谱 虹彩 印花集',
    themeColor: 'printstream-spectrum',
    themeLayout: 'island',
    pageStyle: 'printstream-spectrum',
    effectsLevel: 'full',
  },
  {
    id: 'printstream-ascii-code',
    category: 'printstream',
    nameKey: 'designPack_printstreamAscii_name',
    descriptionKey: 'designPack_printstreamAscii_desc',
    mark: ':::',
    serial: 'ASCII//00',
    searchTerms: 'printstream ascii mono code terminal 印花集 字符 终端',
    themeColor: 'printstream-ascii',
    themeLayout: 'command',
    pageStyle: 'printstream-ascii',
    effectsLevel: 'full',
  },
  {
    id: 'gtnh-stargate-command',
    category: 'gregtech',
    nameKey: 'designPack_gtnhStargate_name',
    descriptionKey: 'designPack_gtnhStargate_desc',
    mark: 'GTNH',
    serial: 'STARGATE//T9',
    searchTerms: 'gtnh stargate endgame ae textech 星门 终局',
    themeColor: 'gtnh-stargate',
    themeLayout: 'orbital-console',
    pageStyle: 'gtnh-cosmos',
    effectsLevel: 'full',
    featured: true,
  },
  {
    id: 'gtnh-cosmic-network',
    category: 'gregtech',
    nameKey: 'designPack_gtnhCosmos_name',
    descriptionKey: 'designPack_gtnhCosmos_desc',
    mark: 'NH',
    serial: 'COSMOS//UHV',
    searchTerms: 'gtnh cosmos network ae2 uhv cosmic 宇宙 网络 高压',
    themeColor: 'gtnh-blue',
    themeLayout: 'mission-control',
    pageStyle: 'gtnh-cosmos',
    effectsLevel: 'full',
  },
  {
    id: 'gregtech-assembly-line',
    category: 'gregtech',
    nameKey: 'designPack_gtAssembly_name',
    descriptionKey: 'designPack_gtAssembly_desc',
    mark: 'GT',
    serial: 'ASSY//MK-IV',
    searchTerms: 'gregtech assembly line steel industrial 格雷科技 装配线 钢',
    themeColor: 'gregtech-steel',
    themeLayout: 'assembly-line',
    pageStyle: 'gt-assembly',
    effectsLevel: 'full',
  },
  {
    id: 'gregtech-bronze-age',
    category: 'gregtech',
    nameKey: 'designPack_gtBronze_name',
    descriptionKey: 'designPack_gtBronze_desc',
    mark: 'LV',
    serial: 'BRONZE//01',
    searchTerms: 'gregtech bronze steam early game 格雷科技 青铜 蒸汽 前期',
    themeColor: 'gregtech-bronze',
    themeLayout: 'assembly-line',
    pageStyle: 'gt-assembly',
    effectsLevel: 'subtle',
  },
  {
    id: 'gregtech-cleanroom',
    category: 'gregtech',
    nameKey: 'designPack_gtCleanroom_name',
    descriptionKey: 'designPack_gtCleanroom_desc',
    mark: 'CR',
    serial: 'ISO//05',
    searchTerms: 'gregtech cleanroom precision sterile 格雷科技 洁净室 精密',
    themeColor: 'gt-cleanroom',
    themeLayout: 'tactical-grid',
    pageStyle: 'gt-cleanroom',
    effectsLevel: 'subtle',
  },
  {
    id: 'gregtech-fusion-core',
    category: 'gregtech',
    nameKey: 'designPack_gtFusion_name',
    descriptionKey: 'designPack_gtFusion_desc',
    mark: 'MK3',
    serial: 'FUSION//PLASMA',
    searchTerms: 'gregtech fusion plasma reactor 格雷科技 聚变 等离子',
    themeColor: 'gt-fusion',
    themeLayout: 'orbital-console',
    pageStyle: 'gt-fusion',
    effectsLevel: 'full',
  },
  {
    id: 'textech-quantum-matrix',
    category: 'gregtech',
    nameKey: 'designPack_textechQuantum_name',
    descriptionKey: 'designPack_textechQuantum_desc',
    mark: 'TT',
    serial: 'QNTM//64Q',
    searchTerms: 'textech quantum data monitor matrix 量子 数据 矩阵',
    themeColor: 'textech-quantum',
    themeLayout: 'quantum-frame',
    pageStyle: 'textech-quantum',
    effectsLevel: 'full',
  },
  {
    id: 'gtnh-industrial-command',
    category: 'gregtech',
    nameKey: 'designPack_gtnhIndustrial_name',
    descriptionKey: 'designPack_gtnhIndustrial_desc',
    mark: 'OPS',
    serial: 'FACTORY//24H',
    searchTerms: 'gtnh factory industrial command dense ops 工厂 工业 指挥台',
    themeColor: 'gregtech-steel',
    themeLayout: 'engine-room',
    pageStyle: 'earth-engine',
    effectsLevel: 'full',
  },
];

export function filterDesignPacks(
  packs: DesignPack[],
  query: string,
  category: DesignPackFilterCategory,
  favorites: ReadonlySet<string> = new Set()
): DesignPack[] {
  const q = query.trim().toLowerCase();
  return packs.filter((pack) => {
    if (category === 'favorites' && !favorites.has(pack.id)) return false;
    if (category === 'featured' && !pack.featured) return false;
    if (
      category !== 'all' &&
      category !== 'featured' &&
      category !== 'favorites' &&
      pack.category !== category
    ) return false;
    if (!q) return true;
    const haystack = [
      pack.id,
      pack.nameKey,
      pack.descriptionKey,
      pack.mark,
      pack.serial,
      pack.searchTerms,
      pack.themeColor,
      pack.themeLayout,
      pack.pageStyle,
    ]
      .join(' ')
      .toLowerCase();
    return haystack.includes(q);
  }).sort((a, b) => {
    const rankA = a.featuredRank ?? (a.featured ? 100 : 1000);
    const rankB = b.featuredRank ?? (b.featured ? 100 : 1000);
    return rankA - rankB;
  });
}

export function isDesignPackActive(
  pack: DesignPack,
  current: Pick<DesignPack, 'themeColor' | 'themeLayout' | 'pageStyle' | 'effectsLevel'>
): boolean {
  return (
    pack.themeColor === current.themeColor &&
    pack.themeLayout === current.themeLayout &&
    pack.pageStyle === current.pageStyle &&
    pack.effectsLevel === current.effectsLevel
  );
}
