import { useCallback, useRef, useState } from 'react';
import type { CardDef, PlayerState } from '../api/client';
import {
  canPlayDrop,
  isPlayWindow,
  parseDropFromElement,
  resolvePlayFromDrop,
  type DropTarget,
} from '../lib/playLegality';

export interface DragState {
  handIndex: number | null;
  hoverTarget: DropTarget | null;
  dropState: 'none' | 'valid' | 'invalid';
  dragging: boolean;
}

export function useDragPlay(args: {
  phase: string;
  me: PlayerState | null;
  opponent: PlayerState | null;
  cardMap: Map<string, CardDef>;
  busy: boolean;
  onPlay: (action: unknown) => void | Promise<void>;
}) {
  const [drag, setDrag] = useState<DragState>({
    handIndex: null,
    hoverTarget: null,
    dropState: 'none',
    dragging: false,
  });
  const dragRef = useRef(drag);
  dragRef.current = drag;

  const evaluateTarget = useCallback(
    (handIndex: number, target: DropTarget) => {
      if (!args.me) return 'invalid' as const;
      const cardId = args.me.hand[handIndex];
      const def = cardId && cardId !== '?' ? args.cardMap.get(cardId) : undefined;
      const legal = canPlayDrop({
        phase: args.phase,
        me: args.me,
        opponent: args.opponent ?? undefined,
        handIndex,
        target,
        cardMap: args.cardMap,
      });
      if (!legal.ok) return 'invalid' as const;
      if (def?.kind !== 'spell' && target.kind === 'unit' && target.side === 'enemy') {
        return 'invalid' as const;
      }
      return 'valid' as const;
    },
    [args.cardMap, args.me, args.opponent, args.phase],
  );

  const beginDrag = useCallback(
    (handIndex: number) => {
      if (args.busy || !isPlayWindow(args.phase) || !args.me) return;
      setDrag({
        handIndex,
        hoverTarget: null,
        dropState: 'none',
        dragging: true,
      });
    },
    [args.busy, args.me, args.phase],
  );

  const hoverDrop = useCallback(
    (target: DropTarget) => {
      const cur = dragRef.current;
      if (!cur.dragging || cur.handIndex == null) return;
      const dropState = evaluateTarget(cur.handIndex, target);
      setDrag((d) => ({ ...d, hoverTarget: target, dropState }));
    },
    [evaluateTarget],
  );

  const leaveDrop = useCallback(() => {
    setDrag((d) =>
      d.dragging ? { ...d, hoverTarget: null, dropState: 'none' } : d,
    );
  }, []);

  const endDrag = useCallback(
    async (clientX?: number, clientY?: number) => {
      const cur = dragRef.current;
      if (!cur.dragging || cur.handIndex == null || !args.me) {
        setDrag({ handIndex: null, hoverTarget: null, dropState: 'none', dragging: false });
        return;
      }

      let target = cur.hoverTarget;
      if (clientX != null && clientY != null) {
        const el = document.elementFromPoint(clientX, clientY);
        const fromPoint = parseDropFromElement(el);
        if (fromPoint) target = fromPoint;
      }

      if (target && evaluateTarget(cur.handIndex, target) === 'valid') {
        const cardId = args.me.hand[cur.handIndex];
        const def = cardId && cardId !== '?' ? args.cardMap.get(cardId) : undefined;
        const action = resolvePlayFromDrop({
          handIndex: cur.handIndex,
          target,
          me: args.me,
          kind: def?.kind,
        });
        setDrag({ handIndex: null, hoverTarget: null, dropState: 'none', dragging: false });
        if (action) await args.onPlay(action);
        return;
      }

      setDrag({ handIndex: null, hoverTarget: null, dropState: 'none', dragging: false });
    },
    [args, evaluateTarget],
  );

  const cancelDrag = useCallback(() => {
    setDrag({ handIndex: null, hoverTarget: null, dropState: 'none', dragging: false });
  }, []);

  const dropStateFor = useCallback(
    (target: DropTarget) => {
      if (!drag.dragging || drag.handIndex == null) return 'none' as const;
      const same =
        drag.hoverTarget?.kind === target.kind &&
        drag.hoverTarget?.side === target.side &&
        drag.hoverTarget?.slot === target.slot &&
        drag.hoverTarget?.attackerInstanceId === target.attackerInstanceId;
      return same ? drag.dropState : ('none' as const);
    },
    [drag],
  );

  return { drag, beginDrag, hoverDrop, leaveDrop, endDrag, cancelDrag, dropStateFor };
};
