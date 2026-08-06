import type { ReactNode } from 'react';
import { Button, Empty, Input, Space, Spin, Typography } from 'antd';
import { DeleteOutlined, DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import { VirtualPatternList } from '@/components/patterns/VirtualPatternList';
import { useI18n } from '@/i18n';
import type { PatternListEntryDto } from '@/types/dto';

const { Text } = Typography;

interface PatternListSidebarProps {
  search: string;
  onSearchChange: (v: string) => void;
  selectedCount: number;
  onSelectAll: () => void;
  onClearSelection: () => void;
  onBatchDelete: () => void;
  onBatchExport: () => void;
  loadingList: boolean;
  patterns: PatternListEntryDto[];
  renderItem: (p: PatternListEntryDto) => ReactNode;
}

export function PatternListSidebar({
  search,
  onSearchChange,
  selectedCount,
  onSelectAll,
  onClearSelection,
  onBatchDelete,
  onBatchExport,
  loadingList,
  patterns,
  renderItem,
}: PatternListSidebarProps) {
  const { t } = useI18n();

  return (
    <>
      <Space style={{ marginBottom: 8, width: '100%' }} direction="vertical" size={8}>
        <Input
          prefix={<SearchOutlined />}
          placeholder={t('patternSearchPlaceholder')}
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          allowClear
        />
        <Space size={4} wrap>
          <Button size="small" onClick={onSelectAll} disabled={patterns.length === 0}>
            {t('patternSelectAll')}
          </Button>
          <Button size="small" onClick={onClearSelection} disabled={selectedCount === 0}>
            {t('patternClearSelection')}
          </Button>
          <Text type="secondary" style={{ fontSize: '0.75rem' }}>
            {t('patternSelected').replace('{n}', String(selectedCount))}
          </Text>
        </Space>
        {selectedCount > 0 && (
          <Space size={4}>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={onBatchDelete}>
              {t('patternBatchDelete')}
            </Button>
            <Button size="small" icon={<DownloadOutlined />} onClick={onBatchExport}>
              {t('patternBatchExport')}
            </Button>
          </Space>
        )}
      </Space>
      <div style={{ maxHeight: 'calc(100vh - 320px)', minHeight: 240, overflow: 'auto', paddingRight: 4 }}>
        {loadingList ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin tip={t('patternLoadingList')} />
          </div>
        ) : patterns.length === 0 ? (
          <Empty description={t('patternListEmpty')} />
        ) : (
          <VirtualPatternList patterns={patterns} renderItem={renderItem} />
        )}
      </div>
    </>
  );
}
