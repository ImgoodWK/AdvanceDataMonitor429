import { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Tabs,
  Select,
  Segmented,
  Switch,
  Button,
  Space,
  Input,
  Upload,
  Tag,
  Modal,
  Popconfirm,
  message,
  Typography,
  Divider,
  Alert,
} from 'antd';
import {
  BgColorsOutlined,
  LayoutOutlined,
  GlobalOutlined,
  PictureOutlined,
  NumberOutlined,
  SaveOutlined,
  DownloadOutlined,
  UploadOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckOutlined,
  KeyOutlined,
  BugOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  BellOutlined,
  CopyOutlined,
  UserAddOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
  CrownOutlined,
  CloseCircleOutlined,
  FolderOpenOutlined,
  RobotOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { importLocalIconPackZip, SERVER_SYNC_PACK_NAME, setActiveLocalPack, syncServerIconPack } from '@/utils/localIconPack';
import {
  canUseDirectoryPicker,
  clearLocalIconDirectory,
  getLocalIconDirMeta,
  isSecureContextForDirectoryPicker,
  localIconDirNeedsPermission,
  LOCAL_ICON_DIR_STATUS_EVENT,
  pickLocalIconDirectory,
  refreshLocalIconDirectoryIndex,
  setLocalIconDirectoryEnabled,
  type LocalIconDirMeta,
} from '@/utils/localIconDirectory';
import { fillMissingIconsFromServer } from '@/utils/iconPrefetch';
import { bumpIconVersion } from '@/utils/icon';
import { getVisibleIconIds } from '@/utils/visibleIconRegistry';
import {
  getLocalDebugFlag,
  setLocalDebugFlag,
  getServerDebugFlags,
  type DebugFeature,
} from '@/utils/debugLog';
import type { ThemeColor } from '@/theme/colors';
import type { ThemeLayout } from '@/theme/layouts';
import type { PageStyle } from '@/theme/pageStyles';
import { formatNumber, type NumberFormat } from '@/utils/format';
import { PageShell } from '@/components/Layout/PageShell';
import type { AppPreset } from '@/utils/presets';
import { AlertsRulesEditor } from '@/components/settings/AlertsRulesEditor';
import { DataFreshnessPanel } from '@/components/settings/DataFreshnessPanel';
import { SettingsBackupPanel } from '@/components/settings/SettingsBackupPanel';
import { AiSettingsPanel } from '@/components/settings/AiSettingsPanel';
import { ThemePreviewMini } from '@/components/theme/ThemePreviewMini';
import { ThemeStudio } from '@/components/theme/ThemeStudio';
import { SettingRow } from '@/components/common/SettingRow';

const { Text, Title } = Typography;

export function SettingsPage() {
  const {
    themeColor,
    setThemeColor,
    themeLayout,
    setThemeLayout,
    pageStyle,
    setPageStyle,
    effectsLevel,
    setEffectsLevel,
    lang,
    setLang,
    displayMode,
    setDisplayMode,
    browsingMode,
    setBrowsingMode,
    numberFormat,
    setNumberFormat,
    iconPack,
    setIconPack,
    iconRenderMode,
    setIconRenderMode,
    iconPacks,
    localIconPack,
    setLocalIconPack,
    localIconPacks,
    refreshLocalIconPacks,
    iconCacheEnabled,
    iconAutoSyncEnabled,
    setIconAutoSyncEnabled,
    refreshIconPacks,
    failedIcons,
    iconWikiEnabled,
    setIconWikiEnabled,
    token,
    setToken,
    authSessionLabel,
    tokenType,
    networks,
    logout,
    presets,
    savePreset,
    applyPreset,
    deletePreset,
    renamePreset,
    overwritePreset,
    exportPreset,
    importPreset,
    notify,
    autoLogin,
    setAutoLogin,
    serverConfig,
    lastUpdateTime,
    triggerRefresh,
    online,
    autoRefresh,
    refreshIntervalMs,
    pauseRefreshWhenHidden,
    setPauseRefreshWhenHidden,
    adminToken,
    isAdmin,
    isOnlineOp,
    adminCapabilities,
    elevateAdmin,
    revokeAdmin,
    checkAdminStatus,
  } = useAppContext();
  const { t } = useI18n();
  const [newToken, setNewToken] = useState('');
  const [guestInviteUrl, setGuestInviteUrl] = useState('');
  const [guestInviteLoading, setGuestInviteLoading] = useState(false);
  const [guestInviteNetworkKeys, setGuestInviteNetworkKeys] = useState<string[]>([]);
  const [renameTarget, setRenameTarget] = useState<AppPreset | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [savePresetOpen, setSavePresetOpen] = useState(false);
  const [presetName, setPresetName] = useState('');
  const [iconSyncLoading, setIconSyncLoading] = useState(false);
  const [iconFillLoading, setIconFillLoading] = useState(false);
  const [localIconDirMeta, setLocalIconDirMetaState] = useState<LocalIconDirMeta | null>(() => getLocalIconDirMeta());
  const [localIconDirBusy, setLocalIconDirBusy] = useState(false);
  const [localIconDirNeedsPerm, setLocalIconDirNeedsPerm] = useState(() => localIconDirNeedsPermission());

  useEffect(() => {
    const onStatus = () => {
      setLocalIconDirMetaState(getLocalIconDirMeta());
      setLocalIconDirNeedsPerm(localIconDirNeedsPermission());
    };
    window.addEventListener(LOCAL_ICON_DIR_STATUS_EVENT, onStatus);
    return () => window.removeEventListener(LOCAL_ICON_DIR_STATUS_EVENT, onStatus);
  }, []);

  // Debug flags: local override snapshot (re-read on each render via getLocalDebugFlag)
  const debugFeatures: DebugFeature[] = ['icons', 'chat', 'dashboard', 'synthesis', 'patterns'];
  const [debugTick, setDebugTick] = useState(0);
  const serverDebugFlags = serverConfig?.debugFlags ?? getServerDebugFlags();

  // Admin elevate form state
  const [elevateCode, setElevateCode] = useState('');
  const [elevateLabel, setElevateLabel] = useState('');
  const [elevateLoading, setElevateLoading] = useState(false);

  const [settingsTab, setSettingsTab] = useState('data-freshness');

  useEffect(() => {
    setLocalIconDirMetaState(getLocalIconDirMeta());
  }, [settingsTab]);

  const [presetFilter, setPresetFilter] = useState('');
  const filteredPresets = useMemo(() => {
    const q = presetFilter.trim().toLowerCase();
    if (!q) return presets;
    return presets.filter((p) => {
      const hay = `${p.name} ${p.id} ${p.settings.themeColor} ${p.settings.pageStyle}`.toLowerCase();
      return hay.includes(q);
    });
  }, [presets, presetFilter]);
  const numberFormatOptions: Array<{ label: string; value: NumberFormat }> = [
    { label: t('numberFormat_full'), value: 'full' },
    { label: t('numberFormat_thousands'), value: 'thousands' },
    { label: t('numberFormat_scientific'), value: 'scientific' },
    { label: t('numberFormat_ae'), value: 'ae' },
    { label: t('numberFormat_engineering'), value: 'engineering' },
    { label: t('numberFormat_short'), value: 'short' },
  ];

  const handleLocalPackImport = async (file: File) => {
    try {
      const meta = await importLocalIconPackZip(file);
      notify(t('localIconPackImported'), 'success');
      await refreshLocalIconPacks();
      setLocalIconPack(meta.name);
    } catch (e) {
      notify((e as Error).message || t('localIconPackImportFailed'), 'error');
    }
    return false;
  };

  const handlePickLocalIconDir = async () => {
    if (!canUseDirectoryPicker()) {
      notify(t('localIconDirUnavailable'), 'warning');
      return;
    }
    setLocalIconDirBusy(true);
    try {
      const meta = await pickLocalIconDirectory();
      setLocalIconDirMetaState(meta);
      notify(
        t('localIconDirPicked').replace('{name}', meta.name).replace('{count}', String(meta.fileCount)),
        'success'
      );
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        notify((e as Error).message || t('localIconDirPickFailed'), 'error');
      }
    } finally {
      setLocalIconDirBusy(false);
    }
  };

  const handleRefreshLocalIconDir = async () => {
    setLocalIconDirBusy(true);
    try {
      const meta = await refreshLocalIconDirectoryIndex();
      setLocalIconDirMetaState(meta);
      if (meta) {
        notify(
          t('localIconDirPicked').replace('{name}', meta.name).replace('{count}', String(meta.fileCount)),
          'success'
        );
      }
    } catch (e) {
      notify((e as Error).message || t('localIconDirPickFailed'), 'error');
    } finally {
      setLocalIconDirBusy(false);
    }
  };

  const handleClearLocalIconDir = async () => {
    setLocalIconDirBusy(true);
    try {
      await clearLocalIconDirectory();
      setLocalIconDirMetaState(null);
    } finally {
      setLocalIconDirBusy(false);
    }
  };

  const handleSyncServerIconPack = async () => {
    if (!token || !iconCacheEnabled) {
      notify(t('iconSyncNeedLogin'), 'warning');
      return;
    }
    setIconSyncLoading(true);
    try {
      const result = await syncServerIconPack({
        pack: iconPack || 'default',
        mode: iconRenderMode || 'nei',
        token,
        force: true,
      });
      setActiveLocalPack(SERVER_SYNC_PACK_NAME);
      setLocalIconPack(SERVER_SYNC_PACK_NAME);
      bumpIconVersion();
      await refreshLocalIconPacks();
      notify(
        t('iconSyncPackDone')
          .replace('{count}', String(result.iconCount))
          .replace('{updated}', result.updated ? t('iconSyncUpdated') : t('iconSyncUnchanged')),
        'success'
      );
    } catch (e) {
      notify((e as Error).message || t('iconSyncPackFailed'), 'error');
    } finally {
      setIconSyncLoading(false);
    }
  };

  const handleFillVisibleMissingIcons = async () => {
    if (!token || !iconCacheEnabled) {
      notify(t('iconSyncNeedLogin'), 'warning');
      return;
    }
    const ids = getVisibleIconIds();
    if (ids.length === 0) {
      notify(t('iconFillVisibleEmpty'), 'info');
      return;
    }
    setIconFillLoading(true);
    try {
      const result = await fillMissingIconsFromServer(ids, {
        iconPack: iconPack || 'default',
        iconRenderMode: iconRenderMode || 'nei',
        token,
        iconCacheEnabled,
        failedIcons,
        localPack: localIconPack || SERVER_SYNC_PACK_NAME,
      });
      await refreshLocalIconPacks();
      notify(
        t('iconFillVisibleDone')
          .replace('{requested}', String(result.requested))
          .replace('{fetched}', String(result.fetched))
          .replace('{missing}', String(result.missing)),
        'success'
      );
    } catch (e) {
      notify((e as Error).message || t('iconFillVisibleFailed'), 'error');
    } finally {
      setIconFillLoading(false);
    }
  };

  const handleSavePreset = () => {
    if (presetName.trim()) {
      savePreset(presetName.trim());
      setPresetName('');
      setSavePresetOpen(false);
    }
  };

  return (
    <PageShell title={t('settings')}>
    <Card>
      <Tabs
        activeKey={settingsTab}
        onChange={setSettingsTab}
        destroyInactiveTabPane
        items={[
          {
            key: 'browsing',
            label: (
              <span>
                <EyeOutlined /> {t('browsingMode')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <SettingRow label={t('browsingMode')} hint={t('browsingModeHint')}>
                  <Switch
                    checked={browsingMode}
                    onChange={setBrowsingMode}
                    aria-label={t('browsingMode')}
                  />
                </SettingRow>
                {browsingMode && <Alert type="success" showIcon message={t('browsingModeActive')} />}
              </Space>
            ),
          },
          {
            key: 'data-freshness',
            label: (
              <span>
                <ClockCircleOutlined /> {t('dataFreshness')}
              </span>
            ),
            children: (
              <DataFreshnessPanel
                online={online}
                lastUpdateTime={lastUpdateTime}
                lang={lang}
                autoRefresh={autoRefresh}
                refreshIntervalMs={refreshIntervalMs}
                pauseRefreshWhenHidden={pauseRefreshWhenHidden}
                setPauseRefreshWhenHidden={setPauseRefreshWhenHidden}
                triggerRefresh={triggerRefresh}
              />
            ),
          },
          {
            key: 'appearance',
            label: (
              <span>
                <BgColorsOutlined /> {t('theme')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <ThemeStudio
                  themeColor={themeColor}
                  setThemeColor={setThemeColor}
                  themeLayout={themeLayout}
                  setThemeLayout={setThemeLayout}
                  pageStyle={pageStyle}
                  setPageStyle={setPageStyle}
                  effectsLevel={effectsLevel}
                  setEffectsLevel={setEffectsLevel}
                  t={t}
                  notify={notify}
                />
                <Divider />
                <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
                  <div>
                    <Text strong>{t('settingsIconWiki')}</Text>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>{t('settingsIconWikiHint')}</div>
                  </div>
                  <Switch checked={iconWikiEnabled} onChange={setIconWikiEnabled} aria-label={t('settingsIconWiki')} />
                </Space>
              </Space>
            ),
          },
          {
            key: 'number',
            label: (
              <span>
                <NumberOutlined /> {t('numberFormat')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Alert type="info" message={t('numberFormat')} showIcon />
                <Select
                  style={{ width: '100%' }}
                  value={numberFormat}
                  onChange={(v) => setNumberFormat(v)}
                  options={numberFormatOptions}
                />
                <div>
                  <Text type="secondary">1234567890 → </Text>
                  <Text strong style={{ color: 'var(--accent)' }}>
                    {formatNumber(1234567890, numberFormat)}
                  </Text>
                </div>
              </Space>
            ),
          },
          {
            key: 'language',
            label: (
              <span>
                <GlobalOutlined /> {t('language')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Select
                  style={{ width: '100%' }}
                  value={lang}
                  onChange={(v) => setLang(v)}
                  options={[
                    { label: '中文 (简体)', value: 'zh' },
                    { label: 'English', value: 'en' },
                  ]}
                />
                <div>
                  <Text strong>{t('displayMode')}</Text>
                  <Segmented
                    block
                    style={{ marginTop: 8 }}
                    options={[
                      { label: t('split'), value: 'split' },
                      { label: t('merged'), value: 'merged' },
                    ]}
                    value={displayMode}
                    onChange={(v) => setDisplayMode(v as 'split' | 'merged')}
                  />
                </div>
              </Space>
            ),
          },
          {
            key: 'icons',
            label: (
              <span>
                <PictureOutlined /> {t('iconPack')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {!iconCacheEnabled && <Alert type="warning" message={t('iconsDisabled')} showIcon />}
                <div>
                  <Text strong>{t('serverIconPack')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                    {t('serverIconPackHint')}
                  </Text>
                  <Select
                    style={{ width: '100%' }}
                    value={iconPack}
                    onChange={setIconPack}
                    disabled={!iconCacheEnabled}
                    options={
                      iconPacks.length > 0
                        ? iconPacks.map((p) => ({ label: `${p.packName} (${p.iconCount})`, value: p.packName }))
                        : [{ label: t('iconPacksNone'), value: 'default' }]
                    }
                  />
                  <Button size="small" style={{ marginTop: 8 }} onClick={() => refreshIconPacks()}>
                    {t('refreshIconPacks')}
                  </Button>
                </div>
                <div>
                  <Text strong>{t('iconRenderMode')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                    {t('iconRenderModeHint')}
                  </Text>
                  <Select
                    style={{ width: '100%' }}
                    value={iconRenderMode || 'nei'}
                    onChange={setIconRenderMode}
                    disabled={!iconCacheEnabled}
                    options={(serverConfig?.iconRenderModes ?? [
                      { id: 'nei', implemented: true },
                    ]).filter((m) => m.implemented !== false).map((m) => ({
                      label: t(`iconRenderMode_${m.id}`),
                      value: m.id,
                      disabled: m.implemented === false,
                    }))}
                  />
                </div>
                <Divider />
                <div>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <div>
                      <Text strong>{t('iconAutoSync')}</Text>
                      <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem' }}>
                        {t('iconAutoSyncHint')}
                      </Text>
                    </div>
                    <Switch checked={iconAutoSyncEnabled} onChange={setIconAutoSyncEnabled} />
                  </Space>
                </div>
                <div>
                  <Text strong>{t('iconManualSync')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                    {t('iconManualSyncHint')}
                  </Text>
                  <Space wrap>
                    <Button
                      icon={<DownloadOutlined />}
                      loading={iconSyncLoading}
                      disabled={!iconCacheEnabled || !token}
                      onClick={() => void handleSyncServerIconPack()}
                    >
                      {t('iconSyncPack')}
                    </Button>
                    <Button
                      icon={<ReloadOutlined />}
                      loading={iconFillLoading}
                      disabled={!iconCacheEnabled || !token}
                      onClick={() => void handleFillVisibleMissingIcons()}
                    >
                      {t('iconFillVisible')}
                    </Button>
                  </Space>
                </div>
                <Divider />
                <div>
                  <Text strong>{t('localIconDir')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                    {t('localIconDirHint')}
                  </Text>
                  {!isSecureContextForDirectoryPicker() && (
                    <Alert type="warning" showIcon style={{ marginBottom: 8 }} message={t('localIconDirSecureHint')} />
                  )}
                  {localIconDirMeta && localIconDirNeedsPerm && (
                    <Alert type="warning" showIcon style={{ marginBottom: 8 }} message={t('localIconDirPermissionHint')} />
                  )}
                  {localIconDirMeta && (
                    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
                      <Text type="secondary">
                        {localIconDirMeta.name} ({localIconDirMeta.fileCount})
                      </Text>
                      <Switch
                        checked={localIconDirMeta.enabled}
                        onChange={(v) => {
                          setLocalIconDirectoryEnabled(v);
                          setLocalIconDirMetaState(getLocalIconDirMeta());
                        }}
                        checkedChildren={t('localIconDirEnable')}
                      />
                    </Space>
                  )}
                  <Space wrap>
                    <Button
                      icon={<FolderOpenOutlined />}
                      loading={localIconDirBusy}
                      disabled={!canUseDirectoryPicker()}
                      onClick={() => void handlePickLocalIconDir()}
                    >
                      {t('localIconDirPick')}
                    </Button>
                    <Button
                      icon={<ReloadOutlined />}
                      loading={localIconDirBusy}
                      disabled={!localIconDirMeta}
                      onClick={() => void handleRefreshLocalIconDir()}
                    >
                      {t('localIconDirRefresh')}
                    </Button>
                    <Button danger loading={localIconDirBusy} disabled={!localIconDirMeta} onClick={() => void handleClearLocalIconDir()}>
                      {t('localIconDirClear')}
                    </Button>
                  </Space>
                </div>
                <Divider />
                <div>
                  <Text strong>{t('localIconPack')}</Text>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                    {t('localIconPackHint')}
                  </Text>
                  <Select
                    style={{ width: '100%', marginBottom: 8 }}
                    value={localIconPack || undefined}
                    onChange={setLocalIconPack}
                    allowClear
                    placeholder={t('localIconPackNone')}
                    options={localIconPacks.map((p) => ({
                      label: `${p.name} (${p.iconCount})`,
                      value: p.name,
                    }))}
                  />
                  <Upload accept=".zip" beforeUpload={handleLocalPackImport} showUploadList={false}>
                    <Button icon={<UploadOutlined />}>{t('localIconPackImport')}</Button>
                  </Upload>
                </div>
                <Alert type="info" message={t('iconUploadHint')} showIcon />
                {serverConfig?.iconLazyCaptureEnabled && (
                  <Alert type="warning" showIcon message={t('iconLazyCaptureHint')} />
                )}
              </Space>
            ),
          },
          {
            key: 'presets',
            label: (
              <span>
                <SaveOutlined /> {t('presetManage')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Space wrap>
                  <Button icon={<SaveOutlined />} onClick={() => setSavePresetOpen(true)}>
                    {t('presetSave')}
                  </Button>
                  <Upload
                    accept=".json"
                    showUploadList={false}
                    beforeUpload={(file) => {
                      importPreset(file);
                      return false;
                    }}
                  >
                    <Button icon={<UploadOutlined />}>{t('presetImport')}</Button>
                  </Upload>
                </Space>
                <Input
                  allowClear
                  size="small"
                  value={presetFilter}
                  onChange={(e) => setPresetFilter(e.target.value)}
                  placeholder={t('themeOptionSearch')}
                  style={{ maxWidth: 360 }}
                />
                {presets.length === 0 ? (
                  <Text type="secondary">{t('presetEmpty')}</Text>
                ) : filteredPresets.length === 0 ? (
                  <Text type="secondary">—</Text>
                ) : (
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                      gap: 12,
                      maxHeight: 560,
                      overflowY: 'auto',
                      paddingBottom: 4,
                    }}
                  >
                    {filteredPresets.map((preset) => (
                      <div
                        key={preset.id}
                        style={{
                          border: '1px solid var(--border)',
                          borderRadius: 10,
                          padding: 10,
                          background: 'var(--bg-card)',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 8,
                        }}
                      >
                        <ThemePreviewMini
                          themeColor={(preset.settings.themeColor || 'dark') as ThemeColor}
                          themeLayout={(preset.settings.themeLayout || 'standard') as ThemeLayout}
                          pageStyle={(preset.settings.pageStyle || 'classic') as PageStyle}
                          effectsLevel={preset.settings.effectsLevel || 'subtle'}
                          title={preset.name}
                        />
                        <Space wrap size={4}>
                          <Text strong style={{ fontSize: 13 }}>{preset.name}</Text>
                          {preset.id.startsWith('builtin') && <Tag color="blue">Built-in</Tag>}
                        </Space>
                        <Text type="secondary" style={{ fontSize: 11, lineHeight: 1.4 }}>
                          {t('themeColor_' + preset.settings.themeColor)} · {t('pageStyle_' + preset.settings.pageStyle)} ·{' '}
                          {t('themeLayout_' + (preset.settings.themeLayout || 'standard'))}
                        </Text>
                        <Space wrap size={4}>
                          <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => applyPreset(preset.id)}>
                            {t('presetApply')}
                          </Button>
                          <Popconfirm
                            title={t('presetConfirmOverwrite').replace('{name}', preset.name)}
                            onConfirm={() => overwritePreset(preset.id)}
                          >
                            <Button size="small" icon={<SaveOutlined />}>{t('presetOverwrite')}</Button>
                          </Popconfirm>
                          <Button
                            size="small"
                            icon={<EditOutlined />}
                            onClick={() => {
                              setRenameTarget(preset);
                              setRenameValue(preset.name);
                            }}
                          >
                            {t('presetRename')}
                          </Button>
                          <Button size="small" icon={<DownloadOutlined />} onClick={() => exportPreset(preset.id)}>
                            {t('presetExport')}
                          </Button>
                          <Popconfirm
                            title={t('presetConfirmDelete').replace('{name}', preset.name)}
                            onConfirm={() => deletePreset(preset.id)}
                          >
                            <Button size="small" danger icon={<DeleteOutlined />}>
                              {t('presetDelete')}
                            </Button>
                          </Popconfirm>
                        </Space>
                      </div>
                    ))}
                  </div>
                )}
              </Space>
            ),
          },
          {
            key: 'backup',
            label: (
              <span>
                <DatabaseOutlined /> {t('settingsBackup')}
              </span>
            ),
            children: (
              <SettingsBackupPanel isLoggedIn={!!token} tokenType={tokenType} />
            ),
          },
          {
            key: 'token',
            label: (
              <span>
                <KeyOutlined /> {t('token')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {authSessionLabel ? (
                  <Alert
                    type="info"
                    showIcon
                    message={t('authSessionLabel')}
                    description={authSessionLabel}
                  />
                ) : null}
                <div>
                  <Text strong>{t('changeToken')}</Text>
                  <Input.Password
                    style={{ marginTop: 8 }}
                    placeholder={t('newTokenPlaceholder')}
                    value={newToken}
                    onChange={(e) => setNewToken(e.target.value)}
                  />
                  <Button
                    style={{ marginTop: 8 }}
                    type="primary"
                    onClick={() => {
                      if (newToken.trim()) {
                        setToken(newToken.trim());
                        setNewToken('');
                        notify(t('tokenChanged'), 'success');
                      }
                    }}
                  >
                    {t('changeToken')}
                  </Button>
                </div>
                <Divider />
                {tokenType !== 'guest' ? (
                  <>
                    <div>
                      <Text strong>{t('inviteGuestTitle')}</Text>
                      <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 8 }}>
                        {t('inviteGuestHint')}
                      </Text>
                      <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 4 }}>
                        {t('inviteGuestSelectNetworks')}
                      </Text>
                      <Select
                        mode="multiple"
                        allowClear
                        style={{ width: '100%', marginBottom: 8 }}
                        placeholder={t('inviteGuestSelectNetworks')}
                        value={guestInviteNetworkKeys}
                        onChange={(v) => setGuestInviteNetworkKeys(v as string[])}
                        options={(networks || []).map((n) => {
                          const key = n.networkKey
                            || (n.monitorDim != null
                              ? `${n.monitorDim}:${n.monitorX}:${n.monitorY}:${n.monitorZ}`
                              : String(n.networkId));
                          return {
                            value: key,
                            label: `#${n.networkId} ${key}${n.healthy === false ? ' (!)' : ''}`,
                          };
                        })}
                      />
                      <Space wrap>
                        <Button
                          type="default"
                          icon={<UserAddOutlined />}
                          loading={guestInviteLoading}
                          onClick={async () => {
                            setGuestInviteLoading(true);
                            try {
                              const body = guestInviteNetworkKeys.length > 0
                                ? { networkKeys: guestInviteNetworkKeys }
                                : {};
                              const data = await getApiClient().post<{ success: boolean; url?: string; message?: string }>(
                                '/api/auth/guest-invite',
                                body
                              );
                              if (data.success && data.url) {
                                setGuestInviteUrl(data.url);
                                notify(t('inviteGuestGenerated'), 'success');
                              } else {
                                notify(data.message || t('inviteGuestFailed'), 'error');
                              }
                            } catch (e) {
                              notify((e as Error).message || t('inviteGuestFailed'), 'error');
                            } finally {
                              setGuestInviteLoading(false);
                            }
                          }}
                        >
                          {t('inviteGuestGenerate')}
                        </Button>
                        {guestInviteUrl ? (
                          <Button
                            icon={<CopyOutlined />}
                            onClick={async () => {
                              try {
                                await navigator.clipboard.writeText(guestInviteUrl);
                                notify(t('inviteGuestCopied'), 'success');
                              } catch {
                                notify(t('inviteGuestCopyFailed'), 'error');
                              }
                            }}
                          >
                            {t('inviteGuestCopy')}
                          </Button>
                        ) : null}
                      </Space>
                      {guestInviteUrl ? (
                        <Input.TextArea
                          style={{ marginTop: 8 }}
                          value={guestInviteUrl}
                          readOnly
                          autoSize={{ minRows: 2, maxRows: 4 }}
                        />
                      ) : null}
                    </div>
                    <Divider />
                  </>
                ) : null}
                <Space>
                  <Switch checked={autoLogin} onChange={setAutoLogin} />
                  <Text>{t('autoLogin')}</Text>
                </Space>
                <Divider />
                {/* Admin elevation section */}
                <div>
                  <Space align="center" style={{ marginBottom: 12 }}>
                    <SafetyCertificateOutlined style={{ fontSize: 18, color: 'var(--accent)' }} />
                    <Text strong>{t('adminSectionTitle')}</Text>
                    {isAdmin && <Tag color="gold">{t('adminActive')}</Tag>}
                    {isOnlineOp && !isAdmin && (
                      <Tag color="blue">{t('adminOnlineOp')}</Tag>
                    )}
                  </Space>
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginBottom: 16 }}>
                    {t('adminSectionHint')}
                  </Text>

                  {!isAdmin ? (
                    <Space direction="vertical" style={{ width: '100%' }} size="small">
                      {tokenType === 'guest' ? (
                        <Alert
                          type="warning"
                          message={t('adminGuestDenied')}
                          showIcon
                        />
                      ) : (
                        <>
                          <Input.Password
                            placeholder={t('adminElevateCodePlaceholder')}
                            value={elevateCode}
                            onChange={(e) => setElevateCode(e.target.value)}
                            disabled={elevateLoading}
                          />
                          <Input
                            placeholder={t('adminElevateLabelPlaceholder')}
                            value={elevateLabel}
                            onChange={(e) => setElevateLabel(e.target.value)}
                            disabled={elevateLoading}
                            maxLength={64}
                          />
                          <Button
                            type="primary"
                            icon={<CrownOutlined />}
                            loading={elevateLoading}
                            disabled={!elevateCode.trim()}
                            onClick={async () => {
                              setElevateLoading(true);
                              try {
                                await elevateAdmin(elevateCode.trim(), elevateLabel.trim() || undefined);
                                setElevateCode('');
                                setElevateLabel('');
                              } finally {
                                setElevateLoading(false);
                              }
                            }}
                          >
                            {t('adminElevateButton')}
                          </Button>
                        </>
                      )}
                    </Space>
                  ) : (
                    <Space direction="vertical" style={{ width: '100%' }} size="small">
                      <Alert
                        type="success"
                        message={t('adminStatusActive')}
                        description={
                          adminCapabilities
                            ? `${t('adminCanForceSnapshot')}: ${adminCapabilities.canForceSnapshot ? '✓' : '✗'} · ${t('adminCanEditRules')}: ${adminCapabilities.canEditRules ? '✓' : '✗'} · ${t('adminCanUploadPacks')}: ${adminCapabilities.canUploadPacks ? '✓' : '✗'}`
                            : undefined
                        }
                        showIcon
                      />
                      <Space>
                        <Button
                          icon={<ReloadOutlined />}
                          size="small"
                          onClick={() => { void checkAdminStatus(); }}
                        >
                          {t('adminRefreshStatus')}
                        </Button>
                        <Popconfirm
                          title={t('adminRevokeConfirm')}
                          onConfirm={() => { void revokeAdmin(); }}
                        >
                          <Button
                            danger
                            size="small"
                            icon={<CloseCircleOutlined />}
                          >
                            {t('adminRevokeButton')}
                          </Button>
                        </Popconfirm>
                      </Space>
                    </Space>
                  )}
                </div>
                <Divider />
                <Button danger onClick={logout}>
                  {t('logout')}
                </Button>
              </Space>
            ),
          },
          {
            key: 'ai',
            label: (
              <span>
                <RobotOutlined /> {t('aiSettingsTitle')}
              </span>
            ),
            children: <AiSettingsPanel isAdmin={isAdmin} notify={notify} />,
          },
          {
            key: 'alerts',
            label: (
              <span>
                <BellOutlined /> {t('alertsSettingsTitle')}
              </span>
            ),
            children: <AlertsRulesEditor notify={notify} />,
          },
          {
            key: 'debug',
            label: (
              <span>
                <BugOutlined /> {t('debugSection')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Alert type="info" message={t('debugHint')} showIcon />
                {debugFeatures.map((feature) => {
                  const local = getLocalDebugFlag(feature);
                  const server = !!serverDebugFlags[feature];
                  return (
                    <div
                      key={feature}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 12px',
                        border: '1px solid var(--border-light)',
                        borderRadius: 6,
                      }}
                    >
                      <div>
                        <Text strong>{t('debugFeature_' + feature)}</Text>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-dim)', marginTop: 2 }}>
                          <span style={{ marginRight: 12 }}>
                            {t('debugLocal')}: {local === null ? '—' : local ? t('debugOn') : t('debugOff')}
                          </span>
                          <span>
                            {t('debugServer')}: {server ? t('debugOn') : t('debugOff')}
                          </span>
                        </div>
                      </div>
                      <Space>
                        <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                          {t('debugLocal')}
                        </Text>
                        <Switch
                          checked={local === true}
                          onChange={(checked) => {
                            setLocalDebugFlag(feature, checked);
                            setDebugTick((n) => n + 1);
                          }}
                          aria-label={t('debugFeature_' + feature)}
                        />
                      </Space>
                    </div>
                  );
                })}
                {/* debugTick forces re-render so the local status text stays in sync */}
                <input type="hidden" value={debugTick} readOnly />
              </Space>
            ),
          },
        ]}
      />

      {/* Save Preset Modal */}
      <Modal
        title={t('presetSave')}
        open={savePresetOpen}
        onOk={handleSavePreset}
        onCancel={() => setSavePresetOpen(false)}
        okText={t('ok')}
        cancelText={t('cancel')}
      >
        <Input
          placeholder={t('presetSavePlaceholder')}
          value={presetName}
          onChange={(e) => setPresetName(e.target.value)}
          onPressEnter={handleSavePreset}
          autoFocus
        />
      </Modal>

      {/* Rename Preset Modal */}
      <Modal
        title={t('presetRename')}
        open={!!renameTarget}
        onOk={() => {
          if (renameTarget && renameValue.trim()) {
            renamePreset(renameTarget.id, renameValue.trim());
          }
          setRenameTarget(null);
        }}
        onCancel={() => setRenameTarget(null)}
        okText={t('ok')}
        cancelText={t('cancel')}
      >
        <Input
          placeholder={t('presetNamePlaceholder')}
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          autoFocus
        />
      </Modal>
    </Card>
    </PageShell>
  );
}
