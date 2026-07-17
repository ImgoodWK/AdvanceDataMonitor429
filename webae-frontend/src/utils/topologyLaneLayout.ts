import type { TopologyNodeDto } from '@/types/dto';
import type { TopologyDisplaySettings } from '@/types/topologyDisplay';

export interface LanePoint {
  x: number;
  y: number;
}

/**
 * Preset pixel layout for ae_budget_v2 channel-lane topology.
 * TB: hub top → trunk → lanes → pods → devices; orbit left/right/bottom of hub.
 * LR: rotates TB coordinates.
 */
export function computeChannelLanePresetLayout(
  nodes: TopologyNodeDto[],
  settings: TopologyDisplaySettings
): Map<string, LanePoint> {
  const points = new Map<string, LanePoint>();
  const depthGap = Math.max(80, settings.depthGap);
  const siblingGap = Math.max(48, settings.siblingGap);
  const lr = settings.layoutDirection === 'LR';

  const byLayer = {
    hub: [] as TopologyNodeDto[],
    trunk: [] as TopologyNodeDto[],
    lane: [] as TopologyNodeDto[],
    pod: [] as TopologyNodeDto[],
    device: [] as TopologyNodeDto[],
    orbit: [] as TopologyNodeDto[],
  };

  for (const node of nodes) {
    const layer = resolveLayer(node);
    if (layer in byLayer) {
      byLayer[layer as keyof typeof byLayer].push(node);
    } else {
      byLayer.device.push(node);
    }
  }

  byLayer.lane.sort((a, b) => (a.branchIndex ?? 0) - (b.branchIndex ?? 0));
  byLayer.pod.sort((a, b) => {
    const ba = a.branchIndex ?? 0;
    const bb = b.branchIndex ?? 0;
    if (ba !== bb) return ba - bb;
    return (a.layoutY ?? 0) - (b.layoutY ?? 0);
  });
  byLayer.device.sort((a, b) => {
    const ba = a.branchIndex ?? 0;
    const bb = b.branchIndex ?? 0;
    if (ba !== bb) return ba - bb;
    return (a.layoutY ?? 0) - (b.layoutY ?? 0);
  });

  const hub = byLayer.hub[0] ?? nodes.find((n) => n.id === 'controller' || n.type === 'controller');
  if (hub) {
    points.set(hub.id, { x: 0, y: 0 });
  }

  // Orbit: energy west/east, storage/cpu south of hub
  const orbitWest: TopologyNodeDto[] = [];
  const orbitEast: TopologyNodeDto[] = [];
  const orbitSouth: TopologyNodeDto[] = [];
  for (const node of byLayer.orbit) {
    if (node.layoutSector === 'west') {
      orbitWest.push(node);
    } else if (node.layoutSector === 'east') {
      orbitEast.push(node);
    } else if (node.podKind === 'power0') {
      (orbitWest.length <= orbitEast.length ? orbitWest : orbitEast).push(node);
    } else {
      orbitSouth.push(node);
    }
  }

  orbitWest.forEach((node, i) => {
    points.set(node.id, { x: -depthGap * 1.1, y: (i - (orbitWest.length - 1) / 2) * siblingGap * 0.7 });
  });
  orbitEast.forEach((node, i) => {
    points.set(node.id, { x: depthGap * 1.1, y: (i - (orbitEast.length - 1) / 2) * siblingGap * 0.7 });
  });
  orbitSouth.forEach((node, i) => {
    points.set(node.id, {
      x: (i - (orbitSouth.length - 1) / 2) * siblingGap * 0.85,
      y: depthGap * 0.55,
    });
  });

  const trunk = byLayer.trunk[0];
  if (trunk) {
    points.set(trunk.id, { x: 0, y: depthGap });
  }

  const laneCount = Math.max(4, byLayer.lane.length);
  byLayer.lane.forEach((node, i) => {
    const idx = node.branchIndex ?? i;
    const x = (idx - (laneCount - 1) / 2) * siblingGap * 1.6;
    points.set(node.id, { x, y: depthGap * 2 });
  });

  // Pods under each lane
  const podsByLane = new Map<number, TopologyNodeDto[]>();
  for (const pod of byLayer.pod) {
    const lane = pod.branchIndex ?? 0;
    const list = podsByLane.get(lane) ?? [];
    list.push(pod);
    podsByLane.set(lane, list);
  }

  const podY = depthGap * 3;
  for (const [lane, pods] of podsByLane) {
    const laneNode = byLayer.lane.find((n) => (n.branchIndex ?? -1) === lane);
    const laneX = laneNode ? (points.get(laneNode.id)?.x ?? 0) : (lane - 1.5) * siblingGap * 1.6;
    pods.forEach((pod, i) => {
      const offset = (i - (pods.length - 1) / 2) * siblingGap * 0.9;
      points.set(pod.id, { x: laneX + offset, y: podY });
    });
  }

  // Devices under their parent pod (or under lane if no parent)
  const devicesByParent = new Map<string, TopologyNodeDto[]>();
  for (const device of byLayer.device) {
    const parent = device.parentId || `lane:${device.branchIndex ?? 0}`;
    const list = devicesByParent.get(parent) ?? [];
    list.push(device);
    devicesByParent.set(parent, list);
  }

  const deviceY = depthGap * 4;
  for (const [parentId, devices] of devicesByParent) {
    let baseX = 0;
    const parentPt = points.get(parentId);
    if (parentPt) {
      baseX = parentPt.x;
    } else if (parentId.startsWith('lane:')) {
      const laneIdx = Number(parentId.slice(5));
      const laneNode = byLayer.lane.find((n) => (n.branchIndex ?? -1) === laneIdx);
      baseX = laneNode ? (points.get(laneNode.id)?.x ?? 0) : 0;
    }
    devices.forEach((device, i) => {
      const offset = (i - (devices.length - 1) / 2) * siblingGap * 0.75;
      points.set(device.id, { x: baseX + offset, y: deviceY + Math.floor(i / 6) * siblingGap * 0.5 });
    });
  }

  // Any remaining nodes
  for (const node of nodes) {
    if (points.has(node.id)) continue;
    points.set(node.id, {
      x: (node.layoutY ?? 0) * siblingGap,
      y: (node.layoutX ?? 0) * depthGap,
    });
  }

  if (lr) {
    for (const [id, p] of points) {
      points.set(id, { x: p.y, y: p.x });
    }
  }

  return points;
}

function resolveLayer(node: TopologyNodeDto): string {
  if (node.layer) return node.layer;
  if (node.id === 'controller' || node.type === 'controller' || node.role === 'hub') return 'hub';
  if (node.type === 'cable_dense' || node.role === 'trunk') return 'trunk';
  if (node.type === 'cable_smart' || node.role === 'lane') return 'lane';
  if (node.type === 'pod' || node.role === 'pod') return 'pod';
  if (node.role === 'orbit' || node.layer === 'orbit') return 'orbit';
  if (node.channelCost === 0 && (node.podKind?.endsWith('0') || node.role === 'orbit')) return 'orbit';
  return 'device';
}
