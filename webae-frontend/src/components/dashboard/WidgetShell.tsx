import { useEffect, useState } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import type { DashboardSettings, DashboardWidgetConfig } from '@/utils/presets';
import { applyWidgetShellStyle } from '@/utils/dashboardResolve';
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
  const showFooter = settings.showLastUpdated && lastUpdateTime != null;
  const [, tick] = useState(0);

  useEffect(() => {
    if (!showFooter) return;
    const id = window.setInterval(() => tick((n) => n + 1), 1000);
    return () => window.clearInterval(id);
  }, [showFooter, lastUpdateTime]);

  const footerText =
    lastUpdateTime != null
      ? t('widgetUpdatedAgo', Math.max(0, Math.floor((Date.now() - lastUpdateTime) / 1000)))
      : '';

  return (
    <div
      className={`grid-stack-item-content widget-shell${className ? ' ' + className : ''}`}
      style={shellStyle as CSSProperties}
    >
      <div className="widget-shell-body">{children}</div>
      {showFooter && (
        <div className="widget-shell-footer" aria-live="polite">
          {footerText}
        </div>
      )}
      {editOverlay}
    </div>
  );
}
