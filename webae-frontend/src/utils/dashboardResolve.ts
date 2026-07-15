// Property resolver for the dashboard widget inheritance system.
//
// Each widget can either inherit the global default from DashboardSettings or
// override it per-widget. Colors additionally honour an `inheritDefault`
// flag so a user can mix-and-match (e.g. keep title color from the global
// preset but override the chart color).

import type {
  Alignment,
  DashboardSettings,
  DashboardWidgetColorOverrides,
  DashboardWidgetConfig,
} from '@/utils/presets';
import type { ChartStyle, ChartStyleRecipe, PageStyle } from '@/theme/pageStyles';
import {
  chartStyleFromPageStyle,
  getChartStyleRecipe,
  isChartStyle,
  resolvePageStyle,
} from '@/theme/pageStyles';

type BaseColorKey = 'titleColor' | 'chartColor' | 'iconColor' | 'backgroundColor' | 'borderColor';

type ScalarColorKey = keyof DashboardWidgetColorOverrides extends infer K
  ? K extends 'inheritDefault' | 'barSegmentColors' | 'pieSliceColors' | 'borderWidth'
    ? never
    : K
  : never;

type ArrayColorKey = 'barSegmentColors' | 'pieSliceColors';

type ChartPartKey =
  | 'chartLineColor'
  | 'chartAreaColor'
  | 'chartGridColor'
  | 'chartPointColor'
  | 'chartSecondaryLineColor'
  | 'chartSecondaryAreaColor';

// Resolve a scalar widget property (fontSize / chartSize / alignment).
// widget override wins when it is a finite, defined value; otherwise we fall
// back to the matching DashboardSettings default. Overloads give callers a
// precise (non-optional) return type so the result can be used directly in
// style/math expressions.
export function resolveProp(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: 'fontSize'
): number;
export function resolveProp(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: 'chartSize'
): number;
export function resolveProp(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: 'alignment'
): Alignment;
export function resolveProp<K extends 'fontSize' | 'chartSize' | 'alignment'>(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: K
): DashboardWidgetConfig[K] {
  const widgetValue = widget[key];
  if (widgetValue !== undefined && widgetValue !== null) {
    return widgetValue as DashboardWidgetConfig[K];
  }
  if (key === 'fontSize') return settings.fontSize as DashboardWidgetConfig[K];
  if (key === 'chartSize') return settings.chartSize as DashboardWidgetConfig[K];
  return settings.defaultAlignment as DashboardWidgetConfig[K];
}

// Resolve a single color channel. Empty string is treated as "unset" so the
// theme CSS variable remains in effect.
export function resolveColor(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: BaseColorKey
): string {
  const overrides = widget.colors;
  if (overrides && !overrides.inheritDefault) {
    const v = overrides[key];
    if (v && v.trim() !== '') return v;
  }
  const def = settings.defaultColors[key as BaseColorKey];
  return def && def.trim() !== '' ? def : '';
}

// Resolve a scalar color channel that falls back to chartColor when unset
// (used by progress fill / gauge stroke / chart parts).
function resolveChartDerivedColor(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: 'progressFillColor' | 'gaugeStrokeColor' | ChartPartKey
): string {
  const overrides = widget.colors;
  if (overrides && !overrides.inheritDefault) {
    const v = overrides[key];
    if (typeof v === 'string' && v.trim() !== '') return v;
  }
  const base = resolveColor(widget, settings, 'chartColor');
  if (key === 'chartSecondaryLineColor' && base) return base;
  if (key === 'chartSecondaryAreaColor' && base) return `${base}33`;
  if (key === 'chartAreaColor' && base) return `${base}33`;
  if (key === 'chartLineColor' && base) return base;
  if (key === 'progressFillColor' && base) return base;
  if (key === 'gaugeStrokeColor' && base) return base;
  return '';
}

