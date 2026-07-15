import { Input, Typography } from 'antd';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import { ThemePreviewMini } from '@/components/theme/ThemePreviewMini';
import type { EffectsLevel, ThemeColor } from '@/theme/colors';
import type { ThemeLayout } from '@/theme/layouts';
import type { PageStyle } from '@/theme/pageStyles';

const { Text } = Typography;

const GRID_GAP = 10;
const MIN_COL_WIDTH = 168;
/** Preview 112 + padding/label ≈ 160 */
const ROW_HEIGHT = 168;

export interface ThemeOptionItem<T extends string = string> {
  id: T;
  label: string;
  themeColor: ThemeColor;
  themeLayout: ThemeLayout;
  pageStyle: PageStyle | string;
  effectsLevel?: EffectsLevel;
  emphasize?: 'color' | 'layout' | 'style' | 'all';
  footer?: ReactNode;
  searchText?: string;
}

interface ThemeOptionGridProps<T extends string> {
  items: ThemeOptionItem<T>[];
  value: T;
  onChange: (id: T) => void;
  searchPlaceholder?: string;
  /** Max height of scrollable grid; omit for no scroll. */
  maxHeight?: number | string;
}

export function ThemeOptionGrid<T extends string>({
  items,
  value,
  onChange,
  searchPlaceholder,
  maxHeight = 420,
}: ThemeOptionGridProps<T>) {
  const [query, setQuery] = useState('');
  const parentRef = useRef<HTMLDivElement>(null);
  const [columnCount, setColumnCount] = useState(3);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((it) => {
      const hay = `${it.label} ${it.id} ${it.searchText || ''}`.toLowerCase();
      return hay.includes(q);
    });
  }, [items, query]);

  useEffect(() => {
    const el = parentRef.current;
    if (!el) return;
    const updateCols = () => {
      const w = el.clientWidth;
      const cols = Math.max(1, Math.floor((w + GRID_GAP) / (MIN_COL_WIDTH + GRID_GAP)));
      setColumnCount(cols);
    };
    updateCols();
    const ro = new ResizeObserver(updateCols);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const rowCount = Math.max(1, Math.ceil(filtered.length / columnCount));

  const virtualizer = useVirtualizer({
    count: filtered.length === 0 ? 0 : rowCount,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT + GRID_GAP,
    overscan: 1,
  });

  const virtualRows = virtualizer.getVirtualItems();

  return (
    <div style={{ width: '100%' }}>
      <Input
        allowClear
        size="small"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={searchPlaceholder}
        style={{ marginBottom: 10 }}
      />
      <div
        ref={parentRef}
        style={{
          maxHeight,
          overflowY: 'auto',
          paddingBottom: 4,
        }}
      >
        {filtered.length === 0 ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Text>
        ) : (
          <div
            style={{
              height: virtualizer.getTotalSize(),
              width: '100%',
              position: 'relative',
            }}
          >
            {virtualRows.map((vRow) => {
              const start = vRow.index * columnCount;
              const rowItems = filtered.slice(start, start + columnCount);
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
                    boxSizing: 'border-box',
                    paddingBottom: GRID_GAP,
                  }}
                >
                  {rowItems.map((it) => {
                    const selected = it.id === value;
                    return (
                      <button
                        key={it.id}
                        type="button"
                        onClick={() => onChange(it.id)}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 6,
                          padding: 8,
                          margin: 0,
                          textAlign: 'left',
                          cursor: 'pointer',
                          borderRadius: 10,
                          border: selected ? '2px solid var(--accent)' : '1px solid var(--border)',
                          background: selected ? 'var(--bg-hover)' : 'var(--bg-card)',
                          boxShadow: selected ? '0 0 0 1px var(--accent-glow)' : 'none',
                          color: 'inherit',
                          font: 'inherit',
                          minWidth: 0,
                        }}
                      >
                        <ThemePreviewMini
                          themeColor={it.themeColor}
                          themeLayout={it.themeLayout}
                          pageStyle={it.pageStyle}
                          effectsLevel={it.effectsLevel || 'subtle'}
                          emphasize={it.emphasize || 'all'}
                          title={it.label}
                        />
                        <Text
                          strong
                          style={{
                            fontSize: 12,
                            lineHeight: 1.3,
                            color: selected ? 'var(--accent)' : 'var(--text-primary)',
                          }}
                        >
                          {it.label}
                        </Text>
                        {it.footer}
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
