import { useEffect, useRef, useState } from 'react';
import { Button, Divider, Drawer, InputNumber, Segmented, Select, Space, Switch, Typography } from 'antd';
import { useI18n } from '@/i18n';
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
    worldMapAeOverlayOpacity: Math.max(0.5, Math.min(1, s.worldMapAeOverlayOpacity ?? 0.85)),
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
        <span
          style={{
            display: 'inline-block',
            width: 14,
            height: 14,
            borderRadius: 2,
            background: c.hex,
            border: '1px solid var(--border)',
          }}
        />
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
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary">
                {t('worldMapTerrainSource') || '地形来源'}:{' '}
              </Text>
              <Text strong>
                {terrainSource === 'dynmap'
                  ? t('worldMapTerrainSource_dynmap') || 'Dynmap 地形'
                  : t('worldMapTerrainSource_self') || '内置渲染'}
              </Text>
            </div>
          )}
          {clientCaptureMode && clientCaptureMode !== 'off' && (
            <div style={{ marginBottom: 16 }}>
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
          {/* Open in Dynmap button */}
          {dynmapBaseUrl && (
            <div style={{ marginBottom: 16 }}>
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
          <Text type="secondary">{t('worldMapObliqueDirection')}</Text>
          <Select
            style={{ width: '100%', marginTop: 8, marginBottom: 16 }}
            value={draft.worldMapObliqueDirection}
            onChange={(v) => patch({ worldMapObliqueDirection: v as WorldMapObliqueDirection })}
            options={obliqueOptions}
          />
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            {t('worldMapObliqueDirectionHint')}
          </Text>
          <Text type="secondary">{t('worldMapQuality')}</Text>
          <Segmented
            style={{ width: '100%', marginTop: 8, marginBottom: 8 }}
            value={draft.worldMapQuality}
            onChange={(v) => patch({ worldMapQuality: v as WorldMapQualityTierId })}
            options={qualityOptions}
            block
          />
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            {t('worldMapQualityHint')}
          </Text>
          <Divider orientation="left" plain>
            {t('worldMapLayerSettingsTitle')}
          </Divider>
          <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
            <Space>
              <Switch checked={draft.showWorldMapTerrain} onChange={(v) => patch({ showWorldMapTerrain: v })} />
              <Text>{t('worldMapLayerTerrain')}</Text>
            </Space>
            <Space>
              <Switch checked={draft.showWorldMapAeOverlay} onChange={(v) => patch({ showWorldMapAeOverlay: v })} />
              <Text>{t('worldMapLayerAeOverlay')}</Text>
            </Space>
            <Space>
              <Switch
                checked={draft.showWorldMapDeviceIcons}
                onChange={(v) => patch({ showWorldMapDeviceIcons: v })}
              />
              <Text>{t('worldMapLayerDeviceIcons')}</Text>
            </Space>
          </Space>
          {draft.showWorldMapAeOverlay && (
            <div style={{ marginBottom: 16 }}>
              <Text>{t('worldMapAeOverlayOpacity')}</Text>
              <InputNumber
                min={0.5}
                max={1}
                step={0.05}
                value={draft.worldMapAeOverlayOpacity}
                onChange={(v) => patch({ worldMapAeOverlayOpacity: v ?? DEFAULT_TOPOLOGY_DISPLAY.worldMapAeOverlayOpacity })}
                style={{ width: '100%', marginTop: 4 }}
              />
            </div>
          )}
        </>
      )}

      {showRenderMode && (
        <>
          <Text type="secondary">{t('topologyRenderMode')}</Text>
          <Segmented
            block
            style={{ marginTop: 8, marginBottom: 16 }}
            value={draft.renderMode}
            onChange={(v) => patch({ renderMode: v as TopologyRenderMode })}
            options={[
              { value: 'abstract', label: t('topologyRenderMode_abstract') },
              { value: 'simulated', label: t('topologyRenderMode_simulated') },
            ]}
          />
          {(draft.renderMode === 'abstract' || draft.renderMode === 'simulated') && (
            <>
              <Text type="secondary">{t('topologyAbstractLayout')}</Text>
              <Segmented
                block
                style={{ marginTop: 8, marginBottom: 16 }}
                value={draft.abstractLayout}
                onChange={(v) => patch({ abstractLayout: v as 'tree' | 'star' })}
                options={[
                  { value: 'tree', label: t('topologyLayout_tree') },
                  { value: 'star', label: t('topologyLayout_star') },
                ]}
              />
            </>
          )}
        </>
      )}

      {!showWorldMapSettings && (
        <>
      <Text type="secondary">{t('topologyLayoutDirection')}</Text>
      <Segmented
        block
        style={{ marginTop: 8, marginBottom: 16 }}
        value={draft.layoutDirection}
        onChange={(v) => patch({ layoutDirection: v as TopologyLayoutDirection })}
        options={[
          { value: 'LR', label: t('topologyLayout_LR') },
          { value: 'TB', label: t('topologyLayout_TB') },
        ]}
      />

      <Divider orientation="left" plain>
        {t('topologySettingsSpacing')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <Text>{t('topologyDepthGap')}</Text>
          <InputNumber min={80} max={240} value={draft.depthGap} onChange={(v) => patch({ depthGap: v ?? DEFAULT_TOPOLOGY_DISPLAY.depthGap })} style={{ width: '100%', marginTop: 4 }} />
        </div>
        <div>
          <Text>{t('topologySiblingGap')}</Text>
          <InputNumber min={48} max={160} value={draft.siblingGap} onChange={(v) => patch({ siblingGap: v ?? DEFAULT_TOPOLOGY_DISPLAY.siblingGap })} style={{ width: '100%', marginTop: 4 }} />
        </div>
        <div>
          <Text>{t('topologyNodeRadius')}</Text>
          <InputNumber min={12} max={32} value={draft.nodeRadius} onChange={(v) => patch({ nodeRadius: v ?? DEFAULT_TOPOLOGY_DISPLAY.nodeRadius })} style={{ width: '100%', marginTop: 4 }} />
        </div>
        <div>
          <Text>{t('topologyCableCellPx')}</Text>
          <InputNumber min={16} max={40} value={draft.cableCellPx} onChange={(v) => patch({ cableCellPx: v ?? DEFAULT_TOPOLOGY_DISPLAY.cableCellPx })} style={{ width: '100%', marginTop: 4 }} />
        </div>
      </Space>

      <Divider orientation="left" plain>
        {t('topologySettingsLabels')}
      </Divider>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Text>{t('topologyLabelStrategy')}</Text>
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
        <Space>
          <Switch checked={draft.showCountLabels} onChange={(v) => patch({ showCountLabels: v })} />
          <Text>{t('topologyShowCountLabels')}</Text>
        </Space>
        <Space>
          <Switch checked={draft.showEdgeChannelLabels} onChange={(v) => patch({ showEdgeChannelLabels: v })} />
          <Text>{t('topologyShowEdgeLabels')}</Text>
        </Space>
        <Space>
          <Switch checked={draft.hideCableNodes} onChange={(v) => patch({ hideCableNodes: v })} />
          <Text>{t('topologyHideCableNodes')}</Text>
        </Space>
      </Space>

      <Divider orientation="left" plain>
        {t('topologySettingsColors')}
      </Divider>
      <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
        {t('topologyAeColorHint')}
      </Text>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {(['smart', 'covered', 'dense'] as const).map((key) => (
          <div key={key}>
            <Text style={{ display: 'block', marginBottom: 4 }}>{t(`topologyCable_${key}`)}</Text>
            <Select
              style={{ width: '100%' }}
              value={draft.cableColorPreset[key]}
              onChange={(v) => patchCablePreset(key, v as AeCableColorId)}
              options={colorOptions}
            />
          </div>
        ))}
      </Space>
        </>
      )}
    </Drawer>
  );
}
