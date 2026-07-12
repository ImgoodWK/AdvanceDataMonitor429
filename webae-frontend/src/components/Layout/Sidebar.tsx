import { Tooltip, Typography } from 'antd';
import { useAppContext, type SidebarMode } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { NAV_PAGES } from './navConfig';

const { Text } = Typography;

export function Sidebar({ mode }: { mode: SidebarMode }) {
  const { activePage, setActivePage } = useAppContext();
  const { t } = useI18n();
  const collapsed = mode === 'collapsed';

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--sidebar-bg)',
      }}
    >
      <div
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

      <nav style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }} aria-label="Main navigation">
        {NAV_PAGES.map((item) => {
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
