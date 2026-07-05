import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Space,
  Switch,
  Table,
  Typography,
  Spin,
} from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { AlertsResponse, WebAlertsConfigDto } from '@/types/dto';

const { Text, Title } = Typography;

type InventoryRuleRow = NonNullable<WebAlertsConfigDto['inventoryThresholds']>[number];

function emptyRule(): InventoryRuleRow {
  return {
    itemId: '',
    fluidName: '',
    minAmount: 0,
    networkId: -1,
    label: '',
  };
}

function cloneRules(rules: WebAlertsConfigDto): WebAlertsConfigDto {
  return {
    ...rules,
    inventoryThresholds: (rules.inventoryThresholds ?? []).map((r) => ({ ...r })),
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
        </Descriptions>
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
