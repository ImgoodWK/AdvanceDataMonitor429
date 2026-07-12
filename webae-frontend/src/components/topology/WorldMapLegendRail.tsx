import { useMemo, useState } from 'react';
import { LockOutlined, UnlockOutlined } from '@ant-design/icons';
import { Checkbox, Tooltip } from 'antd';

import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { WorldMapMarkerDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import { iconItemFromRegistryId } from '@/utils/icon';
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

/** Same icon resolution as WorldMapMarkerLayer device markers. */
function legendIconIdForCategory(
  catId: WorldMapAeCategoryId,
  markers: WorldMapMarkerDto[],
): string | null {
  if (catId === 'other') return null;
  for (const marker of markers) {
    if (resolveMarkerAeCategory(marker) !== catId) continue;
    return blockIconIdForNode(marker.type, marker.iconItemId);
  }
  return WORLD_MAP_AE_CATEGORY_ICON_IDS[catId] ?? null;
}

export function WorldMapLegendRail({ markers, displaySettings, onChange }: WorldMapLegendRailProps) {
  const { t } = useI18n();
  const [hovered, setHovered] = useState(false);
  const locked = displaySettings.worldMapLegendLocked;
  const expanded = hovered;

  const activeCategories = useMemo(() => {
    const set = new Set<WorldMapAeCategoryId>();
    for (const marker of markers) {
      set.add(resolveMarkerAeCategory(marker));
    }
    return WORLD_MAP_AE_CATEGORY_IDS.filter((id) => set.has(id));
  }, [markers]);

  const categoryIconIds = useMemo(() => {
    const map: Partial<Record<WorldMapAeCategoryId, string>> = {};
    for (const catId of activeCategories) {
      const iconId = legendIconIdForCategory(catId, markers);
      if (iconId) map[catId] = iconId;
    }
    return map;
  }, [activeCategories, markers]);

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
          const iconId = categoryIconIds[catId];
          const iconItem = iconId
            ? iconItemFromRegistryId(iconId, t(`worldMapAeCategory_${catId}`))
            : null;
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
                {iconItem && (
                  <Icon item={iconItem} size={expanded ? 20 : 16} linkToWiki={false} alt="" />
                )}
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
