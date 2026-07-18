import { useEffect, useState } from 'react';
import { Drawer, InputNumber, Typography, Space, Divider, Button, List, Popconfirm, Input, message, Switch, Tabs, Select } from 'antd';
import { SaveOutlined, DeleteOutlined, CopyOutlined, ImportOutlined } from '@ant-design/icons';
import { useI18n } from '@/i18n';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { exportWidgetsJson, parseWidgetsImport } from '@/utils/widgetGridActions';
import { SettingRow } from '@/components/common/SettingRow';
import { AlignmentGrid } from './AlignmentGrid';
import { ColorField } from './ColorField';

const { Text } = Typography;

interface DashboardSettingsDrawerProps {
  open: boolean;
  onClose: () => void;
  settings: DashboardSettings;
  onChange: (s: DashboardSettings) => void;
  /** When provided, advanced tab shows widget import/export. */
  widgets?: DashboardWidgetConfig[];
  onWidgetsChange?: (widgets: DashboardWidgetConfig[]) => void;
}

function clampDraft(settings: DashboardSettings): DashboardSettings {
  return {
    ...settings,
    margin: Math.max(0, Math.min(30, settings.margin ?? 12)),
    widgetGap: Math.max(0, Math.min(48, settings.widgetGap ?? 12)),
    contentInset: Math.max(0, Math.min(24, settings.contentInset ?? 0)),
    borderWidth: Math.max(0, Math.min(6, settings.borderWidth ?? 1)),
    fontSize: Math.max(10, Math.min(24, settings.fontSize ?? 14)),
    chartSize: Math.max(0, Math.min(100, settings.chartSize ?? 70)),
  };
}

