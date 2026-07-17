import { Empty, Tag, Typography } from 'antd';
import { McFormattedText } from '@/components/McFormattedText';
import { Icon } from '@/components/Icon';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { useI18n } from '@/i18n';
import type { QuestLineNodeDto } from '@/types/dto';
import { stripMcFormatting } from '@/utils/mcFormatting';
import { questIconProps, questStateColor } from '@/components/quest/questUtils';

const { Text } = Typography;

interface QuestListPanelProps {
  nodes: QuestLineNodeDto[];
  selectedQuestId: string | null;
  onSelect: (questId: string) => void;
  width?: number;
  /** When true, omit side border (e.g. inside a drawer). */
  embedded?: boolean;
}

export function QuestListPanel({
  nodes,
  selectedQuestId,
  onSelect,
  width = 300,
  embedded = false,
}: QuestListPanelProps) {
  const { t } = useI18n();

  if (nodes.length === 0) {
    return (
      <div style={{ width, flexShrink: 0, overflow: 'auto', padding: 8 }}>
        <Empty description={t('quest.noNodes')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </div>
    );
  }

  return (
    <div
      className={'webae-list-panel' + (embedded ? ' webae-list-panel--embedded' : '')}
      style={{ width, flexShrink: 0, maxHeight: '100%' }}
    >
      {nodes.map((n) => {
        const selected = n.questId === selectedQuestId;
        const color = questStateColor(n.state);
        const opacity = n.state === 'LOCKED' ? 0.65 : n.state === 'COMPLETED' ? 0.8 : 1;
        return (
          <SelectableListRow
            key={n.questId}
            selected={selected}
            onClick={() => onSelect(n.questId)}
            opacity={opacity}
            ariaLabel={stripMcFormatting(n.name)}
            ariaCurrent={selected}
            leading={
              <>
                <span
                  className="webae-list-row-dot"
                  style={{
                    background: color,
                    boxShadow: n.canSubmit ? '0 0 0 2px #fbbf24' : undefined,
                  }}
                />
                {(() => {
                  const iconProps = questIconProps({
                    iconItemId: n.iconItemId,
                    meta: n.iconMeta,
                  });
                  return iconProps ? (
                    <Icon {...iconProps} size={28} alt={n.name} />
                  ) : (
                    <span style={{ width: 28, height: 28, flexShrink: 0 }} />
                  );
                })()}
              </>
            }
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, minWidth: 0 }}>
              {n.mainQuest ? (
                <Tag color="gold" style={{ margin: 0, lineHeight: '16px', fontSize: 10 }}>
                  {t('quest.main')}
                </Tag>
              ) : null}
              {n.ghost ? (
                <Tag style={{ margin: 0, lineHeight: '16px', fontSize: 10 }}>{t('quest.ghost')}</Tag>
              ) : null}
              <Text ellipsis style={{ flex: 1 }} title={stripMcFormatting(n.name)}>
                <McFormattedText text={n.name} ellipsis />
              </Text>
            </div>
          </SelectableListRow>
        );
      })}
    </div>
  );
}
