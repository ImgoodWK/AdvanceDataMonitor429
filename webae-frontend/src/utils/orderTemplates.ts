import { getApiClient } from '@/api/client';
import type { OrderTemplate, OrderTemplatesResponse } from '@/types/dto';

export async function fetchOrderTemplates(): Promise<OrderTemplate[]> {
  const data = await getApiClient().get<OrderTemplatesResponse>('/api/order/templates');
  if (!data.success) {
    throw new Error('Failed to load order templates');
  }
  return data.templates || [];
}

export async function saveOrderTemplates(templates: OrderTemplate[]): Promise<OrderTemplate[]> {
  const data = await getApiClient().put<OrderTemplatesResponse>('/api/order/templates', { templates });
  if (!data.success) {
    throw new Error('Failed to save order templates');
  }
  return data.templates || [];
}

export function newTemplateId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `tpl-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
}

/** Match storage item by display or registry name (case-insensitive). */
export function findStorageAmount(
  items: { displayName?: string; registryName?: string; amount: number }[] | undefined,
  itemName: string
): number {
  if (!items || !itemName.trim()) return 0;
  const q = itemName.trim().toLowerCase();
  for (const it of items) {
    const dn = (it.displayName || '').toLowerCase();
    const rn = (it.registryName || '').toLowerCase();
    if (dn === q || rn === q || dn.includes(q) || rn.includes(q)) {
      return it.amount;
    }
  }
  return 0;
}

/** Compute restock gap rows from template targets vs current storage. */
export function computeStorageGaps(
  templateItems: OrderTemplate['items'],
  storageItems: { displayName?: string; registryName?: string; amount: number }[] | undefined,
  skipZeroStock = false
): { itemName: string; amount: number; patternId?: string }[] {
  const rows: { itemName: string; amount: number; patternId?: string }[] = [];
  for (const item of templateItems) {
    if (item.patternId) {
      rows.push({ itemName: item.itemName, amount: item.amount, patternId: item.patternId });
      continue;
    }
    const name = item.itemName.trim();
    if (!name) continue;
    const stock = findStorageAmount(storageItems, name);
    if (skipZeroStock && stock <= 0) continue;
    const gap = Math.max(0, item.amount - stock);
    if (gap > 0) {
      rows.push({ itemName: name, amount: gap });
    }
  }
  return rows;
}
