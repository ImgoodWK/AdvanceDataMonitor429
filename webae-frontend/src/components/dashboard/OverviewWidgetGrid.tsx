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
  AppstoreOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useNumberFormat } from '@/hooks/useNumberFormat';
import { useNetworkMetrics } from '@/hooks/useNetworkMetrics';
import { useDebouncedLocalStorageSaver } from '@/hooks/useDebouncedLocalStorageSaver';
import { useEditorHistory, useUndoRedoHotkeys } from '@/hooks/useEditorHistory';
import {
  overviewAsDashboardSettings,
  loadOverviewSettingsFromStorage,
  type DashboardSettings,
  type DashboardWidgetConfig,
  type StorageOverviewSettings,
} from '@/utils/presets';
import { DashboardSettingsDrawer } from '@/components/dashboard/DashboardSettingsDrawer';
import { EditWidgetModal } from '@/components/dashboard/EditWidgetModal';
import { WidgetContent } from '@/components/dashboard/WidgetContent';
import { WidgetShell } from '@/components/dashboard/WidgetShell';
import { DataPageSection } from '@/components/Layout/DataPageSection';
import type { OverviewSnapshot } from '@/utils/overviewDataSources';
import { copyWidgetConfig } from '@/utils/widgetGridActions';
import { createWidgetId } from '@/utils/widgetId';
import { widgetRemountSignature } from '@/utils/dashboardTree';
import {
  GRID_DRAG_CANCEL_SELECTOR,
  GRID_EDIT_NO_DRAG_CLASS,
  stopGridDragPointer,
  syncWidgetGeometryToGrid,
  observeGridViewport,
  scheduleGridLayoutCommit,
  cancelGridLayoutCommit,
  type GridWidgetGeometry,
} from '@/utils/gridStackEditGuard';

/** Overview cards are fixed to 2 rows in GridStack; keep editor in sync. */
export const OVERVIEW_WIDGET_HEIGHT = 2;
export const OVERVIEW_WIDGET_MIN_WIDTH = 2;

const WIDGET_TYPES: DashboardWidgetConfig['type'][] = [
  'statCard',
  'progressBar',
  'gauge',
  'lineChart',
];

interface OverviewWidgetGridProps {
  storageKey: string;
  defaultSettings: StorageOverviewSettings;
  snapshot: OverviewSnapshot | null;
  dataSources: string[];
  settingsTitleKey?: string;
  sectionTitleKey?: string;
  sectionDescriptionKey?: string;
  gridClassName?: string;
  disabled?: boolean;
}

