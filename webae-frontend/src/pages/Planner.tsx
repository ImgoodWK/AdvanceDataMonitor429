import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Empty,
  Form,
  Input,
  List,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { DownloadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { formatTime } from '@/utils/format';
import type {
  PlanEntryDto,
  PlannerExportFlowResponse,
  PlannerPlansResponse,
} from '@/types/dto';

const { Text, Paragraph } = Typography;

export function PlannerPage() {
  const { t } = useI18n();
  const { notify, selectedNetworks } = useAppContext();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [plans, setPlans] = useState<PlanEntryDto[]>([]);
  const [newTitle, setNewTitle] = useState('');
  const [newText, setNewText] = useState('');
  const [exportItem, setExportItem] = useState('');
  const [exportAmount, setExportAmount] = useState('1024');
  const [exportFormat, setExportFormat] = useState<'gtnh-flow-v1' | 'factory-flow-v1'>(
    'factory-flow-v1'
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<PlannerPlansResponse>('/api/planner/plans');
      setPlans(data.plans || []);
    } catch {
      setPlans([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const createPlan = useCallback(async () => {
    const title = newTitle.trim();
    const rawText = newText.trim();
    if (!title && !rawText) {
      notify(t('plannerCreateRequired'), 'warning');
      return;
    }
    setSaving(true);
    try {
      await getApiClient().post<{ success: boolean; plan?: PlanEntryDto }>('/api/planner/plans', {
        title,
        rawText: rawText || title,
      });
      setNewTitle('');
      setNewText('');
      notify(t('plannerCreatedOk'), 'success');
      await load();
    } catch (e) {
      notify((e as Error).message || t('plannerCreateFailed'), 'error');
    } finally {
      setSaving(false);
    }
  }, [newTitle, newText, load, notify, t]);

  const toggleComplete = useCallback(
    async (plan: PlanEntryDto, completed: boolean) => {
      setSaving(true);
      try {
        await getApiClient().patch(`/api/planner/plans/${plan.id}`, { completed });
        await load();
      } catch (e) {
        notify((e as Error).message || t('plannerUpdateFailed'), 'error');
      } finally {
        setSaving(false);
      }
    },
    [load, notify, t]
  );

  const deletePlan = useCallback(
    async (plan: PlanEntryDto) => {
      setSaving(true);
      try {
        await getApiClient().delete(`/api/planner/plans/${plan.id}`);
        notify(t('plannerDeletedOk'), 'success');
        await load();
      } catch (e) {
        notify((e as Error).message || t('plannerDeleteFailed'), 'error');
      } finally {
        setSaving(false);
      }
    },
    [load, notify, t]
  );

  const exportFlow = useCallback(async () => {
    const itemId = exportItem.trim();
    if (!itemId) {
      notify(t('plannerExportItemRequired'), 'warning');
      return;
    }
    const amount = parseInt(exportAmount, 10) || 1;
    const networkId = selectedNetworks[0] ?? 0;
    setExporting(true);
    try {
      const data = await getApiClient().post<PlannerExportFlowResponse>('/api/planner/export-flow', {
        networkId,
        format: exportFormat,
        roots: [{ itemId, amount }],
      });
      if (!data.success || !data.export) {
        notify(data.message || t('plannerExportFailed'), 'error');
        return;
      }
      const blob = new Blob([JSON.stringify(data.export, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `webae-flow-${itemId.replace(/[^a-z0-9_-]/gi, '_')}.json`;
      a.click();
      URL.revokeObjectURL(url);
      notify(t('plannerExportOk'), 'success');
    } catch (e) {
      notify((e as Error).message || t('plannerExportFailed'), 'error');
    } finally {
      setExporting(false);
    }
  }, [exportItem, exportAmount, exportFormat, selectedNetworks, notify, t]);

  return (
    <PageShell title={t('plannerPage')} description={t('plannerPageDescRw')}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Card title={t('plannerNewTitle')}>
          <Form layout="vertical" onFinish={() => void createPlan()}>
            <Form.Item label={t('plannerNewTitleLabel')}>
              <Input value={newTitle} onChange={(e) => setNewTitle(e.target.value)} />
            </Form.Item>
            <Form.Item label={t('plannerNewTextLabel')}>
              <Input.TextArea rows={2} value={newText} onChange={(e) => setNewText(e.target.value)} />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={saving}>
              {t('plannerCreateBtn')}
            </Button>
          </Form>
        </Card>

        <Card title={t('plannerExportTitle')}>
          <Space wrap style={{ width: '100%' }}>
            <Input
              style={{ minWidth: 220 }}
              placeholder={t('plannerExportItemPlaceholder')}
              value={exportItem}
              onChange={(e) => setExportItem(e.target.value)}
            />
            <Input
              style={{ width: 120 }}
              placeholder="1024"
              value={exportAmount}
              onChange={(e) => setExportAmount(e.target.value)}
            />
            <Select
              style={{ width: 180 }}
              value={exportFormat}
              onChange={(v) => setExportFormat(v)}
              options={[
                { value: 'factory-flow-v1', label: 'Factory Flow v1' },
                { value: 'gtnh-flow-v1', label: 'gtnh-flow v1' },
              ]}
            />
            <Button icon={<DownloadOutlined />} loading={exporting} onClick={() => void exportFlow()}>
              {t('plannerExportBtn')}
            </Button>
          </Space>
          <Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
            {t('plannerExportHint')}
          </Paragraph>
        </Card>

        <Card extra={<a onClick={() => void load()} role="button" tabIndex={0}><ReloadOutlined /> {t('refresh')}</a>}>
          <Spin spinning={loading || saving}>
            {plans.length === 0 ? (
              <Empty description={t('plannerEmpty')} />
            ) : (
              <List
                dataSource={plans}
                renderItem={(plan) => (
                  <List.Item
                    actions={[
                      <Button key="del" type="link" danger size="small" onClick={() => void deletePlan(plan)}>
                        {t('delete')}
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={
                        <Checkbox
                          checked={plan.completed}
                          onChange={(e) => void toggleComplete(plan, e.target.checked)}
                          aria-label={t('plannerMarkDone')}
                        />
                      }
                      title={
                        <SpaceInline>
                          <Text strong>#{plan.id}</Text>
                          <Text delete={plan.completed}>{plan.title}</Text>
                          <Tag color={plan.completed ? 'default' : 'blue'}>
                            {plan.completed ? t('plannerDone') : t('plannerOpen')}
                          </Tag>
                        </SpaceInline>
                      }
                      description={
                        <>
                          <Paragraph type="secondary" style={{ marginBottom: 4 }}>
                            {plan.rawText}
                          </Paragraph>
                          <Text type="secondary">
                            {t('plannerCreated')}: {formatTime(plan.createdAt)}
                            {plan.dueAt > 0 ? ` · ${t('plannerDue')}: ${formatTime(plan.dueAt)}` : ''}
                          </Text>
                        </>
                      }
                    />
                  </List.Item>
                )}
              />
            )}
          </Spin>
        </Card>
      </Space>
    </PageShell>
  );
}

function SpaceInline({ children }: { children: React.ReactNode }) {
  return <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>{children}</span>;
}
