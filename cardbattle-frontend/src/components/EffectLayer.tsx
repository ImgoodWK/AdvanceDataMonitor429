import type { CSSProperties, RefObject } from 'react';
import { DamageNumberLayer } from './DamageNumber';
import type { FloatingDamage } from '../hooks/useAnimations';

export function EffectLayer(props: {
  layerRef: RefObject<HTMLDivElement | null>;
  damages: FloatingDamage[];
  vignette: boolean;
}) {
  return (
    <div className="effect-layer" ref={props.layerRef as RefObject<HTMLDivElement>}>
      <div
        className="vignette-hit"
        style={{ opacity: props.vignette ? 1 : 0, transition: 'opacity 0.2s' } as CSSProperties}
      />
      <DamageNumberLayer items={props.damages} />
    </div>
  );
}
