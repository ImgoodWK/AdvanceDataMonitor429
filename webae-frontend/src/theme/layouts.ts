// Layout presets — spacing, sidebar geometry, font sizes, and chrome placement.
export type ThemeLayout =
  | 'standard'
  | 'compact'
  | 'wide'
  | 'sidebar-right'
  | 'topnav'
  | 'bottomnav'
  | 'floating'
  | 'split-chrome'
  // Batch3 structural layouts
  | 'dual-rail'
  | 'rail-only'
  | 'dock'
  | 'island'
  | 'theater'
  | 'dense-ops'
  | 'magazine'
  | 'split-pane'
  | 'top-tabs'
  | 'zen'
  | 'command'
  | 'tri-chrome'
  | 'card-stack'
  | 'hud-frame'
  | 'pipeline'
  | 'hero-header'
  | 'status-strip'
  | 'drawer-peek'
  | 'corner-hub'
  | 'widescreen'
  | 'right-drawer'
  | 'frame';

/** How AppLayout composes chrome (orthogonal to spacing cssVars). */
export type ChromeKind =
  | 'default'
  | 'dual-rail'
  | 'rail-only'
  | 'dock'
  | 'island'
  | 'theater'
  | 'dense-ops'
  | 'magazine'
  | 'split-pane'
  | 'top-tabs'
  | 'zen'
  | 'command'
  | 'tri-chrome'
  | 'card-stack'
  | 'hud-frame'
  | 'pipeline'
  | 'hero-header'
  | 'status-strip'
  | 'drawer-peek'
  | 'corner-hub'
  | 'widescreen'
  | 'right-drawer'
  | 'frame';

export interface LayoutPreset {
  id: ThemeLayout;
  cssVars: Record<string, string>;
  /** Physical sider side; 'none' means nav lives in top or bottom chrome. */
  sidebarSide: 'left' | 'right' | 'none';
  /** Where primary page nav is rendered when sidebarSide is none. */
  navChrome?: 'top' | 'bottom';
  /** Structural chrome composition for AppLayout / ThemePreviewMini. */
  chromeKind?: ChromeKind;
}

const BASE_LEFT = {
  '--layout-sidebar-width': '240px',
  '--layout-card-gap': '14px',
  '--layout-card-pad': '16px',
  '--layout-font-base': '0.83rem',
  '--layout-dash-value': '1.8rem',
  '--layout-page-pad-x': '24px',
  '--layout-page-pad-y': '16px',
  '--layout-content-max-width': '1600px',
} as const;

export const THEME_LAYOUTS: ThemeLayout[] = [
  'standard',
  'compact',
  'wide',
  'sidebar-right',
  'topnav',
  'bottomnav',
  'floating',
  'split-chrome',
  'dual-rail',
  'rail-only',
  'dock',
  'island',
  'theater',
  'dense-ops',
  'magazine',
  'split-pane',
  'top-tabs',
  'zen',
  'command',
  'tri-chrome',
  'card-stack',
  'hud-frame',
  'pipeline',
  'hero-header',
  'status-strip',
  'drawer-peek',
  'corner-hub',
  'widescreen',
  'right-drawer',
  'frame',
];

