export interface PatternEditorInputSlot {
  registryName: string;
  displayName: string;
  meta: number;
  stackSize: number;
  nbt?: string;
  isFluid: boolean;
  nonConsumable: boolean;
  programmableCircuit: boolean;
}

export interface PatternEditorOutputRow {
  key: string;
  registryName: string;
  displayName: string;
  meta: number;
  stackSize: number;
  nbt?: string;
  /** Original recipe output amount — multiplier cannot go below this. */
  originalStackSize: number;
  isFluid: boolean;
}

export type PatternPickTarget =
  | { kind: 'slot'; slot: number }
  | { kind: 'output' }
  | null;

export const PATTERN_MULTIPLIERS = [2, 4, 8, 16, 32, 64] as const;
