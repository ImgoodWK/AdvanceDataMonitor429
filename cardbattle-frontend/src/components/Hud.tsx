import { PHASE_ZH } from '../lib/themeTokens';
import { ManaRow } from './ManaCrystal';

export function Hud(props: {
  turn: number;
  phase: string;
  meName: string;
  foeName: string;
  meHp: number;
  meMaxHp: number;
  foeHp: number;
  foeMaxHp: number;
  voltage: string;
  foeVoltage: string;
  mana: number;
  maxMana: number;
  bankedMana: number;
  stageLabel?: string;
  eternal?: boolean;
}) {
  return (
    <div className="panel glass hud">
      <span className="tag">回合 {props.turn}</span>
      <span className="tag">{PHASE_ZH[props.phase] ?? props.phase}</span>
      <span className="tag">
        {props.meName} · {props.voltage} · Nexus {props.meHp}/{props.meMaxHp}
      </span>
      <span className="tag">
        {props.foeName} · {props.foeVoltage} · Nexus {props.foeHp}/{props.foeMaxHp}
      </span>
      {props.stageLabel && <span className="tag">{props.stageLabel}</span>}
      {props.eternal && <span className="tag">无尽贪婪已激活</span>}
      <ManaRow mana={props.mana} maxMana={props.maxMana} bankedMana={props.bankedMana} />
    </div>
  );
}
