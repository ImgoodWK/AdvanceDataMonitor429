import { randomUUID } from 'node:crypto';
import type { BattleState, ThemeId, VoltageTier } from '../battle/types.js';
import { createMatch, applyAction, publicState, type PlayerAction, runAiIfNeeded } from '../battle/engine.js';
import {
  ALL_THEMES,
  buildDeck,
  generateStages,
  rewardChoices,
  STARTER_EQUIPMENT,
  validateThemes,
  type RunState,
  type StageRewardChoice,
} from '../pve/run.js';
import { enqueueReward } from '../rewards/pending.js';

const matches = new Map<string, BattleState>();
const runs = new Map<string, RunState>();
const matchOwner = new Map<string, string>();

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
    stages: generateStages(seed, input.voltage, 5),
    pendingChoice: null,
    completed: false,
    victories: 0,
  };
  runs.set(run.runId, run);
  return run;
}

export function getRun(runId: string): RunState | undefined {
  return runs.get(runId);
}

export function beginStage(
  runId: string,
  ownerUuid: string,
  playerName: string,
  rewardChoiceId?: string,
): { run: RunState; match: BattleState; choice?: StageRewardChoice } {
  const run = runs.get(runId);
  if (!run || run.ownerUuid !== ownerUuid) throw new Error('Run not found');
  if (run.completed) throw new Error('Run completed');
  const stage = run.stages[run.stageIndex];
  if (!stage) throw new Error('No stage');

  let choice: StageRewardChoice | undefined;
  if (rewardChoiceId) {
    const choices = rewardChoices(stage, run.voltage);
    choice = choices.find((c) => c.id === rewardChoiceId);
    if (!choice) throw new Error('Invalid reward choice');
  }

  const hard = choice?.hard ?? false;
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
    aiVoltage: hard
      ? stage.aiVoltage
      : stage.aiVoltage,
    aiName: stage.nameZh + (hard ? ' (Hard)' : ''),
    dlbForceEvery: stage.feature === 'dlb_force' || run.themes.includes('dlb') ? 5 : 0,
  });

  // Apply starter equipment to first empty play conceptually — buff first unit card stats in hand via log
  if (run.equipment.attack || run.equipment.health || run.equipment.armor) {
    match.log.push(
      `Loadout +${run.equipment.attack}atk / +${run.equipment.health}hp / +${run.equipment.armor}armor on next unit`,
    );
    (match as BattleState & { _equip?: typeof run.equipment })._equip = run.equipment;
  }

  // Hard mode: AI extra nexus and mana
  if (hard) {
    match.players[1].nexusHp += 5;
    match.players[1].maxNexusHp += 5;
    match.players[1].maxMana += 1;
    match.players[1].mana += 1;
  }

  matches.set(match.matchId, match);
  matchOwner.set(match.matchId, ownerUuid);
  run.pendingChoice = null;
  return { run, match, choice };
}

export function getMatch(matchId: string): BattleState | undefined {
  return matches.get(matchId);
}

export function actOnMatch(
  matchId: string,
  ownerUuid: string,
  action: PlayerAction,
): BattleState {
  const match = matches.get(matchId);
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

  return publicState(match, 0);
}

export function finishMatchIfWon(matchId: string, runId: string | undefined): {
  match: BattleState;
  run?: RunState;
  rewardsQueued?: boolean;
} {
  const match = matches.get(matchId);
  if (!match) throw new Error('Match not found');
  if (match.phase !== 'game_over') return { match: publicState(match, 0) };

  if (!runId) return { match: publicState(match, 0) };
  const run = runs.get(runId);
  if (!run) return { match: publicState(match, 0) };

  const flagged = match as BattleState & { _runSettled?: boolean };
  if (flagged._runSettled) return { match: publicState(match, 0), run };
  flagged._runSettled = true;

  if (match.winner === 0) {
    run.victories += 1;
    const stage = run.stages[run.stageIndex];
    run.pendingChoice = rewardChoices(stage!, run.voltage);
    run.stageIndex += 1;
    if (run.stageIndex >= run.stages.length) {
      run.completed = true;
    }
  } else {
    run.completed = true;
  }
  return { match: publicState(match, 0), run };
}

export function claimStageReward(
  runId: string,
  ownerUuid: string,
  choiceId: string,
): { run: RunState; entryId: string } {
  const run = runs.get(runId);
  if (!run || run.ownerUuid !== ownerUuid) throw new Error('Run not found');
  if (!run.pendingChoice) throw new Error('No pending reward');
  const choice = run.pendingChoice.find((c) => c.id === choiceId);
  if (!choice) throw new Error('Invalid choice');
  const stage = run.stages[Math.max(0, run.stageIndex - 1)];
  const entry = enqueueReward(ownerUuid, choice.items, {
    runId: run.runId,
    stageId: stage?.id ?? 'unknown',
    label: choice.labelZh,
  });
  run.pendingChoice = null;
  return { run, entryId: entry.id };
}

export function viewMatch(matchId: string, ownerUuid: string): BattleState {
  const match = matches.get(matchId);
  if (!match) throw new Error('Match not found');
  if (matchOwner.get(matchId) !== ownerUuid) throw new Error('Forbidden');
  runAiIfNeeded(match);
  return publicState(match, 0);
}
