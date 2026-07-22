import { randomUUID } from 'node:crypto';
import type { BattleState, ThemeId, VoltageTier } from '../battle/types.js';
import { createMatch, applyAction, publicState, type PlayerAction, runAiIfNeeded } from '../battle/engine.js';
import {
  ALL_THEMES,
  buildDeck,
  generateStages,
  rewardChoices,
  RUN_POWERS,
  STARTER_EQUIPMENT,
  validateThemes,
  type RunState,
  type StageRewardChoice,
} from '../pve/run.js';
import { enqueueReward } from '../rewards/pending.js';
import { persistMatch, persistRun, restoreMatch, restoreRun } from '../storage/state.js';

const matches = new Map<string, BattleState>();
const runs = new Map<string, RunState>();
const matchOwner = new Map<string, string>();

function resolveRun(runId: string): RunState | undefined {
  const memory = runs.get(runId);
  if (memory) return memory;
  const restored = restoreRun(runId);
  if (restored) runs.set(runId, restored);
  return restored ?? undefined;
}

function resolveMatch(matchId: string): BattleState | undefined {
  const memory = matches.get(matchId);
  if (memory) return memory;
  const restored = restoreMatch(matchId);
  if (!restored) return undefined;
  matches.set(matchId, restored.match);
  matchOwner.set(matchId, restored.ownerUuid);
  return restored.match;
}

export function listThemes() {
  return ALL_THEMES;
}

export function listEquipment() {
  return STARTER_EQUIPMENT;
}

export function startRun(input: {
  ownerUuid: string;
  playerName: string;
  themes: ThemeId[];
  voltage: VoltageTier;
  seed?: number;
  equipmentIds?: string[];
}): RunState {
  const err = validateThemes(input.themes, input.voltage);
  if (err) throw new Error(err);
  const seed = input.seed ?? Date.now() % 1_000_000;
  const deck = buildDeck(input.themes, input.voltage, seed);
  let attack = 0;
  let health = 0;
  let armor = 0;
  for (const id of input.equipmentIds ?? []) {
    const eq = STARTER_EQUIPMENT.find((e) => e.id === id);
    if (eq) {
      attack += eq.attack;
      health += eq.health;
      armor += eq.armor;
    }
  }
  const run: RunState = {
    runId: randomUUID(),
    ownerUuid: input.ownerUuid,
    seed,
    voltage: input.voltage,
    themes: input.themes,
    deck,
    equipment: { attack, health, armor },
    stageIndex: 0,
    stages: generateStages(seed, input.voltage),
    availableStageIds: ['stage_1a', 'stage_1b'],
    completedStageIds: [],
    currentStageId: null,
    powers: [],
    unlockedSkinIds: ['gt_factory'],
    pendingChoice: null,
    completed: false,
    victories: 0,
  };
  runs.set(run.runId, run);
  persistRun(run);
  return run;
}

export function getRun(runId: string): RunState | undefined {
  return resolveRun(runId);
}

export function beginStage(
  runId: string,
  ownerUuid: string,
  playerName: string,
  stageId?: string,
): { run: RunState; match: BattleState } {
  const run = resolveRun(runId);
  if (!run || run.ownerUuid !== ownerUuid) throw new Error('Run not found');
  if (run.completed) throw new Error('Run completed');
  if (run.currentStageId) throw new Error('Stage already active');
  const selectedStageId = stageId ?? run.availableStageIds[0];
  if (!selectedStageId || !run.availableStageIds.includes(selectedStageId)) throw new Error('Stage is not available');
  const stage = run.stages.find((candidate) => candidate.id === selectedStageId);
  if (!stage) throw new Error('No stage');
  const aiDeck = buildDeck(stage.aiThemes, stage.aiVoltage, run.seed + run.stageIndex * 99);
  const match = createMatch({
    seed: run.seed + run.stageIndex * 17,
    playerId: ownerUuid,
    playerName,
    playerDeck: run.deck,
    playerThemes: run.themes,
    playerVoltage: run.voltage,
    aiDeck,
    aiThemes: stage.aiThemes,
    aiVoltage: stage.aiVoltage,
    aiName: stage.nameZh,
    dlbForceEvery: stage.feature === 'dlb_force' || run.themes.includes('dlb') ? 5 : 0,
  });

  for (const powerId of run.powers) {
    const power = RUN_POWERS.find((candidate) => candidate.id === powerId);
    if (!power) continue;
    if (power.nexusHp) {
      match.players[0].maxNexusHp += power.nexusHp;
      match.players[0].nexusHp += power.nexusHp;
    }
    if (power.startSpellMana) {
      match.players[0].spellMana = Math.min(3, match.players[0].spellMana + power.startSpellMana);
    }
  }

  // Apply starter equipment to first empty play conceptually — buff first unit card stats in hand via log
  const powerEquipment = run.powers.reduce(
    (total, powerId) => {
      const power = RUN_POWERS.find((candidate) => candidate.id === powerId);
      total.attack += power?.firstUnitAttack ?? 0;
      total.health += power?.firstUnitHealth ?? 0;
      total.armor += power?.firstUnitArmor ?? 0;
      return total;
    },
    { attack: 0, health: 0, armor: 0 },
  );
  const encounterEquipment = {
    attack: run.equipment.attack + powerEquipment.attack,
    health: run.equipment.health + powerEquipment.health,
    armor: run.equipment.armor + powerEquipment.armor,
  };
  if (encounterEquipment.attack || encounterEquipment.health || encounterEquipment.armor) {
    match.log.push(
      `Loadout +${encounterEquipment.attack}atk / +${encounterEquipment.health}hp / +${encounterEquipment.armor}armor on next unit`,
    );
    (match as BattleState & { _equip?: typeof encounterEquipment })._equip = encounterEquipment;
  }

  if (stage.kind !== 'battle') {
    const bonus = stage.kind === 'boss' ? 10 : 5;
    match.players[1].nexusHp += bonus;
    match.players[1].maxNexusHp += bonus;
    match.players[1].maxMana += stage.kind === 'boss' ? 2 : 1;
    match.players[1].mana = match.players[1].maxMana;
  }

  matches.set(match.matchId, match);
  matchOwner.set(match.matchId, ownerUuid);
  run.pendingChoice = null;
  run.currentStageId = stage.id;
  persistRun(run);
  persistMatch(ownerUuid, match);
  return { run, match };
}

