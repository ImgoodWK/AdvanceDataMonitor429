import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';
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
  onInspect?: () => void;
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
  const [artFailed, setArtFailed] = useState(false);
  useEffect(() => setArtFailed(false), [artUrl]);
  const kws = unit?.keywords ?? def?.keywords ?? [];
  const speedLabel =
    def?.kind === 'spell'
      ? def.spellSpeed === 'fast'
        ? '快速'
        : def.spellSpeed === 'burst'
          ? '爆发'
          : '慢速'
      : null;

  return (
    <CardFrame
      theme={theme}
      selected={props.selected}
      dragging={props.dragging}
      compact={props.compact}
      onClick={props.onClick}
      onPointerEnter={props.onInspect}
      onFocus={props.onInspect}
      ariaLabel={def ? `${def.nameZh}：${def.rulesZh ?? def.textZh ?? '无额外效果'}` : title}
      title={def?.rulesZh ?? def?.textZh}
      className={props.className}
      style={props.style}
    >
      <div className={`art${artUrl && !artFailed ? '' : ' placeholder'}`}>
        {artUrl && !artFailed && (
          <img src={artUrl} alt="" draggable={false} onError={() => setArtFailed(true)} />
        )}
      </div>
      <div className="art-vignette" />
      {cost != null && <div className="cost">{cost}</div>}
      {speedLabel && <div className={`spell-speed ${def?.spellSpeed ?? 'slow'}`}>{speedLabel}</div>}
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
