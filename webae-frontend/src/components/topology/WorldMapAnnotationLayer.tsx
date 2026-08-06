import { memo, useMemo } from 'react';

import { Button, Popconfirm, Popover, Space, Tooltip, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PushpinFilled } from '@ant-design/icons';

import { useI18n } from '@/i18n';
import type { WorldMapAnnotationDto } from '@/types/dto';
import {
  worldToScreen,
  type MapViewport,
  type WorldMapOrigin,
} from '@/utils/worldMapProjection';

const { Text } = Typography;

export interface WorldMapAnnotationLayerProps {
  annotations: WorldMapAnnotationDto[];
  dimension: number;
  viewport: MapViewport;
  origin: WorldMapOrigin;
  readOnly?: boolean;
  onEdit?: (annotation: WorldMapAnnotationDto) => void;
  onDelete?: (annotation: WorldMapAnnotationDto) => Promise<void> | void;
}

function validColor(color: string | null | undefined): string {
  return typeof color === 'string' && /^#[0-9a-f]{6}$/i.test(color) ? color : '#1677ff';
}

function versionRange(annotation: WorldMapAnnotationDto, allVersionsLabel: string): string {
  const from = Number(annotation.fromVersion) > 0 ? `v${annotation.fromVersion}` : '−∞';
  const to = Number(annotation.toVersion) > 0 ? `v${annotation.toVersion}` : '+∞';
  if (from === '−∞' && to === '+∞') return allVersionsLabel;
  return `${from} → ${to}`;
}

function WorldMapAnnotationLayerInner({
  annotations,
  dimension,
  viewport,
  origin,
  readOnly = false,
  onEdit,
  onDelete,
}: WorldMapAnnotationLayerProps) {
  const { t } = useI18n();
  const visible = useMemo(
    () => annotations.filter((annotation) => annotation.dimension === dimension),
    [annotations, dimension],
  );

  if (visible.length === 0) return null;

  return (
    <div className="worldmap-annotation-layer" aria-label={t('worldMapAnnotations')}>
      {visible.map((annotation) => {
        const { sx, sy } = worldToScreen(annotation.x, annotation.z, viewport, origin);
        const color = validColor(annotation.color);
        const content = (
          <div className="worldmap-annotation-detail">
            {annotation.note && <div className="worldmap-annotation-note">{annotation.note}</div>}
            <div>
              <Text type="secondary">{t('coordinates')}</Text>
              <div>{`D${annotation.dimension} · ${annotation.x}, ${annotation.y}, ${annotation.z}`}</div>
            </div>
            <div>
              <Text type="secondary">{t('worldMapVersionHistory')}</Text>
              <div>{versionRange(annotation, t('worldMapAnnotationUnknownRange'))}</div>
            </div>
            {!readOnly && (onEdit || onDelete) && (
              <Space size="small" className="worldmap-annotation-actions">
                {onEdit && (
                  <Tooltip title={t('worldMapAnnotationEdit')}>
                    <Button
                      type="text"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => onEdit(annotation)}
                      aria-label={t('worldMapAnnotationEdit')}
                    />
                  </Tooltip>
                )}
                {onDelete && (
                  <Popconfirm
                    title={t('worldMapAnnotationDeleteConfirm')}
                    okText={t('delete')}
                    cancelText={t('cancel')}
                    onConfirm={() => onDelete(annotation)}
                  >
                    <Tooltip title={t('worldMapAnnotationDelete')}>
                      <Button
                        danger
                        type="text"
                        size="small"
                        icon={<DeleteOutlined />}
                        aria-label={t('worldMapAnnotationDelete')}
                      />
                    </Tooltip>
                  </Popconfirm>
                )}
              </Space>
            )}
          </div>
        );

        return (
          <Popover
            key={annotation.id}
            title={annotation.label || t('worldMapAnnotations')}
            content={content}
            trigger="click"
          >
            <button
              type="button"
              className="worldmap-annotation-hit"
              style={{
                left: sx,
                top: sy,
                color,
                borderColor: color,
                backgroundColor: `${color}29`,
              }}
              onPointerDown={(event) => event.stopPropagation()}
              onClick={(event) => event.stopPropagation()}
              aria-label={`${annotation.label} · ${annotation.x}, ${annotation.y}, ${annotation.z}`}
            >
              <PushpinFilled aria-hidden />
            </button>
          </Popover>
        );
      })}
    </div>
  );
}

export const WorldMapAnnotationLayer = memo(WorldMapAnnotationLayerInner);
