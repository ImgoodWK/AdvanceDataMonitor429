import { useEffect, useRef, useState, useCallback, type ReactNode } from 'react';
import { Button, Tooltip, Space } from 'antd';
import { LeftOutlined, MenuUnfoldOutlined, RightOutlined } from '@ant-design/icons';
import { Layout } from 'antd';
import { useAppContext, type SidebarMode, type PageId } from '@/context/AppContext';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { Dashboard } from '@/pages/Dashboard';
import { StoragePage } from '@/pages/Storage';
import { EssentiaPage } from '@/pages/Essentia';
import { FluidsPage } from '@/pages/Fluids';
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
import { QuestBookPage } from '@/pages/QuestBook';
import { AssistantPage } from '@/pages/Assistant';
import { AlertsHistoryPage } from '@/pages/AlertsHistory';
import { AdminPage } from '@/pages/Admin';
import { PageStaleBanner } from '@/components/Layout/PageStaleBanner';
import { CommandPalette, useCommandPaletteShortcut } from '@/components/CommandPalette';
import { useWebAlerts } from '@/hooks/useWebAlerts';
import { useEventStream } from '@/hooks/useEventStream';
import { LAYOUT_PRESETS } from '@/theme/layouts';
import { useI18n } from '@/i18n';
import { NAV_PAGES } from './navConfig';

const { Sider, Header, Content, Footer } = Layout;

/**
 * Floating side tab that cycles the sidebar through expanded ↔ collapsed ↔ hidden.
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

  const offset = sidebarMode === 'hidden' ? 0 : siderWidth;
  const sideProp = siderSide === 'right' ? 'right' : 'left';
  const arrowIcon = siderSide === 'right' ? <RightOutlined /> : <LeftOutlined />;
  const icon = sidebarMode === 'hidden' ? <MenuUnfoldOutlined /> : arrowIcon;
  const borderRadius =
    siderSide === 'right' ? '6px 0 0 6px' : '0 6px 6px 0';

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

function BottomNav({
  activePage,
  setActivePage,
}: {
  activePage: PageId;
  setActivePage: (p: PageId) => void;
}) {
  const { t } = useI18n();
  const { isAdmin, isOnlineOp } = useAppContext();
  const visible = NAV_PAGES.filter((item) => {
    if (item.id === 'admin') return isAdmin || isOnlineOp;
    return true;
  });

  return (
    <nav className="webae-bottom-nav" aria-label="Main navigation">
      <Space size={4} wrap style={{ justifyContent: 'center', width: '100%' }}>
        {visible.map((item) => {
          const IconComp = item.Icon;
          const active = activePage === item.id;
          return (
            <Button
              key={item.id}
              type={active ? 'primary' : 'text'}
              size="small"
              className={'webae-bottom-nav-item' + (active ? ' webae-bottom-nav-item--active' : '')}
              icon={<IconComp />}
              onClick={() => setActivePage(item.id)}
              aria-current={active ? 'page' : undefined}
            >
              {t(item.labelKey)}
            </Button>
          );
        })}
      </Space>
    </nav>
  );
}

function PageTransition({ pageKey, children }: { pageKey: string; children: ReactNode }) {
  return (
    <div key={pageKey} className="page-transition">
      {children}
    </div>
  );
}

export function AppLayout() {
  const { activePage, setActivePage, themeLayout, sidebarMode, setSidebarMode } = useAppContext();
  const [commandOpen, setCommandOpen] = useState(false);
  const openCommand = useCallback(() => setCommandOpen(true), []);
  useCommandPaletteShortcut(openCommand);
  useEventStream(true);
  useWebAlerts(true);
  const preset = LAYOUT_PRESETS[themeLayout] || LAYOUT_PRESETS.standard;
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
      case 'fluids':
        return <FluidsPage />;
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
      case 'quests':
        return <QuestBookPage />;
      case 'assistant':
        return <AssistantPage />;
      case 'alertshistory':
        return <AlertsHistoryPage />;
      case 'admin':
        return <AdminPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <Dashboard />;
    }
  };

  const contentPad =
    themeLayout === 'split-chrome'
      ? 'var(--layout-page-pad-y) var(--layout-page-pad-x-end, var(--layout-page-pad-x)) var(--layout-page-pad-y) var(--layout-page-pad-x)'
      : 'var(--layout-page-pad-y) var(--layout-page-pad-x)';

  const pageBody = (
    <>
      <PageStaleBanner />
      <PageTransition pageKey={activePage}>{renderPage()}</PageTransition>
    </>
  );

  const layoutClass = `webae-layout webae-layout--${themeLayout}`;

  // bottomnav: top chrome (no page nav) + content + bottom nav
  if (themeLayout === 'bottomnav') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={{
            padding: contentPad,
            overflow: 'auto',
            position: 'relative',
            paddingBottom: 'calc(var(--layout-page-pad-y) + var(--layout-bottom-nav-height, 56px))',
          }}
        >
          {pageBody}
        </Content>
        <Footer className="webae-bottom-nav-footer" style={{ padding: 0, height: 'auto' }}>
          <BottomNav activePage={activePage} setActivePage={setActivePage} />
        </Footer>
        <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
      </Layout>
    );
  }

  // topnav (and any other none-sider with top chrome)
  if (siderSide === 'none') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar topnavMode pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={{
            padding: contentPad,
            overflow: 'auto',
            position: 'relative',
          }}
        >
          {pageBody}
        </Content>
        <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
      </Layout>
    );
  }

  const siderWidth =
    sidebarMode === 'collapsed'
      ? 60
      : sidebarMode === 'hidden'
        ? 0
        : parseInt(preset.cssVars['--layout-sidebar-width'] || '240', 10);
  const siderVisible = sidebarMode !== 'hidden';
  const siderTransition = layoutTransitionDisabled ? 'none' : 'all 0.3s ease';
  const floating = themeLayout === 'floating';

  const sider = siderVisible && (
    <Sider
      width={siderWidth}
      trigger={null}
      className={floating ? 'webae-sider--floating' : undefined}
      style={{
        order: siderSide === 'right' ? 2 : 0,
        overflow: 'hidden',
        transition: siderTransition,
        flex: `0 0 ${siderWidth}px`,
        maxWidth: siderWidth,
        minWidth: siderWidth,
        ...(floating
          ? {
              margin: 'var(--layout-sider-margin, 14px)',
              borderRadius: 'var(--layout-sider-radius, 20px)',
              height: 'calc(100vh - 2 * var(--layout-sider-margin, 14px))',
              alignSelf: 'center',
            }
          : {}),
      }}
    >
      <Sidebar mode={sidebarMode} />
    </Sider>
  );

  return (
    <Layout
      className={layoutClass}
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
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={{
            padding: contentPad,
            overflow: 'auto',
            position: 'relative',
          }}
        >
          {pageBody}
        </Content>
        <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />
      </Layout>
    </Layout>
  );
}

export { ALL_PAGES, NAV_PAGES } from './navConfig';
