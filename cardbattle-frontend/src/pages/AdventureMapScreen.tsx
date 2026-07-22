import type { RunState } from '../api/client';
import { THEME_ZH, type ThemeId } from '../lib/themeTokens';

export function AdventureMapScreen(props: {
  run: RunState;
  busy: boolean;
  onEnterStage: (stageId: string) => void;
  onAbandon: () => void;
}) {
  const columns = [...new Set(props.run.stages.map((stage) => stage.column))].sort((a, b) => a - b);
  return (
    <div className="panel adventure-map">
      <div className="row adventure-summary">
        <div>
          <h2>GTNH 英雄之路</h2>
          <p className="muted">选择可达节点。路线选择会锁定本列的另一条分支，精英与首领拥有额外强化。</p>
        </div>
        <div className="run-stats">
          <span className="tag">胜利 {props.run.victories}</span>
          <span className="tag">卡组 {props.run.deck.length}</span>
          <span className="tag">能力 {props.run.powers.length}</span>
          <span className="tag">电压 {props.run.voltage}</span>
        </div>
      </div>

      <div className="route-columns">
        {columns.map((column) => (
          <div className="route-column" key={column}>
            <div className="muted">第 {column + 1} 层</div>
            {props.run.stages
              .filter((stage) => stage.column === column)
              .map((stage) => {
                const available = props.run.availableStageIds.includes(stage.id);
                const completed = props.run.completedStageIds.includes(stage.id);
                const locked = !available && !completed;
                return (
                  <button
                    type="button"
                    key={stage.id}
                    className={`route-node ${stage.kind}${available ? ' available' : ''}${completed ? ' completed' : ''}`}
                    disabled={props.busy || locked || completed}
                    onClick={() => props.onEnterStage(stage.id)}
                  >
                    <span>{stage.kind === 'boss' ? '◆ 首领' : stage.kind === 'elite' ? '▲ 精英' : '● 战斗'}</span>
                    <strong>{stage.nameZh}</strong>
                    <span className="muted">
                      {THEME_ZH[stage.aiThemes[0] as ThemeId] ?? stage.aiThemes[0]} · {stage.aiVoltage}
                    </span>
                    <span>{completed ? '已完成' : available ? '可进入' : '未接通'}</span>
                  </button>
                );
              })}
          </div>
        ))}
      </div>

      {!!props.run.powers.length && (
        <p className="muted">本次能力：{props.run.powers.join(' · ')}</p>
      )}
      <button className="danger secondary" disabled={props.busy} onClick={props.onAbandon}>
        放弃本次冒险
      </button>
    </div>
  );
}
