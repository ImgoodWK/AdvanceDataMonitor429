import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  Alert,
  Button,
  Divider,
  Empty,
  Modal,
  Radio,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { CloseOutlined } from '@ant-design/icons';

import { getApiClient } from '@/api/client';
import { Icon } from '@/components/Icon';
import { McFormattedText } from '@/components/McFormattedText';
import { QuestRelationList } from '@/components/quest/QuestRelationList';
import { QuestSubmitPanel } from '@/components/quest/QuestSubmitPanel';
import { QuestTaskRow } from '@/components/quest/QuestTaskRow';
import { questIconProps } from '@/components/quest/questUtils';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import type {
  QuestClaimResultDto,
  QuestDetailDto,
  QuestProgressEntryDto,
  QuestRewardDto,
} from '@/types/dto';

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

function rewardLabel(r: QuestRewardDto): string {
  return r.name || r.description || r.registryName || r.factoryId || `#${r.index + 1}`;
}

type RewardGroup =
  | { type: 'item'; rewardId: string; rows: QuestRewardDto[] }
  | { type: 'choice'; rewardId: string; rows: QuestRewardDto[]; title: string }
  | { type: 'unsupported'; rewardId: string; rows: QuestRewardDto[] };

function groupRewards(rewards: QuestRewardDto[] | undefined): RewardGroup[] {
  if (!rewards?.length) return [];
  const groups: RewardGroup[] = [];
  const indexById = new Map<string, number>();

  for (const row of rewards) {
    const id = row.rewardId || `idx-${row.index}`;
    const existing = indexById.get(id);
    if (existing != null) {
      groups[existing]!.rows.push(row);
      continue;
    }
    indexById.set(id, groups.length);
    if (row.choiceOption || row.kind === 'choice') {
      groups.push({
        type: 'choice',
        rewardId: id,
        rows: [row],
        title: row.description || row.factoryId || id,
      });
    } else if (row.kind === 'unsupported' || row.webClaimable === false) {
      groups.push({ type: 'unsupported', rewardId: id, rows: [row] });
    } else {
      groups.push({ type: 'item', rewardId: id, rows: [row] });
    }
  }
  return groups;
}

