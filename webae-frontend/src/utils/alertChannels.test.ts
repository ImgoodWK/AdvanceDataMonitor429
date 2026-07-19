import { describe, expect, it } from 'vitest';
import {
  createAlertNotificationTarget,
  enableBuiltInAlertChannels,
  isAlertNotificationTargetConfigured,
} from './alertChannels';
import type { WebAlertsConfigDto } from '@/types/dto';

function baseRules(): WebAlertsConfigDto {
  return {
    version: 2,
    enabled: false,
    pollIntervalSeconds: 10,
    cpuStuckMinutes: 5,
    gtErrorEnabled: true,
    orderCompleteEnabled: true,
    channelThresholdPercent: 90,
    channelThresholdAbsolute: 28,
    browserNotifications: { enabled: false, events: ['gt_error'], severities: [] },
    playerChat: { enabled: false, events: [], severities: ['error'] },
  };
}

describe('alert channel quick setup', () => {
  it('enables built-in routes while preserving their filters', () => {
    const enabled = enableBuiltInAlertChannels(baseRules());
    expect(enabled.enabled).toBe(true);
    expect(enabled.browserNotifications).toEqual({
      enabled: true,
      events: ['gt_error'],
      severities: [],
    });
    expect(enabled.playerChat).toEqual({ enabled: true, events: [], severities: ['error'] });
    expect(enabled.playerHud).toMatchObject({
      enabled: true,
      severities: ['warning', 'error'],
      durationSeconds: 10,
      maxVisible: 3,
      position: 'top_right',
    });
  });

  it('creates recommended SMTP defaults', () => {
    expect(createAlertNotificationTarget('email', 42)).toMatchObject({
      id: 'email-42',
      enabled: true,
      smtpPort: 587,
      smtpSecurity: 'starttls',
      subjectPrefix: '[WebAE]',
    });
  });

  it('recognizes masked saved secrets as configured', () => {
    const target = createAlertNotificationTarget('qq_official', 42);
    target.appId = 'app';
    target.appSecret = '';
    target.appSecretConfigured = true;
    target.targetId = 'group-openid';
    expect(isAlertNotificationTargetConfigured(target)).toBe(true);
  });

  it('requires a recipient for a WeCom app target', () => {
    const target = createAlertNotificationTarget('wecom_app', 42);
    target.corpId = 'corp';
    target.corpSecretConfigured = true;
    target.agentId = 100001;
    expect(isAlertNotificationTargetConfigured(target)).toBe(false);
    target.toParty = '2';
    expect(isAlertNotificationTargetConfigured(target)).toBe(true);
  });
});
