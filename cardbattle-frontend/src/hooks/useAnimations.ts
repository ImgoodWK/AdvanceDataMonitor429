import gsap from 'gsap';
import { useEffect, useRef, useState } from 'react';
import type { BattleState } from '../api/client';

export interface FloatingDamage {
  id: string;
  x: number;
  y: number;
  value: number;
  heal?: boolean;
}

function snapshotKey(state: BattleState) {
  const me = state.players[0];
  const foe = state.players[1];
  return JSON.stringify({
    phase: state.phase,
    turn: state.turn,
    meHp: me.nexusHp,
    foeHp: foe.nexusHp,
    meMana: me.mana,
    meHand: me.hand.length,
    meBoard: me.board.map((u) => (u ? `${u.instanceId}:${u.health}` : null)),
    foeBoard: foe.board.map((u) => (u ? `${u.instanceId}:${u.health}` : null)),
    winner: state.winner,
    logLen: state.log.length,
  });
}

function captureBoardCards(arena: HTMLElement): Map<string, HTMLElement> {
  const snapshots = new Map<string, HTMLElement>();
  for (const side of ['player', 'enemy'] as const) {
    for (let slot = 0; slot < 6; slot++) {
      const card = arena.querySelector(
        `[data-slot-side="${side}"][data-slot-index="${slot}"] .card-frame`,
      );
      if (card instanceof HTMLElement) snapshots.set(`${side}:${slot}`, card.cloneNode(true) as HTMLElement);
    }
  }
  return snapshots;
}

function addStrikeImpact(layer: HTMLElement, x: number, y: number, value: number): void {
  const flash = document.createElement('div');
  flash.className = 'combat-strike-flash';
  flash.style.left = `${x}px`;
  flash.style.top = `${y}px`;
  layer.appendChild(flash);
  const number = document.createElement('div');
  number.className = 'combat-strike-number';
  number.textContent = `-${Math.max(0, value)}`;
  number.style.left = `${x}px`;
  number.style.top = `${y}px`;
  layer.appendChild(number);
  gsap.fromTo(flash, { scale: 0.25, opacity: 1, rotate: -20 }, {
    scale: 2.2,
    opacity: 0,
    rotate: 18,
    duration: 0.42,
    ease: 'power3.out',
    onComplete: () => flash.remove(),
  });
  gsap.fromTo(number, { y: 8, opacity: 0, scale: 0.7 }, {
    y: -34,
    opacity: 1,
    scale: 1.15,
    duration: 0.28,
    yoyo: true,
    repeat: 1,
    repeatDelay: 0.12,
    onComplete: () => number.remove(),
  });
}

function playCombatTimeline(
  previous: BattleState,
  arena: HTMLElement,
  layer: HTMLElement,
  snapshots: Map<string, HTMLElement>,
): boolean {
  const attackerIndex = previous.combatAttacker;
  if (attackerIndex == null || previous.attackOrder.length === 0) return false;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false;
  const attackerSide = attackerIndex === 0 ? 'player' : 'enemy';
  const defenderSide = attackerIndex === 0 ? 'enemy' : 'player';
  const attacker = previous.players[attackerIndex];
  const pairs = previous.blockPairs.length
    ? previous.blockPairs
    : previous.attackOrder.map((attackerSlot) => ({ attackerSlot, blockerSlot: -1 }));
  const arenaRect = arena.getBoundingClientRect();

  pairs.forEach((pair, order) => {
    const source = arena.querySelector(
      `[data-slot-side="${attackerSide}"][data-slot-index="${pair.attackerSlot}"]`,
    );
    const target =
      pair.blockerSlot >= 0
        ? arena.querySelector(
            `[data-slot-side="${defenderSide}"][data-slot-index="${pair.blockerSlot}"]`,
          )
        : arena.querySelector(`[data-nexus-side="${defenderSide}"]`);
    if (!(source instanceof HTMLElement) || !(target instanceof HTMLElement)) return;
    const sourceRect = source.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    const ghost =
      snapshots.get(`${attackerSide}:${pair.attackerSlot}`)?.cloneNode(true) ??
      document.createElement('div');
    if (!(ghost instanceof HTMLElement)) return;
    ghost.classList.add('combat-card-ghost');
    ghost.removeAttribute('tabindex');
    ghost.style.left = `${sourceRect.left - arenaRect.left}px`;
    ghost.style.top = `${sourceRect.top - arenaRect.top}px`;
    ghost.style.width = `${sourceRect.width}px`;
    ghost.style.height = `${sourceRect.height}px`;
    layer.appendChild(ghost);
    const destinationX = targetRect.left - sourceRect.left + (targetRect.width - sourceRect.width) / 2;
    const destinationY = targetRect.top - sourceRect.top + (targetRect.height - sourceRect.height) / 2;
    const impactX = targetRect.left - arenaRect.left + targetRect.width / 2;
    const impactY = targetRect.top - arenaRect.top + targetRect.height / 2;
    const attackValue = attacker.board[pair.attackerSlot]?.attack ?? 0;
    const timeline = gsap.timeline({ delay: order * 0.2, onComplete: () => ghost.remove() });
    timeline
      .to(ghost, { scale: 1.08, y: attackerSide === 'player' ? -12 : 12, duration: 0.1 })
      .to(ghost, {
        x: destinationX,
        y: destinationY,
        rotate: attackerSide === 'player' ? 4 : -4,
        scale: 1.16,
        duration: 0.28,
        ease: 'power3.in',
        onComplete: () => addStrikeImpact(layer, impactX, impactY, attackValue),
      })
      .to(ghost, {
        x: 0,
        y: 0,
        rotate: 0,
        scale: 0.96,
        opacity: 0,
        duration: 0.26,
        ease: 'power2.out',
      });
  });
  return true;
}

