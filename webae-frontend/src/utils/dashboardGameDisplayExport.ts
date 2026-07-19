export const GAME_DISPLAY_FORMAT = 'textech-webae-display-snapshot' as const;
export const GAME_DISPLAY_VERSION = 1 as const;

const MAX_VIEWPORT_WIDTH = 960;
const MAX_VIEWPORT_HEIGHT = 720;
const MAX_PRIMITIVES = 600;
const MAX_TEXT_LENGTH = 256;
const MAX_JSON_BYTES = 90 * 1024;

export type GameDisplayAlign = 'left' | 'center' | 'right';

export interface GameDisplayPrimitive {
  kind: 'rect' | 'ellipse' | 'text' | 'polyline';
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  fill?: string;
  stroke?: string;
  radius?: number;
  lineWidth?: number;
  text?: string;
  color?: string;
  size?: number;
  weight?: number;
  align?: GameDisplayAlign;
  points?: number[];
}

export interface GameDisplaySnapshot {
  format: typeof GAME_DISPLAY_FORMAT;
  version: typeof GAME_DISPLAY_VERSION;
  exportedAt: number;
  title: string;
  viewport: {
    width: number;
    height: number;
    background: string;
  };
  primitives: GameDisplayPrimitive[];
}

interface SnapshotBuildInput {
  title: string;
  width: number;
  height: number;
  background?: string;
  primitives: GameDisplayPrimitive[];
  exportedAt?: number;
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function parseCssChannel(value: string): number {
  return clamp(Math.round(Number(value) || 0), 0, 255);
}

/** Convert computed CSS colors to the compact #AARRGGBB format used by the game renderer. */
export function normalizeGameDisplayColor(value: string | null | undefined): string | null {
  const raw = (value || '').trim().toLowerCase();
  if (!raw || raw === 'transparent') return null;

  const rgb = raw.match(/^rgba?\(\s*([\d.]+)[, ]+\s*([\d.]+)[, ]+\s*([\d.]+)(?:\s*[,/]\s*([\d.]+))?\s*\)$/);
  if (rgb) {
    const r = parseCssChannel(rgb[1]);
    const g = parseCssChannel(rgb[2]);
    const b = parseCssChannel(rgb[3]);
    const a = clamp(Math.round((rgb[4] == null ? 1 : Number(rgb[4])) * 255), 0, 255);
    if (a === 0) return null;
    return `#${[a, r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
  }

  const hex = raw.match(/^#([0-9a-f]{3,8})$/i)?.[1];
  if (!hex) return null;
  if (hex.length === 3) {
    return `#FF${hex.split('').map((c) => c + c).join('').toUpperCase()}`;
  }
  if (hex.length === 4) {
    const [r, g, b, a] = hex.split('').map((c) => c + c);
    if (a === '00') return null;
    return `#${a}${r}${g}${b}`.toUpperCase();
  }
  if (hex.length === 6) return `#FF${hex.toUpperCase()}`;
  if (hex.length === 8) {
    const rrggbb = hex.slice(0, 6);
    const aa = hex.slice(6);
    if (aa === '00') return null;
    return `#${aa}${rrggbb}`.toUpperCase();
  }
  return null;
}

function sanitizePrimitive(p: GameDisplayPrimitive): GameDisplayPrimitive | null {
  if (!p || !['rect', 'ellipse', 'text', 'polyline'].includes(p.kind)) return null;
  if (p.kind === 'polyline') {
    const points = (p.points || [])
      .filter((n) => Number.isFinite(n))
      .slice(0, 256)
      .map(round);
    if (points.length < 4 || points.length % 2 !== 0) return null;
    return {
      kind: 'polyline',
      points,
      color: normalizeGameDisplayColor(p.color) || '#FFFFFFFF',
      lineWidth: round(clamp(p.lineWidth ?? 1, 0.2, 16)),
    };
  }

  const x = round(Number.isFinite(p.x) ? p.x! : 0);
  const y = round(Number.isFinite(p.y) ? p.y! : 0);
  const w = round(clamp(Number.isFinite(p.w) ? p.w! : 0, 0, 2400));
  const h = round(clamp(Number.isFinite(p.h) ? p.h! : 0, 0, 1800));
  if (w <= 0 || h <= 0) return null;

  if (p.kind === 'text') {
    const text = (p.text || '').replace(/\s+/g, ' ').trim().slice(0, MAX_TEXT_LENGTH);
    if (!text) return null;
    return {
      kind: 'text',
      x,
      y,
      w,
      h,
      text,
      color: normalizeGameDisplayColor(p.color) || '#FFFFFFFF',
      size: round(clamp(p.size ?? 14, 5, 96)),
      weight: clamp(Math.round(p.weight ?? 400), 100, 900),
      align: p.align === 'center' || p.align === 'right' ? p.align : 'left',
    };
  }

  const fill = normalizeGameDisplayColor(p.fill);
  const stroke = normalizeGameDisplayColor(p.stroke);
  if (!fill && !stroke) return null;
  return {
    kind: p.kind,
    x,
    y,
    w,
    h,
    fill: fill || undefined,
    stroke: stroke || undefined,
    radius: round(clamp(p.radius ?? 0, 0, 128)),
    lineWidth: round(clamp(p.lineWidth ?? 1, 0.2, 16)),
  };
}

export function buildDashboardGameDisplaySnapshot(input: SnapshotBuildInput): GameDisplaySnapshot {
  const width = clamp(Math.round(input.width || 1), 64, MAX_VIEWPORT_WIDTH);
  const height = clamp(Math.round(input.height || 1), 64, MAX_VIEWPORT_HEIGHT);
  const primitives: GameDisplayPrimitive[] = [];
  for (const primitive of input.primitives) {
    const clean = sanitizePrimitive(primitive);
    if (clean) primitives.push(clean);
    if (primitives.length >= MAX_PRIMITIVES) break;
  }
  return {
    format: GAME_DISPLAY_FORMAT,
    version: GAME_DISPLAY_VERSION,
    exportedAt: input.exportedAt ?? Date.now(),
    title: (input.title || 'WebAE Dashboard').replace(/\s+/g, ' ').trim().slice(0, 96),
    viewport: {
      width,
      height,
      background: normalizeGameDisplayColor(input.background) || '#FF08111F',
    },
    primitives,
  };
}

export function buildDashboardGameDisplayJson(input: SnapshotBuildInput): string {
  const snapshot = buildDashboardGameDisplaySnapshot(input);
  let json = JSON.stringify(snapshot);
  const byteLength = (value: string) => {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(value).length;
    return unescape(encodeURIComponent(value)).length;
  };
  while (snapshot.primitives.length > 0 && byteLength(json) > MAX_JSON_BYTES) {
    snapshot.primitives.splice(Math.max(0, snapshot.primitives.length - 16), 16);
    json = JSON.stringify(snapshot);
  }
  return json;
}

function isHidden(element: Element): boolean {
  const style = window.getComputedStyle(element);
  return style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || '1') <= 0;
}

