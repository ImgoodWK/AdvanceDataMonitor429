import { motion } from 'framer-motion';
import type { BoardUnit, CardDef } from '../api/client';
import { CardView } from './CardView';

export function BoardSlot(props: {
  index: number;
  unit: BoardUnit | null;
  cardMap: Map<string, CardDef>;
  selected?: boolean;
  dropState?: 'none' | 'valid' | 'invalid';
  attackArmed?: boolean;
  side: 'player' | 'enemy';
  onClick?: () => void;
  onPointerEnter?: () => void;
  onPointerLeave?: () => void;
}) {
  const classes = [
    'slot',
    props.selected || props.attackArmed ? 'selected' : '',
    props.attackArmed ? 'attack-armed' : '',
    props.dropState === 'valid' ? 'drop-valid' : '',
    props.dropState === 'invalid' ? 'drop-invalid' : '',
    !props.unit ? 'empty-hint' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <motion.div
      className={classes}
      data-slot-index={props.index}
      data-slot-side={props.side}
      data-hint={props.side === 'player' ? `槽 ${props.index}` : ''}
      onClick={props.onClick}
      onPointerEnter={props.onPointerEnter}
      onPointerLeave={props.onPointerLeave}
      layout
      animate={props.attackArmed ? { y: -6 } : { y: 0 }}
      transition={{ type: 'spring', stiffness: 380, damping: 24 }}
    >
      {props.unit && (
        <CardView
          def={props.cardMap.get(props.unit.cardId)}
          unit={props.unit}
          compact
          selected={props.selected || props.attackArmed}
        />
      )}
    </motion.div>
  );
}
