import { Input, Typography } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';

import { ThemePreviewMini } from '@/components/theme/ThemePreviewMini';
import type { EffectsLevel, ThemeColor } from '@/theme/colors';
import type { ThemeLayout } from '@/theme/layouts';
import type { PageStyle } from '@/theme/pageStyles';

const { Text } = Typography;

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
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((it) => {
      const hay = `${it.label} ${it.id} ${it.searchText || ''}`.toLowerCase();
      return hay.includes(q);
    });
  }, [items, query]);

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
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(168px, 1fr))',
          gap: 10,
          maxHeight,
          overflowY: 'auto',
          paddingBottom: 4,
        }}
      >
        {filtered.map((it) => {
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
      {filtered.length === 0 && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          —
        </Text>
      )}
    </div>
  );
}
