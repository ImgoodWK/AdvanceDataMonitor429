import { motion, useMotionValue, useTransform } from 'framer-motion';
import { useGesture } from '@use-gesture/react';
import { useRef } from 'react';
import type { CardDef } from '../api/client';
import { CardView } from './CardView';

export function Hand(props: {
  hand: string[];
  cardMap: Map<string, CardDef>;
  selectedHand: number | null;
  draggingIndex: number | null;
  disabled?: boolean;
  onSelect: (index: number) => void;
  onDragStart: (index: number) => void;
  onDragEnd: () => void;
}) {
  const n = props.hand.filter((id) => id !== '?').length;
  return (
    <div className="hand-wrap">
      <div className="muted" style={{ marginBottom: 4 }}>
        手牌 · 拖到场面出牌（也可点击选中后点空槽）
      </div>
      <div className="hand">
        {props.hand.map((id, i) => {
          if (id === '?') return null;
          const def = props.cardMap.get(id);
          const fan = (i - (n - 1) / 2) * 6;
          return (
            <DraggableHandCard
              key={`${id}-${i}`}
              index={i}
              def={def}
              fan={fan}
              selected={props.selectedHand === i}
              dragging={props.draggingIndex === i}
              disabled={props.disabled}
              onSelect={() => props.onSelect(i)}
              onDragStart={() => props.onDragStart(i)}
              onDragEnd={props.onDragEnd}
            />
          );
        })}
      </div>
    </div>
  );
}

function DraggableHandCard(props: {
  index: number;
  def?: CardDef;
  fan: number;
  selected: boolean;
  dragging: boolean;
  disabled?: boolean;
  onSelect: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
}) {
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rotate = useTransform(x, [-120, 120], [-8, 8]);
  const ref = useRef<HTMLDivElement>(null);

  const bind = useGesture(
    {
      onDragStart: () => {
        if (props.disabled) return;
        props.onDragStart();
      },
      onDrag: ({ offset: [ox, oy], down }) => {
        if (props.disabled) return;
        x.set(down ? ox : 0);
        y.set(down ? oy : 0);
      },
      onDragEnd: () => {
        x.set(0);
        y.set(0);
        props.onDragEnd();
      },
      onClick: () => {
        if (!props.disabled) props.onSelect();
      },
    },
    {
      drag: { filterTaps: true, threshold: 6 },
    },
  );

  return (
    <motion.div
      ref={ref}
      {...(bind() as object)}
      style={{
        x,
        y,
        rotate: props.dragging ? rotate : props.fan,
        zIndex: props.dragging || props.selected ? 30 : 10,
        touchAction: 'none',
        pointerEvents: props.dragging ? 'none' : 'auto',
      }}
      animate={
        props.dragging
          ? { scale: 1.12 }
          : props.selected
            ? { scale: 1.06, y: -12 }
            : { scale: 1, y: 0 }
      }
      transition={{ type: 'spring', stiffness: 420, damping: 28 }}
    >
      <CardView def={props.def} selected={props.selected} dragging={props.dragging} />
    </motion.div>
  );
}
