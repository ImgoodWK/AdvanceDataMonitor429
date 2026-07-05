import type { ReactNode } from 'react';
import { Typography, Space } from 'antd';

const { Title, Text } = Typography;

interface PageShellProps {
  title?: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
}

/**
 * Unified page container — consistent padding, title row, and action slot.
 */
export function PageShell({ title, description, actions, children }: PageShellProps) {
  return (
    <div
      className="page-shell"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--layout-card-gap, 16px)',
        maxWidth: 'var(--layout-content-max-width, none)',
        margin: '0 auto',
        width: '100%',
      }}
    >
      {(title || actions) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <Space direction="vertical" size={2}>
            {title && (
              <Title level={4} style={{ margin: 0 }}>
                {title}
              </Title>
            )}
            {description && <Text type="secondary">{description}</Text>}
          </Space>
          {actions && <div style={{ flexShrink: 0 }}>{actions}</div>}
        </div>
      )}
      {children}
    </div>
  );
}
