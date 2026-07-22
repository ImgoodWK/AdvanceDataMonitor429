import type { CSSProperties } from 'react';
import type { CardDef, BoardUnit } from '../api/client';
import { CardFrame } from './CardFrame';

export function CardView(props: {
  def?: CardDef;
  unit?: BoardUnit;
  selected?: boolean;
  dragging?: boolean;
  onClick?: () => void;
  compact?: boolean;
  className?: string;
  style?: CSSProperties;
}) {
  const def = props.def;
  const unit = props.unit;
  const theme = def?.theme ?? 'vanilla';
  const title = def?.nameZh ?? unit?.cardId ?? '?';
  const cost = def?.cost;
  const atk = unit?.attack ?? def?.attack;
  const hp = unit?.health ?? def?.health;
  const artUrl = def?.art
    ? `/card-art/${def.art}`
    : def?.id
      ? `/card-art/${def.id}.png`
      : null;
  const kws = unit?.keywords ?? def?.keywords ?? [];

  return (
    <CardFrame
      theme={theme}
      selected={props.selected}
      dragging={props.dragging}
      compact={props.compact}
      onClick={props.onClick}
      title={def?.textZh}
      className={props.className}
      style={props.style}
    >
      <div
        className={`art${artUrl ? '' : ' placeholder'}`}
        style={artUrl ? { backgroundImage: `url(${artUrl})` } : undefined}
      />
      <div className="art-vignette" />
      {cost != null && <div className="cost">{cost}</div>}
      <div className="layer">
        <div className="title">{title}</div>
        {kws.length > 0 && <div className="kw">{kws.join(' · ')}</div>}
        {(unit?.aspects?.length ?? 0) > 0 && <div className="kw">{unit!.aspects.join('+')}</div>}
        {(atk != null || hp != null) && (
          <div className="stats">
            <span className="stat-atk">{atk ?? '-'}</span>
            <span className="stat-hp">
              {unit?.armor ? `${unit.armor}/` : ''}
              {hp ?? '-'}
            </span>
          </div>
        )}
      </div>
    </CardFrame>
  );
}