// Resolve a scalar color channel that does NOT fall back to chartColor
// (used by track colors / row alt / axis text / radar axis).
function resolveStandaloneColor(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key:
    | 'progressTrackColor'
    | 'gaugeTrackColor'
    | 'dataTableRowAltColor'
    | 'axisTextColor'
    | 'radarAxisColor'
    | 'categoryItemsColor'
    | 'categoryFluidsColor'
    | 'categoryEssentiaColor'
): string {
  const overrides = widget.colors;
  if (overrides && !overrides.inheritDefault) {
    const v = overrides[key];
    if (typeof v === 'string' && v.trim() !== '') return v;
  }
  return '';
}

// Build the full set of resolved scalar colors for a widget (used by renderWidget).
export function resolveAllColors(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings
): Required<Record<ScalarColorKey, string>> {
  return {
    titleColor: resolveColor(widget, settings, 'titleColor'),
    chartColor: resolveColor(widget, settings, 'chartColor'),
    iconColor: resolveColor(widget, settings, 'iconColor'),
    backgroundColor: resolveColor(widget, settings, 'backgroundColor'),
    borderColor: resolveColor(widget, settings, 'borderColor'),
    chartLineColor: resolveChartDerivedColor(widget, settings, 'chartLineColor'),
    chartAreaColor: resolveChartDerivedColor(widget, settings, 'chartAreaColor'),
    chartGridColor: resolveChartDerivedColor(widget, settings, 'chartGridColor'),
    chartPointColor: resolveChartDerivedColor(widget, settings, 'chartPointColor'),
    chartSecondaryLineColor: resolveChartDerivedColor(widget, settings, 'chartSecondaryLineColor'),
    chartSecondaryAreaColor: resolveChartDerivedColor(widget, settings, 'chartSecondaryAreaColor'),
    progressTrackColor: resolveStandaloneColor(widget, settings, 'progressTrackColor'),
    progressFillColor: resolveChartDerivedColor(widget, settings, 'progressFillColor'),
    gaugeTrackColor: resolveStandaloneColor(widget, settings, 'gaugeTrackColor'),
    gaugeStrokeColor: resolveChartDerivedColor(widget, settings, 'gaugeStrokeColor'),
    dataTableRowAltColor: resolveStandaloneColor(widget, settings, 'dataTableRowAltColor'),
    axisTextColor: resolveStandaloneColor(widget, settings, 'axisTextColor'),
    radarAxisColor: resolveStandaloneColor(widget, settings, 'radarAxisColor'),
    categoryItemsColor: resolveStandaloneColor(widget, settings, 'categoryItemsColor'),
    categoryFluidsColor: resolveStandaloneColor(widget, settings, 'categoryFluidsColor'),
    categoryEssentiaColor: resolveStandaloneColor(widget, settings, 'categoryEssentiaColor'),
  };
}

/**
 * Resolve a multi-color array (bar segments / pie slices). Falls back to a
 * single-element array containing the resolved chartColor so renderers can
 * always loop over a non-empty array.
 */
export function resolveColorArray(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  key: ArrayColorKey
): string[] {
  const overrides = widget.colors;
  if (overrides && !overrides.inheritDefault) {
    const arr = overrides[key];
    if (Array.isArray(arr) && arr.some((c) => c && c.trim() !== '')) {
      // Keep only non-empty entries so trailing blanks don't show transparent.
      const filtered = arr.filter((c) => c && c.trim() !== '');
      if (filtered.length > 0) return filtered;
    }
  }
  const base = resolveColor(widget, settings, 'chartColor');
  return base ? [base] : [];
}

// Map a 9-grid Alignment to CSS flex props. Widgets use a column flex layout:
//   justify-content controls vertical placement (top / center / bottom)
//   align-items     controls horizontal placement (left / center / right)
export interface AlignCss {
  justifyContent: string;
  alignItems: string;
  textAlign: 'left' | 'center' | 'right';
}

