import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  Typography,
  Spin,
} from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { AlertsResponse, WebAlertsConfigDto, WebhookRuleDto, AutomationRuleDto } from '@/types/dto';

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

function cloneRules(rules: WebAlertsConfigDto): WebAlertsConfigDto {
  return {
    ...rules,
    inventoryThresholds: (rules.inventoryThresholds ?? []).map((r) => ({ ...r })),
    webhooks: (rules.webhooks ?? []).map((w) => ({
      ...w,
      events: w.events ? [...w.events] : [],
    })),
    automationRules: (rules.automationRules ?? []).map((r) => ({ ...r })),
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
  const [rules, setRules] = useState<WebAlertsConfigDto | null>(null);
  const [draft, setDraft] = useState<WebAlertsConfigDto | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await getApiClient().get<AlertsResponse>('/api/alerts');
      if (r.rules) {
        setRules(r.rules);
        setDraft(cloneRules(r.rules));
      }
      setCanEdit(!!r.canEditRules);
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
      if (r.rules) {
        setRules(r.rules);
        setDraft(cloneRules(r.rules));
      }
      notify(t('alertsSaveSuccess'), 'success');
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('alertsSaveFailed');
      notify(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Spin />;
  }

  if (!rules || !draft) {
    return <Alert type="warning" message={t('alertsLoadFailed')} showIcon />;
  }

  if (!canEdit) {
    return (
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
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
        </Descriptions>
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
      <Alert type="info" message={t('alertsSettingsDesc')} showIcon style={{ marginBottom: 16 }} />
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Form.Item label={t('alertsEnabled')}>
          <Switch checked={draft.enabled} onChange={(v) => updateDraft({ enabled: v })} />
        </Form.Item>
        <Form.Item label={t('alertsPollInterval')}>
          <InputNumber
            min={1}
            max={300}
            value={draft.pollIntervalSeconds}
            onChange={(v) => updateDraft({ pollIntervalSeconds: v ?? 10 })}
            addonAfter="s"
          />
        </Form.Item>
        <Form.Item label={t('alertsCpuStuck')}>
          <InputNumber
            min={1}
            max={120}
            value={draft.cpuStuckMinutes}
            onChange={(v) => updateDraft({ cpuStuckMinutes: v ?? 5 })}
            addonAfter={t('minutesUnit')}
          />
        </Form.Item>
        <Form.Item label={t('alertsGtErrors')}>
          <Switch
            checked={draft.gtErrorEnabled}
            onChange={(v) => updateDraft({ gtErrorEnabled: v })}
          />
        </Form.Item>
        <Form.Item label={t('alertsOrderComplete')}>
          <Switch
            checked={draft.orderCompleteEnabled}
            onChange={(v) => updateDraft({ orderCompleteEnabled: v })}
          />
        </Form.Item>
        <Form.Item label={t('alertsChannelPercent')}>
          <InputNumber
            min={1}
            max={100}
            value={draft.channelThresholdPercent}
            onChange={(v) => updateDraft({ channelThresholdPercent: v ?? 90 })}
            addonAfter="%"
          />
        </Form.Item>
        <Form.Item label={t('alertsChannelAbsolute')}>
          <InputNumber
            min={1}
            max={128}
            value={draft.channelThresholdAbsolute}
            onChange={(v) => updateDraft({ channelThresholdAbsolute: v ?? 28 })}
          />
        </Form.Item>

        <Title level={5}>{t('alertsServerTps')}</Title>
        <Form.Item label={t('alertsServerTpsEnabled')}>
          <Switch
            checked={!!draft.serverTpsBelowEnabled}
            onChange={(v) => updateDraft({ serverTpsBelowEnabled: v })}
          />
        </Form.Item>
        <Form.Item label={t('alertsServerTpsThreshold')}>
          <InputNumber
            min={1}
            max={20}
            step={0.5}
            disabled={!draft.serverTpsBelowEnabled}
            value={draft.serverTpsThreshold ?? 15}
            onChange={(v) => updateDraft({ serverTpsThreshold: v ?? 15 })}
          />
        </Form.Item>
        <Form.Item label={t('alertsServerTpsDuration')}>
          <InputNumber
            min={10}
            max={600}
            disabled={!draft.serverTpsBelowEnabled}
            value={draft.serverTpsDurationSeconds ?? 60}
            onChange={(v) => updateDraft({ serverTpsDurationSeconds: v ?? 60 })}
            addonAfter="s"
          />
        </Form.Item>

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
              width: 48,
              render: (_: unknown, __: WebhookRuleDto, index: number) => (
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeWebhookRow(index)}
                  aria-label={t('presetDelete')}
                />
              ),
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
