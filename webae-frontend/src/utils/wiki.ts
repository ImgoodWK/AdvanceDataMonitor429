/** GTNH 中文 Wiki（灰机）搜索页基础 URL */
export const GTNH_WIKI_SEARCH_BASE = 'https://gtnh.huijiwiki.com/index.php?search=';

export interface WikiSearchTarget {
  displayName?: string;
  registryName?: string;
  itemId?: string;
  fluidName?: string;
  alt?: string;
}

/** 从物品/流体信息提取 Wiki 搜索关键词（优先显示名）。 */
export function wikiSearchLabel(target?: WikiSearchTarget | null): string {
  if (!target) return '';
  const display = target.displayName?.trim();
  if (display) return display;
  const alt = target.alt?.trim();
  if (alt) return alt;
  const fluid = target.fluidName?.trim();
  if (fluid) return fluid;
  const id = target.itemId?.trim() || target.registryName?.trim();
  if (!id) return '';
  const bare = id.startsWith('fluid:') ? id.slice('fluid:'.length) : id;
  const colon = bare.lastIndexOf(':');
  const namePart = colon >= 0 ? bare.slice(colon + 1) : bare;
  return namePart.replace(/_/g, ' ');
}

export function gtnhWikiSearchUrl(query: string): string {
  return GTNH_WIKI_SEARCH_BASE + encodeURIComponent(query.trim());
}

/** 在新标签页打开 GTNH 中文 Wiki 搜索结果。 */
export function openGtnhWikiSearch(target?: WikiSearchTarget | null): boolean {
  const label = wikiSearchLabel(target);
  if (!label) return false;
  window.open(gtnhWikiSearchUrl(label), '_blank', 'noopener,noreferrer');
  return true;
}
