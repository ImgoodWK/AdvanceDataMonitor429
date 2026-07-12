import { useEffect, useRef, useState } from 'react';
import { Button, Divider, Drawer, InputNumber, Segmented, Select, Space, Switch, Typography } from 'antd';
import { useI18n } from '@/i18n';
import { SettingRow } from '@/components/common/SettingRow';
import {
  DEFAULT_TOPOLOGY_DISPLAY,
  type TopologyDisplaySettings,
  type TopologyLabelStrategy,
  type TopologyLayoutDirection,
  type TopologyRenderMode,
  type WorldMapObliqueDirection,
  type WorldMapQualityTierId,
} from '@/types/topologyDisplay';
import type { WorldMapQualityTierDto, WorldMapViewDto } from '@/types/dto';
import { clampWorldMapQuality } from '@/utils/worldMapTerrain';
import { AE_CABLE_COLORS, type AeCableColorId, hexFromAeCableColorId } from '@/utils/aeCableColors';

const { Text } = Typography;

interface TopologySettingsDrawerProps {
  open: boolean;
  onClose: () => void;
  settings: TopologyDisplaySettings;
  onChange: (s: TopologyDisplaySettings) => void;
  onReset: () => void;
  /** Whether render mode toggle is shown (logical view only). */
  showRenderMode?: boolean;
  /** World map oblique direction (world map mode only). */
  showWorldMapSettings?: boolean;
  obliqueDirectionOptions?: WorldMapViewDto[];
  qualityTierOptions?: WorldMapQualityTierDto[];
  maxQualityTier?: WorldMapQualityTierId;
  /** Current terrain source label for display ("dynmap" / "self"). */
  terrainSource?: string;
  /** Whether client GL capture is available (player online). */
  hdAvailable?: boolean;
  /** Client capture mode from server config. */
  clientCaptureMode?: string;
  /** Dynmap base URL for "Open in Dynmap" button. */
  dynmapBaseUrl?: string;
  /** Configured snapshot terrain capture priority (read-only). */
  snapshotSourcePriority?: string[];
  /** Last snapshot per-source chunk counts. */
  snapshotSourceStats?: Record<string, number>;
  /** Last finalized snapshot source label. */
  snapshotSource?: string;
}

function clampSettings(s: TopologyDisplaySettings, maxQualityTier: WorldMapQualityTierId): TopologyDisplaySettings {
  return {
    ...s,
    depthGap: Math.max(80, Math.min(240, s.depthGap)),
    siblingGap: Math.max(48, Math.min(160, s.siblingGap)),
    labelMargin: Math.max(8, Math.min(48, s.labelMargin)),
    nodeRadius: Math.max(12, Math.min(32, s.nodeRadius)),
    cableCellPx: Math.max(16, Math.min(40, s.cableCellPx)),
    nodeBlockPx: Math.max(24, Math.min(48, s.nodeBlockPx)),
    worldMapAeOverlayOpacity: Math.max(0, Math.min(1, s.worldMapAeOverlayOpacity ?? 0.85)),
    worldMapQuality: clampWorldMapQuality(
      s.worldMapQuality ?? DEFAULT_TOPOLOGY_DISPLAY.worldMapQuality,
      maxQualityTier
    ),
  };
}

function cloneSettings(s: TopologyDisplaySettings): TopologyDisplaySettings {
  return {
    ...s,
    colors: { ...s.colors },
    cableColorPreset: { ...s.cableColorPreset },
    worldMapAeCategoryColors: { ...s.worldMapAeCategoryColors },
    worldMapAeItemColorOverrides: { ...s.worldMapAeItemColorOverrides },
  };
}