function relativeRect(rect: DOMRect, rootRect: DOMRect, scale: number): Pick<GameDisplayPrimitive, 'x' | 'y' | 'w' | 'h'> {
  return {
    x: round((rect.left - rootRect.left) * scale),
    y: round((rect.top - rootRect.top) * scale),
    w: round(rect.width * scale),
    h: round(rect.height * scale),
  };
}

function textAlign(style: CSSStyleDeclaration): GameDisplayAlign {
  if (style.textAlign === 'center') return 'center';
  if (style.textAlign === 'right' || style.textAlign === 'end') return 'right';
  return 'left';
}

function svgPointToRoot(
  svg: SVGSVGElement,
  matrix: DOMMatrix,
  x: number,
  y: number,
  rootRect: DOMRect,
  scale: number
): [number, number] {
  const point = svg.createSVGPoint();
  point.x = x;
  point.y = y;
  const screen = point.matrixTransform(matrix);
  return [round((screen.x - rootRect.left) * scale), round((screen.y - rootRect.top) * scale)];
}

function captureSvgPrimitives(
  root: HTMLElement,
  rootRect: DOMRect,
  scale: number,
  push: (primitive: GameDisplayPrimitive) => void
): void {
  const geometries = root.querySelectorAll<SVGGraphicsElement>('svg polyline, svg polygon, svg line, svg path');
  geometries.forEach((element) => {
    if (isHidden(element)) return;
    const svg = element.ownerSVGElement;
    const matrix = element.getScreenCTM();
    if (!svg || !matrix) return;
    const style = window.getComputedStyle(element);
    const color = normalizeGameDisplayColor(style.stroke)
      ? style.stroke
      : normalizeGameDisplayColor(style.fill)
        ? style.fill
        : null;
    if (!color) return;
    const points: number[] = [];

    if (element instanceof SVGPolylineElement || element instanceof SVGPolygonElement) {
      for (let i = 0; i < element.points.numberOfItems && i < 96; i++) {
        const p = element.points.getItem(i);
        points.push(...svgPointToRoot(svg, matrix, p.x, p.y, rootRect, scale));
      }
      if (element instanceof SVGPolygonElement && points.length >= 4) points.push(points[0], points[1]);
    } else if (element instanceof SVGLineElement) {
      points.push(...svgPointToRoot(svg, matrix, element.x1.baseVal.value, element.y1.baseVal.value, rootRect, scale));
      points.push(...svgPointToRoot(svg, matrix, element.x2.baseVal.value, element.y2.baseVal.value, rootRect, scale));
    } else if (element instanceof SVGPathElement) {
      try {
        const length = element.getTotalLength();
        const samples = Math.max(4, Math.min(48, Math.ceil(length / 12)));
        for (let i = 0; i <= samples; i++) {
          const p = element.getPointAtLength((length * i) / samples);
          points.push(...svgPointToRoot(svg, matrix, p.x, p.y, rootRect, scale));
        }
      } catch {
        return;
      }
    }

    if (points.length >= 4) {
      push({
        kind: 'polyline',
        points,
        color,
        lineWidth: clamp((Number.parseFloat(style.strokeWidth) || 1) * scale, 0.5, 8),
      });
    }
  });

  root.querySelectorAll<SVGGraphicsElement>('svg rect, svg circle, svg ellipse').forEach((element) => {
    if (isHidden(element)) return;
    const rect = element.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) return;
    const style = window.getComputedStyle(element);
    push({
      kind: element instanceof SVGRectElement ? 'rect' : 'ellipse',
      ...relativeRect(rect, rootRect, scale),
      fill: normalizeGameDisplayColor(style.fill) ? style.fill : undefined,
      stroke: normalizeGameDisplayColor(style.stroke) ? style.stroke : undefined,
      lineWidth: clamp((Number.parseFloat(style.strokeWidth) || 1) * scale, 0.5, 8),
    });
  });
}

