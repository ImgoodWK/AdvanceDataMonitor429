import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Badge, Collapse, Input, Tag, Tabs, Typography } from 'antd';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Icon } from '@/components/Icon';
import { SelectableListRow } from '@/components/common/SelectableListRow';
import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { blockIconIdForNode } from '@/utils/aeCableColors';
import {
  buildNodeListRows,
  filterDeviceRows,
  filterNodeRows,
  flattenDevices,
  groupRowsByDeviceType,
  groupRowsByPodKind,
  topologyNodeLabel,
  type DeviceListNodeRow,
  type FlatDeviceRow,
} from '@/utils/topologyDevices';

const ROW_HEIGHT = 52;

const GROUP_I18N_KEYS: Record<string, string> = {
  controller: 'topologyGroup_controller',
  energy_cell: 'topologyGroup_energy_cell',
  energy_acceptor: 'topologyGroup_energy_acceptor',
  energy: 'topologyGroup_energy',
  drive: 'topologyGroup_drive',
  chest: 'topologyGroup_chest',
  io_port: 'topologyGroup_io_port',
  terminal_me: 'topologyGroup_terminal',
  terminal_crafting: 'topologyGroup_terminal_crafting',
  terminal_pattern_encoding: 'topologyGroup_terminal_pattern_encoding',
  terminal_pattern_access: 'topologyGroup_terminal_pattern_access',
  terminal_wireless: 'topologyGroup_terminal_wireless',
  wireless_access_point: 'topologyGroup_wireless_access_point',
  security_terminal: 'topologyGroup_security_terminal',
  terminal_other: 'topologyGroup_terminal',
  terminal: 'topologyGroup_terminal',
  bus_import: 'topologyGroup_bus_import',
  bus_export: 'topologyGroup_bus_export',
  bus_storage: 'topologyGroup_bus_storage',
  bus_ore_filter: 'topologyGroup_bus_ore_filter',
  bus: 'topologyGroup_bus',
  monitor_storage: 'topologyGroup_monitor',
  monitor_conversion: 'topologyGroup_monitor_conversion',
  emitter_level: 'topologyGroup_emitter_level',
  emitter_energy: 'topologyGroup_emitter_energy',
  monitor: 'topologyGroup_monitor',
  interface: 'topologyGroup_interface',
  pattern_provider: 'topologyGroup_pattern_provider',
  cpu: 'topologyGroup_cpu',
  p2p_me: 'topologyGroup_p2p_me',
  p2p_item: 'topologyGroup_p2p_item',
  p2p_fluid: 'topologyGroup_p2p_fluid',
  p2p_power: 'topologyGroup_p2p_power',
  p2p_light: 'topologyGroup_p2p_light',
  p2p_other: 'topologyGroup_p2p',
  p2p: 'topologyGroup_p2p',
  quantum: 'topologyGroup_quantum',
  misc: 'topologyGroup_misc',
  spatial_bin: 'topologyGroup_spatial_bin',
  access: 'topologyPod_access',
  io: 'topologyPod_io',
  craft: 'topologyPod_craft',
  sense: 'topologyPod_sense',
  tunnel: 'topologyPod_tunnel',
  storage0: 'topologyPod_storage0',
  craft0: 'topologyPod_craft0',
  link0: 'topologyPod_link0',
  power0: 'topologyPod_power0',
};

interface TopologyDeviceListProps {
  nodes: TopologyNodeDto[];
  selectedNodeId: string | null;
  hoveredNodeId?: string | null;
  hideCableNodes: boolean;
  onSelectNode: (node: TopologyNodeDto | null) => void;
  onSelectDevice?: (nodeId: string) => void;
  onHoverNode?: (nodeId: string | null) => void;
  height?: number;
}

function VirtualRowList<T extends { key: string }>({
  items,
  height,
  scrollToKey,
  renderRow,
}: {
  items: T[];
  height: number;
  scrollToKey?: string | null;
  renderRow: (item: T, index: number) => ReactNode;
}) {
  const parentRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 8,
    getItemKey: (index) => items[index].key,
  });

  useEffect(() => {
    if (!scrollToKey) return;
    const index = items.findIndex((item) => {
      if (item.key === scrollToKey) return true;
      const anyItem = item as unknown as FlatDeviceRow & DeviceListNodeRow;
      if (anyItem.nodeId === scrollToKey) return true;
      if (anyItem.node?.id === scrollToKey) return true;
      return false;
    });
    if (index >= 0) {
      virtualizer.scrollToIndex(index, { align: 'center' });
    }
  }, [scrollToKey, items, virtualizer]);

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
  scrollToKey,
  renderRow,
  groupLabel,
}: {
  groups: ReturnType<typeof groupRowsByDeviceType<FlatDeviceRow>>;
  groupHeight: number;
  scrollToKey?: string | null;
  renderRow: (row: FlatDeviceRow) => ReactNode;
  groupLabel: (type: string, count: number) => ReactNode;
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
          <div className="topology-device-list-group-rows">
            {group.rows.map((row) => (
              <div key={row.key}>{renderRow(row)}</div>
            ))}
          </div>
        ),
      }))}
    />
  );
}

