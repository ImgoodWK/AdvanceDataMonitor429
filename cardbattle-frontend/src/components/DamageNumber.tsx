import { AnimatePresence, motion } from 'framer-motion';

export function DamageNumber(props: {
  id: string;
  x: number;
  y: number;
  value: number;
  heal?: boolean;
}) {
  return (
    <motion.div
      className={`damage-number${props.heal ? ' heal' : ''}`}
      style={{ left: props.x, top: props.y }}
      initial={{ opacity: 0, y: 8, scale: 0.6 }}
      animate={{ opacity: 1, y: -36, scale: 1.15 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.7 }}
    >
      {props.heal ? '+' : '-'}
      {Math.abs(props.value)}
    </motion.div>
  );
}

export function DamageNumberLayer(props: {
  items: Array<{ id: string; x: number; y: number; value: number; heal?: boolean }>;
}) {
  return (
    <AnimatePresence>
      {props.items.map((d) => (
        <DamageNumber key={d.id} {...d} />
      ))}
    </AnimatePresence>
  );
}
