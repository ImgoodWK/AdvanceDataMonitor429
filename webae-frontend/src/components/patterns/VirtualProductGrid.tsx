import { useCallback, useEffect, useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Spin } from 'antd';
import { PatternProductCard } from '@/components/patterns/PatternProductCard';
import type { PatternProductGroup } from '@/utils/patternGroup';

const GRID_GAP = 12;
const MIN_COL_WIDTH = 120;
const ROW_HEIGHT = 148;

interface VirtualProductGridProps {
  groups: PatternProductGroup[];
  t: (k: string) => string;
  hasMore: boolean;
  loadingMore: boolean;
  onScrollEnd: () => void;
  onSelectGroup: (g: PatternProductGroup) => void;
  onQuickAdd?: (g: PatternProductGroup, amount: number) => void;
  quickAddLoading?: boolean;
}

/** Row-virtualized product-group grid for AE ordering "by product" view. */
export function VirtualProductGrid({
  groups,
  t,
  hasMore,
  loadingMore,
  onScrollEnd,
  onSelectGroup,
  onQuickAdd,
  quickAddLoading,
}: VirtualProductGridProps) {
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

  const dataRowCount = Math.ceil(groups.length / columnCount);
  const rowCount = dataRowCount + (hasMore || loadingMore ? 1 : 0);

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
      <div style={{ height: virtualizer.getTotalSize(), width: '100%', position: 'relative' }}>
        {virtualRows.map((vRow) => {
          const rowIndex = vRow.index;
          const isLoaderRow = rowIndex >= dataRowCount;

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
          const rowItems = groups.slice(startIdx, startIdx + columnCount);

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
              {rowItems.map((g) => (
                <PatternProductCard
                  key={g.key}
                  group={g}
                  t={t}
                  onClick={() => onSelectGroup(g)}
                  onQuickAdd={onQuickAdd}
                  quickAddLoading={quickAddLoading}
                />
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
