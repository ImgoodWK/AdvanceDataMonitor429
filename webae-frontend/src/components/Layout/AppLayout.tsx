import { useEffect, useRef, useState, useCallback } from 'react';
import { Button, Tooltip } from 'antd';
import { LeftOutlined, MenuUnfoldOutlined, RightOutlined } from '@ant-design/icons';
import { Layout } from 'antd';
import { useAppContext, type SidebarMode } from '@/context/AppContext';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { Dashboard } from '@/pages/Dashboard';
import { StoragePage } from '@/pages/Storage';
import { EssentiaPage } from '@/pages/Essentia';
import { CpuPage } from '@/pages/Cpu';
import { PowerPage } from '@/pages/Power';
import { GtMachinesPage } from '@/pages/GtMachines';
import { RecipesPage } from '@/pages/Recipes';
import { PatternEditorPage } from '@/pages/PatternEditor';
import { AeOrderingPage } from '@/pages/AeOrdering';
import { ChatPage } from '@/pages/Chat';
import { SettingsPage } from '@/pages/Settings';
import { NetworkTopologyPage } from '@/pages/NetworkTopology';
import { LinkScannerPage } from '@/pages/LinkScanner';
import { MonitorBindingsPage } from '@/pages/MonitorBindings';
import { PlannerPage } from '@/pages/Planner';
import { AssistantPage } from '@/pages/Assistant';
import { AlertsHistoryPage } from '@/pages/AlertsHistory';
import { CommandPalette, useCommandPaletteShortcut } from '@/components/CommandPalette';
import { useWebAlerts } from '@/hooks/useWebAlerts';
import { useEventStream } from '@/hooks/useEventStream';
import { LAYOUT_PRESETS } from '@/theme/layouts';
import { useI18n } from '@/i18n';
import type { PageId } from '@/context/AppContext';

const { Sider, Header, Content } = Layout;

/**
 * Floating side tab that cycles the sidebar through expanded ↔ collapsed ↔ hidden.
 * Always visible (even when the sidebar is hidden), attached to the screen edge
 * on the sidebar's side, vertically centered. Renders nothing in topnav layout.
 */
function SidebarToggleTab({
  sidebarMode,
  setSidebarMode,
  siderSide,
  siderWidth,
}: {
  sidebarMode: SidebarMode;
  setSidebarMode: (m: SidebarMode) => void;
  siderSide: 'left' | 'right' | 'none';
  siderWidth: number;
}) {
  const { t } = useI18n();
  if (siderSide === 'none') return null;

  const cycleNext = () => {
    const next: SidebarMode =
      sidebarMode === 'expanded' ? 'collapsed' : sidebarMode === 'collapsed' ? 'hidden' : 'expanded';
    setSidebarMode(next);
  };

  const modeLabel =
    sidebarMode === 'expanded'
      ? t('sidebarMode_expanded')
      : sidebarMode === 'collapsed'
        ? t('sidebarMode_collapsed')
        : t('sidebarMode_hidden');

  // Hidden sidebar → tab hugs the screen edge; otherwise sits at the sidebar's
  // outer edge so it visually attaches to the sidebar.
  const offset = sidebarMode === 'hidden' ? 0 : siderWidth;
  const sideProp = siderSide === 'right' ? 'right' : 'left';

  // Icon semantics:
  //   expanded  → arrow pointing toward the sidebar (collapse)
  //   collapsed → arrow pointing toward the sidebar (hide)
  //   hidden    → MenuUnfold (expand)
  // For a left sidebar the arrow points left (LeftOutlined); for a right
  // sidebar it points right (RightOutlined).
  const arrowIcon = siderSide === 'right' ? <RightOutlined /> : <LeftOutlined />;
  const icon = sidebarMode === 'hidden' ? <MenuUnfoldOutlined /> : arrowIcon;

  // Rounded corners only on the screen-facing side so the tab looks like it
  // "peeks out" from behind the sidebar / off the screen edge.
  const borderRadius =
    siderSide === 'right'
      ? '6px 0 0 6px' // right side: rounded on the left (free edge)
      : '0 6px 6px 0'; // left side: rounded on the right (free edge)

  return (
    <Tooltip title={modeLabel} placement={siderSide === 'right' ? 'left' : 'right'}>
      <Button
        type="default"
        size="small"
        icon={icon}
        onClick={cycleNext}
        aria-label={modeLabel}
        aria-pressed={sidebarMode === 'expanded'}
        style={{
          position: 'fixed',
          top: '50%',
          transform: 'translateY(-50%)',
          [sideProp]: offset,
          width: 22,
          height: 72,
          padding: 0,
          borderRadius,
          zIndex: 1000,
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border)',
          color: 'var(--text-secondary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: `${sideProp} 0.3s ease, background 0.2s, color 0.2s`,
          boxShadow: 'var(--shadow-elev, 0 2px 8px rgba(0,0,0,0.15))',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = 'var(--sidebar-hover)';
          e.currentTarget.style.color = 'var(--accent)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'var(--bg-secondary)';
          e.currentTarget.style.color = 'var(--text-secondary)';
        }}
      />
    </Tooltip>
  );
}

