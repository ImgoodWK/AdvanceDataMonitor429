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

export function useBattleAnimations(args: {
  match: BattleState | null;
  arenaRef: React.RefObject<HTMLElement | null>;
  effectRef: React.RefObject<HTMLElement | null>;
}) {
  const prevRef = useRef<BattleState | null>(null);
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
      setBannerPhase(match.phase);
      const t = window.setTimeout(() => setBannerPhase(null), 900);
      return () => window.clearTimeout(t);
    }
    if (snapshotKey(prev) === snapshotKey(match)) return;

    const arena = args.arenaRef.current;
    const layer = args.effectRef.current;

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
    if (layer) {
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

    prevRef.current = match;
  }, [args.match, args.arenaRef, args.effectRef]);

  return { bannerPhase, damages, shake, vignette };
}
