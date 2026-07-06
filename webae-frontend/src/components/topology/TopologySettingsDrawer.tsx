import { useEffect, useRef, useState } from 'react';
import { Button, Divider, Drawer, InputNumber, Segmented, Select, Space, Switch, Typography } from 'antd';
import { useI18n } from '@/i18n';
import {
  DEFAULT_TOPOLOGY_DISPLAY,
  type TopologyDisplaySettings,
  type TopologyLabelStrategy,
  type TopologyLayoutDirection,
  type TopologyRenderMode,
} from '@/types/topologyDisplay';
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
}

function clampSettings(s: TopologyDisplaySettings): TopologyDisplaySettings {
  return {
    ...s,
    depthGap: Math.max(80, Math.min(240, s.depthGap)),
    siblingGap: Math.max(48, Math.min(160, s.siblingGap)),
    labelMargin: Math.max(8, Math.min(48, s.labelMargin)),
    nodeRadius: Math.max(12, Math.min(32, s.nodeRadius)),
    cableCellPx: Math.max(16, Math.min(40, s.cableCellPx)),
    nodeBlockPx: Math.max(24, Math.min(48, s.nodeBlockPx)),
  };
}

function cloneSettings(s: TopologyDisplaySettings): TopologyDisplaySettings {
  return {
    ...s,
    colors: { ...s.colors },
    cableColorPreset: { ...s.cableColorPreset },
  };
}

export function TopologySettingsDrawer({
  open,
  onClose,
  settings,
  onChange,
  onReset,
  showRenderMode = true,
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
    onChange(clampSettings(draft));
    onClose();
  };

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
        </>
      )}

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
    </Drawer>
  );
}
