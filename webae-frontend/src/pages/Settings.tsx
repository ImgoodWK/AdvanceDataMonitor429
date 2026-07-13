import { useEffect, useState } from 'react';
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
  List,
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
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { importLocalIconPackZip, SERVER_SYNC_PACK_NAME, setActiveLocalPack, syncServerIconPack } from '@/utils/localIconPack';
import { fillMissingIconsFromServer } from '@/utils/iconPrefetch';
import { bumpIconVersion } from '@/utils/icon';
import { getVisibleIconIds } from '@/utils/visibleIconRegistry';
import {
  getLocalDebugFlag,
  setLocalDebugFlag,
  getServerDebugFlags,
  type DebugFeature,
} from '@/utils/debugLog';
import { THEME_COLORS } from '@/theme/colors';
import { THEME_LAYOUTS } from '@/theme/layouts';
import { formatNumber, formatTime, formatDuration, type NumberFormat } from '@/utils/format';
import { PageShell } from '@/components/Layout/PageShell';
import type { AppPreset } from '@/utils/presets';
import { AlertsRulesEditor } from '@/components/settings/AlertsRulesEditor';
import { SettingsBackupPanel } from '@/components/settings/SettingsBackupPanel';

const { Text, Title } = Typography;

export function SettingsPage() {
  const {
    themeColor,
    setThemeColor,
    themeLayout,
    setThemeLayout,
    effectsLevel,
    setEffectsLevel,
    lang,
    setLang,
    displayMode,
    setDisplayMode,
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
  } = useAppContext();
  const { t } = useI18n();
  const [newToken, setNewToken] = useState('');
  const [guestInviteUrl, setGuestInviteUrl] = useState('');
  const [guestInviteLoading, setGuestInviteLoading] = useState(false);
  const [renameTarget, setRenameTarget] = useState<AppPreset | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [savePresetOpen, setSavePresetOpen] = useState(false);
  const [presetName, setPresetName] = useState('');
  const [iconSyncLoading, setIconSyncLoading] = useState(false);
  const [iconFillLoading, setIconFillLoading] = useState(false);
  // Debug flags: local override snapshot (re-read on each render via getLocalDebugFlag)
  const debugFeatures: DebugFeature[] = ['icons', 'chat', 'dashboard', 'synthesis', 'patterns'];
  const [debugTick, setDebugTick] = useState(0);
  const serverDebugFlags = serverConfig?.debugFlags ?? getServerDebugFlags();

  // Live "now" tick so the data-freshness indicator updates every second even
  // when auto-refresh is off. Re-render only; no network activity.
  const [nowTick, setNowTick] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNowTick(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const now = nowTick;

  // Freshness: green = <5s, yellow = 5-30s, red = >30s or no data or offline.
  const freshness = (() => {
    if (!online) return { level: 'red' as const, label: t('dataFreshness_offline') };
    if (lastUpdateTime == null)
      return { level: 'red' as const, label: t('dataFreshness_never') };
    const diffMs = now - lastUpdateTime;
    if (diffMs < 5000) return { level: 'green' as const, label: t('dataFreshness_fresh') };
    if (diffMs < 30000) return { level: 'yellow' as const, label: t('dataFreshness_stale') };
    return { level: 'red' as const, label: t('dataFreshness_outdated') };
  })();
  const freshnessColor =
    freshness.level === 'green'
      ? 'var(--success)'
      : freshness.level === 'yellow'
        ? 'var(--warning, #faad14)'
        : 'var(--danger)';
  const lastUpdateDiffText =
    lastUpdateTime == null ? t('dataFreshness_never') : formatDuration(now - lastUpdateTime);

  const colorOptions = THEME_COLORS.map((c) => ({
    label: t('themeColor_' + c),
    value: c,
  }));
  const layoutOptions = THEME_LAYOUTS.map((l) => ({
    label: t('themeLayout_' + l),
    value: l,
  }));
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
        items={[
          {
            key: 'data-freshness',
            label: (
              <span>
                <ClockCircleOutlined /> {t('dataFreshness')}
              </span>
            ),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Alert
                  type="info"
                  message={t('dataFreshnessHint')}
                  showIcon
                />
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '12px 16px',
                    border: '1px solid var(--border-light)',
                    borderRadius: 6,
                    background: 'var(--bg-secondary)',
                  }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 12,
                      height: 12,
                      borderRadius: '50%',
                      background: freshnessColor,
                      flexShrink: 0,
                      boxShadow: `0 0 6px ${freshnessColor}`,
                    }}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Text strong>{freshness.label}</Text>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: 2 }}>
                      <span style={{ marginRight: 12 }}>
                        {t('dataFreshnessLastUpdate')}:{' '}
                        {lastUpdateTime == null ? '--' : formatTime(lastUpdateTime, lang)}
                      </span>
                      <span>
                        {t('dataFreshnessAge')}: {lastUpdateDiffText}
                      </span>
                    </div>
                  </div>
                </div>
                <Space wrap>
                  <Button
                    type="primary"
                    icon={<ReloadOutlined />}
                    onClick={triggerRefresh}
                  >
                    {t('dataFreshnessRefreshNow')}
                  </Button>
                  <Text type="secondary" style={{ fontSize: '0.75rem' }}>
                    {t('dataFreshnessAutoRefresh')}: {autoRefresh ? t('on') : t('off')}
                    {' · '}
                    {t('dataFreshnessInterval')}: {Math.round(refreshIntervalMs / 1000)}s
                  </Text>
                </Space>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 12,
                    padding: '10px 12px',
                    border: '1px solid var(--border-light)',
                    borderRadius: 6,
                  }}
                >
                  <div>
                    <Text strong>{t('pauseRefreshWhenHidden')}</Text>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: 2 }}>
                      {t('pauseRefreshWhenHiddenHint')}
                    </div>
                  </div>
                  <Switch
                    checked={pauseRefreshWhenHidden}
                    onChange={setPauseRefreshWhenHidden}
                    aria-label={t('pauseRefreshWhenHidden')}
                  />
                </div>
                <div
                  style={{
                    padding: '8px 12px',
                    border: '1px solid var(--border-light)',
                    borderRadius: 6,
                    fontSize: '0.75rem',
                    color: 'var(--text-dim)',
                  }}
                  aria-live="polite"
                >
                  {t('dataFreshnessLegend')}
                </div>
              </Space>
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
                <div>
                  <Text strong>{t('themeColor')}</Text>
                  <Select
                    style={{ width: '100%', marginTop: 8 }}
                    value={themeColor}
                    onChange={setThemeColor}
                    options={colorOptions}
                  />
                </div>
                <div>
                  <Text strong>{t('themeLayout')}</Text>
                  <Select
                    style={{ width: '100%', marginTop: 8 }}
                    value={themeLayout}
                    onChange={setThemeLayout}
                    options={layoutOptions}
                  />
                </div>
                <div>
                  <Text strong>{t('effectsLevel')}</Text>
                  <Segmented
                    block
                    style={{ marginTop: 8 }}
                    value={effectsLevel}
                    onChange={(v) => setEffectsLevel(v as 'none' | 'subtle' | 'full')}
                    options={[
                      { label: t('effectsLevel_none'), value: 'none' },
                      { label: t('effectsLevel_subtle'), value: 'subtle' },
                      { label: t('effectsLevel_full'), value: 'full' },
                    ]}
                  />
                  <Text type="secondary" style={{ display: 'block', fontSize: '0.75rem', marginTop: 6 }}>
                    {t('effectsLevel_hint')}
                  </Text>
                </div>
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
                <Space>
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
                {presets.length === 0 ? (
                  <Text type="secondary">{t('presetEmpty')}</Text>
                ) : (
                  <List
                    dataSource={presets}
                    renderItem={(preset) => (
                      <List.Item
                        actions={[
                          <Button key="apply" size="small" type="primary" icon={<CheckOutlined />} onClick={() => applyPreset(preset.id)}>
                            {t('presetApply')}
                          </Button>,
                          <Popconfirm
                            key="overwrite"
                            title={t('presetConfirmOverwrite').replace('{name}', preset.name)}
                            onConfirm={() => overwritePreset(preset.id)}
                          >
                            <Button size="small" icon={<SaveOutlined />}>{t('presetOverwrite')}</Button>
                          </Popconfirm>,
                          <Button
                            key="rename"
                            size="small"
                            icon={<EditOutlined />}
                            onClick={() => {
                              setRenameTarget(preset);
                              setRenameValue(preset.name);
                            }}
                          >
                            {t('presetRename')}
                          </Button>,
                          <Button
                            key="export"
                            size="small"
                            icon={<DownloadOutlined />}
                            onClick={() => exportPreset(preset.id)}
                          >
                            {t('presetExport')}
                          </Button>,
                          <Popconfirm
                            key="delete"
                            title={t('presetConfirmDelete').replace('{name}', preset.name)}
                            onConfirm={() => deletePreset(preset.id)}
                          >
                            <Button size="small" danger icon={<DeleteOutlined />}>
                              {t('presetDelete')}
                            </Button>
                          </Popconfirm>,
                        ]}
                      >
                        <List.Item.Meta
                          title={
                            <Space>
                              <Text strong>{preset.name}</Text>
                              {preset.id.startsWith('builtin') && <Tag color="blue">Built-in</Tag>}
                            </Space>
                          }
                          description={
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                              {t('themeColor')}: {t('themeColor_' + preset.settings.themeColor)} |{' '}
                              {t('iconPack')}: {preset.settings.iconPack} |{' '}
                              {t('numberFormat')}: {t('numberFormat_' + preset.settings.numberFormat)} |{' '}
                              {t('language')}: {preset.settings.lang}
                            </span>
                          }
                        />
                      </List.Item>
                    )}
                  />
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
                      <Space wrap>
                        <Button
                          type="default"
                          icon={<UserAddOutlined />}
                          loading={guestInviteLoading}
                          onClick={async () => {
                            setGuestInviteLoading(true);
                            try {
                              const data = await getApiClient().post<{ success: boolean; url?: string; message?: string }>(
                                '/api/auth/guest-invite',
                                {}
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
                <Button danger onClick={logout}>
                  {t('logout')}
                </Button>
              </Space>
            ),
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
