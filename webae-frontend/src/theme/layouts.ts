// Layout presets — 5 presets controlling spacing, sidebar geometry, and font sizes.
export type ThemeLayout =
  | 'standard'
  | 'compact'
  | 'wide'
  | 'sidebar-right'
  | 'topnav';

export interface LayoutPreset {
  id: ThemeLayout;
  cssVars: Record<string, string>;
  sidebarSide: 'left' | 'right' | 'none';
}

export const THEME_LAYOUTS: ThemeLayout[] = [
  'standard',
  'compact',
  'wide',
  'sidebar-right',
  'topnav',
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
    },
  },
  topnav: {
    id: 'topnav',
    sidebarSide: 'none',
    cssVars: {
      '--layout-sidebar-width': '0px',
      '--layout-card-gap': '14px',
      '--layout-card-pad': '16px',
      '--layout-font-base': '0.83rem',
      '--layout-dash-value': '1.8rem',
      '--layout-page-pad-x': '24px',
      '--layout-page-pad-y': '16px',
    },
  },
};
