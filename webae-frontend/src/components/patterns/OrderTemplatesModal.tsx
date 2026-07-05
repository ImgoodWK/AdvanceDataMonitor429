import { useEffect, useState } from 'react';
import { Button, Input, List, Modal, Space, Typography, Empty, Popconfirm } from 'antd';
import { DeleteOutlined, EditOutlined, DownloadOutlined } from '@ant-design/icons';

import type { OrderTemplate } from '@/types/dto';

const { Text } = Typography;

export type OrderTemplatesModalMode = 'save' | 'manage';

interface OrderTemplatesModalProps {
  open: boolean;
  mode: OrderTemplatesModalMode;
  templates: OrderTemplate[];
  loading?: boolean;
  saving?: boolean;
  onClose: () => void;
  onSave?: (name: string) => void | Promise<void>;
  onLoad?: (template: OrderTemplate) => void;
  onRename?: (id: string, name: string) => void | Promise<void>;
  onDelete?: (id: string) => void | Promise<void>;
  onFillGaps?: (template: OrderTemplate) => void;
  t: (k: string) => string;
}

export function OrderTemplatesModal({
  open,
  mode,
  templates,
  loading,
  saving,
  onClose,
  onSave,
  onLoad,
  onRename,
  onDelete,
  onFillGaps,
  t,
}: OrderTemplatesModalProps) {
  const [saveName, setSaveName] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');

  useEffect(() => {
    if (open && mode === 'save') {
      setSaveName('');
    }
    if (!open) {
      setEditingId(null);
      setEditName('');
    }
  }, [open, mode]);

  const title = mode === 'save' ? t('orderTemplateSaveTitle') : t('orderTemplateManageTitle');

  return (
    <Modal
      open={open}
      title={title}
      onCancel={onClose}
      footer={null}
      destroyOnClose
      width={520}
    >
      {mode === 'save' ? (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Text type="secondary">{t('orderTemplateSaveHint')}</Text>
          <Input
            placeholder={t('orderTemplateNamePlaceholder')}
            value={saveName}
            onChange={(e) => setSaveName(e.target.value)}
            onPressEnter={() => {
              const n = saveName.trim();
              if (n && onSave) void onSave(n);
            }}
            maxLength={64}
            aria-label={t('orderTemplateNamePlaceholder')}
          />
          <Space>
            <Button onClick={onClose}>{t('cancel')}</Button>
            <Button
              type="primary"
              loading={saving}
              disabled={!saveName.trim()}
              onClick={() => {
                const n = saveName.trim();
                if (n && onSave) void onSave(n);
              }}
            >
              {t('orderTemplateSaveAction')}
            </Button>
          </Space>
        </Space>
      ) : (
        <>
          {templates.length === 0 && !loading ? (
            <Empty description={t('orderTemplateEmpty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List
              loading={loading}
              dataSource={templates}
              renderItem={(tpl) => (
                <List.Item
                  actions={[
                    <Button
                      key="load"
                      type="link"
                      size="small"
                      icon={<DownloadOutlined />}
                      onClick={() => onLoad?.(tpl)}
                    >
                      {t('orderTemplateLoad')}
                    </Button>,
                    onFillGaps ? (
                      <Button key="gaps" type="link" size="small" onClick={() => onFillGaps(tpl)}>
                        {t('orderTemplateFillGaps')}
                      </Button>
                    ) : null,
                    editingId === tpl.id ? (
                      <Button
                        key="confirm-rename"
                        type="link"
                        size="small"
                        onClick={() => {
                          const n = editName.trim();
                          if (n && onRename) void onRename(tpl.id, n);
                          setEditingId(null);
                        }}
                      >
                        {t('orderTemplateRenameConfirm')}
                      </Button>
                    ) : (
                      <Button
                        key="rename"
                        type="link"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setEditingId(tpl.id);
                          setEditName(tpl.name);
                        }}
                      >
                        {t('orderTemplateRename')}
                      </Button>
                    ),
                    <Popconfirm
                      key="delete"
                      title={t('orderTemplateDeleteConfirm')}
                      onConfirm={() => onDelete?.(tpl.id)}
                      okText={t('delete')}
                      cancelText={t('cancel')}
                    >
                      <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                        {t('delete')}
                      </Button>
                    </Popconfirm>,
                  ].filter(Boolean)}
                >
                  {editingId === tpl.id ? (
                    <Input
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      onPressEnter={() => {
                        const n = editName.trim();
                        if (n && onRename) void onRename(tpl.id, n);
                        setEditingId(null);
                      }}
                      maxLength={64}
                      style={{ maxWidth: 280 }}
                    />
                  ) : (
                    <List.Item.Meta
                      title={tpl.name}
                      description={
                        <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                          {t('orderTemplateMeta')
                            .replace('{items}', String(tpl.items.length))
                            .replace('{network}', String(tpl.networkId))
                            .replace('{cpu}', tpl.cpuName?.trim() ? tpl.cpuName : t('orderCpuAuto'))}
                        </Text>
                      }
                    />
                  )}
                </List.Item>
              )}
            />
          )}
          <div style={{ marginTop: 12, textAlign: 'right' }}>
            <Button onClick={onClose}>{t('close')}</Button>
          </div>
        </>
      )}
    </Modal>
  );
}
