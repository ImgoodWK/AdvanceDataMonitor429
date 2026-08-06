import { describe, expect, it } from 'vitest';
import type { PowerDto } from '@/types/dto';
import { getPowerDataSourceValue, powerDtoToSnapshot } from './powerDataSources';

function dto(overrides: Partial<PowerDto> = {}): PowerDto {
  return {
    networkId: 1,
    timestamp: 1,
    euStored: 0,
    euMax: 0,
    euInRate: 0,
    euOutRate: 0,
    steamStored: 500,
    steamMax: 0,
    steamSupported: true,
    steamCapacityKnown: false,
    steamInRate: 0,
    steamOutRate: 0,
    euHistory: [],
    steamHistory: [],
    euHistoryTimestamps: [],
    steamHistoryTimestamps: [],
    ...overrides,
  };
}

describe('power data sources', () => {
  it('does not invent a steam percentage without capacity', () => {
    const snapshot = powerDtoToSnapshot(dto());
    expect(snapshot.steamSupported).toBe(true);
    expect(snapshot.steamCapacityKnown).toBe(false);
    expect(getPowerDataSourceValue('steamPercent', snapshot)).toBe(0);
    expect(getPowerDataSourceValue('steamStored', snapshot)).toBe(500);
  });

  it('uses steam max only when explicitly known', () => {
    const snapshot = powerDtoToSnapshot(dto({ steamMax: 1000, steamCapacityKnown: true }));
    expect(getPowerDataSourceValue('steamPercent', snapshot)).toBe(50);
  });
});
