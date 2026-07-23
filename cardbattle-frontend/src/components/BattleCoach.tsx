import type { BattleState, CardDef } from '../api/client';
import { needsEnemyTarget, needsFriendlyTarget, spellTargetKind } from '../lib/playLegality';

export const COACH_STORAGE_KEY = 'textech_cb_coach';

export function isCoachEnabled(): boolean {
  const raw = localStorage.getItem(COACH_STORAGE_KEY);
  if (raw == null) return true;
  return raw !== '0';
}

export function setCoachEnabled(enabled: boolean): void {
  localStorage.setItem(COACH_STORAGE_KEY, enabled ? '1' : '0');
}

function playableUnits(board: BattleState['players'][0]['board']): number {
  return board.filter((u) => u && !u.isStructure && u.attack > 0).length;
}

export function battleCoachTip(args: {
  match: BattleState;
  cardMap: Map<string, CardDef>;
  attackDraftCount?: number;
}): string {
  const { match } = args;
  const me = match.players[0];
  const foe = match.players[1];
  const myTurn = match.activePlayer === 0;

  if (match.phase === 'mulligan') {
    return '调度：换掉高费或暂时用不上的牌，保留能前期站场的单位。';
  }
  if (match.phase === 'game_over') {
    return match.winner === 0 ? '对局结束 — 胜利！' : '对局结束 — 下次再试。';
  }
  if (!myTurn) {
    return '等待敌方行动；可查看场面与战斗队列。';
  }
  if (match.phase === 'spell_response' || match.phase === 'combat_response') {
    const fastInHand = me.hand.some((id) => {
      const def = id !== '?' ? args.cardMap.get(id) : undefined;
      return def?.kind === 'spell' && (def.spellSpeed ?? 'slow') !== 'slow';
    });
    return fastInHand
      ? '响应窗口：可拖快速/爆发法术入栈，或放弃响应。'
      : '响应窗口：手牌无可用响应，建议放弃响应。';
  }
  if (match.phase === 'attack_declare') {
    const n = args.attackDraftCount ?? 0;
    return n
      ? `已选 ${n} 名攻击者 — 拖到战场调整顺序，拖回备战区可移除；确认或空选取消。`
      : '将备战区单位拖到战场行以声明攻击；空选会取消并保留攻击标记。';
  }
  if (match.phase === 'block_declare' && match.combatAttacker === 1) {
    return '将己方单位拖到敌方战场上的攻击者以格挡；每名防守者只能格挡一次。';
  }
  if (match.phase === 'swap_extra') {
    return '神秘换位：依次点击两个备战区槽位完成交换，或跳过。';
  }
  if (match.phase === 'main') {
    const units = playableUnits(me.board);
    if (match.attackTokenAvailable && match.attackTokenPlayer === 0 && units > 0) {
      return '主阶段：可出牌到备战区，或发起攻击消耗攻击标记。';
    }
    const spellNeedTarget = me.hand.some((id) => {
      const def = id !== '?' ? args.cardMap.get(id) : undefined;
      return def?.kind === 'spell' && spellTargetKind(def) !== 'none';
    });
    if (spellNeedTarget) {
      const enemy = me.hand.some((id) => {
        const def = id !== '?' ? args.cardMap.get(id) : undefined;
        return def && needsEnemyTarget(def);
      });
      const friendly = me.hand.some((id) => {
        const def = id !== '?' ? args.cardMap.get(id) : undefined;
        return def && needsFriendlyTarget(def);
      });
      if (enemy && friendly) return '拖法术到对应单位槽；敌我目标需落在正确备战区槽位。';
      if (enemy) return '伤害类法术：拖到敌方备战区上的目标单位。';
      if (friendly) return '友方指向法术：拖到我方备战区上的目标单位。';
    }
    if (firstEmptyBench(me.board) >= 0) {
      return '拖单位/建筑到己方备战区空位（不必精确对准某一槽）。';
    }
    if (foe.nexusHp <= 5) {
      return '备战区已满 — 考虑发起攻击或结束回合。';
    }
    return '主阶段：出牌、攻击或放弃行动权。';
  }
  return '按当前阶段完成操作。';
}

function firstEmptyBench(board: BattleState['players'][0]['board']): number {
  for (let i = 0; i < board.length; i++) {
    if (board[i] == null) return i;
  }
  return -1;
}

export function BattleCoach(props: { tip: string }) {
  return (
    <div className="battle-coach" role="status" aria-live="polite">
      <span className="battle-coach-label">提示</span>
      <span className="battle-coach-text">{props.tip}</span>
    </div>
  );
}
