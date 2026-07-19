import { Modal, Select, Input, InputNumber, Space, Typography, Switch, Tabs, Button, Slider } from 'antd';
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
import { WidgetPinEditor } from './WidgetPinEditor';
import { getValidChartTypes } from '@/utils/dataSourceChartMap';
import { clampContentScale } from '@/utils/dashboardColumns';
import { defaultDataSourceForWidgetType, isLayoutOrFeedType } from '@/utils/dashboardTree';
import type { StorageDto, GtMachineDto } from '@/types/dto';
import { CHART_STYLES } from '@/theme/pageStyles';

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
  /** Optional size clamps (Overview fixes height to 2). */
  widthMin?: number;
  widthMax?: number;
  heightMin?: number;
  heightMax?: number;
  storage?: StorageDto | null;
  gtMachines?: GtMachineDto[] | null;
  balanceSuggestions?: Array<{
    itemId?: string;
    displayName: string;
    transferable: number;
    needyAmount: number;
    resourceType?: string;
  }> | null;
}

const WIDGET_TYPES: DashboardWidgetConfig['type'][] = [
  'statCard', 'progressBar', 'lineChart', 'barChart', 'pieChart',
  'dataTable', 'gauge', 'radarChart',
  'group', 'textNote', 'spacer', 'alertsSummary', 'craftingQueue',
  'networkHealth', 'powerFlow', 'storageMatrix', 'machineFleet',
  'playerPresence', 'activityStream', 'serverVitals',
];