export function AppLayout() {
  const { activePage, setActivePage, themeLayout, sidebarMode, setSidebarMode } = useAppContext();
  const [commandOpen, setCommandOpen] = useState(false);
  const openCommand = useCallback(() => setCommandOpen(true), []);
  useCommandPaletteShortcut(openCommand);
  useEventStream(true);
  useWebAlerts(true);
  const preset = LAYOUT_PRESETS[themeLayout];
  const siderSide = preset.sidebarSide;
  const [layoutTransitionDisabled, setLayoutTransitionDisabled] = useState(false);
  const prevThemeLayout = useRef(themeLayout);

  useEffect(() => {
    if (prevThemeLayout.current === themeLayout) return;
    prevThemeLayout.current = themeLayout;
    setLayoutTransitionDisabled(true);
    const id = window.setTimeout(() => setLayoutTransitionDisabled(false), 50);
    return () => window.clearTimeout(id);
  }, [themeLayout]);

  const renderPage = () => {
    switch (activePage) {
      case 'dashboard':
        return <Dashboard />;
      case 'storage':
        return <StoragePage />;
      case 'essentia':
        return <EssentiaPage />;
      case 'cpu':
        return <CpuPage />;
      case 'power':
        return <PowerPage />;
      case 'topology':
        return <NetworkTopologyPage />;
      case 'gtmachines':
        return <GtMachinesPage />;
      case 'recipes':
        return <RecipesPage />;
      case 'pattern':
        return <PatternEditorPage />;
      case 'order':
        return <AeOrderingPage />;
      case 'chat':
        return <ChatPage />;
      case 'linkscanner':
        return <LinkScannerPage />;
      case 'monitorbindings':
        return <MonitorBindingsPage />;
      case 'planner':
        return <PlannerPage />;
      case 'assistant':
        return <AssistantPage />;
      case 'alertshistory':
        return <AlertsHistoryPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <Dashboard />;
    }
  };

  // topnav layout: no sidebar, navigation in the top bar
  if (siderSide === 'none') {
    return (
      <Layout className="webae-layout" style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar topnavMode pages={ALL_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={{
            padding: 'var(--layout-page-pad-y) var(--layout-page-pad-x)',
            overflow: 'auto',
            position: 'relative',
          }}
        >
          {renderPage()}
        </Content>
        <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
      </Layout>
    );
  }

  const siderWidth =
    sidebarMode === 'collapsed' ? 60 : sidebarMode === 'hidden' ? 0 : parseInt(preset.cssVars['--layout-sidebar-width'] || '240');
  const siderVisible = sidebarMode !== 'hidden';

  const siderTransition = layoutTransitionDisabled ? 'none' : 'all 0.3s ease';

  const sider = siderVisible && (
    <Sider
      width={siderWidth}
      trigger={null}
      style={{
        order: siderSide === 'right' ? 2 : 0,
        overflow: 'hidden',
        transition: siderTransition,
        flex: `0 0 ${siderWidth}px`,
        maxWidth: siderWidth,
        minWidth: siderWidth,
      }}
    >
      <Sidebar mode={sidebarMode} />
    </Sider>
  );

  return (
    <Layout
      className="webae-layout"
      style={{
        height: '100vh',
        flexDirection: siderSide === 'right' ? 'row-reverse' : 'row',
        transition: layoutTransitionDisabled ? 'none' : undefined,
      }}
    >
      {sider}
      <SidebarToggleTab
        sidebarMode={sidebarMode}
        setSidebarMode={setSidebarMode}
        siderSide={siderSide}
        siderWidth={siderWidth}
      />
      <Layout>
        <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
          <TopBar pages={ALL_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={{
            padding: 'var(--layout-page-pad-y) var(--layout-page-pad-x)',
            overflow: 'auto',
            position: 'relative',
          }}
        >
          {renderPage()}
        </Content>
        <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
      </Layout>
    </Layout>
  );
}

export const ALL_PAGES: Array<{ id: PageId; icon: string; labelKey: string }> = [
  { id: 'dashboard', icon: 'DashboardOutlined', labelKey: 'dashboard' },
  { id: 'storage', icon: 'DatabaseOutlined', labelKey: 'storage' },
  { id: 'essentia', icon: 'ExperimentOutlined', labelKey: 'essentiaPage' },
  { id: 'cpu', icon: 'HddOutlined', labelKey: 'cpuPage' },
  { id: 'power', icon: 'ThunderboltOutlined', labelKey: 'power' },
  { id: 'topology', icon: 'ApartmentOutlined', labelKey: 'topology' },
  { id: 'gtmachines', icon: 'SettingOutlined', labelKey: 'gtMachines' },
  { id: 'recipes', icon: 'BookOutlined', labelKey: 'recipes' },
  { id: 'pattern', icon: 'FormOutlined', labelKey: 'patternEditor' },
  { id: 'order', icon: 'ShoppingCartOutlined', labelKey: 'aeOrdering' },
  { id: 'chat', icon: 'MessageOutlined', labelKey: 'chat' },
  { id: 'linkscanner', icon: 'ScanOutlined', labelKey: 'linkScanner' },
  { id: 'monitorbindings', icon: 'EyeOutlined', labelKey: 'monitorBindings' },
  { id: 'planner', icon: 'CalendarOutlined', labelKey: 'plannerPage' },
  { id: 'assistant', icon: 'RobotOutlined', labelKey: 'assistantPage' },
  { id: 'alertshistory', icon: 'BellOutlined', labelKey: 'alertsHistoryPage' },
  { id: 'settings', icon: 'SettingOutlined', labelKey: 'settings' },
];
