import { motion } from 'framer-motion';

export function ManaCrystal(props: { filled: boolean; banked?: boolean }) {
  return (
    <motion.div
      className={`mana-crystal${props.filled ? '' : ' empty'}${props.banked ? ' banked' : ''}`}
      initial={false}
      animate={props.filled ? { scale: 1, opacity: 1 } : { scale: 0.85, opacity: 0.3 }}
      transition={{ type: 'spring', stiffness: 500, damping: 28 }}
    />
  );
}

export function ManaRow(props: { mana: number; maxMana: number; bankedMana?: number }) {
  const crystals = Array.from({ length: Math.max(props.maxMana, 10) }, (_, i) => i < props.mana);
  return (
    <div className="mana-row" title={`法力 ${props.mana}/${props.maxMana}${props.bankedMana ? ` · 库存 ${props.bankedMana}` : ''}`}>
      {crystals.slice(0, props.maxMana).map((filled, i) => (
        <ManaCrystal key={i} filled={filled} />
      ))}
      {!!props.bankedMana &&
        Array.from({ length: Math.min(props.bankedMana, 6) }, (_, i) => (
          <ManaCrystal key={`b${i}`} filled banked />
        ))}
      <span className="muted" style={{ marginLeft: 6, fontSize: '0.8rem' }}>
        {props.mana}/{props.maxMana}
        {props.bankedMana ? `+${props.bankedMana}` : ''}
      </span>
    </div>
  );
}
