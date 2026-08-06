import { CpuComponentIconGrid } from '@/components/topology/CpuComponentIconGrid';
import { Button, Descriptions, Drawer, Table, Tabs, Typography } from 'antd';

import { useI18n } from '@/i18n';
import type { TopologyCellSlotDto, TopologyDeviceRecordDto, TopologyNodeDto } from '@/types/dto';
import { resolveGroupType } from '@/utils/topologyDevices';

const { Text, Title } = Typography;

function deviceTable(devices: TopologyDeviceRecordDto[], t: (k: string) => string) {
  return (
    <Table
      size="small"
      pagination={{ pageSize: 8, showSizeChanger: false }}
      dataSource={devices.map((d, i) => ({ ...d, key: i }))}
      columns={[
        {
          title: t('topologyDeviceName'),
          dataIndex: 'displayName',
          ellipsis: true,
          render: (v: string, row) => v || row.className || '—',
        },
        {
          title: t('topologyCoords'),
          key: 'coords',
          width: 140,
          render: (_, row) => `${row.x}, ${row.y}, ${row.z}`,
        },
        { title: t('topologyDim'), dataIndex: 'dim', width: 48 },
        {
          title: t('topologyChannelCost'),
          dataIndex: 'channelCost',
          width: 72,
          render: (v: number | undefined) => (v != null ? v : '—'),
        },
      ]}
    />
  );
}

function cellSlotsTable(slots: TopologyCellSlotDto[], t: (k: string) => string) {
  return (
    <Table
      size="small"
      pagination={{ pageSize: 10, showSizeChanger: false }}
      dataSource={slots.map((s) => ({ ...s, key: s.slot }))}
      columns={[
        { title: '#', dataIndex: 'slot', width: 40 },
        {
          title: t('topologyDeviceName'),
          dataIndex: 'displayName',
          ellipsis: true,
          render: (v: string, row) => (row.empty ? '—' : v || row.itemId || '—'),
        },
        {
          title: t('topologyCellBytes'),
          key: 'bytes',
          render: (_, row) => {
            if (row.empty) return '—';
            const parts: string[] = [];
            if (row.itemBytes) parts.push(`${row.itemBytes} B`);
            if (row.fluidBytes) parts.push(`${row.fluidBytes} fl`);
            return parts.length > 0 ? parts.join(' · ') : '—';
          },
        },
      ]}
    />
  );
}

export function CpuRuntimeDetailPanel({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  const summary = node.cpuSummary;
  if (!summary) return null;
  return (
    <Descriptions size="small" column={1} style={{ marginBottom: 12 }}>
      <Descriptions.Item label={t('topologyCpuCoProcessors')}>{summary.coProcessors}</Descriptions.Item>
      <Descriptions.Item label={t('topologyCpuStorage')}>
        {t('topologyCpuStorageUsed')}: {summary.usedStorage} · {t('topologyCpuStorageAvail')}:{' '}
        {summary.availableStorage}
      </Descriptions.Item>
      <Descriptions.Item label={t('topologyCpuStatus')}>
        {summary.busy ? t('busy') : t('topologyCpuIdle')}
      </Descriptions.Item>
      <Descriptions.Item label={t('topologyCpuUnitCount')}>{summary.unitCount}</Descriptions.Item>
      {summary.storageUnits > 0 && (
        <Descriptions.Item label={t('topologyCpuStorageUnits')}>{summary.storageUnits}</Descriptions.Item>
      )}
      {summary.acceleratorUnits > 0 && (
        <Descriptions.Item label={t('topologyCpuAcceleratorUnits')}>{summary.acceleratorUnits}</Descriptions.Item>
      )}
      {summary.monitorUnits > 0 && (
        <Descriptions.Item label={t('topologyCpuMonitorUnits')}>{summary.monitorUnits}</Descriptions.Item>
      )}
    </Descriptions>
  );
}

function BaseMeta({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <Descriptions size="small" column={1} style={{ marginBottom: 12 }}>
      <Descriptions.Item label={t('topologyNodeType')}>{resolveGroupType(node)}</Descriptions.Item>
      <Descriptions.Item label={t('topologyNodeCount')}>{node.count}</Descriptions.Item>
      <Descriptions.Item label={t('topologyChannelCost')}>{node.channelCost}</Descriptions.Item>
      {node.dim != null && node.dim !== -2147483648 && (
        <Descriptions.Item label={t('topologyDim')}>{node.dim}</Descriptions.Item>
      )}
      {(node.patternCount ?? 0) > 0 && (
        <Descriptions.Item label={t('topologyPatternCount').replace('{count}', String(node.patternCount))}>
          {node.patternCount}
        </Descriptions.Item>
      )}
    </Descriptions>
  );
}

function InGameHint() {
  const { t } = useI18n();
  return (
    <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12 }}>
      {t('topologyDetailInGameHint')}
    </Text>
  );
}

export function CpuDetailContent({
  node,
  onOpenDriveGui,
}: {
  node: TopologyNodeDto;
  onOpenDriveGui?: (node: TopologyNodeDto) => void;
}) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      <CpuRuntimeDetailPanel node={node} />
      <Title level={5} style={{ marginTop: 0 }}>
        {t('topologyCpuComposition')}
      </Title>
      <CpuComponentIconGrid node={node} />
      {(node.devices?.length ?? 0) > 0 && (
        <>
          <Title level={5} style={{ marginTop: 0 }}>
            {t('topologyCpuComponents')}
          </Title>
          {deviceTable(node.devices ?? [], t)}
        </>
      )}
      {onOpenDriveGui && node.type === 'drive' && (
        <Button type="primary" block style={{ marginTop: 12 }} onClick={() => onOpenDriveGui(node)}>
          {t('topologyOpenDriveGui')}
        </Button>
      )}
    </>
  );
}

