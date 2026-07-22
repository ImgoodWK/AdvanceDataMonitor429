import { motion } from 'framer-motion';

export function EndTurnButton(props: {
  label: string;
  disabled?: boolean;
  onClick: () => void;
  primary?: boolean;
}) {
  return (
    <motion.button
      className={props.primary ? undefined : 'secondary'}
      disabled={props.disabled}
      onClick={props.onClick}
      whileTap={props.disabled ? undefined : { scale: 0.96 }}
    >
      {props.label}
    </motion.button>
  );
}
