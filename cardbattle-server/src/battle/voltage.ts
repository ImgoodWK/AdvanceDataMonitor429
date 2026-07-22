import type { VoltageTier } from './types.js';
import { VOLTAGE_ORDER } from './types.js';

export function voltageIndex(v: VoltageTier): number {
  return VOLTAGE_ORDER.indexOf(v);
}

/** Each own voltage tier reduces nexus damage taken by 5%. */
export function nexusDamageMultiplier(defenderVoltage: VoltageTier): number {
  const tiers = voltageIndex(defenderVoltage) + 1;
  return Math.max(0.25, 1 - tiers * 0.05);
}

/**
 * Attacking a higher-voltage opponent is much harder:
 * damage to their units/nexus is scaled down by under-level gap.
 */
export function offenseMultiplier(attacker: VoltageTier, defender: VoltageTier): number {
  const gap = voltageIndex(defender) - voltageIndex(attacker);
  if (gap <= 0) return 1;
  // Each tier above: 35% less outgoing damage
  return Math.max(0.15, 1 - gap * 0.35);
}

export function applyNexusDamage(
  raw: number,
  attackerVoltage: VoltageTier,
  defenderVoltage: VoltageTier,
  defenderReductionPct: number,
): number {
  let dmg =
    raw * offenseMultiplier(attackerVoltage, defenderVoltage) * nexusDamageMultiplier(defenderVoltage);
  dmg *= Math.max(0, 1 - defenderReductionPct / 100);
  return Math.max(1, Math.floor(dmg));
}
