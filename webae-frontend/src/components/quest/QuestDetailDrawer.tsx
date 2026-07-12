import { Drawer } from 'antd';

import { QuestDetailPanel, type QuestDetailPanelProps } from '@/components/quest/QuestDetailPanel';
import { useI18n } from '@/i18n';

type QuestDetailDrawerProps = Omit<QuestDetailPanelProps, 'embedded' | 'width'> & {
  open: boolean;
};

export function QuestDetailDrawer({ open, onClose, questId, ...rest }: QuestDetailDrawerProps) {
  const { t } = useI18n();

  return (
    <Drawer
      title={t('quest.detailTitle')}
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
    >
      <QuestDetailPanel questId={questId} embedded onClose={onClose} {...rest} />
    </Drawer>
  );
}