export function TopologySettingsDrawer({
  open,
  onClose,
  settings,
  onChange,
  onReset,
  showRenderMode = true,
  showWorldMapSettings = false,
  obliqueDirectionOptions,
  qualityTierOptions,
  maxQualityTier = 'ultra',
  terrainSource,
  hdAvailable,
  clientCaptureMode,
  dynmapBaseUrl,
  snapshotSourcePriority,
  snapshotSourceStats,
  snapshotSource,
}: TopologySettingsDrawerProps) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<TopologyDisplaySettings>(() => cloneSettings(settings));
  const wasOpenRef = useRef(false);

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      setDraft(cloneSettings(settings));
    }
    wasOpenRef.current = open;
  }, [open, settings]);

  const patch = (p: Partial<TopologyDisplaySettings>) => setDraft((prev) => ({ ...prev, ...p }));

  const patchCablePreset = (key: keyof TopologyDisplaySettings['cableColorPreset'], colorId: AeCableColorId) => {
    setDraft((prev) => ({
      ...prev,
      cableColorPreset: { ...prev.cableColorPreset, [key]: colorId },
      colors: { ...prev.colors, [key]: hexFromAeCableColorId(colorId) },
    }));
  };

  const handleApply = () => {
    onChange(clampSettings(draft, maxQualityTier));
    onClose();
  };

  const qualityOptions = (qualityTierOptions && qualityTierOptions.length > 0
    ? qualityTierOptions
    : [
        { id: 'low', labelKey: 'adm.webae.worldmap.quality.low', tilePx: 64, pxPerBlock: 4 },
        { id: 'medium', labelKey: 'adm.webae.worldmap.quality.medium', tilePx: 128, pxPerBlock: 8 },
        { id: 'high', labelKey: 'adm.webae.worldmap.quality.high', tilePx: 256, pxPerBlock: 16 },
        { id: 'ultra', labelKey: 'adm.webae.worldmap.quality.ultra', tilePx: 512, pxPerBlock: 32 },
      ]
  )
    .filter((opt) =>
      clampWorldMapQuality(opt.id as WorldMapQualityTierId, maxQualityTier) === opt.id
    )
    .map((opt) => ({
      value: opt.id,
      label: t(`worldMapQuality_${opt.id}`),
    }));

  const obliqueOptions = (obliqueDirectionOptions && obliqueDirectionOptions.length > 0
    ? obliqueDirectionOptions
    : [
        { id: 'se', labelKey: 'adm.webae.worldmap.oblique.se' },
        { id: 'sw', labelKey: 'adm.webae.worldmap.oblique.sw' },
        { id: 'ne', labelKey: 'adm.webae.worldmap.oblique.ne' },
        { id: 'nw', labelKey: 'adm.webae.worldmap.oblique.nw' },
      ]
  ).map((opt) => ({
    value: opt.id as WorldMapObliqueDirection,
    label: t(`worldMapOblique_${opt.id}`),
  }));

  const colorOptions = AE_CABLE_COLORS.map((c) => ({
    value: c.id,
    label: (
      <Space size={6}>
        <span className="webae-color-swatch" style={{ background: c.hex }} />
        {t(`topologyAeColor_${c.id}`)}
      </Space>
    ),
  }));

  return (
    <Drawer
      title={t('topologySettingsTitle')}
      open={open}
      onClose={onClose}
      width={Math.min(400, window.innerWidth - 24)}
      extra={
        <Button type="link" onClick={onReset}>
          {t('topologySettingsReset')}
        </Button>
      }
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose}>{t('cancel')}</Button>
          <Button type="primary" onClick={handleApply}>
            {t('apply')}
          </Button>
        </Space>
      }
    >
      {showWorldMapSettings && (
        <>
          <Divider orientation="left" plain>
            {t('worldMapSettingsTitle')}
          </Divider>
          {/* Terrain source indicator */}
          {terrainSource && (
            <div className="webae-setting-row">
              <Text type="secondary">
                {t('worldMapTerrainSource') || '地形来源'}:{' '}
              </Text>
              <Text strong>
                {terrainSource === 'direct'
                  ? (t('worldMapTerrainSource_direct') || '单人直读')
                  : terrainSource === 'snapshot'
                    ? (t('worldMapTerrainSource_snapshot') || '快照瓦片')
                    : terrainSource === 'dynmap'
                      ? (t('worldMapTerrainSource_dynmap') || 'Dynmap 地形')
                      : (t('worldMapTerrainSource_self') || '内置渲染')}
              </Text>
            </div>
          )}
          {snapshotSourcePriority && snapshotSourcePriority.length > 0 && (
            <div className="webae-setting-row">
              <Text type="secondary">{t('worldMapSnapshotPriority') || '采集优先级'}: </Text>
              <Text strong>{snapshotSourcePriority.join(' → ')}</Text>
            </div>
          )}
          {snapshotSourceStats && Object.keys(snapshotSourceStats).length > 0 && (
            <div className="webae-setting-row">
              <Text type="secondary">{t('worldMapSnapshotSourceStats') || '上次快照源统计'}: </Text>
              <Text>
                {Object.entries(snapshotSourceStats)
                  .map(([k, v]) => `${k}: ${v}`)
                  .join(', ')}
              </Text>
              {snapshotSource && (
                <>
                  {' '}
                  <Text type="secondary">({snapshotSource})</Text>
                </>
              )}
            </div>
          )}
          {clientCaptureMode && clientCaptureMode !== 'off' && (
            <div className="webae-setting-row">
              <Text type="secondary">{t('worldMapClientCaptureMode')}: </Text>
              <Text strong>
                {t(`worldMapClientCaptureMode_${clientCaptureMode}` as 'worldMapClientCaptureMode_when_online')}
              </Text>
              {hdAvailable != null && (
                <>
                  {' '}
                  <Text type={hdAvailable ? 'success' : 'secondary'}>
                    ({hdAvailable ? t('worldMapHdAvailable') : t('worldMapHdUnavailable')})
                  </Text>
                </>
              )}
            </div>
          )}
          {dynmapBaseUrl && (
            <div className="webae-setting-row">
              <Button
                type="primary"
                ghost
                size="small"
                block
                onClick={() => window.open(dynmapBaseUrl, '_blank', 'noopener')}
              >
                {t('worldMapOpenDynmap') || '在 Dynmap 中打开'}
              </Button>
            </div>
          )}
          <SettingRow label={t('worldMapObliqueDirection')} hint={t('worldMapObliqueDirectionHint')}>
            <Select
              className="webae-full-width"
              value={draft.worldMapObliqueDirection}
              onChange={(v) => patch({ worldMapObliqueDirection: v as WorldMapObliqueDirection })}
              options={obliqueOptions}
            />
          </SettingRow>
          <SettingRow label={t('worldMapQuality')} hint={t('worldMapQualityHint')}>
            <Segmented
              className="webae-full-width"
              value={draft.worldMapQuality}
              onChange={(v) => patch({ worldMapQuality: v as WorldMapQualityTierId })}
              options={qualityOptions}
              block
            />
          </SettingRow>
          <Divider orientation="left" plain>
            {t('worldMapLayerSettingsTitle')}
          </Divider>
          <SettingRow label={t('worldMapLayerTerrain')}>
            <Switch checked={draft.showWorldMapTerrain} onChange={(v) => patch({ showWorldMapTerrain: v })} />
          </SettingRow>
          <SettingRow label={t('worldMapLayerAeOverlay')}>
            <Switch checked={draft.showWorldMapAeOverlay} onChange={(v) => patch({ showWorldMapAeOverlay: v })} />
          </SettingRow>
          <SettingRow label={t('worldMapLayerDeviceIcons')}>
            <Switch
              checked={draft.showWorldMapDeviceIcons}
              onChange={(v) => patch({ showWorldMapDeviceIcons: v })}
            />
          </SettingRow>
          {draft.showWorldMapAeOverlay && (
            <SettingRow label={t('worldMapAeOverlayOpacity')}>
              <InputNumber
                min={0}
                max={1}
                step={0.01}
                value={draft.worldMapAeOverlayOpacity}
                onChange={(v) => patch({ worldMapAeOverlayOpacity: v ?? DEFAULT_TOPOLOGY_DISPLAY.worldMapAeOverlayOpacity })}
                className="webae-full-width"
              />
            </SettingRow>
          )}
        </>
      )}

      {showRenderMode && (
        <>
          <SettingRow label={t('topologyRenderMode')}>
            <Segmented
              block
              value={draft.renderMode}
              onChange={(v) => patch({ renderMode: v as TopologyRenderMode })}
              options={[
                { value: 'abstract', label: t('topologyRenderMode_abstract') },
                { value: 'simulated', label: t('topologyRenderMode_simulated') },
              ]}
            />
          </SettingRow>
          {(draft.renderMode === 'abstract' || draft.renderMode === 'simulated') && (
            <SettingRow label={t('topologyAbstractLayout')}>
              <Segmented
                block
                value={draft.abstractLayout}
                onChange={(v) => patch({ abstractLayout: v as 'tree' | 'star' })}
                options={[
                  { value: 'tree', label: t('topologyLayout_tree') },
                  { value: 'star', label: t('topologyLayout_star') },
                ]}
              />
            </SettingRow>
          )}
        </>
      )}

      {!showWorldMapSettings && (
        <>
      <SettingRow label={t('topologyLayoutDirection')}>
        <Segmented
          block
          value={draft.layoutDirection}
          onChange={(v) => patch({ layoutDirection: v as TopologyLayoutDirection })}
          options={[
            { value: 'LR', label: t('topologyLayout_LR') },
            { value: 'TB', label: t('topologyLayout_TB') },
          ]}
        />
      </SettingRow>

      <Divider orientation="left" plain>
        {t('topologySettingsSpacing')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <SettingRow label={t('topologyDepthGap')}>
          <InputNumber min={80} max={240} value={draft.depthGap} onChange={(v) => patch({ depthGap: v ?? DEFAULT_TOPOLOGY_DISPLAY.depthGap })} style={{ width: '100%' }} />
        </SettingRow>
        <SettingRow label={t('topologySiblingGap')}>
          <InputNumber min={48} max={160} value={draft.siblingGap} onChange={(v) => patch({ siblingGap: v ?? DEFAULT_TOPOLOGY_DISPLAY.siblingGap })} style={{ width: '100%' }} />
        </SettingRow>
        <SettingRow label={t('topologyNodeRadius')}>
          <InputNumber min={12} max={32} value={draft.nodeRadius} onChange={(v) => patch({ nodeRadius: v ?? DEFAULT_TOPOLOGY_DISPLAY.nodeRadius })} style={{ width: '100%' }} />
        </SettingRow>
        <SettingRow label={t('topologyCableCellPx')}>
          <InputNumber min={16} max={40} value={draft.cableCellPx} onChange={(v) => patch({ cableCellPx: v ?? DEFAULT_TOPOLOGY_DISPLAY.cableCellPx })} style={{ width: '100%' }} />
        </SettingRow>
      </Space>

      <Divider orientation="left" plain>
        {t('topologySettingsLabels')}
      </Divider>
      <SettingRow label={t('topologyLabelStrategy')}>
        <Segmented
          block
          value={draft.labelStrategy}
          onChange={(v) => patch({ labelStrategy: v as TopologyLabelStrategy })}
          options={[
            { value: 'external', label: t('topologyLabel_external') },
            { value: 'below', label: t('topologyLabel_below') },
            { value: 'hover', label: t('topologyLabel_hover') },
          ]}
        />
      </SettingRow>
      <SettingRow label={t('topologyShowCountLabels')}>
        <Switch checked={draft.showCountLabels} onChange={(v) => patch({ showCountLabels: v })} />
      </SettingRow>
      <SettingRow label={t('topologyShowEdgeLabels')}>
        <Switch checked={draft.showEdgeChannelLabels} onChange={(v) => patch({ showEdgeChannelLabels: v })} />
      </SettingRow>
      <SettingRow label={t('topologyHideCableNodes')}>
        <Switch checked={draft.hideCableNodes} onChange={(v) => patch({ hideCableNodes: v })} />
      </SettingRow>

      <Divider orientation="left" plain>
        {t('topologySettingsColors')}
      </Divider>
      <Text type="secondary" className="webae-setting-row-hint">
        {t('topologyAeColorHint')}
      </Text>
      {(['smart', 'covered', 'dense'] as const).map((key) => (
        <SettingRow key={key} label={t(`topologyCable_${key}`)}>
          <Select
            className="webae-full-width"
            value={draft.cableColorPreset[key]}
            onChange={(v) => patchCablePreset(key, v as AeCableColorId)}
            options={colorOptions}
          />
        </SettingRow>
      ))}
        </>
      )}
    </Drawer>
  );
}
