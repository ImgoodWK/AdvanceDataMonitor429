import { motion } from 'framer-motion';
import type { CSSProperties, ReactNode } from 'react';

export function CardFrame(props: {
  theme?: string;
  selected?: boolean;
  dragging?: boolean;
  compact?: boolean;
  onClick?: () => void;
  title?: string;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  return (
    <motion.div
      className={`card-frame${props.compact ? ' compact' : ''}${props.selected ? ' selected' : ''}${
        props.dragging ? ' dragging' : ''
      }${props.className ? ` ${props.className}` : ''}`}
      data-theme={props.theme ?? 'vanilla'}
      onClick={props.onClick}
      title={props.title}
      style={props.style}
      whileHover={props.dragging ? undefined : { y: -6, scale: 1.04 }}
      transition={{ type: 'spring', stiffness: 420, damping: 28 }}
    >
      {props.children}
    </motion.div>
  );
}
