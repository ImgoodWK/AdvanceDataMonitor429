import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { GridStack } from 'gridstack';
import 'gridstack/dist/gridstack.min.css';
import { Button, Modal, Select, InputNumber, Space, Empty, Progress, Tag, Tooltip, Skeleton } from 'antd';
import {
  EditOutlined,
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SettingOutlined,
  AlignLeftOutlined,
  ControlOutlined,
  DeleteFilled,
  UndoOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { usePlayers } from '@/hooks/usePlayers';
import { useServerHealth } from '@/hooks/useServerHealth';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useDebouncedLocalStorageSaver } from '@/hooks/useDebouncedLocalStorageSaver';
import { useEditorHistory, useUndoRedoHotkeys } from '@/hooks/useEditorHistory';
import { useDashboardPinMetrics, lookupPinSeries } from '@/hooks/useDashboardPinMetrics';
import { useNetworkBalance } from '@/hooks/useNetworkBalance';
import { useDashboardAlerts } from '@/hooks/useDashboardAlerts';
import { resolvePins } from '@/utils/dashboardPins';
import { resolveColumns } from '@/utils/dashboardColumns';
import { getValidChartTypes } from '@/utils/dataSourceChartMap';
import {
  isHistoryDataSource,
  isPlayerHistoryDataSource,
  isPowerHistoryDataSource,
  isServerHealthHistoryDataSource,
} from '@/utils/dataSourceChartMap';
import {
  addChildToGroup,
  applyOuterNodePositions,
  flattenWidgets,
  isLayoutOrFeedType,
  removeWidgetById,
  updateWidgetById,
  widgetLayoutSignature,
} from '@/utils/dashboardTree';

import {
  DEFAULT_DASHBOARD_SETTINGS,
  DEFAULT_DASHBOARD_WIDGETS,
  DASHBOARD_CONFIG_KEY,
  loadDashboardSettings,
  type DashboardSettings,
  type DashboardWidgetConfig,
} from '@/utils/presets';
import {
  resolveProp,
  resolveAllColors,
  resolveChartStretchMode,
  resolveChartStyleRecipe,
} from '@/utils/dashboardResolve';
import { formatBytes, formatDuration, formatTime, formatLargeWithDelta, formatSignificant } from '@/utils/format';
import { Icon } from '@/components/Icon';
import { PageShell } from '@/components/Layout/PageShell';
import { DashboardSettingsDrawer } from '@/components/dashboard/DashboardSettingsDrawer';
import { EditWidgetModal } from '@/components/dashboard/EditWidgetModal';
import { ChartTrendSvg } from '@/components/dashboard/ChartTrendSvg';
import { NetworkBalanceTable } from '@/components/dashboard/NetworkBalanceTable';
import { RadarChartWidget, resolveRadarAxes } from '@/components/dashboard/RadarChartWidget';
import { WidgetShell } from '@/components/dashboard/WidgetShell';
import { GroupWidget } from '@/components/dashboard/GroupWidget';
import {
  AlertsSummaryWidget,
  CraftingQueueWidget,
  SpacerWidget,
  TextNoteWidget,
} from '@/components/dashboard/SpecialWidgets';
import { copyWidgetConfig } from '@/utils/widgetGridActions';
import { createWidgetId } from '@/utils/widgetId';
import {
  GRID_DRAG_CANCEL_SELECTOR,
  GRID_EDIT_NO_DRAG_CLASS,
  stopGridDragPointer,
} from '@/utils/gridStackEditGuard';
import {
  buildNetworkCompareRows,
  getGtMachinesForTable,
  getGtStatusBreakdown,
  getStorageCategoryBreakdown,
  gtStatusLabel,
} from '@/utils/overviewDataSources';
import {
  renderCategoricalBarChart,
  renderCategoricalPieChart,
  renderNetworkCompareChart,
} from '@/components/dashboard/WidgetContent';
const WIDGET_TYPES: DashboardWidgetConfig['type'][] = [
  'statCard', 'progressBar', 'lineChart', 'barChart', 'pieChart',
  'dataTable', 'gauge', 'radarChart',
  'group', 'textNote', 'spacer', 'alertsSummary', 'craftingQueue',
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
];

const PALETTE_PRESETS: Array<{ type: DashboardWidgetConfig['type']; dataSource: string; w: number; h: number }> = [
  { type: 'statCard', dataSource: 'itemCount', w: 3, h: 2 },
  { type: 'gauge', dataSource: 'bytesPercent', w: 3, h: 3 },
  { type: 'lineChart', dataSource: 'itemCount', w: 6, h: 3 },
  { type: 'dataTable', dataSource: 'topItems', w: 6, h: 4 },
  { type: 'group', dataSource: 'none', w: 6, h: 4 },
  { type: 'alertsSummary', dataSource: 'alertsActive', w: 4, h: 3 },
  { type: 'craftingQueue', dataSource: 'craftingBusy', w: 4, h: 3 },
  { type: 'textNote', dataSource: 'none', w: 3, h: 2 },
  { type: 'spacer', dataSource: 'none', w: 12, h: 1 },
];

