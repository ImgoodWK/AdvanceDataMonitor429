import { useEffect, useState } from 'react';
import { Alert, Button, Space, Switch, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

import { useI18n } from '@/i18n';
import { formatDuration, formatTime } from '@/utils/format';

const { Text } = Typography;

export interface DataFreshnessPanelProps {
  online: boolean;
  lastUpdateTime: number | null;
  lang: 'zh' | 'en';
  autoRefresh: boolean;
  refreshIntervalMs: number;
  pauseRefreshWhenHidden: boolean;
  setPauseRefreshWhenHidden: (v: boolean) => void;
  triggerRefresh: () => void;
}

/**
 * Isolated so the 1Hz freshness tick does not re-render the whole Settings page
 * (theme iframes live in sibling tabs).
 */
export function DataFreshnessPanel({
  online,
  lastUpdateTime,
  lang,
  autoRefresh,
  refreshIntervalMs,
  pauseRefreshWhenHidden,
  setPauseRefreshWhenHidden,
  triggerRefresh,
}: DataFreshnessPanelProps) {
  const { t } = useI18n();
  const [nowTick, setNowTick] = useState(Date.now());

  useEffect(() => {
    const id = setInterval(() => setNowTick(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const now = nowTick;
  const freshness = (() => {
    if (!online) return { level: 'red' as const, label: t('dataFreshness_offline') };
    if (lastUpdateTime == null)
      return { level: 'red' as const, label: t('dataFreshness_never') };
    const diffMs = now - lastUpdateTime;
    if (diffMs < 5000) return { level: 'green' as const, label: t('dataFreshness_fresh') };
    if (diffMs < 30000) return { level: 'yellow' as const, label: t('dataFreshness_stale') };
    return { level: 'red' as const, label: t('dataFreshness_outdated') };
  })();
  const freshnessColor =
    freshness.level === 'green'
      ? 'var(--success)'
      : freshness.level === 'yellow'
        ? 'var(--warning, #faad14)'
        : 'var(--danger)';
  const lastUpdateDiffText =
    lastUpdateTime == null ? t('dataFreshness_never') : formatDuration(now - lastUpdateTime);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Alert type="info" message={t('dataFreshnessHint')} showIcon />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 16px',
          border: '1px solid var(--border-light)',
          borderRadius: 6,
          background: 'var(--bg-secondary)',
        }}
      >
        <span
          aria-hidden="true"
          style={{
            width: 12,
            height: 12,
            borderRadius: '50%',
            background: freshnessColor,
            flexShrink: 0,
            boxShadow: `0 0 6px ${freshnessColor}`,
          }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text strong>{freshness.label}</Text>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: 2 }}>
            <span style={{ marginRight: 12 }}>
              {t('dataFreshnessLastUpdate')}:{' '}
              {lastUpdateTime == null ? '--' : formatTime(lastUpdateTime, lang)}
            </span>
            <span>
              {t('dataFreshnessAge')}: {lastUpdateDiffText}
            </span>
          </div>
        </div>
      </div>
      <Space wrap>
        <Button type="primary" icon={<ReloadOutlined />} onClick={triggerRefresh}>
          {t('dataFreshnessRefreshNow')}
        </Button>
        <Text type="secondary" style={{ fontSize: '0.75rem' }}>
          {t('dataFreshnessAutoRefresh')}: {autoRefresh ? t('on') : t('off')}
          {' · '}
          {t('dataFreshnessInterval')}: {Math.round(refreshIntervalMs / 1000)}s
        </Text>
      </Space>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '10px 12px',
          border: '1px solid var(--border-light)',
          borderRadius: 6,
        }}
      >
        <div>
          <Text strong>{t('pauseRefreshWhenHidden')}</Text>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: 2 }}>
            {t('pauseRefreshWhenHiddenHint')}
          </div>
        </div>
        <Switch
          checked={pauseRefreshWhenHidden}
          onChange={setPauseRefreshWhenHidden}
          aria-label={t('pauseRefreshWhenHidden')}
        />
      </div>
      <div
        style={{
          padding: '8px 12px',
          border: '1px solid var(--border-light)',
          borderRadius: 6,
          fontSize: '0.75rem',
          color: 'var(--text-dim)',
        }}
        aria-live="polite"
      >
        {t('dataFreshnessLegend')}
      </div>
    </Space>
  );
}
