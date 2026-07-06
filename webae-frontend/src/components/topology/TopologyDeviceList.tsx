import { useMemo, useRef, useState, type ReactNode } from 'react';
import { Collapse, Input, Tabs, Typography } from 'antd';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import {
  buildNodeListRows,
  filterDeviceRows,
  filterNodeRows,
  flattenDevices,
  groupRowsByDeviceType,
  type DeviceListNodeRow,
  type FlatDeviceRow,
} from '@/utils/topologyDevices';

const ROW_HEIGHT = 44;

const GROUP_I18N_KEYS: Record<string, string> = {
  controller: 'topologyGroup_controller',
  drive: 'topologyGroup_drive',
  terminal: 'topologyGroup_terminal',
  bus: 'topologyGroup_bus',
  monitor: 'topologyGroup_monitor',
  interface: 'topologyGroup_interface',
  cpu: 'topologyGroup_cpu',
  p2p: 'topologyGroup_p2p',
  quantum: 'topologyGroup_quantum',
  misc: 'topologyGroup_misc',
  spatial_bin: 'topologyGroup_spatial_bin',
};

interface TopologyDeviceListProps {
  nodes: TopologyNodeDto[];
  selectedNodeId: string | null;
  hideCableNodes: boolean;
  onSelectNode: (node: TopologyNodeDto | null) => void;
  onSelectDevice?: (nodeId: string) => void;
  height?: number;
}

function VirtualRowList<T>({
  items,
  height,
  renderRow,
}: {
  items: T[];
  height: number;
  renderRow: (item: T, index: number) => ReactNode;
}) {
  const parentRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 8,
  });

  if (items.length === 0) {
    return (
      <div className="topology-device-list-scroll" style={{ height, overflow: 'auto' }}>
        <Typography.Text type="secondary" style={{ display: 'block', padding: 12, textAlign: 'center' }}>
          —
        </Typography.Text>
      </div>
    );
  }

  return (
    <div ref={parentRef} className="topology-device-list-scroll" style={{ height, overflow: 'auto' }}>
      <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
        {virtualizer.getVirtualItems().map((vRow) => (
          <div
            key={vRow.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: vRow.size,
              transform: `translateY(${vRow.start}px)`,
            }}
          >
            {renderRow(items[vRow.index], vRow.index)}
          </div>
        ))}
      </div>
    </div>
  );
}

function GroupedDeviceList({
  groups,
  groupHeight,
  renderRow,
  groupLabel,
}: {
  groups: ReturnType<typeof groupRowsByDeviceType<FlatDeviceRow>>;
  groupHeight: number;
  renderRow: (row: FlatDeviceRow) => ReactNode;
  groupLabel: (type: string, count: number) => string;
}) {
  if (groups.length === 0) {
    return (
      <Typography.Text type="secondary" style={{ display: 'block', padding: 12, textAlign: 'center' }}>
        —
      </Typography.Text>
    );
  }
  return (
    <Collapse
      ghost
      size="small"
      defaultActiveKey={groups.map((g) => g.type)}
      items={groups.map((group) => ({
        key: group.type,
        label: groupLabel(group.type, group.rows.length),
        children: (
          <VirtualRowList
            items={group.rows}
            height={Math.min(groupHeight, Math.max(ROW_HEIGHT * 2, group.rows.length * ROW_HEIGHT))}
            renderRow={(row) => renderRow(row)}
          />
        ),
      }))}
    />
  );
}

function GroupedNodeList({
  groups,
  groupHeight,
  renderRow,
  groupLabel,
}: {
  groups: ReturnType<typeof groupRowsByDeviceType<DeviceListNodeRow>>;
  groupHeight: number;
  renderRow: (row: DeviceListNodeRow) => ReactNode;
  groupLabel: (type: string, count: number) => string;
}) {
  if (groups.length === 0) {
    return (
      <Typography.Text type="secondary" style={{ display: 'block', padding: 12, textAlign: 'center' }}>
        —
      </Typography.Text>
    );
  }
  return (
    <Collapse
      ghost
      size="small"
      defaultActiveKey={groups.map((g) => g.type)}
      items={groups.map((group) => ({
        key: group.type,
        label: groupLabel(group.type, group.rows.length),
        children: (
          <VirtualRowList
            items={group.rows}
            height={Math.min(groupHeight, Math.max(ROW_HEIGHT * 2, group.rows.length * ROW_HEIGHT))}
            renderRow={(row) => renderRow(row)}
          />
        ),
      }))}
    />
  );
}