export function useBattleAnimations(args: {
  match: BattleState | null;
  arenaRef: React.RefObject<HTMLElement | null>;
  effectRef: React.RefObject<HTMLElement | null>;
}) {
  const prevRef = useRef<BattleState | null>(null);
  const boardSnapshotsRef = useRef<Map<string, HTMLElement>>(new Map());
  const [bannerPhase, setBannerPhase] = useState<string | null>(null);
  const [damages, setDamages] = useState<FloatingDamage[]>([]);
  const [shake, setShake] = useState(false);
  const [vignette, setVignette] = useState(false);

  useEffect(() => {
    const match = args.match;
    if (!match) {
      prevRef.current = null;
      return;
    }
    const prev = prevRef.current;
    if (!prev) {
      prevRef.current = match;
      if (args.arenaRef.current) boardSnapshotsRef.current = captureBoardCards(args.arenaRef.current);
      setBannerPhase(match.phase);
      const t = window.setTimeout(() => setBannerPhase(null), 900);
      return () => window.clearTimeout(t);
    }
    if (snapshotKey(prev) === snapshotKey(match)) return;

    const arena = args.arenaRef.current;
    const layer = args.effectRef.current;
    const combatPlayed =
      Boolean(arena && layer) &&
      prev.phase === 'combat_response' &&
      match.phase !== 'combat_response' &&
      playCombatTimeline(prev, arena!, layer!, boardSnapshotsRef.current);

    if (prev.phase !== match.phase) {
      setBannerPhase(match.phase);
      window.setTimeout(() => setBannerPhase(null), 1100);
    }

    const meHpDelta = match.players[0].nexusHp - prev.players[0].nexusHp;
    const foeHpDelta = match.players[1].nexusHp - prev.players[1].nexusHp;

    const spawnDamage = (value: number, foe: boolean, heal?: boolean) => {
      if (!arena) return;
      const rect = arena.getBoundingClientRect();
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      const item: FloatingDamage = {
        id,
        x: rect.width * (foe ? 0.5 : 0.5) + (Math.random() * 40 - 20),
        y: foe ? 48 : rect.height - 120,
        value,
        heal,
      };
      setDamages((d) => [...d, item]);
      window.setTimeout(() => setDamages((d) => d.filter((x) => x.id !== id)), 800);
    };

    if (meHpDelta < 0) {
      spawnDamage(-meHpDelta, false);
      setShake(true);
      setVignette(true);
      window.setTimeout(() => setShake(false), 360);
      window.setTimeout(() => setVignette(false), 480);
    } else if (meHpDelta > 0) {
      spawnDamage(meHpDelta, false, true);
    }
    if (foeHpDelta < 0) {
      spawnDamage(-foeHpDelta, true);
      setShake(true);
      window.setTimeout(() => setShake(false), 360);
    } else if (foeHpDelta > 0) {
      spawnDamage(foeHpDelta, true, true);
    }

    // Play / death impact rings via GSAP on effect layer
    if (layer && !combatPlayed) {
      const ring = document.createElement('div');
      ring.className = 'impact-ring';
      ring.style.left = `${40 + Math.random() * 60}%`;
      ring.style.top = `${35 + Math.random() * 30}%`;
      layer.appendChild(ring);
      gsap.fromTo(
        ring,
        { scale: 0.2, opacity: 0.95 },
        {
          scale: 2.4,
          opacity: 0,
          duration: 0.45,
          ease: 'power2.out',
          onComplete: () => ring.remove(),
        },
      );

      for (let i = 0; i < 8; i++) {
        const dust = document.createElement('div');
        dust.className = 'pixel-dust';
        dust.style.left = ring.style.left;
        dust.style.top = ring.style.top;
        layer.appendChild(dust);
        gsap.to(dust, {
          x: (Math.random() - 0.5) * 80,
          y: (Math.random() - 0.5) * 60,
          opacity: 0,
          duration: 0.5 + Math.random() * 0.25,
          ease: 'power2.out',
          onComplete: () => dust.remove(),
        });
      }
    }

    // Unit death: board slot emptied
    for (let i = 0; i < 6; i++) {
      const was = prev.players[0].board[i];
      const now = match.players[0].board[i];
      if (was && !now && arena) {
        const el = arena.querySelector(`[data-slot-side="player"][data-slot-index="${i}"]`);
        if (el) {
          gsap.fromTo(el, { filter: 'brightness(2)' }, { filter: 'brightness(1)', duration: 0.4 });
        }
      }
      const wasF = prev.players[1].board[i];
      const nowF = match.players[1].board[i];
      if (wasF && !nowF && arena) {
        const el = arena.querySelector(`[data-slot-side="enemy"][data-slot-index="${i}"]`);
        if (el) {
          gsap.fromTo(el, { filter: 'brightness(2.2)' }, { filter: 'brightness(1)', duration: 0.4 });
        }
      }
    }

    if (arena) boardSnapshotsRef.current = captureBoardCards(arena);
    prevRef.current = match;
  }, [args.match, args.arenaRef, args.effectRef]);

  return { bannerPhase, damages, shake, vignette };
}
