import { useMemo } from 'react';
import { Modal, Typography } from 'antd';
import { Icon } from '@/components/Icon';
import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { topologyNodeLabel } from '@/utils/topologyDevices';
import { formatBytes } from '@/utils/format';

const { Text } = Typography;

interface DriveSimulatedGuiProps {
  node: TopologyNodeDto | null;
  cellChildNodes?: TopologyNodeDto[];
  open: boolean;
  onClose: () => void;
}

interface SlotView {
  slot: number;
  empty: boolean;
  displayName: string;
  itemId?: string;
  itemBytes?: number;
  fluidBytes?: number;
}

function buildSlots(node: TopologyNodeDto, cellChildren: TopologyNodeDto[]): SlotView[] {
  if (node.cellSlots && node.cellSlots.length > 0) {
    return node.cellSlots.map((c) => ({
      slot: c.slot,
      empty: c.empty,
      displayName: c.displayName || '',
      itemId: c.itemId,
      itemBytes: c.itemBytes,
      fluidBytes: c.fluidBytes,
    }));
  }
  const sorted = [...cellChildren].sort((a, b) => (a.id ?? '').localeCompare(b.id ?? ''));
  return sorted.map((c, i) => {
    const label = topologyNodeLabel(c);
    return {
    slot: i,
    empty: label.toLowerCase().includes('empty'),
    displayName: label,
    itemId: c.iconItemId,
    itemBytes: undefined,
    fluidBytes: undefined,
  };
  });
}

export function DriveSimulatedGui({ node, cellChildNodes = [], open, onClose }: DriveSimulatedGuiProps) {
  const { t } = useI18n();

  const slots = useMemo(() => (node ? buildSlots(node, cellChildNodes) : []), [node, cellChildNodes]);

  const title = node
    ? `${topologyNodeLabel(node)}${node.devices?.[0] ? ` (${node.devices[0].x}, ${node.devices[0].y}, ${node.devices[0].z})` : ''}`
    : '';

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      title={title}
      width={Math.min(560, window.innerWidth - 24)}
      className="drive-simulated-modal"
      zIndex={1100}
      destroyOnClose
      styles={{ body: { padding: '12px 16px 16px' } }}
    >
      {node && (
        <div className="drive-simulated-grid" role="grid" aria-label={t('topologyDriveGuiTitle')}>
          {Array.from({ length: 10 }, (_, slotIndex) => {
            const slot = slots.find((s) => s.slot === slotIndex) ?? {
              slot: slotIndex,
              empty: true,
              displayName: '',
            };
            return (
              <div
                key={slotIndex}
                className={`drive-simulated-slot${slot.empty ? ' drive-simulated-slot--empty' : ''}`}
                role="gridcell"
              >
                {slot.empty ? (
                  <Text type="secondary" className="drive-simulated-slot-empty">
                    {t('topologyEmptySlot')}
                  </Text>
                ) : (
                  <>
                    <div className="drive-simulated-slot-icon">
                      {slot.itemId ? <Icon id={slot.itemId} size={28} linkToWiki={false} /> : null}
                    </div>
                    <span className="drive-simulated-slot-name">{slot.displayName}</span>
                    {(slot.itemBytes != null && slot.itemBytes > 0) || (slot.fluidBytes != null && slot.fluidBytes > 0) ? (
                      <Text type="secondary" className="drive-simulated-slot-usage">
                        {slot.itemBytes ? formatBytes(slot.itemBytes) : ''}
                        {slot.itemBytes && slot.fluidBytes ? ' · ' : ''}
                        {slot.fluidBytes ? `${t('fluidTypes')} ${formatBytes(slot.fluidBytes)}` : ''}
                      </Text>
                    ) : null}
                  </>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
