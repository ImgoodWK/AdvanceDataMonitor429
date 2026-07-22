export function Nexus(props: {
  label: string;
  hp: number;
  maxHp: number;
  foe?: boolean;
}) {
  const pct = Math.max(0, Math.min(100, (props.hp / Math.max(1, props.maxHp)) * 100));
  return (
    <div className="nexus-row">
      <span>{props.label}</span>
      <div className="nexus-track">
        <div className={`nexus-fill${props.foe ? ' foe' : ''}`} style={{ width: `${pct}%` }} />
      </div>
      <span>
        {props.hp}/{props.maxHp}
      </span>
    </div>
  );
}

export function NexusBar(props: {
  meHp: number;
  meMax: number;
  foeHp: number;
  foeMax: number;
}) {
  return (
    <div className="nexus-bar">
      <Nexus label="敌方 Nexus" hp={props.foeHp} maxHp={props.foeMax} foe />
      <Nexus label="己方 Nexus" hp={props.meHp} maxHp={props.meMax} />
    </div>
  );
}
