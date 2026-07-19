import { useEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { applyWidgetShellStyle } from '@/utils/dashboardResolve';
import { effectiveContentScale } from '@/utils/dashboardColumns';
import { useI18n } from '@/i18n';

interface WidgetShellProps {
  widget: DashboardWidgetConfig;
  settings: DashboardSettings;
  className?: string;
  children: ReactNode;
  /** Edit-mode overlay (delete/settings buttons). */
  editOverlay?: ReactNode;
  /** Snapshot refresh timestamp (ms); used when showLastUpdated is enabled. */
  lastUpdateTime?: number | null;
}

/**
 * Applies resolved shell colors/border/inset to `.grid-stack-item-content` via CSS variables.
 */
export function WidgetShell({
  widget,
  settings,
  className,
  children,
  editOverlay,
  lastUpdateTime,
}: WidgetShellProps) {
  const { t } = useI18n();
  const shellStyle = applyWidgetShellStyle(widget, settings);
  const bodyRef = useRef<HTMLDivElement>(null);
  const [cellSize, setCellSize] = useState({ w: 240, h: 128 });
  const showFooter = settings.showLastUpdated && lastUpdateTime != null;
  const [, tick] = useState(0);

  useEffect(() => {
    if (!showFooter) return;
    const id = window.setInterval(() => tick((n) => n + 1), 1000);
    return () => window.clearInterval(id);
  }, [showFooter, lastUpdateTime]);

  useEffect(() => {
    const el = bodyRef.current?.parentElement;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver((entries) => {
      const cr = entries[0]?.contentRect;
      if (!cr) return;
      setCellSize((prev) =>
        prev.w === cr.width && prev.h === cr.height ? prev : { w: cr.width, h: cr.height }
      );
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const scale = useMemo(
    () => effectiveContentScale(widget, cellSize.w, cellSize.h),
    [widget, cellSize.w, cellSize.h]
  );

  const footerText =
    lastUpdateTime != null
      ? t('widgetUpdatedAgo', Math.max(0, Math.floor((Date.now() - lastUpdateTime) / 1000)))
      : '';

  return (
    <div
      className={`grid-stack-item-content widget-shell${className ? ' ' + className : ''}`}
      style={
        {
          ...shellStyle,
          '--widget-content-scale': String(scale),
          fontSize: `calc(1em * ${scale})`,
        } as CSSProperties
      }
    >
      <div className="widget-shell-body" ref={bodyRef} style={{ transformOrigin: 'top left' }}>
        {children}
      </div>
      {showFooter && (
        <div className="widget-shell-footer" aria-live="polite">
          {footerText}
        </div>
      )}
      {editOverlay}
    </div>
  );
}
