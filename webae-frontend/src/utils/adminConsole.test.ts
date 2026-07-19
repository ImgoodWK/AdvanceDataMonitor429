import { describe, expect, it } from 'vitest';
import { insertCommandToken, isHighRiskAdminCommand, normalizeAdminCommand } from './adminConsole';

describe('admin console helpers', () => {
  it('normalizes optional leading slashes', () => {
    expect(normalizeAdminCommand('  /say hello  ')).toBe('say hello');
    expect(normalizeAdminCommand('/// kick Alex')).toBe('kick Alex');
  });

  it('classifies disruptive command roots without substring false positives', () => {
    expect(isHighRiskAdminCommand('/stop')).toBe(true);
    expect(isHighRiskAdminCommand('whitelist add Alex')).toBe(true);
    expect(isHighRiskAdminCommand('stopsound Alex')).toBe(false);
    expect(isHighRiskAdminCommand('say stop')).toBe(false);
  });

  it('inserts a player token at the selection with safe spacing', () => {
    expect(insertCommandToken('kick reason', 'Alex', 5, 5)).toEqual({
      value: 'kick Alex reason',
      cursor: 10,
    });
    expect(insertCommandToken('tp target', 'uuid', 3, 9).value).toBe('tp uuid');
  });
});
