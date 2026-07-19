import { describe, expect, it, vi } from 'vitest';
import { alertOccurrenceKey, notifyAlertOnce } from './alertNotification';
import type { WebAlertDto } from '@/types/dto';

function alert(patch: Partial<WebAlertDto> = {}): WebAlertDto {
  return {
    id: 'owner:source:1',
    type: 'gt_error',
    severity: 'error',
    title: 'GT error',
    message: 'Machine stopped',
    timestamp: 1,
    networkId: 0,
    ...patch,
  };
}

describe('alertNotification', () => {
  it('uses the occurrence id when present', () => {
    expect(alertOccurrenceKey(alert())).toBe('owner:source:1');
  });

  it('does not show WebAE/browser notifications when the server route is disabled', () => {
    const notify = vi.fn();
    expect(notifyAlertOnce(alert({ browserNotify: false }), notify)).toBe(false);
    expect(notify).not.toHaveBeenCalled();
  });
});