export function Dashboard() {
  const { selectedNetworks, notify, lastUpdateTime, pageStyle } = useAppContext();
  const { t } = useI18n();
  const { storageMap, powerMap, gtMap, loading } = useSnapshotData();
  const fmtNum = useNumberFormat();
  const { onlineCount: playerOnlineCount, history: playerOnlineHistory } = usePlayers();
  const serverHealth = useServerHealth();
  const networkMetrics = useNetworkMetrics();
  const [editMode, setEditMode] = useState(false);
  const {
    present: settings,
    commit: commitSettings,
    undo: undoSettings,
    redo: redoSettings,
    canUndo,
    canRedo,
  } = useEditorHistory<DashboardSettings>(loadDashboardSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [addWidgetOpen, setAddWidgetOpen] = useState(false);
  /** When set, new widgets are inserted into this group instead of the root grid. */
  const [addTargetGroupId, setAddTargetGroupId] = useState<string | null>(null);
  const [editWidgetTarget, setEditWidgetTarget] = useState<DashboardWidgetConfig | null>(null);
  const [newWidget, setNewWidget] = useState<Partial<DashboardWidgetConfig>>({
    type: 'statCard',
    dataSource: 'itemCount',
    scope: 'perNetwork',
    title: '',
    width: 3,
    height: 2,
    contentScale: 1,
    pins: [],
  });

  // GridStack 实例与 DOM 引用（提前声明以便 handleAutoArrange 等回调引用）
  const gridRef = useRef<HTMLDivElement>(null);
  const gridInstanceRef = useRef<GridStack | null>(null);
  const { schedule: scheduleLayoutSave, flush: flushLayoutSave, cancel: cancelLayoutSave } =
    useDebouncedLocalStorageSaver<DashboardSettings>(DASHBOARD_CONFIG_KEY);

  const persistSettings = useCallback((s: DashboardSettings) => {
    try {
      localStorage.setItem(DASHBOARD_CONFIG_KEY, JSON.stringify(s));
    } catch {
      /* ignore */
    }
  }, []);

  /** Immediate save with functional updater; cancels pending debounced layout writes. */
  const saveSettings = useCallback(
    (s: DashboardSettings | ((prev: DashboardSettings) => DashboardSettings)) => {
      cancelLayoutSave();
      commitSettings((prev) => {
        const next = typeof s === 'function' ? s(prev) : s;
        persistSettings(next);
        return next;
      });
    },
    [cancelLayoutSave, commitSettings, persistSettings]
  );

  const handleUndo = useCallback(() => {
    const restored = undoSettings();
    if (!restored) return;
    cancelLayoutSave();
    persistSettings(restored);
  }, [undoSettings, cancelLayoutSave, persistSettings]);

  const handleRedo = useCallback(() => {
    const restored = redoSettings();
    if (!restored) return;
    cancelLayoutSave();
    persistSettings(restored);
  }, [redoSettings, cancelLayoutSave, persistSettings]);

  useUndoRedoHotkeys(handleUndo, handleRedo, editMode);

  const currentNet = selectedNetworks[0] ?? 0;
  const storage = storageMap[currentNet];
  const power = powerMap[currentNet];
  const gt = gtMap[currentNet];
  // Do not fall back to defaults when the user cleared the layout (empty array).
  const widgets = settings.widgets;
  const pinMetrics = useDashboardPinMetrics(currentNet, widgets, 10_000);
  const { suggestions: balanceSuggestions } = useNetworkBalance(
    selectedNetworks,
    selectedNetworks.length >= 2
  );
  const needsAlerts = useMemo(
    () => flattenWidgets(widgets).some((w) => w.type === 'alertsSummary'),
    [widgets]
  );
  const { alerts: activeAlerts, loading: alertsLoading } = useDashboardAlerts(needsAlerts);

  // Compute data source values
  const dataSourceValue = useCallback(
    (ds: string): number => {
      if (storage) {
        switch (ds) {
          case 'itemCount': return storage.items?.length || 0;
          case 'fluidCount': return storage.fluids?.length || 0;
          case 'essentiaCount': return storage.essentia?.length || 0;
          case 'bytesUsed': return storage.bytesUsed || 0;
          case 'bytesMax': return storage.bytesMax || 0;
          case 'bytesPercent': return storage.bytesMax > 0 ? (storage.bytesUsed / storage.bytesMax) * 100 : 0;
          case 'activeCpu': return storage.cpus?.filter((c) => c.isBusy).length || 0;
          case 'busyCpu': return storage.cpus?.filter((c) => c.isBusy).length || 0;
          case 'cpuBusyRatio': {
            const total = storage.cpus?.length || 0;
            const busy = storage.cpus?.filter((c) => c.isBusy).length || 0;
            return total > 0 ? (busy / total) * 100 : 0;
          }
          case 'itemTotal': return storage.items?.reduce((s, i) => s + i.amount, 0) || 0;
          case 'fluidTotal': return storage.fluids?.reduce((s, f) => s + f.amount, 0) || 0;
        }
      }
      if (power) {
        switch (ds) {
          case 'euStored': return power.euStored || 0;
          case 'euMax': return power.euMax || 0;
          case 'euPercent': return power.euMax > 0 ? (power.euStored / power.euMax) * 100 : 0;
          case 'euInRate': return power.euInRate || 0;
          case 'euOutRate': return power.euOutRate || 0;
          case 'steamStored': return power.steamStored || 0;
        }
      }
      if (gt) {
        switch (ds) {
          case 'gtMachineCount': return gt.machines?.length || 0;
          case 'gtActiveCount': return gt.machines?.filter((m) => m.isActive).length || 0;
        }
      }
      // 跨网络全局指标（p2-dashboard）
      switch (ds) {
        case 'playerOnlineCount': return playerOnlineCount;
        case 'playerOnlineTrend': return playerOnlineHistory.length;
        case 'serverTps': return serverHealth.health?.tps ?? 0;
        case 'serverMspt': return serverHealth.health?.mspt ?? 0;
      }
      return 0;
    },
    [storage, power, gt, playerOnlineCount, playerOnlineHistory, serverHealth.health]
  );

  const pinCtx = useMemo(
    () => ({
      storage,
      power,
      gtMachines: gt?.machines ?? null,
      balanceSuggestions,
      scalarValue: dataSourceValue,
    }),
    [storage, power, gt, balanceSuggestions, dataSourceValue]
  );

  const dataSourceLabel = (ds: string): string => {
    const map: Record<string, string> = {};
    const keys = [
      'itemCount', 'fluidCount', 'essentiaCount', 'bytesUsed', 'bytesMax', 'bytesPercent',
      'euStored', 'euMax', 'euPercent', 'euInRate', 'euOutRate', 'steamStored',
      'activeCpu', 'busyCpu', 'cpuBusyRatio', 'gtMachineCount', 'gtActiveCount',
      'itemTotal', 'fluidTotal', 'topItems', 'cpuList', 'gtMachineList',
      'powerHistory', 'storageByCategory', 'machineByStatus', 'networkCompare', 'networkBalance',
      'playerOnlineCount', 'playerOnlineTrend', 'serverTps', 'serverMspt', 'customPins',
      'none', 'alertsActive', 'craftingBusy',
    ];
    for (const k of keys) map[k] = t('dataSource_' + k);
    return map[ds] || ds;
  };

  // Label style: title color + font size aware
  const labelStyle = (widget: DashboardWidgetConfig): React.CSSProperties => {
    const colors = resolveAllColors(widget, settings);
    const fs = resolveProp(widget, settings, 'fontSize');
    const style: React.CSSProperties = { fontSize: Math.max(10, fs - 2) };
    if (colors.titleColor) style.color = colors.titleColor;
    return style;
  };

  const valueStyle = (widget: DashboardWidgetConfig): React.CSSProperties => {
    const colors = resolveAllColors(widget, settings);
    const fs = resolveProp(widget, settings, 'fontSize');
    const style: React.CSSProperties = { fontSize: fs + 6 };
    if (colors.chartColor) style.color = colors.chartColor;
    return style;
  };

  const renderWidget = (widget: DashboardWidgetConfig) => {
    if (widget.type === 'group') {
      // Group content is rendered by GroupWidget (nested grid); shell still calls renderWidget for safety.
      return null;
    }

    const value = dataSourceValue(widget.dataSource);
    const isPercent = widget.dataSource.includes('Percent') || widget.dataSource === 'cpuBusyRatio';
    const isBytes = widget.dataSource === 'bytesUsed' || widget.dataSource === 'bytesMax';
    const colors = resolveAllColors(widget, settings);
    const chartSize = resolveProp(widget, settings, 'chartSize');
    const chartColor = colors.chartColor || 'var(--accent)';
    const threshold = widget.alertThreshold ?? 0;
    const overThreshold = threshold > 0 && value >= threshold;

    const wrap = (children: React.ReactNode) => (
      <div
        className={`widget-align dashboard-widget-inner${overThreshold ? ' widget-alert-threshold' : ''}`}
        data-align={resolveProp(widget, settings, 'alignment')}
        style={{
          fontSize: resolveProp(widget, settings, 'fontSize'),
          height: '100%',
          overflow: 'hidden',
        }}
      >
        {children}
      </div>
    );

    const label = widget.title ? t(widget.title) : dataSourceLabel(widget.dataSource);
    const labelText = (text: string) => (
      <div className="stat-card-label" style={labelStyle(widget)}>{text}</div>
    );

    const chartStretch = resolveChartStretchMode(widget, settings, widget.type);
    const chartRecipe = resolveChartStyleRecipe(widget, settings, pageStyle);

    if (widget.type === 'textNote') {
      return <TextNoteWidget widget={widget} titleColor={colors.titleColor} />;
    }
    if (widget.type === 'spacer') {
      return <SpacerWidget widget={widget} />;
    }
    if (widget.type === 'alertsSummary') {
      return (
        <AlertsSummaryWidget
          widget={widget}
          alerts={activeAlerts}
          loading={alertsLoading}
          titleColor={colors.titleColor}
        />
      );
    }
    if (widget.type === 'craftingQueue') {
      return (
        <CraftingQueueWidget
          widget={widget}
          cpus={storage?.cpus}
          titleColor={colors.titleColor}
          formatNumber={fmtNum}
        />
      );
    }

    if (loading && !storage && !power && !gt) {
      return wrap(
        <Skeleton active paragraph={{ rows: widget.type === 'statCard' ? 1 : 3 }} title={false} />
      );
    }

    switch (widget.type) {
      case 'statCard': {
        const pinned = resolvePins(widget.pins, pinCtx);
        const showDelta = widget.showDelta ?? false;
        const sigDigits = widget.significantDigits ?? 5;
        if (pinned.length > 0) {
          const p = pinned[0];
          return wrap(
            <>
              {labelText(p.label || label)}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'center' }}>
                {p.iconItem && <Icon item={p.iconItem} size={28} />}
                <div className="stat-card-value" style={valueStyle(widget)}>
                  {fmtNum(p.value)}
                </div>
              </div>
            </>
          );
        }
        let mainText = isBytes ? formatBytes(value) : isPercent ? value.toFixed(1) + '%' : fmtNum(value);
        let deltaEl: ReactNode = null;
        if (showDelta) {
          const hist = networkMetrics.getHistory(currentNet, widget.dataSource);
          const prev = hist.length >= 2 ? hist[hist.length - 2]?.value : undefined;
          const formatted = formatLargeWithDelta(value, prev, 'ae');
          mainText = widget.significantDigits
            ? formatSignificant(value, 'ae', sigDigits)
            : formatted.main;
          if (formatted.delta) {
            const color =
              formatted.deltaPositive === true
                ? 'var(--success, #52c41a)'
                : formatted.deltaPositive === false
                  ? 'var(--error, #ff4d4f)'
                  : 'var(--text-dim)';
            deltaEl = (
              <div
                className="stat-card-delta"
                style={{
                  fontSize: Math.max(9, resolveProp(widget, settings, 'fontSize') - 3),
                  color,
                }}
              >
                {formatted.delta}
              </div>
            );
          }
        }
        return wrap(
          <>
            {labelText(label)}
            <div className="stat-card-value" style={valueStyle(widget)}>
              {mainText}
            </div>
            {deltaEl}
          </>
        );
      }

      case 'progressBar': {
        const pinned = resolvePins(widget.pins, pinCtx);
        const pin0 = pinned[0];
        const fillColor = colors.progressFillColor || colors.chartColor || 'var(--accent)';
        const trackColor = colors.progressTrackColor || undefined;
        let pct = Math.min(100, isPercent ? value : 0);
        let fmt = isPercent ? value.toFixed(1) + '%' : fmtNum(value);
        if (pin0) {
          if (pin0.max && pin0.max > 0) {
            pct = Math.min(100, (pin0.value / pin0.max) * 100);
          } else if (pin0.value <= 1) {
            pct = Math.min(100, pin0.value * 100);
          } else if (pin0.value <= 100) {
            pct = Math.min(100, pin0.value);
          }
          fmt = fmtNum(pin0.value);
        }
        return wrap(
          <>
            {labelText(pin0?.label || label)}
            <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
              <Progress
                percent={pct}
                type={widget.style === 'circular' ? 'circle' : 'line'}
                strokeColor={trackColor ? { color: fillColor, trailColor: trackColor } : fillColor}
                format={() => fmt}
                style={{ width: '100%' }}
              />
            </div>
          </>
        );
      }

      case 'gauge': {
        const pinned = resolvePins(widget.pins, pinCtx);
        const pin0 = pinned[0];
        const strokeColor = colors.gaugeStrokeColor || colors.chartColor || 'var(--accent)';
        const trackColor = colors.gaugeTrackColor || undefined;
        let pct = Math.min(100, isPercent ? value : (power?.euMax ? (value / power.euMax) * 100 : 0));
        let fmt = isPercent ? value.toFixed(0) + '%' : fmtNum(value);
        if (pin0) {
          const thr = widget.gaugeThreshold && widget.gaugeThreshold > 0
            ? widget.gaugeThreshold
            : pin0.max && pin0.max > 0
              ? pin0.max
              : 100;
          pct = Math.min(100, (pin0.value / thr) * 100);
          fmt = fmtNum(pin0.value);
        }
        return wrap(
          <>
            {labelText(pin0?.label || label)}
            <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
              <Progress
                type="circle"
                percent={pct}
                strokeColor={trackColor ? { color: strokeColor, trailColor: trackColor } : strokeColor}
                format={() => fmt}
              />
            </div>
          </>
        );
      }

      case 'lineChart': {
        const ds = widget.dataSource;
        const lineColor = colors.chartLineColor || chartColor;
        const areaColor = colors.chartAreaColor || (colors.chartColor ? `${colors.chartColor}33` : 'var(--accent-dim)');
        const secondaryLine = colors.chartSecondaryLineColor || 'var(--warning, #faad14)';
        const secondaryArea = colors.chartSecondaryAreaColor || undefined;
        const notEnoughData = (
          <>
            {labelText(label)}
            <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
              <span style={{ color: 'var(--text-dim)' }}>{t('notEnoughData')}</span>
            </div>
          </>
        );

        type TrendSeries = {
          id: string;
          label: string;
          points: { ts?: number; value: number }[];
          lineColor: string;
          areaColor?: string;
        };
        const merged: TrendSeries[] = [];
        const pinPalette = [lineColor, secondaryLine, chartColor, 'var(--success, #52c41a)', 'var(--info, #1677ff)'];

        // Built-in history series (when dataSource is not customPins-only)
        if (ds !== 'customPins') {
          if (isPlayerHistoryDataSource(ds)) {
            const hist = playerOnlineHistory;
            if (hist.length >= 2) {
              merged.push({
                id: 'players',
                label: t('dataSource_playerOnlineCount'),
                points: hist.map((p) => ({ value: p.count, ts: p.ts })),
                lineColor,
                areaColor,
              });
            }
          } else if (isServerHealthHistoryDataSource(ds)) {
            const points =
              ds === 'serverMspt' ? serverHealth.getMsptHistory() : serverHealth.getTpsHistory();
            if (points.length >= 2) {
              merged.push({ id: ds, label, points, lineColor, areaColor });
            }
          } else if (isPowerHistoryDataSource(ds)) {
            const history = power?.euHistory || [];
            const historyTs = power?.euHistoryTimestamps || [];
            if (history.length >= 2) {
              merged.push({
                id: 'eu',
                label: t('euStored'),
                points: history.map((v, i) => ({ value: v, ts: historyTs[i] })),
                lineColor,
                areaColor,
              });
            }
          } else if (isHistoryDataSource(ds)) {
            const points = networkMetrics.getHistory(currentNet, ds);
            if (points.length >= 2) {
              merged.push({ id: ds, label, points, lineColor, areaColor });
            }
          }
        }

        // Pin history series (union with built-in)
        (widget.pins || []).forEach((p, idx) => {
          const pts = lookupPinSeries(p, pinMetrics);
          if (pts.length < 2) return;
          const color = pinPalette[idx % pinPalette.length];
          merged.push({
            id: `${p.kind}:${p.id}`,
            label: p.label || p.id,
            points: pts,
            lineColor: color,
            areaColor: idx === 0 ? areaColor : secondaryArea,
          });
        });

        if (merged.length === 0) {
          if (
            ds !== 'customPins' &&
            !isHistoryDataSource(ds) &&
            !isPlayerHistoryDataSource(ds) &&
            !isPowerHistoryDataSource(ds) &&
            !isServerHealthHistoryDataSource(ds) &&
            !(widget.pins && widget.pins.length > 0)
          ) {
            return wrap(
              <>
                {labelText(label)}
                <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%` }}>
                  <span style={{ color: 'var(--text-dim)' }}>{t('trendNotSupported')}</span>
                </div>
              </>
            );
          }
          return wrap(notEnoughData);
        }

        const isPct = ds.includes('Percent') || ds === 'cpuBusyRatio';
        const isMspt = ds === 'serverMspt';
        const isTps = ds === 'serverTps';
        return wrap(
          <>
            {labelText(label)}
            <div className="widget-chart-area widget-chart-area--sized" style={{ height: `${chartSize}%`, minHeight: 80 }}>
              <ChartTrendSvg
                series={merged}
                formatValue={(v) =>
                  isMspt
                    ? v.toFixed(1) + ' ms'
                    : isTps
                      ? v.toFixed(1)
                      : isPct
                        ? v.toFixed(1) + '%'
                        : fmtNum(v)
                }
                formatTime={(ts) => formatTime(ts)}
                showValueAxis={settings.chartShowValueAxis}
                showTimeAxis={settings.chartShowTimeAxis}
                stretchMode={chartStretch}
                yDomain={isTps ? [0, 20] : undefined}
                recipe={chartRecipe}
                colors={{
                  gridColor: colors.chartGridColor || 'var(--border-light)',
                  pointColor: colors.chartPointColor || lineColor,
                  axisTextColor: colors.axisTextColor || undefined,
                }}
              />
            </div>
          </>
        );
      }

      case 'dataTable': {
        const rowAltBg = colors.dataTableRowAltColor || undefined;
        const cols = resolveColumns(widget);
        const pinnedRows = resolvePins(widget.pins, pinCtx);

        if (widget.dataSource === 'customPins') {
          return wrap(
            <>
              {labelText(label)}
              <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                {pinnedRows.length === 0 ? (
                  <Empty description={t('dashPinEmpty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  pinnedRows.map((row, i) => (
                    <div
                      key={i}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                        margin: '2px 0',
                        fontSize: '0.75rem',
                        padding: '2px 4px',
                        borderRadius: 4,
                        background: i % 2 === 1 ? rowAltBg : undefined,
                      }}
                    >
                      {cols.includes('icon') && row.iconItem && <Icon item={row.iconItem} size={20} />}
                      {cols.includes('name') && (
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {row.label}
                        </span>
                      )}
                      {cols.includes('kind') && (
                        <Tag style={{ margin: 0, fontSize: '0.65rem' }}>{row.pin.kind}</Tag>
                      )}
                      {cols.includes('amount') && (
                        <strong style={{ color: chartColor }}>{fmtNum(row.value)}</strong>
                      )}
                    </div>
                  ))
                )}
              </div>
            </>
          );
        }

        if (widget.dataSource === 'topItems' && storage?.items) {
          const top = widget.pinsOnly
            ? []
            : [...storage.items].sort((a, b) => b.amount - a.amount).slice(0, widget.maxRows || 10);
          const pinIds = new Set(pinnedRows.filter((p) => p.pin.kind === 'item').map((p) => p.pin.id));
          return wrap(
            <>
              {labelText(label)}
              <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                {pinnedRows.map((row, i) => (
                  <div
                    key={'pin-' + i}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                      margin: '2px 0',
                      fontSize: '0.75rem',
                      padding: '2px 4px',
                      borderRadius: 4,
                      background: 'var(--accent-dim, rgba(64,158,255,0.12))',
                    }}
                  >
                    {cols.includes('icon') && row.iconItem && <Icon item={row.iconItem} size={20} />}
                    {cols.includes('name') && (
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.iconItem?.displayName || row.label}
                      </span>
                    )}
                    {cols.includes('registryName') && (
                      <code style={{ flex: cols.includes('name') ? undefined : 1, fontSize: '0.65rem' }}>{row.pin.id}</code>
                    )}
                    {cols.includes('amount') && <strong style={{ color: chartColor }}>{fmtNum(row.value)}</strong>}
                  </div>
                ))}
                {top
                  .filter((item) => {
                    const id = item.itemId || item.registryName;
                    return !id || !pinIds.has(id);
                  })
                  .map((item, i) => (
                  <div key={i} style={{
                    display: 'flex', alignItems: 'center', gap: 4, margin: '2px 0',
                    fontSize: '0.75rem', padding: '2px 4px', borderRadius: 4,
                    background: i % 2 === 1 ? rowAltBg : undefined,
                  }}>
                    {cols.includes('icon') && <Icon item={item} size={20} />}
                    {cols.includes('name') && (
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {item.displayName || item.registryName}
                      </span>
                    )}
                    {cols.includes('registryName') && (
                      <code style={{ flex: cols.includes('name') ? undefined : 1, fontSize: '0.65rem' }}>
                        {item.registryName || item.itemId || '-'}
                      </code>
                    )}
                    {cols.includes('amount') && <strong style={{ color: chartColor }}>{fmtNum(item.amount)}</strong>}
                  </div>
                ))}
              </div>
            </>
          );
        }
        if (widget.dataSource === 'cpuList' && storage?.cpus) {
          return wrap(
            <>
              {labelText(label)}
              <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                {storage.cpus.map((cpu, i) => {
                  const storageTotal = cpu.usedStorage + cpu.availableStorage;
                  const storagePct =
                    storageTotal > 0 ? Math.round((cpu.usedStorage / storageTotal) * 100) : 0;
                  const itemPct =
                    cpu.maxItems > 0 ? Math.round((cpu.storedItems / cpu.maxItems) * 100) : 0;
                  return (
                    <div
                      key={i}
                      style={{
                        fontSize: '0.7rem',
                        margin: '4px 0',
                        padding: '4px 6px',
                        background: i % 2 === 1 ? (rowAltBg || 'var(--bg-hover)') : 'var(--bg-hover)',
                        borderRadius: 4,
                        border: '1px solid var(--border-light)',
                      }}
                    >
                      {(cols.includes('name') || cols.includes('status')) && <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        {cols.includes('name') && (
                        <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {cpu.name}
                        </span>
                        )}
                        {cols.includes('status') && (
                        <Tag color={cpu.isBusy ? 'processing' : 'default'} style={{ margin: 0, fontSize: '0.65rem' }}>
                          {cpu.isBusy ? t('busy') : t('idle')}
                        </Tag>
                        )}
                      </div>
                      }
                      <div style={{ display: 'flex', gap: 8, marginTop: 2, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
                        {cols.includes('coProcessors') && cpu.coProcessors > 0 && (
                          <span title={t('coprocessors')}>×{cpu.coProcessors}</span>
                        )}
                        {cols.includes('storage') && storageTotal > 0 && (
                          <Tooltip title={`${formatBytes(cpu.usedStorage)} / ${formatBytes(storageTotal)}`}>
                            <span>{t('cpuStorage')}: {storagePct}%</span>
                          </Tooltip>
                        )}
                        {cols.includes('items') && cpu.maxItems > 0 && (
                          <span>{t('stored')}: {fmtNum(cpu.storedItems)}/{fmtNum(cpu.maxItems)} ({itemPct}%)</span>
                        )}
                        {cols.includes('elapsedTime') && cpu.isBusy && cpu.elapsedTime > 0 && (
                          <span>{t('elapsedTime')}: {formatDuration(cpu.elapsedTime)}</span>
                        )}
                      </div>
                      {cols.includes('finalOutput') && cpu.isBusy && cpu.finalOutputName && (
                        <div style={{ marginTop: 2, color: chartColor, fontSize: '0.68rem' }}>
                          {cpu.finalOutputName} ×{fmtNum(cpu.finalOutputAmount)}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </>
          );
        }
        if (widget.dataSource === 'gtMachineList') {
          const machines = getGtMachinesForTable(gt, widget.maxRows || 10);
          if (machines.length === 0) {
            return wrap(
              <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Empty description={t('noGTData')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
              </div>
            );
          }
          return wrap(
            <>
              {labelText(label)}
              <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                {machines.map((m, i) => {
                  const statusColorMap: Record<string, string> = {
                    Running: 'success',
                    Idle: 'default',
                    Error: 'error',
                    Problem: 'warning',
                    Maintenance: 'warning',
                  };
                  return (
                    <div
                      key={`${m.x}_${m.y}_${m.z}_${i}`}
                      style={{
                        fontSize: '0.7rem',
                        margin: '4px 0',
                        padding: '4px 6px',
                        background: i % 2 === 1 ? (rowAltBg || 'var(--bg-hover)') : 'var(--bg-hover)',
                        borderRadius: 4,
                        border: '1px solid var(--border-light)',
                      }}
                    >
                      {(cols.includes('recipe') || cols.includes('status')) && <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 4 }}>
                        {cols.includes('recipe') && (
                        <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                          {m.recipeMapName || m.machineMode || '-'}
                        </span>
                        )}
                        {cols.includes('status') && (
                        <Tag color={statusColorMap[m.statusText] || 'default'} style={{ margin: 0, fontSize: '0.65rem' }}>
                          {gtStatusLabel(m.statusText, t)}
                        </Tag>
                        )}
                      </div>
                      }
                      <div style={{ display: 'flex', gap: 8, marginTop: 2, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
                        {cols.includes('coords') && <code style={{ fontSize: '0.65rem' }}>{m.x},{m.y},{m.z}</code>}
                        {cols.includes('progress') && m.maxProgressTime > 0 && (
                          <span>{Math.round(m.progressPercent)}% ({m.progressTime}/{m.maxProgressTime}t)</span>
                        )}
                        {cols.includes('parallel') && m.parallelCount > 1 && <Tag color="blue">×{m.parallelCount}</Tag>}
                      </div>
                      {cols.includes('output') && m.currentOutput && (
                        <div style={{ marginTop: 2, color: chartColor, fontSize: '0.68rem' }}>{m.currentOutput}</div>
                      )}
                    </div>
                  );
                })}
              </div>
            </>
          );
        }
        if (widget.dataSource === 'networkBalance') {
          const balPins = pinnedRows.filter((p) => p.pin.kind === 'balance');
          if (balPins.length > 0 || widget.pinsOnly) {
            const rows = widget.pinsOnly
              ? balPins
              : [
                  ...balPins,
                  ...resolvePins(
                    (balanceSuggestions || [])
                      .filter((s) => {
                        const id = s.itemId || s.displayName;
                        return !balPins.some((p) => p.pin.id === id);
                      })
                      .slice(0, widget.maxRows || 10)
                      .map((s) => ({
                        kind: 'balance' as const,
                        id: s.itemId || s.displayName,
                        label: s.displayName,
                      })),
                    pinCtx
                  ),
                ];
            return wrap(
              <>
                {labelText(label)}
                <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                  {rows.map((row, i) => (
                    <div
                      key={i}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                        margin: '2px 0',
                        fontSize: '0.75rem',
                        padding: '2px 4px',
                        borderRadius: 4,
                        background: i % 2 === 1 ? rowAltBg : undefined,
                      }}
                    >
                      {cols.includes('resource') && (
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {row.label}
                        </span>
                      )}
                      {cols.includes('type') && row.secondary && <Tag style={{ margin: 0, fontSize: '0.65rem' }}>{row.secondary}</Tag>}
                      {(cols.includes('gap') || cols.includes('needy') || cols.includes('surplus')) && (
                        <strong style={{ color: chartColor }}>{fmtNum(row.value)}</strong>
                      )}
                    </div>
                  ))}
                  {rows.length === 0 && (
                    <Empty description={t('dashPinEmpty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  )}
                </div>
              </>
            );
          }
          return wrap(
            <>
              {labelText(label)}
              <div style={{ overflow: 'auto', flex: 1, width: '100%' }}>
                <NetworkBalanceTable networkIds={selectedNetworks} compact visibleColumns={cols} />
              </div>
            </>
          );
        }
        return wrap(<div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Empty description={t('noData')} /></div>);
      }

      case 'barChart': {
        if (widget.dataSource === 'customPins') {
          const pinned = resolvePins(widget.pins, pinCtx);
          return renderCategoricalBarChart(
            pinned.map((p) => ({ label: p.label, value: p.value })),
            widget,
            settings,
            chartSize,
            chartColor,
            fmtNum,
            labelText,
            label,
            wrap,
            chartRecipe
          );
        }
        if (widget.dataSource === 'machineByStatus') {
          return renderCategoricalBarChart(
            getGtStatusBreakdown(gt?.machines, t),
            widget,
            settings,
            chartSize,
            chartColor,
            fmtNum,
            labelText,
            label,
            wrap,
            chartRecipe
          );
        }
        if (widget.dataSource === 'networkCompare') {
          return renderNetworkCompareChart(
            buildNetworkCompareRows(selectedNetworks, storageMap, powerMap, gtMap),
            widget,
            settings,
            chartSize,
            chartColor,
            t,
            fmtNum,
            labelText,
            label,
            wrap
          );
        }
        return renderCategoricalBarChart(
          getStorageCategoryBreakdown(
            storage
              ? {
                  items: storage.items,
                  fluids: storage.fluids,
                  essentia: storage.essentia,
                  bytesUsed: storage.bytesUsed,
                  bytesMax: storage.bytesMax,
                  cpus: storage.cpus || [],
                }
              : null,
            t
          ),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }

      case 'pieChart': {
        if (widget.dataSource === 'customPins') {
          const pinned = resolvePins(widget.pins, pinCtx);
          return renderCategoricalPieChart(
            pinned.map((p) => ({ label: p.label, value: p.value })),
            widget,
            settings,
            chartSize,
            chartColor,
            fmtNum,
            labelText,
            label,
            wrap,
            chartRecipe
          );
        }
        if (widget.dataSource === 'machineByStatus') {
          return renderCategoricalPieChart(
            getGtStatusBreakdown(gt?.machines, t),
            widget,
            settings,
            chartSize,
            chartColor,
            fmtNum,
            labelText,
            label,
            wrap,
            chartRecipe
          );
        }
        return renderCategoricalPieChart(
          getStorageCategoryBreakdown(
            storage
              ? {
                  items: storage.items,
                  fluids: storage.fluids,
                  essentia: storage.essentia,
                  bytesUsed: storage.bytesUsed,
                  bytesMax: storage.bytesMax,
                  cpus: storage.cpus || [],
                }
              : null,
            t
          ),
          widget,
          settings,
          chartSize,
          chartColor,
          fmtNum,
          labelText,
          label,
          wrap,
          chartRecipe
        );
      }

      case 'radarChart': {
        const pinned = resolvePins(widget.pins, pinCtx);
        const pinAxisValues =
          pinned.length >= 3
            ? pinned.slice(0, 8).map((p) => ({ label: p.label, value: p.value }))
            : pinned.length > 0
              ? [
                  ...pinned.map((p) => ({ label: p.label, value: p.value })),
                  ...resolveRadarAxes(widget)
                    .slice(0, Math.max(0, 3 - pinned.length))
                    .map((a) => ({
                      label: a.label?.trim() || dataSourceLabel(a.dataSource),
                      value: dataSourceValue(a.dataSource),
                    })),
                ]
              : undefined;
        // Need at least 3 axes for a readable radar
        const axisValues =
          pinAxisValues && pinAxisValues.length >= 3 ? pinAxisValues : undefined;
        return wrap(
          <>
            {labelText(label)}
            <RadarChartWidget
              widget={widget}
              axes={resolveRadarAxes(widget)}
              axisValues={axisValues}
              chartSize={chartSize}
              chartColor={chartColor}
              radarAxisColor={colors.radarAxisColor || 'var(--border)'}
              getValue={dataSourceValue}
              getLabel={dataSourceLabel}
              fmtNum={fmtNum}
              recipe={chartRecipe}
            />
          </>
        );
      }

      default:
        return wrap(<div>{widget.type}</div>);
    }
  };

  const handleAddWidget = () => {
    const type = (newWidget.type || 'statCard') as DashboardWidgetConfig['type'];
    const widget: DashboardWidgetConfig = {
      id: createWidgetId('w-'),
      type,
      dataSource: isLayoutOrFeedType(type)
        ? (type === 'alertsSummary' ? 'alertsActive' : type === 'craftingQueue' ? 'craftingBusy' : 'none')
        : (newWidget.dataSource || 'itemCount'),
      scope: (newWidget.scope || 'perNetwork') as 'global' | 'perNetwork',
      title: newWidget.title || '',
      width: newWidget.width || (type === 'group' ? 6 : 3),
      height: newWidget.height || (type === 'group' ? 4 : 2),
      x: 0,
      y: 0,
      contentScale: 1,
      pins: [],
      children: type === 'group' ? [] : undefined,
      noteText: type === 'textNote' ? '' : undefined,
    };
    if (addTargetGroupId) {
      const groupId = addTargetGroupId;
      saveSettings((prev) => ({
        ...prev,
        widgets: addChildToGroup(prev.widgets, groupId, widget),
      }));
    } else {
      saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, widget] }));
    }
    setAddWidgetOpen(false);
    setAddTargetGroupId(null);
    setNewWidget({ type: 'statCard', dataSource: 'itemCount', scope: 'perNetwork', title: '', width: 3, height: 2 });
  };

  const handleAddFromPalette = (preset: (typeof PALETTE_PRESETS)[number]) => {
    const widget: DashboardWidgetConfig = {
      id: createWidgetId('w-'),
      type: preset.type,
      dataSource: preset.dataSource,
      scope: 'perNetwork',
      title: '',
      width: preset.w,
      height: preset.h,
      x: 0,
      y: 0,
      contentScale: 1,
      pins: [],
      children: preset.type === 'group' ? [] : undefined,
    };
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, widget] }));
  };

  const handleDeleteWidget = (id: string) => {
    saveSettings((prev) => ({ ...prev, widgets: removeWidgetById(prev.widgets, id) }));
  };

  const handleCopyWidget = (widget: DashboardWidgetConfig) => {
    const copy = copyWidgetConfig(widget, 'w-');
    // If copying a child, we append to root; GroupWidget copy appends inside group via callback.
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, copy] }));
    notify(t('widgetCopied'), 'success');
  };

  const handleCopyChildIntoGroup = (groupId: string, child: DashboardWidgetConfig) => {
    const copy = copyWidgetConfig(child, 'w-');
    saveSettings((prev) => ({
      ...prev,
      widgets: addChildToGroup(prev.widgets, groupId, copy),
    }));
    notify(t('widgetCopied'), 'success');
  };

  const handleResetLayout = () => {
    saveSettings({ ...DEFAULT_DASHBOARD_SETTINGS, widgets: DEFAULT_DASHBOARD_WIDGETS });
    notify(t('layoutReset'), 'info');
  };

  const commitOuterLayoutFromGrid = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid?.engine.nodes.length) return;
    const nodes = grid.engine.nodes;
    cancelLayoutSave();
    commitSettings((prev) => {
      const next = { ...prev, widgets: applyOuterNodePositions(prev.widgets, nodes) };
      scheduleLayoutSave(next);
      return next;
    });
  }, [cancelLayoutSave, commitSettings, scheduleLayoutSave]);

  // 自动排列：调用 GridStack.compact() 将所有 widget 紧凑排列到顶部。
  // 仅在编辑模式下可用（grid 处于非 static 状态时 compact 才会真正重排）。
  const handleAutoArrange = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      // GridStack compact：'compact' 模式会尽量消除空隙、将 widget 紧凑排列到顶部。
      grid.compact('compact');
      commitOuterLayoutFromGrid();
      flushLayoutSave();
      notify(t('autoArrangeDone'), 'success');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t, commitOuterLayoutFromGrid, flushLayoutSave]);
  // 重建 GridStack 的依赖：widget id 列表 + 每个 widget 的宽高（含嵌套）+ 锁标志
  const layoutSignature = useMemo(
    () => widgetLayoutSignature(widgets),
    [widgets]
  );


  // GridStack init / layout persistence
  useEffect(() => {
    if (!gridRef.current || selectedNetworks.length === 0) return;

    const grid = GridStack.init(
      {
        column: 12,
        cellHeight: 64,
        margin: settings.widgetGap ?? 12,
        staticGrid: !editMode,
        // float:true —— 重建时严格尊重 gs-x/gs-y，避免按旧 margin 排布的 widget
        // 在新间距下被吸附紧凑重排（grid.margin() 不会重排已有节点内联 transform）。
        // init 完成后立即 grid.float(false) 恢复拖拽下落行为，仅设置标志不重排已有节点。
        float: true,
        animate: true,
        acceptWidgets: false,
        removable: editMode ? '.dashboard-trash' : false,
        removableOptions: { accept: '.grid-stack-item' },
        draggable: { cancel: GRID_DRAG_CANCEL_SELECTOR },
      },
      gridRef.current
    );
    gridInstanceRef.current = grid;
    grid.float(false);

    const onRemoved = (_ev: Event, items: Array<{ id?: string | number }>) => {
      if (!items?.length) return;
      cancelLayoutSave();
      commitSettings((prev) => {
        let nextWidgets = prev.widgets;
        for (const item of items) {
          if (item.id != null) {
            nextWidgets = removeWidgetById(nextWidgets, String(item.id));
          }
        }
        const next = { ...prev, widgets: nextWidgets };
        persistSettings(next);
        return next;
      });
    };
    const onDragOrResizeStop = () => {
      commitOuterLayoutFromGrid();
      flushLayoutSave();
    };
    grid.on('removed', onRemoved);
    grid.on('dragstop', onDragOrResizeStop);
    grid.on('resizestop', onDragOrResizeStop);

    return () => {
      flushLayoutSave();
      grid.off('removed');
      grid.off('dragstop');
      grid.off('resizestop');
      grid.destroy(false);
      gridInstanceRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layoutSignature, editMode, selectedNetworks.length, settings.margin, settings.widgetGap]);

  if (selectedNetworks.length === 0) {
    return (
      <PageShell title={t('dashboard')}>
        <Empty description={t('selectNetworkFirst')} />
      </PageShell>
    );
  }

  return (
    <PageShell
      title={t('dashboard')}
      actions={
        <Space wrap>
          <Tooltip title={t('dashCfgOpenHint')}>
            <Button
              icon={<ControlOutlined />}
              onClick={() => setSettingsOpen(true)}
            >
              {t('dashCfgOpen')}
            </Button>
          </Tooltip>
          <Button
            type={editMode ? 'primary' : 'default'}
            icon={<EditOutlined />}
            onClick={() => setEditMode(!editMode)}
          >
            {editMode ? t('done') : t('editDashboard')}
          </Button>
          <Tooltip title={t('editorUndoHint')}>
            <Button icon={<UndoOutlined />} onClick={handleUndo} disabled={!editMode || !canUndo}>
              {t('editorUndo')}
            </Button>
          </Tooltip>
          <Tooltip title={t('editorRedoHint')}>
            <Button icon={<RedoOutlined />} onClick={handleRedo} disabled={!editMode || !canRedo}>
              {t('editorRedo')}
            </Button>
          </Tooltip>
          <Button
            icon={<PlusOutlined />}
            onClick={() => {
              setAddTargetGroupId(null);
              setAddWidgetOpen(true);
            }}
            disabled={!editMode}
          >
            {t('addWidget')}
          </Button>
          <Tooltip title={t('autoArrangeHint')}>
            <Button
              icon={<AlignLeftOutlined />}
              onClick={handleAutoArrange}
              disabled={!editMode}
            >
              {t('autoArrange')}
            </Button>
          </Tooltip>
          <Button icon={<ReloadOutlined />} onClick={handleResetLayout} disabled={!editMode}>
            {t('resetLayout')}
          </Button>
        </Space>
      }
    >
      <div className="dashboard-grid-wrap">
        {editMode && (
          <div className="dashboard-palette" aria-label={t('widgetPalette')}>
            <span className="dashboard-palette-label">{t('widgetPalette')}</span>
            <Space wrap size={[6, 6]}>
              {PALETTE_PRESETS.map((p) => (
                <Button
                  key={p.type + p.dataSource}
                  size="small"
                  onClick={() => handleAddFromPalette(p)}
                >
                  {t('widgetType_' + p.type)}
                </Button>
              ))}
            </Space>
          </div>
        )}
        <div className="grid-stack" ref={gridRef} style={{ padding: settings.margin }}>
          {widgets.map((widget) => (
            <div
              key={widget.id}
              className="grid-stack-item"
              gs-id={widget.id}
              gs-x={widget.x}
              gs-y={widget.y}
              gs-w={widget.width}
              gs-h={widget.height}
              gs-min-w={1}
              gs-min-h={1}
              {...(widget.locked ? { 'gs-locked': 'yes' } : {})}
              {...(widget.noMove ? { 'gs-no-move': 'yes' } : {})}
              {...(widget.noResize ? { 'gs-no-resize': 'yes' } : {})}
              {...(widget.sizeToContent ? { 'gs-size-to-content': 'true' } : {})}
            >
              <WidgetShell
                widget={widget}
                settings={settings}
                lastUpdateTime={lastUpdateTime}
                className={widget.type === 'group' ? 'widget-shell--group' : undefined}
                editOverlay={
                  editMode && widget.type !== 'group' ? (
                    <div
                      className={`dashboard-grid-edit-actions ${GRID_EDIT_NO_DRAG_CLASS}`}
                      style={{ position: 'absolute', top: 4, right: 4, display: 'flex', gap: 4, zIndex: 2 }}
                      onMouseDown={stopGridDragPointer}
                      onPointerDown={stopGridDragPointer}
                    >
                      <Tooltip title={t('editWidget')}>
                        <Button
                          size="small"
                          icon={<SettingOutlined />}
                          onClick={() => setEditWidgetTarget(widget)}
                          aria-label={t('editWidget')}
                        />
                      </Tooltip>
                      <Tooltip title={t('copyWidget')}>
                        <Button
                          size="small"
                          icon={<PlusOutlined />}
                          onClick={() => handleCopyWidget(widget)}
                          aria-label={t('copyWidget')}
                        />
                      </Tooltip>
                      <Tooltip title={t('deleteWidget')}>
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => handleDeleteWidget(widget.id)}
                          aria-label={t('deleteWidget')}
                        />
                      </Tooltip>
                    </div>
                  ) : undefined
                }
              >
                {widget.type === 'group' ? (
                  <GroupWidget
                    widget={widget}
                    settings={settings}
                    editMode={editMode}
                    lastUpdateTime={lastUpdateTime}
                    renderChild={renderWidget}
                    flushLayoutSave={flushLayoutSave}
                    onChildrenChange={(children) => {
                      saveSettings((prev) => ({
                        ...prev,
                        widgets: updateWidgetById(prev.widgets, widget.id, (g) => ({ ...g, children })),
                      }));
                    }}
                    onEditGroup={() => setEditWidgetTarget(widget)}
                    onEditChild={(child) => setEditWidgetTarget(child)}
                    onCopyChild={(child) => handleCopyChildIntoGroup(widget.id, child)}
                    onDeleteChild={(childId) => handleDeleteWidget(childId)}
                    onAddChild={() => {
                      setAddTargetGroupId(widget.id);
                      setAddWidgetOpen(true);
                    }}
                  />
                ) : (
                  renderWidget(widget)
                )}
              </WidgetShell>
            </div>
          ))}
        </div>
        {editMode && (
          <div className="dashboard-trash" title={t('dashboardTrashHint')}>
            <DeleteFilled />
            <span>{t('dashboardTrash')}</span>
          </div>
        )}
      </div>

      <DashboardSettingsDrawer
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        settings={settings}
        onChange={saveSettings}
        widgets={widgets}
        onWidgetsChange={(w) => saveSettings((prev) => ({ ...prev, widgets: w }))}
      />

      {/* Add Widget Modal */}
      <Modal
        title={addTargetGroupId ? t('addWidgetToGroup') : t('addWidget')}
        open={addWidgetOpen}
        onOk={handleAddWidget}
        onCancel={() => {
          setAddWidgetOpen(false);
          setAddTargetGroupId(null);
        }}
        okText={t('ok')}
        cancelText={t('cancel')}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          {!isLayoutOrFeedType((newWidget.type || 'statCard') as DashboardWidgetConfig['type']) && (
            <div>
              <span>{t('dataSource')}</span>
              <Select
                style={{ width: '100%', marginTop: 4 }}
                value={newWidget.dataSource}
                onChange={(v) => {
                  const valid = getValidChartTypes(v);
                  const typeOk = newWidget.type && valid.includes(newWidget.type);
                  setNewWidget({
                    ...newWidget,
                    dataSource: v,
                    type: typeOk ? newWidget.type : valid[0] || 'statCard',
                  });
                }}
                options={DATA_SOURCES.filter((ds) => !['none', 'alertsActive', 'craftingBusy'].includes(ds)).map((ds) => ({
                  label: t('dataSource_' + ds),
                  value: ds,
                }))}
                showSearch
                optionFilterProp="label"
              />
            </div>
          )}
          <div>
            <span>{t('editWidget_type')}</span>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={newWidget.type}
              onChange={(v) => {
                const type = v as DashboardWidgetConfig['type'];
                setNewWidget({
                  ...newWidget,
                  type,
                  dataSource: isLayoutOrFeedType(type)
                    ? (type === 'alertsSummary' ? 'alertsActive' : type === 'craftingQueue' ? 'craftingBusy' : 'none')
                    : (newWidget.dataSource && !['none', 'alertsActive', 'craftingBusy'].includes(newWidget.dataSource)
                      ? newWidget.dataSource
                      : 'itemCount'),
                  width: type === 'group' ? 6 : newWidget.width || 3,
                  height: type === 'group' ? 4 : newWidget.height || 2,
                });
              }}
              options={(newWidget.dataSource && !isLayoutOrFeedType((newWidget.type || 'statCard') as DashboardWidgetConfig['type'])
                ? getValidChartTypes(newWidget.dataSource)
                : WIDGET_TYPES
              )
                .concat(WIDGET_TYPES.filter(isLayoutOrFeedType))
                .filter((tp, i, arr) => arr.indexOf(tp) === i)
                .filter((tp) => WIDGET_TYPES.includes(tp))
                .map((tp) => ({ label: t('widgetType_' + tp), value: tp }))}
            />
          </div>
          <Space>
            <span>{t('width')}:</span>
            <InputNumber min={1} max={12} value={newWidget.width} onChange={(v) => setNewWidget({ ...newWidget, width: v || 3 })} />
            <span>{t('height')}:</span>
            <InputNumber min={1} max={10} value={newWidget.height} onChange={(v) => setNewWidget({ ...newWidget, height: v || 2 })} />
          </Space>
        </Space>
      </Modal>

      <EditWidgetModal
        open={!!editWidgetTarget}
        widget={editWidgetTarget}
        settings={settings}
        storage={storage}
        gtMachines={gt?.machines ?? null}
        balanceSuggestions={balanceSuggestions}
        onWidgetChange={(w) => setEditWidgetTarget(w)}
        onOk={() => {
          if (editWidgetTarget) {
            const target = editWidgetTarget;
            saveSettings((prev) => ({
              ...prev,
              widgets: updateWidgetById(prev.widgets, target.id, () => target),
            }));
          }
          setEditWidgetTarget(null);
        }}
        onCancel={() => setEditWidgetTarget(null)}
      />
    </PageShell>
  );
}