export function TopologyDeviceList({
  nodes,
  selectedNodeId,
  hideCableNodes,
  onSelectNode,
  onSelectDevice,
  height = 480,
}: TopologyDeviceListProps) {
  const { t } = useI18n();
  const [query, setQuery] = useState('');
  const [tab, setTab] = useState<'devices' | 'nodes'>('devices');

  const deviceRows = useMemo(() => filterDeviceRows(flattenDevices(nodes), query), [nodes, query]);
  const nodeRows = useMemo(
    () => filterNodeRows(buildNodeListRows(nodes), query, hideCableNodes),
    [nodes, query, hideCableNodes]
  );

  const deviceGroups = useMemo(() => groupRowsByDeviceType(deviceRows), [deviceRows]);
  const nodeGroups = useMemo(() => groupRowsByDeviceType(nodeRows), [nodeRows]);

  const scrollHeight = height - 88;
  const groupHeight = Math.max(120, Math.floor(scrollHeight / Math.max(1, Math.min(4, tab === 'devices' ? deviceGroups.length : nodeGroups.length))));

  const groupLabel = (type: string, count: number) => {
    const key = GROUP_I18N_KEYS[type];
    const label = key ? t(key) : type;
    return `${label} (${count})`;
  };

  const renderDeviceRow = (row: FlatDeviceRow) => {
    const selected = selectedNodeId === row.nodeId;
    const iconId = blockIconIdForNode(row.nodeType, row.device.iconItemId);
    return (
      <div
        className={`topology-device-list-row${selected ? ' topology-device-list-row--selected' : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => {
          onSelectDevice?.(row.nodeId);
          const node = nodes.find((n) => n.id === row.nodeId);
          if (node) onSelectNode(node);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            onSelectDevice?.(row.nodeId);
            const node = nodes.find((n) => n.id === row.nodeId);
            if (node) onSelectNode(node);
          }
        }}
      >
        <div className="topology-device-list-row-icon">
          {iconId ? <Icon id={iconId} size={22} linkToWiki={false} /> : null}
        </div>
        <div className="topology-device-list-row-body">
          <Typography.Text ellipsis>{row.device.displayName || row.nodeDisplayName}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.device.x}, {row.device.y}, {row.device.z} · {row.nodeType}
          </Typography.Text>
        </div>
      </div>
    );
  };

  const renderNodeRow = (row: DeviceListNodeRow) => {
    const selected = selectedNodeId === row.node.id;
    const iconId = blockIconIdForNode(row.node.type, row.node.iconItemId);
    return (
      <div
        className={`topology-device-list-row${selected ? ' topology-device-list-row--selected' : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => onSelectNode(selected ? null : row.node)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSelectNode(selected ? null : row.node);
        }}
      >
        <div className="topology-device-list-row-icon">
          {iconId ? <Icon id={iconId} size={22} linkToWiki={false} /> : null}
        </div>
        <div className="topology-device-list-row-body">
          <Typography.Text ellipsis>{row.node.displayName}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {row.node.type}
            {row.node.count > 1 ? ` ×${row.node.count}` : ''}
          </Typography.Text>
        </div>
      </div>
    );
  };

  return (
    <div className="topology-device-list">
      <Input.Search
        allowClear
        placeholder={t('topologyDeviceSearch')}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        style={{ marginBottom: 8 }}
        aria-label={t('topologyDeviceSearch')}
      />
      <Tabs
        activeKey={tab}
        onChange={(k) => setTab(k as 'devices' | 'nodes')}
        size="small"
        items={[
          {
            key: 'devices',
            label: `${t('topologyTabDevices')} (${deviceRows.length})`,
            children: (
              <div className="topology-device-list-scroll" style={{ height: scrollHeight, overflow: 'auto' }}>
                <GroupedDeviceList
                  groups={deviceGroups}
                  groupHeight={groupHeight}
                  renderRow={renderDeviceRow}
                  groupLabel={groupLabel}
                />
              </div>
            ),
          },
          {
            key: 'nodes',
            label: `${t('topologyTabNodes')} (${nodeRows.length})`,
            children: (
              <div className="topology-device-list-scroll" style={{ height: scrollHeight, overflow: 'auto' }}>
                <GroupedNodeList
                  groups={nodeGroups}
                  groupHeight={groupHeight}
                  renderRow={renderNodeRow}
                  groupLabel={groupLabel}
                />
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
