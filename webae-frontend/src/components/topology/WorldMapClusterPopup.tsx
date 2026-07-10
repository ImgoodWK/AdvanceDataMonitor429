import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { CloseOutlined } from '@ant-design/icons';

import { useI18n } from '@/i18n';
import type { TopologyNodeDto } from '@/types/dto';
import { WorldMapStackDeviceList } from '@/components/topology/WorldMapStackDeviceList';

export interface WorldMapClusterPopupProps {
  open: boolean;
  anchorX: number;
  anchorY: number;
  nodes: TopologyNodeDto[];
  selectedNodeId?: string | null;
  viewportRef: React.RefObject<HTMLDivElement | null>;
  onClose: () => void;
  onSelectNode: (node: TopologyNodeDto) => void;
}

const POPUP_W = 280;
const POPUP_MAX_H = 360;

export function WorldMapClusterPopup({
  open,
  anchorX,
  anchorY,
  nodes,
  selectedNodeId,
  viewportRef,
  onClose,
  onSelectNode,
}: WorldMapClusterPopupProps) {
  const { t } = useI18n();
  const cardRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef({ active: false, startX: 0, startY: 0, left: 0, top: 0 });
  const [offset, setOffset] = useState({ left: 0, top: 0 });

  const clampPosition = useCallback(
    (left: number, top: number) => {
      const vp = viewportRef.current;
      if (!vp) return { left, top };
      const maxLeft = Math.max(8, vp.clientWidth - POPUP_W - 8);
      const maxTop = Math.max(8, vp.clientHeight - 80);
      return {
        left: Math.min(maxLeft, Math.max(8, left)),
        top: Math.min(maxTop, Math.max(8, top)),
      };
    },
    [viewportRef]
  );

  useEffect(() => {
    if (!open) return;
    const vp = viewportRef.current;
    if (!vp) return;
    const rect = vp.getBoundingClientRect();
    const rawLeft = anchorX - rect.left - POPUP_W / 2;
    const rawTop = anchorY - rect.top - 12;
    setOffset(clampPosition(rawLeft, rawTop));
  }, [open, anchorX, anchorY, viewportRef, clampPosition]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const stopBubble = (e: React.SyntheticEvent) => {
    e.stopPropagation();
  };

  const onTitlePointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.stopPropagation();
    dragRef.current = {
      active: true,
      startX: e.clientX,
      startY: e.clientY,
      left: offset.left,
      top: offset.top,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onTitlePointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!dragRef.current.active) return;
    e.stopPropagation();
    const dx = e.clientX - dragRef.current.startX;
    const dy = e.clientY - dragRef.current.startY;
    setOffset(clampPosition(dragRef.current.left + dx, dragRef.current.top + dy));
  };

  const onTitlePointerUp = (e: ReactPointerEvent<HTMLDivElement>) => {
    dragRef.current.active = false;
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {
      /* ignore */
    }
  };

  if (!open) return null;

  return (
    <div
      className="worldmap-cluster-popup-backdrop"
      onPointerDown={stopBubble}
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
    >
      <div
        ref={cardRef}
        className="worldmap-cluster-popup"
        style={{ left: offset.left, top: offset.top, width: POPUP_W, maxHeight: POPUP_MAX_H }}
        onPointerDown={stopBubble}
        onClick={stopBubble}
        onWheel={stopBubble}
        role="dialog"
        aria-label={t('worldMapPopupTitle')}
      >
        <div
          className="worldmap-cluster-popup-header"
          onPointerDown={onTitlePointerDown}
          onPointerMove={onTitlePointerMove}
          onPointerUp={onTitlePointerUp}
        >
          <span className="worldmap-cluster-popup-title">{t('worldMapPopupTitle')}</span>
          <button
            type="button"
            className="worldmap-cluster-popup-close"
            aria-label={t('close')}
            onClick={(e) => {
              e.stopPropagation();
              onClose();
            }}
          >
            <CloseOutlined />
          </button>
        </div>
        <div className="worldmap-cluster-popup-body" onWheel={stopBubble}>
          <WorldMapStackDeviceList
            nodes={nodes}
            selectedNodeId={selectedNodeId}
            onSelectNode={onSelectNode}
          />
        </div>
      </div>
    </div>
  );
}
