import { useEffect, useRef, useState, useCallback, type CSSProperties, type ReactNode } from 'react';
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
import { SparkPage } from '@/pages/Spark';
import { AdminPage } from '@/pages/Admin';
import { PageStaleBanner } from '@/components/Layout/PageStaleBanner';
import { CommandPalette, useCommandPaletteShortcut } from '@/components/CommandPalette';
import { useWebAlerts } from '@/hooks/useWebAlerts';
import { useEventStream } from '@/hooks/useEventStream';
import { LAYOUT_PRESETS, type ChromeKind } from '@/theme/layouts';
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
  className,
}: {
  activePage: PageId;
  setActivePage: (p: PageId) => void;
  className?: string;
}) {
  const { t } = useI18n();
  const { isAdmin, isOnlineOp, serverConfig } = useAppContext();
  const visible = NAV_PAGES.filter((item) => {
    if (item.id === 'admin') return isAdmin || isOnlineOp;
    if (item.id === 'spark') return !!serverConfig?.sparkEnabled;
    return true;
  });

  return (
    <nav className={className || 'webae-bottom-nav'} aria-label="Main navigation">
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

function TickerStrip() {
  return (
    <div className="webae-layout-ticker" aria-hidden>
      <span className="webae-layout-ticker__pulse" />
      <span>WebAE OPS</span>
      <span className="webae-layout-ticker__sep">|</span>
      <span>NETWORK LIVE</span>
    </div>
  );
}

function StatusStrip() {
  return (
    <div className="webae-layout-status" aria-hidden>
      <span>READY</span>
      <span className="webae-layout-ticker__sep">·</span>
      <span>AE2</span>
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
  const chromeKind: ChromeKind = preset.chromeKind || 'default';
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
      case 'spark':
        return <SparkPage />;
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

  const layoutClass = `webae-layout webae-layout--${themeLayout} webae-chrome--${chromeKind}`;
  const palette = <CommandPalette open={commandOpen} onClose={() => setCommandOpen(false)} />;

  const contentStyle = (extra?: CSSProperties): CSSProperties => ({
    padding: contentPad,
    overflow: 'auto',
    position: 'relative',
    ...extra,
  });

  const resolvedSiderMode: SidebarMode =
    chromeKind === 'rail-only' || chromeKind === 'status-strip' || chromeKind === 'zen'
      ? 'collapsed'
      : sidebarMode;

  const siderWidth =
    resolvedSiderMode === 'collapsed'
      ? chromeKind === 'status-strip'
        ? 40
        : 60
      : resolvedSiderMode === 'hidden'
        ? 0
        : parseInt(preset.cssVars['--layout-sidebar-width'] || '240', 10);

  const siderVisible = resolvedSiderMode !== 'hidden';
  const siderTransition = layoutTransitionDisabled ? 'none' : 'all 0.3s ease';
  const floatingSider =
    themeLayout === 'floating' || chromeKind === 'card-stack' || chromeKind === 'drawer-peek';

  const buildSider = (opts?: {
    forceCollapsed?: boolean;
    overlay?: boolean;
    order?: number;
    className?: string;
  }) => {
    if (!siderVisible && !opts?.overlay) return null;
    const width = opts?.forceCollapsed
      ? parseInt(preset.cssVars['--layout-rail-width'] || '56', 10)
      : siderWidth;
    const mode: SidebarMode = opts?.forceCollapsed ? 'collapsed' : resolvedSiderMode;
    return (
      <Sider
        width={width}
        trigger={null}
        className={
          (floatingSider ? 'webae-sider--floating ' : '') + (opts?.className || '')
        }
        style={{
          order: opts?.order ?? (siderSide === 'right' ? 2 : 0),
          overflow: 'hidden',
          transition: siderTransition,
          flex: `0 0 ${width}px`,
          maxWidth: width,
          minWidth: width,
          ...(floatingSider
            ? {
                margin: 'var(--layout-sider-margin, 14px)',
                borderRadius: 'var(--layout-sider-radius, 20px)',
                height: 'calc(100vh - 2 * var(--layout-sider-margin, 14px))',
                alignSelf: 'center',
              }
            : {}),
          ...(opts?.overlay
            ? {
                position: 'fixed' as const,
                right: siderSide === 'right' ? 0 : undefined,
                left: siderSide === 'left' ? 0 : undefined,
                top: 0,
                height: '100vh',
                zIndex: 900,
                boxShadow: 'var(--shadow-elev, -4px 0 24px rgba(0,0,0,0.35))',
              }
            : {}),
        }}
      >
        <Sidebar mode={mode} />
      </Sider>
    );
  };

  // --- Batch3 chrome kinds ---

  if (chromeKind === 'dock') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content id="main-content" className="app-content" style={contentStyle({
          paddingBottom: 'calc(var(--layout-page-pad-y) + var(--layout-bottom-nav-height, 64px) + var(--layout-dock-margin, 12px))',
        })}>
          {pageBody}
        </Content>
        <div className="webae-dock-wrap">
          <BottomNav activePage={activePage} setActivePage={setActivePage} className="webae-bottom-nav webae-dock" />
        </div>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'island' || chromeKind === 'pipeline' || chromeKind === 'hero-header' || chromeKind === 'widescreen' || chromeKind === 'command' || chromeKind === 'frame' || chromeKind === 'theater') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header
          className={
            chromeKind === 'hero-header'
              ? 'webae-hero-header'
              : chromeKind === 'island'
                ? 'webae-island-header'
                : chromeKind === 'pipeline'
                  ? 'webae-pipeline-header'
                  : undefined
          }
          style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}
        >
          <TopBar topnavMode pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className={'app-content' + (chromeKind === 'theater' ? ' webae-theater-content' : '') + (chromeKind === 'frame' ? ' webae-frame-content' : '')}
          style={contentStyle()}
        >
          {pageBody}
        </Content>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'hud-frame') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar topnavMode pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content id="main-content" className="app-content" style={contentStyle({
          paddingBottom: 'calc(var(--layout-page-pad-y) + var(--layout-bottom-nav-height, 52px))',
        })}>
          {pageBody}
        </Content>
        <Footer className="webae-bottom-nav-footer webae-hud-footer" style={{ padding: 0, height: 'auto' }}>
          <BottomNav activePage={activePage} setActivePage={setActivePage} />
        </Footer>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'top-tabs') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <div className="webae-top-tabs">
          <BottomNav activePage={activePage} setActivePage={setActivePage} className="webae-bottom-nav webae-top-tabs-nav" />
        </div>
        <Content id="main-content" className="app-content" style={contentStyle()}>
          {pageBody}
        </Content>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'corner-hub') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content id="main-content" className="app-content" style={contentStyle()}>
          {pageBody}
        </Content>
        <div className="webae-corner-hub">
          <BottomNav activePage={activePage} setActivePage={setActivePage} className="webae-bottom-nav webae-corner-hub-nav" />
        </div>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'dense-ops') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh', flexDirection: 'column' }}>
        <TickerStrip />
        <Layout style={{ flex: 1, minHeight: 0, flexDirection: 'row' }}>
          {buildSider()}
          <SidebarToggleTab
            sidebarMode={sidebarMode}
            setSidebarMode={setSidebarMode}
            siderSide="left"
            siderWidth={siderWidth}
          />
          <Layout>
            <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
              <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
            </Header>
            <Content id="main-content" className="app-content" style={contentStyle()}>
              {pageBody}
            </Content>
          </Layout>
        </Layout>
        <StatusStrip />
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'tri-chrome') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh', flexDirection: 'column' }}>
        <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Layout style={{ flex: 1, minHeight: 0, flexDirection: 'row' }}>
          {buildSider()}
          <SidebarToggleTab
            sidebarMode={sidebarMode}
            setSidebarMode={setSidebarMode}
            siderSide="left"
            siderWidth={siderWidth}
          />
          <Content id="main-content" className="app-content" style={contentStyle({
            paddingBottom: 'calc(var(--layout-page-pad-y) + var(--layout-bottom-nav-height, 48px))',
          })}>
            {pageBody}
          </Content>
        </Layout>
        <Footer className="webae-bottom-nav-footer" style={{ padding: 0, height: 'auto' }}>
          <BottomNav activePage={activePage} setActivePage={setActivePage} />
        </Footer>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'dual-rail') {
    const railW = parseInt(preset.cssVars['--layout-rail-width'] || '56', 10);
    return (
      <Layout
        className={layoutClass}
        style={{
          height: '100vh',
          flexDirection: 'row',
          transition: layoutTransitionDisabled ? 'none' : undefined,
        }}
      >
        {buildSider({ forceCollapsed: true, className: 'webae-sider--rail' })}
        {buildSider({ order: 1 })}
        <SidebarToggleTab
          sidebarMode={sidebarMode}
          setSidebarMode={setSidebarMode}
          siderSide="left"
          siderWidth={railW + siderWidth}
        />
        <Layout style={{ order: 2 }}>
          <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
            <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
          </Header>
          <Content id="main-content" className="app-content" style={contentStyle()}>
            {pageBody}
          </Content>
        </Layout>
        {palette}
      </Layout>
    );
  }

  if (chromeKind === 'right-drawer') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh', flexDirection: 'row' }}>
        <Layout style={{ flex: 1, minWidth: 0 }}>
          <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
            <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
          </Header>
          <Content id="main-content" className="app-content" style={contentStyle({
            paddingRight: siderVisible ? undefined : contentPad,
          })}>
            {pageBody}
          </Content>
        </Layout>
        {buildSider({ overlay: sidebarMode === 'expanded', className: 'webae-sider--drawer-right' })}
        <SidebarToggleTab
          sidebarMode={sidebarMode}
          setSidebarMode={setSidebarMode}
          siderSide="right"
          siderWidth={siderWidth}
        />
        {palette}
      </Layout>
    );
  }

  // default path: bottomnav
  if (themeLayout === 'bottomnav') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content
          id="main-content"
          className="app-content"
          style={contentStyle({
            paddingBottom: 'calc(var(--layout-page-pad-y) + var(--layout-bottom-nav-height, 56px))',
          })}
        >
          {pageBody}
        </Content>
        <Footer className="webae-bottom-nav-footer" style={{ padding: 0, height: 'auto' }}>
          <BottomNav activePage={activePage} setActivePage={setActivePage} />
        </Footer>
        {palette}
      </Layout>
    );
  }

  // topnav (and any other none-sider with top chrome / default)
  if (siderSide === 'none') {
    return (
      <Layout className={layoutClass} style={{ height: '100vh' }}>
        <Header style={{ padding: 0, height: 'auto' }}>
          <TopBar topnavMode pages={NAV_PAGES} activePage={activePage} setActivePage={setActivePage} />
        </Header>
        <Content id="main-content" className="app-content" style={contentStyle()}>
          {pageBody}
        </Content>
        {palette}
      </Layout>
    );
  }

  const floating = themeLayout === 'floating' || chromeKind === 'card-stack';

  const sider = siderVisible && (
    <Sider
      width={siderWidth}
      trigger={null}
      className={
        (floating ? 'webae-sider--floating ' : '') +
        (chromeKind === 'drawer-peek' ? 'webae-sider--peek ' : '') +
        (chromeKind === 'magazine' ? 'webae-sider--magazine ' : '')
      }
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
      <Sidebar mode={resolvedSiderMode} />
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
      {chromeKind !== 'rail-only' && chromeKind !== 'status-strip' && chromeKind !== 'zen' && (
        <SidebarToggleTab
          sidebarMode={sidebarMode}
          setSidebarMode={setSidebarMode}
          siderSide={siderSide}
          siderWidth={siderWidth}
        />
      )}
      <Layout>
        <Header style={{ padding: 0, height: 'auto', lineHeight: 'normal' }}>
          <TopBar
            topnavMode={chromeKind === 'status-strip'}
            pages={NAV_PAGES}
            activePage={activePage}
            setActivePage={setActivePage}
          />
        </Header>
        <Content id="main-content" className="app-content" style={contentStyle()}>
          {pageBody}
        </Content>
        {palette}
      </Layout>
    </Layout>
  );
}

export { ALL_PAGES, NAV_PAGES } from './navConfig';
