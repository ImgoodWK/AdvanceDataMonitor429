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
 * CSS hooks (page-shell__*) let pageStyle compose chrome without forking pages.
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
        <div className="page-shell__header">
          <Space className="page-shell__titles" direction="vertical" size={2}>
            {title && (
              <Title level={4} className="page-shell__title" style={{ margin: 0 }}>
                {title}
              </Title>
            )}
            {description && (
              <Text className="page-shell__desc" type="secondary">
                {description}
              </Text>
            )}
          </Space>
          {actions && <div className="page-shell__actions">{actions}</div>}
        </div>
      )}
      <div className="page-shell__body">{children}</div>
    </div>
  );
}
