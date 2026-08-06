import { describe, expect, it } from 'vitest';

import type { WorldMapMarkerDto } from '@/types/dto';
import { consolidateMultiblockMarkers } from '@/utils/worldMapMarkers';

function marker(
  nodeId: string,
  type: string,
  subtype: string,
  x: number,
  y: number,
  z: number,
  displayName = type
): WorldMapMarkerDto {
  return {
    id: `0:${x}:${y}:${z}`,
    nodeId,
    type,
    subtype,
    displayName,
    iconItemId: '',
    x,
    y,
    z,
    dim: 0,
    channelCost: 0,
  };
}

describe('consolidateMultiblockMarkers', () => {
  it('merges multiple cpu markers for the same nodeId into one (monitor wins)', () => {
    const input = [
      marker('cpu:1', 'cpu', 'cpu', 1, 2, 3, 'Crafting Storage'),
      marker('cpu:1', 'cpu', 'cpu', 4, 5, 6, 'Crafting Monitor'),
      marker('bus:2', 'bus', 'bus_import', 7, 8, 9, 'Import Bus'),
    ];
    const out = consolidateMultiblockMarkers(input);
    expect(out).toHaveLength(2);
    expect(out.find((m) => m.nodeId === 'cpu:1')).toMatchObject({ x: 4, y: 5, z: 6 });
    expect(out.find((m) => m.nodeId === 'bus:2')).toBeTruthy();
  });

  it('merges multiple controller markers for the same nodeId into lowest-y anchor', () => {
    const input = [
      marker('ctrl:1', 'controller', 'controller', 10, 70, 20),
      marker('ctrl:1', 'controller', 'controller', 11, 64, 21),
      marker('ctrl:1', 'controller', 'controller', 12, 65, 22),
    ];
    const out = consolidateMultiblockMarkers(input);
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({ nodeId: 'ctrl:1', x: 11, y: 64, z: 21 });
  });

  it('leaves unrelated markers untouched', () => {
    const input = [
      marker('bus:1', 'bus', 'bus_export', 1, 2, 3),
      marker('bus:2', 'bus', 'bus_import', 4, 5, 6),
    ];
    expect(consolidateMultiblockMarkers(input)).toHaveLength(2);
  });
});