export function DriveDetailContent({
  node,
  onOpenDriveGui,
}: {
  node: TopologyNodeDto;
  onOpenDriveGui?: (node: TopologyNodeDto) => void;
}) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      {onOpenDriveGui && (
        <Button type="primary" block style={{ marginBottom: 12 }} onClick={() => onOpenDriveGui(node)}>
          {t('topologyOpenDriveGui')}
        </Button>
      )}
      {(node.cellSlots?.length ?? 0) > 0 && (
        <>
          <Title level={5} style={{ marginTop: 0 }}>
            {t('topologyCellSlots')}
          </Title>
          {cellSlotsTable(node.cellSlots ?? [], t)}
        </>
      )}
      {(node.patternSlots?.length ?? 0) > 0 && (
        <>
          <Title level={5}>{t('topologyPatternSlots')}</Title>
          <Table
            size="small"
            pagination={false}
            dataSource={(node.patternSlots ?? []).map((s) => ({ ...s, key: s.slot }))}
            columns={[
              { title: '#', dataIndex: 'slot', width: 40 },
              { title: t('topologyDeviceName'), dataIndex: 'displayName', ellipsis: true },
            ]}
          />
        </>
      )}
      {(node.devices?.length ?? 0) > 0 && deviceTable(node.devices ?? [], t)}
    </>
  );
}

export function ChestDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      {(node.cellSlots?.length ?? 0) > 0 ? (
        cellSlotsTable(node.cellSlots ?? [], t)
      ) : (
        deviceTable(node.devices ?? [], t)
      )}
    </>
  );
}

export function IoPortDetailContent({ node }: { node: TopologyNodeDto }) {
  return <ChestDetailContent node={node} />;
}

export function BusDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  const subtype = resolveGroupType(node);
  return (
    <>
      <BaseMeta node={node} />
      <InGameHint />
      <Tabs
        items={[
          {
            key: 'devices',
            label: t('topologyBusDevices'),
            children: deviceTable(node.devices ?? [], t),
          },
          ...(node.cellSlots?.length
            ? [
                {
                  key: 'filters',
                  label: t('topologyBusFilters'),
                  children: cellSlotsTable(node.cellSlots ?? [], t),
                },
              ]
            : []),
        ]}
        defaultActiveKey="devices"
      />
      <Text type="secondary" style={{ fontSize: 12 }}>
        {subtype}
      </Text>
    </>
  );
}

export function InterfaceDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      <InGameHint />
      {deviceTable(node.devices ?? [], t)}
    </>
  );
}

export function SecurityTerminalDetailContent({ node }: { node: TopologyNodeDto }) {
  return <InterfaceDetailContent node={node} />;
}

export function LevelMaintainerDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      <InGameHint />
      <Text type="secondary">{t('topologyLevelMaintainerHint')}</Text>
      {deviceTable(node.devices ?? [], t)}
    </>
  );
}

export function ControllerDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      {deviceTable(node.devices ?? [], t)}
    </>
  );
}

export function GenericDetailContent({ node }: { node: TopologyNodeDto }) {
  const { t } = useI18n();
  return (
    <>
      <BaseMeta node={node} />
      {(node.devices?.length ?? 0) > 0 && deviceTable(node.devices ?? [], t)}
      {(node.cellSlots?.length ?? 0) > 0 && cellSlotsTable(node.cellSlots ?? [], t)}
      {!node.devices?.length && !node.cellSlots?.length && (
        <Text type="secondary">{t('topologyDetailNoData')}</Text>
      )}
    </>
  );
}

export function TopologyDetailContent({
  node,
  onOpenDriveGui,
}: {
  node: TopologyNodeDto;
  onOpenDriveGui?: (node: TopologyNodeDto) => void;
}) {
  const subtype = resolveGroupType(node);
  if (subtype === 'cpu') return <CpuDetailContent node={node} onOpenDriveGui={onOpenDriveGui} />;
  if (subtype === 'drive') return <DriveDetailContent node={node} onOpenDriveGui={onOpenDriveGui} />;
  if (subtype === 'chest') return <ChestDetailContent node={node} />;
  if (subtype === 'io_port') return <IoPortDetailContent node={node} />;
  if (subtype === 'bus_import' || subtype === 'bus_export' || subtype === 'bus_storage') {
    return <BusDetailContent node={node} />;
  }
  if (subtype === 'interface') return <InterfaceDetailContent node={node} />;
  if (subtype === 'security_terminal') return <SecurityTerminalDetailContent node={node} />;
  if (subtype === 'level_maintainer') return <LevelMaintainerDetailContent node={node} />;
  if (subtype === 'controller') return <ControllerDetailContent node={node} />;
  if (
    subtype === 'quantum' ||
    subtype === 'energy_cell' ||
    subtype === 'energy_acceptor' ||
    subtype.startsWith('p2p_')
  ) {
    return <GenericDetailContent node={node} />;
  }
  return <GenericDetailContent node={node} />;
}

export interface TopologyNodeDetailDrawerProps {
  node: TopologyNodeDto | null;
  open: boolean;
  onClose: () => void;
  onOpenDriveGui?: (node: TopologyNodeDto) => void;
}

export function TopologyNodeDetailDrawer({
  node,
  open,
  onClose,
  onOpenDriveGui,
}: TopologyNodeDetailDrawerProps) {
  const { t } = useI18n();
  return (
    <Drawer
      title={node ? (node.displayName || node.type) : t('topologyNodeDetail')}
      open={open}
      onClose={onClose}
      width={Math.min(520, window.innerWidth - 24)}
      destroyOnClose={false}
    >
      {node && <TopologyDetailContent node={node} onOpenDriveGui={onOpenDriveGui} />}
    </Drawer>
  );
}