export const LAYOUT_PRESETS: Record<ThemeLayout, LayoutPreset> = {
  standard: {
    id: 'standard',
    sidebarSide: 'left',
    chromeKind: 'default',
    cssVars: { ...BASE_LEFT },
  },
  compact: {
    id: 'compact',
    sidebarSide: 'left',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '180px',
      '--layout-card-gap': '8px',
      '--layout-card-pad': '10px',
      '--layout-font-base': '0.78rem',
      '--layout-dash-value': '1.4rem',
      '--layout-page-pad-x': '14px',
      '--layout-page-pad-y': '10px',
      '--layout-content-max-width': '1600px',
    },
  },
  wide: {
    id: 'wide',
    sidebarSide: 'left',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '260px',
      '--layout-card-gap': '22px',
      '--layout-card-pad': '22px',
      '--layout-font-base': '0.9rem',
      '--layout-dash-value': '2.2rem',
      '--layout-page-pad-x': '36px',
      '--layout-page-pad-y': '24px',
      '--layout-content-max-width': 'none',
    },
  },
  'sidebar-right': {
    id: 'sidebar-right',
    sidebarSide: 'right',
    chromeKind: 'default',
    cssVars: { ...BASE_LEFT },
  },
  topnav: {
    id: 'topnav',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
    },
  },
  bottomnav: {
    id: 'bottomnav',
    sidebarSide: 'none',
    navChrome: 'bottom',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '12px',
      '--layout-card-pad': '14px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '20px',
      '--layout-page-pad-y': '14px',
      '--layout-content-max-width': '1600px',
      '--layout-bottom-nav-height': '56px',
    },
  },
  floating: {
    id: 'floating',
    sidebarSide: 'left',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '220px',
      '--layout-card-gap': '18px',
      '--layout-card-pad': '18px',
      '--layout-font-base': '0.85rem',
      '--layout-dash-value': '1.9rem',
      '--layout-page-pad-x': '28px',
      '--layout-page-pad-y': '20px',
      '--layout-content-max-width': '1200px',
      '--layout-sider-margin': '14px',
      '--layout-sider-radius': '20px',
    },
  },
  'split-chrome': {
    id: 'split-chrome',
    sidebarSide: 'left',
    chromeKind: 'default',
    cssVars: {
      '--layout-sidebar-width': '200px',
      '--layout-card-gap': '16px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.84rem',
      '--layout-dash-value': '1.85rem',
      '--layout-page-pad-x': '48px',
      '--layout-page-pad-y': '20px',
      '--layout-page-pad-x-end': '20px',
      '--layout-content-max-width': 'none',
    },
  },
  'dual-rail': {
    id: 'dual-rail',
    sidebarSide: 'left',
    chromeKind: 'dual-rail',
    cssVars: {
      '--layout-sidebar-width': '220px',
      '--layout-rail-width': '56px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '28px',
      '--layout-page-pad-y': '18px',
      '--layout-content-max-width': '1400px',
    },
  },
  'rail-only': {
    id: 'rail-only',
    sidebarSide: 'left',
    chromeKind: 'rail-only',
    cssVars: {
      '--layout-sidebar-width': '64px',
      '--layout-card-gap': '12px',
      '--layout-card-pad': '14px',
      '--layout-font-base': '0.82rem',
      '--layout-dash-value': '1.7rem',
      '--layout-page-pad-x': '20px',
      '--layout-page-pad-y': '14px',
      '--layout-content-max-width': 'none',
    },
  },
  dock: {
    id: 'dock',
    sidebarSide: 'none',
    navChrome: 'bottom',
    chromeKind: 'dock',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.84rem',
      '--layout-dash-value': '1.85rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
      '--layout-bottom-nav-height': '64px',
      '--layout-dock-radius': '18px',
      '--layout-dock-margin': '12px',
    },
  },
  island: {
    id: 'island',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'island',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '16px',
      '--layout-card-pad': '18px',
      '--layout-font-base': '0.85rem',
      '--layout-dash-value': '1.9rem',
      '--layout-page-pad-x': '28px',
      '--layout-page-pad-y': '48px',
      '--layout-content-max-width': '1400px',
      '--layout-island-radius': '24px',
    },
  },
  theater: {
    id: 'theater',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'theater',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '18px',
      '--layout-card-pad': '20px',
      '--layout-font-base': '0.88rem',
      '--layout-dash-value': '2rem',
      '--layout-page-pad-x': '72px',
      '--layout-page-pad-y': '20px',
      '--layout-content-max-width': '1200px',
      '--layout-curtain-width': '48px',
    },
  },
  'dense-ops': {
    id: 'dense-ops',
    sidebarSide: 'left',
    chromeKind: 'dense-ops',
    cssVars: {
      '--layout-sidebar-width': '200px',
      '--layout-card-gap': '8px',
      '--layout-card-pad': '10px',
      '--layout-font-base': '0.76rem',
      '--layout-dash-value': '1.35rem',
      '--layout-page-pad-x': '12px',
      '--layout-page-pad-y': '8px',
      '--layout-content-max-width': 'none',
      '--layout-ticker-height': '28px',
      '--layout-status-height': '28px',
    },
  },
  magazine: {
    id: 'magazine',
    sidebarSide: 'right',
    chromeKind: 'magazine',
    cssVars: {
      '--layout-sidebar-width': '280px',
      '--layout-card-gap': '20px',
      '--layout-card-pad': '20px',
      '--layout-font-base': '0.88rem',
      '--layout-dash-value': '2rem',
      '--layout-page-pad-x': '32px',
      '--layout-page-pad-y': '24px',
      '--layout-content-max-width': '1100px',
    },
  },
  'split-pane': {
    id: 'split-pane',
    sidebarSide: 'left',
    chromeKind: 'split-pane',
    cssVars: {
      '--layout-sidebar-width': '320px',
      '--layout-card-gap': '12px',
      '--layout-card-pad': '14px',
      '--layout-font-base': '0.82rem',
      '--layout-dash-value': '1.7rem',
      '--layout-page-pad-x': '16px',
      '--layout-page-pad-y': '12px',
      '--layout-content-max-width': 'none',
    },
  },
  'top-tabs': {
    id: 'top-tabs',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'top-tabs',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
      '--layout-tabs-height': '40px',
    },
  },
  zen: {
    id: 'zen',
    sidebarSide: 'left',
    chromeKind: 'zen',
    cssVars: {
      '--layout-sidebar-width': '72px',
      '--layout-card-gap': '28px',
      '--layout-card-pad': '28px',
      '--layout-font-base': '0.92rem',
      '--layout-dash-value': '2.4rem',
      '--layout-page-pad-x': '64px',
      '--layout-page-pad-y': '40px',
      '--layout-content-max-width': '960px',
    },
  },
  command: {
    id: 'command',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'command',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '10px',
      '--layout-card-pad': '12px',
      '--layout-font-base': '0.8rem',
      '--layout-dash-value': '1.6rem',
      '--layout-page-pad-x': '12px',
      '--layout-page-pad-y': '8px',
      '--layout-content-max-width': 'none',
    },
  },
  'tri-chrome': {
    id: 'tri-chrome',
    sidebarSide: 'left',
    chromeKind: 'tri-chrome',
    cssVars: {
      '--layout-sidebar-width': '200px',
      '--layout-card-gap': '12px',
      '--layout-card-pad': '14px',
      '--layout-font-base': '0.82rem',
      '--layout-dash-value': '1.7rem',
      '--layout-page-pad-x': '16px',
      '--layout-page-pad-y': '12px',
      '--layout-content-max-width': '1600px',
      '--layout-bottom-nav-height': '48px',
    },
  },
  'card-stack': {
    id: 'card-stack',
    sidebarSide: 'left',
    chromeKind: 'card-stack',
    cssVars: {
      '--layout-sidebar-width': '200px',
      '--layout-card-gap': '18px',
      '--layout-card-pad': '18px',
      '--layout-font-base': '0.85rem',
      '--layout-dash-value': '1.9rem',
      '--layout-page-pad-x': '40px',
      '--layout-page-pad-y': '24px',
      '--layout-content-max-width': '1080px',
      '--layout-sider-margin': '16px',
      '--layout-sider-radius': '16px',
    },
  },
  'hud-frame': {
    id: 'hud-frame',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'hud-frame',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '10px',
      '--layout-card-pad': '12px',
      '--layout-font-base': '0.8rem',
      '--layout-dash-value': '1.6rem',
      '--layout-page-pad-x': '16px',
      '--layout-page-pad-y': '12px',
      '--layout-content-max-width': 'none',
      '--layout-bottom-nav-height': '52px',
    },
  },
  pipeline: {
    id: 'pipeline',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'pipeline',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '20px',
      '--layout-content-max-width': '1400px',
      '--layout-pipeline-height': '56px',
    },
  },
  'hero-header': {
    id: 'hero-header',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'hero-header',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '18px',
      '--layout-card-pad': '20px',
      '--layout-font-base': '0.88rem',
      '--layout-dash-value': '2.1rem',
      '--layout-page-pad-x': '32px',
      '--layout-page-pad-y': '24px',
      '--layout-content-max-width': '1200px',
      '--layout-hero-height': '96px',
    },
  },
  'status-strip': {
    id: 'status-strip',
    sidebarSide: 'left',
    chromeKind: 'status-strip',
    cssVars: {
      '--layout-sidebar-width': '40px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '20px',
      '--layout-page-pad-y': '14px',
      '--layout-content-max-width': '1600px',
    },
  },
  'drawer-peek': {
    id: 'drawer-peek',
    sidebarSide: 'left',
    chromeKind: 'drawer-peek',
    cssVars: {
      '--layout-sidebar-width': '260px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
      '--layout-drawer-peek': '8px',
    },
  },
  'corner-hub': {
    id: 'corner-hub',
    sidebarSide: 'none',
    navChrome: 'bottom',
    chromeKind: 'corner-hub',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.84rem',
      '--layout-dash-value': '1.85rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
      '--layout-hub-size': '72px',
    },
  },
  widescreen: {
    id: 'widescreen',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'widescreen',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '8px',
      '--layout-card-pad': '10px',
      '--layout-font-base': '0.78rem',
      '--layout-dash-value': '1.5rem',
      '--layout-page-pad-x': '8px',
      '--layout-page-pad-y': '6px',
      '--layout-content-max-width': 'none',
    },
  },
  'right-drawer': {
    id: 'right-drawer',
    sidebarSide: 'right',
    chromeKind: 'right-drawer',
    cssVars: {
      '--layout-sidebar-width': '280px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
    },
  },
  frame: {
    id: 'frame',
    sidebarSide: 'none',
    navChrome: 'top',
    chromeKind: 'frame',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '16px',
      '--layout-card-pad': '18px',
      '--layout-font-base': '0.85rem',
      '--layout-dash-value': '1.9rem',
      '--layout-page-pad-x': '36px',
      '--layout-page-pad-y': '28px',
      '--layout-content-max-width': '1400px',
      '--layout-frame-inset': '16px',
    },
  },
};

export function resolveThemeLayout(value: string | null | undefined): ThemeLayout {
  if (value && (THEME_LAYOUTS as string[]).includes(value)) {
    return value as ThemeLayout;
  }
  return 'standard';
}
