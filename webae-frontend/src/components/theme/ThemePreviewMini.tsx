import { memo, useMemo, type CSSProperties } from 'react';

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

function varsToStyle(vars: Record<string, string>): CSSProperties {
  // CSS custom properties are valid React inline style keys.
  return vars as CSSProperties;
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
  const side = layout.sidebarSide;
  const navChrome = layout.navChrome;
  const sidebarRight = side === 'right';
  const topNav = side === 'none' && navChrome === 'top';
  const bottomNav = side === 'none' && navChrome === 'bottom';
  const floating = themeLayout === 'floating';
  const isDark = COLOR_SCHEMES[themeColor]?.isDark ?? true;

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
          ...(floating
            ? {
                margin: 8,
                borderRadius: 12,
                border: '1px solid var(--border)',
              }
            : null),
        }}
      >
        {topNav && (
          <div
            style={{
              height: 18,
              background: 'var(--bg-secondary)',
              borderBottom: '1px solid var(--border)',
              display: 'flex',
              gap: 6,
              alignItems: 'center',
              padding: '0 8px',
              flexShrink: 0,
            }}
          >
            <span
              style={{
                width: 28,
                height: 6,
                background: 'var(--accent)',
                borderRadius: 2,
                opacity: 0.85,
              }}
            />
            <span
              style={{
                width: 22,
                height: 5,
                background: 'var(--text-dim)',
                borderRadius: 2,
                opacity: 0.5,
              }}
            />
            <span
              style={{
                width: 22,
                height: 5,
                background: 'var(--text-dim)',
                borderRadius: 2,
                opacity: 0.5,
              }}
            />
          </div>
        )}

        <div
          style={{
            display: 'flex',
            flex: 1,
            minHeight: 0,
            flexDirection: sidebarRight ? 'row-reverse' : 'row',
          }}
        >
          {side !== 'none' && (
            <aside
              style={{
                width: floating ? 52 : 44,
                background: 'var(--sidebar-bg)',
                borderRight: sidebarRight ? undefined : '1px solid var(--border)',
                borderLeft: sidebarRight ? '1px solid var(--border)' : undefined,
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
                  background: 'var(--sidebar-active)',
                  borderRadius: 4,
                }}
              />
              <div
                style={{
                  height: 10,
                  background: 'var(--sidebar-hover)',
                  borderRadius: 4,
                  opacity: 0.7,
                }}
              />
              <div
                style={{
                  height: 10,
                  background: 'var(--sidebar-hover)',
                  borderRadius: 4,
                  opacity: 0.5,
                }}
              />
            </aside>
          )}

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
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 6,
              }}
            >
              <div style={cardStyle}>
                <div
                  style={{
                    fontSize: 8,
                    color: 'var(--text-dim)',
                    marginBottom: 4,
                  }}
                >
                  Items
                </div>
                <div
                  style={{
                    fontSize: 14,
                    fontWeight: 700,
                    color: 'var(--accent)',
                  }}
                >
                  12.4k
                </div>
              </div>
              <div style={cardStyle}>
                <div
                  style={{
                    fontSize: 8,
                    color: 'var(--text-dim)',
                    marginBottom: 4,
                  }}
                >
                  CPU
                </div>
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
              <div
                style={{
                  ...cardStyle,
                  gridColumn: '1 / -1',
                  minHeight: 28,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <span
                  style={{
                    display: 'inline-block',
                    padding: '3px 8px',
                    borderRadius: 'var(--style-radius-sm, 6px)',
                    background: 'var(--accent)',
                    color: '#fff',
                    fontSize: 8,
                    fontWeight: 600,
                  }}
                >
                  Primary
                </span>
                <span style={{ fontSize: 8, color: 'var(--text-secondary)' }}>Sample chrome</span>
              </div>
            </div>
          </main>
        </div>

        {bottomNav && (
          <div
            style={{
              height: 18,
              background: 'var(--bg-secondary)',
              borderTop: '1px solid var(--border)',
              display: 'flex',
              justifyContent: 'center',
              gap: 10,
              alignItems: 'center',
              flexShrink: 0,
            }}
          >
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: 'var(--accent)',
              }}
            />
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: 'var(--text-dim)',
                opacity: 0.4,
              }}
            />
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: 'var(--text-dim)',
                opacity: 0.4,
              }}
            />
          </div>
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
