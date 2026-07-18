import { useEffect, useMemo, useRef, type ReactNode } from 'react';
import { GridStack } from 'gridstack';
import { Button, Tooltip, Typography } from 'antd';
import {
  DeleteOutlined,
  PlusOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { WidgetShell } from '@/components/dashboard/WidgetShell';
import {
  applyChildNodePositions,
  widgetLayoutSignature,
} from '@/utils/dashboardTree';
import {
  GRID_DRAG_CANCEL_SELECTOR,
  GRID_EDIT_NO_DRAG_CLASS,
  stopGridDragPointer,
} from '@/utils/gridStackEditGuard';
import { useI18n } from '@/i18n';

const { Text } = Typography;

export interface GroupWidgetProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  editMode: boolean;
  lastUpdateTime?: number | null;
  renderChild: (child: DashboardWidgetConfig) => ReactNode;
  onChildrenChange: (children: DashboardWidgetConfig[]) => void;
  onEditGroup: () => void;
  onEditChild: (child: DashboardWidgetConfig) => void;
  onCopyChild: (child: DashboardWidgetConfig) => void;
  onDeleteChild: (childId: string) => void;
  onAddChild: () => void;
  flushLayoutSave: () => void;
}

/**
 * Nested GridStack host for type === 'group'. Outer item geometry is owned by the parent grid;
 * this component only manages children layout.
 */
export function GroupWidget({
  widget,
  settings,
  editMode,
  lastUpdateTime,
  renderChild,
  onChildrenChange,
  onEditGroup,
  onEditChild,
  onCopyChild,
  onDeleteChild,
  onAddChild,
  flushLayoutSave,
}: GroupWidgetProps) {
  const { t } = useI18n();
  const gridRef = useRef<HTMLDivElement>(null);
  const gridInstanceRef = useRef<GridStack | null>(null);
  const children = widget.children || [];
  const childrenRef = useRef(children);
  childrenRef.current = children;
  const onChildrenChangeRef = useRef(onChildrenChange);
  onChildrenChangeRef.current = onChildrenChange;
  const layoutSig = useMemo(() => widgetLayoutSignature(children), [children]);
  const title = widget.title ? t(widget.title) : t('widgetType_group');

  useEffect(() => {
    if (!gridRef.current) return;

    const grid = GridStack.init(
      {
        column: 'auto',
        cellHeight: 48,
        margin: Math.max(4, Math.floor((settings.widgetGap ?? 12) / 2)),
        staticGrid: !editMode,
        float: true,
        animate: true,
        sizeToContent: false,
        acceptWidgets: false,
        draggable: { cancel: GRID_DRAG_CANCEL_SELECTOR },
      },
      gridRef.current
    );
    gridInstanceRef.current = grid;
    grid.float(false);

    const commitChildren = () => {
      const nodes = grid.engine.nodes;
      if (!nodes.length) return;
      onChildrenChangeRef.current(applyChildNodePositions(childrenRef.current, nodes));
    };
    const onDragOrResizeStop = () => {
      commitChildren();
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
  }, [layoutSig, editMode, settings.widgetGap]);

  return (
    <div className="widget-group">
      <div
        className={`widget-group-header ${GRID_EDIT_NO_DRAG_CLASS}`}
        onMouseDown={stopGridDragPointer}
        onPointerDown={stopGridDragPointer}
      >
        <Text strong className="widget-group-title">
          {title}
        </Text>
        {editMode && (
          <div className={`widget-group-header-actions ${GRID_EDIT_NO_DRAG_CLASS}`}>
            <Tooltip title={t('addWidgetToGroup')}>
              <Button size="small" icon={<PlusOutlined />} onClick={onAddChild} aria-label={t('addWidgetToGroup')} />
            </Tooltip>
            <Tooltip title={t('editWidget')}>
              <Button size="small" icon={<SettingOutlined />} onClick={onEditGroup} aria-label={t('editWidget')} />
            </Tooltip>
          </div>
        )}
      </div>
      <div className="grid-stack widget-group-grid" ref={gridRef}>
        {children.map((child) => (
          <div
            key={child.id}
            className="grid-stack-item"
            gs-id={child.id}
            gs-x={child.x}
            gs-y={child.y}
            gs-w={child.width}
            gs-h={child.height}
            gs-min-w={1}
            gs-min-h={1}
            {...(child.locked ? { 'gs-locked': 'yes' } : {})}
            {...(child.noMove ? { 'gs-no-move': 'yes' } : {})}
            {...(child.noResize ? { 'gs-no-resize': 'yes' } : {})}
            {...(child.sizeToContent ? { 'gs-size-to-content': 'true' } : {})}
          >
            <WidgetShell
              widget={child}
              settings={settings}
              lastUpdateTime={lastUpdateTime}
              editOverlay={
                editMode ? (
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
                        onClick={() => onEditChild(child)}
                        aria-label={t('editWidget')}
                      />
                    </Tooltip>
                    <Tooltip title={t('copyWidget')}>
                      <Button
                        size="small"
                        icon={<PlusOutlined />}
                        onClick={() => onCopyChild(child)}
                        aria-label={t('copyWidget')}
                      />
                    </Tooltip>
                    <Tooltip title={t('deleteWidget')}>
                      <Button
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => onDeleteChild(child.id)}
                        aria-label={t('deleteWidget')}
                      />
                    </Tooltip>
                  </div>
                ) : undefined
              }
            >
              {renderChild(child)}
            </WidgetShell>
          </div>
        ))}
      </div>
      {children.length === 0 && (
        <div className="widget-group-empty">
          <Text type="secondary">{t('widgetGroupEmpty')}</Text>
        </div>
      )}
    </div>
  );
}
