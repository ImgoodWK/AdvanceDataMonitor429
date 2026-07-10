import { useMemo, useState } from 'react';
import { LockOutlined, UnlockOutlined } from '@ant-design/icons';
import { Checkbox, Tooltip } from 'antd';

import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { WorldMapMarkerDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import {
  DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS,
  resolveMarkerAeCategory,
  WORLD_MAP_AE_CATEGORY_ICON_IDS,
  WORLD_MAP_AE_CATEGORY_IDS,
  type WorldMapAeCategoryId,
} from '@/utils/worldMapAeCategories';

export interface WorldMapLegendRailProps {
  markers: WorldMapMarkerDto[];
  displaySettings: TopologyDisplaySettings;
  onChange: (patch: Partial<TopologyDisplaySettings>) => void;
}

export function WorldMapLegendRail({ markers, displaySettings, onChange }: WorldMapLegendRailProps) {
  const { t } = useI18n();
  const [hovered, setHovered] = useState(false);
  const locked = displaySettings.worldMapLegendLocked;
  const expanded = hovered || locked;

  const activeCategories = useMemo(() => {
    const set = new Set<WorldMapAeCategoryId>();
    for (const marker of markers) {
      set.add(resolveMarkerAeCategory(marker));
    }
    return WORLD_MAP_AE_CATEGORY_IDS.filter((id) => set.has(id));
  }, [markers]);

  const patchCategoryColor = (categoryId: WorldMapAeCategoryId, hex: string) => {
    if (locked) return;
    onChange({
      worldMapAeCategoryColors: {
        ...displaySettings.worldMapAeCategoryColors,
        [categoryId]: hex,
      },
    });
  };

  const patchVisibility = (categoryId: WorldMapAeCategoryId, visible: boolean) => {
    if (locked) return;
    onChange({
      worldMapAeCategoryVisibility: {
        ...displaySettings.worldMapAeCategoryVisibility,
        [categoryId]: visible,
      },
    });
  };

  const toggleLock = () => {
    onChange({ worldMapLegendLocked: !locked });
  };

  if (activeCategories.length === 0) return null;

  return (
    <div
      className={`worldmap-legend-rail${expanded ? ' worldmap-legend-rail--expanded' : ''}${locked ? ' worldmap-legend-rail--locked' : ''}`}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onPointerDown={(e) => e.stopPropagation()}
      onWheel={(e) => e.stopPropagation()}
    >
      <div className="worldmap-legend-rail-head">
        {expanded && <span className="worldmap-legend-rail-label">{t('worldMapLegendTitle')}</span>}
        <Tooltip title={locked ? t('worldMapLegendUnlock') : t('worldMapLegendLock')}>
          <button
            type="button"
            className="worldmap-legend-rail-lock"
            onClick={toggleLock}
            aria-label={locked ? t('worldMapLegendUnlock') : t('worldMapLegendLock')}
          >
            {locked ? <LockOutlined /> : <UnlockOutlined />}
          </button>
        </Tooltip>
      </div>
      <ul className="worldmap-legend-rail-list">
        {activeCategories.map((catId) => {
          const color =
            displaySettings.worldMapAeCategoryColors[catId] ??
            DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS[catId];
          const visible = displaySettings.worldMapAeCategoryVisibility[catId] !== false;
          const iconId = WORLD_MAP_AE_CATEGORY_ICON_IDS[catId];
          return (
            <li
              key={catId}
              className={`worldmap-legend-rail-item${visible ? '' : ' worldmap-legend-rail-item--hidden'}`}
            >
              <span
                className="worldmap-legend-rail-swatch"
                style={{ background: color }}
                title={color}
              >
                <Icon id={iconId} size={expanded ? 20 : 16} linkToWiki={false} alt="" />
              </span>
              {expanded && (
                <>
                  <span className="worldmap-legend-rail-name">{t(`worldMapAeCategory_${catId}`)}</span>
                  {!locked && (
                    <>
                      <Checkbox
                        checked={visible}
                        onChange={(e) => patchVisibility(catId, e.target.checked)}
                        aria-label={t(`worldMapAeCategory_${catId}`)}
                      />
                      <input
                        type="color"
                        className="worldmap-legend-rail-color"
                        value={color}
                        onChange={(e) => patchCategoryColor(catId, e.target.value)}
                        aria-label={t(`worldMapAeCategory_${catId}`)}
                      />
                    </>
                  )}
                </>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
