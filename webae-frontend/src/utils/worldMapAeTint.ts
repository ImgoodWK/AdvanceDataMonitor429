import {
  DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS,
  resolveCategoryIdFromPixel,
  WORLD_MAP_AE_CATEGORY_IDS,
  type WorldMapAeCategoryId,
} from '@/utils/worldMapAeCategories';

export interface WorldMapAeColorPalette {
  categoryColors: Record<WorldMapAeCategoryId, string>;
  itemColorOverrides: Record<string, string>;
}

const tintCache = new Map<string, string>();

export function buildWorldMapAePalette(
  categoryColors: Record<WorldMapAeCategoryId, string>,
  itemColorOverrides: Record<string, string>
): WorldMapAeColorPalette {
  return {
    categoryColors: { ...DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS, ...categoryColors },
    itemColorOverrides: { ...itemColorOverrides },
  };
}

export function paletteHash(palette: WorldMapAeColorPalette): string {
  const cat = WORLD_MAP_AE_CATEGORY_IDS.map((id) => `${id}:${palette.categoryColors[id] ?? ''}`).join('|');
  const items = Object.keys(palette.itemColorOverrides)
    .sort()
    .map((k) => `${k}=${palette.itemColorOverrides[k]}`)
    .join(';');
  return `${cat}#${items}`;
}

function parseHex(hex: string): { r: number; g: number; b: number } | null {
  const raw = hex.trim().replace(/^#/, '');
  if (raw.length !== 6) return null;
  const n = parseInt(raw, 16);
  if (Number.isNaN(n)) return null;
  return { r: (n >> 16) & 0xff, g: (n >> 8) & 0xff, b: n & 0xff };
}

function colorForCategory(categoryByte: number, palette: WorldMapAeColorPalette): { r: number; g: number; b: number } {
  const key = resolveCategoryIdFromPixel(categoryByte);
  const hex = palette.categoryColors[key] ?? DEFAULT_WORLD_MAP_AE_CATEGORY_COLORS.other;
  return parseHex(hex) ?? { r: 0x88, g: 0x99, b: 0xaa };
}

/**
 * Tint a server AE category ID PNG blob using the user palette. Cached by tile key + palette hash + opacity.
 */
export async function tintAeIdBlob(
  blob: Blob,
  palette: WorldMapAeColorPalette,
  cacheKey: string,
  opacity = 1
): Promise<string> {
  const clampedOpacity = Math.max(0, Math.min(1, opacity));
  const hash = paletteHash(palette);
  const fullKey = `${cacheKey}:${hash}:${clampedOpacity.toFixed(3)}`;
  const cached = tintCache.get(fullKey);
  if (cached) {
    return cached;
  }

  const bitmap = await createImageBitmap(blob);
  const canvas = document.createElement('canvas');
  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    bitmap.close();
    return URL.createObjectURL(blob);
  }
  ctx.drawImage(bitmap, 0, 0);
  bitmap.close();

  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const data = imageData.data;
  for (let i = 0; i < data.length; i += 4) {
    const a = data[i + 3];
    if (a <= 0) {
      continue;
    }
    const categoryByte = data[i];
    const { r, g, b } = colorForCategory(categoryByte, palette);
    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
    data[i + 3] = Math.round(a * clampedOpacity);
  }
  ctx.putImageData(imageData, 0, 0);

  const outBlob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  const url = URL.createObjectURL(outBlob ?? blob);
  tintCache.set(fullKey, url);
  return url;
}

export function clearTintCache(): void {
  for (const url of tintCache.values()) {
    URL.revokeObjectURL(url);
  }
  tintCache.clear();
}
