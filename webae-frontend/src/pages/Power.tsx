import { useEffect, useMemo, useState } from 'react';
import { Card, Empty, Tabs, Spin } from 'antd';
import { ApartmentOutlined } from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { useSnapshotData } from '@/hooks/useSnapshotData';
import { PageShell } from '@/components/Layout/PageShell';
import { PowerWidgetGrid } from '@/components/dashboard/PowerWidgetGrid';
import { powerDtoToSnapshot } from '@/utils/powerDataSources';
import { resolveActiveNetworkId } from '@/utils/networkSelection';

export function PowerPage() {
  const { selectedNetworks } = useAppContext();
  const { t } = useI18n();
  const { powerMap, initialLoading, refreshing } = useSnapshotData();
  const [activeNetwork, setActiveNetwork] = useState<number | null>(null);

  const currentNet = resolveActiveNetworkId(selectedNetworks, activeNetwork);
  const data = powerMap[currentNet];

  useEffect(() => {
    if (activeNetwork != null && !selectedNetworks.includes(activeNetwork)) {
      setActiveNetwork(null);
    }
  }, [activeNetwork, selectedNetworks]);

  const snapshot = useMemo(
    () => (data ? powerDtoToSnapshot(data) : null),
    [data]
  );

  if (selectedNetworks.length === 0) {
    return (
      <PageShell title={t('power')}>
        <Card>
          <Empty description={t('selectNetworkFirst')} />
        </Card>
      </PageShell>
    );
  }

  if (!data && initialLoading) {
    return (
      <PageShell title={t('power')}>
        <Card>
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin aria-label={t('loading')} />
          </div>
        </Card>
      </PageShell>
    );
  }

  if (!data) {
    return (
      <PageShell title={t('power')}>
        <Card>
          <Empty description={t('noPowerData')} />
        </Card>
      </PageShell>
    );
  }

  return (
    <PageShell
      title={t('power')}
      description={t('powerPageDesc')}
      actions={
        refreshing ? (
          <span
            className="power-refresh-indicator"
            aria-live="polite"
            style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}
          >
            {t('refreshing')}
          </span>
        ) : undefined
      }
    >
      <div className="data-page-flow">
        {selectedNetworks.length > 1 && (
          <div className="data-page-network-switcher">
            <div className="data-page-network-switcher__label">
              <ApartmentOutlined />
              <span>{t('viewingNetwork')}</span>
            </div>
            <Tabs
              activeKey={String(currentNet)}
              onChange={(k) => setActiveNetwork(Number(k))}
              items={selectedNetworks.map((nid) => ({
                key: String(nid),
                label: `${t('networkId')} ${nid}`,
              }))}
            />
          </div>
        )}

        <PowerWidgetGrid
          snapshot={snapshot}
          networkId={currentNet}
          initialLoading={initialLoading}
        />
      </div>
    </PageShell>
  );
}
