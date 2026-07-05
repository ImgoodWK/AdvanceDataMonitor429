import { Modal, Select, Input, InputNumber, Space, Typography, Switch, Tabs, Button } from 'antd';
import type { ReactNode } from 'react';
import { useI18n } from '@/i18n';
import {
  ALL_ALIGNMENTS,
  type Alignment,
  type DashboardSettings,
  type DashboardWidgetConfig,
} from '@/utils/presets';
import { AlignmentGrid } from './AlignmentGrid';
import { WidgetColorSection } from './WidgetColorSection';
import { getValidChartTypes } from '@/utils/dataSourceChartMap';

const { Text } = Typography;

interface EditWidgetModalProps {
  open: boolean;
  widget: DashboardWidgetConfig | null;
  settings: DashboardSettings;
  onWidgetChange: (w: DashboardWidgetConfig) => void;
  onOk: () => void;
  onCancel: () => void;
  allowedDataSources?: string[];
  allowedWidgetTypes?: DashboardWidgetConfig['type'][];
}

const WIDGET_TYPES: DashboardWidgetConfig['type'][] = [
  'statCard', 'progressBar', 'lineChart', 'barChart', 'pieChart',
  'dataTable', 'gauge', 'radarChart',
];

const DATA_SOURCES = [
  'itemCount', 'fluidCount', 'essentiaCount', 'bytesUsed', 'bytesMax', 'bytesPercent',
  'euStored', 'euMax', 'euPercent', 'euInRate', 'euOutRate', 'steamStored',
  'activeCpu', 'busyCpu', 'cpuBusyRatio', 'gtMachineCount', 'gtActiveCount',
  'itemTotal', 'fluidTotal', 'topItems', 'cpuList', 'gtMachineList',
  'powerHistory', 'storageByCategory', 'machineByStatus', 'networkCompare', 'networkBalance',
  'playerOnlineCount', 'playerOnlineTrend',
];

const STRETCH_OPTIONS = [
  { value: 'fit', labelKey: 'dashCfgStretchFit' },
  { value: 'stretchX', labelKey: 'dashCfgStretchX' },
  { value: 'fill', labelKey: 'dashCfgStretchFill' },
] as const;