/** Capture the currently rendered dashboard as a compact, bounded vector/text snapshot for Minecraft. */
export function captureDashboardGameDisplayJson(root: HTMLElement, title: string): string {
  const rootRect = root.getBoundingClientRect();
  if (rootRect.width < 1 || rootRect.height < 1) throw new Error('dashboard_not_visible');
  const scale = Math.min(1, MAX_VIEWPORT_WIDTH / rootRect.width, MAX_VIEWPORT_HEIGHT / rootRect.height);
  const width = Math.max(64, Math.round(rootRect.width * scale));
  const height = Math.max(64, Math.round(rootRect.height * scale));
  const rootStyle = window.getComputedStyle(root);
  const bodyBackgroundCss = window.getComputedStyle(document.body).backgroundColor;
  const bodyBackground = normalizeGameDisplayColor(bodyBackgroundCss) ? bodyBackgroundCss : null;
  const primitives: GameDisplayPrimitive[] = [];
  const push = (primitive: GameDisplayPrimitive) => {
    if (primitives.length < MAX_PRIMITIVES) primitives.push(primitive);
  };

  const items = Array.from(root.children).filter((child): child is HTMLElement =>
    child instanceof HTMLElement && child.classList.contains('grid-stack-item')
  );
  for (const item of items) {
    const shell = item.querySelector<HTMLElement>('.widget-shell');
    if (!shell || isHidden(shell)) continue;
    const shellRect = shell.getBoundingClientRect();
    const shellStyle = window.getComputedStyle(shell);
    push({
      kind: 'rect',
      ...relativeRect(shellRect, rootRect, scale),
      fill: normalizeGameDisplayColor(shellStyle.backgroundColor) ? shellStyle.backgroundColor : '#101826E6',
      stroke: normalizeGameDisplayColor(shellStyle.borderColor) ? shellStyle.borderColor : '#4A6A8880',
      radius: (Number.parseFloat(shellStyle.borderRadius) || 8) * scale,
      lineWidth: (Number.parseFloat(shellStyle.borderWidth) || 1) * scale,
    });

    const body = shell.querySelector<HTMLElement>('.widget-shell-body') || shell;
    const descendants = body.querySelectorAll<HTMLElement>('*');
    for (const element of descendants) {
      if (primitives.length >= MAX_PRIMITIVES || isHidden(element)) break;
      if (element.closest('.dashboard-grid-edit-actions')) continue;
      const rect = element.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2 || rect.width * rect.height > shellRect.width * shellRect.height * 0.96) continue;
      const style = window.getComputedStyle(element);
      const fill = normalizeGameDisplayColor(style.backgroundColor) ? style.backgroundColor : null;
      const stroke = Number.parseFloat(style.borderWidth) > 0
        ? (normalizeGameDisplayColor(style.borderColor) ? style.borderColor : null)
        : null;
      if (!fill && !stroke) continue;
      push({
        kind: 'rect',
        ...relativeRect(rect, rootRect, scale),
        fill: fill || undefined,
        stroke: stroke || undefined,
        radius: (Number.parseFloat(style.borderRadius) || 0) * scale,
        lineWidth: (Number.parseFloat(style.borderWidth) || 1) * scale,
      });
    }

    captureSvgPrimitives(body, rootRect, scale, push);

    const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node && primitives.length < MAX_PRIMITIVES) {
      const parent = node.parentElement;
      const text = (node.textContent || '').replace(/\s+/g, ' ').trim();
      if (parent && text && !parent.closest('.dashboard-grid-edit-actions') && !isHidden(parent)) {
        const range = document.createRange();
        range.selectNodeContents(node);
        const rect = range.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          const style = window.getComputedStyle(parent);
          push({
            kind: 'text',
            ...relativeRect(rect, rootRect, scale),
            text,
            color: normalizeGameDisplayColor(style.color) ? style.color : '#FFFFFF',
            size: (Number.parseFloat(style.fontSize) || 14) * scale,
            weight: Number.parseInt(style.fontWeight, 10) || (style.fontWeight === 'bold' ? 700 : 400),
            align: textAlign(style),
          });
        }
      }
      node = walker.nextNode();
    }
  }

  return buildDashboardGameDisplayJson({
    title,
    width,
    height,
    background: normalizeGameDisplayColor(rootStyle.backgroundColor)
      ? rootStyle.backgroundColor
      : bodyBackground || '#08111F',
    primitives,
  });
}

export async function copyDashboardGameDisplayJson(root: HTMLElement, title: string): Promise<string> {
  const json = captureDashboardGameDisplayJson(root, title);
  try {
    await navigator.clipboard.writeText(json);
  } catch {
    const area = document.createElement('textarea');
    area.value = json;
    area.setAttribute('readonly', 'true');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const copied = document.execCommand('copy');
    area.remove();
    if (!copied) throw new Error('clipboard_unavailable');
  }
  return json;
}
