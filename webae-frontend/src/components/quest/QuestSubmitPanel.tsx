import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, message, Modal, Progress, Space, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type {
  QuestAnalysisDto,
  QuestAnalysisStepDto,
  QuestChainPlanDto,
  QuestChainSubmitResultDto,
  QuestCraftJobDto,
  QuestSubmitResultDto,
  QuestSubmitStepResultDto,
} from '@/types/dto';
import { fluidIconId } from '@/utils/icon';

const { Text } = Typography;

function stepCraftableEnough(step: QuestAnalysisStepDto): boolean {
  const missing = step.missing ?? 0;
  const fluidMissing = step.fluidMissing ?? 0;
  if (fluidMissing > 0) {
    return false;
  }
  const craftable = step.craftable ?? 0;
  return missing > 0 && craftable > 0 && craftable >= missing;
}

function formatAeStock(
  step: QuestAnalysisStepDto,
  t: (key: string, params?: Record<string, string | number>) => string
): string {
  if (step.fluidName) {
    return t('quest.aeStock', {
      available: step.fluidAvailable ?? 0,
      required: step.fluidRequired ?? 0,
    });
  }
  return t('quest.aeStock', {
    available: step.available ?? 0,
    required: step.required ?? 0,
  });
}

function formatDryRunStepLabel(
  step: QuestSubmitStepResultDto,
  analysisStep: QuestAnalysisStepDto | undefined,
  t: (key: string, params?: Record<string, string | number>) => string
): string {
  const name = step.itemId || step.fluidName || step.message || '';
  if (analysisStep) {
    return `${name} (${formatAeStock(analysisStep, t)})`;
  }
  if (step.amount != null && step.amount > 0) {
    return `${name} −${step.amount}`;
  }
  return name;
}

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
  const [craftConfirmOpen, setCraftConfirmOpen] = useState(false);

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

  /** Categorize dry-run steps into sufficient / needs-craft / insufficient. */
  const dryRunCats = useMemo(() => {
    const steps = dryRunResult?.steps ?? [];
    const analysisByIndex = new Map(
      (analysis?.steps ?? []).map((s) => [s.index, s] as const)
    );
    const sufficient: QuestSubmitStepResultDto[] = [];
    const needCraft: QuestSubmitStepResultDto[] = [];
    const insufficient: QuestSubmitStepResultDto[] = [];
    for (const s of steps) {
      const ana = analysisByIndex.get(s.index);
      const missing = ana?.missing ?? s.amount ?? 0;
      const fluidMissing = ana?.fluidMissing ?? s.fluidAmount ?? 0;
      if (missing === 0 && fluidMissing === 0) {
        sufficient.push(s);
      } else if (ana && stepCraftableEnough(ana)) {
        needCraft.push(s);
      } else if (
        s.message?.includes('craft') ||
        s.message?.includes('合成') ||
        s.message?.includes('Needs craft')
      ) {
        needCraft.push(s);
      } else {
        insufficient.push(s);
      }
    }
    return { sufficient, needCraft, insufficient, analysisByIndex };
  }, [dryRunResult, analysis]);

  const allSufficient = dryRunCats.insufficient.length === 0 && dryRunCats.needCraft.length === 0 && dryRunCats.sufficient.length > 0;
  const allInsufficient = dryRunCats.sufficient.length === 0 && dryRunCats.needCraft.length === 0 && dryRunCats.insufficient.length > 0;
  const canConfirm = dryRunCats.sufficient.length > 0 || dryRunCats.needCraft.length > 0;

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
      message.success(t('quest.stepSubmitted'));
      onSubmitted?.();
      await loadAnalysis();
    } catch (err) {
      message.error(t('quest.stepSubmitFailed', { reason: err instanceof Error ? err.message : String(err) }));
    } finally {
      setLoading(false);
    }
  };

  const submitCraft = async () => {
    setLoading(true);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();

      // Pre-check: load analysis to see if materials are already sufficient
      let ana = analysis;
      if (!ana) {
        const anaRes = await getApiClient().get<{ success: boolean; analysis: QuestAnalysisDto }>(
          `/api/quests/${questId}/analysis?network=${networkId}`
        );
        ana = anaRes.analysis ?? null;
        setAnalysis(ana);
      }

      const missingSteps = ana?.steps?.filter(
        (s) => (s.missing ?? 0) > 0 || (s.fluidMissing ?? 0) > 0
      ) ?? [];
      const craftableSteps = missingSteps.filter((s) => stepCraftableEnough(s));

      if (missingSteps.length === 0) {
        // All sufficient — suggest direct submit
        setCraftConfirmOpen(true);
        setLoading(false);
        return;
      }
      if (craftableSteps.length === 0) {
        message.error(t('quest.craftNoRecipe'));
        setLoading(false);
        return;
      }

      // Proceed with craft
      const res = await getApiClient().post<{ success: boolean; job: QuestCraftJobDto }>(
        `/api/quests/${questId}/submit-craft`,
        { networkId }
      );
      setJob(res.job ?? null);
    } catch {
      /* errors shown via job polling */
    } finally {
      setLoading(false);
    }
  };

  const skipCraftConfirm = async () => {
    setCraftConfirmOpen(false);
    setLoading(true);
    try {
      const res = await getApiClient().post<{ success: boolean; job: QuestCraftJobDto }>(
        `/api/quests/${questId}/submit-craft`,
        { networkId }
      );
      setJob(res.job ?? null);
    } catch {
      /* errors shown via job polling */
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

  /** Compute chain stats: total, ready, needCraft, skipped */
  const chainStats = useMemo(() => {
    const steps = chainPlan?.steps ?? [];
    const total = steps.length;
    const skipped = steps.filter((s) => s.skipped).length;
    const ready = steps.filter((s) => !s.skipped && s.fullySatisfied).length;
    const needCraft = steps.filter((s) => !s.skipped && !s.fullySatisfied && s.craftable).length;
    return { total, ready, needCraft, skip: skipped };
  }, [chainPlan]);

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
        const doneCount = chain?.steps?.filter((s) => s.submitResult?.success).length ?? 0;
        const totalCount = chain?.steps?.length ?? 0;
        if (doneCount === totalCount) {
          message.success(t('quest.chainDoneAll', { count: doneCount }));
        } else {
          message.warning(t('quest.chainDonePartial', { done: doneCount, total: totalCount, skip: totalCount - doneCount }));
        }
        onSubmitted?.();
        await loadAnalysis();
      }
    } catch (err) {
      message.error(t('quest.chainAborted', { reason: err instanceof Error ? err.message : String(err) }));
    } finally {
      setLoading(false);
    }
  };

  // Poll craft job
  useEffect(() => {
    if (!job || job.complete) return;
    const id = window.setInterval(async () => {
      try {
        const res = await getApiClient().get<{ success: boolean; job: QuestCraftJobDto }>(
          `/api/quests/submit-jobs/${job.jobId}`
        );
        setJob(res.job);
        if (res.job?.complete) {
          if (res.job.success) {
            message.success(t('quest.craftSubmitDone'));
          } else if (res.job.phase === 'escrow_failed') {
            message.error(t('quest.escrowFailed', { reason: res.job.message || '' }));
          } else {
            message.warning(res.job.message || t('quest.craftTimeout'));
          }
          onSubmitted?.();
          void loadAnalysis();
        }
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => window.clearInterval(id);
  }, [job, loadAnalysis, onSubmitted, t]);

  // Poll chain job
  useEffect(() => {
    if (!chainJob || chainJob.complete || !chainJob.jobId) return;
    const id = window.setInterval(async () => {
      try {
        const res = await getApiClient().get<{ success: boolean; chain: QuestChainSubmitResultDto }>(
          `/api/quests/chain-jobs/${chainJob.jobId}`
        );
        setChainJob(res.chain);
        if (res.chain?.complete) {
          const doneCount = res.chain.steps?.filter((s) => s.submitResult?.success).length ?? 0;
          const totalCount = res.chain.steps?.length ?? 0;
          if (doneCount === totalCount) {
            message.success(t('quest.chainDoneAll', { count: doneCount }));
          } else {
            message.warning(t('quest.chainDonePartial', { done: doneCount, total: totalCount, skip: totalCount - doneCount }));
          }
          onSubmitted?.();
          void loadAnalysis();
        }
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => window.clearInterval(id);
  }, [chainJob, loadAnalysis, onSubmitted, t]);

  const missingSteps =
    analysis?.steps?.filter(
      (s) => s.webCapable && !s.complete && ((s.missing ?? 0) > 0 || (s.fluidMissing ?? 0) > 0)
    ) ?? [];

  const isBusy = loading || (job != null && !job.complete) || (chainJob != null && !chainJob.complete);

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
                    {s.registryName || s.fluidName} {formatAeStock(s, t)}
                    {stepCraftableEnough(s)
                      ? ` (${t('quest.craftable')}: ${s.craftable})`
                      : ''}
                  </Text>
                </div>
              ))}
            </div>
          }
        />
      ) : null}
      {job && !job.complete ? (
        <div>
          <Text>
            {job.phase === 'locking'
              ? t('quest.lockingEscrow')
              : job.phase === 'escrow_failed'
                ? t('quest.escrowFailed', { reason: job.message || '' })
                : t('quest.craftingProgress', {
                    itemName: job.message || questId.slice(0, 8),
                    done: job.ordersDone ?? 0,
                    total: job.ordersTotal ?? 0,
                  })}
          </Text>
          <Progress
            percent={
              job.phase === 'locking'
                ? 90
                : job.ordersTotal
                  ? Math.round(((job.ordersDone ?? 0) / job.ordersTotal) * 100)
                  : 30
            }
            status={job.phase === 'escrow_failed' ? 'exception' : 'active'}
          />
        </div>
      ) : null}
      {chainJob && !chainJob.complete ? (
        <div>
          <Text>
            {chainJob.phase === 'locking'
              ? t('quest.lockingEscrow')
              : chainJob.phase === 'escrow_failed'
                ? t('quest.escrowFailed', { reason: chainJob.message || '' })
                : chainJob.message}
          </Text>
          <Progress
            percent={chainJob.phase === 'locking' ? 90 : 40}
            status={chainJob.phase === 'escrow_failed' ? 'exception' : 'active'}
          />
          {chainJob.steps?.map((s) => (
            <div key={s.questId} style={{ marginTop: 4 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                [{s.action}] {s.name}: {s.message}
              </Text>
            </div>
          ))}
        </div>
      ) : null}
      <Space wrap>
        {canSubmit ? (
          <>
            <Button type="primary" loading={isBusy} disabled={isBusy} onClick={() => void runDryRun()}>
              {isBusy ? t('quest.submitting') : t('quest.submit')}
            </Button>
            <Button loading={isBusy} disabled={isBusy} onClick={() => void submitCraft()}>
              {isBusy ? t('quest.submitting') : t('quest.submitCraft')}
            </Button>
          </>
        ) : null}
        {chainEnabled ? (
          <Button loading={isBusy} disabled={isBusy} onClick={() => void openChainPlan()}>
            {isBusy ? t('quest.submitting') : t('quest.chainSubmit')}
          </Button>
        ) : null}
      </Space>

      {/* Dry-run confirm modal with categorized display */}
      <Modal
        title={t('quest.confirmSubmit')}
        open={confirmOpen}
        onOk={() => void confirmSubmit()}
        onCancel={() => setConfirmOpen(false)}
        okText={t('quest.confirm')}
        cancelText={t('quest.cancel')}
        okButtonProps={{ disabled: !canConfirm }}
      >
        {allSufficient ? (
          <Alert type="success" showIcon message={t('quest.dryRunAllSufficient')} style={{ marginBottom: 12 }} />
        ) : allInsufficient ? (
          <Alert type="error" showIcon message={t('quest.dryRunAllInsufficient')} style={{ marginBottom: 12 }} />
        ) : (
          <Alert
            type="warning"
            showIcon
            message={t('quest.dryRunPartial', { count: dryRunCats.needCraft.length + dryRunCats.insufficient.length })}
            style={{ marginBottom: 12 }}
          />
        )}
        {dryRunCats.sufficient.map((s) => (
          <div key={s.index} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            {s.itemId ? <Icon item={{ itemId: s.itemId }} size={24} /> : null}
            <Text>{formatDryRunStepLabel(s, dryRunCats.analysisByIndex.get(s.index), t)}</Text>
            <Tag color="green" style={{ marginLeft: 'auto' }}>{t('quest.dryRunSufficient')}</Tag>
          </div>
        ))}
        {dryRunCats.needCraft.map((s) => (
          <div key={s.index} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            {s.itemId ? <Icon item={{ itemId: s.itemId }} size={24} /> : null}
            <Text>{formatDryRunStepLabel(s, dryRunCats.analysisByIndex.get(s.index), t)}</Text>
            <Tag color="gold" style={{ marginLeft: 'auto' }}>{t('quest.dryRunNeedCraft')}</Tag>
          </div>
        ))}
        {dryRunCats.insufficient.map((s) => (
          <div key={s.index} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            {s.itemId ? <Icon item={{ itemId: s.itemId }} size={24} /> : null}
            <Text>{formatDryRunStepLabel(s, dryRunCats.analysisByIndex.get(s.index), t)}</Text>
            <Tag color="red" style={{ marginLeft: 'auto' }}>{t('quest.dryRunInsufficient')}</Tag>
          </div>
        ))}
      </Modal>

      {/* Craft confirmation when all materials already in network */}
      <Modal
        title={t('quest.submitCraft')}
        open={craftConfirmOpen}
        onOk={() => void skipCraftConfirm()}
        onCancel={() => setCraftConfirmOpen(false)}
        okText={t('quest.confirm')}
        cancelText={t('quest.cancel')}
      >
        <Alert type="info" showIcon message={t('quest.craftAllSufficient')} />
      </Modal>

      {/* Chain plan modal with stats summary */}
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
        <Alert
          type={chainStats.ready === chainStats.total ? 'success' : chainStats.needCraft > 0 ? 'warning' : 'info'}
          showIcon
          message={t('quest.chainStats', {
            total: chainStats.total,
            ready: chainStats.ready,
            craft: chainStats.needCraft,
            skip: chainStats.skip,
          })}
          style={{ marginBottom: 12 }}
        />
        {chainPlan?.steps?.map((s) => {
          const borderColor = s.skipped
            ? '#64748b'
            : s.fullySatisfied
              ? '#22c55e'
              : s.craftable
                ? '#f59e0b'
                : '#ef4444';
          return (
            <div
              key={s.questId}
              style={{
                marginBottom: 8,
                padding: 8,
                borderRadius: 6,
                border: '1px solid var(--border-color, #334155)',
                borderLeft: `3px solid ${borderColor}`,
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
                      {st.registryName || st.fluidName} {formatAeStock(st, t)}
                    </Text>
                  </div>
                ))}
            </div>
          );
        })}
      </Modal>
    </Space>
  );
}
