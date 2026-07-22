import { useCallback, useRef, useState } from 'react';
import type { CardDef, PlayerState } from '../api/client';
import { buildPlayAction, canPlayToSlot, type DropSide } from '../lib/playLegality';

export interface DragState {
  handIndex: number | null;
  hoverSlot: number | null;
  hoverSide: DropSide | null;
  dropState: 'none' | 'valid' | 'invalid';
  dragging: boolean;
}

export function useDragPlay(args: {
  phase: string;
  me: PlayerState | null;
  cardMap: Map<string, CardDef>;
  busy: boolean;
  onPlay: (action: unknown) => void | Promise<void>;
}) {
  const [drag, setDrag] = useState<DragState>({
    handIndex: null,
    hoverSlot: null,
    hoverSide: null,
    dropState: 'none',
    dragging: false,
  });
  const dragRef = useRef(drag);
  dragRef.current = drag;

  const evaluate = useCallback(
    (handIndex: number, slot: number, side: DropSide) => {
      if (!args.me) return 'invalid' as const;
      const cardId = args.me.hand[handIndex];
      const def = cardId && cardId !== '?' ? args.cardMap.get(cardId) : undefined;
      const legal = canPlayToSlot({
        phase: args.phase,
        me: args.me,
        handIndex,
        targetSlot: slot,
        cardMap: args.cardMap,
        side,
      });
      if (!legal.ok) return 'invalid' as const;
      if (def?.kind !== 'spell' && side === 'enemy') return 'invalid' as const;
      return 'valid' as const;
    },
    [args.cardMap, args.me, args.phase],
  );

  const beginDrag = useCallback(
    (handIndex: number) => {
      if (args.busy || args.phase !== 'main' || !args.me) return;
      setDrag({
        handIndex,
        hoverSlot: null,
        hoverSide: null,
        dropState: 'none',
        dragging: true,
      });
    },
    [args.busy, args.me, args.phase],
  );

  const hoverSlot = useCallback(
    (slot: number, side: DropSide) => {
      const cur = dragRef.current;
      if (!cur.dragging || cur.handIndex == null) return;
      const dropState = evaluate(cur.handIndex, slot, side);
      setDrag((d) => ({ ...d, hoverSlot: slot, hoverSide: side, dropState }));
    },
    [evaluate],
  );

  const leaveSlot = useCallback(() => {
    setDrag((d) =>
      d.dragging ? { ...d, hoverSlot: null, hoverSide: null, dropState: 'none' } : d,
    );
  }, []);

  const endDrag = useCallback(async () => {
    const cur = dragRef.current;
    if (!cur.dragging || cur.handIndex == null || !args.me) {
      setDrag({ handIndex: null, hoverSlot: null, hoverSide: null, dropState: 'none', dragging: false });
      return;
    }
    if (cur.hoverSlot != null && cur.hoverSide && cur.dropState === 'valid') {
      const cardId = args.me.hand[cur.handIndex];
      const def = cardId && cardId !== '?' ? args.cardMap.get(cardId) : undefined;
      const action = buildPlayAction({
        handIndex: cur.handIndex,
        targetSlot: cur.hoverSlot,
        side: cur.hoverSide,
        kind: def?.kind,
      });
      setDrag({ handIndex: null, hoverSlot: null, hoverSide: null, dropState: 'none', dragging: false });
      await args.onPlay(action);
      return;
    }
    setDrag({ handIndex: null, hoverSlot: null, hoverSide: null, dropState: 'none', dragging: false });
  }, [args]);

  const cancelDrag = useCallback(() => {
    setDrag({ handIndex: null, hoverSlot: null, hoverSide: null, dropState: 'none', dragging: false });
  }, []);

  return { drag, beginDrag, hoverSlot, leaveSlot, endDrag, cancelDrag, evaluate };
}
