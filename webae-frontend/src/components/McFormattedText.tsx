import { Fragment, type CSSProperties } from 'react';

import { parseMcFormatting } from '@/utils/mcFormatting';

interface McFormattedTextProps {
  text: string;
  style?: CSSProperties;
  className?: string;
  /** Single-line ellipsis (e.g. sidebar list rows). */
  ellipsis?: boolean;
  /** Preserve line breaks in descriptions. */
  preWrap?: boolean;
}

function McFormattedLine({
  text,
  style,
  className,
  ellipsis,
}: {
  text: string;
  style?: CSSProperties;
  className?: string;
  ellipsis?: boolean;
}) {
  const segments = parseMcFormatting(text);
  const wrapperStyle: CSSProperties = {
    ...style,
    ...(ellipsis
      ? { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', display: 'block' }
      : {}),
  };

  return (
    <span className={className} style={wrapperStyle}>
      {segments.map((seg, i) => {
        const segStyle: CSSProperties = {};
        if (seg.color) segStyle.color = seg.color;
        if (seg.bold) segStyle.fontWeight = 'bold';
        if (seg.italic) segStyle.fontStyle = 'italic';
        const decorations: string[] = [];
        if (seg.underline) decorations.push('underline');
        if (seg.strikethrough) decorations.push('line-through');
        if (decorations.length) segStyle.textDecoration = decorations.join(' ');

        const hasStyle =
          seg.color || seg.bold || seg.italic || seg.underline || seg.strikethrough;
        if (!hasStyle) {
          return <Fragment key={i}>{seg.text}</Fragment>;
        }
        return (
          <span key={i} style={segStyle}>
            {seg.text}
          </span>
        );
      })}
    </span>
  );
}

export function McFormattedText({
  text,
  style,
  className,
  ellipsis,
  preWrap,
}: McFormattedTextProps) {
  if (text.includes('\n')) {
    const lines = text.split('\n');
    return (
      <span
        className={className}
        style={{ ...style, ...(preWrap ? { whiteSpace: 'pre-wrap' } : undefined) }}
      >
        {lines.map((line, i) => (
          <Fragment key={i}>
            {i > 0 ? <br /> : null}
            <McFormattedLine text={line} ellipsis={ellipsis && i === 0} />
          </Fragment>
        ))}
      </span>
    );
  }

  return (
    <McFormattedLine text={text} style={style} className={className} ellipsis={ellipsis} />
  );
}
