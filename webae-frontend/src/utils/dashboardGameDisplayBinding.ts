export const GAME_DISPLAY_BINDING_FORMAT = 'textech-webae-display-binding' as const;
export const GAME_DISPLAY_BINDING_VERSION = 1 as const;

export type GameDisplayBindingMode = 'dashboard_live' | 'live_url' | 'dashboard_snapshot';

export interface GameDisplayBinding {
  format: typeof GAME_DISPLAY_BINDING_FORMAT;
  version: typeof GAME_DISPLAY_BINDING_VERSION;
  mode: GameDisplayBindingMode;
  displayId: string;
  viewToken: string;
  title: string;
  exportedAt: number;
  viewportHint?: { width: number; height: number };
  webaeOrigin?: string;
  embedPath?: string;
  /** Optional live_url mode target. */
  url?: string;
}

export function isGameDisplayBinding(value: unknown): value is GameDisplayBinding {
  if (!value || typeof value !== 'object') return false;
  const o = value as Record<string, unknown>;
  return (
    o.format === GAME_DISPLAY_BINDING_FORMAT &&
    o.version === GAME_DISPLAY_BINDING_VERSION &&
    typeof o.displayId === 'string' &&
    typeof o.viewToken === 'string' &&
    (o.mode === 'dashboard_live' || o.mode === 'live_url' || o.mode === 'dashboard_snapshot')
  );
}

export function parseGameDisplayBindingJson(json: string): GameDisplayBinding {
  const parsed = JSON.parse(json) as unknown;
  if (!isGameDisplayBinding(parsed)) {
    throw new Error('invalid_display_binding');
  }
  return parsed;
}

export function buildGameDisplayBindingJson(binding: GameDisplayBinding): string {
  return JSON.stringify({
    format: GAME_DISPLAY_BINDING_FORMAT,
    version: GAME_DISPLAY_BINDING_VERSION,
    mode: binding.mode,
    displayId: binding.displayId,
    viewToken: binding.viewToken,
    title: (binding.title || 'WebAE Dashboard').replace(/\s+/g, ' ').trim().slice(0, 96),
    exportedAt: binding.exportedAt || Date.now(),
    viewportHint: binding.viewportHint,
    webaeOrigin: binding.webaeOrigin || '',
    embedPath: binding.embedPath,
    url: binding.url,
  });
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', 'true');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const copied = document.execCommand('copy');
    area.remove();
    if (!copied) throw new Error('clipboard_unavailable');
  }
}

export interface PublishDisplayResponse {
  success: boolean;
  display?: {
    id: string;
    title: string;
    viewportWidth: number;
    viewportHeight: number;
    updatedAt: number;
  };
  binding?: GameDisplayBinding;
  message?: string;
}

/** Publish current dashboard layout/settings and copy a live binding JSON for the in-game monitor. */
export async function publishAndCopyDashboardLiveBinding(input: {
  title: string;
  layout: unknown;
  viewportWidth?: number;
  viewportHeight?: number;
  reuseId?: string;
  postJson: (url: string, body: unknown) => Promise<PublishDisplayResponse>;
}): Promise<GameDisplayBinding> {
  const resp = await input.postJson('/api/display', {
    title: input.title,
    layout: input.layout,
    viewportWidth: input.viewportWidth ?? 960,
    viewportHeight: input.viewportHeight ?? 720,
    id: input.reuseId,
  });
  if (!resp?.success || !resp.binding || !isGameDisplayBinding(resp.binding)) {
    throw new Error(resp?.message || 'publish_failed');
  }
  const json = buildGameDisplayBindingJson(resp.binding);
  await copyText(json);
  return resp.binding;
}