const DATA_SOURCES = [
  'itemCount', 'fluidCount', 'essentiaCount', 'bytesUsed', 'bytesMax', 'bytesPercent',
  'euStored', 'euMax', 'euPercent', 'euInRate', 'euOutRate', 'steamStored',
  'activeCpu', 'busyCpu', 'cpuBusyRatio', 'gtMachineCount', 'gtActiveCount',
  'itemTotal', 'fluidTotal', 'topItems', 'cpuList', 'gtMachineList',
  'powerHistory', 'storageByCategory', 'machineByStatus', 'networkCompare', 'networkBalance',
  'playerOnlineCount', 'playerOnlineTrend', 'serverTps', 'serverMspt',
  'customPins',
  'none', 'alertsActive', 'craftingBusy',
  'networkHealth', 'powerFlow', 'storageMatrix', 'machineFleet',
  'playerPresence', 'activityStream', 'serverVitals',
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
  widthMin = 1,
  widthMax = 12,
  heightMin = 1,
  heightMax = 10,
  storage,
  gtMachines,
  balanceSuggestions,
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

  const hasPins = (widget.pins?.length ?? 0) > 0;
  const layoutOrFeed = isLayoutOrFeedType(widget.type);
  const validTypesForDs = layoutOrFeed
    ? getValidChartTypes(defaultDataSourceForWidgetType(widget.type))
    : hasPins && widget.dataSource === 'customPins'
      ? getValidChartTypes('customPins')
      : getValidChartTypes(widget.dataSource);
  const availableWidgetTypes = allWidgetTypes.filter((tp) => {
    if (layoutOrFeed) return isLayoutOrFeedType(tp) || validTypesForDs.includes(tp);
    return isLayoutOrFeedType(tp) || validTypesForDs.includes(tp);
  });
  const availableDataSources = allDataSources;

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

  const onDataSourceChange = (v: string) => {
    const valid = getValidChartTypes(v);
    const typeOk = valid.includes(widget.type);
    patch({
      dataSource: v,
      type: typeOk ? widget.type : valid.find((tp) => allWidgetTypes.includes(tp)) ?? widget.type,
      // Column keys are data-source specific; let the new source choose its defaults.
      columns: undefined,
    });
  };

  return (
    <Modal
      title={t('editWidget')}
      open={open}
      onOk={onOk}
      onCancel={onCancel}
      okText={t('apply')}
      cancelText={t('cancel')}
      width={620}
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
                {!layoutOrFeed && (
                  <>
                <div>
                  <Text strong>{t('dataSource')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                    {t('dashDataFirstHint')}
                  </Text>
                  <Select
                    style={{ width: '100%', marginTop: 4 }}
                    value={widget.dataSource}
                    onChange={onDataSourceChange}
                    options={availableDataSources.map((ds) => ({ label: t('dataSource_' + ds), value: ds }))}
                    showSearch
                    optionFilterProp="label"
                  />
                </div>

                <WidgetPinEditor
                  widget={widget}
                  onChange={patch}
                  storage={storage}
                  gtMachines={gtMachines}
                  balanceSuggestions={balanceSuggestions}
                  scalarDataSources={allDataSources.filter(
                    (ds) =>
                      !['topItems', 'cpuList', 'gtMachineList', 'networkBalance', 'storageByCategory',
                        'machineByStatus', 'networkCompare', 'customPins', 'powerHistory', 'playerOnlineTrend',
                        'none', 'alertsActive', 'craftingBusy', 'networkHealth', 'powerFlow',
                        'storageMatrix', 'machineFleet', 'playerPresence', 'activityStream', 'serverVitals'].includes(ds)
                  )}
                />
                  </>
                )}

                <div>
                  <Text strong>{t('editWidget_type')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                    {t('dashChartTypeAfterDataHint')}
                  </Text>
                  <Select
                    style={{ width: '100%', marginTop: 4 }}
                    value={widget.type}
                    onChange={(v) => {
                      const nextType = v as DashboardWidgetConfig['type'];
                      const patchExtra: Partial<DashboardWidgetConfig> = { type: nextType };
                      if (isLayoutOrFeedType(nextType)) {
                        patchExtra.dataSource = defaultDataSourceForWidgetType(nextType);
                        if (nextType === 'group' && !widget.children) {
                          patchExtra.children = [];
                        }
                      }
                      patch(patchExtra);
                    }}
                    options={availableWidgetTypes.map((tp) => ({ label: t('widgetType_' + tp), value: tp }))}
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

                {widget.type === 'textNote' && (
                  <div>
                    <Text strong>{t('editWidget_noteText')}</Text>
                    <Input.TextArea
                      style={{ marginTop: 4 }}
                      rows={4}
                      value={widget.noteText || ''}
                      onChange={(e) => patch({ noteText: e.target.value })}
                      placeholder={t('editWidget_noteTextHint')}
                    />
                  </div>
                )}

                {([
                  'alertsSummary', 'craftingQueue', 'dataTable', 'machineFleet',
                  'playerPresence', 'activityStream',
                ] as DashboardWidgetConfig['type'][]).includes(widget.type) && (
                  <div>
                    <Text strong>{t('editWidget_maxRows')}</Text>
                    <InputNumber
                      style={{ width: '100%', marginTop: 4 }}
                      min={1}
                      max={50}
                      value={widget.maxRows ?? (widget.type === 'dataTable' ? 10 : 5)}
                      onChange={(v) => patch({ maxRows: v ?? 5 })}
                    />
                  </div>
                )}

                {widget.type === 'gauge' && (
                  <div>
                    <Text strong>{t('dashGaugeThreshold')}</Text>
                    <InputNumber
                      style={{ width: '100%', marginTop: 4 }}
                      min={0}
                      value={widget.gaugeThreshold ?? 0}
                      onChange={(v) => patch({ gaugeThreshold: v ?? 0 })}
                    />
                  </div>
                )}

                {(widget.type === 'statCard' || widget.type === 'gauge' || widget.type === 'progressBar') && (
                  <div>
                    <Text strong>{t('editWidget_alertThreshold')}</Text>
                    <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                      {t('editWidget_alertThresholdHint')}
                    </Text>
                    <InputNumber
                      style={{ width: '100%', marginTop: 4 }}
                      min={0}
                      value={widget.alertThreshold ?? 0}
                      onChange={(v) => patch({ alertThreshold: v ?? 0 })}
                    />
                  </div>
                )}

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
                      min={widthMin}
                      max={widthMax}
                      value={widget.width}
                      onChange={(v) => patch({ width: v ?? widthMin })}
                    />
                  </div>
                  <div>
                    <Text strong>{t('height')}</Text>
                    <InputNumber
                      style={{ marginTop: 4, display: 'block' }}
                      min={heightMin}
                      max={heightMax}
                      value={widget.height}
                      onChange={(v) => patch({ height: v ?? heightMin })}
                      disabled={heightMin === heightMax}
                    />
                  </div>
                </Space>

                <div>
                  <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>{t('editWidget_locked')}</Text>
                    <Switch
                      checked={!!widget.locked}
                      onChange={(checked) => patch({ locked: checked || undefined })}
                    />
                  </Space>
                  <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                    {t('editWidget_lockedHint')}
                  </Text>
                </div>
                <div>
                  <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>{t('editWidget_noMove')}</Text>
                    <Switch
                      checked={!!widget.noMove}
                      onChange={(checked) => patch({ noMove: checked || undefined })}
                    />
                  </Space>
                </div>
                <div>
                  <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text strong>{t('editWidget_noResize')}</Text>
                    <Switch
                      checked={!!widget.noResize}
                      onChange={(checked) => patch({ noResize: checked || undefined })}
                    />
                  </Space>
                </div>
                {(widget.type === 'dataTable'
                  || widget.type === 'textNote'
                  || widget.type === 'alertsSummary'
                  || widget.type === 'craftingQueue'
                  || widget.type === 'machineFleet'
                  || widget.type === 'playerPresence'
                  || widget.type === 'activityStream') && (
                  <div>
                    <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                      <Text strong>{t('editWidget_sizeToContent')}</Text>
                      <Switch
                        checked={!!widget.sizeToContent}
                        onChange={(checked) => patch({ sizeToContent: checked || undefined })}
                      />
                    </Space>
                    <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                      {t('editWidget_sizeToContentHint')}
                    </Text>
                  </div>
                )}

                <div>
                  <Text strong>{t('dashContentScale')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                    {t('dashContentScaleHint')}
                  </Text>
                  <Slider
                    style={{ marginTop: 8 }}
                    min={50}
                    max={200}
                    step={5}
                    value={Math.round(clampContentScale(widget.contentScale) * 100)}
                    onChange={(v) => patch({ contentScale: (v as number) / 100 })}
                    tooltip={{ formatter: (v) => `${v}%` }}
                  />
                </div>

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

                {isChartType && (
                  <div>
                    <Text strong>{t('chartStyle')}</Text>
                    <Text type="secondary" style={{ display: 'block', fontSize: '0.72rem' }}>
                      {t('chartStyle_hint')}
                    </Text>
                    <Select
                      style={{ width: '100%', marginTop: 4 }}
                      value={widget.chartStyle ?? settings.defaultChartStyle ?? 'inherit'}
                      onChange={(v) => patch({ chartStyle: v })}
                      options={CHART_STYLES.map((id) => ({
                        label: t('chartStyle_' + id),
                        value: id,
                      }))}
                    />
                  </div>
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