export function alignmentToCss(alignment: Alignment): AlignCss {
  const [vertical, horizontal] = alignment.split('-');
  const vMap: Record<string, string> = {
    top: 'flex-start',
    center: 'center',
    bottom: 'flex-end',
  };
  const hMap: Record<string, string> = {
    left: 'flex-start',
    center: 'center',
    right: 'flex-end',
  };
  // 'center' / 'top' / 'bottom' alone mean horizontal center
  const v = vertical || 'center';
  const h = horizontal || 'center';
  const textAlign = (h === 'left' || h === 'center' || h === 'right' ? h : 'center') as
    | 'left'
    | 'center'
    | 'right';
  return {
    justifyContent: vMap[v] ?? 'center',
    alignItems: hMap[h] ?? 'center',
    textAlign,
  };
}

export function resolveAlignCss(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings
): AlignCss {
  return alignmentToCss(resolveProp(widget, settings, 'alignment'));
}

const CONTENT_INSET_BASE = 10;

export function resolveContentInset(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings
): number {
  let userInset: number;
  if (widget.contentInset !== undefined && widget.contentInset !== null) {
    userInset = widget.contentInset;
  } else {
    userInset = settings.contentInset ?? 0;
  }
  return CONTENT_INSET_BASE + Math.max(0, Math.min(24, userInset));
}

export function resolveBorderWidth(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings
): number {
  const overrides = widget.colors;
  if (overrides && !overrides.inheritDefault && overrides.borderWidth !== undefined) {
    return Math.max(0, Math.min(6, overrides.borderWidth));
  }
  return Math.max(0, Math.min(6, settings.borderWidth ?? 1));
}

export type ChartStretchMode = 'fit' | 'stretchX' | 'fill';

export function resolveChartStretchMode(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings,
  widgetType?: DashboardWidgetConfig['type']
): ChartStretchMode {
  if (widget.chartStretchMode) {
    return widget.chartStretchMode;
  }
  if (settings.chartStretchMode) {
    return settings.chartStretchMode;
  }
  if (widgetType === 'lineChart') {
    return 'stretchX';
  }
  return 'fit';
}

/**
 * Resolve concrete chart drawing recipe.
 * Order: widget.chartStyle → settings.defaultChartStyle → pageStyle default → classic.
 */
export function resolveChartStyleId(
  widget: DashboardWidgetConfig | null | undefined,
  settings: DashboardSettings | null | undefined,
  pageStyle: PageStyle | string = 'classic'
): Exclude<ChartStyle, 'inherit'> {
  const page = resolvePageStyle(pageStyle);
  const candidates = [widget?.chartStyle, settings?.defaultChartStyle];
  for (const c of candidates) {
    if (!c || c === 'inherit' || !isChartStyle(c)) continue;
    return c;
  }
  return chartStyleFromPageStyle(page);
}

export function resolveChartStyleRecipe(
  widget: DashboardWidgetConfig | null | undefined,
  settings: DashboardSettings | null | undefined,
  pageStyle: PageStyle | string = 'classic'
): ChartStyleRecipe {
  return getChartStyleRecipe(resolveChartStyleId(widget, settings, pageStyle));
}

/** Inline style + CSS variables for `.grid-stack-item-content` shell. */
export function applyWidgetShellStyle(
  widget: DashboardWidgetConfig,
  settings: DashboardSettings
): Record<string, string | number> {
  const bg = resolveColor(widget, settings, 'backgroundColor');
  const border = resolveColor(widget, settings, 'borderColor');
  const borderWidth = resolveBorderWidth(widget, settings);
  const inset = resolveContentInset(widget, settings);
  const style: Record<string, string | number> = {
    padding: `${inset}px`,
    ['--widget-bg' as string]: bg || 'var(--bg-card)',
    ['--widget-border' as string]: border || 'var(--border)',
    ['--widget-border-width' as string]: String(borderWidth),
  };
  if (bg) {
    style.background = bg;
  } else {
    style.background = 'var(--widget-bg, var(--bg-card))';
  }
  if (border && borderWidth > 0) {
    style.border = `${borderWidth}px solid ${border}`;
  } else if (borderWidth > 0) {
    style.border = `${borderWidth}px solid var(--widget-border, var(--border))`;
  } else {
    style.border = 'none';
  }
  style.borderRadius = 'var(--style-radius, 8px)';
  return style;
}
