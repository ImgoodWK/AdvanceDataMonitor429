import { memo, useMemo, type CSSProperties, type ReactNode } from 'react';

import { COLOR_SCHEMES, type EffectsLevel, type ThemeColor } from '@/theme/colors';
import { collectPreviewVars } from '@/theme/collectPreviewVars';
import { LAYOUT_PRESETS, type ChromeKind, type ThemeLayout } from '@/theme/layouts';
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

function varsToStyle(vars: Record<string, string>): CSSProperties {
  return vars as CSSProperties;
}

function NavDots({ count = 3 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <span
          key={i}
          style={{
            width: i === 0 ? 28 : 18,
            height: i === 0 ? 6 : 5,
            background: i === 0 ? 'var(--accent)' : 'var(--text-dim)',
            borderRadius: 2,
            opacity: i === 0 ? 0.85 : 0.45,
          }}
        />
      ))}
    </>
  );
}

function SideRail({ width, accent }: { width: number; accent?: boolean }) {
  return (
    <aside
      style={{
        width,
        background: 'var(--sidebar-bg)',
        borderRight: '1px solid var(--border)',
        padding: '6px 4px',
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        flexShrink: 0,
        boxSizing: 'border-box',
      }}
    >
      <div
        style={{
          height: 10,
          background: accent ? 'var(--sidebar-active)' : 'var(--sidebar-hover)',
          borderRadius: 4,
        }}
      />
      <div style={{ height: 10, background: 'var(--sidebar-hover)', borderRadius: 4, opacity: 0.7 }} />
      <div style={{ height: 10, background: 'var(--sidebar-hover)', borderRadius: 4, opacity: 0.5 }} />
    </aside>
  );
}

function ContentCards({ cardStyle }: { cardStyle: CSSProperties }) {
  return (
    <main
      style={{
        flex: 1,
        position: 'relative',
        padding: 8,
        minWidth: 0,
        overflow: 'hidden',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ marginBottom: 8 }}>
        <div
          style={{
            fontSize: 12,
            fontWeight: 'var(--style-title-weight, 600)' as CSSProperties['fontWeight'],
            color: 'var(--text-primary)',
          }}
        >
          WebAE
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
        <div style={cardStyle}>
          <div style={{ fontSize: 8, color: 'var(--text-dim)', marginBottom: 4 }}>Items</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--accent)' }}>12.4k</div>
        </div>
        <div style={cardStyle}>
          <div style={{ fontSize: 8, color: 'var(--text-dim)', marginBottom: 4 }}>CPU</div>
          <div
            style={{
              height: 16,
              marginTop: 4,
              background: 'linear-gradient(90deg, var(--accent), var(--accent-dim))',
              borderRadius: 3,
              opacity: 0.85,
            }}
          />
        </div>
      </div>
    </main>
  );
}

function TopStrip({ tall, island }: { tall?: boolean; island?: boolean }) {
  return (
    <div
      style={{
        height: tall ? 32 : 18,
        background: 'var(--bg-secondary)',
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        gap: 6,
        alignItems: 'center',
        justifyContent: island ? 'center' : 'flex-start',
        padding: island ? '0 24px' : '0 8px',
        flexShrink: 0,
        ...(island
          ? {
              margin: '6px 24px 0',
              borderRadius: 12,
              border: '1px solid var(--border)',
            }
          : null),
      }}
    >
      <NavDots />
    </div>
  );
}

function BottomStrip({ dock, corner }: { dock?: boolean; corner?: boolean }) {
  if (corner) {
    return (
      <div
        style={{
          position: 'absolute',
          right: 10,
          bottom: 10,
          width: 48,
          height: 48,
          borderRadius: 14,
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 4,
          zIndex: 2,
        }}
      >
        <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--accent)' }} />
        <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--text-dim)', opacity: 0.5 }} />
      </div>
    );
  }
  return (
    <div
      style={{
        height: dock ? 22 : 18,
        background: 'var(--bg-secondary)',
        borderTop: '1px solid var(--border)',
        display: 'flex',
        justifyContent: 'center',
        gap: 10,
        alignItems: 'center',
        flexShrink: 0,
        ...(dock
          ? {
              margin: '0 28px 6px',
              borderRadius: 12,
              border: '1px solid var(--border)',
            }
          : null),
      }}
    >
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--accent)' }} />
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--text-dim)', opacity: 0.4 }} />
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--text-dim)', opacity: 0.4 }} />
    </div>
  );
}

