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
  row?: 'bench' | 'battlefield';
  dataDrop?: string;
  attackOrderIndex?: number;
  dimmed?: boolean;
  onClick?: () => void;
  onPointerEnter?: () => void;
  onPointerLeave?: () => void;
  onUnitPointerDown?: (e: React.PointerEvent) => void;
  onInspect?: (def: CardDef | undefined, unit: BoardUnit) => void;
  combatLabel?: string;
}) {
  const classes = [
    'slot',
    props.row === 'battlefield' ? 'battlefield-slot' : 'bench-slot',
    props.selected || props.attackArmed ? 'selected' : '',
    props.attackArmed ? 'attack-armed' : '',
    props.dropState === 'valid' ? 'drop-valid' : '',
    props.dropState === 'invalid' ? 'drop-invalid' : '',
    !props.unit ? 'empty-hint' : '',
    props.dimmed ? 'dimmed' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <motion.div
      className={classes}
      data-slot-index={props.index}
      data-slot-side={props.side}
      data-drop={props.dataDrop}
      data-hint={props.side === 'player' && props.row === 'bench' ? `槽 ${props.index}` : ''}
      onClick={props.onClick}
      onPointerEnter={props.onPointerEnter}
      onPointerLeave={props.onPointerLeave}
      layout
      layoutId={props.unit ? `unit-${props.unit.instanceId}` : undefined}
      animate={props.attackArmed ? { y: -6 } : { y: 0 }}
      transition={{ type: 'spring', stiffness: 380, damping: 24 }}
    >
      {props.unit && (
        <>
          {props.attackOrderIndex != null && props.attackOrderIndex > 0 && (
            <span className="attack-order-badge">{props.attackOrderIndex}</span>
          )}
          {props.combatLabel && <span className="combat-role-badge">{props.combatLabel}</span>}
          <div
            className="slot-card-wrap"
            onPointerDown={props.onUnitPointerDown}
            style={props.onUnitPointerDown ? { touchAction: 'none' } : undefined}
          >
            <CardView
              def={props.cardMap.get(props.unit.cardId)}
              unit={props.unit}
              compact
              selected={props.selected || props.attackArmed}
              onInspect={() => props.onInspect?.(props.cardMap.get(props.unit!.cardId), props.unit!)}
            />
          </div>
        </>
      )}
    </motion.div>
  );
}
