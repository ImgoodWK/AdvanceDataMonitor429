import { Button, Input, InputNumber, Space } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined, FolderOpenOutlined } from '@ant-design/icons';

export interface OrderBatchRow {
  key: string;
  itemName: string;
  amount: number;
  patternId?: string;
}

interface OrderBatchPanelProps {
  batchRows: OrderBatchRow[];
  setBatchRows: (rows: OrderBatchRow[]) => void;
  batchKeySeq: React.MutableRefObject<number>;
  submitting: boolean;
  onPlaceBatch: () => void;
  onSaveTemplate?: () => void;
  onManageTemplates?: () => void;
  t: (k: string) => string;
}

export function OrderBatchPanel({
  batchRows,
  setBatchRows,
  batchKeySeq,
  submitting,
  onPlaceBatch,
  onSaveTemplate,
  onManageTemplates,
  t,
}: OrderBatchPanelProps) {
  return (
    <div style={{ marginTop: 8 }}>
      <Space style={{ marginBottom: 8 }} wrap>
        <strong>{t('batchOrder')}</strong>
        <Button
          icon={<PlusOutlined />}
          size="small"
          onClick={() => {
            batchKeySeq.current += 1;
            setBatchRows([...batchRows, { key: String(batchKeySeq.current), itemName: '', amount: 1 }]);
          }}
        >
          {t('addRow')}
        </Button>
        {onSaveTemplate ? (
          <Button icon={<SaveOutlined />} size="small" onClick={onSaveTemplate}>
            {t('orderTemplateSave')}
          </Button>
        ) : null}
        {onManageTemplates ? (
          <Button icon={<FolderOpenOutlined />} size="small" onClick={onManageTemplates}>
            {t('orderTemplateLoad')}
          </Button>
        ) : null}
        <Button type="primary" size="small" loading={submitting} onClick={onPlaceBatch}>
          {t('placeAll')}
        </Button>
      </Space>
      {batchRows.map((row, idx) => (
        <Space key={row.key} style={{ width: '100%', marginBottom: 6 }} wrap>
          <Input
            placeholder={t('itemNamePlaceholder')}
            value={row.patternId ? `${row.itemName} (${t('orderBatchPatternRow')})` : row.itemName}
            disabled={Boolean(row.patternId)}
            onChange={(e) => {
              const next = [...batchRows];
              next[idx] = { ...row, itemName: e.target.value, patternId: undefined };
              setBatchRows(next);
            }}
            style={{ width: 300 }}
          />
          <InputNumber
            min={1}
            value={row.amount}
            onChange={(v) => {
              const next = [...batchRows];
              next[idx] = { ...row, amount: v || 1 };
              setBatchRows(next);
            }}
          />
          <Button
            icon={<DeleteOutlined />}
            danger
            onClick={() => setBatchRows(batchRows.filter((r) => r.key !== row.key))}
            disabled={batchRows.length === 1}
          />
        </Space>
      ))}
    </div>
  );
}