function GroupedNodeList({
  groups,
  groupHeight,
  scrollToKey,
  renderRow,
  groupLabel,
}: {
  groups: ReturnType<typeof groupRowsByDeviceType<DeviceListNodeRow>>;
  groupHeight: number;
  scrollToKey?: string | null;
  renderRow: (row: DeviceListNodeRow) => ReactNode;
  groupLabel: (type: string, count: number) => ReactNode;
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
          <div className="topology-device-list-group-rows">
            {group.rows.map((row) => (
              <div key={row.key}>{renderRow(row)}</div>
            ))}
          </div>
        ),
      }))}
    />
  );
}

export function TopologyDeviceList({
  nodes,
  selectedNodeId,
  hoveredNodeId,
  hideCableNodes,
  onSelectNode,
  onSelectDevice,
  onHoverNode,
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
  const nodeGroups = useMemo(() => groupRowsByPodKind(nodeRows), [nodeRows]);

  const scrollHeight = height - 88;
  const groupHeight = Math.max(120, Math.floor(scrollHeight / Math.max(1, Math.min(4, tab === 'devices' ? deviceGroups.length : nodeGroups.length))));

  const groupLabel = (type: string, count: number) => {
    const key = GROUP_I18N_KEYS[type];
    const label = key ? t(key) : type;
    return (
      <span className="topology-device-list-group-label">
        <span>{label}</span>
        <Badge count={count} size="small" color="var(--accent)" />
      </span>
    );
  };

  const renderDeviceRow = (row: FlatDeviceRow) => {
    const selected = selectedNodeId === row.nodeId;
    const hovered = hoveredNodeId === row.nodeId;
    const iconId = blockIconIdForNode(row.nodeType, row.device.iconItemId);
    const node = nodes.find((n) => n.id === row.nodeId);
    return (
      <SelectableListRow
        as="div"
        selected={selected}
        hovered={hovered}
        onClick={() => {
          onSelectDevice?.(row.nodeId);
          if (node) onSelectNode(node);
        }}
        onMouseEnter={() => onHoverNode?.(row.nodeId)}
        onMouseLeave={() => onHoverNode?.(null)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            onSelectDevice?.(row.nodeId);
            if (node) onSelectNode(node);
          }
        }}
        leading={iconId ? <Icon id={iconId} size={24} linkToWiki={false} /> : null}
      >
        <Typography.Text ellipsis>{row.device.displayName || row.nodeDisplayName}</Typography.Text>
        <Typography.Text type="secondary" className="topology-device-list-row-meta">
          {row.device.x}, {row.device.y}, {row.device.z}
          {row.device.channelCost != null && row.device.channelCost > 0 ? ` · ${row.device.channelCost}ch` : ''}
          {(node?.patternCount ?? 0) > 0 ? (
            <>
              {' · '}
              <Tag className="topology-pattern-tag" bordered={false}>
                {t('topologyPatternCount').replace('{count}', String(node!.patternCount!))}
              </Tag>
            </>
          ) : null}
        </Typography.Text>
      </SelectableListRow>
    );
  };

  const renderNodeRow = (row: DeviceListNodeRow) => {
    const selected = selectedNodeId === row.node.id;
    const hovered = hoveredNodeId === row.node.id;
    const iconId = blockIconIdForNode(row.node.type, row.node.iconItemId);
    return (
      <SelectableListRow
        as="div"
        selected={selected}
        hovered={hovered}
        onClick={() => onSelectNode(selected ? null : row.node)}
        onMouseEnter={() => onHoverNode?.(row.node.id)}
        onMouseLeave={() => onHoverNode?.(null)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onSelectNode(selected ? null : row.node);
        }}
        leading={iconId ? <Icon id={iconId} size={24} linkToWiki={false} /> : null}
      >
        <Typography.Text ellipsis>{topologyNodeLabel(row.node)}</Typography.Text>
        <Typography.Text type="secondary" className="topology-device-list-row-meta">
          {row.node.subtype || row.node.type}
          {(row.node.count ?? 0) > 1 ? ` ×${row.node.count}` : ''}
          {(row.node.channelCost ?? 0) > 0 ? ` · ${row.node.channelCost}ch` : ''}
          {(row.node.patternCount ?? 0) > 0 ? (
            <>
              {' · '}
              <Tag className="topology-pattern-tag" bordered={false}>
                {t('topologyPatternCount').replace('{count}', String(row.node.patternCount!))}
              </Tag>
            </>
          ) : null}
        </Typography.Text>
      </SelectableListRow>
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
                  scrollToKey={selectedNodeId}
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
                  scrollToKey={selectedNodeId}
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