function RewardRow({ reward }: { reward: QuestRewardDto }) {
  const rewardIcon = questIconProps({
    iconItemId: reward.iconItemId,
    itemId: reward.itemId,
    registryName: reward.registryName,
    meta: reward.meta,
  });
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
      {rewardIcon ? (
        <Icon {...rewardIcon} size={36} />
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
          {reward.factoryId ? reward.factoryId.slice(0, 3) : '?'}
        </span>
      )}
      <div style={{ minWidth: 0 }}>
        <Text>
          <McFormattedText text={rewardLabel(reward)} />
          {reward.amount != null ? ` x${reward.amount}` : ''}
        </Text>
        {reward.description && reward.description !== reward.name ? (
          <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
            <McFormattedText text={reward.description} />
          </Text>
        ) : null}
      </div>
    </div>
  );
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
  const [choiceSelections, setChoiceSelections] = useState<Record<string, number>>({});
  const [claimBusy, setClaimBusy] = useState(false);

  const load = useCallback(async () => {
    if (!questId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const res = await getApiClient().get<{ success: boolean; quest: QuestDetailDto }>(
        `/api/quests/${questId}`
      );
      setDetail(res.quest ?? null);
      setChoiceSelections({});
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
      setChoiceSelections({});
      return;
    }
    setDetail(null);
    setLoadError(null);
    setLoading(true);
    setChoiceSelections({});
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

  const rewardGroups = useMemo(() => groupRewards(detail?.rewards), [detail?.rewards]);

  const choiceGroups = useMemo(
    () => rewardGroups.filter((g): g is Extract<RewardGroup, { type: 'choice' }> => g.type === 'choice'),
    [rewardGroups]
  );

  const choicesComplete = choiceGroups.every((g) => choiceSelections[g.rewardId] != null);

  const canClaimWeb =
    serverConfig?.questClaimEnabled !== false &&
    tokenType !== 'guest' &&
    !!detail?.webClaimable &&
    (detail.state === 'UNCLAIMED' || !!detail.canClaim);

  const claimBlockedReason = detail?.claimBlockReason;

  const submitStep = async (index: number, webAction: string) => {
    if (!questId) return;
    setStepBusy(index);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      if (webAction === 'DETECT') {
        await getApiClient().post(`/api/quests/${questId}/detect`, {
          networkId: selectedNetworks[0] ?? 0,
        });
      } else {
        await getApiClient().post(`/api/quests/${questId}/submit`, {
          networkId: selectedNetworks[0] ?? 0,
          dryRun: false,
          steps: [index],
        });
      }
      message.success(t('quest.stepSubmitted'));
      await load();
      onSubmitted?.();
    } catch (err) {
      message.error(
        t('quest.stepSubmitFailed', {
          reason: err instanceof Error ? err.message : String(err),
        })
      );
    } finally {
      setStepBusy(null);
    }
  };

  const claimErrorMessage = (code?: string, fallback?: string) => {
    switch (code) {
      case 'ae_full':
        return t('quest.claimError.aeFull');
      case 'inventory_full':
        return t('quest.claimError.inventoryFull');
      case 'choice_required':
        return t('quest.claimError.choiceRequired');
      case 'non_item_reward':
        return t('quest.claimError.nonItem');
      case 'claim_disabled':
        return t('quest.claimError.disabled');
      case 'no_network':
      case 'no_storage':
        return t('quest.claimError.noNetwork');
      case 'not_unclaimed':
        return t('quest.claimError.notUnclaimed');
      case 'guest_readonly':
        return t('quest.claimError.guest');
      default:
        return fallback || t('quest.claimFailed');
    }
  };

  const doClaim = async () => {
    if (!questId || !canClaimWeb) return;
    if (!choicesComplete) {
      message.warning(t('quest.claimError.choiceRequired'));
      return;
    }
    const networkId = selectedNetworks[0] ?? 0;
    setClaimBusy(true);
    try {
      if (onBeforeSubmit) await onBeforeSubmit();
      const res = await getApiClient().post<{ success: boolean; claim: QuestClaimResultDto }>(
        `/api/quests/${questId}/claim`,
        {
          networkId,
          choices: choiceSelections,
        }
      );
      const claim = res.claim;
      if (!claim?.success) {
        message.error(claimErrorMessage(claim?.code, claim?.message));
        return;
      }
      if (claim.code === 'partial_ae') {
        message.warning(t('quest.claimPartialAe'));
      } else {
        message.success(t('quest.claimSuccess'));
      }
      await load();
      onSubmitted?.();
    } catch (err) {
      message.error(
        t('quest.claimFailedDetail', {
          reason: err instanceof Error ? err.message : String(err),
        })
      );
    } finally {
      setClaimBusy(false);
    }
  };

  const confirmClaim = () => {
    const networkId = selectedNetworks[0] ?? 0;
    Modal.confirm({
      title: t('quest.confirmClaimTitle'),
      content: t('quest.confirmClaimBody', { network: String(networkId) }),
      okText: t('quest.claimToAe'),
      cancelText: t('quest.cancel'),
      onOk: () => doClaim(),
    });
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
        {(() => {
          const iconProps = questIconProps({
            iconItemId: detail.iconItemId,
            meta: detail.iconMeta,
          });
          return iconProps ? <Icon {...iconProps} size={40} /> : null;
        })()}
        <div>
          <Tag color="blue">{detail.state}</Tag>
          {detail.mainQuest ? <Tag color="gold">{t('quest.main')}</Tag> : null}
          {detail.canSubmit ? <Tag color="gold">{t('quest.canSubmit')}</Tag> : null}
          {detail.webClaimable ? <Tag color="green">{t('quest.canClaimWeb')}</Tag> : null}
        </div>
      </Space>

      <Paragraph style={{ marginBottom: 0 }}>
        <McFormattedText text={detail.description || ''} preWrap />
      </Paragraph>

      {detail.state === 'UNCLAIMED' && !canClaimWeb ? (
        <Alert
          type="info"
          showIcon
          message={
            claimBlockedReason === 'non_item_reward'
              ? t('quest.claimInGameNonItem')
              : t('quest.claimInGame')
          }
        />
      ) : null}

      {canClaimWeb ? (
        <Alert
          type="success"
          showIcon
          message={t('quest.claimToAeHint', { network: String(selectedNetworks[0] ?? 0) })}
        />
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
      {rewardGroups.length ? (
        <div>
          {rewardGroups.map((group) => {
            if (group.type === 'choice') {
              return (
                <div key={`choice-${group.rewardId}`} style={{ marginBottom: 14 }}>
                  <Text strong style={{ display: 'block', marginBottom: 6 }}>
                    {t('quest.choiceReward')}
                  </Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: 12, marginBottom: 8 }}>
                    {t('quest.choiceRewardHint')}
                  </Text>
                  <Radio.Group
                    value={choiceSelections[group.rewardId]}
                    onChange={(e) =>
                      setChoiceSelections((prev) => ({
                        ...prev,
                        [group.rewardId]: e.target.value as number,
                      }))
                    }
                    style={{ width: '100%' }}
                  >
                    <Space direction="vertical" style={{ width: '100%' }}>
                      {group.rows.map((row) => (
                        <Radio key={`${group.rewardId}-${row.choiceIndex}`} value={row.choiceIndex}>
                          <RewardRow reward={row} />
                        </Radio>
                      ))}
                    </Space>
                  </Radio.Group>
                </div>
              );
            }
            return (
              <div key={`${group.type}-${group.rewardId}`} style={{ marginBottom: 8 }}>
                {group.type === 'unsupported' ? (
                  <Text type="secondary" style={{ display: 'block', fontSize: 12, marginBottom: 4 }}>
                    {t('quest.rewardInGameOnly')}
                  </Text>
                ) : null}
                {group.rows.map((row) => (
                  <RewardRow key={row.index} reward={row} />
                ))}
              </div>
            );
          })}

          {canClaimWeb ? (
            <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
              {!choicesComplete ? (
                <Text type="warning" style={{ fontSize: 12 }}>
                  {t('quest.claimSelectChoices')}
                </Text>
              ) : null}
              <Button
                type="primary"
                block
                loading={claimBusy}
                disabled={!choicesComplete || claimBusy}
                onClick={confirmClaim}
              >
                {t('quest.claimToAe')}
              </Button>
            </Space>
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {detail.state === 'UNCLAIMED'
                ? claimBlockedReason === 'non_item_reward'
                  ? t('quest.claimInGameNonItem')
                  : t('quest.claimInGame')
                : null}
            </Text>
          )}
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
        borderLeft: '1px solid var(--border, var(--border-color, rgba(128,128,128,0.25)))',
        background: 'var(--layout-panel-bg, var(--bg-card))',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 12px',
          borderBottom: '1px solid var(--border, var(--border-color, rgba(128,128,128,0.25)))',
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
