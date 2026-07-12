import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Modal, Space, Tooltip, Typography } from 'antd';
import { Icon } from '@/components/Icon';
import { ColorField } from '@/components/dashboard/ColorField';
import { useI18n } from '@/i18n';
import type { WorldMapAePlacementDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import {
  DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS,
  groupIconIdsByAeCategory,
  WORLD_MAP_AE_CATEGORY_ICON_IDS,
  WORLD_MAP_AE_CATEGORY_IDS,
  type WorldMapAeCategoryId,
} from '@/utils/worldMapAeCategories';

const { Text } = Typography;

interface WorldMapAeColorModalProps {
  open: boolean;
  onClose: () => void;
  settings: TopologyDisplaySettings;
  onChange: (next: TopologyDisplaySettings) => void;
  aePlacements?: WorldMapAePlacementDto[];
}

function cloneColorFields(s: TopologyDisplaySettings): Pick<
  TopologyDisplaySettings,
  'worldMapAeCategoryColors' | 'worldMapAeItemColorOverrides'
> {
  return {
    worldMapAeCategoryColors: { ...s.worldMapAeCategoryColors },
    worldMapAeItemColorOverrides: { ...s.worldMapAeItemColorOverrides },
  };
}

export function WorldMapAeColorModal({
  open,
  onClose,
  settings,
  onChange,
  aePlacements = [],
}: WorldMapAeColorModalProps) {
  const { t } = useI18n();
  const [draft, setDraft] = useState(() => cloneColorFields(settings));
  const wasOpenRef = useRef(false);

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      setDraft(cloneColorFields(settings));
    }
    wasOpenRef.current = open;
  }, [open, settings]);

  const iconsByCategory = useMemo(() => groupIconIdsByAeCategory(aePlacements), [aePlacements]);

  const patchCategoryColor = (categoryId: WorldMapAeCategoryId, hex: string) => {
    setDraft((prev) => ({
      ...prev,
      worldMapAeCategoryColors: {
        ...prev.worldMapAeCategoryColors,
        [categoryId]: hex,
      },
    }));
  };

  const handleApply = () => {
    onChange({
      ...settings,
      worldMapAeCategoryColors: { ...draft.worldMapAeCategoryColors },
      worldMapAeItemColorOverrides: { ...draft.worldMapAeItemColorOverrides },
    });
    onClose();
  };

  const handleResetCategories = () => {
    setDraft((prev) => ({
      ...prev,
      worldMapAeCategoryColors: { ...DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS },
    }));
  };

  return (
    <Modal
      title={t('worldMapAeColorTitle')}
      open={open}
      onCancel={onClose}
      width={Math.min(560, window.innerWidth - 32)}
      destroyOnClose
      footer={
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Button onClick={handleResetCategories}>{t('worldMapAeColorReset')}</Button>
          <Space>
            <Button onClick={onClose}>{t('cancel')}</Button>
            <Button type="primary" onClick={handleApply}>
              {t('apply')}
            </Button>
          </Space>
        </Space>
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        {t('worldMapAeColorHint')}
      </Text>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {WORLD_MAP_AE_CATEGORY_IDS.map((catId) => {
          const color = draft.worldMapAeCategoryColors[catId] ?? DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS[catId];
          const networkIcons = iconsByCategory[catId] ?? [];
          const previewIconId = WORLD_MAP_AE_CATEGORY_ICON_IDS[catId];

          return (
            <div
              key={catId}
              style={{
                display: 'flex',
                gap: 12,
                alignItems: 'flex-start',
                padding: '10px 12px',
                borderRadius: 8,
                border: '1px solid var(--border, rgba(255,255,255,0.12))',
                background: 'var(--surface-raised, rgba(255,255,255,0.03))',
              }}
            >
              <div
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 8,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  background: color,
                  border: '1px solid rgba(0,0,0,0.25)',
                }}
                title={color}
              >
                <Icon id={previewIconId} size={32} linkToWiki={false} />
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <Text strong style={{ display: 'block', marginBottom: 6 }}>
                  {t(`worldMapAeCategory_${catId}`)}
                </Text>

                {networkIcons.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                      {t('worldMapAeColorNetworkIcons')}
                    </Text>
                    <Space size={4} wrap>
                      {networkIcons.map((iconId) => (
                        <Tooltip key={iconId} title={iconId}>
                          <span
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              width: 28,
                              height: 28,
                              borderRadius: 4,
                              background: color,
                              border: '1px solid rgba(0,0,0,0.2)',
                            }}
                          >
                            <Icon id={iconId} size={22} linkToWiki={false} />
                          </span>
                        </Tooltip>
                      ))}
                    </Space>
                  </div>
                )}

                <ColorField
                  label={t(`worldMapAeCategory_${catId}`)}
                  value={color}
                  onChange={(hex) => patchCategoryColor(catId, hex)}
                />
              </div>
            </div>
          );
        })}
      </div>
    </Modal>
  );
}
