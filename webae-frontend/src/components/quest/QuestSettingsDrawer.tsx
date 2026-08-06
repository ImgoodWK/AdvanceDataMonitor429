import { Button, Divider, Drawer, InputNumber, Slider, Space, Switch, Typography } from 'antd';
import { SettingRow } from '@/components/common/SettingRow';
import { useI18n } from '@/i18n';import { type QuestDisplaySettings } from '@/types/questDisplay';

const { Title } = Typography;

interface QuestSettingsDrawerProps {  open: boolean;
  onClose: () => void;
  settings: QuestDisplaySettings;
  onChange: (patch: Partial<QuestDisplaySettings>) => void;
  onReset: () => void;
}

export function QuestSettingsDrawer({  open,
  onClose,
  settings,
  onChange,
  onReset,
}: QuestSettingsDrawerProps) {
  const { t } = useI18n();

  const patch = (p: Partial<QuestDisplaySettings>) => {
    onChange(p);
  };

  return (
    <Drawer
      title={t('quest.settingsTitle')}
      open={open}
      onClose={onClose}
      width={360}
      footer={
        <Space>
          <Button onClick={onReset}>{t('quest.settingsReset')}</Button>
          <Button type="primary" onClick={onClose}>
            {t('quest.settingsClose')}
          </Button>
        </Space>
      }
    >
      <Title level={5} style={{ marginTop: 0 }}>
        {t('quest.settings.layout')}
      </Title>
      <SettingRow label={t('quest.settings.nodeSpacing')} hint={t('quest.settings.nodeSpacingHint')}>
        <Slider
          min={0.6}
          max={3.0}
          step={0.05}
          value={settings.coordScale}
          onChange={(v) => patch({ coordScale: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.fitPadding')} hint={t('quest.settings.fitPaddingHint')}>
        <Slider
          min={16}
          max={64}
          step={4}
          value={settings.fitPadding}
          onChange={(v) => patch({ fitPadding: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.autoFitOnSettingsChange')}>
        <Switch
          checked={settings.autoFitOnSettingsChange}
          onChange={(v) => patch({ autoFitOnSettingsChange: v })}
        />
      </SettingRow>

      <Divider />
      <Title level={5}>{t('quest.settings.nodes')}</Title>
      <SettingRow label={t('quest.settings.minNodeSize')} hint={t('quest.settings.minNodeSizeHint')}>
        <Slider
          min={48}
          max={112}
          step={4}
          value={settings.minNodeSize}
          onChange={(v) => patch({ minNodeSize: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.nodeSizeScale')} hint={t('quest.settings.nodeSizeScaleHint')}>
        <Slider
          min={0.7}
          max={1.8}
          step={0.05}
          value={settings.nodeSizeScale}
          onChange={(v) => patch({ nodeSizeScale: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.iconFillPercent')} hint={t('quest.settings.iconFillPercentHint')}>
        <Slider
          min={60}
          max={95}
          step={1}
          value={settings.iconFillPercent}
          onChange={(v) => patch({ iconFillPercent: v })}
        />
      </SettingRow>

      <Divider />
      <Title level={5}>{t('quest.settings.labels')}</Title>
      <SettingRow label={t('quest.settings.labelFontSize')}>
        <InputNumber
          min={12}
          max={22}
          value={settings.labelFontSize}
          onChange={(v) => patch({ labelFontSize: v ?? settings.labelFontSize })}
          style={{ width: '100%' }}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.labelMaxWidth')} hint={t('quest.settings.labelMaxWidthHint')}>
        <InputNumber
          min={180}
          max={320}
          step={10}
          value={settings.labelMaxWidth}
          onChange={(v) => patch({ labelMaxWidth: v ?? settings.labelMaxWidth })}
          style={{ width: '100%' }}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.edgeWidth')}>
        <Slider
          min={1.5}
          max={5}
          step={0.5}
          value={settings.edgeWidth}
          onChange={(v) => patch({ edgeWidth: v })}
        />
      </SettingRow>

      <Divider />
      <Title level={5}>{t('quest.settings.display')}</Title>
      <SettingRow label={t('quest.settings.showNodeLabels')}>
        <Switch checked={settings.showLabels} onChange={(v) => patch({ showLabels: v })} />
      </SettingRow>
      <SettingRow label={t('quest.settings.showGhostNodes')} hint={t('quest.settings.showGhostNodesHint')}>
        <Switch checked={settings.showGhostNodes} onChange={(v) => patch({ showGhostNodes: v })} />
      </SettingRow>

      <Divider />
      <Title level={5}>{t('quest.settings.sidebar')}</Title>
      <SettingRow label={t('quest.settings.linePanelWidth')}>
        <Slider
          min={200}
          max={360}
          step={10}
          value={settings.linePanelWidth}
          onChange={(v) => patch({ linePanelWidth: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.detailPanelWidth')}>
        <Slider
          min={300}
          max={520}
          step={10}
          value={settings.detailPanelWidth}
          onChange={(v) => patch({ detailPanelWidth: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.linePanelIconSize')}>
        <Slider
          min={24}
          max={48}
          step={2}
          value={settings.linePanelIconSize}
          onChange={(v) => patch({ linePanelIconSize: v })}
        />
      </SettingRow>
      <SettingRow label={t('quest.settings.linePanelFontSize')}>
        <InputNumber
          min={12}
          max={18}
          value={settings.linePanelFontSize}
          onChange={(v) => patch({ linePanelFontSize: v ?? settings.linePanelFontSize })}
          style={{ width: '100%' }}
        />
      </SettingRow>

      <Divider />
      <Title level={5}>{t('quest.settings.interaction')}</Title>
      <SettingRow label={t('quest.settings.centerMinZoom')} hint={t('quest.settings.centerMinZoomHint')}>
        <Slider
          min={1.0}
          max={2.0}
          step={0.05}
          value={settings.centerMinZoom}
          onChange={(v) => patch({ centerMinZoom: v })}
        />
      </SettingRow>
    </Drawer>
  );
}
