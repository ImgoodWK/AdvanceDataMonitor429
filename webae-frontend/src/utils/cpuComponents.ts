import { AE_CPU_COMPONENT_ICON_IDS, type AeCpuComponentKind } from '@/utils/aeCableColors';
import type { TopologyNodeDto } from '@/types/dto';

export interface CpuComponentGroup {
  kind: AeCpuComponentKind;
  iconId: string;
  count: number;
}

const COMPONENT_ORDER: AeCpuComponentKind[] = ['storage', 'accelerator', 'monitor', 'unit'];

/** Infer crafting CPU part kind from device label / class name. */
export function inferCpuComponentKind(label: string): AeCpuComponentKind {
  const hay = (label ?? '').toLowerCase();
  if (hay.includes('monitor')) return 'monitor';
  if (hay.includes('co-processor') || hay.includes('coprocessor') || hay.includes('accelerator')) {
    return 'accelerator';
  }
  if (hay.includes('storage')) return 'storage';
  return 'unit';
}

/** First non-empty iconItemId per component kind from devices[]. */
function iconIdsFromDevices(node: TopologyNodeDto): Map<AeCpuComponentKind, string> {
  const icons = new Map<AeCpuComponentKind, string>();
  for (const device of node.devices ?? []) {
    const kind = inferCpuComponentKind(device.displayName || device.className || '');
    const iconId = device.iconItemId?.trim();
    if (iconId && !icons.has(kind)) {
      icons.set(kind, iconId);
    }
  }
  return icons;
}

/** Summarize crafting CPU multiblock composition for icon grid display. */
export function summarizeCpuComponents(node: TopologyNodeDto): CpuComponentGroup[] {
  const summary = node.cpuSummary;
  const out: CpuComponentGroup[] = [];
  const deviceIcons = iconIdsFromDevices(node);

  const push = (kind: AeCpuComponentKind, count: number) => {
    if (count <= 0) return;
    out.push({
      kind,
      iconId: deviceIcons.get(kind) || AE_CPU_COMPONENT_ICON_IDS[kind],
      count,
    });
  };

  if (
    summary &&
    (summary.storageUnits > 0 || summary.acceleratorUnits > 0 || summary.monitorUnits > 0 || summary.unitCount > 0)
  ) {
    push('storage', summary.storageUnits);
    const accelerators =
      summary.acceleratorUnits > 0 ? summary.acceleratorUnits : summary.coProcessors ?? 0;
    push('accelerator', accelerators);
    push('monitor', summary.monitorUnits);
    const known = summary.storageUnits + accelerators + summary.monitorUnits;
    const remainder = Math.max(0, (summary.unitCount ?? 0) - known);
    push('unit', remainder);
    if (out.length > 0) return out;
  }

  const counts = new Map<AeCpuComponentKind, { count: number; iconId: string }>();
  for (const device of node.devices ?? []) {
    const kind = inferCpuComponentKind(device.displayName || device.className || '');
    const iconId = device.iconItemId || AE_CPU_COMPONENT_ICON_IDS[kind];
    const prev = counts.get(kind);
    if (prev) {
      counts.set(kind, { count: prev.count + 1, iconId: prev.iconId || iconId });
    } else {
      counts.set(kind, { count: 1, iconId });
    }
  }
  for (const kind of COMPONENT_ORDER) {
    const entry = counts.get(kind);
    if (entry && entry.count > 0) {
      out.push({ kind, iconId: entry.iconId, count: entry.count });
    }
  }
  return out;
}
