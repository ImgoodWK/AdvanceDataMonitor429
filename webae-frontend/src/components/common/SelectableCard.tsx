import type { KeyboardEvent, ReactNode } from 'react';
import { Card, type CardProps } from 'antd';

const CARD_BODY_STYLE: CardProps['styles'] = {
  body: {
    padding: 8,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: '100%',
  },
};

export interface SelectableCardProps extends Omit<CardProps, 'onClick' | 'children' | 'styles'> {
  selected?: boolean;
  onClick?: () => void;
  children: ReactNode;
  cardStyles?: CardProps['styles'];
}

/** Square thumbnail card with optional selection outline — recipes, pattern browse grids. */
export function SelectableCard({
  selected,
  onClick,
  className,
  children,
  cardStyles,
  ...rest
}: SelectableCardProps) {
  const cls = [
    'recipe-thumbnail-card',
    'webae-selectable-card',
    selected ? 'webae-selectable-card--selected' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const handleKeyDown = onClick
    ? (e: KeyboardEvent<HTMLDivElement>) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }
    : undefined;

  return (
    <Card
      size="small"
      hoverable={Boolean(onClick)}
      onClick={onClick}
      className={cls}
      styles={cardStyles ?? CARD_BODY_STYLE}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-pressed={selected}
      onKeyDown={handleKeyDown}
      {...rest}
    >
      {children}
    </Card>
  );
}
