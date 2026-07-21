import { useEffect, useState } from 'react';
import { Empty, Spin } from 'antd';
import { useAppContext } from '@/context/AppContext';
import {
  DASHBOARD_CONFIG_KEY,
  DEFAULT_DASHBOARD_SETTINGS,
  migrateDashboardWidgets,
  type DashboardSettings,
} from '@/utils/presets';
import { flattenWidgets } from '@/utils/dashboardTree';
import { Dashboard } from '@/pages/Dashboard';

function parseEmbedParams(): { id: string; token: string } | null {
  const path = window.location.pathname || '';
  const match = path.match(/\/embed\/dashboard\/([^/?#]+)/);
  const params = new URLSearchParams(window.location.search);
  const token = params.get('token') || params.get('viewToken') || '';
  const id = match?.[1] || params.get('id') || '';
  if (!id || !token) return null;
  return { id, token };
}

/**
 * Shell-less live dashboard for /embed/dashboard/:id — used by in-game capture.
 * Bootstraps display viewToken auth, loads published layout into local settings, then reuses Dashboard.
 */
export function EmbedDashboard() {
  const { login, setSelectedNetworks } = useAppContext();
  const [phase, setPhase] = useState<'boot' | 'ready' | 'error'>('boot');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    document.body.classList.add('webae-embed-dashboard');
    document.documentElement.classList.add('webae-embed-dashboard');
    return () => {
      document.body.classList.remove('webae-embed-dashboard');
      document.documentElement.classList.remove('webae-embed-dashboard');
      document.documentElement.removeAttribute('data-webae-capture-ready');
    };
  }, []);

  // Headless Chrome --screenshot waits on virtual time; mark when layout is painted so
  // DisplayCaptureService can rely on a settled embed (capture=1 query is advisory).
  useEffect(() => {
    if (phase === 'boot') {
      document.documentElement.removeAttribute('data-webae-capture-ready');
      return;
    }
    let cancelled = false;
    // Error UI is still visible content — mark ready so capture is not stuck on dark empty shell.
    if (phase === 'error') {
      document.documentElement.setAttribute('data-webae-capture-ready', 'error');
      return;
    }
    const settleMs = /[?&]capture=1(?:&|$)/.test(window.location.search || '') ? 800 : 400;
    const timer = window.setTimeout(() => {
      if (cancelled) return;
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          if (!cancelled) {
            document.documentElement.setAttribute('data-webae-capture-ready', '1');
          }
        });
      });
    }, settleMs);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [phase]);

  useEffect(() => {
    let cancelled = false;
    async function boot() {
      const embed = parseEmbedParams();
      if (!embed) {
        setError('missing_embed_params');
        setPhase('error');
        return;
      }
      try {
        // Prefer public layout+token path: works even when viewToken is not a WebAuth owner token.
        // Still call login so Dashboard live APIs can reuse Bearer = viewToken (display guest session).
        await login(embed.token);
        if (cancelled) return;
        const resp = await fetch(
          `/api/display/${encodeURIComponent(embed.id)}/layout?token=${encodeURIComponent(embed.token)}`
        );
        const data = (await resp.json()) as {
          success?: boolean;
          layout?: Partial<DashboardSettings>;
          code?: string;
          message?: string;
        };
        if (cancelled) return;
        if (!resp.ok || !data?.success || !data.layout) {
          throw new Error(data?.code || data?.message || 'layout_missing');
        }
        const next: DashboardSettings = {
          ...DEFAULT_DASHBOARD_SETTINGS,
          ...data.layout,
          defaultColors: {
            ...DEFAULT_DASHBOARD_SETTINGS.defaultColors,
            ...(data.layout.defaultColors || {}),
          },
          colorPresets: data.layout.colorPresets ?? [],
          widgets: migrateDashboardWidgets(
            Array.isArray(data.layout.widgets) ? data.layout.widgets : DEFAULT_DASHBOARD_SETTINGS.widgets
          ),
        };
        localStorage.setItem(DASHBOARD_CONFIG_KEY, JSON.stringify(next));
        const nets = new Set<number>();
        for (const w of flattenWidgets(next.widgets)) {
          if (typeof w.networkId === 'number' && Number.isFinite(w.networkId)) nets.add(w.networkId);
        }
        if (nets.size === 0) nets.add(0);
        setSelectedNetworks(Array.from(nets));
        // Signal Dashboard to treat this session as embed (hide chrome).
        sessionStorage.setItem('webae_embed_mode', '1');
        setPhase('ready');
      } catch (e) {
        if (!cancelled) {
          setError((e as Error).message || 'embed_failed');
          setPhase('error');
        }
      }
    }
    void boot();
    return () => {
      cancelled = true;
    };
  }, [login, setSelectedNetworks]);

  if (phase === 'error') {
    return (
      <div style={{ padding: 24 }}>
        <Empty description={error || 'error'} />
      </div>
    );
  }
  if (phase !== 'ready') {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }
  return <Dashboard />;
}

export function isEmbedDashboardMode(): boolean {
  try {
    return sessionStorage.getItem('webae_embed_mode') === '1'
      || /\/embed\/dashboard\//.test(window.location.pathname || '');
  } catch {
    return /\/embed\/dashboard\//.test(window.location.pathname || '');
  }
}
