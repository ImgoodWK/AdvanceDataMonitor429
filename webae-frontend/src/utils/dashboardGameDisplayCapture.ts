import { domToBlob } from 'modern-screenshot';
import type { GameDisplayBinding } from '@/utils/dashboardGameDisplayBinding';

const PUSH_INTERVAL_MS = 3500;
const MAX_CAPTURE_WIDTH = 1280;

export type PushBinaryFn = (
  url: string,
  file: Blob | File,
  contentType?: string
) => Promise<{ success?: boolean }>;

/** Prefer present-mode content, then dashboard grid, then full page shell. */
export function resolveGameDisplayCaptureRoot(preferred?: HTMLElement | null): HTMLElement {
  if (typeof document === 'undefined') {
    if (preferred) return preferred;
    throw new Error('document_unavailable');
  }
  const present = document.documentElement.classList.contains('webae-dashboard-present')
    || document.body.classList.contains('webae-dashboard-present');
  if (present) {
    const appContent = document.querySelector('.app-content') as HTMLElement | null;
    if (appContent) return appContent;
    const pageBody = document.querySelector('.page-shell__body') as HTMLElement | null;
    if (pageBody) return pageBody;
  }
  if (preferred && preferred.isConnected) return preferred;
  const wrap = document.querySelector('.dashboard-grid-wrap') as HTMLElement | null;
  if (wrap) return wrap;
  const appContent = document.querySelector('.app-content') as HTMLElement | null;
  if (appContent) return appContent;
  return document.body;
}

export async function captureDashboardViewportJpeg(
  preferred?: HTMLElement | null,
  quality = 0.82
): Promise<Blob> {
  const root = resolveGameDisplayCaptureRoot(preferred);
  const width = Math.min(MAX_CAPTURE_WIDTH, Math.max(320, Math.round(root.scrollWidth || root.clientWidth || 960)));
  const blob = await domToBlob(root, {
    type: 'image/jpeg',
    quality,
    width,
    backgroundColor: '#0b1220',
    scale: 1,
  });
  if (!blob || blob.size < 256) {
    throw new Error('capture_empty');
  }
  return blob;
}

export async function pushBrowserDisplayFrame(input: {
  binding: GameDisplayBinding;
  preferredRoot?: HTMLElement | null;
  width?: number;
  postBinary: PushBinaryFn;
}): Promise<boolean> {
  const jpeg = await captureDashboardViewportJpeg(input.preferredRoot);
  const id = encodeURIComponent(input.binding.displayId);
  const token = encodeURIComponent(input.binding.viewToken);
  const width = input.width ?? Math.min(1024, Math.max(256, Math.round(input.binding.viewportHint?.width || 512)));
  const url = `/api/display/${id}/frame.jpg?token=${token}&width=${width}`;
  const resp = await input.postBinary(url, jpeg, 'image/jpeg');
  return !!resp?.success;
}

let livePushTimer: number | null = null;
let livePushBinding: GameDisplayBinding | null = null;
let livePushRoot: HTMLElement | null = null;
let livePushBinary: PushBinaryFn | null = null;
let livePushInFlight = false;

async function tickLivePush(): Promise<void> {
  if (!livePushBinding || !livePushBinary) return;
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
  if (livePushInFlight) return;
  livePushInFlight = true;
  try {
    await pushBrowserDisplayFrame({
      binding: livePushBinding,
      preferredRoot: livePushRoot,
      postBinary: livePushBinary,
    });
  } catch {
    // Keep interval; next tick may succeed after layout settles.
  } finally {
    livePushInFlight = false;
  }
}

/** Keep pushing browser JPEG while the WebAE tab stays open (approx live mirror). */
export function startLiveBrowserFramePush(input: {
  binding: GameDisplayBinding;
  preferredRoot?: HTMLElement | null;
  postBinary: PushBinaryFn;
}): void {
  stopLiveBrowserFramePush();
  livePushBinding = input.binding;
  livePushRoot = input.preferredRoot || null;
  livePushBinary = input.postBinary;
  void tickLivePush();
  if (typeof window === 'undefined') return;
  livePushTimer = window.setInterval(() => {
    void tickLivePush();
  }, PUSH_INTERVAL_MS);
}

export function stopLiveBrowserFramePush(): void {
  if (livePushTimer != null && typeof window !== 'undefined') {
    window.clearInterval(livePushTimer);
  }
  livePushTimer = null;
  livePushBinding = null;
  livePushRoot = null;
  livePushBinary = null;
  livePushInFlight = false;
}
