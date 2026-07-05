import { useCallback, useEffect, useState } from 'react';
import { Card, Empty, List, Spin, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { useI18n } from '@/i18n';
import { formatTime } from '@/utils/format';
import type { PlanEntryDto, PlannerPlansResponse } from '@/types/dto';

const { Text, Paragraph } = Typography;

export function PlannerPage() {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [plans, setPlans] = useState<PlanEntryDto[]>([]);

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

  return (
    <PageShell title={t('plannerPage')} description={t('plannerPageDesc')}>
      <Card extra={<a onClick={() => void load()} role="button" tabIndex={0}><ReloadOutlined /> {t('refresh')}</a>}>
        <Spin spinning={loading}>
          {plans.length === 0 ? (
            <Empty description={t('plannerEmpty')} />
          ) : (
            <List
              dataSource={plans}
              renderItem={(plan) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <SpaceInline>
                        <Text strong>#{plan.id}</Text>
                        <Text>{plan.title}</Text>
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
    </PageShell>
  );
}

function SpaceInline({ children }: { children: React.ReactNode }) {
  return <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>{children}</span>;
}
