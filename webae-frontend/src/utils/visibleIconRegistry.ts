/** Ref-counted registry of icon ids currently shown in the WebAE UI (for Settings "fill visible missing"). */

const visibleCounts = new Map<string, number>();

export function trackVisibleIcon(itemId: string): () => void {
  const id = itemId?.trim();
  if (!id) return () => undefined;
  visibleCounts.set(id, (visibleCounts.get(id) || 0) + 1);
  return () => {
    const next = (visibleCounts.get(id) || 1) - 1;
    if (next <= 0) visibleCounts.delete(id);
    else visibleCounts.set(id, next);
  };
}

export function trackVisibleIcons(itemIds: string[]): () => void {
  const unsubs = itemIds.map((id) => trackVisibleIcon(id));
  return () => {
    for (const unsub of unsubs) unsub();
  };
}

export function getVisibleIconIds(): string[] {
  return Array.from(visibleCounts.keys());
}
