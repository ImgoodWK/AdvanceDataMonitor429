import { useCallback, useEffect, useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Spin } from 'antd';
import { PatternOrderCard } from '@/components/patterns/PatternOrderCard';
import type { PatternListEntryDto } from '@/types/dto';

const GRID_GAP = 12;
const MIN_COL_WIDTH = 120;
const ROW_HEIGHT = 148;

function patternEntryKey(p: PatternListEntryDto): string {
  return p.patternId || p.gridKey || '';
}

interface VirtualPatternGridProps {
  patterns: PatternListEntryDto[];
  t: (k: string) => string;
  hasMore: boolean;
  loadingMore: boolean;
  onScrollEnd: () => void;
  onInfo: (p: PatternListEntryDto) => void;
  onOrder: (p: PatternListEntryDto) => void;
}

/**
 * Row-virtualized CSS grid for pattern browse (byPattern view).
 * Only visible rows are mounted; infinite scroll triggers onScrollEnd.
 */
export function VirtualPatternGrid({
  patterns,
  t,
  hasMore,
  loadingMore,
  onScrollEnd,
  onInfo,
  onOrder,
}: VirtualPatternGridProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const [columnCount, setColumnCount] = useState(4);

  useEffect(() => {
    const el = parentRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      const w = el.clientWidth;
      const cols = Math.max(1, Math.floor((w + GRID_GAP) / (MIN_COL_WIDTH + GRID_GAP)));
      setColumnCount(cols);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const rowCount = Math.ceil(patterns.length / columnCount) + (hasMore || loadingMore ? 1 : 0);

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT + GRID_GAP,
    overscan: 2,
  });

  const handleScroll = useCallback(() => {
    const el = parentRef.current;
    if (!el || !hasMore || loadingMore) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - ROW_HEIGHT * 2) {
      onScrollEnd();
    }
  }, [hasMore, loadingMore, onScrollEnd]);

  const virtualRows = virtualizer.getVirtualItems();

  return (
    <div
      ref={parentRef}
      className="virtual-pattern-grid"
      style={{ maxHeight: 520, overflow: 'auto' }}
      onScroll={handleScroll}
    >
      <div
        style={{
          height: virtualizer.getTotalSize(),
          width: '100%',
          position: 'relative',
        }}
      >
        {virtualRows.map((vRow) => {
          const rowIndex = vRow.index;
          const isLoaderRow = rowIndex >= Math.ceil(patterns.length / columnCount);

          if (isLoaderRow) {
            return (
              <div
                key={vRow.key}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: vRow.size,
                  transform: `translateY(${vRow.start}px)`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                {loadingMore && <Spin size="small" />}
              </div>
            );
          }

          const startIdx = rowIndex * columnCount;
          const rowItems = patterns.slice(startIdx, startIdx + columnCount);

          return (
            <div
              key={vRow.key}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: vRow.size,
                transform: `translateY(${vRow.start}px)`,
                display: 'grid',
                gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))`,
                gap: GRID_GAP,
                paddingBottom: GRID_GAP,
              }}
            >
              {rowItems.map((p) => {
                const key = patternEntryKey(p);
                return (
                  <PatternOrderCard
                    key={key}
                    pattern={p}
                    t={t}
                    onInfo={() => onInfo(p)}
                    onOrder={() => onOrder(p)}
                  />
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
