import { Button, Empty, Input, Segmented, Space, Spin, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { VirtualPatternGrid } from '@/components/patterns/VirtualPatternGrid';
import { VirtualProductGrid } from '@/components/patterns/VirtualProductGrid';
import { useI18n } from '@/i18n';
import type { PatternListEntryDto } from '@/types/dto';
import type { PatternProductGroup } from '@/utils/patternGroup';
import type { PatternViewMode } from './orderUtils';

const { Text } = Typography;

interface OrderPatternsTabProps {
  patternViewMode: PatternViewMode;
  onPatternViewModeChange: (mode: PatternViewMode) => void;
  patternSearch: string;
  onPatternSearchChange: (v: string) => void;
  loadingPatterns: boolean;
  onRefreshPatterns: () => void;
  onForceRefreshPatterns: () => void;
  browseCached: boolean;
  browseTimestamp: number;
  productGroups: PatternProductGroup[];
  filteredPatterns: PatternListEntryDto[];
  browseTotal: number;
  browseSources: { grid: number; interface: number };
  loadingMorePatterns: boolean;
  onScrollEnd: () => void;
  onSelectProductGroup: (g: PatternProductGroup) => void;
  onQuickAddProduct: (g: PatternProductGroup, qty: number) => void;
  quickAddLoading: boolean;
  onPatternInfo: (p: PatternListEntryDto) => void;
  onOrderPattern: (p: PatternListEntryDto) => void;
}

export function OrderPatternsTab({
  patternViewMode,
  onPatternViewModeChange,
  patternSearch,
  onPatternSearchChange,
  loadingPatterns,
  onRefreshPatterns,
  onForceRefreshPatterns,
  browseCached,
  browseTimestamp,
  productGroups,
  filteredPatterns,
  browseTotal,
  browseSources,
  loadingMorePatterns,
  onScrollEnd,
  onSelectProductGroup,
  onQuickAddProduct,
  quickAddLoading,
  onPatternInfo,
  onOrderPattern,
}: OrderPatternsTabProps) {
  const { t } = useI18n();

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space style={{ width: '100%' }} wrap>
        <Segmented
          value={patternViewMode}
          onChange={(v) => onPatternViewModeChange(v as PatternViewMode)}
          options={[
            { label: t('orderViewByProduct'), value: 'byProduct' },
            { label: t('orderViewByPattern'), value: 'byPattern' },
          ]}
          aria-label={t('orderViewMode')}
        />
        <Input
          placeholder={t('orderPatternSearchPlaceholder')}
          prefix={<SearchOutlined />}
          value={patternSearch}
          onChange={(e) => onPatternSearchChange(e.target.value)}
          allowClear
          style={{ width: 280 }}
        />
        <Button icon={<ReloadOutlined />} size="small" onClick={onRefreshPatterns} loading={loadingPatterns}>
          {t('orderPatternRefresh')}
        </Button>
        <Tooltip title={t('orderPatternForceRefreshHint')}>
          <Button size="small" onClick={() => void onForceRefreshPatterns()}>
            {t('orderPatternForceRefresh')}
          </Button>
        </Tooltip>
        {browseCached ? (
          <Tag color="blue">{t('cached')}</Tag>
        ) : browseTimestamp > 0 ? (
          <Tag color="orange">{t('dataFreshness_stale')}</Tag>
        ) : null}
        <Text type="secondary" style={{ fontSize: '0.75rem' }}>
          {patternViewMode === 'byProduct'
            ? `${productGroups.length} ${t('orderProductUnit')}`
            : `${t('orderPatternLoaded').replace('{loaded}', String(filteredPatterns.length)).replace('{total}', String(browseTotal))} (${t('orderPatternSourceGrid')} ${browseSources.grid} / ${t('orderPatternSourceInterface')} ${browseSources.interface})`}
        </Text>
      </Space>
      {loadingPatterns ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin tip={t('orderPatternLoading')} />
        </div>
      ) : filteredPatterns.length === 0 ? (
        <Empty description={t('orderPatternListEmpty')} />
      ) : patternViewMode === 'byProduct' ? (
        <VirtualProductGrid
          groups={productGroups}
          t={t}
          hasMore={filteredPatterns.length < browseTotal}
          loadingMore={loadingMorePatterns}
          onScrollEnd={onScrollEnd}
          onSelectGroup={onSelectProductGroup}
          onQuickAdd={onQuickAddProduct}
          quickAddLoading={quickAddLoading}
        />
      ) : (
        <VirtualPatternGrid
          patterns={filteredPatterns}
          t={t}
          hasMore={filteredPatterns.length < browseTotal}
          loadingMore={loadingMorePatterns}
          onScrollEnd={onScrollEnd}
          onInfo={onPatternInfo}
          onOrder={onOrderPattern}
        />
      )}
    </Space>
  );
}
