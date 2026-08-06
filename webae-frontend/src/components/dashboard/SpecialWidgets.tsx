import { Empty, Tag, Typography } from 'antd';
import type { CSSProperties } from 'react';
import type { DashboardWidgetConfig } from '@/utils/presets';
import type {
  GtMachineDto,
  PlayerDto,
  PowerDto,
  ServerHealthResponse,
  StorageCpu,
  StorageDto,
  WebAlertDto,
} from '@/types/dto';
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

function clamp(value: number, min = 0, max = 100) {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));
}

function percent(value: number, max: number) {
  return max > 0 ? clamp((value / max) * 100) : 0;
}

function widgetLabel(widget: DashboardWidgetConfig, fallbackKey: string, t: (key: string) => string) {
  return widget.title ? t(widget.title) : t(fallbackKey);
}

interface NetworkHealthProps {
  widget: DashboardWidgetConfig;
  storage?: StorageDto | null;
  power?: PowerDto | null;
  gtMachines?: GtMachineDto[] | null;
  alerts: WebAlertDto[];
  health?: ServerHealthResponse | null;
  titleColor?: string;
}

export function NetworkHealthWidget({
  widget,
  storage,
  power,
  gtMachines,
  alerts,
  health,
  titleColor,
}: NetworkHealthProps) {
  const { t } = useI18n();
  const storageScore = storage?.bytesMax ? 100 - percent(storage.bytesUsed, storage.bytesMax) : null;
  const powerScore = power?.euMax ? percent(power.euStored, power.euMax) : null;
  const cpuTotal = storage?.cpus?.length || 0;
  const cpuBusy = storage?.cpus?.filter((cpu) => cpu.isBusy).length || 0;
  const cpuScore = cpuTotal > 0 ? 100 - percent(cpuBusy, cpuTotal) : null;
  const machineTotal = gtMachines?.length || 0;
  const machineFaults = gtMachines?.filter((machine) => machine.errorId !== 0 || machine.problemId !== 0).length || 0;
  const machineScore = machineTotal > 0 ? 100 - percent(machineFaults, machineTotal) : null;
  const serverScore = health
    ? clamp(Math.min((health.tps / 20) * 100, health.mspt > 0 ? (50 / health.mspt) * 100 : 100))
    : null;
  const metrics = [
    { key: 'storage', label: t('widgetHealthStorage'), value: storageScore },
    { key: 'power', label: t('widgetHealthPower'), value: powerScore },
    { key: 'cpu', label: t('widgetHealthCrafting'), value: cpuScore },
    { key: 'machines', label: t('widgetHealthMachines'), value: machineScore },
    { key: 'server', label: t('widgetHealthServer'), value: serverScore },
  ].filter((metric): metric is { key: string; label: string; value: number } => metric.value != null);
  const alertPenalty = alerts.reduce((sum, alert) => {
    if (alert.severity === 'critical' || alert.severity === 'error') return sum + 10;
    if (alert.severity === 'warning') return sum + 4;
    return sum + 1;
  }, 0);
  const base = metrics.length > 0
    ? metrics.reduce((sum, metric) => sum + metric.value, 0) / metrics.length
    : 0;
  const score = Math.round(clamp(base - alertPenalty));
  const stateKey = score >= 85
    ? 'widgetHealthExcellent'
    : score >= 65
      ? 'widgetHealthStable'
      : score >= 40
        ? 'widgetHealthStrained'
        : 'widgetHealthCritical';

  return (
    <div className="dashboard-widget-inner widget-network-health">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_networkHealth', t)}
      </div>
      {metrics.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('widgetNoOperationalData')} />
      ) : (
        <div className="widget-health-layout">
          <div className="widget-health-core" style={{ '--health-score': `${score * 3.6}deg` } as CSSProperties}>
            <strong>{score}</strong>
            <small>{t(stateKey)}</small>
          </div>
          <div className="widget-health-metrics">
            {metrics.map((metric) => (
              <div className="widget-health-metric" key={metric.key}>
                <span>{metric.label}</span>
                <i><b style={{ width: `${clamp(metric.value)}%` }} /></i>
                <em>{Math.round(metric.value)}</em>
              </div>
            ))}
            <div className="widget-health-alerts">
              <span>{t('widgetHealthAlerts')}</span>
              <Tag color={alerts.length > 0 ? 'volcano' : 'success'}>{alerts.length}</Tag>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface PowerFlowProps {
  widget: DashboardWidgetConfig;
  power?: PowerDto | null;
  titleColor?: string;
  formatNumber: (n: number) => string;
}

export function PowerFlowWidget({ widget, power, titleColor, formatNumber }: PowerFlowProps) {
  const { t } = useI18n();
  const net = (power?.euInRate || 0) - (power?.euOutRate || 0);
  const charge = power ? percent(power.euStored, power.euMax) : 0;
  return (
    <div className="dashboard-widget-inner widget-power-flow" data-flow={net >= 0 ? 'positive' : 'negative'}>
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_powerFlow', t)}
      </div>
      {!power ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noPowerData')} />
      ) : (
        <>
          <div className="widget-power-flow__rail">
            <div><small>{t('euInRate')}</small><strong>+{formatNumber(power.euInRate)}</strong></div>
            <div className="widget-power-flow__core" style={{ '--charge': `${charge * 3.6}deg` } as CSSProperties}>
              <strong>{Math.round(charge)}%</strong>
              <small>EU</small>
            </div>
            <div><small>{t('euOutRate')}</small><strong>-{formatNumber(power.euOutRate)}</strong></div>
          </div>
          <div className="widget-power-flow__beam"><i /></div>
          <div className="widget-power-flow__footer">
            <span>{t('widgetPowerNet')}</span>
            <strong>{net >= 0 ? '+' : ''}{formatNumber(net)} EU/t</strong>
            <span>
              {t('steam')}: {formatNumber(power.steamStored)}
              {power.steamCapacityKnown ? ` / ${formatNumber(power.steamMax)}` : ''}
            </span>
          </div>
        </>
      )}
    </div>
  );
}

interface StorageMatrixProps {
  widget: DashboardWidgetConfig;
  storage?: StorageDto | null;
  titleColor?: string;
  formatNumber: (n: number) => string;
}

export function StorageMatrixWidget({ widget, storage, titleColor, formatNumber }: StorageMatrixProps) {
  const { t } = useI18n();
  const usage = storage ? percent(storage.bytesUsed, storage.bytesMax) : 0;
  const cells = storage ? [
    { key: 'items', label: t('items'), count: storage.items?.length || 0, amount: storage.items?.reduce((sum, item) => sum + item.amount, 0) || 0 },
    { key: 'fluids', label: t('fluids'), count: storage.fluids?.length || 0, amount: storage.fluids?.reduce((sum, fluid) => sum + fluid.amount, 0) || 0 },
    { key: 'essentia', label: t('essentia'), count: storage.essentia?.length || 0, amount: storage.essentia?.reduce((sum, aspect) => sum + aspect.amount, 0) || 0 },
    { key: 'cpus', label: t('cpus'), count: storage.cpus?.length || 0, amount: storage.cpus?.filter((cpu) => cpu.isBusy).length || 0 },
  ] : [];
  return (
    <div className="dashboard-widget-inner widget-storage-matrix">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_storageMatrix', t)}
      </div>
      {!storage ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('clickRefresh')} />
      ) : (
        <>
          <div className="widget-storage-matrix__capacity">
            <span>{t('storageUsage')}</span>
            <i><b style={{ width: `${usage}%` }} /></i>
            <strong>{Math.round(usage)}%</strong>
          </div>
          <div className="widget-storage-matrix__grid">
            {cells.map((cell) => (
              <div key={cell.key} data-cell={cell.key}>
                <small>{cell.label}</small>
                <strong>{formatNumber(cell.count)}</strong>
                <span>{formatNumber(cell.amount)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

interface MachineFleetProps {
  widget: DashboardWidgetConfig;
  machines?: GtMachineDto[] | null;
  titleColor?: string;
  formatNumber: (n: number) => string;
}

export function MachineFleetWidget({ widget, machines, titleColor, formatNumber }: MachineFleetProps) {
  const { t } = useI18n();
  const list = machines || [];
  const faults = list.filter((machine) => machine.errorId !== 0 || machine.problemId !== 0);
  const active = list.filter((machine) => machine.isActive && machine.errorId === 0 && machine.problemId === 0);
  const idle = Math.max(0, list.length - active.length - faults.length);
  const avgProgress = active.length > 0
    ? active.reduce((sum, machine) => sum + clamp(machine.progressPercent), 0) / active.length
    : 0;
  const maxRows = widget.maxRows ?? 4;
  return (
    <div className="dashboard-widget-inner widget-machine-fleet">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_machineFleet', t)}
      </div>
      {list.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noGTData')} />
      ) : (
        <>
          <div className="widget-machine-fleet__stats">
            <span data-state="active"><strong>{formatNumber(active.length)}</strong><small>{t('active')}</small></span>
            <span data-state="idle"><strong>{formatNumber(idle)}</strong><small>{t('idle')}</small></span>
            <span data-state="fault"><strong>{formatNumber(faults.length)}</strong><small>{t('widgetMachineFaults')}</small></span>
            <span data-state="progress"><strong>{Math.round(avgProgress)}%</strong><small>{t('progress')}</small></span>
          </div>
          <ul className="widget-machine-fleet__list">
            {active.slice(0, maxRows).map((machine) => (
              <li key={`${machine.dim}:${machine.x}:${machine.y}:${machine.z}`}>
                <i style={{ '--machine-progress': `${clamp(machine.progressPercent)}%` } as CSSProperties} />
                <span>{machine.currentOutput || machine.recipeMapName || machine.statusText || t('active')}</span>
                <em>{Math.round(machine.progressPercent)}%</em>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

interface PlayerPresenceProps {
  widget: DashboardWidgetConfig;
  players: PlayerDto[];
  loading?: boolean;
  titleColor?: string;
}

export function PlayerPresenceWidget({ widget, players, loading, titleColor }: PlayerPresenceProps) {
  const { t } = useI18n();
  const online = players.filter((player) => player.online);
  const maxRows = widget.maxRows ?? 8;
  return (
    <div className="dashboard-widget-inner widget-player-presence">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_playerPresence', t)}
        <Tag color={online.length > 0 ? 'success' : 'default'} style={{ marginLeft: 8 }}>{online.length}</Tag>
      </div>
      {loading && players.length === 0 ? (
        <Text type="secondary">{t('loading')}</Text>
      ) : online.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('chatNoPlayers')} />
      ) : (
        <div className="widget-player-presence__grid">
          {online.slice(0, maxRows).map((player) => (
            <div key={player.uuid}>
              <span>{player.name.slice(0, 2).toUpperCase()}</span>
              <strong>{player.name}</strong>
              <small>{formatDuration(player.onlineMs)}</small>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

interface ActivityStreamProps {
  widget: DashboardWidgetConfig;
  alerts: WebAlertDto[];
  cpus?: StorageCpu[];
  titleColor?: string;
  formatNumber: (n: number) => string;
}

export function ActivityStreamWidget({ widget, alerts, cpus, titleColor, formatNumber }: ActivityStreamProps) {
  const { t } = useI18n();
  const maxRows = widget.maxRows ?? 8;
  const rows = [
    ...alerts.map((alert) => ({
      id: `alert:${alert.id}`,
      kind: 'alert',
      title: alert.title || alert.message,
      meta: alert.severity || alert.type,
      severity: alert.severity,
    })),
    ...(cpus || []).filter((cpu) => cpu.isBusy).map((cpu) => ({
      id: `cpu:${cpu.name}`,
      kind: 'craft',
      title: cpu.finalOutputName || cpu.name,
      meta: cpu.finalOutputAmount > 0
        ? `${formatNumber(cpu.finalOutputAmount)} · ${Math.round(cpu.craftingProgress * 100)}%`
        : `${Math.round(cpu.craftingProgress * 100)}%`,
      severity: 'processing',
    })),
  ].slice(0, maxRows);
  return (
    <div className="dashboard-widget-inner widget-activity-stream">
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_activityStream', t)}
      </div>
      {rows.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('widgetActivityEmpty')} />
      ) : (
        <ol>
          {rows.map((row, index) => (
            <li key={row.id} data-kind={row.kind}>
              <i>{String(index + 1).padStart(2, '0')}</i>
              <span><strong>{row.title}</strong><small>{row.meta}</small></span>
              <Tag color={row.kind === 'craft' ? 'processing' : row.severity === 'critical' || row.severity === 'error' ? 'red' : 'orange'}>
                {row.kind === 'craft' ? t('crafting') : t('alertsHistoryPage')}
              </Tag>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function sparkPath(values: number[], min: number, max: number) {
  if (values.length < 2) return '';
  const range = Math.max(0.0001, max - min);
  return values.map((value, index) => {
    const x = (index / (values.length - 1)) * 100;
    const y = 28 - ((clamp(value, min, max) - min) / range) * 24;
    return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
  }).join(' ');
}

interface ServerVitalsProps {
  widget: DashboardWidgetConfig;
  health?: ServerHealthResponse | null;
  loading?: boolean;
  titleColor?: string;
}

export function ServerVitalsWidget({ widget, health, loading, titleColor }: ServerVitalsProps) {
  const { t } = useI18n();
  const tpsPath = sparkPath(health?.history?.tps || [], 0, 20);
  const msptPath = sparkPath(health?.history?.mspt || [], 0, 100);
  const state = !health ? 'offline' : health.tps >= 19 && health.mspt <= 45 ? 'good' : health.tps >= 15 && health.mspt <= 65 ? 'warn' : 'bad';
  return (
    <div className="dashboard-widget-inner widget-server-vitals" data-state={state}>
      <div className="stat-card-label" style={titleColor ? { color: titleColor } : undefined}>
        {widgetLabel(widget, 'widgetType_serverVitals', t)}
      </div>
      {loading && !health ? (
        <Text type="secondary">{t('loading')}</Text>
      ) : !health ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('offline')} />
      ) : (
        <>
          <div className="widget-server-vitals__stats">
            <span><small>TPS</small><strong>{health.tps.toFixed(1)}</strong></span>
            <span><small>MSPT</small><strong>{health.mspt.toFixed(1)}</strong></span>
            <span><small>{t('chatOnline')}</small><strong>{health.onlinePlayers}</strong></span>
            <span><small>{t('adminPanelUptime')}</small><strong>{formatDuration(health.uptimeSeconds * 1000)}</strong></span>
          </div>
          <svg className="widget-server-vitals__pulse" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden>
            <path className="vitals-grid" d="M0 8H100 M0 16H100 M0 24H100" />
            {tpsPath && <path className="vitals-tps" d={tpsPath} />}
            {msptPath && <path className="vitals-mspt" d={msptPath} />}
          </svg>
          <div className="widget-server-vitals__legend"><span>TPS</span><span>MSPT</span><em>{t(`widgetVitals_${state}`)}</em></div>
        </>
      )}
    </div>
  );
}
