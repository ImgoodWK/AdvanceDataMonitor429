import { Button, Empty, Tooltip, Typography } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { McFormattedText } from '@/components/McFormattedText';
import { Icon } from '@/components/Icon';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { useI18n } from '@/i18n';
import type { QuestLineSummaryDto } from '@/types/dto';
import { stripMcFormatting } from '@/utils/mcFormatting';
import { questIconProps } from '@/components/quest/questUtils';

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
  previewMode?: boolean;
  lineSubmittableCounts?: Record<string, number>;
  lineCompletedCounts?: Record<string, number>;
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
  previewMode = true,
  lineSubmittableCounts = {},
  lineCompletedCounts = {},
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
            const submittableCount = lineSubmittableCounts[line.lineId] ?? 0;
            const completedCount = lineCompletedCounts[line.lineId] ?? 0;
            const isDimmed = !previewMode && submittableCount === 0;
            const allDone = line.questCount > 0 && completedCount >= line.questCount;
            const row = (
              <SelectableListRow
                key={line.lineId}
                selected={selected}
                onClick={() => onSelect(line.lineId)}
                ariaLabel={stripMcFormatting(line.name)}
                ariaCurrent={selected}
                leading={
                  (() => {
                    const iconProps = questIconProps({
                      iconItemId: line.iconItemId,
                      meta: line.iconMeta,
                    });
                    return iconProps ? (
                      <Icon {...iconProps} size={lineIconSize} alt={line.name} />
                    ) : (
                      <span style={{ width: lineIconSize, height: lineIconSize, flexShrink: 0 }} />
                    );
                  })()
                }
                style={
                  isDimmed
                    ? { opacity: 0.4, color: 'var(--text-secondary)' }
                    : undefined
                }
              >
                <Text
                  ellipsis
                  style={{
                    display: 'block',
                    fontSize: lineFontSize,
                    ...(isDimmed ? { color: 'var(--text-secondary)' } : {}),
                  }}
                  title={stripMcFormatting(line.name)}
                >
                  <McFormattedText text={line.name} ellipsis />
                </Text>
                {line.questCount > 0 ? (
                  <Text
                    type="secondary"
                    style={{
                      fontSize: Math.max(11, lineFontSize - 2),
                      flexShrink: 0,
                      color: allDone ? 'var(--success)' : undefined,
                    }}
                  >
                    {completedCount}/{line.questCount}
                  </Text>
                ) : null}
              </SelectableListRow>
            );
            if (isDimmed) {
              return (
                <Tooltip key={line.lineId} title={t('quest.lineNoSubmittable')} placement="right">
                  {row}
                </Tooltip>
              );
            }
            return row;
          })
        )}
      </div>
    </div>
  );
}