export function DashboardSettingsDrawer({
  open,
  onClose,
  settings,
  onChange,
  widgets,
  onWidgetsChange,
}: DashboardSettingsDrawerProps) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<DashboardSettings>(settings);
  const [presetName, setPresetName] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [importText, setImportText] = useState('');

  useEffect(() => {
    if (open) {
      setDraft({ ...settings, defaultColors: { ...settings.defaultColors } });
    }
  }, [open, settings]);

  const patch = (p: Partial<DashboardSettings>) => setDraft((prev) => ({ ...prev, ...p }));
  const patchColors = (p: Partial<DashboardSettings['defaultColors']>) =>
    setDraft((prev) => ({ ...prev, defaultColors: { ...prev.defaultColors, ...p } }));

  const handleClose = () => {
    onClose();
  };

  const handleApply = () => {
    onChange(clampDraft(draft));
    onClose();
  };

  const addPreset = () => {
    const name = presetName.trim();
    if (!name) return;
    if (draft.colorPresets.some((p) => p.name === name)) {
      void message.warning(t('presetConfirmOverwrite').replace('{name}', name));
    }
    patch({
      colorPresets: [
        ...draft.colorPresets.filter((p) => p.name !== name),
        { name, colors: { ...draft.defaultColors } },
      ],
    });
    setPresetName('');
  };

  const applyPreset = (name: string) => {
    const preset = draft.colorPresets.find((p) => p.name === name);
    if (preset) patchColors(preset.colors);
  };

  const deletePreset = (name: string) => {
    patch({ colorPresets: draft.colorPresets.filter((p) => p.name !== name) });
  };

  const handleExportWidgets = async () => {
    if (!widgets) {
      void message.warning(t('dashCfgExportWidgetsEmpty'));
      return;
    }
    try {
      await navigator.clipboard.writeText(exportWidgetsJson(widgets));
      void message.success(t('dashCfgExportWidgetsDone'));
    } catch {
      void message.error(t('dashCfgExportWidgetsFailed'));
    }
  };

  const handleImportWidgets = () => {
    try {
      const imported = parseWidgetsImport(importText);
      onWidgetsChange?.(imported);
      setImportOpen(false);
      setImportText('');
      void message.success(t('dashCfgImportWidgetsDone'));
    } catch (e) {
      const detail = e instanceof Error && e.message ? e.message : '';
      void message.error(
        detail ? `${t('dashCfgImportWidgetsFailed')}: ${detail}` : t('dashCfgImportWidgetsFailed')
      );
    }
  };

  const widgetImportExportPanel =
    widgets && onWidgetsChange ? (
      <>
        <Divider orientation="left">{t('dashCfgWidgetsIo')}</Divider>
        <Space wrap>
          <Button icon={<CopyOutlined />} onClick={handleExportWidgets}>
            {t('dashCfgExportWidgets')}
          </Button>
          <Button icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>
            {t('dashCfgImportWidgets')}
          </Button>
        </Space>
        <Input.TextArea
          style={{ display: importOpen ? 'block' : 'none', marginTop: 8 }}
          rows={6}
          value={importText}
          onChange={(e) => setImportText(e.target.value)}
          placeholder={t('dashCfgImportWidgetsPlaceholder')}
        />
        {importOpen && (
          <Space style={{ marginTop: 8 }}>
            <Button type="primary" onClick={handleImportWidgets} disabled={!importText.trim()}>
              {t('dashCfgImportWidgetsApply')}
            </Button>
            <Button onClick={() => { setImportOpen(false); setImportText(''); }}>
              {t('cancel')}
            </Button>
          </Space>
        )}
      </>
    ) : null;

  const presetPanel = (
    <>
      <Text strong>{t('dashCfgPresets')}</Text>
      <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
        {t('dashCfgPresetsHint')}
      </Text>
      <Space.Compact style={{ width: '100%', marginTop: 8 }}>
        <Input
          placeholder={t('dashCfgPresetNamePlaceholder')}
          value={presetName}
          onChange={(e) => setPresetName(e.target.value)}
          onPressEnter={addPreset}
        />
        <Button type="primary" icon={<SaveOutlined />} onClick={addPreset}>
          {t('dashCfgPresetAdd')}
        </Button>
      </Space.Compact>
      {draft.colorPresets.length > 0 && (
        <List
          size="small"
          style={{ marginTop: 8 }}
          dataSource={draft.colorPresets}
          renderItem={(preset) => (
            <List.Item
              actions={[
                <Button key="apply" size="small" type="primary" onClick={() => applyPreset(preset.name)}>
                  {t('dashCfgPresetApply')}
                </Button>,
                <Popconfirm
                  key="delete"
                  title={t('dashCfgPresetDeleteConfirm').replace('{name}', preset.name)}
                  onConfirm={() => deletePreset(preset.name)}
                >
                  <Button size="small" danger icon={<DeleteOutlined />}>
                    {t('dashCfgPresetDelete')}
                  </Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta title={preset.name} />
            </List.Item>
          )}
        />
      )}
    </>
  );

  return (
    <Drawer
      title={t('dashboardSettings')}
      open={open}
      onClose={handleClose}
      width={420}
      destroyOnClose
      footer={
        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
          <Button onClick={handleClose}>{t('cancel')}</Button>
          <Button type="primary" onClick={handleApply}>{t('apply')}</Button>
        </Space>
      }
    >
      <Tabs
        defaultActiveKey="layout"
        items={[
          {
            key: 'layout',
            label: t('dashCfgTabLayout'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <SettingRow label={t('dashCfgMargin')}>
                  <InputNumber
                    min={0}
                    max={30}
                    value={draft.margin}
                    onChange={(v) => patch({ margin: v ?? undefined })}
                    style={{ width: '100%' }}
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgWidgetGap')}>
                  <InputNumber
                    min={0}
                    max={48}
                    value={draft.widgetGap ?? 12}
                    onChange={(v) => patch({ widgetGap: v ?? undefined })}
                    style={{ width: '100%' }}
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgContentInset')} hint={t('dashCfgContentInsetHint')}>
                  <InputNumber
                    min={0}
                    max={24}
                    value={draft.contentInset ?? 0}
                    onChange={(v) => patch({ contentInset: v ?? undefined })}
                    style={{ width: '100%' }}
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgBorderWidth')}>
                  <InputNumber
                    min={0}
                    max={6}
                    value={draft.borderWidth}
                    onChange={(v) => patch({ borderWidth: v ?? undefined })}
                    style={{ width: '100%' }}
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgAlignment')}>
                  <AlignmentGrid value={draft.defaultAlignment} onChange={(v) => patch({ defaultAlignment: v })} />
                </SettingRow>
              </Space>
            ),
          },
          {
            key: 'chart',
            label: t('dashCfgTabChart'),
            children: (
              <Space direction="vertical" className="webae-full-width" size="middle">
                <SettingRow label={t('dashCfgFontSize')}>
                  <InputNumber
                    min={10}
                    max={24}
                    value={draft.fontSize}
                    onChange={(v) => patch({ fontSize: v ?? undefined })}
                    className="webae-full-width"
                    addonAfter="px"
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgChartSize')}>
                  <InputNumber
                    min={0}
                    max={100}
                    value={draft.chartSize}
                    onChange={(v) => patch({ chartSize: v ?? undefined })}
                    className="webae-full-width"
                    addonAfter="%"
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgChartStretchMode')}>
                  <Select
                    className="webae-full-width"
                    value={draft.chartStretchMode ?? 'stretchX'}
                    onChange={(v) => patch({ chartStretchMode: v })}
                    options={[
                      { label: t('dashCfgStretchFit'), value: 'fit' },
                      { label: t('dashCfgStretchX'), value: 'stretchX' },
                      { label: t('dashCfgStretchFill'), value: 'fill' },
                    ]}
                  />
                </SettingRow>
                <SettingRow label={t('dashCfgChartValueAxis')}>
                  <Switch checked={draft.chartShowValueAxis ?? false} onChange={(c) => patch({ chartShowValueAxis: c })} />
                </SettingRow>
                <SettingRow label={t('dashCfgChartTimeAxis')}>
                  <Switch checked={draft.chartShowTimeAxis ?? false} onChange={(c) => patch({ chartShowTimeAxis: c })} />
                </SettingRow>
                <SettingRow label={t('dashCfgShowLastUpdated')}>
                  <Switch checked={draft.showLastUpdated ?? false} onChange={(c) => patch({ showLastUpdated: c })} />
                </SettingRow>
              </Space>
            ),
          },
          {
            key: 'colors',
            label: t('dashCfgTabColors'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="small">
                <ColorField label={t('dashCfgTitleColor')} value={draft.defaultColors.titleColor} onChange={(v) => patchColors({ titleColor: v })} />
                <ColorField label={t('dashCfgChartColor')} value={draft.defaultColors.chartColor} onChange={(v) => patchColors({ chartColor: v })} />
                <ColorField label={t('dashCfgIconColor')} value={draft.defaultColors.iconColor} onChange={(v) => patchColors({ iconColor: v })} />
                <ColorField label={t('dashCfgBackgroundColor')} value={draft.defaultColors.backgroundColor} onChange={(v) => patchColors({ backgroundColor: v })} />
                <ColorField label={t('dashCfgBorderColor')} value={draft.defaultColors.borderColor} onChange={(v) => patchColors({ borderColor: v })} />
              </Space>
            ),
          },
          {
            key: 'advanced',
            label: t('dashCfgTabAdvanced'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {widgetImportExportPanel}
                <Divider orientation="left">{t('dashCfgColorPresets')}</Divider>
                {presetPanel}
              </Space>
            ),
          },
        ]}
      />
    </Drawer>
  );
}
