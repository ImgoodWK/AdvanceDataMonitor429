import { Empty, Tag, Typography } from 'antd';
import type { DashboardWidgetConfig } from '@/utils/presets';
import type { StorageCpu, WebAlertDto } from '@/types/dto';
import { useI18n } from '@/i18n';
import { formatDuration } from '@/utils/format';

const { Text } = Typography;

interface TextNoteProps {
  widget: DashboardWidgetConfig;
  titleColor?: string;
}

export function TextNoteWidget({ widget, titleColor }: TextNoteProps) {
  const { t } = useI18n();
  const label = widget.title ? t(widget.title) : t('widgetType_textNote');
  return (
    <div className="dashboard-widget-inner widget-text-note">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {label}
      </div>
      <pre className="widget-text-note-body">
        {widget.noteText?.trim() ? widget.noteText : t('widgetTextNoteEmpty')}
      </pre>
    </div>
  );
}

export function SpacerWidget({ widget }: { widget: DashboardWidgetConfig }) {
  const { t } = useI18n();
  return (
    <div className="dashboard-widget-inner widget-spacer" aria-hidden>
      <div className="widget-spacer-line" />
      {widget.title ? (
        <span className="widget-spacer-label">{t(widget.title)}</span>
      ) : null}
      <div className="widget-spacer-line" />
    </div>
  );
}

interface AlertsSummaryProps {
  widget: DashboardWidgetConfig;
  alerts: WebAlertDto[];
  loading?: boolean;
  titleColor?: string;
}

export function AlertsSummaryWidget({ widget, alerts, loading, titleColor }: AlertsSummaryProps) {
  const { t } = useI18n();
  const label = widget.title ? t(widget.title) : t('widgetType_alertsSummary');
  const maxRows = widget.maxRows ?? 5;
  const rows = alerts.slice(0, maxRows);

  return (
    <div className="dashboard-widget-inner widget-alerts-summary">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {label}
        <Tag color={alerts.length > 0 ? 'volcano' : 'default'} style={{ marginLeft: 8 }}>
          {alerts.length}
        </Tag>
      </div>
      {loading && alerts.length === 0 ? (
        <Text type="secondary">{t('loading')}</Text>
      ) : rows.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('widgetAlertsEmpty')} />
      ) : (
        <ul className="widget-feed-list">
          {rows.map((a) => (
            <li key={a.id || `${a.type}:${a.sourceKey}:${a.timestamp}`}>
              <Tag color={a.severity === 'critical' || a.severity === 'error' ? 'red' : 'orange'}>
                {a.severity || a.type}
              </Tag>
              <span className="widget-feed-title">{a.title || a.message}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface CraftingQueueProps {
  widget: DashboardWidgetConfig;
  cpus: StorageCpu[] | undefined;
  titleColor?: string;
  formatNumber: (n: number) => string;
}

export function CraftingQueueWidget({
  widget,
  cpus,
  titleColor,
  formatNumber,
}: CraftingQueueProps) {
  const { t } = useI18n();
  const label = widget.title ? t(widget.title) : t('widgetType_craftingQueue');
  const maxRows = widget.maxRows ?? 8;
  const busy = (cpus || []).filter((c) => c.isBusy).slice(0, maxRows);

  return (
    <div className="dashboard-widget-inner widget-crafting-queue">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {label}
        <Tag color={busy.length > 0 ? 'processing' : 'default'} style={{ marginLeft: 8 }}>
          {busy.length}
        </Tag>
      </div>
      {busy.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('widgetCraftingIdle')} />
      ) : (
        <ul className="widget-feed-list">
          {busy.map((cpu) => (
            <li key={cpu.name}>
              <span className="widget-feed-title">{cpu.name}</span>
              <span className="widget-feed-meta">
                {cpu.finalOutputName
                  ? `${cpu.finalOutputName} × ${formatNumber(cpu.finalOutputAmount || 0)}`
                  : t('busy')}
                {cpu.elapsedTime > 0 ? ` · ${formatDuration(cpu.elapsedTime)}` : ''}
                {cpu.craftingProgress > 0
                  ? ` · ${Math.round(cpu.craftingProgress * 100)}%`
                  : ''}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
