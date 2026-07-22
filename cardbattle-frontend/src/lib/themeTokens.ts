/** Pixel-flat GTNH design tokens — UI chrome is shared; card frames differ by theme. */

export const THEME_IDS = [
  'vanilla',
  'gt',
  'thaum',
  'forestry',
  'astral',
  'avaritia',
  'ee',
  'genetics',
  'ae',
  'dlb',
] as const;

export type ThemeId = (typeof THEME_IDS)[number];

export const THEME_ZH: Record<ThemeId, string> = {
  vanilla: '原版',
  gt: '格雷',
  thaum: '神秘',
  forestry: '林业',
  astral: '星辉',
  avaritia: '无尽',
  ee: '等价交换',
  genetics: '基因突变',
  ae: 'AE',
  dlb: 'DLB',
};

export interface ThemePalette {
  primary: string;
  secondary: string;
  glow: string;
  frame: string;
}

export const THEME_PALETTE: Record<ThemeId, ThemePalette> = {
  vanilla: { primary: '#6b8f71', secondary: '#3d5c44', glow: '#8fcf9a', frame: '#5a7a60' },
  gt: { primary: '#4a90c8', secondary: '#2a5080', glow: '#7ec8ff', frame: '#c9a227' },
  thaum: { primary: '#9b59b6', secondary: '#5e2d7a', glow: '#d4a0ff', frame: '#6b3d8a' },
  forestry: { primary: '#d4a017', secondary: '#8a6a10', glow: '#f0d060', frame: '#a07820' },
  astral: { primary: '#7ec8e3', secondary: '#3a7088', glow: '#b0e8ff', frame: '#5a98b0' },
  avaritia: { primary: '#c0c0c0', secondary: '#6a6a6a', glow: '#ffffff', frame: '#8a8a8a' },
  ee: { primary: '#e67e22', secondary: '#8a4a10', glow: '#ffb060', frame: '#c06020' },
  genetics: { primary: '#2ecc71', secondary: '#1a7a40', glow: '#6effa0', frame: '#28a858' },
  ae: { primary: '#1abc9c', secondary: '#0a6a58', glow: '#5ff0d0', frame: '#18a088' },
  dlb: { primary: '#e74c3c', secondary: '#8a2018', glow: '#ff8070', frame: '#c03028' },
};

export const UI = {
  bg0: '#0a0e12',
  bg1: '#121820',
  panel: '#161e28',
  panelEdge: '#2a3848',
  text: '#e8eef4',
  muted: '#7a8a9a',
  accent: '#5a9fd4',
  danger: '#e07060',
  gold: '#d4b45a',
  success: '#5bcf8a',
  pixelBorder: '2px solid',
  shadow: '4px 4px 0 rgba(0,0,0,0.45)',
  fontPixel: '"Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
} as const;

export function themeOf(id: string | undefined): ThemePalette {
  if (id && id in THEME_PALETTE) return THEME_PALETTE[id as ThemeId];
  return THEME_PALETTE.vanilla;
}

export const PHASE_ZH: Record<string, string> = {
  mulligan: '调度',
  main: '交替行动',
  spell_response: '法术响应',
  attack_declare: '攻击声明',
  block_declare: '格挡声明',
  combat_response: '战斗响应',
  swap_extra: '神秘换位',
  resolve: '结算',
  turn_end: '轮次结束',
  game_over: '对局结束',
};
