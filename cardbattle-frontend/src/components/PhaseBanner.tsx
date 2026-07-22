import { AnimatePresence, motion } from 'framer-motion';
import { PHASE_ZH } from '../lib/themeTokens';

export function PhaseBanner(props: { phase: string | null }) {
  const text = props.phase ? PHASE_ZH[props.phase] ?? props.phase : null;
  return (
    <AnimatePresence>
      {text && (
        <motion.div
          className="phase-banner"
          key={text}
          initial={{ opacity: 0, y: -24, scale: 0.9 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: 12, scale: 0.95 }}
          transition={{ duration: 0.35 }}
        >
          {text}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
