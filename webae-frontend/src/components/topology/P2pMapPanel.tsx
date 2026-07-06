import { useCallback, useEffect, useState } from 'react';
import { Card, Empty, Spin, Table, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { P2pFrequencyGroupDto, P2pMapResponse, P2pPowerChannelDto } from '@/types/dto';

const { Text } = Typography;

interface P2pMapPanelProps {
  networkId: number;
}

export function P2pMapPanel({ networkId }: P2pMapPanelProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [groups, setGroups] = useState<P2pFrequencyGroupDto[]>([]);
  const [powerChannels, setPowerChannels] = useState<P2pPowerChannelDto[]>([]);
  const [tunnelCount, setTunnelCount] = useState(0);

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
                render: (_, c) => (
                  <Tag color="gold">
                    {c.frequencyHex || c.frequency.toString(16).toUpperCase().padStart(4, '0')}
                  </Tag>
                ),
              },
              { title: t('p2pColEndpoints'), dataIndex: 'endpointCount', width: 90 },
              {
                title: t('p2pColPowerEu'),
                dataIndex: 'avgEuPerTick',
                render: (v: number) => (v > 0 ? v.toFixed(1) : '—'),
              },
            ]}
          />
        </Card>
      )}
    <Card size="small" title={t('p2pMapTitle')} extra={<Text type="secondary">{t('p2pTunnelCount', tunnelCount)}</Text>}>
      <Spin spinning={loading}>
        {groups.length === 0 ? (
          <Empty description={t('p2pMapEmpty')} />
        ) : (
          <Table
            size="small"
            pagination={{ pageSize: 20 }}
            rowKey={(g) => String(g.frequency)}
            dataSource={groups}
            columns={[
              {
                title: t('p2pColFrequency'),
                key: 'freq',
                width: 120,
                render: (_, g) => (
                  <Tag color="blue">
                    {g.frequencyHex || g.frequency.toString(16).toUpperCase().padStart(4, '0')}
                  </Tag>
                ),
              },
              { title: t('p2pColType'), dataIndex: 'type', ellipsis: true },
              { title: t('p2pColEndpoints'), dataIndex: 'endpointCount', width: 90 },
              {
                title: t('p2pColCoords'),
                key: 'coords',
                render: (_, g) =>
                  (g.endpoints || [])
                    .slice(0, 4)
                    .map((e) => `D${e.dim}(${e.x},${e.y},${e.z})${e.inputSide ? ' IN' : ' OUT'}`)
                    .join(' · ') + ((g.endpoints?.length ?? 0) > 4 ? ' …' : ''),
              },
            ]}
          />
        )}
      </Spin>
    </Card>
    </>
  );
}
