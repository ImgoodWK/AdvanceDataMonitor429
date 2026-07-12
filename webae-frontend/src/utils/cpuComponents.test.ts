import { describe, expect, it } from 'vitest';

import type { TopologyNodeDto } from '@/types/dto';
import { AE_CPU_COMPONENT_ICON_IDS } from '@/utils/aeCableColors';
import { inferCpuComponentKind, summarizeCpuComponents } from '@/utils/cpuComponents';

function cpuNode(partial: Partial<TopologyNodeDto>): TopologyNodeDto {
  return {
    id: 'cpu:1',
    type: 'cpu',
    subtype: 'cpu',
    displayName: 'Crafting CPU',
    count: 1,
    channelCost: 0,
    layoutX: 0,
    layoutY: 0,
    ...partial,
  };
}

describe('inferCpuComponentKind', () => {
  it('detects storage, monitor, and accelerator labels', () => {
    expect(inferCpuComponentKind('Crafting Storage Unit')).toBe('storage');
    expect(inferCpuComponentKind('Crafting Monitor')).toBe('monitor');
    expect(inferCpuComponentKind('Crafting Co-Processor')).toBe('accelerator');
  });
});

describe('summarizeCpuComponents', () => {
  it('uses cpuSummary counts with hardcoded fallback icons when devices lack icons', () => {
    const groups = summarizeCpuComponents(
      cpuNode({
        cpuSummary: {
          coProcessors: 2,
          availableStorage: 4096,
          usedStorage: 0,
          busy: false,
          unitCount: 6,
          storageUnits: 3,
          acceleratorUnits: 2,
          monitorUnits: 1,
        },
      })
    );
    expect(groups).toEqual([
      { kind: 'storage', iconId: AE_CPU_COMPONENT_ICON_IDS.storage, count: 3 },
      { kind: 'accelerator', iconId: AE_CPU_COMPONENT_ICON_IDS.accelerator, count: 2 },
      { kind: 'monitor', iconId: AE_CPU_COMPONENT_ICON_IDS.monitor, count: 1 },
    ]);
  });

  it('prefers devices[].iconItemId for icons when cpuSummary provides counts', () => {
    const groups = summarizeCpuComponents(
      cpuNode({
        cpuSummary: {
          coProcessors: 1,
          availableStorage: 1024,
          usedStorage: 0,
          busy: false,
          unitCount: 4,
          storageUnits: 2,
          acceleratorUnits: 1,
          monitorUnits: 1,
        },
        devices: [
          {
            displayName: 'Crafting Storage Unit',
            iconItemId: 'appliedenergistics2:tile.BlockCraftingStorage',
            x: 1,
            y: 2,
            z: 3,
            dim: 0,
          },
          {
            displayName: 'Crafting Co-Processor',
            iconItemId: 'appliedenergistics2:tile.BlockCraftingUnit:1',
            x: 2,
            y: 2,
            z: 3,
            dim: 0,
          },
          {
            displayName: 'Crafting Monitor',
            iconItemId: 'appliedenergistics2:tile.BlockCraftingMonitor',
            x: 3,
            y: 2,
            z: 3,
            dim: 0,
          },
        ],
      })
    );
    expect(groups).toEqual([
      { kind: 'storage', iconId: 'appliedenergistics2:tile.BlockCraftingStorage', count: 2 },
      { kind: 'accelerator', iconId: 'appliedenergistics2:tile.BlockCraftingUnit:1', count: 1 },
      { kind: 'monitor', iconId: 'appliedenergistics2:tile.BlockCraftingMonitor', count: 1 },
    ]);
  });

  it('falls back to device display names', () => {
    const groups = summarizeCpuComponents(
      cpuNode({
        devices: [
          { displayName: 'Crafting Storage Unit', x: 1, y: 2, z: 3, dim: 0 },
          { displayName: 'Crafting Monitor', x: 4, y: 5, z: 6, dim: 0 },
        ],
      })
    );
    expect(groups).toEqual([
      { kind: 'storage', iconId: AE_CPU_COMPONENT_ICON_IDS.storage, count: 1 },
      { kind: 'monitor', iconId: AE_CPU_COMPONENT_ICON_IDS.monitor, count: 1 },
    ]);
  });
});
