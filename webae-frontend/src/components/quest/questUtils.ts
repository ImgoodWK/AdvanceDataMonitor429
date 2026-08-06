import type { QuestLineEdgeDto, QuestLineNodeDto } from '@/types/dto';
import { FLUID_ID_PREFIX, fluidIconId } from '@/utils/icon';

const STATE_COLORS: Record<string, string> = {
  LOCKED: '#64748b',
  UNLOCKED: '#3b82f6',
  UNCLAIMED: '#f59e0b',
  COMPLETED: '#22c55e',
  REPEATABLE: '#a855f7',
};

export function questStateColor(state?: string): string {
  return STATE_COLORS[state || 'LOCKED'] ?? STATE_COLORS.LOCKED;
}

/** Fields used to resolve a quest step / analysis icon. */
export type QuestIconSource = {
  iconItemId?: string | null;
  fluidName?: string | null;
  itemId?: string | null;
  registryName?: string | null;
  meta?: number | null;
  displayName?: string | null;
};

function isFluidId(id?: string | null): boolean {
  return Boolean(id?.startsWith(FLUID_ID_PREFIX));
}

function isGtFluidDisplay(registryName?: string | null): boolean {
  return Boolean(registryName && registryName.indexOf('GregTech_FluidDisplay') >= 0);
}

/** Bake meta into itemId like RecipeItemEntries.buildItemId (recipe NEI path). */
function buildItemIconId(
  itemId?: string | null,
  registryName?: string | null,
  meta?: number | null
): string | undefined {
  const reg = (registryName || itemId || '').trim();
  if (!reg || isFluidId(reg)) return undefined;
  if (itemId && !isFluidId(itemId) && /:\d+$/.test(itemId)) {
    return itemId;
  }
  const m = meta ?? 0;
  if (m > 0) return `${reg}:${m}`;
  return reg;
}

/**
 * Human-readable material label for submit conditions / AE stock rows.
 * Prefer localized {@code displayName} (e.g. GT fluid cell) over raw registry ids.
 */
export function questMaterialLabel(
  src: QuestIconSource | null | undefined,
  fallback = ''
): string {
  if (!src) return fallback;
  if (src.displayName) return src.displayName;
  if (src.fluidName) return src.fluidName;
  if (src.iconItemId?.startsWith(FLUID_ID_PREFIX)) {
    return src.iconItemId.slice(FLUID_ID_PREFIX.length);
  }
  return src.registryName || src.itemId || fallback;
}

/**
 * Icon props for quest UI.
 * <p>
 * Node icons work via {@code { id: iconItemId }} — submit/detect rows must use the
 * same primary path. Passing {@code item={registryName, meta}} alone can put bare
 * {@code mod:id} first in {@code iconLookupIds} and show the wrong GT meta icon.
 */
export function questIconProps(
  src: QuestIconSource | null | undefined
): { id?: string; item?: { itemId?: string; registryName?: string; meta?: number; displayName?: string } } | null {
  if (!src) return null;

  const registryName = src.registryName || undefined;
  const bakedItemId = buildItemIconId(src.itemId, registryName, src.meta);
  const hasItemStack = Boolean(bakedItemId);
  const fluidDisplay = isGtFluidDisplay(registryName);
  const legacyFluidOnCell =
    isFluidId(src.iconItemId) && hasItemStack && !fluidDisplay && !src.fluidName;

  // Same as working node/header icons: prefer explicit cache id.
  if (src.iconItemId && !legacyFluidOnCell) {
    return { id: src.iconItemId };
  }

  if (src.fluidName && (!hasItemStack || fluidDisplay)) {
    return { id: fluidIconId(src.fluidName) };
  }

  // Filled cell / item fallback (incl. ignoring legacy fluid: rewrite on cells)
  if (bakedItemId) {
    return {
      id: bakedItemId,
      item: {
        itemId: bakedItemId,
        registryName: registryName && !isFluidId(registryName) ? registryName : undefined,
        meta: src.meta ?? undefined,
        displayName: src.displayName ?? undefined,
      },
    };
  }

  if (src.fluidName) {
    return { id: fluidIconId(src.fluidName) };
  }
  return null;
}

/** Topological order of nodes in a line (prereqs first); tie-break by y then x. */
export function orderQuestNodes(
  nodes: QuestLineNodeDto[],
  edges: QuestLineEdgeDto[]
): QuestLineNodeDto[] {
  const byId = new Map(nodes.map((n) => [n.questId, n]));
  const indegree = new Map<string, number>();
  const children = new Map<string, string[]>();
  for (const n of nodes) {
    indegree.set(n.questId, 0);
    children.set(n.questId, []);
  }
  for (const e of edges) {
    if (!byId.has(e.fromQuestId) || !byId.has(e.toQuestId)) continue;
    indegree.set(e.toQuestId, (indegree.get(e.toQuestId) ?? 0) + 1);
    children.get(e.fromQuestId)!.push(e.toQuestId);
  }

  const cmpPos = (a: string, b: string) => {
    const na = byId.get(a)!;
    const nb = byId.get(b)!;
    if ((na.y || 0) !== (nb.y || 0)) return (na.y || 0) - (nb.y || 0);
    return (na.x || 0) - (nb.x || 0);
  };

  const queue = [...indegree.entries()]
    .filter(([, d]) => d === 0)
    .map(([id]) => id)
    .sort(cmpPos);

  const ordered: QuestLineNodeDto[] = [];
  const seen = new Set<string>();
  while (queue.length) {
    const id = queue.shift()!;
    if (seen.has(id)) continue;
    seen.add(id);
    const node = byId.get(id);
    if (node) ordered.push(node);
    for (const child of children.get(id) ?? []) {
      const next = (indegree.get(child) ?? 1) - 1;
      indegree.set(child, next);
      if (next === 0) {
        queue.push(child);
        queue.sort(cmpPos);
      }
    }
  }
  for (const n of nodes) {
    if (!seen.has(n.questId)) ordered.push(n);
  }
  return ordered;
}

export const QUEST_PREVIEW_MODE_KEY = 'webae.quest.previewMode';
export const QUEST_REFRESH_CD_MS = 30_000;

/**
 * Compute the set of questIds visible in preview mode:
 * all canSubmit nodes and any node directly connected to a canSubmit node by an edge.
 */
export function computePreviewVisibleNodes(
  nodes: QuestLineNodeDto[],
  edges: QuestLineEdgeDto[]
): Set<string> {
  const canSubmitIds = new Set(nodes.filter((n) => n.canSubmit).map((n) => n.questId));
  const visible = new Set(canSubmitIds);
  for (const edge of edges) {
    const fromIn = canSubmitIds.has(edge.fromQuestId);
    const toIn = canSubmitIds.has(edge.toQuestId);
    if (fromIn || toIn) {
      visible.add(edge.fromQuestId);
      visible.add(edge.toQuestId);
    }
  }
  return visible;
}
