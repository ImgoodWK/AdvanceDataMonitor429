import { Tooltip, Typography } from 'antd';
import {
  DashboardOutlined,
  DatabaseOutlined,
  HddOutlined,
  ThunderboltOutlined,
  SettingOutlined,
  BookOutlined,
  FormOutlined,
  ShoppingCartOutlined,
  MessageOutlined,
  ApartmentOutlined,
  ScanOutlined,
  EyeOutlined,
  CalendarOutlined,
  RobotOutlined,
  BellOutlined,
} from '@ant-design/icons';
import { useAppContext, type SidebarMode, type PageId } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { ALL_PAGES } from './AppLayout';

const { Text } = Typography;

const ICON_MAP: Record<string, React.ComponentType<{ style?: React.CSSProperties }>> = {
  DashboardOutlined,
  DatabaseOutlined,
  HddOutlined,
  ThunderboltOutlined,
  SettingOutlined,
  BookOutlined,
  FormOutlined,
  ShoppingCartOutlined,
  MessageOutlined,
  ApartmentOutlined,
  ScanOutlined,
  EyeOutlined,
  CalendarOutlined,
  RobotOutlined,
  BellOutlined,
};

export function Sidebar({ mode }: { mode: SidebarMode }) {
  const { activePage, setActivePage } = useAppContext();
  const { t } = useI18n();
  const collapsed = mode === 'collapsed';

  const navItems: Array<{ id: PageId; icon: string; labelKey: string }> = ALL_PAGES;

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--sidebar-bg)',
      }}
    >
      {/* Header / Logo */}
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

      {/* Nav items — bottom cycleMode button removed in Phase 2; the floating
          side tab in AppLayout now handles expanded ↔ collapsed ↔ hidden. */}
      <nav style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }} aria-label="Main navigation">
        {navItems.map((item) => {
          const IconComp = ICON_MAP[item.icon] || DashboardOutlined;
          const isActive = activePage === item.id;
          return (
            <Tooltip key={item.id} title={collapsed ? t(item.labelKey) : ''} placement="right">
              <button
                onClick={() => setActivePage(item.id)}
                aria-label={t(item.labelKey)}
                aria-current={isActive ? 'page' : undefined}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  width: '100%',
                  padding: collapsed ? '12px 8px' : '10px 16px',
                  background: isActive ? 'var(--sidebar-active)' : 'transparent',
                  border: 'none',
                  color: isActive ? 'var(--accent)' : 'var(--text-secondary)',
                  cursor: 'pointer',
                  fontSize: 'var(--layout-font-base, 0.83rem)',
                  justifyContent: collapsed ? 'center' : 'flex-start',
                  transition: 'background 0.2s, color 0.2s',
                }}
                onMouseEnter={(e) => {
                  if (!isActive) e.currentTarget.style.background = 'var(--sidebar-hover)';
                }}
                onMouseLeave={(e) => {
                  if (!isActive) e.currentTarget.style.background = 'transparent';
                }}
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
