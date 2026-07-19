import type {
  AlertNotificationTargetDto,
  AlertNotificationTargetType,
  WebAlertsConfigDto,
} from '@/types/dto';

export const ALERT_NOTIFICATION_TARGET_TYPES: AlertNotificationTargetType[] = [
  'qq_official',
  'wechat_official',
  'email',
  'wecom_bot',
  'wecom_app',
];

export function createAlertNotificationTarget(
  type: AlertNotificationTargetType,
  now = Date.now()
): AlertNotificationTargetDto {
  const common: AlertNotificationTargetDto = {
    id: `${type}-${now}`,
    type,
    enabled: true,
    events: [],
    severities: [],
    ownerUuids: [],
  };
  if (type === 'qq_official') {
    return { ...common, targetType: 'group', appId: '', appSecret: '', targetId: '' };
  }
  if (type === 'wechat_official') {
    return { ...common, mode: 'customer_service', appId: '', appSecret: '', targetId: '' };
  }
  if (type === 'email') {
    return {
      ...common,
      smtpHost: '',
      smtpPort: 587,
      smtpSecurity: 'starttls',
      smtpUsername: '',
      smtpPassword: '',
      mailFrom: '',
      mailTo: [],
      mailCc: [],
      subjectPrefix: '[WebAE]',
    };
  }
  if (type === 'wecom_bot') {
    return { ...common, url: '' };
  }
  return {
    ...common,
    corpId: '',
    corpSecret: '',
    agentId: 0,
    toUser: '',
    toParty: '',
    toTag: '',
  };
}

function hasSecret(value?: string, configured?: boolean): boolean {
  return !!configured || !!value?.trim();
}

export function isAlertNotificationTargetConfigured(
  target: AlertNotificationTargetDto
): boolean {
  if (target.type === 'qq_official') {
    return !!target.appId?.trim() && hasSecret(target.appSecret, target.appSecretConfigured)
      && !!target.targetId?.trim();
  }
  if (target.type === 'wechat_official') {
    return !!target.appId?.trim() && hasSecret(target.appSecret, target.appSecretConfigured)
      && !!target.targetId?.trim()
      && (target.mode !== 'template' || !!target.templateId?.trim());
  }
  if (target.type === 'email') {
    return !!target.smtpHost?.trim() && !!target.smtpPort && !!target.mailFrom?.trim()
      && !!target.mailTo?.length
      && (!target.smtpUsername?.trim()
        || hasSecret(target.smtpPassword, target.smtpPasswordConfigured));
  }
  if (target.type === 'wecom_bot') {
    return hasSecret(target.url, target.urlConfigured);
  }
  return !!target.corpId?.trim() && hasSecret(target.corpSecret, target.corpSecretConfigured)
    && !!target.agentId && !!(target.toUser?.trim() || target.toParty?.trim() || target.toTag?.trim());
}

export function enableBuiltInAlertChannels(rules: WebAlertsConfigDto): WebAlertsConfigDto {
  return {
    ...rules,
    enabled: true,
    browserNotifications: {
      enabled: true,
      events: [...(rules.browserNotifications?.events ?? [])],
      severities: [...(rules.browserNotifications?.severities ?? [])],
    },
    playerChat: {
      enabled: true,
      events: [...(rules.playerChat?.events ?? [])],
      severities: [...(rules.playerChat?.severities ?? [])],
    },
    playerHud: {
      enabled: true,
      events: [...(rules.playerHud?.events ?? [])],
      severities: [...(rules.playerHud?.severities ?? ['warning', 'error'])],
      durationSeconds: rules.playerHud?.durationSeconds ?? 10,
      maxVisible: rules.playerHud?.maxVisible ?? 3,
      position: rules.playerHud?.position ?? 'top_right',
      soundEnabled: rules.playerHud?.soundEnabled ?? false,
    },
  };
}

export type BrowserNotificationPermission = NotificationPermission | 'unsupported';

export function getBrowserNotificationPermission(): BrowserNotificationPermission {
  if (typeof Notification === 'undefined') return 'unsupported';
  return Notification.permission;
}