export function OverviewWidgetGrid({
  storageKey,
  defaultSettings,
  snapshot,
  dataSources,
  settingsTitleKey = 'storageOverviewSettings',
  sectionTitleKey = 'customOverviewTitle',
  sectionDescriptionKey = 'customOverviewDesc',
  gridClassName = 'overview-widget-grid',
  disabled = false,
}: OverviewWidgetGridProps) {
  const { notify, selectedNetworks, lastUpdateTime, browsingMode } = useAppContext();
  const { t } = useI18n();
  const fmtNum = useNumberFormat();
  const networkMetrics = useNetworkMetrics();
  const primaryNetworkId = selectedNetworks[0] ?? 0;
  const [editMode, setEditMode] = useState(false);
  const effectiveEditMode = editMode && !browsingMode && !disabled;
  const {
    present: settings,
    commit: commitSettings,
    undo: undoSettings,
    redo: redoSettings,
    canUndo,
    canRedo,
  } = useEditorHistory<StorageOverviewSettings>(() =>
    loadOverviewSettingsFromStorage(storageKey, defaultSettings)
  );
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [addWidgetOpen, setAddWidgetOpen] = useState(false);
  const [editWidgetTarget, setEditWidgetTarget] = useState<DashboardWidgetConfig | null>(null);
  const [newWidget, setNewWidget] = useState<Partial<DashboardWidgetConfig>>({
    type: 'statCard',
    dataSource: dataSources[0] || 'itemCount',
    scope: 'perNetwork',
    title: '',
    width: 3,
    height: OVERVIEW_WIDGET_HEIGHT,
  });

  const gridRef = useRef<HTMLDivElement>(null);
  const gridInstanceRef = useRef<GridStack | null>(null);
  const pendingGridCommitRef = useRef<number | null>(null);
  const { schedule: scheduleLayoutSave, flush: flushLayoutSave, cancel: cancelLayoutSave } =
    useDebouncedLocalStorageSaver<StorageOverviewSettings>(storageKey);

  const persistSettings = useCallback(
    (s: StorageOverviewSettings) => {
      try {
        localStorage.setItem(storageKey, JSON.stringify(s));
      } catch {
        /* ignore */
      }
    },
    [storageKey]
  );

  const saveSettings = useCallback(
    (s: StorageOverviewSettings | ((prev: StorageOverviewSettings) => StorageOverviewSettings)) => {
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

  useUndoRedoHotkeys(handleUndo, handleRedo, effectiveEditMode);

  useEffect(() => {
    if (!browsingMode) return;
    setEditMode(false);
    setSettingsOpen(false);
    setAddWidgetOpen(false);
    setEditWidgetTarget(null);
  }, [browsingMode]);

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
      id: createWidgetId('w-'),
      type: (newWidget.type || 'statCard') as DashboardWidgetConfig['type'],
      dataSource: newWidget.dataSource || dataSources[0],
      scope: 'perNetwork',
      title: newWidget.title || '',
      width: newWidget.width || 3,
      height: OVERVIEW_WIDGET_HEIGHT,
      x: 0,
      y: 0,
    };
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, widget] }));
    setAddWidgetOpen(false);
    setNewWidget({
      type: 'statCard',
      dataSource: dataSources[0],
      scope: 'perNetwork',
      title: '',
      width: 3,
      height: OVERVIEW_WIDGET_HEIGHT,
    });
  };

  const handleDeleteWidget = (id: string) => {
    saveSettings((prev) => ({ ...prev, widgets: prev.widgets.filter((w) => w.id !== id) }));
  };

  const handleCopyWidget = (widget: DashboardWidgetConfig) => {
    const copy = copyWidgetConfig(widget, 'w-');
    copy.height = OVERVIEW_WIDGET_HEIGHT;
    saveSettings((prev) => ({ ...prev, widgets: [...prev.widgets, copy] }));
    notify(t('widgetCopied'), 'success');
  };

  const handleResetLayout = () => {
    saveSettings(defaultSettings);
    notify(t('layoutReset'), 'info');
  };

  const commitLayout = useCallback((nodes: GridWidgetGeometry[]) => {
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
          height: OVERVIEW_WIDGET_HEIGHT,
        };
      });
      const next = { ...prev, widgets: nextWidgets };
      scheduleLayoutSave(next);
      return next;
    });
  }, [cancelLayoutSave, commitSettings, scheduleLayoutSave]);

  const scheduleLayoutCommit = useCallback(() => {
    scheduleGridLayoutCommit(gridInstanceRef.current, pendingGridCommitRef, commitLayout);
  }, [commitLayout]);

  const handleAutoArrange = useCallback(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      grid.compact('compact');
      scheduleLayoutCommit();
      notify(t('autoArrangeDone'), 'success');
    } catch (e) {
      notify((e as Error).message, 'error');
    }
  }, [notify, t, scheduleLayoutCommit]);

  const remountSignature = useMemo(() => widgetRemountSignature(widgets), [widgets]);

  useEffect(() => {
    if (!gridRef.current || disabled) return;

    const grid = GridStack.init(
      {
        column: 12,
        cellHeight: 56,
        margin: settings.widgetGap ?? 12,
        staticGrid: !effectiveEditMode,
        float: true,
        animate: true,
        draggable: { cancel: GRID_DRAG_CANCEL_SELECTOR },
      },
      gridRef.current
    );
    gridInstanceRef.current = grid;
    grid.float(false);
    const stopViewportObserver = observeGridViewport(grid, 56, 38);

    const onDragOrResizeStop = () => {
      scheduleLayoutCommit();
    };
    grid.on('dragstop', onDragOrResizeStop);
    grid.on('resizestop', onDragOrResizeStop);

    return () => {
      stopViewportObserver();
      cancelGridLayoutCommit(pendingGridCommitRef);
      flushLayoutSave();
      grid.offAll();
      if (gridInstanceRef.current === grid) gridInstanceRef.current = null;
      try {
        grid.destroy(false);
      } catch {
        // StrictMode/navigation may already have released the instance.
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remountSignature, disabled, storageKey]);

  useEffect(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      grid.setStatic(!effectiveEditMode);
    } catch {
      /* grid is changing pages */
    }
  }, [effectiveEditMode, remountSignature]);

  useEffect(() => {
    const grid = gridInstanceRef.current;
    if (!grid) return;
    try {
      grid.margin(settings.widgetGap ?? 12);
    } catch {
      /* grid is changing pages */
    }
  }, [settings.widgetGap, remountSignature]);

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
    <DataPageSection
      title={t(sectionTitleKey)}
      description={t(sectionDescriptionKey)}
      eyebrow={t('customOverviewEyebrow')}
      icon={<AppstoreOutlined />}
      variant="overview"
      className={`overview-grid-section ${effectiveEditMode ? 'data-page-section--editing' : ''}`}
      actions={!browsingMode ? (
        <Space wrap>
          <Tooltip title={t('dashCfgOpenHint')}>
            <Button
              icon={<ControlOutlined />}
              onClick={() => setSettingsOpen(true)}
              size="small"
              aria-label={t(settingsTitleKey)}
            >
              {t(settingsTitleKey)}
            </Button>
          </Tooltip>
          <Button
            type={effectiveEditMode ? 'primary' : 'default'}
            icon={<EditOutlined />}
            size="small"
            onClick={() => setEditMode(!effectiveEditMode)}
            aria-pressed={effectiveEditMode}
          >
            {effectiveEditMode ? t('done') : t('editOverview')}
          </Button>
        </Space>
      ) : undefined}
    >
      {effectiveEditMode && (
        <div className="dashboard-editor-ribbon" role="status">
          <div className="dashboard-editor-ribbon__copy">
            <span className="dashboard-editor-ribbon__pulse" aria-hidden="true" />
            <div>
              <strong>{t('overviewEditingTitle')}</strong>
              <span>{t('overviewEditingDesc')}</span>
            </div>
          </div>
          <Space wrap size={[6, 6]} className="dashboard-editor-ribbon__actions">
            <Tooltip title={t('editorUndoHint')}>
              <Button
                icon={<UndoOutlined />}
                size="small"
                onClick={handleUndo}
                disabled={!canUndo}
                aria-label={t('editorUndo')}
              />
            </Tooltip>
            <Tooltip title={t('editorRedoHint')}>
              <Button
                icon={<RedoOutlined />}
                size="small"
                onClick={handleRedo}
                disabled={!canRedo}
                aria-label={t('editorRedo')}
              />
            </Tooltip>
            <Button icon={<PlusOutlined />} size="small" onClick={() => setAddWidgetOpen(true)}>
              {t('addWidget')}
            </Button>
            <Tooltip title={t('autoArrangeHint')}>
              <Button icon={<AlignLeftOutlined />} size="small" onClick={handleAutoArrange}>
                {t('autoArrange')}
              </Button>
            </Tooltip>
            <Button icon={<ReloadOutlined />} size="small" onClick={handleResetLayout}>
              {t('resetLayout')}
            </Button>
          </Space>
        </div>
      )}

      <div
        className={`grid-stack ${gridClassName}`}
        ref={gridRef}
        style={{ padding: settings.margin }}
      >
        {widgets.map((widget) => (
          <div
            key={widget.id}
            className="grid-stack-item overview-grid-item"
            gs-id={widget.id}
            gs-x={widget.x}
            gs-y={widget.y}
            gs-w={widget.width}
            gs-h={OVERVIEW_WIDGET_HEIGHT}
            gs-min-w={OVERVIEW_WIDGET_MIN_WIDTH}
            gs-min-h={OVERVIEW_WIDGET_HEIGHT}
            gs-max-h={OVERVIEW_WIDGET_HEIGHT}
          >
            <WidgetShell
              widget={widget}
              settings={dashboardSettings}
              className="overview-grid-item-content"
              lastUpdateTime={lastUpdateTime}
              editOverlay={
                effectiveEditMode ? (
                  <div
                    className={`overview-grid-edit-actions ${GRID_EDIT_NO_DRAG_CLASS}`}
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
              <WidgetContent
                widget={widget}
                settings={dashboardSettings}
                snapshot={snapshot}
                t={t}
                fmtNum={fmtNum}
                dataSourceLabel={dataSourceLabel}
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
            options={dataSources.map((ds) => ({ label: dataSourceLabel(ds), value: ds }))}
          />
          <Space>
            <span>{t('width')}:</span>
            <InputNumber
              min={OVERVIEW_WIDGET_MIN_WIDTH}
              max={12}
              value={newWidget.width}
              onChange={(v) => setNewWidget({ ...newWidget, width: v || 3 })}
            />
            <span>{t('height')}:</span>
            <InputNumber
              min={OVERVIEW_WIDGET_HEIGHT}
              max={OVERVIEW_WIDGET_HEIGHT}
              value={OVERVIEW_WIDGET_HEIGHT}
              disabled
            />
          </Space>
        </Space>
      </Modal>

      <EditWidgetModal
        open={!!editWidgetTarget}
        widget={editWidgetTarget}
        settings={dashboardSettings}
        onWidgetChange={(w) => setEditWidgetTarget({ ...w, height: OVERVIEW_WIDGET_HEIGHT })}
        onOk={() => {
          if (editWidgetTarget) {
            const target = { ...editWidgetTarget, height: OVERVIEW_WIDGET_HEIGHT };
            saveSettings((prev) => ({
              ...prev,
              widgets: prev.widgets.map((w) => (w.id === target.id ? target : w)),
            }));
            window.requestAnimationFrame(() => {
              if (syncWidgetGeometryToGrid(target)) scheduleLayoutCommit();
            });
          }
          setEditWidgetTarget(null);
        }}
        onCancel={() => setEditWidgetTarget(null)}
        allowedDataSources={dataSources}
        allowedWidgetTypes={WIDGET_TYPES}
        widthMin={OVERVIEW_WIDGET_MIN_WIDTH}
        heightMin={OVERVIEW_WIDGET_HEIGHT}
        heightMax={OVERVIEW_WIDGET_HEIGHT}
      />
    </DataPageSection>
  );
}
