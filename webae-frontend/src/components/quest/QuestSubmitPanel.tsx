import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Modal, Progress, Space, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type {
  QuestAnalysisDto,
  QuestChainPlanDto,
  QuestChainSubmitResultDto,
  QuestCraftJobDto,
  QuestSubmitResultDto,
} from '@/types/dto';
import { fluidIconId } from '@/utils/icon';

const { Text } = Typography;

interface QuestSubmitPanelProps {
  questId: string;
  networkId: number;
  canSubmit?: boolean;
  chainEnabled?: boolean;
  onBeforeSubmit?: () => Promise<void>;
  onSubmitted?: () => void;
}

export function QuestSubmitPanel({
  questId,
  networkId,
  canSubmit = true,
  chainEnabled,
  onBeforeSubmit,
  onSubmitted,
}: QuestSubmitPanelProps) {
  const { t } = useI18n();
  const [analysis, setAnalysis] = useState<QuestAnalysisDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [job, setJob] = useState<QuestCraftJobDto | null>(null);
  const [chainJob, setChainJob] = useState<QuestChainSubmitResultDto | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [dryRunResult, setDryRunResult] = useState<QuestSubmitResultDto | null>(null);
  const [chainPlanOpen, setChainPlanOpen] = useState(false);
  const [chainPlan, setChainPlan] = useState<QuestChainPlanDto | null>(null);

  const loadAnalysis = useCallback(async () => {
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().get<{ success: boolean; analysis: QuestAnalysisDto }>(
        `/api/quests/${questId}/analysis?network=${networkId}`
      );
      setAnalysis(res.analysis ?? null);
    } catch {
      setAnalysis(null);
    }
  }, [questId, networkId, onBeforeSubmit]);

  useEffect(() => {
    void loadAnalysis();
  }, [loadAnalysis]);

  const runDryRun = async () => {
    setLoading(true);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().post<{ success: boolean; submit: QuestSubmitResultDto }>(
        `/api/quests/${questId}/submit`,
        { networkId, dryRun: true }
      );
      setDryRunResult(res.submit ?? null);
      setConfirmOpen(true);
    } finally {
      setLoading(false);
    }
  };

  const confirmSubmit = async () => {
    setLoading(true);
    setConfirmOpen(false);
    try {
      await getApiClient().post(`/api/quests/${questId}/submit`, { networkId, dryRun: false });
      onSubmitted?.();
      await loadAnalysis();
    } finally {
      setLoading(false);
    }
  };

  const submitCraft = async () => {
    setLoading(true);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().post<{ success: boolean; job: QuestCraftJobDto }>(
        `/api/quests/${questId}/submit-craft`,
        { networkId }
      );
      setJob(res.job ?? null);
    } finally {
      setLoading(false);
    }
  };

  const openChainPlan = async () => {
    setLoading(true);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().get<{ success: boolean; plan: QuestChainPlanDto }>(
        `/api/quests/${questId}/chain-plan?network=${networkId}`
      );
      setChainPlan(res.plan ?? null);
      setChainPlanOpen(true);
    } finally {
      setLoading(false);
    }
  };

  const runChain = async (craftMissing: boolean, skipMissing: boolean) => {
    setLoading(true);
    setChainPlanOpen(false);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().post<{ success: boolean; chain: QuestChainSubmitResultDto }>(
        `/api/quests/${questId}/submit-chain`,
        { networkId, dryRun: false, skipMissing, craftMissing }
      );
      const chain = res.chain;
      if (chain?.jobId && !chain.complete) {
        setChainJob(chain);
      } else {
        onSubmitted?.();
        await loadAnalysis();
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!job || job.complete) return;
    const id = window.setInterval(async () => {
      try {
        const res = await getApiClient().get<{ success: boolean; job: QuestCraftJobDto }>(
          `/api/quests/submit-jobs/${job.jobId}`
        );
        setJob(res.job);
        if (res.job?.complete) {
          onSubmitted?.();
          void loadAnalysis();
        }
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => window.clearInterval(id);
  }, [job, loadAnalysis, onSubmitted]);

  useEffect(() => {
    if (!chainJob || chainJob.complete || !chainJob.jobId) return;
    const id = window.setInterval(async () => {
      try {
        const res = await getApiClient().get<{ success: boolean; chain: QuestChainSubmitResultDto }>(
          `/api/quests/chain-jobs/${chainJob.jobId}`
        );
        setChainJob(res.chain);
        if (res.chain?.complete) {
          onSubmitted?.();
          void loadAnalysis();
        }
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => window.clearInterval(id);
  }, [chainJob, loadAnalysis, onSubmitted]);

  const missingSteps =
    analysis?.steps?.filter(
      (s) => s.webCapable && !s.complete && ((s.missing ?? 0) > 0 || (s.fluidMissing ?? 0) > 0)
    ) ?? [];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {missingSteps.length > 0 ? (
        <Alert
          type="warning"
          message={t('quest.missingMaterials', missingSteps.length)}
          description={
            <div>
              {missingSteps.map((s) => (
                <div
                  key={s.index}
                  style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}
                >
                  {s.registryName ? (
                    <Icon
                      item={{
                        itemId: s.itemId ?? s.registryName,
                        registryName: s.registryName,
                        meta: s.meta,
                      }}
                      size={20}
                    />
                  ) : s.fluidName ? (
                    <Icon id={fluidIconId(s.fluidName)} size={20} />
                  ) : null}
                  <Text type="secondary">
                    {s.registryName || s.fluidName} −{s.missing || s.fluidMissing}
                    {s.craftable > 0 ? ` (${t('quest.craftable')}: ${s.craftable})` : ''}
                  </Text>
                </div>
              ))}
            </div>
          }
        />
      ) : null}
      {job && !job.complete ? (
        <div>
          <Text>{job.message}</Text>
          <Progress
            percent={
              job.ordersTotal ? Math.round(((job.ordersDone ?? 0) / job.ordersTotal) * 100) : 30
            }
            status="active"
          />
        </div>
      ) : null}
      {chainJob && !chainJob.complete ? (
        <div>
          <Text>{chainJob.message}</Text>
          <Progress percent={40} status="active" />
          {chainJob.steps?.map((s) => (
            <div key={s.questId}>
              <Text type="secondary">
                [{s.action}] {s.name}: {s.message}
              </Text>
            </div>
          ))}
        </div>
      ) : null}
      <Space wrap>
        {canSubmit ? (
          <>
            <Button type="primary" loading={loading} onClick={() => void runDryRun()}>
              {t('quest.submit')}
            </Button>
            <Button loading={loading} onClick={() => void submitCraft()}>
              {t('quest.submitCraft')}
            </Button>
          </>
        ) : null}
        {chainEnabled ? (
          <Button loading={loading} onClick={() => void openChainPlan()}>
            {t('quest.chainSubmit')}
          </Button>
        ) : null}
      </Space>
      <Modal
        title={t('quest.confirmSubmit')}
        open={confirmOpen}
        onOk={() => void confirmSubmit()}
        onCancel={() => setConfirmOpen(false)}
        okText={t('quest.confirm')}
        cancelText={t('quest.cancel')}
      >
        {dryRunResult?.steps?.map((s) => (
          <div key={s.index} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            {s.itemId ? <Icon item={{ itemId: s.itemId }} size={24} /> : null}
            <Text>
              {s.itemId ? `${s.itemId} x${s.amount}` : s.message}
            </Text>
          </div>
        ))}
      </Modal>
      <Modal
        title={t('quest.chainPlanTitle')}
        open={chainPlanOpen}
        onCancel={() => setChainPlanOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setChainPlanOpen(false)}>
            {t('quest.cancel')}
          </Button>,
          <Button
            key="skip"
            onClick={() => void runChain(true, true)}
            loading={loading}
          >
            {t('quest.chainSubmitAvailable')}
          </Button>,
          <Button
            key="all"
            type="primary"
            onClick={() => void runChain(true, false)}
            loading={loading}
          >
            {t('quest.chainSubmitAll')}
          </Button>,
        ]}
        width={520}
      >
        <Alert type="info" showIcon message={t('quest.chainPlanHint')} style={{ marginBottom: 12 }} />
        {chainPlan?.steps?.map((s) => (
          <div
            key={s.questId}
            style={{
              marginBottom: 8,
              padding: 8,
              borderRadius: 6,
              border: '1px solid var(--border-color, #334155)',
              opacity: s.skipped ? 0.6 : 1,
            }}
          >
            <Space wrap>
              <Text strong>
                {s.target ? '★ ' : ''}
                {s.name}
              </Text>
              <Text type="secondary">[{s.state}]</Text>
              {s.skipped ? <Text type="secondary">{s.skipReason}</Text> : null}
              {!s.skipped && !s.fullySatisfied ? (
                <Text type="warning">
                  {t('quest.missingKinds', s.missingItemKinds ?? 0)}
                  {s.craftable ? ` · ${t('quest.craftable')}` : ''}
                </Text>
              ) : null}
              {!s.skipped && s.fullySatisfied ? (
                <Text type="success">{t('quest.ready')}</Text>
              ) : null}
            </Space>
            {s.analysis?.steps
              ?.filter((st) => st.webCapable && !st.complete && (st.missing > 0 || (st.fluidMissing ?? 0) > 0))
              .map((st) => (
                <div
                  key={st.index}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4, marginLeft: 8 }}
                >
                  {st.registryName ? (
                    <Icon
                      item={{
                        itemId: st.itemId ?? st.registryName,
                        registryName: st.registryName,
                        meta: st.meta,
                      }}
                      size={18}
                    />
                  ) : null}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {st.registryName || st.fluidName} −{st.missing || st.fluidMissing}
                  </Text>
                </div>
              ))}
          </div>
        ))}
      </Modal>
    </Space>
  );
}
