import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { GridStack } from 'gridstack';
import 'gridstack/dist/gridstack.min.css';
import { Button, Modal, Select, InputNumber, Space, Tooltip } from 'antd';
import {
  EditOutlined,
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SettingOutlined,
  AlignLeftOutlined,
  ControlOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useDebouncedLocalStorageSaver } from '@/hooks/useDebouncedLocalStorageSaver';
import {
  overviewAsDashboardSettings,
  DEFAULT_POWER_SETTINGS,
  POWER_CONFIG_KEY,
  loadOverviewSettingsFromStorage,
  type DashboardSettings,
  type DashboardWidgetConfig,
  type PowerSettings,
} from '@/utils/presets';
import { DashboardSettingsDrawer } from '@/components/dashboard/DashboardSettingsDrawer';
import { EditWidgetModal } from '@/components/dashboard/EditWidgetModal';
import { PowerWidgetContent } from '@/components/dashboard/PowerWidgetContent';
import { WidgetShell } from '@/components/dashboard/WidgetShell';
import { copyWidgetConfig } from '@/utils/widgetGridActions';
import { POWER_DATA_SOURCES, type PowerSnapshot } from '@/utils/powerDataSources';

const WIDGET_TYPES: DashboardWidgetConfig['type'][] = [
  'statCard',
  'progressBar',
  'gauge',
  'lineChart',
];

interface PowerWidgetGridProps {
  snapshot: PowerSnapshot | null;
  initialLoading?: boolean;
  disabled?: boolean;
}

