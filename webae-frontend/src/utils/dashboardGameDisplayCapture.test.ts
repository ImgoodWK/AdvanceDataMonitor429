import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { GAME_DISPLAY_BINDING_FORMAT } from './dashboardGameDisplayBinding';

describe('resolveGameDisplayCaptureRoot', () => {
  it('returns preferred root when document is unavailable', async () => {
    const { resolveGameDisplayCaptureRoot } = await import('./dashboardGameDisplayCapture');
    const preferred = { isConnected: true } as HTMLElement;
    expect(resolveGameDisplayCaptureRoot(preferred)).toBe(preferred);
  });
});

describe('pushBrowserDisplayFrame', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.doUnmock('modern-screenshot');
  });

  it('posts jpeg to frame endpoint', async () => {
    vi.doMock('modern-screenshot', () => ({
      domToBlob: async () => new Blob([new Uint8Array(512)], { type: 'image/jpeg' }),
    }));
    const { pushBrowserDisplayFrame } = await import('./dashboardGameDisplayCapture');
    const preferred = {
      isConnected: true,
      scrollWidth: 960,
      clientWidth: 960,
    } as HTMLElement;
    const postBinary = vi.fn(async (url: string, file: Blob, contentType?: string) => {
      expect(url).toContain('/api/display/abc/frame.jpg');
      expect(url).toContain('token=tok');
      expect(file.type).toContain('jpeg');
      expect(contentType).toBe('image/jpeg');
      return { success: true };
    });
    const ok = await pushBrowserDisplayFrame({
      binding: {
        format: GAME_DISPLAY_BINDING_FORMAT,
        version: 1,
        mode: 'dashboard_live',
        displayId: 'abc',
        viewToken: 'tok',
        title: 't',
        exportedAt: 1,
        viewportHint: { width: 512, height: 384 },
      },
      preferredRoot: preferred,
      postBinary,
    });
    expect(ok).toBe(true);
    expect(postBinary).toHaveBeenCalledTimes(1);
  });
});