export function getMatch(matchId: string): BattleState | undefined {
  return resolveMatch(matchId);
}

export function actOnMatch(
  matchId: string,
  ownerUuid: string,
  action: PlayerAction,
): BattleState {
  const match = resolveMatch(matchId);
  if (!match) throw new Error('Match not found');
  if (matchOwner.get(matchId) !== ownerUuid) throw new Error('Forbidden');
  applyAction(match, 0, action);

  // Apply pending equipment once when first unit is on board
  const eq = (match as BattleState & { _equip?: { attack: number; health: number; armor: number }; _equipApplied?: boolean })._equip;
  const flag = match as BattleState & { _equipApplied?: boolean };
  if (eq && !flag._equipApplied) {
    const u = match.players[0].board.find((x) => x && !x.isStructure);
    if (u) {
      u.attack += eq.attack;
      u.health += eq.health;
      u.maxHealth += eq.health;
      u.armor += eq.armor;
      flag._equipApplied = true;
      match.log.push('Starter equipment applied');
    }
  }

  persistMatch(ownerUuid, match);
  return publicState(match, 0);
}

export function finishMatchIfWon(matchId: string, runId: string | undefined): {
  match: BattleState;
  run?: RunState;
  rewardsQueued?: boolean;
} {
  const match = resolveMatch(matchId);
  if (!match) throw new Error('Match not found');
  if (match.phase !== 'game_over') return { match: publicState(match, 0) };

  if (!runId) return { match: publicState(match, 0) };
  const run = resolveRun(runId);
  if (!run) return { match: publicState(match, 0) };

  const flagged = match as BattleState & { _runSettled?: boolean };
  if (flagged._runSettled) return { match: publicState(match, 0), run };
  flagged._runSettled = true;

  if (match.winner === 0) {
    run.victories += 1;
    const stage = run.stages.find((candidate) => candidate.id === run.currentStageId);
    if (!stage) throw new Error('Active stage not found');
    run.pendingChoice = rewardChoices(stage!, run.voltage);
    run.completedStageIds.push(stage.id);
    run.stageIndex = run.completedStageIds.length;
    run.availableStageIds = stage.nextStageIds.slice();
    run.currentStageId = null;
    if (stage.nextStageIds.length === 0) {
      run.completed = true;
    }
  } else {
    run.completed = true;
  }
  persistMatch(run.ownerUuid, match);
  persistRun(run);
  return { match: publicState(match, 0), run };
}

export function claimStageReward(
  runId: string,
  ownerUuid: string,
  choiceId: string,
): { run: RunState; entryId: string | null; unlockedSkinIds: string[] } {
  const run = resolveRun(runId);
  if (!run || run.ownerUuid !== ownerUuid) throw new Error('Run not found');
  if (!run.pendingChoice) throw new Error('No pending reward');
  const choice = run.pendingChoice.find((c) => c.id === choiceId);
  if (!choice) throw new Error('Invalid choice');
  const stageId = run.completedStageIds[run.completedStageIds.length - 1] ?? 'unknown';
  if (choice.cardIds?.length) run.deck.push(...choice.cardIds);
  if (choice.powerId && !run.powers.includes(choice.powerId)) run.powers.push(choice.powerId);
  const unlockedSkinIds: string[] = [];
  if (choice.skinId && !run.unlockedSkinIds.includes(choice.skinId)) {
    run.unlockedSkinIds.push(choice.skinId);
    unlockedSkinIds.push(choice.skinId);
  }
  const entry = choice.items.length
    ? enqueueReward(ownerUuid, choice.items, {
        runId: run.runId,
        stageId,
        label: choice.labelZh,
        schemaVersion: 2,
        rewardKey: choice.rewardKey,
        voltageTier: choice.voltageTier,
        delivery: choice.delivery,
      })
    : null;
  run.pendingChoice = null;
  persistRun(run);
  return { run, entryId: entry?.id ?? null, unlockedSkinIds };
}

export function viewMatch(matchId: string, ownerUuid: string): BattleState {
  const match = resolveMatch(matchId);
  if (!match) throw new Error('Match not found');
  if (matchOwner.get(matchId) !== ownerUuid) throw new Error('Forbidden');
  runAiIfNeeded(match);
  persistMatch(ownerUuid, match);
  return publicState(match, 0);
}
