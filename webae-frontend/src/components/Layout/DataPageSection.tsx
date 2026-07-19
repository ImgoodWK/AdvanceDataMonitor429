import { useId, type ReactNode } from 'react';
import { Typography } from 'antd';

const { Text, Title } = Typography;

type DataPageSectionVariant = 'overview' | 'details' | 'insight';

interface DataPageSectionProps {
  title: string;
  description?: string;
  eyebrow?: string;
  icon?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  variant?: DataPageSectionVariant;
  className?: string;
  bodyClassName?: string;
}

/**
 * Shared visual boundary for data-heavy pages. It keeps editable overview
 * workspaces clearly separated from searchable tables and secondary insight.
 */
export function DataPageSection({
  title,
  description,
  eyebrow,
  icon,
  actions,
  children,
  variant = 'details',
  className = '',
  bodyClassName = '',
}: DataPageSectionProps) {
  const titleId = useId();

  return (
    <section
      className={`data-page-section data-page-section--${variant} ${className}`.trim()}
      aria-labelledby={titleId}
    >
      <div className="data-page-section__header">
        <div className="data-page-section__heading">
          {icon && <span className="data-page-section__icon">{icon}</span>}
          <div className="data-page-section__heading-copy">
            {eyebrow && <span className="data-page-section__eyebrow">{eyebrow}</span>}
            <Title id={titleId} level={5} className="data-page-section__title">
              {title}
            </Title>
            {description && (
              <Text type="secondary" className="data-page-section__description">
                {description}
              </Text>
            )}
          </div>
        </div>
        {actions && <div className="data-page-section__actions">{actions}</div>}
      </div>
      <div className={`data-page-section__body ${bodyClassName}`.trim()}>{children}</div>
    </section>
  );
}
