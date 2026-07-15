// Layout presets — spacing, sidebar geometry, font sizes, and chrome placement.
export type ThemeLayout =
  | 'standard'
  | 'compact'
  | 'wide'
  | 'sidebar-right'
  | 'topnav'
  | 'bottomnav'
  | 'floating'
  | 'split-chrome';

export interface LayoutPreset {
  id: ThemeLayout;
  cssVars: Record<string, string>;
  /** Physical sider side; 'none' means nav lives in top or bottom chrome. */
  sidebarSide: 'left' | 'right' | 'none';
  /** Where primary page nav is rendered when sidebarSide is none. */
  navChrome?: 'top' | 'bottom';
}

export const THEME_LAYOUTS: ThemeLayout[] = [
  'standard',
  'compact',
  'wide',
  'sidebar-right',
  'topnav',
  'bottomnav',
  'floating',
  'split-chrome',
];

export const LAYOUT_PRESETS: Record<ThemeLayout, LayoutPreset> = {
  standard: {
    id: 'standard',
    sidebarSide: 'left',
    cssVars: {
      '--layout-sidebar-width': '240px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
    },
  },
  compact: {
    id: 'compact',
    sidebarSide: 'left',
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
    cssVars: {
      '--layout-sidebar-width': '240px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
      '--layout-content-max-width': '1600px',
    },
  },
  topnav: {
    id: 'topnav',
    sidebarSide: 'none',
    navChrome: 'top',
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
};
