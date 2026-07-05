import { useCallback, useEffect, useState } from 'react';
import { Card, Empty, Spin, Table, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { P2pFrequencyGroupDto, P2pMapResponse } from '@/types/dto';

const { Text } = Typography;

interface P2pMapPanelProps {
  networkId: number;
}

export function P2pMapPanel({ networkId }: P2pMapPanelProps) {
  const { t } = useI18n();
  const [loading, setLoading] = useState(false);
  const [groups, setGroups] = useState<P2pFrequencyGroupDto[]>([]);
  const [tunnelCount, setTunnelCount] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<P2pMapResponse>(`/api/network/p2p?network=${networkId}`);
      if (data.success && data.data) {
        setGroups(data.data.groups || []);
        setTunnelCount(data.data.tunnelCount || 0);
      } else {
        setGroups([]);
        setTunnelCount(0);
      }
    } catch {
      setGroups([]);
      setTunnelCount(0);
    } finally {
      setLoading(false);
    }
  }, [networkId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
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
  );
}
