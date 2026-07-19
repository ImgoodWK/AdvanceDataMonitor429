import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

import { COLOR_SCHEMES, type ThemeColor } from './colors';
import { LAYOUT_PRESETS, type ThemeLayout } from './layouts';
import { PAGE_STYLE_PRESETS, resolvePageStyle, type PageStyle } from './pageStyles';

/**
 * Build an antd ThemeConfig from the selected color scheme + page style.
 */
export function buildAntdThemeSync(
  color: ThemeColor,
  compact: boolean,
  pageStyle: PageStyle = 'classic'
): ThemeConfig {
  const scheme = COLOR_SCHEMES[color];
  const isDark = scheme.isDark;
  const stylePreset = PAGE_STYLE_PRESETS[resolvePageStyle(pageStyle)];
  const baseRadius = stylePreset.borderRadius;
  const borderRadius = compact ? Math.max(0, Math.min(4, Math.floor(baseRadius / 2))) : baseRadius;
  const fontFamily =
    stylePreset.fontFamily ||
    "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Microsoft YaHei', 'PingFang SC', sans-serif";

  return {
    token: {
      colorPrimary: scheme.antdColorPrimary,
      colorBgBase: scheme.antdColorBgBase,
      colorInfo: scheme.antdColorPrimary,
      colorSuccess: scheme.cssVars['--success'],
      colorWarning: scheme.cssVars['--warning'],
      colorError: scheme.cssVars['--danger'],
      borderRadius,
      fontFamily,
      fontSize: compact ? 12 : 13,
    },
    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    components: {
      Layout: {
        headerBg: scheme.cssVars['--bg-secondary'],
        siderBg: scheme.cssVars['--sidebar-bg'],
        bodyBg: scheme.cssVars['--bg-primary'],
      },
      Menu: {
        darkItemBg: scheme.cssVars['--sidebar-bg'],
        darkItemSelectedBg: scheme.cssVars['--sidebar-active'],
        darkItemHoverBg: scheme.cssVars['--sidebar-hover'],
      },
      Card: {
        colorBgContainer: scheme.cssVars['--bg-card'],
        borderRadiusLG: borderRadius,
      },
      Table: {
        colorBgContainer: scheme.cssVars['--bg-secondary'],
        headerBg: scheme.cssVars['--bg-secondary'],
      },
      Modal: {
        contentBg: scheme.cssVars['--bg-secondary'],
      },
    },
  };
}

/**
 * Apply CSS custom properties from color + layout + pageStyle to the document root.
 * effectsLevel gates glassmorphism & animation via `data-effects-level`.
 */
export function applyCssVars(
  color: ThemeColor,
  layout: string,
  effectsLevel: 'none' | 'subtle' | 'full',
  pageStyle: string = 'classic'
) {
  const scheme = COLOR_SCHEMES[color];
  const layoutPreset = LAYOUT_PRESETS[layout as ThemeLayout];
  const styleId = resolvePageStyle(pageStyle);
  const stylePreset = PAGE_STYLE_PRESETS[styleId];
  const root = document.documentElement;

  for (const [k, v] of Object.entries(scheme.cssVars)) {
    root.style.setProperty(k, v);
  }

  if (layoutPreset) {
    for (const [k, v] of Object.entries(layoutPreset.cssVars)) {
      root.style.setProperty(k, v);
    }
  }

  for (const [k, v] of Object.entries(stylePreset.cssVars)) {
    root.style.setProperty(k, v);
  }

  root.setAttribute('data-theme-color', color);
  root.setAttribute('data-theme-layout', layout);
  root.setAttribute('data-page-style', styleId);
  root.setAttribute('data-style-enter', stylePreset.cssVars['--style-enter'] || 'fade');
  root.setAttribute('data-ui-mode', 'advanced');
  root.setAttribute('data-effects-level', effectsLevel);
  root.setAttribute('data-color-scheme', scheme.isDark ? 'dark' : 'light');
}