export function EditWidgetModal({
  open,
  widget,
  settings,
  onWidgetChange,
  onOk,
  onCancel,
  allowedDataSources,
  allowedWidgetTypes,
}: EditWidgetModalProps) {
  const { t } = useI18n();
  if (!widget) return null;

  const allWidgetTypes = allowedWidgetTypes ?? WIDGET_TYPES;
  const allDataSources = allowedDataSources ?? DATA_SOURCES;

  const patch = (p: Partial<DashboardWidgetConfig>) => onWidgetChange({ ...widget, ...p });

  const ensureColors = () =>
    widget.colors ?? {
      inheritDefault: true,
      titleColor: '',
      chartColor: '',
      iconColor: '',
      backgroundColor: '',
      borderColor: '',
      chartLineColor: '',
      chartAreaColor: '',
      chartGridColor: '',
      chartPointColor: '',
      chartSecondaryLineColor: '',
      chartSecondaryAreaColor: '',
      progressTrackColor: '',
      progressFillColor: '',
      gaugeTrackColor: '',
      gaugeStrokeColor: '',
      barSegmentColors: [],
      pieSliceColors: [],
      dataTableRowAltColor: '',
      axisTextColor: '',
      radarAxisColor: '',
      categoryItemsColor: '',
      categoryFluidsColor: '',
      categoryEssentiaColor: '',
    };

  const patchColors = (p: Partial<NonNullable<DashboardWidgetConfig['colors']>>) =>
    patch({ colors: { ...ensureColors(), ...p } });

  const validTypesForDs = getValidChartTypes(widget.dataSource);
  const availableWidgetTypes = allWidgetTypes.filter((tp) => validTypesForDs.includes(tp));
  const availableDataSources = allDataSources.filter((ds) =>
    getValidChartTypes(ds).includes(widget.type)
  );

  const colorsInherit = ensureColors().inheritDefault;
  const isChartType =
    widget.type === 'lineChart' ||
    widget.type === 'barChart' ||
    widget.type === 'pieChart' ||
    widget.type === 'radarChart';

  const inheritSwitch = (
    label: string,
    globalHint: string,
    enabled: boolean,
    onEnable: (checked: boolean) => void,
    field: ReactNode
  ) => (
    <div>
      <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Text strong>{label}</Text>
        <Space>
          <Text type="secondary" style={{ fontSize: '0.72rem' }}>
            {globalHint}
          </Text>
          <Switch size="small" checked={enabled} onChange={onEnable} aria-label={label} />
        </Space>
      </Space>
      {enabled && field}
    </div>
  );

  return (
    <Modal
      title={t('editWidget')}
      open={open}
      onOk={onOk}
      onCancel={onCancel}
      okText={t('apply')}
      cancelText={t('cancel')}
      width={560}
      destroyOnClose
    >
      <Tabs
        defaultActiveKey="basic"
        items={[
          {
            key: 'basic',
            label: t('editWidgetSection_basic'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <div>
                  <Text strong>{t('editWidget_type')}</Text>
                  <Select
                    style={{ width: '100%', marginTop: 4 }}
                    value={widget.type}
                    onChange={(v) => {
                      const newValid = getValidChartTypes(widget.dataSource).includes(v);
                      patch({
                        type: v,
                        dataSource: newValid ? widget.dataSource : availableDataSources[0] ?? widget.dataSource,
                      });
                    }}
                    options={availableWidgetTypes.map((tp) => ({ label: t('widgetType_' + tp), value: tp }))}
                  />
                </div>
                <div>
                  <Text strong>{t('dataSource')}</Text>
                  <Select
                    style={{ width: '100%', marginTop: 4 }}
                    value={widget.dataSource}
                    onChange={(v) => patch({ dataSource: v })}
                    options={availableDataSources.map((ds) => ({ label: t('dataSource_' + ds), value: ds }))}
                  />
                </div>
                <div>
                  <Text strong>{t('editWidget_title')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                    {t('editWidget_titleHint')}
                  </Text>
                  <Input
                    style={{ marginTop: 4 }}
                    value={widget.title}
                    onChange={(e) => patch({ title: e.target.value })}
                    placeholder={t('editWidget_titleHint')}
                    allowClear
                  />
                </div>
                {widget.type === 'radarChart' && (
                  <div>
                    <Text strong>{t('dashCfgRadarAxes')}</Text>
                    <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                      {t('dashCfgRadarAxesHint')}
                    </Text>
                    <Space direction="vertical" style={{ width: '100%', marginTop: 8 }} size="small">
                      {(widget.radarAxes?.length ? widget.radarAxes : [{ dataSource: 'itemCount' }]).map((axis, idx) => (
                        <Space key={idx} wrap style={{ width: '100%' }}>
                          <Select
                            style={{ minWidth: 180 }}
                            value={axis.dataSource}
                            onChange={(v) => {
                              const axes = [...(widget.radarAxes || [{ dataSource: 'itemCount' }])];
                              axes[idx] = { ...axes[idx], dataSource: v };
                              patch({ radarAxes: axes });
                            }}
                            options={allDataSources.map((ds) => ({ label: t('dataSource_' + ds), value: ds }))}
                          />
                          <Input
                            placeholder={t('dashCfgRadarAxisLabel')}
                            value={axis.label || ''}
                            onChange={(e) => {
                              const axes = [...(widget.radarAxes || [{ dataSource: 'itemCount' }])];
                              axes[idx] = { ...axes[idx], label: e.target.value };
                              patch({ radarAxes: axes });
                            }}
                            style={{ width: 140 }}
                          />
                          <Button
                            danger
                            size="small"
                            disabled={(widget.radarAxes?.length || 1) <= 3}
                            onClick={() => {
                              const axes = (widget.radarAxes || []).filter((_, i) => i !== idx);
                              patch({ radarAxes: axes.length >= 3 ? axes : widget.radarAxes });
                            }}
                          >
                            {t('delete')}
                          </Button>
                        </Space>
                      ))}
                      <Button
                        size="small"
                        disabled={(widget.radarAxes?.length || 1) >= 8}
                        onClick={() => {
                          const axes = [...(widget.radarAxes || [{ dataSource: 'itemCount' }]), { dataSource: allDataSources[0] || 'itemCount' }];
                          patch({ radarAxes: axes.slice(0, 8) });
                        }}
                      >
                        {t('dashCfgRadarAxisAdd')}
                      </Button>
                    </Space>
                  </div>
                )}
              </Space>
            ),
          },
          {
            key: 'layout',
            label: t('editWidgetSection_layout'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Space>
                  <div>
                    <Text strong>{t('width')}</Text>
                    <InputNumber
                      style={{ marginTop: 4, display: 'block' }}
                      min={2}
                      max={12}
                      value={widget.width}
                      onChange={(v) => patch({ width: v ?? 3 })}
                    />
                  </div>
                  <div>
                    <Text strong>{t('height')}</Text>
                    <InputNumber
                      style={{ marginTop: 4, display: 'block' }}
                      min={2}
                      max={10}
                      value={widget.height}
                      onChange={(v) => patch({ height: v ?? 2 })}
                    />
                  </div>
                </Space>

                {inheritSwitch(
                  t('editWidget_contentInset'),
                  `${t('dashCfgInheritDefault')}: ${settings.contentInset ?? 0}px`,
                  widget.contentInset !== undefined,
                  (checked) => patch({ contentInset: checked ? settings.contentInset ?? 0 : undefined }),
                  <InputNumber
                    style={{ width: '100%', marginTop: 4 }}
                    min={0}
                    max={24}
                    value={widget.contentInset}
                    onChange={(v) => patch({ contentInset: v ?? 0 })}
                    addonAfter="px"
                  />
                )}

                {inheritSwitch(
                  t('editWidget_fontSize'),
                  `${t('dashCfgInheritDefault')}: ${settings.fontSize}px`,
                  widget.fontSize !== undefined,
                  (checked) => patch({ fontSize: checked ? settings.fontSize : undefined }),
                  <InputNumber
                    style={{ width: '100%', marginTop: 4 }}
                    min={10}
                    max={24}
                    value={widget.fontSize}
                    onChange={(v) => patch({ fontSize: v ?? settings.fontSize })}
                    addonAfter="px"
                  />
                )}

                {inheritSwitch(
                  t('editWidget_chartSize'),
                  `${t('dashCfgInheritDefault')}: ${settings.chartSize}%`,
                  widget.chartSize !== undefined,
                  (checked) => patch({ chartSize: checked ? settings.chartSize : undefined }),
                  <InputNumber
                    style={{ width: '100%', marginTop: 4 }}
                    min={0}
                    max={100}
                    value={widget.chartSize}
                    onChange={(v) => patch({ chartSize: v ?? settings.chartSize })}
                    addonAfter="%"
                  />
                )}

                {isChartType &&
                  inheritSwitch(
                    t('editWidget_chartStretchMode'),
                    `${t('dashCfgInheritDefault')}: ${t('dashCfgStretch' + (settings.chartStretchMode === 'stretchX' ? 'X' : settings.chartStretchMode === 'fill' ? 'Fill' : 'Fit'))}`,
                    widget.chartStretchMode !== undefined,
                    (checked) =>
                      patch({ chartStretchMode: checked ? settings.chartStretchMode ?? 'stretchX' : undefined }),
                    <Select
                      style={{ width: '100%', marginTop: 4 }}
                      value={widget.chartStretchMode ?? settings.chartStretchMode ?? 'stretchX'}
                      onChange={(v) => patch({ chartStretchMode: v })}
                      options={STRETCH_OPTIONS.map((o) => ({ label: t(o.labelKey), value: o.value }))}
                    />
                  )}

                <div>
                  <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>{t('editWidget_alignment')}</Text>
                    <Space>
                      <Text type="secondary" style={{ fontSize: '0.72rem' }}>
                        {t('dashCfgInheritDefault')}: {t('alignment_' + settings.defaultAlignment)}
                      </Text>
                      <Switch
                        size="small"
                        checked={widget.alignment !== undefined}
                        onChange={(checked) =>
                          patch({ alignment: checked ? settings.defaultAlignment : undefined })
                        }
                        aria-label={t('editWidget_alignment')}
                      />
                    </Space>
                  </Space>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.72rem' }}>
                    {t('editWidget_alignmentHint')}
                  </Text>
                  {widget.alignment !== undefined ? (
                    <div style={{ marginTop: 8 }}>
                      <AlignmentGrid
                        value={widget.alignment as Alignment}
                        onChange={(v) => patch({ alignment: v })}
                      />
                    </div>
                  ) : (
                    <div className="align-grid" aria-hidden style={{ opacity: 0.4, pointerEvents: 'none' }}>
                      {ALL_ALIGNMENTS.map((a) => (
                        <div key={a} className="align-grid-cell" />
                      ))}
                    </div>
                  )}
                </div>
              </Space>
            ),
          },
          {
            key: 'colors',
            label: t('editWidgetSection_colors'),
            children: (
              <WidgetColorSection
                widget={widget}
                settings={settings}
                t={t}
                colorsInherit={colorsInherit}
                ensureColors={ensureColors}
                patchColors={patchColors}
              />
            ),
          },
          {
            key: 'advanced',
            label: t('editWidgetSection_advanced'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {inheritSwitch(
                  t('editWidget_borderWidth'),
                  `${t('dashCfgInheritDefault')}: ${settings.borderWidth}px`,
                  !colorsInherit && ensureColors().borderWidth !== undefined,
                  (checked) => {
                    if (checked) {
                      patchColors({ inheritDefault: false, borderWidth: settings.borderWidth });
                    } else {
                      patchColors({ borderWidth: undefined });
                    }
                  },
                  <InputNumber
                    style={{ width: '100%', marginTop: 4 }}
                    min={0}
                    max={6}
                    value={ensureColors().borderWidth ?? settings.borderWidth}
                    onChange={(v) => patchColors({ inheritDefault: false, borderWidth: v ?? settings.borderWidth })}
                    addonAfter="px"
                    disabled={colorsInherit}
                  />
                )}

                {widget.type === 'statCard' && (
                  <>
                    <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Text strong>{t('editWidget_showDelta')}</Text>
                      <Switch
                        checked={widget.showDelta ?? false}
                        onChange={(checked) => patch({ showDelta: checked })}
                        aria-label={t('editWidget_showDelta')}
                      />
                    </Space>
                    {widget.showDelta && (
                      <div>
                        <Text strong>{t('editWidget_significantDigits')}</Text>
                        <InputNumber
                          style={{ width: '100%', marginTop: 4 }}
                          min={3}
                          max={12}
                          value={widget.significantDigits ?? 5}
                          onChange={(v) => patch({ significantDigits: v ?? 5 })}
                        />
                      </div>
                    )}
                  </>
                )}

                {widget.type === 'progressBar' && (
                  <div>
                    <Text strong style={{ fontSize: '0.8rem' }}>{t('progressStyle')}</Text>
                    <Select
                      style={{ width: '100%', marginTop: 4 }}
                      value={widget.style === 'circular' ? 'circular' : 'line'}
                      onChange={(v) => patch({ style: v === 'circular' ? 'circular' : 'horizontal' })}
                      options={[
                        { label: t('progressHorizontal'), value: 'line' },
                        { label: t('progressCircular'), value: 'circular' },
                      ]}
                    />
                  </div>
                )}

                {widget.type === 'dataTable' && (
                  <div>
                    <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Text strong>{t('dashCfgDataTableMaxRows')}</Text>
                      <InputNumber
                        min={1}
                        max={50}
                        value={widget.maxRows ?? 10}
                        onChange={(v) => patch({ maxRows: v ?? 10 })}
                      />
                    </Space>
                  </div>
                )}
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  );
}
