import { COLOR_SCHEMES, type EffectsLevel, type ThemeColor } from './colors';
import { LAYOUT_PRESETS, type ThemeLayout } from './layouts';
import { PAGE_STYLE_PRESETS, resolvePageStyle, type PageStyle } from './pageStyles';

/** Collect CSS custom properties for a theme combo (for scoped / iframe previews). */
export function collectPreviewVars(
  color: ThemeColor,
  layout: ThemeLayout,
  pageStyle: PageStyle | string,
  _effectsLevel: EffectsLevel = 'subtle'
): Record<string, string> {
  const scheme = COLOR_SCHEMES[color] || COLOR_SCHEMES.dark;
  const layoutPreset = LAYOUT_PRESETS[layout] || LAYOUT_PRESETS.standard;
  const styleId = resolvePageStyle(pageStyle);
  const stylePreset = PAGE_STYLE_PRESETS[styleId];
  const vars: Record<string, string> = {
    ...scheme.cssVars,
    ...layoutPreset.cssVars,
    ...stylePreset.cssVars,
  };
  if (layout === 'wide') {
    vars['--layout-content-max-width'] = 'none';
  }
  return vars;
}

export function previewSchemeMeta(color: ThemeColor) {
  const scheme = COLOR_SCHEMES[color] || COLOR_SCHEMES.dark;
  return {
    isDark: scheme.isDark,
    accent: scheme.cssVars['--accent'],
    bg: scheme.cssVars['--bg-primary'],
    card: scheme.cssVars['--bg-card'],
    secondary: scheme.cssVars['--bg-secondary'],
    text: scheme.cssVars['--text-primary'],
  };
}
