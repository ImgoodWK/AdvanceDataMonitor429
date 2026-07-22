import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';
import type { BattleState, CardDef, RunState } from '../api/client';
import { BoardSlot } from '../components/BoardSlot';
import { CardInspector } from '../components/CardInspector';
import { CardView } from '../components/CardView';
import { CombatLog } from '../components/CombatLog';
import { EffectLayer } from '../components/EffectLayer';
import { EndTurnButton } from '../components/EndTurnButton';
import { Hand } from '../components/Hand';
import { Hud } from '../components/Hud';
import { NexusBar } from '../components/Nexus';
import { PhaseBanner } from '../components/PhaseBanner';
import { useBattleAnimations } from '../hooks/useAnimations';
import { useDragPlay } from '../hooks/useDragPlay';
import { buildPlayAction, canPlayToSlot, isPlayWindow } from '../lib/playLegality';
import { resolveSkin } from '../lib/skins';

export function BattleScreen(props: {
  match: BattleState;
  run: RunState | null;
  cardMap: Map<string, CardDef>;
  skinId: string;
  busy: boolean;
  onAction: (action: unknown) => Promise<void>;
  onNextAfterWin: (choiceId: string) => Promise<void>;
  onBackLobby: () => void;
}) {
  const { match, run, cardMap, busy } = props;
  const me = match.players[0];
  const foe = match.players[1];
  const skin = resolveSkin(props.skinId);
  const responseWindow = match.phase === 'spell_response' || match.phase === 'combat_response';
  const hasAttackers = me.board.some((unit) => unit && !unit.isStructure && unit.attack > 0);
  const phaseMessage =
    match.activePlayer !== 0
      ? '等待敌方行动。你仍可检查任意卡牌与战斗队列。'
      : responseWindow
        ? '你拥有响应权：可打出快速或爆发法术，也可放弃响应。'
        : match.phase === 'attack_declare'
          ? '选择至多 6 名非结构单位。空选择会取消攻击且保留攻击标记。'
          : match.phase === 'block_declare'
            ? '为每名攻击者安排至多一名格挡者；被移除的格挡者仍会留下幽灵格挡。'
            : match.phase === 'main'
              ? '你拥有行动权：出牌、发起攻击或放弃行动。'
              : '按当前阶段完成战斗操作。';

  const [selectedHand, setSelectedHand] = useState<number | null>(null);
  const [mulliganSelection, setMulliganSelection] = useState<Set<number>>(new Set());
  const [attackSlots, setAttackSlots] = useState<number[]>([]);
  const [blockAssignments, setBlockAssignments] = useState<Record<number, number>>({});
  const [inspection, setInspection] = useState<{ def?: CardDef; unit?: BattleState['players'][0]['board'][number] }>({
    def: cardMap.get(me.hand.find((cardId) => cardId !== '?') ?? ''),
  });
  const [inspectorPinned, setInspectorPinned] = useState(false);
  const arenaRef = useRef<HTMLDivElement>(null);
  const effectRef = useRef<HTMLDivElement>(null);

  const { drag, beginDrag, hoverSlot, leaveSlot, endDrag } = useDragPlay({
    phase: match.phase,
    me,
    opponent: foe,
    cardMap,
    busy: busy || match.activePlayer !== 0,
    onPlay: props.onAction,
  });

  const { bannerPhase, damages, shake, vignette } = useBattleAnimations({
    match,
    arenaRef: arenaRef as RefObject<HTMLElement | null>,
    effectRef: effectRef as RefObject<HTMLElement | null>,
  });

  useEffect(() => {
    setAttackSlots([]);
    setBlockAssignments({});
    setSelectedHand(null);
    setMulliganSelection(new Set());
  }, [match.turn, match.phase]);

  function inspect(def?: CardDef, unit?: BattleState['players'][0]['board'][number], force = false) {
    if (!inspectorPinned || force) setInspection({ def, unit: unit ?? undefined });
  }

  function selectHand(index: number) {
    setSelectedHand((previous) => (previous === index ? null : index));
    inspect(cardMap.get(me.hand[index] ?? ''), undefined, true);
  }

  async function clickPlayToSlot(slot: number, side: 'player' | 'enemy') {
    if (selectedHand == null || busy || !isPlayWindow(match.phase) || match.activePlayer !== 0) return;
    const legal = canPlayToSlot({
      phase: match.phase,
      me,
      opponent: foe,
      handIndex: selectedHand,
      targetSlot: slot,
      cardMap,
      side,
    });
    if (!legal.ok) return;
    const cardId = me.hand[selectedHand];
    const def = cardId && cardId !== '?' ? cardMap.get(cardId) : undefined;
    await props.onAction(
      buildPlayAction({
        handIndex: selectedHand,
        targetSlot: slot,
        side,
        kind: def?.kind,
      }),
    );
    setSelectedHand(null);
  }

  if (match.phase === 'mulligan') {
    return (
      <div className="battle mulligan-screen">
        <div className="panel mulligan-panel">
          <div>
            <span className="tag">开局调度</span>
            <h2>选择要替换的起手牌</h2>
            <p className="muted">可选择任意张；新牌抽出后，换下的牌才会洗回牌库，因此不会立即抽回同一张。</p>
          </div>
          <div className="mulligan-workspace">
            <div className="mulligan-grid">
              {me.hand.map((cardId, index) => {
                const selected = mulliganSelection.has(index);
                return (
                <button
                  key={`${cardId}-${index}`}
                  type="button"
                  className={`mulligan-card${selected ? ' selected' : ''}`}
                  aria-pressed={selected}
                  disabled={busy || match.mulliganDone[0]}
                  onClick={() =>
                    setMulliganSelection((previous) => {
                      const next = new Set(previous);
                      if (next.has(index)) next.delete(index);
                      else next.add(index);
                      return next;
                    })
                  }
                >
                  <CardView
                    def={cardMap.get(cardId)}
                    selected={selected}
                    onInspect={() => inspect(cardMap.get(cardId))}
                  />
                  <span>{selected ? '将替换' : '保留'}</span>
                </button>
                );
              })}
            </div>
            <CardInspector
              def={inspection.def}
              unit={inspection.unit ?? undefined}
              pinned={inspectorPinned}
              onTogglePin={() => setInspectorPinned((value) => !value)}
            />
          </div>
          <div className="row mulligan-actions">
            <EndTurnButton
              label={match.mulliganDone[0] ? '等待对手确认' : `确认调度 · 替换 ${mulliganSelection.size} 张`}
              primary
              disabled={busy || match.mulliganDone[0]}
              onClick={() =>
                void props.onAction({
                  type: 'confirm_mulligan',
                  replaceIndices: [...mulliganSelection].sort((a, b) => a - b),
                })
              }
            />
            <span className="muted">敌方：{match.mulliganDone[1] ? '已确认' : '选择中'}</span>
          </div>
        </div>
        <CombatLog lines={match.log} />
      </div>
    );
  }

  return (
    <div className={`battle${shake ? ' shake-target' : ''}`}>
      <Hud
        turn={match.turn}
        phase={match.phase}
        meName={me.name || '你'}
        foeName={foe.name || '敌方'}
        meHp={me.nexusHp}
        meMaxHp={me.maxNexusHp}
        foeHp={foe.nexusHp}
        foeMaxHp={foe.maxNexusHp}
        voltage={me.voltage}
        foeVoltage={foe.voltage}
        mana={me.mana}
        maxMana={me.maxMana}
        spellMana={me.spellMana}
        bankedMana={me.bankedMana}
        priorityLabel={match.activePlayer === 0 ? '你' : '敌方'}
        attackTokenLabel={
          match.attackTokenAvailable
            ? match.attackTokenPlayer === 0
              ? '你'
              : '敌方'
            : '本轮已消耗'
        }
        stageLabel={run ? `关卡 ${run.stageIndex + 1}/${run.stages.length} · 胜 ${run.victories}` : undefined}
        eternal={me.eternalActive}
      />

      <aside className="panel battle-status-panel">
        <span className="eyebrow">当前决策</span>
        <h3>{match.activePlayer === 0 ? '你的行动窗口' : '敌方行动窗口'}</h3>
        <p>{phaseMessage}</p>
        <div className="status-pips">
          <span>主行动放弃 {match.consecutivePasses}/2</span>
          <span>响应放弃 {match.responsePasses}/2</span>
          <span>法术栈 {match.spellStack.length}</span>
        </div>
      </aside>

      <aside className="battle-inspector">
        <CardInspector
          def={inspection.def}
          unit={inspection.unit ?? undefined}
          pinned={inspectorPinned}
          onTogglePin={() => setInspectorPinned((value) => !value)}
        />
      </aside>

      <div
        className="battle-arena"
        data-skin={skin.id}
        ref={arenaRef}
        style={
          {
            '--skin-tint': skin.frameTint,
            '--board-bg': skin.boardBg,
            '--board-bg-alt': skin.boardBgAlt,
          } as CSSProperties
        }
      >
        <PhaseBanner phase={bannerPhase} />
        <EffectLayer layerRef={effectRef} damages={damages} vignette={vignette} />

        <div className="muted">敌方场面</div>
        <div className="board-row">
          {foe.board.map((u, i) => (
            <BoardSlot
              key={`e-${i}`}
              index={i}
              unit={u}
              cardMap={cardMap}
              side="enemy"
              attackArmed={match.combatAttacker === 1 && match.attackOrder.includes(i)}
              combatLabel={
                match.combatAttacker === 1 && match.attackOrder.includes(i)
                  ? '攻击'
                  : match.combatAttacker === 0 && match.blockPairs.some((pair) => pair.blockerSlot === i)
                    ? '格挡'
                    : undefined
              }
              dropState={
                drag.dragging && drag.hoverSide === 'enemy' && drag.hoverSlot === i
                  ? drag.dropState
                  : 'none'
              }
              onPointerEnter={() => hoverSlot(i, 'enemy')}
              onPointerLeave={leaveSlot}
              onInspect={(def, unit) => inspect(def, unit)}
              onClick={() => clickPlayToSlot(i, 'enemy')}
            />
          ))}
        </div>

        <NexusBar
          meHp={me.nexusHp}
          meMax={me.maxNexusHp}
          foeHp={foe.nexusHp}
          foeMax={foe.maxNexusHp}
        />

        <div className="muted">己方场面 · 拖到手牌到空槽 / 攻击声明时点击勾选</div>
        <div className="board-row">
          {me.board.map((u, i) => (
            <BoardSlot
              key={`p-${i}`}
              index={i}
              unit={u}
              cardMap={cardMap}
              side="player"
              attackArmed={attackSlots.includes(i)}
              selected={attackSlots.includes(i)}
              combatLabel={
                match.combatAttacker === 0 && match.attackOrder.includes(i)
                  ? '攻击'
                  : match.combatAttacker === 1 && match.blockPairs.some((pair) => pair.blockerSlot === i)
                    ? '格挡'
                    : undefined
              }
              dropState={
                drag.dragging && drag.hoverSide === 'player' && drag.hoverSlot === i
                  ? drag.dropState
                  : 'none'
              }
              onPointerEnter={() => hoverSlot(i, 'player')}
              onPointerLeave={leaveSlot}
              onInspect={(def, unit) => inspect(def, unit)}
              onClick={() => {
                if (match.phase === 'attack_declare' && u && !u.isStructure) {
                  setAttackSlots((prev) =>
                    prev.includes(i) ? prev.filter((x) => x !== i) : [...prev, i],
                  );
                  return;
                }
                clickPlayToSlot(i, 'player');
              }}
            />
          ))}
        </div>
      </div>

      {responseWindow && (
        <div className="panel spell-stack-panel" aria-live="polite">
          <div>
            <span className="tag">{match.phase === 'combat_response' ? '战斗响应' : '法术响应'}</span>
            <h3>{match.spellStack.length > 0 ? `法术栈 · ${match.spellStack.length} 层` : '法术栈为空'}</h3>
            <p className="muted">
              {match.phase === 'combat_response'
                ? '双方完成响应后才结算攻击；快速法术可入栈，爆发法术立即生效。'
                : '双方连续放弃响应后，法术按栈顶到栈底的顺序结算。'}
            </p>
          </div>
          <ol className="spell-stack-list">
            {[...match.spellStack].reverse().map((item, index) => {
              const def = cardMap.get(item.cardId);
              return (
                <li key={item.stackId} className="spell-stack-item">
                  <span className="stack-position">{index === 0 ? '栈顶' : `第 ${index + 1} 层`}</span>
                  <span>{def?.nameZh ?? item.cardId}</span>
                  <span className="muted">{item.caster === 0 ? '你' : '敌方'} · {item.speed === 'fast' ? '快速' : '慢速'}</span>
                </li>
              );
            })}
          </ol>
          <span className="muted">响应放弃：{match.responsePasses}/2</span>
        </div>
      )}

      <div className="panel command-deck-panel">
        <Hand
          hand={me.hand}
          cardMap={cardMap}
          selectedHand={selectedHand}
          draggingIndex={drag.handIndex}
          disabled={busy || !isPlayWindow(match.phase) || match.activePlayer !== 0}
          onSelect={selectHand}
          onInspect={(def) => inspect(def)}
          onDragStart={beginDrag}
          onDragEnd={() => {
            void endDrag();
          }}
        />

        {selectedHand != null && isPlayWindow(match.phase) && match.activePlayer === 0 && (
          <div className="row fallback-play">
            {me.board.map((u, i) => (
              <EndTurnButton
                key={i}
                label={`出到槽 ${i}`}
                disabled={busy || u != null}
                onClick={() => void clickPlayToSlot(i, 'player')}
              />
            ))}
            <EndTurnButton
              label="打出法术（默认目标）"
              disabled={busy}
              primary
              onClick={() => void clickPlayToSlot(0, 'enemy')}
            />
          </div>
        )}

        {match.phase === 'block_declare' && match.combatAttacker === 1 && (
          <div className="block-panel">
            <h3>安排格挡</h3>
            <p className="muted">每名防守单位只能格挡一次；不选择即由该攻击者直击 Nexus。</p>
            <div className="block-grid">
              {match.attackOrder.map((attackerSlot) => {
                const attacker = foe.board[attackerSlot];
                return (
                  <label key={attackerSlot} className="block-assignment">
                    敌方槽 {attackerSlot}（{attacker?.attack ?? 0} 攻）
                    <select
                      value={blockAssignments[attackerSlot] ?? -1}
                      onChange={(e) => {
                        const blockerSlot = Number(e.target.value);
                        setBlockAssignments((prev) => ({ ...prev, [attackerSlot]: blockerSlot }));
                      }}
                    >
                      <option value={-1}>不格挡</option>
                      {me.board.map((unit, blockerSlot) => {
                        if (!unit || unit.isStructure || unit.untargetable) return null;
                        if (attacker?.keywords.includes('stealth') && !unit.keywords.includes('stealth')) return null;
                        const usedElsewhere = Object.entries(blockAssignments).some(
                          ([slot, selected]) => Number(slot) !== attackerSlot && selected === blockerSlot,
                        );
                        return (
                          <option key={blockerSlot} value={blockerSlot} disabled={usedElsewhere}>
                            己方槽 {blockerSlot}（{unit.attack}/{unit.health}）
                          </option>
                        );
                      })}
                    </select>
                  </label>
                );
              })}
            </div>
          </div>
        )}

        <div className="action-bar" style={{ marginTop: '0.75rem' }}>
          <div className="row contextual-actions">
            {match.phase === 'main' && (
              <>
                {match.attackTokenAvailable && match.attackTokenPlayer === 0 && (
                  <EndTurnButton
                    label="发起攻击"
                    disabled={busy || match.activePlayer !== 0 || !hasAttackers}
                    primary
                    onClick={() => void props.onAction({ type: 'start_attack' })}
                  />
                )}
                <EndTurnButton
                  label={match.consecutivePasses ? '再次放弃 · 结束轮次' : '放弃行动权'}
                  disabled={busy || match.activePlayer !== 0}
                  onClick={() => void props.onAction({ type: 'pass_priority' })}
                />
              </>
            )}
            {match.phase === 'attack_declare' && (
              <EndTurnButton
                label={attackSlots.length ? `确认攻击 · ${attackSlots.length} 名` : '取消攻击'}
                disabled={busy}
                primary={attackSlots.length > 0}
                onClick={() => void props.onAction({ type: 'declare_attacks', slots: attackSlots })}
              />
            )}
            {match.phase === 'block_declare' && match.combatAttacker === 1 && (
              <>
                <EndTurnButton
                  label="确认格挡"
                  disabled={busy}
                  primary
                  onClick={() =>
                    void props.onAction({
                      type: 'declare_blocks',
                      pairs: match.attackOrder.map((attackerSlot) => ({
                        attackerSlot,
                        blockerSlot: blockAssignments[attackerSlot] ?? -1,
                      })),
                    })
                  }
                />
                <EndTurnButton
                  label="全部放行"
                  disabled={busy}
                  onClick={() => void props.onAction({ type: 'pass_block' })}
                />
              </>
            )}
            {responseWindow && (
              <EndTurnButton
                label={
                  match.responsePasses
                    ? match.phase === 'combat_response'
                      ? '确认无响应 · 结算战斗'
                      : '确认无响应 · 结算法术栈'
                    : '放弃响应'
                }
                disabled={busy || match.activePlayer !== 0}
                primary={match.responsePasses > 0}
                onClick={() => void props.onAction({ type: 'pass_priority' })}
              />
            )}
            {match.phase === 'swap_extra' && (
              <>
                <EndTurnButton
                  label="神秘换位 0 ↔ 1"
                  disabled={busy}
                  primary
                  onClick={() => void props.onAction({ type: 'swap_slots', a: 0, b: 1 })}
                />
                <EndTurnButton
                  label="跳过换位"
                  disabled={busy}
                  onClick={() => void props.onAction({ type: 'pass_swap' })}
                />
              </>
            )}
            {match.activePlayer !== 0 && <span className="priority-wait">敌方正在思考…</span>}
          </div>
          <button className="danger secondary" disabled={busy} onClick={() => void props.onAction({ type: 'concede' })}>
            认输
          </button>
        </div>
      </div>

      <CombatLog lines={match.log} />

      {match.phase === 'game_over' && (
        <div className="game-over-overlay">
          <div className="panel game-over-card">
            <h2>{match.winner === 0 ? '胜利！' : '失败'}</h2>
            {match.winner === 0 && run?.pendingChoice && (
              <div className="row" style={{ justifyContent: 'center', marginTop: '0.75rem' }}>
                {run.pendingChoice.map((c) => (
                  <button key={c.id} disabled={busy} onClick={() => void props.onNextAfterWin(c.id)}>
                    {c.labelZh}
                    {c.items[0]?.displayName ? ` · ${c.items[0].displayName}` : ''}
                  </button>
                ))}
              </div>
            )}
            {(match.winner !== 0 || run?.completed) && (
              <button style={{ marginTop: '0.75rem' }} onClick={props.onBackLobby}>
                返回大厅
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
