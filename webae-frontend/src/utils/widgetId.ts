/** Monotonic counter so rapid consecutive creates never collide within the same ms. */
let widgetIdSeq = 0;

/** Generate a unique widget id. Safe for root, overview, power, and nested group copies. */
export function createWidgetId(prefix = 'w-'): string {
  widgetIdSeq += 1;
  const time = Date.now().toString(36);
  const seq = widgetIdSeq.toString(36);
  const rand = Math.random().toString(36).slice(2, 8);
  return `${prefix}${time}-${seq}-${rand}`;
}

/** Reset sequence (tests only). */
export function resetWidgetIdSeqForTests(): void {
  widgetIdSeq = 0;
}
