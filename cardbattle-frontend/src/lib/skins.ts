/** LoR-style board skins — UI chrome shared; board backdrop unlockable. */

export interface BoardSkin {
  id: string;
  nameZh: string;
  frameTint: string;
  boardBg: string;
  boardBgAlt: string;
  unlockHint: string;
  /** victories required; 0 = default unlocked */
  unlockVictories: number;
}

export const BOARD_SKINS: BoardSkin[] = [
  {
    id: 'gt_factory',
    nameZh: '格雷工厂',
    frameTint: '#4a90c8',
    boardBg: 'linear-gradient(180deg, #0c141c 0%, #1a2838 40%, #0e1820 100%)',
    boardBgAlt:
      'repeating-linear-gradient(90deg, transparent, transparent 23px, rgba(74,144,200,0.06) 24px), repeating-linear-gradient(0deg, transparent, transparent 23px, rgba(74,144,200,0.04) 24px)',
    unlockHint: '默认皮肤',
    unlockVictories: 0,
  },
  {
    id: 'thaum_workshop',
    nameZh: '神秘工坊',
    frameTint: '#9b59b6',
    boardBg: 'linear-gradient(180deg, #120818 0%, #2a1838 45%, #100814 100%)',
    boardBgAlt:
      'radial-gradient(ellipse at 30% 20%, rgba(155,89,182,0.18), transparent 50%), radial-gradient(ellipse at 70% 80%, rgba(100,40,140,0.12), transparent 45%)',
    unlockHint: '累计胜利 2 场解锁',
    unlockVictories: 2,
  },
  {
    id: 'astral_observatory',
    nameZh: '星辉观象台',
    frameTint: '#7ec8e3',
    boardBg: 'linear-gradient(180deg, #060a14 0%, #102038 50%, #080c18 100%)',
    boardBgAlt:
      'radial-gradient(1px 1px at 20% 30%, #fff8, transparent), radial-gradient(1px 1px at 60% 70%, #aef8, transparent), radial-gradient(1.5px 1.5px at 80% 20%, #fff6, transparent)',
    unlockHint: '累计胜利 5 场解锁',
    unlockVictories: 5,
  },
];

const SKIN_KEY = 'textech_cardbattle_skin';
const VICTORIES_KEY = 'textech_cardbattle_victories';

export function getSelectedSkinId(): string {
  return localStorage.getItem(SKIN_KEY) ?? 'gt_factory';
}

export function setSelectedSkinId(id: string): void {
  localStorage.setItem(SKIN_KEY, id);
}

export function getLifetimeVictories(): number {
  const n = Number(localStorage.getItem(VICTORIES_KEY) ?? '0');
  return Number.isFinite(n) ? n : 0;
}

export function addLifetimeVictories(delta: number): number {
  const next = getLifetimeVictories() + Math.max(0, delta);
  localStorage.setItem(VICTORIES_KEY, String(next));
  return next;
}

export function isSkinUnlocked(skin: BoardSkin, victories = getLifetimeVictories()): boolean {
  return victories >= skin.unlockVictories;
}

export function resolveSkin(id?: string): BoardSkin {
  const hit = BOARD_SKINS.find((s) => s.id === (id ?? getSelectedSkinId()));
  return hit ?? BOARD_SKINS[0];
}

export function listSkinsForUi(victories = getLifetimeVictories()): Array<BoardSkin & { unlocked: boolean }> {
  return BOARD_SKINS.map((s) => ({ ...s, unlocked: isSkinUnlocked(s, victories) }));
}
