import { useMemo, useState } from 'react';
import { Button, Empty, Input, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import {
  InboxOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { Icon } from '@/components/Icon';
import { interfaceAddress } from '@/utils/patternEditor';
import type {
  InterfaceDto,
  InterfaceExistingPattern,
  PatternBufferEntry,
  PatternListEntryDto,
} from '@/types/dto';

const { Text } = Typography;
const DRAG_TYPE = 'application/x-webae-pattern-transfer';

interface DragPayload {
  kind: 'interface' | 'buffer';
  id: string;
}

interface PatternInterfaceWorkspaceProps {
  t: (key: string) => string;
  interfaces: InterfaceDto[];
  bufferEntries: PatternBufferEntry[];
  currentPattern: PatternListEntryDto | null;
  selectedInterfaceId: string;
  selectedSlot: number;
  selectedBufferId: string;
  busy: boolean;
  canInject: boolean;
  onSelectedInterfaceChange: (id: string) => void;
  onSelectedSlotChange: (slot: number) => void;
  onSelectedBufferChange: (id: string) => void;
  onEditPattern: (patternId: string) => void;
  onMovePattern: (patternId: string, iface: InterfaceDto, slot: number, swap: boolean) => void;
  onTakePattern: (patternId: string) => void;
  onPlaceBuffer: (bufferId: string, iface: InterfaceDto, slot: number) => void;
  onInjectCurrent: () => void;
  onRefresh: () => void;
}

function occupiedPattern(iface: InterfaceDto, slot: number): InterfaceExistingPattern | undefined {
  return iface.existingPatterns?.find((entry) => entry.slotIndex === slot);
}

export function PatternInterfaceWorkspace({
  t,
  interfaces,
  bufferEntries,
  currentPattern,
  selectedInterfaceId,
  selectedSlot,
  selectedBufferId,
  busy,
  canInject,
  onSelectedInterfaceChange,
  onSelectedSlotChange,
  onSelectedBufferChange,
  onEditPattern,
  onMovePattern,
  onTakePattern,
  onPlaceBuffer,
  onInjectCurrent,
  onRefresh,
}: PatternInterfaceWorkspaceProps) {
  const [filter, setFilter] = useState('');
  const selectedInterface = interfaces.find((iface) => interfaceAddress(iface) === selectedInterfaceId);
  const q = filter.trim().toLowerCase();
  const filteredInterfaces = useMemo(
    () => interfaces.filter((iface) => {
      if (!q) return true;
      return `${iface.name} ${iface.machineRecipeType || ''} ${interfaceAddress(iface)}`.toLowerCase().includes(q);
    }),
    [interfaces, q]
  );

  const moveWithConfirmation = (patternId: string, iface: InterfaceDto, slot: number) => {
    const occupied = Boolean(occupiedPattern(iface, slot));
    if (!occupied) {
      onMovePattern(patternId, iface, slot, false);
      return;
    }
    Modal.confirm({
      title: t('patternSwapConfirm'),
      okText: t('patternSwap'),
      cancelText: t('cancel'),
      onOk: () => onMovePattern(patternId, iface, slot, true),
    });
  };

  const handleDrop = (event: React.DragEvent, iface: InterfaceDto, slot: number) => {
    event.preventDefault();
    try {
      const payload = JSON.parse(event.dataTransfer.getData(DRAG_TYPE)) as DragPayload;
      if (payload.kind === 'interface') moveWithConfirmation(payload.id, iface, slot);
      else if (!occupiedPattern(iface, slot)) onPlaceBuffer(payload.id, iface, slot);
    } catch {
      // Ignore unrelated browser drags.
    }
  };

  return (
    <section className="webae-pattern-rail webae-pattern-interface-workspace" aria-label={t('patternInterfaceWorkspace')}>
      <div className="webae-pattern-rail-header webae-pattern-rail-header--row">
        <div>
          <Text strong>{t('patternInterfaceWorkspace')}</Text>
          <Text type="secondary" className="webae-text-2xs">{t('patternInterfaceWorkspaceHint')}</Text>
        </div>
        <Tooltip title={t('patternRefreshInterfaces')}>
          <Button icon={<ReloadOutlined />} loading={busy} onClick={onRefresh} aria-label={t('patternRefreshInterfaces')} />
        </Tooltip>
      </div>
      <Input
        prefix={<SearchOutlined />}
        value={filter}
        onChange={(event) => setFilter(event.target.value)}
        placeholder={t('patternInterfaceSearch')}
        allowClear
      />
      <div className="webae-pattern-interface-list webae-scroll-panel">
        {filteredInterfaces.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('patternNoInterfaces')} />
        ) : (
          filteredInterfaces.map((iface) => {
            const id = interfaceAddress(iface);
            const occupied = iface.existingPatterns?.length ?? 0;
            return (
              <button
                type="button"
                key={id}
                className={`webae-pattern-interface-card${id === selectedInterfaceId ? ' is-active' : ''}`}
                onClick={() => onSelectedInterfaceChange(id)}
              >
                <span className="webae-pattern-interface-title">{iface.name}</span>
                <span className="webae-pattern-interface-meta">
                  {id} · {t('patternInterfacePatternCount').replace('{n}', String(occupied))}
                </span>
                {iface.machineRecipeType && <span className="webae-pattern-interface-meta">{iface.machineRecipeType}</span>}
              </button>
            );
          })
        )}
      </div>

      {selectedInterface && (
        <>
          <div className="webae-pattern-slot-summary">
            <Text strong>{selectedInterface.name}</Text>
            <Tag>{t('patternSlotsCount').replace('{n}', String(selectedInterface.activeSlots))}</Tag>
            {selectedInterface.partSide && <Tag color="cyan">{selectedInterface.partSide}</Tag>}
          </div>
          <div className="webae-pattern-interface-slots" role="grid" aria-label={t('patternInterfaceSlots')}>
            {Array.from({ length: selectedInterface.activeSlots }, (_, slot) => {
              const pattern = occupiedPattern(selectedInterface, slot);
              const output = pattern?.outputs?.[0];
              const selected = slot === selectedSlot;
              return (
                <Tooltip
                  key={slot}
                  title={pattern ? output?.displayName || output?.registryName || pattern.patternId : t('patternEmptySlot')}
                >
                  <button
                    type="button"
                    role="gridcell"
                    aria-selected={selected}
                    draggable={Boolean(pattern)}
                    className={`webae-pattern-interface-slot${selected ? ' is-selected' : ''}${pattern ? ' is-occupied' : ''}`}
                    onClick={() => {
                      onSelectedSlotChange(slot);
                      if (pattern) onEditPattern(pattern.patternId);
                    }}
                    onDragStart={(event) => {
                      if (!pattern) return;
                      event.dataTransfer.setData(DRAG_TYPE, JSON.stringify({ kind: 'interface', id: pattern.patternId }));
                    }}
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => handleDrop(event, selectedInterface, slot)}
                  >
                    <span className="webae-pattern-interface-slot-index">{slot + 1}</span>
                    {output && <Icon item={output} size={24} alt={output.displayName || output.registryName} />}
                  </button>
                </Tooltip>
              );
            })}
          </div>
          <Space wrap className="webae-pattern-transfer-actions">
            <Button
              icon={<SaveOutlined />}
              type="primary"
              disabled={!canInject || Boolean(occupiedPattern(selectedInterface, selectedSlot))}
              loading={busy}
              onClick={onInjectCurrent}
            >
              {t('patternWriteSelectedSlot')}
            </Button>
            <Button
              icon={<SwapOutlined />}
              disabled={!currentPattern}
              loading={busy}
              onClick={() => currentPattern && moveWithConfirmation(currentPattern.patternId, selectedInterface, selectedSlot)}
            >
              {t('patternMoveSelectedSlot')}
            </Button>
            <Button
              icon={<InboxOutlined />}
              disabled={!currentPattern}
              loading={busy}
              onClick={() => currentPattern && onTakePattern(currentPattern.patternId)}
            >
              {t('patternMoveToBuffer')}
            </Button>
          </Space>
        </>
      )}

      <div className="webae-pattern-buffer-header">
        <Text strong>{t('patternWebBuffer')}</Text>
        <Tag>{bufferEntries.length}</Tag>
      </div>
      <div className="webae-pattern-buffer-list webae-scroll-panel">
        {bufferEntries.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('patternWebBufferEmpty')} />
        ) : (
          bufferEntries.map((entry) => {
            const output = entry.outputs?.[0];
            return (
              <button
                type="button"
                key={entry.id}
                draggable
                className={`webae-pattern-buffer-row${entry.id === selectedBufferId ? ' is-active' : ''}`}
                onClick={() => onSelectedBufferChange(entry.id)}
                onDragStart={(event) => {
                  event.dataTransfer.setData(DRAG_TYPE, JSON.stringify({ kind: 'buffer', id: entry.id }));
                }}
              >
                {output && <Icon item={output} size={28} alt={output.displayName || output.registryName} />}
                <span>
                  <span className="webae-pattern-interface-title">{output?.displayName || output?.registryName || entry.id}</span>
                  <span className="webae-pattern-interface-meta">
                    {entry.sourceInterfaceName} · {t('patternSlot')} {entry.sourceSlot + 1}
                  </span>
                </span>
              </button>
            );
          })
        )}
      </div>
      <Button
        block
        icon={<InboxOutlined />}
        disabled={!selectedInterface || !selectedBufferId || Boolean(selectedInterface && occupiedPattern(selectedInterface, selectedSlot))}
        loading={busy}
        onClick={() => selectedInterface && selectedBufferId && onPlaceBuffer(selectedBufferId, selectedInterface, selectedSlot)}
      >
        {t('patternPlaceBuffer')}
      </Button>
    </section>
  );
}
