import { Drawer, Input, Segmented, Space } from 'antd';
import { QuestListPanel } from '@/components/quest/QuestListPanel';
import { useI18n } from '@/i18n';
import type { QuestLineNodeDto } from '@/types/dto';

interface QuestListDrawerProps {
  open: boolean;
  onClose: () => void;
  nodes: QuestLineNodeDto[];
  selectedQuestId: string | null;
  search: string;
  onSearchChange: (value: string) => void;
  filter: 'all' | 'submit' | 'active';
  onFilterChange: (value: 'all' | 'submit' | 'active') => void;
  onSelect: (questId: string) => void;
}

export function QuestListDrawer({
  open,
  onClose,
  nodes,
  selectedQuestId,
  search,
  onSearchChange,
  filter,
  onFilterChange,
  onSelect,
}: QuestListDrawerProps) {
  const { t } = useI18n();

  const handleSelect = (questId: string) => {
    onSelect(questId);
    onClose();
  };

  return (
    <Drawer
      title={t('quest.openTaskList')}
      open={open}
      onClose={onClose}
      width={360}
      destroyOnClose={false}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Input.Search
          allowClear
          value={search}
          placeholder={t('quest.searchPlaceholder')}
          onChange={(e) => onSearchChange(e.target.value)}
          onSearch={onSearchChange}
        />
        <Segmented
          block
          options={[
            { label: t('quest.filterAll'), value: 'all' },
            { label: t('quest.filterSubmit'), value: 'submit' },
            { label: t('quest.filterActive'), value: 'active' },
          ]}
          value={filter}
          onChange={(v) => onFilterChange(v as typeof filter)}
        />
        <QuestListPanel
          nodes={nodes}
          selectedQuestId={selectedQuestId}
          onSelect={handleSelect}
          width={320}
          embedded
        />
      </Space>
    </Drawer>
  );
}
