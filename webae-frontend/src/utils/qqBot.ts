export function splitQqBotList(value: string): string[] {
  const seen = new Set<string>();
  return value
    .split(/[\n,]/g)
    .map((item) => item.trim())
    .filter((item) => {
      if (!item || seen.has(item)) return false;
      seen.add(item);
      return true;
    });
}

export function joinQqBotList(value: string[] | undefined): string {
  return (value || []).join('\n');
}

export function qqBotConnectionColor(connected: boolean, phase: string): string {
  if (connected) return 'success';
  if (phase === 'disabled' || phase === 'stopped') return 'default';
  if (phase === 'reconnecting' || phase === 'connecting' || phase === 'identify') return 'processing';
  return 'error';
}
