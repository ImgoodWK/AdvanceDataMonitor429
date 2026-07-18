import { Tooltip, Typography } from 'antd';
import { useAppContext, type SidebarMode } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { NAV_PAGES } from './navConfig';

const { Text } = Typography;

export function Sidebar({ mode }: { mode: SidebarMode }) {
  const { activePage, setActivePage, isAdmin, isOnlineOp, serverConfig } = useAppContext();
  const { t } = useI18n();
  const collapsed = mode === 'collapsed';

  const visiblePages = NAV_PAGES.filter((item) => {
    if (item.id === 'admin') return isAdmin || isOnlineOp;
    if (item.id === 'spark') return !!serverConfig?.sparkEnabled;
    return true;
  });

  return (
    <div
      className="webae-sidebar"
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--sidebar-bg)',
      }}
    >
      <div
        className="webae-sidebar-brand"
        style={{
          padding: collapsed ? '14px 8px' : '16px 16px',
          borderBottom: '1px solid var(--border)',
          textAlign: collapsed ? 'center' : 'left',
        }}
      >
        <Text strong style={{ color: 'var(--accent)', fontSize: collapsed ? '0.7rem' : '1rem' }}>
          {collapsed ? 'TT' : 'TeXTech WebAE'}
        </Text>
      </div>

      <nav
        className="webae-sidebar-nav"
        style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}
        aria-label="Main navigation"
      >
        {visiblePages.map((item) => {
          const IconComp = item.Icon;
          const isActive = activePage === item.id;
          const navClass =
            'webae-nav-item' + (isActive ? ' webae-nav-item--active' : '') + (collapsed ? ' webae-nav-item--collapsed' : '');
          return (
            <Tooltip key={item.id} title={collapsed ? t(item.labelKey) : ''} placement="right">
              <button
                type="button"
                className={navClass}
                onClick={() => setActivePage(item.id)}
                aria-label={t(item.labelKey)}
                aria-current={isActive ? 'page' : undefined}
              >
                <IconComp style={{ fontSize: '1.1rem', flexShrink: 0 }} />
                {!collapsed && <span>{t(item.labelKey)}</span>}
              </button>
            </Tooltip>
          );
        })}
      </nav>
    </div>
  );
}
