import { Button, Empty, Tooltip, Typography } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { McFormattedText } from '@/components/McFormattedText';
import { Icon } from '@/components/Icon';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { useI18n } from '@/i18n';
import type { QuestLineSummaryDto } from '@/types/dto';
import { stripMcFormatting } from '@/utils/mcFormatting';

const { Text } = Typography;

interface QuestLinePanelProps {
  lines: QuestLineSummaryDto[];
  activeLineId: string | null;
  onSelect: (lineId: string) => void;
  width?: number;
  lineIconSize?: number;
  lineFontSize?: number;
  collapsed?: boolean;
  onToggleCollapsed?: () => void;
}

export function QuestLinePanel({
  lines,
  activeLineId,
  onSelect,
  width = 250,
  lineIconSize = 28,
  lineFontSize = 14,
  collapsed = false,
  onToggleCollapsed,
}: QuestLinePanelProps) {
  const { t } = useI18n();
  const toggleLabel = collapsed ? t('quest.expandLines') : t('quest.collapseLines');

  const header = (
    <div className="webae-list-panel-header">
      <span className="webae-list-panel-header-title">{t('quest.linesPanel')}</span>
      {onToggleCollapsed ? (
        <Tooltip title={toggleLabel} placement="right">
          <Button
            type="text"
            size="small"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={onToggleCollapsed}
            aria-label={toggleLabel}
            aria-expanded={!collapsed}
          />
        </Tooltip>
      ) : null}
    </div>
  );

  if (collapsed) {
    return (
      <div
        className="webae-list-panel webae-list-panel--collapsed"
        style={{ width: 'auto', flexShrink: 0, maxHeight: '100%' }}
      >
        {header}
      </div>
    );
  }

  return (
    <div
      className="webae-list-panel"
      style={{
        width,
        flexShrink: 0,
        maxHeight: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      {header}
      <div className="webae-list-panel-body">
        {lines.length === 0 ? (
          <div style={{ padding: 8 }}>
            <Empty description={t('quest.noLines')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          </div>
        ) : (
          lines.map((line) => {
            const selected = line.lineId === activeLineId;
            return (
              <SelectableListRow
                key={line.lineId}
                selected={selected}
                onClick={() => onSelect(line.lineId)}
                ariaLabel={stripMcFormatting(line.name)}
                ariaCurrent={selected}
                leading={
                  line.iconItemId ? (
                    <Icon item={{ itemId: line.iconItemId, meta: line.iconMeta }} size={lineIconSize} alt={line.name} />
                  ) : (
                    <span style={{ width: lineIconSize, height: lineIconSize, flexShrink: 0 }} />
                  )
                }
              >
                <Text
                  ellipsis
                  style={{ display: 'block', fontSize: lineFontSize }}
                  title={stripMcFormatting(line.name)}
                >
                  <McFormattedText text={line.name} ellipsis />
                </Text>
                {line.questCount > 0 ? (
                  <Text type="secondary" style={{ fontSize: Math.max(11, lineFontSize - 2) }}>
                    {line.questCount}
                  </Text>
                ) : null}
              </SelectableListRow>
            );
          })
        )}
      </div>
    </div>
  );
}
