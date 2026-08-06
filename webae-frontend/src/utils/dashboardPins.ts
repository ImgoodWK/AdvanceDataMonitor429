import type { StorageDto, StorageItem, PowerDto, GtMachineDto } from '@/types/dto';
import type { DashboardPin } from '@/utils/presets';

export interface ResolvedPinValue {
  pin: DashboardPin;
  label: string;
  value: number;
  max?: number;
  iconItem?: { itemId?: string; registryName?: string; meta?: number; displayName?: string };
  secondary?: string;
}

export interface PinResolveContext {
  storage?: StorageDto | null;
  power?: PowerDto | null;
  gtMachines?: GtMachineDto[] | null;
  /** Network balance suggestions for balance pin current values. */
  balanceSuggestions?: Array<{
    itemId?: string;
    displayName: string;
    transferable: number;
    needyAmount: number;
    resourceType?: string;
  }> | null;
  scalarValue: (dataSource: string) => number;
}

function findItemAmount(
  storage: StorageDto | null | undefined,
  id: string
): { amount: number; item?: StorageItem } {
  if (!storage?.items) return { amount: 0 };
  const exact = storage.items.find(
    (i) =>
      i.itemId === id ||
      i.registryName === id ||
      (i.registryName && `${i.registryName}:${i.meta}` === id) ||
      (i.registryName && i.meta === 0 && `${i.registryName}:0` === id)
  );
  if (exact) return { amount: exact.amount, item: exact };
  return { amount: 0 };
}

function findFluidAmount(storage: StorageDto | null | undefined, id: string): number {
  if (!storage?.fluids) return 0;
  const needle = id.toLowerCase();
  let total = 0;
  for (const f of storage.fluids) {
    if (f.fluidName?.toLowerCase() === needle || f.fluidName?.toLowerCase().includes(needle)) {
      total += f.amount;
    }
  }
  return total;
}

function findEssentiaAmount(storage: StorageDto | null | undefined, id: string): number {
  if (!storage?.essentia) return 0;
  const e = storage.essentia.find((a) => a.aspect === id || a.aspect?.toLowerCase() === id.toLowerCase());
  return e?.amount ?? 0;
}

function findCpu(storage: StorageDto | null | undefined, id: string) {
  const name = id.startsWith('cpu:') ? id.slice(4) : id;
  return storage?.cpus?.find((c) => c.name === name) ?? null;
}

function findGt(machines: GtMachineDto[] | null | undefined, id: string): GtMachineDto | null {
  const key = id.startsWith('gt:') ? id.slice(3) : id;
  const parts = key.split(':');
  if (parts.length !== 4) return null;
  const [dim, x, y, z] = parts.map((p) => Number(p));
  if ([dim, x, y, z].some((n) => Number.isNaN(n))) return null;
  return machines?.find((m) => m.dim === dim && m.x === x && m.y === y && m.z === z) ?? null;
}

function cpuField(cpu: NonNullable<ReturnType<typeof findCpu>>, field?: string): number {
  if (!cpu) return 0;
  switch (field) {
    case 'storedItems': return cpu.storedItems;
    case 'maxItems': return cpu.maxItems;
    case 'usedStorage': return cpu.usedStorage;
    case 'availableStorage': return cpu.availableStorage;
    case 'coProcessors': return cpu.coProcessors;
    case 'elapsedTime': return cpu.elapsedTime;
    case 'isBusy': return cpu.isBusy ? 1 : 0;
    case 'finalOutputAmount': return cpu.finalOutputAmount;
    case 'craftingProgress':
    case 'progress':
    default:
      return cpu.craftingProgress;
  }
}

function gtField(m: GtMachineDto, field?: string): number {
  switch (field) {
    case 'storedEU': return m.storedEU;
    case 'euCapacity': return m.euCapacity;
    case 'isActive': return m.isActive ? 1 : 0;
    case 'parallelCount': return m.parallelCount;
    case 'progressTime': return m.progressTime;
    case 'maxProgressTime': return m.maxProgressTime;
    case 'progressPercent':
    case 'progress':
    default:
      return m.progressPercent;
  }
}

export function resolvePinValue(pin: DashboardPin, ctx: PinResolveContext): ResolvedPinValue {
  const label = pin.label || pin.id;
  switch (pin.kind) {
    case 'item': {
      const { amount, item } = findItemAmount(ctx.storage, pin.id);
      return {
        pin,
        label: pin.label || item?.displayName || item?.registryName || pin.id,
        value: amount,
        iconItem: item || { itemId: pin.id, registryName: pin.id },
      };
    }
    case 'fluid': {
      return {
        pin,
        label,
        value: findFluidAmount(ctx.storage, pin.id),
      };
    }
    case 'essentia': {
      return {
        pin,
        label,
        value: findEssentiaAmount(ctx.storage, pin.id),
      };
    }
    case 'scalar':
    case 'power': {
      return {
        pin,
        label: pin.label || pin.id,
        value: ctx.scalarValue(pin.id),
      };
    }
    case 'cpu': {
      const cpu = findCpu(ctx.storage, pin.id);
      const value = cpu ? cpuField(cpu, pin.metricField) : 0;
      let max: number | undefined;
      if (pin.metricField === 'storedItems' || !pin.metricField || pin.metricField === 'craftingProgress') {
        max = cpu?.maxItems || undefined;
      }
      return {
        pin,
        label: pin.label || cpu?.name || pin.id,
        value,
        max,
        secondary: cpu?.isBusy ? 'busy' : 'idle',
      };
    }
    case 'gt': {
      const m = findGt(ctx.gtMachines, pin.id);
      return {
        pin,
        label: pin.label || m?.recipeMapName || m?.statusText || pin.id,
        value: m ? gtField(m, pin.metricField) : 0,
        max: 100,
        secondary: m?.statusText,
      };
    }
    case 'balance': {
      const list = ctx.balanceSuggestions || [];
      const hit = list.find(
        (s) =>
          (s.itemId && s.itemId === pin.id) ||
          s.displayName === pin.id ||
          s.displayName === pin.label
      );
      return {
        pin,
        label: pin.label || hit?.displayName || pin.id,
        value: hit ? hit.transferable || hit.needyAmount : 0,
        secondary: hit?.resourceType,
      };
    }
    default:
      return { pin, label, value: 0 };
  }
}

export function resolvePins(pins: DashboardPin[] | undefined, ctx: PinResolveContext): ResolvedPinValue[] {
  if (!pins?.length) return [];
  return pins.map((p) => resolvePinValue(p, ctx));
}

export function entityApiKey(pin: DashboardPin): string | null {
  if (pin.kind === 'cpu') {
    return pin.id.startsWith('cpu:') ? pin.id : `cpu:${pin.id}`;
  }
  if (pin.kind === 'gt') {
    return pin.id.startsWith('gt:') ? pin.id : `gt:${pin.id}`;
  }
  return null;
}
