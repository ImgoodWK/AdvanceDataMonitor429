import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type RefObject } from 'react';
import type { BattleState, BoardUnit, CardDef, PlayerState, RunState } from '../api/client';
import { BattleCoach, battleCoachTip, isCoachEnabled } from '../components/BattleCoach';
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
import {
  buildPlayAction,
  canPlayToBench,
  canPlayToSlot,
  firstEmptyBenchSlot,
  isPlayWindow,
  parseDropFromElement,
  type DropTarget,
} from '../lib/playLegality';
import { resolveSkin } from '../lib/skins';

function findSlotById(board: (BoardUnit | null)[], id: string): number {
  return board.findIndex((u) => u?.instanceId === id);
}

function unitById(board: (BoardUnit | null)[], id: string): BoardUnit | null {
  const slot = findSlotById(board, id);
  return slot >= 0 ? board[slot] : null;
}

function canUnitAttack(unit: BoardUnit | null): boolean {
  return Boolean(unit && !unit.isStructure && unit.attack > 0);
}

function canBlock(attacker: BoardUnit | null, blocker: BoardUnit | null): boolean {
  if (!attacker || !blocker) return false;
  if (blocker.isStructure || blocker.untargetable) return false;
  if (attacker.keywords.includes('stealth') && !blocker.keywords.includes('stealth')) return false;
  return true;
}

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
  const [coachOn] = useState(isCoachEnabled);

  const attackOrderIds =
    match.phase === 'attack_declare'
      ? undefined
      : match.attackOrderIds && match.attackOrderIds.length
        ? match.attackOrderIds
        : match.attackOrder
            .map((slot) => {
              const side = match.combatAttacker === 0 ? me.board : foe.board;
              return side[slot]?.instanceId;
            })
            .filter((id): id is string => Boolean(id));

  const [attackDraftIds, setAttackDraftIds] = useState<string[]>([]);
  const [blockByAttacker, setBlockByAttacker] = useState<Record<string, string>>({});
  const [swapPick, setSwapPick] = useState<number | null>(null);
  const [unitDragId, setUnitDragId] = useState<string | null>(null);
  const unitDragRef = useRef<string | null>(null);
  unitDragRef.current = unitDragId;

  const [selectedHand, setSelectedHand] = useState<number | null>(null);
  const [mulliganSelection, setMulliganSelection] = useState<Set<number>>(new Set());
  const [inspection, setInspection] = useState<{ def?: CardDef; unit?: BattleState['players'][0]['board'][number] }>({
    def: cardMap.get(me.hand.find((cardId) => cardId !== '?') ?? ''),
  });
  const [inspectorPinned, setInspectorPinned] = useState(false);
  const arenaRef = useRef<HTMLDivElement>(null);
  const effectRef = useRef<HTMLDivElement>(null);

  const { drag, beginDrag, hoverDrop, leaveDrop, endDrag, dropStateFor } = useDragPlay({
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
    setAttackDraftIds([]);
    setBlockByAttacker({});
    setSwapPick(null);
    setSelectedHand(null);
    setMulliganSelection(new Set());
    setUnitDragId(null);
  }, [match.turn, match.phase]);

  const activeAttackIds = match.phase === 'attack_declare' ? attackDraftIds : attackOrderIds ?? [];

  const coachTip = useMemo(
    () =>
      battleCoachTip({
        match,
        cardMap,
        attackDraftCount: match.phase === 'attack_declare' ? attackDraftIds.length : activeAttackIds.length,
      }),
    [match, cardMap, attackDraftIds.length, activeAttackIds.length],
  );

  const handleUnitDrop = useCallback(
    (instanceId: string, target: DropTarget | null, clientX: number, clientY: number) => {
      if (!target) {
        const el = document.elementFromPoint(clientX, clientY);
        target = parseDropFromElement(el);
      }
      if (!target) return;

      if (match.phase === 'attack_declare' && match.combatAttacker === 0) {
        const unit = unitById(me.board, instanceId);
        if (!canUnitAttack(unit)) return;

        if (target.kind === 'bench' && target.side === 'player') {
          setAttackDraftIds((prev) => prev.filter((id) => id !== instanceId));
          return;
        }
        if (target.kind === 'battlefield' && target.side === 'player') {
          setAttackDraftIds((prev) => {
            const without = prev.filter((id) => id !== instanceId);
            if (without.length >= 6) return prev;
            if (target.slot != null && Number.isInteger(target.slot)) {
              const next = [...without];
              next.splice(Math.min(target.slot, next.length), 0, instanceId);
              return next.slice(0, 6);
            }
            if (prev.includes(instanceId)) return prev;
            return [...without, instanceId].slice(0, 6);
          });
          return;
        }
        if (target.kind === 'unit' && target.side === 'player' && target.slot != null) {
          setAttackDraftIds((prev) => {
            const idx = target.slot!;
            const without = prev.filter((id) => id !== instanceId);
            const next = [...without];
            next.splice(Math.min(idx, next.length), 0, instanceId);
            return next.slice(0, 6);
          });
        }
        return;
      }

      if (match.phase === 'block_declare' && match.combatAttacker === 1 && target.kind === 'blocker') {
        const attackerId = target.attackerInstanceId;
        if (!attackerId) return;
        const blocker = unitById(me.board, instanceId);
        const attacker = unitById(foe.board, attackerId);
        if (!canBlock(attacker, blocker)) return;
        const usedElsewhere = Object.entries(blockByAttacker).some(
          ([aid, bid]) => aid !== attackerId && bid === instanceId,
        );
        if (usedElsewhere) return;
        setBlockByAttacker((prev) => ({ ...prev, [attackerId]: instanceId }));
      }
    },
    [match.phase, match.combatAttacker, me.board, foe.board, blockByAttacker],
  );

  useEffect(() => {
    if (!unitDragId) return;
    const onUp = (e: PointerEvent) => {
      const id = unitDragRef.current;
      if (!id) return;
      handleUnitDrop(id, null, e.clientX, e.clientY);
      setUnitDragId(null);
    };
    document.addEventListener('pointerup', onUp);
    return () => document.removeEventListener('pointerup', onUp);
  }, [unitDragId, handleUnitDrop]);

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
    if (!legal.ok) {
      if (side === 'player') {
        const benchOk = canPlayToBench({
          phase: match.phase,
          me,
          handIndex: selectedHand,
          cardMap,
        });
        if (!benchOk.ok) return;
        const cardId = me.hand[selectedHand];
        const def = cardId && cardId !== '?' ? cardMap.get(cardId) : undefined;
        await props.onAction(buildPlayAction({ handIndex: selectedHand, side: 'player', kind: def?.kind }));
        setSelectedHand(null);
      }
      return;
    }
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

  function hoverTarget(target: DropTarget) {
    hoverDrop(target);
  }

  function renderBattlefieldRow(side: 'player' | 'enemy', ids: string[], board: PlayerState['board']) {
    const isEnemy = side === 'enemy';
    const slots = Array.from({ length: 6 }, (_, i) => ids[i] ?? null);
    return (
      <div
        className="battlefield-row"
        data-drop={`battlefield:${side}`}
        onPointerEnter={() => hoverTarget({ kind: 'battlefield', side })}
        onPointerLeave={leaveDrop}
      >
        {slots.map((instanceId, i) => {
          const unit = instanceId ? unitById(board, instanceId) : null;
          const attackerId = instanceId ?? undefined;
          const dropKey: DropTarget =
            match.phase === 'block_declare' && isEnemy && attackerId
              ? { kind: 'blocker', attackerInstanceId: attackerId }
              : { kind: 'battlefield', side, slot: i };
          const dropAttr =
            match.phase === 'block_declare' && isEnemy && attackerId
              ? `blocker:${attackerId}`
              : `battlefield:${side}:${i}`;
          const blockerId = attackerId ? blockByAttacker[attackerId] : undefined;
          const blockerUnit = blockerId ? unitById(me.board, blockerId) : null;
          return (
            <div key={`bf-${side}-${i}`} className="battlefield-cell">
              <BoardSlot
                index={i}
                unit={unit}
                cardMap={cardMap}
                side={side}
                row="battlefield"
                dataDrop={dropAttr}
                attackOrderIndex={unit && instanceId ? ids.indexOf(instanceId) + 1 : undefined}
                dropState={dropStateFor(dropKey)}
                attackArmed={Boolean(instanceId && activeAttackIds.includes(instanceId))}
                combatLabel={
                  unit && match.combatAttacker === (isEnemy ? 0 : 1) && activeAttackIds.includes(instanceId ?? '')
                    ? '攻击'
                    : undefined
                }
                onPointerEnter={() => hoverTarget(dropKey)}
                onPointerLeave={leaveDrop}
                onUnitPointerDown={
                  match.phase === 'attack_declare' && !isEnemy && unit
                    ? (e) => {
                        e.preventDefault();
                        setUnitDragId(unit.instanceId);
                      }
                    : undefined
                }
                onInspect={(def, u) => inspect(def, u)}
              />
              {blockerUnit && isEnemy && match.phase === 'block_declare' && (
                <span className="blocker-link-badge">← {blockerUnit.attack}/{blockerUnit.health}</span>
              )}
            </div>
          );
        })}
      </div>
    );
  }

  function renderBenchRow(side: 'player' | 'enemy', board: PlayerState['board']) {
    const isPlayer = side === 'player';
    const hideOnBattlefield =
      match.phase === 'attack_declare' && isPlayer ? new Set(attackDraftIds) : new Set<string>();
    return (
      <div
        className="bench-row"
        data-drop={`bench:${side}`}
        onPointerEnter={() => hoverTarget({ kind: 'bench', side })}
        onPointerLeave={leaveDrop}
      >
        {board.map((u, i) => {
          const hidden = u && hideOnBattlefield.has(u.instanceId);
          const dropKey: DropTarget = { kind: 'unit', side, slot: i };
          return (
            <BoardSlot
              key={u ? u.instanceId : `empty-${side}-${i}`}
              index={i}
              unit={hidden ? null : u}
              cardMap={cardMap}
              side={side}
              row="bench"
              dataDrop={`unit:${side}:${i}`}
              dimmed={Boolean(u && hideOnBattlefield.has(u.instanceId))}
              dropState={dropStateFor(dropKey)}
              selected={swapPick === i && isPlayer}
              onPointerEnter={() => hoverTarget(dropKey)}
              onPointerLeave={leaveDrop}
              onUnitPointerDown={
                isPlayer && u
                  ? (e) => {
                      if (match.phase === 'attack_declare' && canUnitAttack(u)) {
                        e.preventDefault();
                        setUnitDragId(u.instanceId);
                        return;
                      }
                      if (match.phase === 'block_declare' && match.combatAttacker === 1 && !u.isStructure) {
                        e.preventDefault();
                        setUnitDragId(u.instanceId);
                      }
                    }
                  : undefined
              }
              onInspect={(def, unit) => inspect(def, unit)}
              onClick={() => {
                if (match.phase === 'swap_extra' && isPlayer) {
                  if (swapPick == null) {
                    setSwapPick(i);
                    return;
                  }
                  if (swapPick === i) {
                    setSwapPick(null);
                    return;
                  }
                  void props.onAction({ type: 'swap_slots', a: swapPick, b: i });
                  setSwapPick(null);
                  return;
                }
                if (match.phase === 'attack_declare' && u && canUnitAttack(u)) {
                  setAttackDraftIds((prev) =>
                    prev.includes(u.instanceId)
                      ? prev.filter((id) => id !== u.instanceId)
                      : [...prev, u.instanceId].slice(0, 6),
                  );
                  return;
                }
                if (isPlayer) clickPlayToSlot(i, 'player');
              }}
            />
          );
        })}
      </div>
    );
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

  const enemyAttackIds =
    match.combatAttacker === 1 ? activeAttackIds : [];
  const playerAttackIds =
    match.combatAttacker === 0 ? activeAttackIds : match.combatAttacker === 1 ? [] : activeAttackIds;

  return (
    <div className={`battle${shake ? ' shake-target' : ''}${unitDragId ? ' unit-dragging' : ''}`}>
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

      {coachOn && <BattleCoach tip={coachTip} />}

      <aside className="panel battle-status-panel">
        <span className="eyebrow">当前决策</span>
        <h3>{match.activePlayer === 0 ? '你的行动窗口' : '敌方行动窗口'}</h3>
        <p>{coachTip}</p>
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

        <div className="board-side board-side-enemy">
          <div className="row-label muted">敌方 · 备战区</div>
          {renderBenchRow('enemy', foe.board)}
          <div className="row-label muted">敌方 · 战场</div>
          {renderBattlefieldRow('enemy', enemyAttackIds, foe.board)}
        </div>

        <NexusBar meHp={me.nexusHp} meMax={me.maxNexusHp} foeHp={foe.nexusHp} foeMax={foe.maxNexusHp} />

        <div className="board-side board-side-player">
          <div className="row-label muted">己方 · 战场</div>
          {renderBattlefieldRow('player', playerAttackIds, me.board)}
          <div className="row-label muted">己方 · 备战区</div>
          {renderBenchRow('player', me.board)}
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
          onDragEnd={(x, y) => {
            void endDrag(x, y);
          }}
        />

        {selectedHand != null && isPlayWindow(match.phase) && match.activePlayer === 0 && (
          <div className="row fallback-play">
            <EndTurnButton
              label="出到备战区"
              disabled={busy || firstEmptyBenchSlot(me.board) < 0}
              primary
              onClick={() => {
                const cardId = me.hand[selectedHand];
                const def = cardId && cardId !== '?' ? cardMap.get(cardId) : undefined;
                void props.onAction(
                  buildPlayAction({ handIndex: selectedHand, side: 'player', kind: def?.kind }),
                );
                setSelectedHand(null);
              }}
            />
            <EndTurnButton
              label="打出法术（默认目标）"
              disabled={busy}
              onClick={() => void clickPlayToSlot(0, 'enemy')}
            />
          </div>
        )}

        {match.phase === 'block_declare' && match.combatAttacker === 1 && (
          <div className="block-panel block-panel-minimal">
            <p className="muted">拖备战区单位到敌方战场攻击者上格挡；也可在下方选手动备选。</p>
            <details>
              <summary>手动格挡（备选）</summary>
              <div className="block-grid">
                {(match.attackOrderIds ?? match.attackOrder.map((s) => foe.board[s]?.instanceId).filter(Boolean)).map(
                  (attackerId) => {
                    if (!attackerId) return null;
                    const attackerSlot = findSlotById(foe.board, attackerId);
                    const attacker = foe.board[attackerSlot];
                    return (
                      <label key={attackerId} className="block-assignment">
                        攻击者 #{activeAttackIds.indexOf(attackerId) + 1}
                        <select
                          value={blockByAttacker[attackerId] ?? ''}
                          onChange={(e) => {
                            const val = e.target.value;
                            setBlockByAttacker((prev) => {
                              const next = { ...prev };
                              if (!val) delete next[attackerId];
                              else next[attackerId] = val;
                              return next;
                            });
                          }}
                        >
                          <option value="">不格挡</option>
                          {me.board.map((unit) => {
                            if (!unit || unit.isStructure || unit.untargetable) return null;
                            if (!canBlock(attacker, unit)) return null;
                            const usedElsewhere = Object.entries(blockByAttacker).some(
                              ([aid, bid]) => aid !== attackerId && bid === unit.instanceId,
                            );
                            return (
                              <option key={unit.instanceId} value={unit.instanceId} disabled={usedElsewhere}>
                                {cardMap.get(unit.cardId)?.nameZh ?? unit.cardId} ({unit.attack}/{unit.health})
                              </option>
                            );
                          })}
                        </select>
                      </label>
                    );
                  },
                )}
              </div>
            </details>
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
                label={attackDraftIds.length ? `确认攻击 · ${attackDraftIds.length} 名` : '取消攻击'}
                disabled={busy}
                primary={attackDraftIds.length > 0}
                onClick={() =>
                  void props.onAction({
                    type: 'declare_attacks',
                    instanceIds: attackDraftIds,
                  })
                }
              />
            )}
            {match.phase === 'block_declare' && match.combatAttacker === 1 && (
              <>
                <EndTurnButton
                  label="确认格挡"
                  disabled={busy}
                  primary
                  onClick={() => {
                    const ids =
                      match.attackOrderIds ??
                      match.attackOrder
                        .map((s) => foe.board[s]?.instanceId)
                        .filter((id): id is string => Boolean(id));
                    void props.onAction({
                      type: 'declare_blocks',
                      pairs: ids.map((attackerInstanceId) => {
                        const attackerSlot = findSlotById(foe.board, attackerInstanceId);
                        const blockerInstanceId = blockByAttacker[attackerInstanceId];
                        const blockerSlot = blockerInstanceId
                          ? findSlotById(me.board, blockerInstanceId)
                          : -1;
                        return {
                          attackerSlot,
                          blockerSlot: blockerSlot >= 0 ? blockerSlot : -1,
                          attackerInstanceId,
                          blockerInstanceId: blockerInstanceId ?? null,
                        };
                      }),
                    });
                  }}
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
                  label={swapPick != null ? `已选槽 ${swapPick} · 再点另一槽` : '点击两个备战槽换位'}
                  disabled={busy}
                  primary={swapPick != null}
                  onClick={() => {
                    if (swapPick != null) setSwapPick(null);
                  }}
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
