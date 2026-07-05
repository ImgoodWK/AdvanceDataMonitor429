import { Button } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { useI18n } from '@/i18n';
import { buildCsv, downloadCsv } from '@/utils/csvExport';

interface ExportCsvButtonProps {
  filename: string;
  headers: string[];
  rows: (string | number | null | undefined)[][];
  disabled?: boolean;
}

export function ExportCsvButton({ filename, headers, rows, disabled }: ExportCsvButtonProps) {
  const { t } = useI18n();
  return (
    <Button
      icon={<DownloadOutlined />}
      size="small"
      disabled={disabled || rows.length === 0}
      onClick={() => downloadCsv(filename, buildCsv([headers, ...rows]))}
      aria-label={t('exportCsv')}
    >
      {t('exportCsv')}
    </Button>
  );
}
