import { useCallback, useEffect, useState } from 'react';

import { Alert, Button, Divider, Empty, Space, Spin, Tag, Typography } from 'antd';
import { CloseOutlined } from '@ant-design/icons';

import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { McFormattedText } from '@/components/McFormattedText';
import { QuestRelationList } from '@/components/quest/QuestRelationList';
import { QuestSubmitPanel } from '@/components/quest/QuestSubmitPanel';
import { QuestTaskRow } from '@/components/quest/QuestTaskRow';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import type { QuestDetailDto, QuestProgressEntryDto } from '@/types/dto';

const { Paragraph, Text, Title } = Typography;

export interface QuestDetailPanelProps {
  questId: string | null;
  width?: number;
  onClose?: () => void;
  /** When true, omit panel header (Drawer supplies title). */
  embedded?: boolean;
  progressMap?: Record<string, QuestProgressEntryDto>;
  questNames?: Record<string, string>;
  onJumpQuest?: (questId: string, lineId?: string) => void;
  onBeforeSubmit?: () => Promise<void>;
  onSubmitted?: () => void;
}

function rewardLabel(r: QuestDetailDto['rewards'][number]): string {
  return r.name || r.description || r.registryName || r.factoryId || `#${r.index + 1}`;
}

export function QuestDetailPanel({
  questId,
  width = 380,
  onClose,
  embedded = false,
  progressMap,
  questNames,
  onJumpQuest,
  onBeforeSubmit,
  onSubmitted,
}: QuestDetailPanelProps) {
  const { t } = useI18n();
  const { selectedNetworks, setPageSearchPrefill, setActivePage, tokenType, serverConfig } =
    useAppContext();
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<QuestDetailDto | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [stepBusy, setStepBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (!questId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const res = await getApiClient().get<{ success: boolean; quest: QuestDetailDto }>(
        `/api/quests/${questId}`
      );
      setDetail(res.quest ?? null);
      if (!res.quest) {
        setLoadError(t('quest.notFound'));
      }
    } catch (err) {
      setDetail(null);
      setLoadError(err instanceof Error ? err.message : t('quest.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [questId, t]);

  useEffect(() => {
    if (!questId) {
      setDetail(null);
      setLoadError(null);
      setLoading(false);
      return;
    }
    setDetail(null);
    setLoadError(null);
    setLoading(true);
    void load();
  }, [questId, load]);

  const jumpRecipe = (registryName: string) => {
    setPageSearchPrefill({ page: 'recipes', query: registryName, networkId: selectedNetworks[0] });
    setActivePage('recipes');
  };

  const canSubmit =
    serverConfig?.questSubmitEnabled !== false &&
    tokenType !== 'guest' &&
    detail?.canSubmit;

  const showChain =
    serverConfig?.questChainSubmitEnabled !== false &&
    serverConfig?.questSubmitEnabled !== false &&
    tokenType !== 'guest' &&
    detail != null &&
    detail.state !== 'COMPLETED' &&
    detail.state !== 'UNCLAIMED';

  const submitStep = async (index: number, webAction: string) => {
    if (!questId) return;
    setStepBusy(index);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      if (webAction === 'DETECT') {
        await getApiClient().post(`/api/quests/${questId}/detect`, {});
      } else {
        await getApiClient().post(`/api/quests/${questId}/submit`, {
          networkId: selectedNetworks[0] ?? 0,
          dryRun: false,
          steps: [index],
        });
      }
      await load();
      onSubmitted?.();
    } finally {
      setStepBusy(null);
    }
  };

  const prerequisites =
    detail?.prerequisites?.length
      ? detail.prerequisites
      : (detail?.requirementQuestIds ?? []).map((id) => ({
          questId: id,
          name: questNames?.[id] ?? id.slice(0, 8) + '…',
          lineId: '',
          state: progressMap?.[id]?.state ?? '',
          requirementType: 'NORMAL',
        }));

  const panelBody = loading ? (
    <div style={{ padding: 24, textAlign: 'center' }}>
      <Spin />
    </div>
  ) : loadError ? (
    <Alert type="error" showIcon message={loadError} />
  ) : !detail ? (
    <Empty description={t('quest.notFound')} />
  ) : (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space align="start">
        {detail.iconItemId ? (
          <Icon item={{ itemId: detail.iconItemId, meta: detail.iconMeta }} size={40} />
        ) : null}
        <div>
          <Tag color="blue">{detail.state}</Tag>
          {detail.mainQuest ? <Tag color="gold">{t('quest.main')}</Tag> : null}
          {detail.canSubmit ? <Tag color="gold">{t('quest.canSubmit')}</Tag> : null}
        </div>
      </Space>

      <Paragraph style={{ marginBottom: 0 }}>
        <McFormattedText text={detail.description || ''} preWrap />
      </Paragraph>

      {detail.state === 'UNCLAIMED' ? (
        <Alert type="info" showIcon message={t('quest.claimInGame')} />
      ) : null}

      <Divider style={{ margin: '8px 0' }} />
      <Title level={5} style={{ margin: 0 }}>
        {t('quest.relationsSection')}
      </Title>
      <QuestRelationList
        title={t('quest.prerequisites')}
        relations={prerequisites}
        emptyText={t('quest.noPrerequisites')}
        onJumpQuest={onJumpQuest}
      />
      <QuestRelationList
        title={t('quest.dependents')}
        relations={detail.dependents ?? []}
        emptyText={t('quest.noDependents')}
        onJumpQuest={onJumpQuest}
      />

      <Divider style={{ margin: '8px 0' }} />
      <Title level={5} style={{ margin: 0 }}>
        {t('quest.requirementsSection')}
      </Title>
      {detail.tasks?.length ? (
        <div>
          {detail.tasks.map((task) => (
            <QuestTaskRow
              key={task.taskId ?? task.index}
              task={task}
              busy={stepBusy === task.index}
              canClickSubmit={
                !!canSubmit &&
                !task.complete &&
                (task.webAction === 'SUBMIT' || task.webAction === 'DETECT')
              }
              onSubmitStep={() => void submitStep(task.index, task.webAction)}
              onFindRecipe={
                task.registryName ? () => jumpRecipe(task.registryName!) : undefined
              }
            />
          ))}
        </div>
      ) : (
        <Empty description={t('quest.noRequirements')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}

      <Divider style={{ margin: '8px 0' }} />
      <Title level={5} style={{ margin: 0 }}>
        {t('quest.rewardsSection')}
      </Title>
      {detail.rewards?.length ? (
        <div>
          {detail.rewards.map((r) => (
            <div
              key={r.rewardId ?? r.index}
              style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}
            >
              {r.registryName ? (
                <Icon
                  item={{
                    itemId: r.itemId ?? r.registryName,
                    registryName: r.registryName,
                    meta: r.meta,
                  }}
                  size={36}
                />
              ) : (
                <span
                  style={{
                    width: 36,
                    height: 36,
                    flexShrink: 0,
                    borderRadius: 6,
                    background: 'rgba(148,163,184,0.15)',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    color: 'var(--text-secondary, #94a3b8)',
                  }}
                >
                  {r.factoryId ? r.factoryId.slice(0, 3) : '?'}
                </span>
              )}
              <div style={{ minWidth: 0 }}>
                <Text>
                  <McFormattedText text={rewardLabel(r)} />
                  {r.amount != null ? ` x${r.amount}` : ''}
                </Text>
                {r.description && r.description !== r.name ? (
                  <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                    <McFormattedText text={r.description} />
                  </Text>
                ) : null}
              </div>
            </div>
          ))}
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('quest.claimInGame')}
          </Text>
        </div>
      ) : (
        <Empty description={t('quest.noRewards')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}

      {(canSubmit || showChain) && questId ? (
        <QuestSubmitPanel
          questId={questId}
          networkId={selectedNetworks[0] ?? 0}
          canSubmit={!!canSubmit}
          chainEnabled={showChain}
          onBeforeSubmit={onBeforeSubmit}
          onSubmitted={() => {
            void load();
            onSubmitted?.();
          }}
        />
      ) : null}
    </Space>
  );

  if (embedded) {
    return panelBody;
  }

  return (
    <div
      style={{
        width,
        flexShrink: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '1px solid var(--border-color, #334155)',
        background: 'var(--layout-panel-bg, rgba(15,23,42,0.6))',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 12px',
          borderBottom: '1px solid var(--border-color, #334155)',
          flexShrink: 0,
        }}
      >
        <Title level={5} style={{ margin: 0, flex: 1, minWidth: 0 }}>
          {detail?.name ? <McFormattedText text={detail.name} /> : t('quest.detailTitle')}
        </Title>
        {onClose ? (
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            onClick={onClose}
            aria-label={t('quest.closeDetail')}
          />
        ) : null}
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '12px 14px' }}>{panelBody}</div>
    </div>
  );
}
