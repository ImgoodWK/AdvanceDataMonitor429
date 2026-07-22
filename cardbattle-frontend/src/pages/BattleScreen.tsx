import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';
import type { BattleState, CardDef, RunState } from '../api/client';
import { BoardSlot } from '../components/BoardSlot';
import { CombatLog } from '../components/CombatLog';
import { EffectLayer } from '../components/EffectLayer';
import { EndTurnButton } from '../components/EndTurnButton';
import { Hand } from '../components/Hand';
import { Hud } from '../components/Hud';
import { NexusBar } from '../components/Nexus';
import { PhaseBanner } from '../components/PhaseBanner';
import { useBattleAnimations } from '../hooks/useAnimations';
import { useDragPlay } from '../hooks/useDragPlay';
import { buildPlayAction, canPlayToSlot } from '../lib/playLegality';
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

  const [selectedHand, setSelectedHand] = useState<number | null>(null);
  const [attackSlots, setAttackSlots] = useState<number[]>([]);
  const arenaRef = useRef<HTMLDivElement>(null);
  const effectRef = useRef<HTMLDivElement>(null);

  const { drag, beginDrag, hoverSlot, leaveSlot, endDrag } = useDragPlay({
    phase: match.phase,
    me,
    cardMap,
    busy,
    onPlay: props.onAction,
  });

  const { bannerPhase, damages, shake, vignette } = useBattleAnimations({
    match,
    arenaRef: arenaRef as RefObject<HTMLElement | null>,
    effectRef: effectRef as RefObject<HTMLElement | null>,
  });

  useEffect(() => {
    setAttackSlots([]);
    setSelectedHand(null);
  }, [match.turn, match.phase]);

  async function clickPlayToSlot(slot: number, side: 'player' | 'enemy') {
    if (selectedHand == null || busy || match.phase !== 'main') return;
    const legal = canPlayToSlot({
      phase: match.phase,
      me,
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
        bankedMana={me.bankedMana}
        stageLabel={run ? `关卡 ${run.stageIndex + 1}/${run.stages.length} · 胜 ${run.victories}` : undefined}
        eternal={me.eternalActive}
      />

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
              dropState={
                drag.dragging && drag.hoverSide === 'enemy' && drag.hoverSlot === i
                  ? drag.dropState
                  : 'none'
              }
              onPointerEnter={() => hoverSlot(i, 'enemy')}
              onPointerLeave={leaveSlot}
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
              dropState={
                drag.dragging && drag.hoverSide === 'player' && drag.hoverSlot === i
                  ? drag.dropState
                  : 'none'
              }
              onPointerEnter={() => hoverSlot(i, 'player')}
              onPointerLeave={leaveSlot}
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

      <div className="panel">
        <Hand
          hand={me.hand}
          cardMap={cardMap}
          selectedHand={selectedHand}
          draggingIndex={drag.handIndex}
          disabled={busy || match.phase !== 'main'}
          onSelect={setSelectedHand}
          onDragStart={beginDrag}
          onDragEnd={() => {
            void endDrag();
          }}
        />

        {selectedHand != null && match.phase === 'main' && (
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

        <div className="action-bar" style={{ marginTop: '0.75rem' }}>
          <div className="row">
            <EndTurnButton
              label="结束出牌 → 攻击"
              disabled={busy || match.phase !== 'main'}
              primary
              onClick={() => void props.onAction({ type: 'end_main' })}
            />
            <EndTurnButton
              label="确认攻击"
              disabled={busy || match.phase !== 'attack_declare'}
              primary
              onClick={() => void props.onAction({ type: 'declare_attacks', slots: attackSlots })}
            />
            <EndTurnButton
              label="不格挡"
              disabled={busy || match.phase !== 'block_declare'}
              onClick={() => void props.onAction({ type: 'pass_block' })}
            />
            <EndTurnButton
              label="神秘换位 0↔1"
              disabled={busy || match.phase !== 'swap_extra'}
              onClick={() => void props.onAction({ type: 'swap_slots', a: 0, b: 1 })}
            />
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
