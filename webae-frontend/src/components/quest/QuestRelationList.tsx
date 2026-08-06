import { Button, Space, Tag, Typography } from 'antd';

import { McFormattedText } from '@/components/McFormattedText';
import { questStateColor } from '@/components/quest/questUtils';
import { useI18n } from '@/i18n';
import type { QuestRelationDto } from '@/types/dto';

const { Text } = Typography;

interface QuestRelationListProps {
  title: string;
  relations: QuestRelationDto[];
  emptyText: string;
  onJumpQuest?: (questId: string, lineId?: string) => void;
}

function reqTypeLabel(t: (key: string) => string, type?: string): string | null {
  if (!type || type === 'NORMAL') return null;
  if (type === 'IMPLICIT') return t('quest.reqType.implicit');
  if (type === 'HIDDEN') return t('quest.reqType.hidden');
  return type;
}

export function QuestRelationList({
  title,
  relations,
  emptyText,
  onJumpQuest,
}: QuestRelationListProps) {
  const { t } = useI18n();

  if (!relations.length) {
    return (
      <div>
        <Text strong>{title}</Text>
        <div style={{ marginTop: 6 }}>
          <Text type="secondary">{emptyText}</Text>
        </div>
      </div>
    );
  }

  return (
    <div>
      <Text strong>{title}</Text>
      <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
        {relations.map((rel) => {
          const typeLabel = reqTypeLabel(t, rel.requirementType);
          return (
            <div key={rel.questId} style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              <Button
                size="small"
                type="link"
                onClick={() => onJumpQuest?.(rel.questId, rel.lineId || undefined)}
                style={{
                  paddingLeft: 0,
                  height: 'auto',
                  color: questStateColor(rel.state),
                }}
              >
                {rel.state ? `[${rel.state}] ` : ''}
                <McFormattedText text={rel.name || rel.questId.slice(0, 8) + '…'} />
              </Button>
              {typeLabel ? <Tag style={{ margin: 0 }}>{typeLabel}</Tag> : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}
