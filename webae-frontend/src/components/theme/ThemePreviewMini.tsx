import { useEffect, useMemo, useRef } from 'react';

import { COLOR_SCHEMES, type EffectsLevel, type ThemeColor } from '@/theme/colors';
import { collectPreviewVars } from '@/theme/collectPreviewVars';
import { LAYOUT_PRESETS, type ThemeLayout } from '@/theme/layouts';
import { resolvePageStyle, type PageStyle } from '@/theme/pageStyles';

export interface ThemePreviewMiniProps {
  themeColor: ThemeColor;
  themeLayout?: ThemeLayout;
  pageStyle?: PageStyle | string;
  effectsLevel?: EffectsLevel;
  /** Color strip emphasis when browsing color options. */
  emphasize?: 'color' | 'layout' | 'style' | 'all';
  className?: string;
  title?: string;
}

let cachedParentCss: string | null = null;

function collectParentStylesCss(): string {
  if (cachedParentCss != null) return cachedParentCss;
  const chunks: string[] = [];
  for (let i = 0; i < document.styleSheets.length; i++) {
    const sheet = document.styleSheets[i];
    try {
      const rules = sheet.cssRules;
      if (!rules) continue;
      for (let j = 0; j < rules.length; j++) {
        chunks.push(rules[j].cssText);
      }
    } catch {
      // cross-origin sheets — skip
    }
  }
  cachedParentCss = chunks.join('\n');
  return cachedParentCss;
}

/** Call after hot-reload / theme CSS changes if previews look stale. */
export function invalidateThemePreviewCssCache() {
  cachedParentCss = null;
}

function buildPreviewHtml(opts: {
  themeColor: ThemeColor;
  themeLayout: ThemeLayout;
  pageStyle: PageStyle;
  effectsLevel: EffectsLevel;
  vars: Record<string, string>;
  emphasize: string;
  isDark: boolean;
}): string {
  const { themeColor, themeLayout, pageStyle, effectsLevel, vars, emphasize, isDark } = opts;
  const layout = LAYOUT_PRESETS[themeLayout] || LAYOUT_PRESETS.standard;
  const side = layout.sidebarSide;
  const navChrome = layout.navChrome;
  const varCss = Object.entries(vars)
    .map(([k, v]) => `${k}:${String(v).replace(/;/g, '')}`)
    .join(';');

  const sidebarRight = side === 'right';
  const topNav = side === 'none' && navChrome === 'top';
  const bottomNav = side === 'none' && navChrome === 'bottom';
  const floating = themeLayout === 'floating';
  const shellStyle = floating
    ? 'margin:8px;border-radius:12px;overflow:hidden;border:1px solid var(--border)'
    : '';

  return `<!DOCTYPE html><html data-theme-color="${themeColor}" data-theme-layout="${themeLayout}" data-page-style="${pageStyle}" data-effects-level="${effectsLevel}" data-ui-mode="advanced" data-color-scheme="${isDark ? 'dark' : 'light'}" style="${varCss};font-size:11px"><head><meta charset="utf-8"/></head><body style="margin:0;background:var(--bg-primary);color:var(--text-primary);font-family:var(--style-font-ui,system-ui,sans-serif);overflow:hidden">
<div class="theme-preview-root" data-emphasize="${emphasize}" style="display:flex;flex-direction:column;height:100vh;${shellStyle}">
  ${topNav ? `<div style="height:18px;background:var(--bg-secondary);border-bottom:1px solid var(--border);display:flex;gap:6px;align-items:center;padding:0 8px"><span style="width:28px;height:6px;background:var(--accent);border-radius:2px;opacity:.85"></span><span style="width:22px;height:5px;background:var(--text-dim);border-radius:2px;opacity:.5"></span><span style="width:22px;height:5px;background:var(--text-dim);border-radius:2px;opacity:.5"></span></div>` : ''}
  <div style="display:flex;flex:1;min-height:0;${sidebarRight ? 'flex-direction:row-reverse' : ''}">
    ${side !== 'none' ? `<aside style="width:${floating ? '52px' : '44px'};background:var(--sidebar-bg);border-${sidebarRight ? 'left' : 'right'}:1px solid var(--border);padding:6px 4px;display:flex;flex-direction:column;gap:4px">
      <div class="webae-nav-item webae-nav-item--active" style="height:10px;background:var(--sidebar-active);border-radius:4px"></div>
      <div class="webae-nav-item" style="height:10px;background:var(--sidebar-hover);border-radius:4px;opacity:.7"></div>
      <div class="webae-nav-item" style="height:10px;background:var(--sidebar-hover);border-radius:4px;opacity:.5"></div>
    </aside>` : ''}
    <main class="app-content" style="flex:1;position:relative;padding:8px;min-width:0;overflow:hidden">
      <div class="page-shell__header" style="margin-bottom:8px">
        <div class="page-shell__title" style="font-size:12px;font-weight:var(--style-title-weight,600);color:var(--text-primary)">WebAE</div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:6px">
        <div class="widget-shell stat-card" style="background:var(--style-card-bg,var(--bg-card));border:var(--style-border-width,1px) solid var(--style-chrome-border,var(--border));border-radius:var(--style-radius,8px);padding:8px;box-shadow:var(--style-shadow);min-height:42px">
          <div class="stat-card-label" style="font-size:8px;color:var(--text-dim);margin-bottom:4px">Items</div>
          <div class="stat-card-value" style="font-size:14px;font-weight:700;color:var(--accent)">12.4k</div>
        </div>
        <div class="widget-shell stat-card" style="background:var(--style-card-bg,var(--bg-card));border:var(--style-border-width,1px) solid var(--style-chrome-border,var(--border));border-radius:var(--style-radius,8px);padding:8px;box-shadow:var(--style-shadow);min-height:42px">
          <div class="stat-card-label" style="font-size:8px;color:var(--text-dim);margin-bottom:4px">CPU</div>
          <div style="height:16px;margin-top:4px;background:linear-gradient(90deg,var(--accent),var(--accent-dim));border-radius:3px;opacity:.85"></div>
        </div>
        <div class="widget-shell" style="grid-column:1/-1;background:var(--style-card-bg,var(--bg-card));border:var(--style-border-width,1px) solid var(--style-chrome-border,var(--border));border-radius:var(--style-radius,8px);padding:8px;box-shadow:var(--style-shadow);min-height:28px;display:flex;align-items:center;gap:8px">
          <span style="display:inline-block;padding:3px 8px;border-radius:var(--style-radius-sm,6px);background:var(--accent);color:#fff;font-size:8px;font-weight:600">Primary</span>
          <span style="font-size:8px;color:var(--text-secondary)">Sample chrome</span>
        </div>
      </div>
    </main>
  </div>
  ${bottomNav ? `<div style="height:18px;background:var(--bg-secondary);border-top:1px solid var(--border);display:flex;justify-content:center;gap:10px;align-items:center"><span style="width:8px;height:8px;border-radius:50%;background:var(--accent)"></span><span style="width:8px;height:8px;border-radius:50%;background:var(--text-dim);opacity:.4"></span><span style="width:8px;height:8px;border-radius:50%;background:var(--text-dim);opacity:.4"></span></div>` : ''}
</div>
</body></html>`;
}

