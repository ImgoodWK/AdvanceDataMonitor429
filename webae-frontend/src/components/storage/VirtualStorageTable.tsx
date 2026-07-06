import { useCallback, useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Spin } from 'antd';
import type { ColumnType } from 'antd/es/table';

const ROW_HEIGHT = 48;
const OVERSCAN = 8;

interface VirtualStorageTableProps<T extends object> {
  rows: T[];
  columns: ColumnType<T>[];
  rowKey: (record: T) => string;
  totalEstimate: number;
  loading: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  emptyText?: React.ReactNode;
}

function ColumnHeaderRow<T extends object>({ columns }: { columns: ColumnType<T>[] }) {
  return (
    <div
      className="virtual-storage-header"
      style={{
        display: 'flex',
        gap: 8,
        padding: '8px 8px',
        borderBottom: '1px solid var(--border-subtle, rgba(255,255,255,0.12))',
        fontSize: '0.75rem',
        fontWeight: 600,
        color: 'var(--text-dim)',
      }}
    >
      {columns.map((col) => {
        const flex = col.key === 'amount' ? '0 0 120px' : '1 1 auto';
        return (
          <div
            key={String(col.key ?? col.dataIndex)}
            style={{
              flex,
              textAlign: col.align === 'right' ? 'right' : 'left',
            }}
          >
            {col.title as React.ReactNode}
          </div>
        );
      })}
    </div>
  );
}

/**
 * Row-virtualized table for storage items/fluids/essentia.
 * Renders only visible rows; triggers onLoadMore near scroll end.
 */
export function VirtualStorageTable<T extends object>({
  rows,
  columns,
  rowKey,
  totalEstimate,
  loading,
  loadingMore,
  hasMore,
  onLoadMore,
  emptyText,
}: VirtualStorageTableProps<T>) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: rows.length + (hasMore || loadingMore ? 1 : 0),
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: OVERSCAN,
  });

  const handleScroll = useCallback(() => {
    const el = parentRef.current;
    if (!el || !hasMore || loadingMore) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - ROW_HEIGHT * 3) {
      onLoadMore();
    }
  }, [hasMore, loadingMore, onLoadMore]);

  const virtualRows = virtualizer.getVirtualItems();

  if (!loading && rows.length === 0) {
    return <>{emptyText}</>;
  }

  return (
    <div>
      <ColumnHeaderRow columns={columns} />
      <div
        ref={parentRef}
        className="virtual-storage-table"
        style={{ maxHeight: 520, overflow: 'auto' }}
        onScroll={handleScroll}
        aria-busy={loading || loadingMore}
      >
        <div style={{ position: 'relative', height: virtualizer.getTotalSize(), width: '100%' }}>
          {virtualRows.map((vRow) => {
            const isLoader = vRow.index >= rows.length;
            if (isLoader) {
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

            const record = rows[vRow.index];
            return (
              <div
                key={vRow.key}
                className="virtual-storage-row"
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: vRow.size,
                  transform: `translateY(${vRow.start}px)`,
                  display: 'flex',
                  alignItems: 'center',
                  borderBottom: '1px solid var(--border-subtle, rgba(255,255,255,0.06))',
                  padding: '0 8px',
                  boxSizing: 'border-box',
                }}
              >
                <VirtualStorageRowCells record={record} columns={columns} rowKey={rowKey(record)} />
              </div>
            );
          })}
        </div>
        {loading && rows.length === 0 && (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        )}
      </div>
      {!loading && totalEstimate > 0 && (
        <div
          className="virtual-storage-footer"
          style={{ fontSize: '0.75rem', color: 'var(--text-dim)', padding: '8px 0' }}
        >
          {rows.length} / {totalEstimate}
        </div>
      )}
    </div>
  );
}

function VirtualStorageRowCells<T extends object>({
  record,
  columns,
  rowKey,
}: {
  record: T;
  columns: ColumnType<T>[];
  rowKey: string;
}) {
  return (
    <div style={{ display: 'flex', width: '100%', gap: 8, alignItems: 'center' }} data-row-key={rowKey}>
      {columns.map((col) => {
        const dataIndex = col.dataIndex as keyof T | undefined;
        const raw = dataIndex ? record[dataIndex] : undefined;
        const rendered: React.ReactNode =
          col.render != null
            ? (col.render(raw as never, record, 0) as React.ReactNode)
            : raw != null
              ? String(raw)
              : '';
        const flex = col.key === 'amount' ? '0 0 120px' : '1 1 auto';
        return (
          <div
            key={String(col.key ?? col.dataIndex)}
            style={{
              flex,
              textAlign: col.align === 'right' ? 'right' : 'left',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {rendered}
          </div>
        );
      })}
    </div>
  );
}
