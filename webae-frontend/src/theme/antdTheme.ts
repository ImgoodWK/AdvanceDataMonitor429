import type { ThemeConfig } from 'antd';

import { theme as antdTheme } from 'antd';

import { COLOR_SCHEMES, type ThemeColor } from './colors';

import { LAYOUT_PRESETS, type ThemeLayout } from './layouts';



/**

 * Build an antd ThemeConfig from the selected color scheme.

 */

export function buildAntdThemeSync(color: ThemeColor, compact: boolean): ThemeConfig {

  const scheme = COLOR_SCHEMES[color];

  const isDark = scheme.isDark;

  return {

    token: {

      colorPrimary: scheme.antdColorPrimary,

      colorBgBase: scheme.antdColorBgBase,

      colorInfo: scheme.antdColorPrimary,

      colorSuccess: scheme.cssVars['--success'],

      colorWarning: scheme.cssVars['--warning'],

      colorError: scheme.cssVars['--danger'],

      borderRadius: compact ? 4 : 8,

      fontFamily:

        "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Microsoft YaHei', 'PingFang SC', sans-serif",

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

 * Apply CSS custom properties from the color scheme + layout preset to the

 * document root so custom (non-antd) components can use var(--accent) etc.

 *

 * The effectsLevel (none/subtle/full) gates glassmorphism & animation intensity

 * via the `data-effects-level` attribute consumed by styles/global.css.

 */

export function applyCssVars(color: ThemeColor, layout: string, effectsLevel: 'none' | 'subtle' | 'full') {

  const scheme = COLOR_SCHEMES[color];

  const layoutPreset = LAYOUT_PRESETS[layout as ThemeLayout];

  const root = document.documentElement;

  for (const [k, v] of Object.entries(scheme.cssVars)) {

    root.style.setProperty(k, v);

  }

  if (layoutPreset) {

    for (const [k, v] of Object.entries(layoutPreset.cssVars)) {

      root.style.setProperty(k, v);

    }

    const maxWidth = layout === 'wide' ? 'none' : '1600px';

    root.style.setProperty('--layout-content-max-width', maxWidth);

  }

  root.setAttribute('data-theme-color', color);

  root.setAttribute('data-theme-layout', layout);

  root.setAttribute('data-ui-mode', 'advanced');

  root.setAttribute('data-effects-level', effectsLevel);

  root.setAttribute('data-color-scheme', scheme.isDark ? 'dark' : 'light');

}

