import type { QuestLineEdgeDto, QuestLineNodeDto } from '@/types/dto';

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

export const QUEST_HIDE_COMPLETED_KEY = 'webae.quest.hideCompleted';
export const QUEST_REFRESH_CD_MS = 30_000;
