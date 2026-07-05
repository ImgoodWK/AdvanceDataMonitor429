import { useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { PatternListEntryDto } from '@/types/dto';
import type { ReactNode } from 'react';

const ROW_HEIGHT = 52;

interface VirtualPatternListProps {
  patterns: PatternListEntryDto[];
  renderItem: (pattern: PatternListEntryDto) => ReactNode;
  maxHeight?: string | number;
}

/**
 * Single-column virtual list for the Pattern Editor sidebar.
 * Renders only visible rows to keep large pattern sets responsive.
 */
export function VirtualPatternList({
  patterns,
  renderItem,
  maxHeight = 'calc(100vh - 320px)',
}: VirtualPatternListProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: patterns.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 6,
  });

  return (
    <div
      ref={parentRef}
      style={{
        maxHeight,
        minHeight: 240,
        overflow: 'auto',
        paddingRight: 4,
      }}
    >
      <div
        style={{
          height: virtualizer.getTotalSize(),
          width: '100%',
          position: 'relative',
        }}
      >
        {virtualizer.getVirtualItems().map((virtualRow) => {
          const pattern = patterns[virtualRow.index];
          return (
            <div
              key={pattern.patternId}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: virtualRow.size,
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              {renderItem(pattern)}
            </div>
          );
        })}
      </div>
    </div>
  );
}
