import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, Empty, Input, Segmented, Space, Spin, Table, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { P2pFrequencyGroupDto, P2pMapResponse, P2pPowerChannelDto } from '@/types/dto';

const { Text } = Typography;

interface P2pMapPanelProps {
  networkId: number;
}

type P2pSortKey = 'frequency' | 'type' | 'count';

export function P2pMapPanel({ networkId }: P2pMapPanelProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [groups, setGroups] = useState<P2pFrequencyGroupDto[]>([]);
  const [powerChannels, setPowerChannels] = useState<P2pPowerChannelDto[]>([]);
  const [tunnelCount, setTunnelCount] = useState(0);
  const [query, setQuery] = useState('');
  const [sortKey, setSortKey] = useState<P2pSortKey>('frequency');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<P2pMapResponse>(`/api/network/p2p?network=${networkId}`);
      if (data.success && data.data) {
        setGroups(data.data.groups || []);
        setPowerChannels(data.data.powerChannels || []);
        setTunnelCount(data.data.tunnelCount || 0);
      } else {
        setGroups([]);
        setPowerChannels([]);
        setTunnelCount(0);
      }
    } catch {
      setGroups([]);
      setPowerChannels([]);
      setTunnelCount(0);
    } finally {
      setLoading(false);
    }
  }, [networkId]);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredGroups = useMemo(() => {
    let result = groups;
    const q = query.trim().toLowerCase();
    if (q) {
      result = result.filter((g) => {
        const hay = [g.type, g.frequencyHex, String(g.frequency)]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        return hay.includes(q);
      });
    }
    switch (sortKey) {
      case 'type':
        return [...result].sort((a, b) => (a.type || '').localeCompare(b.type || ''));
      case 'count':
        return [...result].sort((a, b) => (b.endpointCount || 0) - (a.endpointCount || 0));
      default:
        return [...result].sort((a, b) => a.frequency - b.frequency);
    }
  }, [groups, query, sortKey]);

  const frequencyHex = (freq: number): string => {
    return freq.toString(16).toUpperCase().padStart(4, '0');
  };

  return (
    <>
      {powerChannels.length > 0 && (
        <Card size="small" title={t('p2pPowerChannels')} style={{ marginBottom: 16 }}>
          <Table
            size="small"
            pagination={false}
            rowKey={(c) => String(c.frequency)}
            dataSource={powerChannels}
            columns={[
              {
                title: t('p2pColFrequency'),
                key: 'freq',
                width: 100,
                render: (_, c) => (
                  <Tag color="gold">
                    {frequencyHex(c.frequency)}
                  </Tag>
                ),
              },
              { title: t('p2pColEndpoints'), dataIndex: 'endpointCount', width: 80 },
              {
                title: t('p2pColPowerEu'),
                dataIndex: 'avgEuPerTick',
                width: 90,
                render: (v: number) => (v > 0 ? `${v.toFixed(1)} EU/t` : '—'),
              },
            ]}
          />
        </Card>
      )}

      <Card
        size="small"
        title={t('p2pMapTitle')}
        extra={
          <Text type="secondary">
            {t('p2pTunnelCount', tunnelCount)}
          </Text>
        }
      >
        <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }} wrap>
          <Input.Search
            allowClear
            placeholder={t('topologyDeviceSearch')}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 240 }}
            aria-label={t('topologyDeviceSearch')}
          />
          <Segmented
            value={sortKey}
            onChange={(v) => setSortKey(v as P2pSortKey)}
            options={[
              { value: 'frequency', label: t('p2pSortFreq') },
              { value: 'type', label: t('p2pSortType') },
              { value: 'count', label: t('p2pSortEndpoints') },
            ]}
            size="small"
          />
        </Space>

        <Spin spinning={loading}>
          {filteredGroups.length === 0 ? (
            <Empty description={t('p2pMapEmpty')} />
          ) : (
            <div className="p2p-frequency-grid">
              {filteredGroups.map((g) => (
                <div key={g.frequency} className="p2p-frequency-card">
                  <div className="p2p-frequency-card-header">
                    <Tag color="blue" className="p2p-freq-tag">
                      {frequencyHex(g.frequency)}
                    </Tag>
                    <Tag className="p2p-type-tag">{g.type || '?'}</Tag>
                    <span className="p2p-endpoint-count">
                      {g.endpointCount} {t('p2pColEndpointsLower')}
                    </span>
                  </div>
                  <div className="p2p-frequency-card-body">
                    {(g.endpoints || []).map((e, i) => (
                      <div key={i} className="p2p-endpoint-row">
                        <Tag color={e.inputSide ? 'green' : 'orange'} className="p2p-dir-tag">
                          {e.inputSide ? 'IN' : 'OUT'}
                        </Tag>
                        <Text type="secondary" className="p2p-coord-text">
                          D{e.dim} ({e.x}, {e.y}, {e.z})
                        </Text>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Spin>
      </Card>
    </>
  );
}
