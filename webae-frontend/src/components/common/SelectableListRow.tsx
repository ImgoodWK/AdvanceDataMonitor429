import type { CSSProperties, KeyboardEvent, MouseEvent, ReactNode } from 'react';

export type SelectableListRowVariant = 'plain' | 'card';

export interface SelectableListRowProps {
  selected?: boolean;
  active?: boolean;
  hovered?: boolean;
  variant?: SelectableListRowVariant;
  as?: 'button' | 'div';
  onClick?: (e: MouseEvent) => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
  onKeyDown?: (e: KeyboardEvent) => void;
  leading?: ReactNode;
  trailing?: ReactNode;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  opacity?: number;
  ariaLabel?: string;
  ariaCurrent?: boolean;
  tabIndex?: number;
}

function buildClassName(
  variant: SelectableListRowVariant,
  selected?: boolean,
  active?: boolean,
  hovered?: boolean,
  extra?: string
): string {
  const base = variant === 'card' ? 'webae-list-row webae-list-row--card' : 'webae-list-row';
  let cls = base;
  if (selected) cls += ' webae-list-row--selected';
  if (active) cls += ' webae-list-row--active';
  if (hovered) cls += ' webae-list-row--hover';
  if (extra) cls += ' ' + extra;
  return cls;
}

/** Unified selectable list row — quest lists, pattern sidebar, topology devices. */
export function SelectableListRow({
  selected,
  active,
  hovered,
  variant = 'plain',
  as = 'button',
  onClick,
  onMouseEnter,
  onMouseLeave,
  onKeyDown,
  leading,
  trailing,
  children,
  className,
  style,
  opacity,
  ariaLabel,
  ariaCurrent,
  tabIndex,
}: SelectableListRowProps) {
  const cls = buildClassName(variant, selected, active, hovered, className);
  const shared = {
    className: cls,
    style: opacity != null ? { ...style, opacity } : style,
    onClick,
    onMouseEnter,
    onMouseLeave,
    onKeyDown,
  };

  const content = (
    <>
      {leading ? <span className="webae-list-row-leading">{leading}</span> : null}
      <span className="webae-list-row-body">{children}</span>
      {trailing ? <span className="webae-list-row-trailing">{trailing}</span> : null}
    </>
  );

  if (as === 'div') {
    return (
      <div
        role="button"
        tabIndex={tabIndex ?? 0}
        aria-label={ariaLabel}
        {...shared}
      >
        {content}
      </div>
    );
  }

  return (
    <button
      type="button"
      aria-label={ariaLabel}
      aria-current={ariaCurrent ? 'true' : undefined}
      tabIndex={tabIndex}
      {...shared}
    >
      {content}
    </button>
  );
}
