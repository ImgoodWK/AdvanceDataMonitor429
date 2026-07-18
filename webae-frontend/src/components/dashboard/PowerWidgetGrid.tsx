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
  UndoOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useDebouncedLocalStorageSaver } from '@/hooks/useDebouncedLocalStorageSaver';
import { useEditorHistory, useUndoRedoHotkeys } from '@/hooks/useEditorHistory';
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
import { createWidgetId } from '@/utils/widgetId';
import { widgetLayoutSignature } from '@/utils/dashboardTree';
import {
  GRID_DRAG_CANCEL_SELECTOR,
  GRID_EDIT_NO_DRAG_CLASS,
  stopGridDragPointer,
} from '@/utils/gridStackEditGuard';
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
  const {
    present: settings,
    commit: commitSettings,
    undo: undoSettings,
    redo: redoSettings,
    canUndo,
    canRedo,
  } = useEditorHistory<PowerSettings>(() =>
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
  const { schedule: scheduleLayoutSave, flush: flushLayoutSave, cancel: cancelLayoutSave } =
    useDebouncedLocalStorageSaver<PowerSettings>(POWER_CONFIG_KEY);

  const persistSettings = useCallback((s: PowerSettings) => {
    try {
      localStorage.setItem(POWER_CONFIG_KEY, JSON.stringify(s));
    } catch {
      /* ignore */
    }
  }, []);

  const saveSettings = useCallback(
    (s: PowerSettings | ((prev: PowerSettings) => PowerSettings)) => {
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

  useUndoRedoHotkeys(handleUndo, handleRedo, editMode && !disabled);

  const dashboardSettings = useMemo(() => overviewAsDashboardSettings(settings), [settings]);
  const widgets = settings.widgets;

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
      id: createWidgetId('pw-'),
      type: (newWidget.type || 'statCard') as DashboardWidgetConfig['type'],
      dataSource: newWidget.dataSource || POWER_DATA_SOURCES[0],
      scope: 'perNetwork',
      title: newWidget.title || '',
      width: newWidget.width || 3,
      height: newWidget.height || 2,
      x: 0,
      y: 0,
    };
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, widget] }));
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
    saveSettings((prev) => ({ ...prev, widgets: prev.widgets.filter((w) => w.id !== id) }));
  };

  const handleCopyWidget = (widget: DashboardWidgetConfig) => {
    const copy = copyWidgetConfig(widget, 'pw-');
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, copy] }));
    notify(t('widgetCopied'), 'success');
  };

  const handleResetLayout = () => {
    saveSettings(DEFAULT_POWER_SETTINGS);
    notify(t('layoutReset'), 'info');
  };

  const commitLayoutFromGrid = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid?.engine.nodes.length) return;
    const nodes = grid.engine.nodes;
    cancelLayoutSave();
    commitSettings((prev) => {
      const nextWidgets = prev.widgets.map((w) => {
        const node = nodes.find((n) => String(n.id) === w.id);
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
  }, [cancelLayoutSave, commitSettings, scheduleLayoutSave]);

  const handleAutoArrange = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      grid.compact('compact');
      commitLayoutFromGrid();
      flushLayoutSave();
      notify(t('autoArrangeDone'), 'success');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t, commitLayoutFromGrid, flushLayoutSave]);

  const layoutSignature = useMemo(() => widgetLayoutSignature(widgets), [widgets]);

  useEffect(() => {
    if (!gridRef.current || disabled) return;

    const grid = GridStack.init(
      {
        column: 12,
        cellHeight: 64,
        margin: settings.widgetGap ?? 12,
        staticGrid: !editMode,
        float: true,
        animate: true,
        draggable: { cancel: GRID_DRAG_CANCEL_SELECTOR },
      },
      gridRef.current
    );
    gridInstanceRef.current = grid;
    grid.float(false);

    const onDragOrResizeStop = () => {
      commitLayoutFromGrid();
      flushLayoutSave();
    };
    grid.on('dragstop', onDragOrResizeStop);
    grid.on('resizestop', onDragOrResizeStop);

    return () => {
      flushLayoutSave();
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
          <Tooltip title={t('editorUndoHint')}>
            <Button
              icon={<UndoOutlined />}
              size="small"
              onClick={handleUndo}
              disabled={!editMode || !canUndo}
            />
          </Tooltip>
          <Tooltip title={t('editorRedoHint')}>
            <Button
              icon={<RedoOutlined />}
              size="small"
              onClick={handleRedo}
              disabled={!editMode || !canRedo}
            />
          </Tooltip>
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
                  <div
                    className={`power-grid-edit-actions ${GRID_EDIT_NO_DRAG_CLASS}`}
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
        onWidgetsChange={(w) => saveSettings((prev) => ({ ...prev, widgets: w }))}
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
            const target = editWidgetTarget;
            saveSettings((prev) => ({
              ...prev,
              widgets: prev.widgets.map((w) => (w.id === target.id ? target : w)),
            }));
          }
          setEditWidgetTarget(null);
        }}
        onCancel={() => setEditWidgetTarget(null)}
        allowedDataSources={POWER_DATA_SOURCES}
        allowedWidgetTypes={WIDGET_TYPES}
        widthMin={2}
        heightMin={2}
        heightMax={10}
      />
    </div>
  );
}