function previewBody(chrome: ChromeKind, cardStyle: CSSProperties): ReactNode {
  switch (chrome) {
    case 'dual-rail':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <SideRail width={18} accent />
            <SideRail width={40} />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'rail-only':
    case 'status-strip':
    case 'zen':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <SideRail width={chrome === 'status-strip' ? 14 : 22} accent />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'dock':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
          <BottomStrip dock />
        </>
      );
    case 'island':
      return (
        <>
          <TopStrip island />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'theater':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <div style={{ width: 16, background: 'var(--bg-secondary)', opacity: 0.85 }} />
            <ContentCards cardStyle={cardStyle} />
            <div style={{ width: 16, background: 'var(--bg-secondary)', opacity: 0.85 }} />
          </div>
        </>
      );
    case 'dense-ops':
      return (
        <>
          <div style={{ height: 10, background: 'var(--accent)', opacity: 0.35, flexShrink: 0 }} />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <SideRail width={36} />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <TopStrip />
              <ContentCards cardStyle={cardStyle} />
            </div>
          </div>
          <div style={{ height: 10, background: 'var(--bg-secondary)', borderTop: '1px solid var(--border)', flexShrink: 0 }} />
        </>
      );
    case 'magazine':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0, flexDirection: 'row-reverse' }}>
            <SideRail width={48} />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'split-pane':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <SideRail width={72} accent />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'top-tabs':
      return (
        <>
          <TopStrip />
          <div style={{ height: 14, background: 'var(--bg-card)', borderBottom: '1px solid var(--border)', display: 'flex', gap: 8, alignItems: 'center', padding: '0 8px', flexShrink: 0 }}>
            <NavDots count={4} />
          </div>
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'command':
    case 'widescreen':
    case 'pipeline':
    case 'hero-header':
    case 'frame':
      return (
        <>
          <TopStrip tall={chrome === 'hero-header' || chrome === 'pipeline'} />
          <div style={{ display: 'flex', flex: 1, minHeight: 0, ...(chrome === 'frame' ? { margin: 8, border: '2px solid var(--accent-dim)', borderRadius: 6 } : null) }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'tri-chrome':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <SideRail width={36} />
            <ContentCards cardStyle={cardStyle} />
          </div>
          <BottomStrip />
        </>
      );
    case 'card-stack':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0, padding: 6 }}>
            <aside
              style={{
                width: 40,
                margin: 4,
                borderRadius: 10,
                background: 'var(--sidebar-bg)',
                border: '1px solid var(--border)',
                padding: 4,
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
              }}
            >
              <div style={{ height: 8, background: 'var(--sidebar-active)', borderRadius: 4 }} />
              <div style={{ height: 8, background: 'var(--sidebar-hover)', borderRadius: 4, opacity: 0.6 }} />
            </aside>
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'hud-frame':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
          <BottomStrip />
        </>
      );
    case 'drawer-peek':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0, position: 'relative' }}>
            <div style={{ width: 6, background: 'var(--accent)', opacity: 0.5 }} />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    case 'corner-hub':
      return (
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative', minHeight: 0 }}>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
            <ContentCards cardStyle={cardStyle} />
          </div>
          <BottomStrip corner />
        </div>
      );
    case 'right-drawer':
      return (
        <>
          <TopStrip />
          <div style={{ display: 'flex', flex: 1, minHeight: 0, flexDirection: 'row-reverse' }}>
            <SideRail width={44} accent />
            <ContentCards cardStyle={cardStyle} />
          </div>
        </>
      );
    default: {
      const side = LAYOUT_PRESETS.standard.sidebarSide;
      void side;
      return (
        <>
          {/* filled by outer using classic layout flags */}
        </>
      );
    }
  }
}

/** Lightweight in-DOM theme thumbnail (no iframe / no host CSS copy). */
function ThemePreviewMiniInner({
  themeColor,
  themeLayout = 'standard',
  pageStyle = 'classic',
  effectsLevel = 'subtle',
  emphasize = 'all',
  className,
  title,
}: ThemePreviewMiniProps) {
  const styleId = resolvePageStyle(pageStyle);
  const vars = useMemo(
    () => collectPreviewVars(themeColor, themeLayout, styleId, effectsLevel),
    [themeColor, themeLayout, styleId, effectsLevel]
  );
  const layout = LAYOUT_PRESETS[themeLayout] || LAYOUT_PRESETS.standard;
  const chrome = layout.chromeKind || 'default';
  const side = layout.sidebarSide;
  const navChrome = layout.navChrome;
  const sidebarRight = side === 'right';
  const topNav = side === 'none' && navChrome === 'top';
  const bottomNav = side === 'none' && navChrome === 'bottom';
  const floating = themeLayout === 'floating';
  const isDark = COLOR_SCHEMES[themeColor]?.isDark ?? true;
  const useChromeSketch = chrome !== 'default';

  const cardStyle: CSSProperties = {
    background: 'var(--style-card-bg, var(--bg-card))',
    border: 'var(--style-border-width, 1px) solid var(--style-chrome-border, var(--border))',
    borderRadius: 'var(--style-radius, 8px)',
    padding: 8,
    boxShadow: 'var(--style-shadow)',
    minHeight: 42,
  };

  return (
    <div
      className={className}
      title={title}
      data-theme-preview
      data-theme-color={themeColor}
      data-theme-layout={themeLayout}
      data-page-style={styleId}
      data-effects-level={effectsLevel}
      data-color-scheme={isDark ? 'dark' : 'light'}
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
      <div
        style={{
          ...varsToStyle(vars),
          width: '220%',
          height: '220%',
          transform: 'scale(0.455)',
          transformOrigin: 'top left',
          pointerEvents: 'none',
          display: 'flex',
          flexDirection: 'column',
          boxSizing: 'border-box',
          fontSize: 11,
          fontFamily: 'var(--style-font-ui, system-ui, sans-serif)',
          background: 'var(--bg-primary)',
          color: 'var(--text-primary)',
          overflow: 'hidden',
          position: 'relative',
          ...(floating
            ? {
                margin: 8,
                borderRadius: 12,
                border: '1px solid var(--border)',
              }
            : null),
        }}
      >
        {useChromeSketch ? (
          previewBody(chrome, cardStyle)
        ) : (
          <>
            {topNav && <TopStrip />}
            <div
              style={{
                display: 'flex',
                flex: 1,
                minHeight: 0,
                flexDirection: sidebarRight ? 'row-reverse' : 'row',
              }}
            >
              {side !== 'none' && <SideRail width={floating ? 52 : 44} accent />}
              <ContentCards cardStyle={cardStyle} />
            </div>
            {bottomNav && <BottomStrip />}
          </>
        )}
      </div>

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

export const ThemePreviewMini = memo(ThemePreviewMiniInner);