/** Near-real theme thumbnail via iframe (isolates documentElement data-* from host app). */
export function ThemePreviewMini({
  themeColor,
  themeLayout = 'standard',
  pageStyle = 'classic',
  effectsLevel = 'subtle',
  emphasize = 'all',
  className,
  title,
}: ThemePreviewMiniProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const styleId = resolvePageStyle(pageStyle);
  const vars = useMemo(
    () => collectPreviewVars(themeColor, themeLayout, styleId, effectsLevel),
    [themeColor, themeLayout, styleId, effectsLevel]
  );
  const schemeDark = COLOR_SCHEMES[themeColor]?.isDark ?? true;

  useEffect(() => {
    const iframe = iframeRef.current;
    if (!iframe) return;
    const doc = iframe.contentDocument;
    if (!doc) return;

    const html = buildPreviewHtml({
      themeColor,
      themeLayout,
      pageStyle: styleId,
      effectsLevel,
      vars,
      emphasize,
      isDark: schemeDark,
    });
    doc.open();
    doc.write(html);
    doc.close();

    const styleEl = doc.createElement('style');
    styleEl.textContent =
      collectParentStylesCss() +
      `
      html, body { width: 100%; height: 100%; }
      .widget-shell, .stat-card { position: relative; }
      * { box-sizing: border-box; }
    `;
    doc.head.appendChild(styleEl);
  }, [themeColor, themeLayout, styleId, effectsLevel, vars, emphasize, schemeDark]);

  return (
    <div
      className={className}
      title={title}
      style={{
        position: 'relative',
        width: '100%',
        height: 112,
        borderRadius: 8,
        overflow: 'hidden',
        border: '1px solid var(--border)',
        background: 'var(--bg-secondary)',
        contain: 'strict',
      }}
    >
      <iframe
        ref={iframeRef}
        title={title || 'theme-preview'}
        tabIndex={-1}
        aria-hidden
        style={{
          width: '220%',
          height: '220%',
          border: 0,
          transform: 'scale(0.455)',
          transformOrigin: 'top left',
          pointerEvents: 'none',
          display: 'block',
        }}
      />
      {emphasize === 'color' && (
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            height: 8,
            display: 'flex',
          }}
        >
          <span style={{ flex: 2, background: vars['--bg-primary'] }} />
          <span style={{ flex: 2, background: vars['--bg-card'] }} />
          <span style={{ flex: 1, background: vars['--accent'] }} />
          <span style={{ flex: 1, background: vars['--text-primary'] }} />
        </div>
      )}
    </div>
  );
}
