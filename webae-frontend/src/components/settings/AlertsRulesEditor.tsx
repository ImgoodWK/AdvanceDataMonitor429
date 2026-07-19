import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  Spin,
} from 'antd';
import {
  DeleteOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SaveOutlined,
  SearchOutlined,
  SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { AlertDeliveryGuideModal } from '@/components/settings/AlertDeliveryGuideModal';
import { QqIdProbeModal } from '@/components/settings/QqIdProbeModal';
import { useI18n } from '@/i18n';
import type {
  AlertDeliveryStatusDto,
  AlertDeliveryTestResponse,
  AlertNotificationFilterDto,
  AlertNotificationTargetDto,
  AlertNotificationTargetType,
  AlertsResponse,
  AutomationRuleDto,
  WebAlertsConfigDto,
  WebhookRuleDto,
} from '@/types/dto';
import {
  ALERT_NOTIFICATION_TARGET_TYPES,
  createAlertNotificationTarget,
  enableBuiltInAlertChannels,
  getBrowserNotificationPermission,
  isAlertNotificationTargetConfigured,
  type BrowserNotificationPermission,
} from '@/utils/alertChannels';

const { Text, Title } = Typography;

const WEBHOOK_EVENT_KEYS = [
  'inventory_threshold',
  'cpu_stuck',
  'gt_error',
  'order_complete',
  'channel_overload',
  'server_tps_below',
  'automation_craft',
] as const;

const ALERT_SEVERITY_KEYS = ['info', 'warning', 'error'] as const;

type InventoryRuleRow = NonNullable<WebAlertsConfigDto['inventoryThresholds']>[number];
type AutomationRuleRow = AutomationRuleDto;

function emptyAutomationRule(): AutomationRuleRow {
  return {
    id: `auto-${Date.now()}`,
    enabled: true,
    type: 'craft_when_below',
    itemId: '',
    threshold: 1000,
    craftAmount: 0,
    patternId: '',
    cpuName: '',
    networkId: -1,
    cooldownSeconds: 300,
    requireCpuIdle: true,
    maxTriggersPerHour: 12,
  };
}

function emptyRule(): InventoryRuleRow {
  return {
    itemId: '',
    fluidName: '',
    minAmount: 0,
    networkId: -1,
    label: '',
  };
}

function emptyWebhook(): WebhookRuleDto {
  return {
    id: `webhook-${Date.now()}`,
    url: '',
    enabled: true,
    events: ['inventory_threshold'],
    mention: '',
  };
}

function cloneFilter<T extends AlertNotificationFilterDto | undefined>(filter: T): T {
  if (!filter) return filter;
  return {
    ...filter,
    events: filter.events ? [...filter.events] : [],
    severities: filter.severities ? [...filter.severities] : [],
  } as T;
}

function secretInputValue(value?: string): string {
  return value?.startsWith('***') ? '' : value ?? '';
}

function secretInputPlaceholder(value?: string, configured?: boolean): string {
  return configured || value?.startsWith('***') ? value || '********' : '';
}

function targetHasConfiguredSecret(target: AlertNotificationTargetDto): boolean {
  return !!(
    target.urlConfigured ||
    target.appSecretConfigured ||
    target.corpSecretConfigured ||
    target.smtpPasswordConfigured ||
    target.url?.startsWith('***') ||
    target.appSecret?.startsWith('***') ||
    target.corpSecret?.startsWith('***') ||
    target.smtpPassword?.startsWith('***')
  );
}

function cloneRules(rules: WebAlertsConfigDto): WebAlertsConfigDto {
  return {
    ...rules,
    inventoryThresholds: (rules.inventoryThresholds ?? []).map((r) => ({ ...r })),
    webhooks: (rules.webhooks ?? []).map((w) => ({
      ...w,
      events: w.events ? [...w.events] : [],
    })),
    automationRules: (rules.automationRules ?? []).map((r) => ({ ...r })),
    browserNotifications: cloneFilter(rules.browserNotifications),
    playerChat: cloneFilter(rules.playerChat),
    playerHud: cloneFilter(rules.playerHud),
    notificationTargets: (rules.notificationTargets ?? []).map((target) => ({
      ...target,
      events: target.events ? [...target.events] : [],
      severities: target.severities ? [...target.severities] : [],
      ownerUuids: target.ownerUuids ? [...target.ownerUuids] : [],
      mailTo: target.mailTo ? [...target.mailTo] : [],
      mailCc: target.mailCc ? [...target.mailCc] : [],
    })),
  };
}

interface AlertsRulesEditorProps {
  notify: (msg: string, type?: 'success' | 'error' | 'info' | 'warning') => void;
}

export function AlertsRulesEditor({ notify }: AlertsRulesEditorProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [canEdit, setCanEdit] = useState(false);
  const [serverFeatureEnabled, setServerFeatureEnabled] = useState(true);
  const [deliveryStatus, setDeliveryStatus] = useState<AlertDeliveryStatusDto | null>(null);
  const [rules, setRules] = useState<WebAlertsConfigDto | null>(null);
  const [draft, setDraft] = useState<WebAlertsConfigDto | null>(null);
  const [guideOpen, setGuideOpen] = useState(false);
  const [quickEnabling, setQuickEnabling] = useState(false);
  const [qqProbeIndex, setQqProbeIndex] = useState<number | null>(null);
  const [testingRoute, setTestingRoute] = useState<string | null>(null);
  const [browserPermission, setBrowserPermission] = useState<BrowserNotificationPermission>(
    getBrowserNotificationPermission
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await getApiClient().get<AlertsResponse>('/api/alerts');
      if (r.rules) {
        setRules(r.rules);
        setDraft(cloneRules(r.rules));
      }
      setCanEdit(!!r.canEditRules);
      setServerFeatureEnabled(r.serverFeatureEnabled !== false);
      setDeliveryStatus(r.deliveryStatus ?? null);
    } catch {
      setRules(null);
      setDraft(null);
      notify(t('alertsLoadFailed'), 'error');
    } finally {
      setLoading(false);
    }
  }, [notify, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const updateDraft = (patch: Partial<WebAlertsConfigDto>) => {
    setDraft((prev) => (prev ? { ...prev, ...patch } : prev));
  };

  const updateInventoryRow = (index: number, patch: Partial<InventoryRuleRow>) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.inventoryThresholds ?? [])];
      rows[index] = { ...rows[index], ...patch };
      return { ...prev, inventoryThresholds: rows };
    });
  };

  const addInventoryRow = () => {
    setDraft((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        inventoryThresholds: [...(prev.inventoryThresholds ?? []), emptyRule()],
      };
    });
  };

  const removeInventoryRow = (index: number) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.inventoryThresholds ?? [])];
      rows.splice(index, 1);
      return { ...prev, inventoryThresholds: rows };
    });
  };

  const updateWebhookRow = (index: number, patch: Partial<WebhookRuleDto>) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.webhooks ?? [])];
      rows[index] = { ...rows[index], ...patch };
      return { ...prev, webhooks: rows };
    });
  };

  const addWebhookRow = () => {
    setDraft((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        webhooks: [...(prev.webhooks ?? []), emptyWebhook()],
      };
    });
  };

  const removeWebhookRow = (index: number) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.webhooks ?? [])];
      rows.splice(index, 1);
      return { ...prev, webhooks: rows };
    });
  };

  const updateAutomationRow = (index: number, patch: Partial<AutomationRuleRow>) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.automationRules ?? [])];
      rows[index] = { ...rows[index], ...patch };
      return { ...prev, automationRules: rows };
    });
  };

  const updateNotificationFilter = (
    key: 'browserNotifications' | 'playerChat',
    patch: Partial<AlertNotificationFilterDto>
  ) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const current = prev[key] ?? { enabled: true, events: [], severities: [] };
      return { ...prev, [key]: { ...current, ...patch } };
    });
  };

  const updateHudFilter = (patch: Partial<NonNullable<WebAlertsConfigDto['playerHud']>>) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const current = prev.playerHud ?? {
        enabled: true,
        events: [],
        severities: ['warning', 'error'],
        durationSeconds: 10,
        maxVisible: 3,
        position: 'top_right',
        soundEnabled: false,
      };
      return { ...prev, playerHud: { ...current, ...patch } };
    });
  };

  const updateNotificationTarget = (index: number, patch: Partial<AlertNotificationTargetDto>) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.notificationTargets ?? [])];
      rows[index] = { ...rows[index], ...patch };
      return { ...prev, notificationTargets: rows };
    });
  };

  const replaceNotificationTarget = (index: number, target: AlertNotificationTargetDto) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.notificationTargets ?? [])];
      rows[index] = target;
      return { ...prev, notificationTargets: rows };
    });
  };

  const addNotificationTarget = (type: AlertNotificationTargetType) => {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            notificationTargets: [
              ...(prev.notificationTargets ?? []),
              createAlertNotificationTarget(type),
            ],
          }
        : prev
    );
  };

  const requestBrowserPermission = (): Promise<BrowserNotificationPermission> => {
    if (typeof Notification === 'undefined') {
      setBrowserPermission('unsupported');
      return Promise.resolve('unsupported');
    }
    if (Notification.permission !== 'default') {
      setBrowserPermission(Notification.permission);
      return Promise.resolve(Notification.permission);
    }
    return Notification.requestPermission()
      .then((permission) => {
        setBrowserPermission(permission);
        return permission;
      })
      .catch(() => {
        const permission = getBrowserNotificationPermission();
        setBrowserPermission(permission);
        return permission;
      });
  };

  const removeNotificationTarget = (index: number) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.notificationTargets ?? [])];
      rows.splice(index, 1);
      return { ...prev, notificationTargets: rows };
    });
  };

  const addAutomationRow = () => {
    setDraft((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        automationRules: [...(prev.automationRules ?? []), emptyAutomationRule()],
      };
    });
  };

  const removeAutomationRow = (index: number) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const rows = [...(prev.automationRules ?? [])];
      rows.splice(index, 1);
      return { ...prev, automationRules: rows };
    });
  };

  const validateDraft = (cfg: WebAlertsConfigDto): string | null => {
    if (cfg.pollIntervalSeconds < 1 || cfg.pollIntervalSeconds > 300) {
      return t('alertsValidationPoll');
    }
    if (cfg.cpuStuckMinutes < 1 || cfg.cpuStuckMinutes > 120) {
      return t('alertsValidationCpu');
    }
    if (cfg.channelThresholdPercent < 1 || cfg.channelThresholdPercent > 100) {
      return t('alertsValidationChannelPct');
    }
    if (cfg.channelThresholdAbsolute < 1 || cfg.channelThresholdAbsolute > 128) {
      return t('alertsValidationChannelAbs');
    }
    for (const rule of cfg.inventoryThresholds ?? []) {
      const hasItem = !!(rule.itemId && rule.itemId.trim());
      const hasFluid = !!(rule.fluidName && rule.fluidName.trim());
      if (!hasItem && !hasFluid) {
        return t('alertsValidationInventory');
      }
      if (rule.minAmount < 0) {
        return t('alertsValidationMinAmount');
      }
      if (rule.networkId < -1) {
        return t('alertsValidationNetwork');
      }
    }
    for (const hook of cfg.webhooks ?? []) {
      const url = (hook.url ?? '').trim();
      if (url && !url.startsWith('***') && !url.startsWith('http://') && !url.startsWith('https://')) {
        return t('alertsValidationWebhookUrl');
      }
    }
    const targetIds = new Set<string>();
    for (const target of cfg.notificationTargets ?? []) {
      if (!target.id?.trim() || targetIds.has(target.id.trim())) {
        return t('alertsValidationTargetId');
      }
      targetIds.add(target.id.trim());
      for (const value of [target.baseUrl, target.tokenUrl]) {
        if (value?.trim() && !value.startsWith('http://') && !value.startsWith('https://')) {
          return t('alertsValidationUrlOverride');
        }
      }
      if (target.type === 'qq_official') {
        if (!target.appId?.trim() || (!target.appSecret?.trim() && !target.appSecretConfigured) || !target.targetId?.trim()) {
          return t('alertsValidationQq');
        }
      } else if (target.type === 'wechat_official') {
        if (!target.appId?.trim() || (!target.appSecret?.trim() && !target.appSecretConfigured) || !target.targetId?.trim()) {
          return t('alertsValidationWechat');
        }
        if (target.mode === 'template' && !target.templateId?.trim()) {
          return t('alertsValidationWechatTemplate');
        }
      } else if (target.type === 'email') {
        if (!target.smtpHost?.trim() || !target.smtpPort || !target.mailFrom?.trim() || !(target.mailTo?.length)) {
          return t('alertsValidationEmail');
        }
        if (target.smtpUsername?.trim() && !target.smtpPassword?.trim() && !target.smtpPasswordConfigured) {
          return t('alertsValidationEmailPassword');
        }
      } else if (target.type === 'wecom_bot') {
        if (!target.url?.trim() && !target.urlConfigured) {
          return t('alertsValidationWecomBot');
        }
      } else if (target.type === 'wecom_app') {
        if (
          !target.corpId?.trim()
          || (!target.corpSecret?.trim() && !target.corpSecretConfigured)
          || !target.agentId
          || !(target.toUser?.trim() || target.toParty?.trim() || target.toTag?.trim())
        ) {
          return t('alertsValidationWecomApp');
        }
      }
    }
    if (cfg.serverTpsBelowEnabled) {
      const thr = cfg.serverTpsThreshold ?? 15;
      const dur = cfg.serverTpsDurationSeconds ?? 60;
      if (thr < 1 || thr > 20) return t('alertsValidationTpsThreshold');
      if (dur < 10 || dur > 600) return t('alertsValidationTpsDuration');
    }
    for (const rule of cfg.automationRules ?? []) {
      if (!rule.itemId?.trim() || rule.threshold < 1) {
        return t('alertsValidationAutomation');
      }
      if (rule.cooldownSeconds < 1) {
        return t('alertsValidationAutomation');
      }
    }
    return null;
  };

  const applyRulesResponse = (response: AlertsResponse) => {
    if (response.rules) {
      setRules(response.rules);
      setDraft(cloneRules(response.rules));
    }
    setServerFeatureEnabled(response.serverFeatureEnabled !== false);
    setDeliveryStatus(response.deliveryStatus ?? deliveryStatus);
  };

  const save = async () => {
    if (!draft || !canEdit) return;
    const err = validateDraft(draft);
    if (err) {
      notify(err, 'error');
      return;
    }
    setSaving(true);
    try {
      const r = await getApiClient().put<AlertsResponse>('/api/alerts/rules', draft);
      applyRulesResponse(r);
      notify(t('alertsSaveSuccess'), 'success');
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('alertsSaveFailed');
      notify(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  const quickEnableBuiltIns = async () => {
    if (!draft || !canEdit || !serverFeatureEnabled) return;
    const next = enableBuiltInAlertChannels(cloneRules(draft));
    const err = validateDraft(next);
    if (err) {
      notify(err, 'error');
      return;
    }

    // requestPermission must start directly inside this click handler to satisfy browser policy.
    const permissionPromise = requestBrowserPermission();
    setQuickEnabling(true);
    setSaving(true);
    try {
      const [response, permission] = await Promise.all([
        getApiClient().put<AlertsResponse>('/api/alerts/rules', next),
        permissionPromise,
      ]);
      applyRulesResponse(response);
      if (permission === 'denied') {
        notify(t('alertsQuickEnableBrowserDenied'), 'warning');
      } else {
        notify(t('alertsQuickEnableSuccess'), 'success');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('alertsSaveFailed');
      notify(msg, 'error');
    } finally {
      setSaving(false);
      setQuickEnabling(false);
    }
  };

  const sendDeliveryTest = async (kind: 'target' | 'webhook', id: string) => {
    if (!id || !canEdit || !serverFeatureEnabled) return;
    const routeKey = `${kind}:${id}`;
    setTestingRoute(routeKey);
    try {
      const response = await getApiClient().post<AlertDeliveryTestResponse>('/api/alerts/test', {
        kind,
        id,
      });
      setDeliveryStatus(response.deliveryStatus ?? deliveryStatus);
      notify(t('alertsTestQueued'), 'success');
    } catch (e) {
      const message = e instanceof Error ? e.message : t('alertsTestFailed');
      notify(message, 'error');
    } finally {
      setTestingRoute(null);
    }
  };

  const addRouteFromGuide = (route: AlertNotificationTargetType | 'webhook') => {
    if (!canEdit) return;
    if (route === 'webhook') {
      addWebhookRow();
    } else {
      addNotificationTarget(route);
    }
    setGuideOpen(false);
    notify(t('alertsGuideRouteAdded'), 'info');
    window.setTimeout(() => {
      document.getElementById('alerts-external-targets')?.scrollIntoView({ behavior: 'smooth' });
    }, 0);
  };

  if (loading) {
    return <Spin />;
  }

  if (!rules || !draft) {
    return <Alert type="warning" message={t('alertsLoadFailed')} showIcon />;
  }

  const builtInEnabledCount = [
    draft.browserNotifications?.enabled !== false,
    draft.playerChat?.enabled !== false,
    draft.playerHud?.enabled !== false,
  ].filter(Boolean).length;

  const guideButton = (
    <Tooltip title={t('alertsGuideOpen')}>
      <Button
        size="small"
        shape="circle"
        icon={<QuestionCircleOutlined />}
        aria-label={t('alertsGuideOpen')}
        onClick={() => setGuideOpen(true)}
      />
    </Tooltip>
  );

  const guideModal = (
    <AlertDeliveryGuideModal
      open={guideOpen}
      canEdit={canEdit}
      serverFeatureEnabled={serverFeatureEnabled}
      builtInEnabledCount={builtInEnabledCount}
      browserPermission={browserPermission}
      quickEnabling={quickEnabling}
      onClose={() => setGuideOpen(false)}
      onQuickEnable={() => void quickEnableBuiltIns()}
      onAddRoute={addRouteFromGuide}
    />
  );

  const qqProbeTarget =
    qqProbeIndex != null && draft?.notificationTargets
      ? draft.notificationTargets[qqProbeIndex] ?? null
      : null;

  const qqProbeModal = (
    <QqIdProbeModal
      open={qqProbeIndex != null}
      target={qqProbeTarget}
      canEdit={canEdit && serverFeatureEnabled}
      onClose={() => setQqProbeIndex(null)}
      onApply={(kind, targetId) => {
        if (qqProbeIndex == null) return;
        updateNotificationTarget(qqProbeIndex, { targetType: kind, targetId });
        setQqProbeIndex(null);
        notify(t('alertsQqProbeApplied'), 'success');
      }}
    />
  );

  if (!canEdit) {
    return (
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Space>
          <Title level={5} style={{ margin: 0 }}>{t('alertsSettingsTitle')}</Title>
          {guideButton}
        </Space>
        {guideModal}
        {qqProbeModal}
        {!serverFeatureEnabled && (
          <Alert type="warning" message={t('alertsServerFeatureDisabled')} showIcon />
        )}
        <Alert type="info" message={t('alertsReadOnlyHint')} showIcon />
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label={t('alertsEnabled')}>
            {rules.enabled ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsPollInterval')}>
            {rules.pollIntervalSeconds}s
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsCpuStuck')}>
            {rules.cpuStuckMinutes}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsGtErrors')}>
            {rules.gtErrorEnabled ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsOrderComplete')}>
            {rules.orderCompleteEnabled ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsChannelPercent')}>
            {rules.channelThresholdPercent}%
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsChannelAbsolute')}>
            {rules.channelThresholdAbsolute}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsServerTps')}>
            {rules.serverTpsBelowEnabled ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsBrowserNotifications')}>
            {rules.browserNotifications?.enabled !== false ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsPlayerChat')}>
            {rules.playerChat?.enabled !== false ? t('on') : t('off')}
          </Descriptions.Item>
          <Descriptions.Item label={t('alertsPlayerHud')}>
            {rules.playerHud?.enabled !== false ? t('on') : t('off')}
          </Descriptions.Item>
        </Descriptions>
        <Title level={5}>{t('alertsExternalTargets')}</Title>
        {(rules.notificationTargets?.length ?? 0) === 0 ? (
          <Text type="secondary">{t('alertsNoExternalTargets')}</Text>
        ) : (
          <Table
            size="small"
            bordered
            pagination={false}
            rowKey={(row) => row.id}
            dataSource={rules.notificationTargets}
            columns={[
              { title: t('alertsTargetId'), dataIndex: 'id' },
              {
                title: t('alertsTargetType'),
                dataIndex: 'type',
                render: (value: string) => t(`alertsTargetType_${value}`),
              },
              {
                title: t('alertsTargetSeverities'),
                dataIndex: 'severities',
                render: (values?: string[]) =>
                  values?.length
                    ? values.map((value) => t(`alertsSeverity_${value}`)).join(', ')
                    : t('alertsAll'),
              },
              {
                title: t('alertsEnabled'),
                dataIndex: 'enabled',
                render: (value: boolean) => (value ? t('on') : t('off')),
              },
            ]}
          />
        )}
        <Title level={5}>{t('alertsWebhooks')}</Title>
        {(rules.webhooks?.length ?? 0) === 0 ? (
          <Text type="secondary">{t('alertsNoRules')}</Text>
        ) : (
          <Table
            size="small"
            bordered
            pagination={false}
            rowKey={(r) => r.id}
            dataSource={rules.webhooks}
            columns={[
              { title: t('alertsWebhookId'), dataIndex: 'id', ellipsis: true },
              {
                title: t('alertsWebhookUrl'),
                dataIndex: 'url',
                render: (v: string, row: WebhookRuleDto) =>
                  row.urlConfigured || (v && v.startsWith('***')) ? v : t('off'),
              },
              {
                title: t('alertsWebhookEvents'),
                dataIndex: 'events',
                render: (evs: string[] | undefined) =>
                  (evs ?? []).map((e) => t(`alertsEvent_${e}`)).join(', '),
              },
            ]}
          />
        )}
        <Title level={5}>{t('alertsInventoryRules')}</Title>
        {(rules.inventoryThresholds?.length ?? 0) === 0 ? (
          <Text type="secondary">{t('alertsNoRules')}</Text>
        ) : (
          <Table
            size="small"
            bordered
            pagination={false}
            rowKey={(_, i) => String(i)}
            dataSource={rules.inventoryThresholds}
            columns={[
              { title: t('alertsRuleLabel'), dataIndex: 'label', ellipsis: true },
              { title: t('alertsRuleItemId'), dataIndex: 'itemId', ellipsis: true },
              { title: t('alertsRuleFluid'), dataIndex: 'fluidName', ellipsis: true },
              { title: t('alertsRuleMin'), dataIndex: 'minAmount' },
              {
                title: t('alertsRuleNetwork'),
                dataIndex: 'networkId',
                render: (v: number) => (v < 0 ? t('alertsRuleNetworkAll') : v),
              },
            ]}
          />
        )}
      </Space>
    );
  }

  return (
    <Form layout="vertical" onFinish={() => void save()}>
      <Space align="start" style={{ width: '100%', marginBottom: 16 }}>
        <Alert type="info" message={t('alertsSettingsDesc')} showIcon style={{ flex: 1 }} />
        {guideButton}
      </Space>
      {guideModal}
      {qqProbeModal}
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {!serverFeatureEnabled && (
          <Alert type="warning" message={t('alertsServerFeatureDisabled')} showIcon />
        )}
        {deliveryStatus && (
          <Alert
            type={deliveryStatus.circuitOpenTargets > 0 || deliveryStatus.dropped > 0 ? 'warning' : 'success'}
            message={t('alertsDeliveryStatus')}
            description={t('alertsDeliveryStatusDetail', {
              queue: deliveryStatus.queueDepth,
              capacity: deliveryStatus.queueCapacity,
              delivered: deliveryStatus.delivered,
              failed: deliveryStatus.failed,
              dropped: deliveryStatus.dropped,
              circuits: deliveryStatus.circuitOpenTargets,
            })}
            showIcon
          />
        )}
        <Card
          size="small"
          title={
            <Space>
              <ThunderboltOutlined />
              <span>{t('alertsQuickStartTitle')}</span>
            </Space>
          }
          extra={<Tag color={builtInEnabledCount === 3 && draft.enabled ? 'success' : 'default'}>{t('alertsBuiltInStatus', { count: builtInEnabledCount })}</Tag>}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">{t('alertsQuickStartDesc')}</Text>
            <Space wrap>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={quickEnabling}
                disabled={!serverFeatureEnabled}
                onClick={() => void quickEnableBuiltIns()}
              >
                {t('alertsQuickEnable')}
              </Button>
              <Tag>{t(`alertsBrowserPermission_${browserPermission}`)}</Tag>
              <Text>{t('alertsGlobalSwitch')}</Text>
              <Switch
                checked={draft.enabled}
                onChange={(enabled) => updateDraft({ enabled })}
                checkedChildren={t('on')}
                unCheckedChildren={t('off')}
                aria-label={t('alertsEnabled')}
              />
            </Space>
          </Space>
        </Card>

        <Collapse
          size="small"
          items={[
            {
              key: 'detection',
              label: t('alertsDetectionAdvanced'),
              children: (
                <Space wrap align="start">
                  <Form.Item label={t('alertsPollInterval')}>
                    <InputNumber min={1} max={300} value={draft.pollIntervalSeconds} onChange={(v) => updateDraft({ pollIntervalSeconds: v ?? 10 })} addonAfter="s" />
                  </Form.Item>
                  <Form.Item label={t('alertsCpuStuck')}>
                    <InputNumber min={1} max={120} value={draft.cpuStuckMinutes} onChange={(v) => updateDraft({ cpuStuckMinutes: v ?? 5 })} addonAfter={t('minutesUnit')} />
                  </Form.Item>
                  <Form.Item label={t('alertsGtErrors')}>
                    <Switch checked={draft.gtErrorEnabled} onChange={(v) => updateDraft({ gtErrorEnabled: v })} />
                  </Form.Item>
                  <Form.Item label={t('alertsOrderComplete')}>
                    <Switch checked={draft.orderCompleteEnabled} onChange={(v) => updateDraft({ orderCompleteEnabled: v })} />
                  </Form.Item>
                  <Form.Item label={t('alertsChannelPercent')}>
                    <InputNumber min={1} max={100} value={draft.channelThresholdPercent} onChange={(v) => updateDraft({ channelThresholdPercent: v ?? 90 })} addonAfter="%" />
                  </Form.Item>
                  <Form.Item label={t('alertsChannelAbsolute')}>
                    <InputNumber min={1} max={128} value={draft.channelThresholdAbsolute} onChange={(v) => updateDraft({ channelThresholdAbsolute: v ?? 28 })} />
                  </Form.Item>
                  <Form.Item label={t('alertsServerTpsEnabled')}>
                    <Switch checked={!!draft.serverTpsBelowEnabled} onChange={(v) => updateDraft({ serverTpsBelowEnabled: v })} />
                  </Form.Item>
                  <Form.Item label={t('alertsServerTpsThreshold')}>
                    <InputNumber min={1} max={20} step={0.5} disabled={!draft.serverTpsBelowEnabled} value={draft.serverTpsThreshold ?? 15} onChange={(v) => updateDraft({ serverTpsThreshold: v ?? 15 })} />
                  </Form.Item>
                  <Form.Item label={t('alertsServerTpsDuration')}>
                    <InputNumber min={10} max={600} disabled={!draft.serverTpsBelowEnabled} value={draft.serverTpsDurationSeconds ?? 60} onChange={(v) => updateDraft({ serverTpsDurationSeconds: v ?? 60 })} addonAfter="s" />
                  </Form.Item>
                </Space>
              ),
            },
          ]}
        />

        <Title level={5}>{t('alertsLocalChannels')}</Title>
        <Alert type="info" message={t('alertsLocalChannelsDesc')} showIcon />
        <Card
          size="small"
          title={t('alertsBrowserNotifications')}
          extra={<Switch checked={draft.browserNotifications?.enabled !== false} onChange={(enabled) => { updateNotificationFilter('browserNotifications', { enabled }); if (enabled) void requestBrowserPermission(); }} aria-label={t('alertsBrowserNotifications')} />}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">{t(`alertsBrowserPermission_${browserPermission}`)}</Text>
            <Collapse
              ghost
              size="small"
              items={[{
                key: 'advanced',
                label: t('alertsChannelAdvanced'),
                children: (
                  <Space wrap align="start">
                    <Form.Item label={t('alertsTargetEvents')}>
                      <Select mode="multiple" allowClear style={{ minWidth: 260 }} placeholder={t('alertsAllEvents')} value={draft.browserNotifications?.events ?? []} onChange={(events) => updateNotificationFilter('browserNotifications', { events })} options={WEBHOOK_EVENT_KEYS.map((key) => ({ value: key, label: t(`alertsEvent_${key}`) }))} />
                    </Form.Item>
                    <Form.Item label={t('alertsTargetSeverities')}>
                      <Select mode="multiple" allowClear style={{ minWidth: 220 }} placeholder={t('alertsAllSeverities')} value={draft.browserNotifications?.severities ?? []} onChange={(severities) => updateNotificationFilter('browserNotifications', { severities })} options={ALERT_SEVERITY_KEYS.map((key) => ({ value: key, label: t(`alertsSeverity_${key}`) }))} />
                    </Form.Item>
                  </Space>
                ),
              }]}
            />
          </Space>
        </Card>
        <Card
          size="small"
          title={t('alertsPlayerChat')}
          extra={<Switch checked={draft.playerChat?.enabled !== false} onChange={(enabled) => updateNotificationFilter('playerChat', { enabled })} aria-label={t('alertsPlayerChat')} />}
        >
          <Collapse
            ghost
            size="small"
            items={[{
              key: 'advanced',
              label: t('alertsChannelAdvanced'),
              children: (
                <Space wrap align="start">
                  <Form.Item label={t('alertsTargetEvents')}>
                    <Select mode="multiple" allowClear style={{ minWidth: 260 }} placeholder={t('alertsAllEvents')} value={draft.playerChat?.events ?? []} onChange={(events) => updateNotificationFilter('playerChat', { events })} options={WEBHOOK_EVENT_KEYS.map((key) => ({ value: key, label: t(`alertsEvent_${key}`) }))} />
                  </Form.Item>
                  <Form.Item label={t('alertsTargetSeverities')}>
                    <Select mode="multiple" allowClear style={{ minWidth: 220 }} placeholder={t('alertsAllSeverities')} value={draft.playerChat?.severities ?? []} onChange={(severities) => updateNotificationFilter('playerChat', { severities })} options={ALERT_SEVERITY_KEYS.map((key) => ({ value: key, label: t(`alertsSeverity_${key}`) }))} />
                  </Form.Item>
                </Space>
              ),
            }]}
          />
        </Card>
        <Card
          size="small"
          title={t('alertsPlayerHud')}
          extra={<Switch checked={draft.playerHud?.enabled !== false} onChange={(enabled) => updateHudFilter({ enabled })} aria-label={t('alertsPlayerHud')} />}
        >
          <Collapse
            ghost
            size="small"
            items={[{
              key: 'advanced',
              label: t('alertsChannelAdvanced'),
              children: (
                <Space wrap align="start">
                  <Form.Item label={t('alertsTargetEvents')}>
                    <Select mode="multiple" allowClear style={{ minWidth: 260 }} placeholder={t('alertsAllEvents')} value={draft.playerHud?.events ?? []} onChange={(events) => updateHudFilter({ events })} options={WEBHOOK_EVENT_KEYS.map((key) => ({ value: key, label: t(`alertsEvent_${key}`) }))} />
                  </Form.Item>
                  <Form.Item label={t('alertsTargetSeverities')}>
                    <Select mode="multiple" allowClear style={{ minWidth: 220 }} placeholder={t('alertsAllSeverities')} value={draft.playerHud?.severities ?? []} onChange={(severities) => updateHudFilter({ severities })} options={ALERT_SEVERITY_KEYS.map((key) => ({ value: key, label: t(`alertsSeverity_${key}`) }))} />
                  </Form.Item>
                  <Form.Item label={t('alertsHudDuration')}>
                    <InputNumber min={2} max={120} value={draft.playerHud?.durationSeconds ?? 10} onChange={(durationSeconds) => updateHudFilter({ durationSeconds: durationSeconds ?? 10 })} addonAfter="s" />
                  </Form.Item>
                  <Form.Item label={t('alertsHudMaxVisible')}>
                    <InputNumber min={1} max={8} value={draft.playerHud?.maxVisible ?? 3} onChange={(maxVisible) => updateHudFilter({ maxVisible: maxVisible ?? 3 })} />
                  </Form.Item>
                  <Form.Item label={t('alertsHudPosition')}>
                    <Select style={{ minWidth: 150 }} value={draft.playerHud?.position ?? 'top_right'} onChange={(position) => updateHudFilter({ position })} options={['top_left', 'top_right', 'bottom_left', 'bottom_right'].map((value) => ({ value, label: t(`alertsHudPosition_${value}`) }))} />
                  </Form.Item>
                  <Form.Item label={t('alertsHudSound')}>
                    <Switch checked={!!draft.playerHud?.soundEnabled} onChange={(soundEnabled) => updateHudFilter({ soundEnabled })} />
                  </Form.Item>
                </Space>
              ),
            }]}
          />
        </Card>

        <Title id="alerts-external-targets" level={5}>{t('alertsExternalTargets')}</Title>
        <Alert type="info" message={t('alertsExternalTargetsDesc')} showIcon />
        <Space wrap>
          {ALERT_NOTIFICATION_TARGET_TYPES.map((type) => (
            <Button key={type} icon={<PlusOutlined />} onClick={() => addNotificationTarget(type)}>
              {t(`alertsTargetType_${type}`)}
            </Button>
          ))}
        </Space>
        {(draft.notificationTargets ?? []).map((target, index) => {
          const configured = isAlertNotificationTargetConfigured(target);
          const savedTarget = rules.notificationTargets?.find((saved) => saved.id === target.id);
          const targetIsSaved = !!savedTarget && JSON.stringify(savedTarget) === JSON.stringify(target);
          const testDisabled = !targetIsSaved || !configured || !serverFeatureEnabled;
          return (
          <Card
            key={`${target.id}-${index}`}
            size="small"
            title={
              <Space wrap>
                <span>{`${t(`alertsTargetType_${target.type}`)} · ${target.id || index + 1}`}</span>
                <Tag color={configured ? 'success' : 'warning'}>
                  {configured ? t('alertsTargetConfigured') : t('alertsTargetNeedsConfig')}
                </Tag>
              </Space>
            }
            extra={
              <Space>
                <Tooltip title={!serverFeatureEnabled ? t('alertsTestServerDisabled') : testDisabled ? t('alertsTestSaveFirst') : t('alertsTestSend')}>
                  <span>
                    <Button
                      size="small"
                      icon={<SendOutlined />}
                      loading={testingRoute === `target:${target.id}`}
                      disabled={testDisabled}
                      aria-label={t('alertsTestSend')}
                      onClick={() => void sendDeliveryTest('target', target.id)}
                    >
                      {t('alertsTestSend')}
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip title={t('presetDelete')}>
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={t('presetDelete')}
                    onClick={() => removeNotificationTarget(index)}
                  />
                </Tooltip>
              </Space>
            }
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              <Space wrap>
                <Text>{t('alertsEnabled')}</Text>
                <Switch
                  checked={target.enabled}
                  onChange={(enabled) => updateNotificationTarget(index, { enabled })}
                  aria-label={t('alertsEnabled')}
                />
                <Text type="secondary">{t(`alertsQuickFields_${target.type}`)}</Text>
              </Space>
              {targetHasConfiguredSecret(target) && (
                <Text type="secondary">{t('alertsTargetIdentityLocked')}</Text>
              )}
              <Collapse
                ghost
                size="small"
                items={[{
                  key: 'advanced',
                  label: t('alertsTargetAdvanced'),
                  children: (
                    <Space wrap align="start">
                      <Form.Item label={t('alertsTargetId')}>
                        <Input value={target.id} disabled={targetHasConfiguredSecret(target)} onChange={(event) => updateNotificationTarget(index, { id: event.target.value })} />
                      </Form.Item>
                      <Form.Item label={t('alertsTargetType')}>
                        <Select
                          style={{ minWidth: 190 }}
                          value={target.type}
                          disabled={targetHasConfiguredSecret(target)}
                          onChange={(type: AlertNotificationTargetType) => replaceNotificationTarget(index, { ...createAlertNotificationTarget(type), id: target.id })}
                          options={ALERT_NOTIFICATION_TARGET_TYPES.map((type) => ({ value: type, label: t(`alertsTargetType_${type}`) }))}
                        />
                      </Form.Item>
                      <Form.Item label={t('alertsTargetEvents')}>
                        <Select mode="multiple" allowClear style={{ minWidth: 260 }} placeholder={t('alertsAllEvents')} value={target.events ?? []} onChange={(events) => updateNotificationTarget(index, { events })} options={WEBHOOK_EVENT_KEYS.map((key) => ({ value: key, label: t(`alertsEvent_${key}`) }))} />
                      </Form.Item>
                      <Form.Item label={t('alertsTargetSeverities')}>
                        <Select mode="multiple" allowClear style={{ minWidth: 220 }} placeholder={t('alertsAllSeverities')} value={target.severities ?? []} onChange={(severities) => updateNotificationTarget(index, { severities })} options={ALERT_SEVERITY_KEYS.map((key) => ({ value: key, label: t(`alertsSeverity_${key}`) }))} />
                      </Form.Item>
                      <Form.Item label={t('alertsTargetOwners')}>
                        <Select mode="tags" tokenSeparators={[',', ' ']} style={{ minWidth: 320 }} placeholder={t('alertsTargetOwnersPlaceholder')} value={target.ownerUuids ?? []} onChange={(ownerUuids) => updateNotificationTarget(index, { ownerUuids })} />
                      </Form.Item>
                      {(target.type === 'qq_official' || target.type === 'wechat_official' || target.type === 'wecom_app') && (
                        <Form.Item label={t('alertsApiBaseOptional')}>
                          <Input value={target.baseUrl ?? ''} onChange={(event) => updateNotificationTarget(index, { baseUrl: event.target.value })} />
                        </Form.Item>
                      )}
                      {(target.type === 'qq_official' || target.type === 'wechat_official') && (
                        <Form.Item label={t('alertsTokenUrlOptional')}>
                          <Input value={target.tokenUrl ?? ''} onChange={(event) => updateNotificationTarget(index, { tokenUrl: event.target.value })} />
                        </Form.Item>
                      )}
                      {target.type === 'email' && (
                        <>
                          <Form.Item label={t('alertsSmtpSecurity')}>
                            <Select style={{ minWidth: 130 }} value={target.smtpSecurity ?? 'starttls'} onChange={(smtpSecurity) => updateNotificationTarget(index, { smtpSecurity })} options={['none', 'starttls', 'ssl'].map((value) => ({ value, label: value }))} />
                          </Form.Item>
                          <Form.Item label={t('alertsMailCc')}>
                            <Select mode="tags" tokenSeparators={[',', ';', ' ']} style={{ minWidth: 320 }} value={target.mailCc ?? []} onChange={(mailCc) => updateNotificationTarget(index, { mailCc })} />
                          </Form.Item>
                          <Form.Item label={t('alertsMailSubjectPrefix')}>
                            <Input value={target.subjectPrefix ?? '[WebAE]'} onChange={(event) => updateNotificationTarget(index, { subjectPrefix: event.target.value })} />
                          </Form.Item>
                        </>
                      )}
                    </Space>
                  ),
                }]}
              />

              {target.type === 'qq_official' && (
                <>
                  <Alert type="warning" message={t('alertsQqOfficialHint')} showIcon />
                  <Space wrap align="start">
                    <Form.Item label="AppID">
                      <Input
                        value={target.appId ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { appId: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label="ClientSecret">
                      <Input.Password
                        value={secretInputValue(target.appSecret)}
                        placeholder={secretInputPlaceholder(target.appSecret, target.appSecretConfigured)}
                        onChange={(event) =>
                          updateNotificationTarget(index, { appSecret: event.target.value })
                        }
                      />
                    </Form.Item>
                    <Form.Item label={t('alertsQqTargetType')}>
                      <Select
                        style={{ minWidth: 140 }}
                        value={target.targetType ?? 'group'}
                        onChange={(targetType) => updateNotificationTarget(index, { targetType })}
                        options={['group', 'c2c', 'channel'].map((value) => ({
                          value,
                          label: t(`alertsQqTargetType_${value}`),
                        }))}
                      />
                    </Form.Item>
                    <Form.Item label={t('alertsTargetIdValue')}>
                      <Input
                        value={target.targetId ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { targetId: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label=" ">
                      <Button
                        icon={<SearchOutlined />}
                        disabled={!canEdit || !serverFeatureEnabled}
                        onClick={() => setQqProbeIndex(index)}
                      >
                        {t('alertsQqProbeOpen')}
                      </Button>
                    </Form.Item>
                  </Space>
                </>
              )}

              {target.type === 'wechat_official' && (
                <>
                  <Alert type="warning" message={t('alertsWechatOfficialHint')} showIcon />
                  <Space wrap align="start">
                    <Form.Item label="AppID">
                      <Input
                        value={target.appId ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { appId: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label="AppSecret">
                      <Input.Password
                        value={secretInputValue(target.appSecret)}
                        placeholder={secretInputPlaceholder(target.appSecret, target.appSecretConfigured)}
                        onChange={(event) =>
                          updateNotificationTarget(index, { appSecret: event.target.value })
                        }
                      />
                    </Form.Item>
                    <Form.Item label="OpenID">
                      <Input
                        value={target.targetId ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { targetId: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label={t('alertsWechatMode')}>
                      <Select
                        style={{ minWidth: 180 }}
                        value={target.mode ?? 'customer_service'}
                        onChange={(mode) => updateNotificationTarget(index, { mode })}
                        options={['customer_service', 'template'].map((value) => ({
                          value,
                          label: t(`alertsWechatMode_${value}`),
                        }))}
                      />
                    </Form.Item>
                    {target.mode === 'template' && (
                      <>
                        <Form.Item label="Template ID">
                          <Input
                            value={target.templateId ?? ''}
                            onChange={(event) =>
                              updateNotificationTarget(index, { templateId: event.target.value })
                            }
                          />
                        </Form.Item>
                        <Form.Item label={t('alertsTemplateUrlOptional')}>
                          <Input
                            value={target.templateUrl ?? ''}
                            onChange={(event) =>
                              updateNotificationTarget(index, { templateUrl: event.target.value })
                            }
                          />
                        </Form.Item>
                      </>
                    )}
                  </Space>
                </>
              )}

              {target.type === 'email' && (
                <Space wrap align="start">
                  <Form.Item label={t('alertsSmtpHost')}>
                    <Input
                      value={target.smtpHost ?? ''}
                      onChange={(event) => updateNotificationTarget(index, { smtpHost: event.target.value })}
                    />
                  </Form.Item>
                  <Form.Item label={t('alertsSmtpPort')}>
                    <InputNumber
                      min={1}
                      max={65535}
                      value={target.smtpPort ?? 587}
                      onChange={(smtpPort) => updateNotificationTarget(index, { smtpPort: smtpPort ?? 587 })}
                    />
                  </Form.Item>
                  <Form.Item label={t('alertsSmtpUsername')}>
                    <Input
                      value={target.smtpUsername ?? ''}
                      onChange={(event) =>
                        updateNotificationTarget(index, { smtpUsername: event.target.value })
                      }
                    />
                  </Form.Item>
                  <Form.Item label={t('alertsSmtpPassword')}>
                    <Input.Password
                      value={secretInputValue(target.smtpPassword)}
                      placeholder={secretInputPlaceholder(target.smtpPassword, target.smtpPasswordConfigured)}
                      onChange={(event) =>
                        updateNotificationTarget(index, { smtpPassword: event.target.value })
                      }
                    />
                  </Form.Item>
                  <Form.Item label={t('alertsMailFrom')}>
                    <Input
                      value={target.mailFrom ?? ''}
                      onChange={(event) => updateNotificationTarget(index, { mailFrom: event.target.value })}
                    />
                  </Form.Item>
                  <Form.Item label={t('alertsMailTo')}>
                    <Select
                      mode="tags"
                      tokenSeparators={[',', ';', ' ']}
                      style={{ minWidth: 320 }}
                      value={target.mailTo ?? []}
                      onChange={(mailTo) => updateNotificationTarget(index, { mailTo })}
                    />
                  </Form.Item>
                </Space>
              )}

              {target.type === 'wecom_bot' && (
                <>
                  <Alert type="info" message={t('alertsWecomBotHint')} showIcon />
                  <Form.Item label={t('alertsWebhookUrl')}>
                    <Input.Password
                      value={secretInputValue(target.url)}
                      placeholder={secretInputPlaceholder(target.url, target.urlConfigured)}
                      onChange={(event) => updateNotificationTarget(index, { url: event.target.value })}
                    />
                  </Form.Item>
                </>
              )}

              {target.type === 'wecom_app' && (
                <>
                  <Alert type="info" message={t('alertsWecomAppHint')} showIcon />
                  <Space wrap align="start">
                    <Form.Item label="CorpID">
                      <Input
                        value={target.corpId ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { corpId: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label="CorpSecret">
                      <Input.Password
                        value={secretInputValue(target.corpSecret)}
                        placeholder={secretInputPlaceholder(target.corpSecret, target.corpSecretConfigured)}
                        onChange={(event) =>
                          updateNotificationTarget(index, { corpSecret: event.target.value })
                        }
                      />
                    </Form.Item>
                    <Form.Item label="AgentID">
                      <InputNumber
                        min={1}
                        value={target.agentId ?? 0}
                        onChange={(agentId) => updateNotificationTarget(index, { agentId: agentId ?? 0 })}
                      />
                    </Form.Item>
                    <Form.Item label="ToUser">
                      <Input
                        value={target.toUser ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { toUser: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label="ToParty">
                      <Input
                        value={target.toParty ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { toParty: event.target.value })}
                      />
                    </Form.Item>
                    <Form.Item label="ToTag">
                      <Input
                        value={target.toTag ?? ''}
                        onChange={(event) => updateNotificationTarget(index, { toTag: event.target.value })}
                      />
                    </Form.Item>
                  </Space>
                </>
              )}
            </Space>
          </Card>
          );
        })}

        <Collapse
          size="small"
          items={[{
            key: 'delivery',
            label: t('alertsDeliveryAdvanced'),
            children: (
          <Space wrap align="start">
            <Form.Item label={t('alertsMaxDeliveries')}>
              <InputNumber
                min={1}
                max={64}
                value={draft.notificationMaxDeliveriesPerAlert ?? 16}
                onChange={(notificationMaxDeliveriesPerAlert) =>
                  updateDraft({ notificationMaxDeliveriesPerAlert: notificationMaxDeliveriesPerAlert ?? 16 })
                }
              />
            </Form.Item>
            <Form.Item label={t('alertsRetryAttempts')}>
              <InputNumber
                min={1}
                max={5}
                value={draft.notificationRetryMaxAttempts ?? 3}
                onChange={(notificationRetryMaxAttempts) =>
                  updateDraft({ notificationRetryMaxAttempts: notificationRetryMaxAttempts ?? 3 })
                }
              />
            </Form.Item>
            <Form.Item label={t('alertsConnectTimeout')}>
              <InputNumber
                min={500}
                max={15000}
                value={draft.notificationConnectTimeoutMs ?? 3000}
                onChange={(notificationConnectTimeoutMs) =>
                  updateDraft({ notificationConnectTimeoutMs: notificationConnectTimeoutMs ?? 3000 })
                }
                addonAfter="ms"
              />
            </Form.Item>
            <Form.Item label={t('alertsReadTimeout')}>
              <InputNumber
                min={500}
                max={30000}
                value={draft.notificationReadTimeoutMs ?? 5000}
                onChange={(notificationReadTimeoutMs) =>
                  updateDraft({ notificationReadTimeoutMs: notificationReadTimeoutMs ?? 5000 })
                }
                addonAfter="ms"
              />
            </Form.Item>
            <Form.Item label={t('alertsCircuitFailures')}>
              <InputNumber
                min={1}
                max={20}
                value={draft.notificationCircuitBreakFailures ?? 5}
                onChange={(notificationCircuitBreakFailures) =>
                  updateDraft({ notificationCircuitBreakFailures: notificationCircuitBreakFailures ?? 5 })
                }
              />
            </Form.Item>
            <Form.Item label={t('alertsCircuitSeconds')}>
              <InputNumber
                min={10}
                max={3600}
                value={draft.notificationCircuitBreakSeconds ?? 60}
                onChange={(notificationCircuitBreakSeconds) =>
                  updateDraft({ notificationCircuitBreakSeconds: notificationCircuitBreakSeconds ?? 60 })
                }
                addonAfter="s"
              />
            </Form.Item>
          </Space>
            ),
          }]}
        />

        <Title level={5}>{t('alertsWebhooks')}</Title>
        <Alert type="info" message={t('alertsWebhooksDesc')} showIcon style={{ marginBottom: 8 }} />
        <Table
          size="small"
          bordered
          pagination={false}
          rowKey={(r) => r.id}
          dataSource={draft.webhooks ?? []}
          locale={{ emptyText: t('alertsNoRules') }}
          columns={[
            {
              title: t('alertsWebhookId'),
              dataIndex: 'id',
              render: (_: unknown, row: WebhookRuleDto, index: number) => (
                <Input
                  value={draft.webhooks?.[index]?.id ?? ''}
                  onChange={(e) => updateWebhookRow(index, { id: e.target.value })}
                />
              ),
            },
            {
              title: t('alertsWebhookUrl'),
              dataIndex: 'url',
              render: (_: unknown, row: WebhookRuleDto, index: number) => {
                const current = draft.webhooks?.[index];
                const placeholder =
                  current?.urlConfigured && current.url?.startsWith('***')
                    ? current.url
                    : t('alertsWebhookUrlPlaceholder');
                return (
                  <Input
                    value={current?.url ?? ''}
                    placeholder={placeholder}
                    onChange={(e) => updateWebhookRow(index, { url: e.target.value })}
                  />
                );
              },
            },
            {
              title: t('alertsWebhookEvents'),
              dataIndex: 'events',
              render: (_: unknown, __: WebhookRuleDto, index: number) => (
                <Select
                  mode="multiple"
                  style={{ minWidth: 180 }}
                  value={draft.webhooks?.[index]?.events ?? []}
                  onChange={(v) => updateWebhookRow(index, { events: v })}
                  options={WEBHOOK_EVENT_KEYS.map((k) => ({
                    value: k,
                    label: t(`alertsEvent_${k}`),
                  }))}
                />
              ),
            },
            {
              title: t('alertsWebhookMention'),
              dataIndex: 'mention',
              render: (_: unknown, __: WebhookRuleDto, index: number) => (
                <Input
                  value={draft.webhooks?.[index]?.mention ?? ''}
                  placeholder={t('alertsWebhookMentionPlaceholder')}
                  onChange={(e) => updateWebhookRow(index, { mention: e.target.value })}
                />
              ),
            },
            {
              title: t('alertsEnabled'),
              dataIndex: 'enabled',
              width: 72,
              render: (_: unknown, row: WebhookRuleDto, index: number) => (
                <Switch
                  checked={!!draft.webhooks?.[index]?.enabled}
                  onChange={(v) => updateWebhookRow(index, { enabled: v })}
                />
              ),
            },
            {
              title: '',
              key: 'actions',
              width: 118,
              render: (_: unknown, __: WebhookRuleDto, index: number) => {
                const current = draft.webhooks?.[index];
                const saved = rules.webhooks?.find((hook) => hook.id === current?.id && hook.urlConfigured);
                const webhookIsSaved = !!saved && JSON.stringify(saved) === JSON.stringify(current);
                return (
                  <Space size="small">
                    <Tooltip title={!serverFeatureEnabled ? t('alertsTestServerDisabled') : webhookIsSaved ? t('alertsTestSend') : t('alertsTestSaveFirst')}>
                      <span>
                        <Button
                          size="small"
                          icon={<SendOutlined />}
                          loading={testingRoute === `webhook:${current?.id}`}
                          disabled={!webhookIsSaved || !serverFeatureEnabled || !current?.id}
                          aria-label={t('alertsTestSend')}
                          onClick={() => void sendDeliveryTest('webhook', current?.id ?? '')}
                        />
                      </span>
                    </Tooltip>
                    <Tooltip title={t('presetDelete')}>
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => removeWebhookRow(index)}
                        aria-label={t('presetDelete')}
                      />
                    </Tooltip>
                  </Space>
                );
              },
            },
          ]}
        />
        <Button type="dashed" icon={<PlusOutlined />} onClick={addWebhookRow} block>
          {t('alertsAddWebhook')}
        </Button>

        <Title level={5}>{t('alertsInventoryRules')}</Title>
        <Table
          size="small"
          bordered
          pagination={false}
          rowKey={(_, i) => String(i)}
          dataSource={draft.inventoryThresholds ?? []}
          locale={{ emptyText: t('alertsNoRules') }}
          columns={[
            {
              title: t('alertsRuleLabel'),
              dataIndex: 'label',
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <Input
                  value={draft.inventoryThresholds?.[index]?.label ?? ''}
                  onChange={(e) => updateInventoryRow(index, { label: e.target.value })}
                  placeholder={t('alertsRuleLabelPlaceholder')}
                />
              ),
            },
            {
              title: t('alertsRuleItemId'),
              dataIndex: 'itemId',
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <Input
                  value={draft.inventoryThresholds?.[index]?.itemId ?? ''}
                  onChange={(e) => updateInventoryRow(index, { itemId: e.target.value })}
                  placeholder="minecraft:iron_ingot"
                />
              ),
            },
            {
              title: t('alertsRuleFluid'),
              dataIndex: 'fluidName',
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <Input
                  value={draft.inventoryThresholds?.[index]?.fluidName ?? ''}
                  onChange={(e) => updateInventoryRow(index, { fluidName: e.target.value })}
                  placeholder="water"
                />
              ),
            },
            {
              title: t('alertsRuleMin'),
              dataIndex: 'minAmount',
              width: 110,
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <InputNumber
                  min={0}
                  style={{ width: '100%' }}
                  value={draft.inventoryThresholds?.[index]?.minAmount ?? 0}
                  onChange={(v) => updateInventoryRow(index, { minAmount: v ?? 0 })}
                />
              ),
            },
            {
              title: t('alertsRuleNetwork'),
              dataIndex: 'networkId',
              width: 100,
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <InputNumber
                  min={-1}
                  style={{ width: '100%' }}
                  value={draft.inventoryThresholds?.[index]?.networkId ?? -1}
                  onChange={(v) => updateInventoryRow(index, { networkId: v ?? -1 })}
                />
              ),
            },
            {
              title: '',
              key: 'actions',
              width: 48,
              render: (_: unknown, __: InventoryRuleRow, index: number) => (
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeInventoryRow(index)}
                  aria-label={t('presetDelete')}
                />
              ),
            },
          ]}
        />
        <Button type="dashed" icon={<PlusOutlined />} onClick={addInventoryRow} block>
          {t('alertsAddRule')}
        </Button>

        <Title level={5}>{t('alertsAutomationRules')}</Title>
        <Table
          size="small"
          bordered
          pagination={false}
          rowKey={(r) => r.id}
          dataSource={draft.automationRules ?? []}
          locale={{ emptyText: t('alertsNoRules') }}
          scroll={{ x: 900 }}
          columns={[
            {
              title: t('alertsRuleItemId'),
              dataIndex: 'itemId',
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Input
                  value={draft.automationRules?.[index]?.itemId ?? ''}
                  onChange={(e) => updateAutomationRow(index, { itemId: e.target.value })}
                />
              ),
            },
            {
              title: t('alertsAutomationThreshold'),
              dataIndex: 'threshold',
              width: 100,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <InputNumber
                  min={1}
                  style={{ width: '100%' }}
                  value={draft.automationRules?.[index]?.threshold ?? 0}
                  onChange={(v) => updateAutomationRow(index, { threshold: v ?? 0 })}
                />
              ),
            },
            {
              title: t('alertsAutomationCraftAmount'),
              dataIndex: 'craftAmount',
              width: 100,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <InputNumber
                  min={0}
                  style={{ width: '100%' }}
                  value={draft.automationRules?.[index]?.craftAmount ?? 0}
                  onChange={(v) => updateAutomationRow(index, { craftAmount: v ?? 0 })}
                />
              ),
            },
            {
              title: t('alertsAutomationPatternId'),
              dataIndex: 'patternId',
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Input
                  value={draft.automationRules?.[index]?.patternId ?? ''}
                  onChange={(e) => updateAutomationRow(index, { patternId: e.target.value })}
                />
              ),
            },
            {
              title: t('alertsAutomationCpu'),
              dataIndex: 'cpuName',
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Input
                  value={draft.automationRules?.[index]?.cpuName ?? ''}
                  onChange={(e) => updateAutomationRow(index, { cpuName: e.target.value })}
                />
              ),
            },
            {
              title: t('alertsRuleNetwork'),
              dataIndex: 'networkId',
              width: 90,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <InputNumber
                  min={-1}
                  style={{ width: '100%' }}
                  value={draft.automationRules?.[index]?.networkId ?? -1}
                  onChange={(v) => updateAutomationRow(index, { networkId: v ?? -1 })}
                />
              ),
            },
            {
              title: t('alertsAutomationCooldown'),
              dataIndex: 'cooldownSeconds',
              width: 90,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <InputNumber
                  min={1}
                  style={{ width: '100%' }}
                  value={draft.automationRules?.[index]?.cooldownSeconds ?? 300}
                  onChange={(v) => updateAutomationRow(index, { cooldownSeconds: v ?? 300 })}
                />
              ),
            },
            {
              title: t('alertsAutomationRequireIdle'),
              dataIndex: 'requireCpuIdle',
              width: 72,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Switch
                  checked={draft.automationRules?.[index]?.requireCpuIdle !== false}
                  onChange={(v) => updateAutomationRow(index, { requireCpuIdle: v })}
                />
              ),
            },
            {
              title: t('alertsEnabled'),
              dataIndex: 'enabled',
              width: 72,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Switch
                  checked={!!draft.automationRules?.[index]?.enabled}
                  onChange={(v) => updateAutomationRow(index, { enabled: v })}
                />
              ),
            },
            {
              title: '',
              key: 'actions',
              width: 48,
              render: (_: unknown, __: AutomationRuleRow, index: number) => (
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeAutomationRow(index)}
                  aria-label={t('presetDelete')}
                />
              ),
            },
          ]}
        />
        <Button type="dashed" icon={<PlusOutlined />} onClick={addAutomationRow} block>
          {t('alertsAddAutomationRule')}
        </Button>

        <Space>
          <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
            {t('alertsSaveRules')}
          </Button>
          <Button
            onClick={() => {
              if (rules) setDraft(cloneRules(rules));
            }}
            disabled={saving}
          >
            {t('alertsDiscardChanges')}
          </Button>
        </Space>
      </Space>
    </Form>
  );
}
