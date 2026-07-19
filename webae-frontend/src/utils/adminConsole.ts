export type AdminConsolePlayerFilter = 'all' | 'online' | 'offline';

const HIGH_RISK_ROOTS = new Set([
  'stop',
  'restart',
  'save-off',
  'op',
  'deop',
  'ban',
  'ban-ip',
  'pardon',
  'pardon-ip',
  'kick',
  'kill',
  'whitelist',
]);

export function normalizeAdminCommand(command: string): string {
  return command.trim().replace(/^\/+\s*/, '');
}

export function isHighRiskAdminCommand(command: string): boolean {
  const root = normalizeAdminCommand(command).toLowerCase().split(/\s+/, 1)[0] ?? '';
  return HIGH_RISK_ROOTS.has(root);
}

export function insertCommandToken(
  command: string,
  token: string,
  selectionStart = command.length,
  selectionEnd = selectionStart,
): { value: string; cursor: number } {
  const cleanToken = token.trim();
  if (!cleanToken) return { value: command, cursor: selectionStart };
  const start = Math.max(0, Math.min(selectionStart, command.length));
  const end = Math.max(start, Math.min(selectionEnd, command.length));
  const before = command.slice(0, start);
  const after = command.slice(end);
  const prefix = before.length > 0 && !/\s$/.test(before) ? ' ' : '';
  const suffix = after.length > 0 && !/^\s/.test(after) ? ' ' : '';
  const inserted = `${prefix}${cleanToken}${suffix}`;
  return {
    value: `${before}${inserted}${after}`,
    cursor: before.length + prefix.length + cleanToken.length + suffix.length,
  };
}