export function PowerWidgetGrid({
  snapshot,
  initialLoading = false,
  disabled = false,
}: PowerWidgetGridProps) {
  const { notify, selectedNetworks, lastUpdateTime } = useAppContext();
  const { t } = useI18n();
  const fmtNum = useNumberFormat();
  const networkMetrics = useNetworkMetrics();
  const primaryNetworkId = selectedNetworks[0] ?? 0;
  const [editMode, setEditMode] = useState(false);
  const [settings, setSettings] = useState<PowerSettings>(() =>
    loadOverviewSettingsFromStorage(POWER_CONFIG_KEY, DEFAULT_POWER_SETTINGS)
  );
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [addWidgetOpen, setAddWidgetOpen] = useState(false);
  const [editWidgetTarget, setEditWidgetTarget] = useState<DashboardWidgetConfig | null>(null);
  const [newWidget, setNewWidget] = useState<Partial<DashboardWidgetConfig>>({
    type: 'statCard',
    dataSource: POWER_DATA_SOURCES[0],
    scope: 'perNetwork',
    title: '',
    width: 3,
    height: 2,
  });

  const gridRef = useRef<HTMLDivElement>(null);
  const gridInstanceRef = useRef<GridStack | null>(null);
  const { schedule: scheduleLayoutSave, flush: flushLayoutSave } =
    useDebouncedLocalStorageSaver<PowerSettings>(POWER_CONFIG_KEY);

  const saveSettings = useCallback((s: PowerSettings) => {
    setSettings(s);
    try {
      localStorage.setItem(POWER_CONFIG_KEY, JSON.stringify(s));
    } catch {
      /* ignore */
    }
  }, []);

  const dashboardSettings = useMemo(() => overviewAsDashboardSettings(settings), [settings]);
  const widgets = settings.widgets.length > 0 ? settings.widgets : DEFAULT_POWER_SETTINGS.widgets;

  const dataSourceLabel = useCallback(
    (ds: string) => {
      const key = 'dataSource_' + ds;
      const translated = t(key);
      return translated !== key ? translated : ds;
    },
    [t]
  );

  const handleAddWidget = () => {
    const widget: DashboardWidgetConfig = {
      id: 'pw-' + Date.now(),
      type: (newWidget.type || 'statCard') as DashboardWidgetConfig['type'],
      dataSource: newWidget.dataSource || POWER_DATA_SOURCES[0],
      scope: 'perNetwork',
      title: newWidget.title || '',
      width: newWidget.width || 3,
      height: newWidget.height || 2,
      x: 0,
      y: 0,
    };
    saveSettings({ ...settings, widgets: [...settings.widgets, widget] });
    setAddWidgetOpen(false);
    setNewWidget({
      type: 'statCard',
      dataSource: POWER_DATA_SOURCES[0],
      scope: 'perNetwork',
      title: '',
      width: 3,
      height: 2,
    });
  };

  const handleDeleteWidget = (id: string) => {
    saveSettings({ ...settings, widgets: settings.widgets.filter((w) => w.id !== id) });
  };

  const handleCopyWidget = (widget: DashboardWidgetConfig) => {
    const copy = copyWidgetConfig(widget, 'pw-');
    saveSettings({ ...settings, widgets: [...settings.widgets, copy] });
    notify(t('widgetCopied'), 'success');
  };

  const handleResetLayout = () => {
    saveSettings(DEFAULT_POWER_SETTINGS);
    notify(t('layoutReset'), 'info');
  };

  const handleAutoArrange = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      grid.compact('compact');
      notify(t('autoArrangeDone'), 'success');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t]);

  const layoutSignature = useMemo(
    () => widgets.map((w) => `${w.id}_${w.width}_${w.height}`).join(','),
    [widgets]
  );

  useEffect(() => {
    if (!gridRef.current || disabled) return;

    const grid = GridStack.init(
      {
        column: 12,
        cellHeight: 64,
        margin: settings.widgetGap ?? 12,
        staticGrid: !editMode,
        // float:true —— 重建时严格尊重 gs-x/gs-y，避免间距变化触发吸附重排。
        // init 完成后立即 grid.float(false) 恢复拖拽下落行为，仅设置标志不重排已有节点。
        float: true,
        animate: true,
      },
      gridRef.current
    );
    gridInstanceRef.current = grid;
    grid.float(false);

    const onChange = () => {
      const nodes = grid.engine.nodes;
      if (!nodes.length) return;
      setSettings((prev) => {
        const nextWidgets = prev.widgets.map((w) => {
          const node = nodes.find((n) => n.id === w.id);
          if (!node) return w;
          return {
            ...w,
            x: node.x ?? w.x,
            y: node.y ?? w.y,
            width: node.w ?? w.width,
            height: node.h ?? w.height,
          };
        });
        const next = { ...prev, widgets: nextWidgets };
        scheduleLayoutSave(next);
        return next;
      });
    };
    grid.on('change', onChange);
    grid.on('dragstop', flushLayoutSave);
    grid.on('resizestop', flushLayoutSave);

    return () => {
      flushLayoutSave();
      grid.off('change');
      grid.off('dragstop');
      grid.off('resizestop');
      grid.destroy(false);
      gridInstanceRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layoutSignature, editMode, settings.margin, settings.widgetGap, disabled]);

  const handleSettingsChange = (s: DashboardSettings) => {
    saveSettings({
      margin: s.margin,
      widgetGap: s.widgetGap ?? 12,
      contentInset: s.contentInset ?? 0,
      borderWidth: s.borderWidth,
      chartStretchMode: s.chartStretchMode ?? 'stretchX',
      fontSize: s.fontSize,
      chartSize: s.chartSize,
      chartShowValueAxis: s.chartShowValueAxis ?? false,
      chartShowTimeAxis: s.chartShowTimeAxis ?? false,
      defaultAlignment: s.defaultAlignment,
      defaultColors: s.defaultColors,
      colorPresets: s.colorPresets,
      widgets: s.widgets,
    });
  };

  return (
    <div className="power-grid-section">
      <div className="power-grid-toolbar" style={{ marginBottom: 8 }}>
        <Space wrap>
          <Tooltip title={t('dashCfgOpenHint')}>
            <Button
              icon={<ControlOutlined />}
              onClick={() => setSettingsOpen(true)}
              size="small"
              aria-label={t('powerOverviewSettings')}
            >
              {t('powerOverviewSettings')}
            </Button>
          </Tooltip>
          <Button
            type={editMode ? 'primary' : 'default'}
            icon={<EditOutlined />}
            size="small"
            onClick={() => setEditMode(!editMode)}
            aria-pressed={editMode}
          >
            {editMode ? t('done') : t('editPowerLayout')}
          </Button>
          <Button
            icon={<PlusOutlined />}
            size="small"
            onClick={() => setAddWidgetOpen(true)}
            disabled={!editMode}
          >
            {t('addWidget')}
          </Button>
          <Tooltip title={t('autoArrangeHint')}>
            <Button
              icon={<AlignLeftOutlined />}
              size="small"
              onClick={handleAutoArrange}
              disabled={!editMode}
            >
              {t('autoArrange')}
            </Button>
          </Tooltip>
          <Button
            icon={<ReloadOutlined />}
            size="small"
            onClick={handleResetLayout}
            disabled={!editMode}
          >
            {t('resetLayout')}
          </Button>
        </Space>
      </div>

      <div
        className={`grid-stack power-widget-grid ${disabled ? 'power-grid-disabled' : ''}`}
        ref={gridRef}
        style={{ padding: settings.margin }}
      >
        {widgets.map((widget) => (
          <div
            key={widget.id}
            className="grid-stack-item power-grid-item"
            gs-id={widget.id}
            gs-x={widget.x}
            gs-y={widget.y}
            gs-w={widget.width}
            gs-h={widget.height}
            gs-min-w={2}
            gs-min-h={2}
          >
            <WidgetShell
              widget={widget}
              settings={dashboardSettings}
              className="power-grid-item-content"
              lastUpdateTime={lastUpdateTime}
              editOverlay={
                editMode ? (
                  <div className="power-grid-edit-actions">
                    <Tooltip title={t('editWidget')}>
                      <Button size="small" icon={<SettingOutlined />} onClick={() => setEditWidgetTarget(widget)} aria-label={t('editWidget')} />
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
                      <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteWidget(widget.id)} aria-label={t('deleteWidget')} />
                    </Tooltip>
                  </div>
                ) : undefined
              }
            >
              <PowerWidgetContent
                widget={widget}
                settings={dashboardSettings}
                snapshot={snapshot}
                t={t}
                fmtNum={fmtNum}
                dataSourceLabel={dataSourceLabel}
                initialLoading={initialLoading && widget.type === 'lineChart'}
                networkId={primaryNetworkId}
                getHistory={networkMetrics.getHistory}
              />
            </WidgetShell>
          </div>
        ))}
      </div>

      <DashboardSettingsDrawer
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        settings={dashboardSettings}
        onChange={handleSettingsChange}
        widgets={widgets}
        onWidgetsChange={(w) => saveSettings({ ...settings, widgets: w })}
      />

      <Modal
        title={t('addWidget')}
        open={addWidgetOpen}
        onOk={handleAddWidget}
        onCancel={() => setAddWidgetOpen(false)}
        okText={t('ok')}
        cancelText={t('cancel')}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            style={{ width: '100%' }}
            value={newWidget.type}
            onChange={(v) => setNewWidget({ ...newWidget, type: v })}
            options={WIDGET_TYPES.map((tp) => ({ label: t('widgetType_' + tp), value: tp }))}
          />
          <Select
            style={{ width: '100%' }}
            value={newWidget.dataSource}
            onChange={(v) => setNewWidget({ ...newWidget, dataSource: v })}
            options={POWER_DATA_SOURCES.map((ds) => ({
              label: dataSourceLabel(ds),
              value: ds,
            }))}
          />
          <Space>
            <span>{t('width')}:</span>
            <InputNumber
              min={2}
              max={12}
              value={newWidget.width}
              onChange={(v) => setNewWidget({ ...newWidget, width: v || 3 })}
            />
            <span>{t('height')}:</span>
            <InputNumber
              min={2}
              max={10}
              value={newWidget.height}
              onChange={(v) => setNewWidget({ ...newWidget, height: v || 2 })}
            />
          </Space>
        </Space>
      </Modal>

      <EditWidgetModal
        open={!!editWidgetTarget}
        widget={editWidgetTarget}
        settings={dashboardSettings}
        onWidgetChange={(w) => setEditWidgetTarget(w)}
        onOk={() => {
          if (editWidgetTarget) {
            saveSettings({
              ...settings,
              widgets: settings.widgets.map((w) =>
                w.id === editWidgetTarget.id ? editWidgetTarget : w
              ),
            });
          }
          setEditWidgetTarget(null);
        }}
        onCancel={() => setEditWidgetTarget(null)}
        allowedDataSources={POWER_DATA_SOURCES}
        allowedWidgetTypes={WIDGET_TYPES}
      />
    </div>
  );
}
