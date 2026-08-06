import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Divider,
  Input,
  Modal,
  Space,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import {
  parseUiSettingsBundle,
  type UiSettingsSection,
} from '@/utils/uiSettingsBundle';

const { Text, Title } = Typography;

interface SettingsBackupPanelProps {
  isLoggedIn: boolean;
  tokenType: string | null;
}

export function SettingsBackupPanel({ isLoggedIn, tokenType }: SettingsBackupPanelProps) {
  const { t } = useI18n();
  const { exportUiSettingsBundle, importUiSettingsBundle, restorePackUiDefaults, notify } =
    useAppContext();

  const [exportName, setExportName] = useState('');
  const [exportNote, setExportNote] = useState('');
  const [includePresets, setIncludePresets] = useState(false);
  const [includeServer, setIncludeServer] = useState(false);
  const [includeAlerts, setIncludeAlerts] = useState(false);
  const [canEditAlerts, setCanEditAlerts] = useState(false);
  const [exporting, setExporting] = useState(false);

  const [importMerge, setImportMerge] = useState(false);
  const [importServerAlerts, setImportServerAlerts] = useState(false);
  const [importServerFavorites, setImportServerFavorites] = useState(false);
  const [importServerTemplates, setImportServerTemplates] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewSections, setPreviewSections] = useState<UiSettingsSection[]>([]);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [restoring, setRestoring] = useState(false);

  useEffect(() => {
    if (!isLoggedIn) {
      setCanEditAlerts(false);
      return;
    }
    void getApiClient()
      .get<{ canEditRules?: boolean }>('/api/alerts')
      .then((r) => setCanEditAlerts(!!r.canEditRules))
      .catch(() => setCanEditAlerts(false));
  }, [isLoggedIn]);

  const handleExport = useCallback(async () => {
    setExporting(true);
    try {
      await exportUiSettingsBundle({
        name: exportName.trim() || undefined,
        note: exportNote.trim() || undefined,
        includePresets,
        includeServer: includeServer && isLoggedIn,
        includeAlerts: includeAlerts && canEditAlerts,
      });
    } catch {
      notify(t('uiBundleExportFailed'), 'error');
    } finally {
      setExporting(false);
    }
  }, [
    exportUiSettingsBundle,
    exportName,
    exportNote,
    includePresets,
    includeServer,
    includeAlerts,
    isLoggedIn,
    canEditAlerts,
    notify,
    t,
  ]);

  const handleFileSelect = useCallback(async (file: File) => {
    try {
      const text = await file.text();
      const { sections } = parseUiSettingsBundle(JSON.parse(text));
      setPreviewSections(sections);
      setPendingFile(file);
      setPreviewOpen(true);
      setImportServerAlerts(sections.includes('serverAlerts') && canEditAlerts);
      setImportServerFavorites(sections.includes('serverFavorites') && tokenType !== 'guest');
      setImportServerTemplates(
        sections.includes('serverOrderTemplates') && tokenType !== 'guest'
      );
    } catch {
      notify(t('uiBundleImportFailed'), 'error');
    }
    return false;
  }, [canEditAlerts, notify, t, tokenType]);

  const handleImportConfirm = useCallback(async () => {
    if (!pendingFile) return;
    setImporting(true);
    try {
      const ok = await importUiSettingsBundle(pendingFile, {
        merge: importMerge,
        importServer:
          importServerAlerts || importServerFavorites || importServerTemplates
            ? {
                alerts: importServerAlerts,
                favorites: importServerFavorites,
                orderTemplates: importServerTemplates,
                canEditAlerts,
              }
            : undefined,
      });
      if (ok) {
        setPreviewOpen(false);
        setPendingFile(null);
        Modal.info({
          title: t('uiBundleReloadTitle'),
          content: t('uiBundleReloadHint'),
          okText: t('uiBundleReloadNow'),
          onOk: () => window.location.reload(),
        });
      }
    } finally {
      setImporting(false);
    }
  }, [
    pendingFile,
    importUiSettingsBundle,
    importMerge,
    importServerAlerts,
    importServerFavorites,
    importServerTemplates,
    canEditAlerts,
    t,
  ]);

  const handleRestoreDefaults = useCallback(() => {
    Modal.confirm({
      title: t('uiBundleRestoreDefaultsTitle'),
      content: t('uiBundleRestoreDefaultsHint'),
      okText: t('confirm'),
      cancelText: t('cancel'),
      onOk: async () => {
        setRestoring(true);
        try {
          const ok = await restorePackUiDefaults();
          if (ok) {
            Modal.info({
              title: t('uiBundleReloadTitle'),
              content: t('uiBundleReloadHint'),
              okText: t('uiBundleReloadNow'),
              onOk: () => window.location.reload(),
            });
          }
        } finally {
          setRestoring(false);
        }
      },
    });
  }, [restorePackUiDefaults, t]);

  const sectionLabel = (s: UiSettingsSection) => t('uiBundleSection_' + s);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Alert type="info" message={t('uiBundlePackAuthorHint')} showIcon />

      <div>
        <Title level={5}>{t('uiBundleExportTitle')}</Title>
        <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: '0.85rem' }}>
          {t('uiBundleExportDesc')}
        </Text>
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Input
            placeholder={t('uiBundleExportName')}
            value={exportName}
            onChange={(e) => setExportName(e.target.value)}
          />
          <Input.TextArea
            placeholder={t('uiBundleExportNote')}
            value={exportNote}
            onChange={(e) => setExportNote(e.target.value)}
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
          <Checkbox checked={includePresets} onChange={(e) => setIncludePresets(e.target.checked)}>
            {t('uiBundleIncludePresets')}
          </Checkbox>
          <Checkbox
            checked={includeServer}
            disabled={!isLoggedIn}
            onChange={(e) => setIncludeServer(e.target.checked)}
          >
            {t('uiBundleIncludeServer')}
          </Checkbox>
          {includeServer && isLoggedIn ? (
            <Checkbox
              checked={includeAlerts}
              disabled={!canEditAlerts}
              onChange={(e) => setIncludeAlerts(e.target.checked)}
              style={{ marginLeft: 24 }}
            >
              {t('uiBundleIncludeAlerts')}
              {!canEditAlerts ? (
                <Text type="secondary" style={{ marginLeft: 8, fontSize: '0.75rem' }}>
                  ({t('uiBundleAlertsOpOnly')})
                </Text>
              ) : null}
            </Checkbox>
          ) : null}
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            loading={exporting}
            onClick={() => void handleExport()}
          >
            {t('uiBundleExportButton')}
          </Button>
        </Space>
      </div>

      <Divider />

      <div>
        <Title level={5}>{t('uiBundleImportTitle')}</Title>
        <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: '0.85rem' }}>
          {t('uiBundleImportDesc')}
        </Text>
        <Space wrap>
          <Upload accept=".json" showUploadList={false} beforeUpload={handleFileSelect}>
            <Button icon={<CloudUploadOutlined />}>{t('uiBundleImportButton')}</Button>
          </Upload>
          <Button icon={<UndoOutlined />} loading={restoring} onClick={handleRestoreDefaults}>
            {t('uiBundleRestoreDefaults')}
          </Button>
        </Space>
      </div>

      <Modal
        title={t('uiBundlePreviewTitle')}
        open={previewOpen}
        onCancel={() => {
          setPreviewOpen(false);
          setPendingFile(null);
        }}
        onOk={() => void handleImportConfirm()}
        confirmLoading={importing}
        okText={t('uiBundleImportConfirm')}
        cancelText={t('cancel')}
        width={560}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Text>{t('uiBundlePreviewSections')}</Text>
          <div>
            {previewSections.map((s) => (
              <Tag key={s} style={{ marginBottom: 4 }}>
                {sectionLabel(s)}
              </Tag>
            ))}
          </div>
          <Checkbox checked={importMerge} onChange={(e) => setImportMerge(e.target.checked)}>
            {t('uiBundleImportMerge')}
          </Checkbox>
          {previewSections.some((s) => s.startsWith('server')) ? (
            <>
              <Divider style={{ margin: '8px 0' }} />
              <Text strong>{t('uiBundleImportServerTitle')}</Text>
              {previewSections.includes('serverAlerts') ? (
                <Checkbox
                  checked={importServerAlerts}
                  disabled={!canEditAlerts}
                  onChange={(e) => setImportServerAlerts(e.target.checked)}
                >
                  {t('uiBundleImportServerAlerts')}
                </Checkbox>
              ) : null}
              {previewSections.includes('serverFavorites') ? (
                <Checkbox
                  checked={importServerFavorites}
                  disabled={tokenType === 'guest'}
                  onChange={(e) => setImportServerFavorites(e.target.checked)}
                >
                  {t('uiBundleImportServerFavorites')}
                </Checkbox>
              ) : null}
              {previewSections.includes('serverOrderTemplates') ? (
                <Checkbox
                  checked={importServerTemplates}
                  disabled={tokenType === 'guest'}
                  onChange={(e) => setImportServerTemplates(e.target.checked)}
                >
                  {t('uiBundleImportServerTemplates')}
                </Checkbox>
              ) : null}
            </>
          ) : null}
        </Space>
      </Modal>
    </Space>
  );
}
