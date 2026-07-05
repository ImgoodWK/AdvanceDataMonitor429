import { useMemo, useState } from 'react';

import { Badge, Button, Input, Space, Tag, Typography } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';

import type { RecipeHandlerInfo } from '@/types/dto';

interface HandlerCategoryFilterProps {
  handlers: RecipeHandlerInfo[];
  browseHandlers: string[];
  onBrowseHandlersChange: (ids: string[]) => void;
  onBrowseAll: () => void;
  t: (k: string) => string;
}

export function HandlerCategoryFilter({
  handlers,
  browseHandlers,
  onBrowseHandlersChange,
  onBrowseAll,
  t,
}: HandlerCategoryFilterProps) {
  const [expanded, setExpanded] = useState(true);
  const [categorySearch, setCategorySearch] = useState('');

  const filteredHandlers = useMemo(() => {
    const q = categorySearch.trim().toLowerCase();
    if (!q) return handlers;
    return handlers.filter(
      (h) =>
        h.handlerName.toLowerCase().includes(q) || h.handlerId.toLowerCase().includes(q)
    );
  }, [handlers, categorySearch]);

  const toggleHandler = (handlerId: string) => {
    if (browseHandlers.includes(handlerId)) {
      onBrowseHandlersChange(browseHandlers.filter((id) => id !== handlerId));
    } else {
      onBrowseHandlersChange([...browseHandlers, handlerId]);
    }
  };

  const selectedCount = browseHandlers.length;
  const allSelected = selectedCount === 0;

  return (
    <div style={{ marginBottom: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          marginBottom: expanded ? 8 : 0,
        }}
      >
        <Space wrap>
          <Typography.Text strong>{t('handlerTypes')}</Typography.Text>
          {!expanded && selectedCount > 0 && (
            <Badge count={selectedCount} aria-label={t('handlerSelectedCount')} />
          )}
          {!expanded && allSelected && (
            <Tag color="processing">{t('allHandlerTypes')}</Tag>
          )}
        </Space>
        <Button
          type="text"
          size="small"
          icon={expanded ? <UpOutlined /> : <DownOutlined />}
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-label={expanded ? t('collapseCategories') : t('expandCategories')}
        >
          {expanded ? t('collapseCategories') : t('expandCategories')}
        </Button>
      </div>

      {expanded && (
        <>
          <Input.Search
            placeholder={t('searchHandlerPlaceholder')}
            value={categorySearch}
            onChange={(e) => setCategorySearch(e.target.value)}
            allowClear
            style={{ marginBottom: 8, maxWidth: 360 }}
            aria-label={t('searchHandlerPlaceholder')}
          />
          <Space wrap>
            <Tag
              color={allSelected ? 'processing' : 'default'}
              style={{ cursor: 'pointer' }}
              onClick={() => {
                onBrowseHandlersChange([]);
                onBrowseAll();
              }}
              role="button"
              tabIndex={0}
              aria-pressed={allSelected}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onBrowseHandlersChange([]);
                  onBrowseAll();
                }
              }}
            >
              {t('allHandlerTypes')}
            </Tag>
            {filteredHandlers.map((h) => {
              const selected = browseHandlers.includes(h.handlerId);
              return (
                <Tag
                  key={h.handlerId}
                  color={selected ? 'processing' : 'default'}
                  style={{ cursor: 'pointer' }}
                  onClick={() => toggleHandler(h.handlerId)}
                  role="button"
                  tabIndex={0}
                  aria-pressed={selected}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      toggleHandler(h.handlerId);
                    }
                  }}
                >
                  {h.handlerName} ({h.recipeCount})
                </Tag>
              );
            })}
            {filteredHandlers.length === 0 && (
              <Typography.Text type="secondary">{t('noHandlerMatch')}</Typography.Text>
            )}
          </Space>
        </>
      )}
    </div>
  );
}