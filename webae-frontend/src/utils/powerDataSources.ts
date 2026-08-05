import type { PowerDto } from '@/types/dto';

/** Power page widget snapshot — one network's power DTO slice. */
export interface PowerSnapshot {
  euStored: number;
  euMax: number;
  euInRate: number;
  euOutRate: number;
  steamStored: number;
  steamMax: number;
  steamSupported: boolean;
  steamCapacityKnown: boolean;
  steamInRate: number;
  steamOutRate: number;
  euHistory: number[];
  steamHistory: number[];
  euHistoryTimestamps: number[];
  steamHistoryTimestamps: number[];
}

export function powerDtoToSnapshot(d: PowerDto): PowerSnapshot {
  return {
    euStored: d.euStored || 0,
    euMax: d.euMax || 0,
    euInRate: d.euInRate || 0,
    euOutRate: d.euOutRate || 0,
    steamStored: d.steamStored || 0,
    steamMax: d.steamMax || 0,
    steamSupported: d.steamSupported === true,
    steamCapacityKnown: d.steamCapacityKnown === true,
    steamInRate: d.steamInRate || 0,
    steamOutRate: d.steamOutRate || 0,
    euHistory: d.euHistory || [],
    steamHistory: d.steamHistory || [],
    euHistoryTimestamps: d.euHistoryTimestamps || [],
    steamHistoryTimestamps: d.steamHistoryTimestamps || [],
  };
}

export function getPowerDataSourceValue(ds: string, snap: PowerSnapshot | null): number {
  if (!snap) return 0;
  switch (ds) {
    case 'euStored':
      return snap.euStored;
    case 'euMax':
      return snap.euMax;
    case 'euPercent':
      return snap.euMax > 0 ? (snap.euStored / snap.euMax) * 100 : 0;
    case 'euInRate':
      return snap.euInRate;
    case 'euOutRate':
      return snap.euOutRate;
    case 'steamStored':
      return snap.steamStored;
    case 'steamMax':
      return snap.steamMax;
    case 'steamPercent':
      return snap.steamCapacityKnown && snap.steamMax > 0
        ? (snap.steamStored / snap.steamMax) * 100
        : 0;
    case 'steamInRate':
      return snap.steamInRate;
    case 'steamOutRate':
      return snap.steamOutRate;
    case 'powerHistory':
      return snap.euHistory.length;
    default:
      return 0;
  }
}

export const POWER_DATA_SOURCES = [
  'euStored',
  'euMax',
  'euPercent',
  'euInRate',
  'euOutRate',
  'steamStored',
  'steamMax',
  'steamPercent',
  'steamInRate',
  'steamOutRate',
  'powerHistory',
];
