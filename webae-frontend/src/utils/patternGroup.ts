import type { PatternItemEntry, PatternListEntryDto } from '@/types/dto';

export interface PatternProductGroup {
  /** 去重键：`<registryName>:<meta>` 或 `fluid:<registryName>` */
  key: string;
  /** 主产物（取首个样板的 outputs[0]） */
  primaryOutput: PatternItemEntry;
  /** 该产物的所有样板变体 */
  patterns: PatternListEntryDto[];
  /** 来源接口坐标去重集合 */
  sourceInterfaces: string[];
  /** 来源接口显示名去重集合 */
  sourceInterfaceNames: string[];
  /** 是否所有变体都是合成样板 */
  allCrafting: boolean;
}

function patternOutputKey(entry: PatternItemEntry | null | undefined): string {
  if (!entry || !entry.registryName) return '';
  if (entry.isFluid) return `fluid:${entry.registryName}`;
  return entry.meta && entry.meta > 0 ? `${entry.registryName}:${entry.meta}` : entry.registryName;
}

/**
 * 按主产物（outputs[0]）的 `registryName:meta` 去重，得到产物分组列表。
 * 同一产物多个样板时合并为一组，保留所有变体与来源接口信息。
 */
export function groupByPatternOutput(patterns: PatternListEntryDto[]): PatternProductGroup[] {
  const map = new Map<string, PatternProductGroup>();
  for (const p of patterns) {
    const out = p.outputs[0];
    if (!out || !out.registryName) continue;
    const key = patternOutputKey(out);
    if (!key) continue;
    const existing = map.get(key);
    if (existing) {
      existing.patterns.push(p);
      if (p.sourceInterface && !existing.sourceInterfaces.includes(p.sourceInterface)) {
        existing.sourceInterfaces.push(p.sourceInterface);
      }
      const name = p.sourceInterfaceName || p.sourceInterface;
      if (name && !existing.sourceInterfaceNames.includes(name)) {
        existing.sourceInterfaceNames.push(name);
      }
      existing.allCrafting = existing.allCrafting && p.crafting;
    } else {
      map.set(key, {
        key,
        primaryOutput: out,
        patterns: [p],
        sourceInterfaces: p.sourceInterface ? [p.sourceInterface] : [],
        sourceInterfaceNames: [p.sourceInterfaceName || p.sourceInterface].filter(Boolean),
        allCrafting: p.crafting,
      });
    }
  }
  return Array.from(map.values()).sort((a, b) => {
    const na = a.primaryOutput.displayName || a.primaryOutput.registryName || '';
    const nb = b.primaryOutput.displayName || b.primaryOutput.registryName || '';
    return na.localeCompare(nb);
  });
}
