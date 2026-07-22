export function CombatLog(props: { lines: string[] }) {
  return <div className="panel log">{props.lines.slice(-14).join('\n')}</div>;
}
