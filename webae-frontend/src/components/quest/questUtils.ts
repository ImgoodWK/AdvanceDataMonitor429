import type { QuestLineEdgeDto, QuestLineNodeDto } from '@/types/dto';
import { fluidIconId } from '@/utils/icon';

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
  if (src.iconItemId?.startsWith('fluid:')) {
    return src.iconItemId.slice('fluid:'.length);
  }
  return src.registryName || src.itemId || fallback;
}

/**
 * Icon props for quest UI: prefer display {@code iconItemId} (fluid: for cells),
 * then true fluid tasks, then item registry.
 */
export function questIconProps(
  src: QuestIconSource | null | undefined
): { id: string } | { item: { itemId?: string; registryName?: string; meta?: number } } | null {
  if (!src) return null;
  if (src.iconItemId) {
    return { id: src.iconItemId };
  }
  if (src.fluidName) {
    return { id: fluidIconId(src.fluidName) };
  }
  const registryName = src.registryName || undefined;
  const itemId = src.itemId || registryName;
  if (!itemId && !registryName) return null;
  return {
    item: {
      itemId: itemId || undefined,
      registryName,
      meta: src.meta ?? undefined,
    },
  };
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
