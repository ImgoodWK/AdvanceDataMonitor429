import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Col,
  Collapse,
  Drawer,
  Input,
  InputNumber,
  List,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SaveOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import type { WebAiProfileDto, WebAiSearchDto, WebAiSettingsDto } from '@/types/dto';
import {
  completePersonalAi,
  defaultPersonalAiProfile,
  isPersonalAiStoreConfigured,
  loadPersonalAiStore,
  loadPreferredAiSource,
  resolveEffectiveAiSource,
  savePersonalAiStore,
  savePreferredAiSource,
  type AiKeySource,
  type PersonalAiProfile,
  type PersonalAiStore,
} from '@/utils/personalAi';

const { Text } = Typography;

interface Props {
  isAdmin: boolean;
  notify: (message: string, type?: 'success' | 'error' | 'warning' | 'info') => void;
}

type DraftProfile = PersonalAiProfile & { apiKeyInput?: string };

const SEARCH_MODES = [
  'auto',
  'tavily_keyless',
  'duckduckgo',
  'tavily',
  'brave',
  'serper',
  'searxng',
];

export function AiSettingsPanel({ isAdmin, notify }: Props) {
  const { t } = useI18n();
  const { serverConfig, actorUuid } = useAppContext();
  const providers = useMemo(() => serverConfig?.webAiProviders || [], [serverConfig?.webAiProviders]);
  const serverEnabled = serverConfig?.webAiServerKeyEnabled
    ?? (serverConfig?.webAiKeyMode !== 'browser');
  const browserEnabled = serverConfig?.webAiBrowserKeyEnabled
    ?? (serverConfig?.webAiKeyMode === 'browser');
  const [preferred, setPreferred] = useState<AiKeySource>('server');
  const [shared, setShared] = useState<WebAiSettingsDto | null>(null);
  const [personal, setPersonal] = useState<PersonalAiStore | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerScope, setDrawerScope] = useState<'server' | 'browser'>('server');
  const [draft, setDraft] = useState<DraftProfile | null>(null);
  const [searchDraft, setSearchDraft] = useState<WebAiSearchDto & { apiKeyInput?: string } | null>(null);
  const [searchKeyInput, setSearchKeyInput] = useState('');
  const host = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
  const secureSecretContext = typeof window === 'undefined'
    || window.isSecureContext
    || host === 'localhost'
    || host === '127.0.0.1'
    || host === '::1';

  const load = useCallback(async () => {
    setPreferred(loadPreferredAiSource(undefined, actorUuid || '', browserEnabled && !serverEnabled ? 'browser' : 'server'));
    const personalStore = loadPersonalAiStore(providers, undefined, actorUuid || '');
    setPersonal(personalStore);
    if (!serverEnabled) {
      setShared(null);
      return;
    }
    if (!isAdmin) {
      const summary = serverConfig?.webAiShared;
      if (summary) {
        setShared({
          enabled: summary.configured,
          configured: summary.configured,
          hasApiKey: summary.enabledCount > 0,
          apiKeyHint: '',
          providerId: summary.profiles[0]?.providerId || '',
          protocol: 'openai-compatible',
          baseUrl: '',
          model: summary.profiles[0]?.model || '',
          timeoutSeconds: 45,
          temperature: 0.1,
          maxTokens: 1200,
          updatedAt: 0,
          updatedBy: '',
          secretStorage: 'server-local-aes-gcm',
          providers,
          profiles: summary.profiles.map((profile) => ({
            ...profile,
            protocol: 'openai-compatible',
            baseUrl: '',
            timeoutSeconds: 45,
            temperature: 0.1,
            maxTokens: 1200,
          })),
          search: summary.search,
        });
      }
      return;
    }
    setLoading(true);
    try {
      const response = await getApiClient().get<{ success: boolean; settings: WebAiSettingsDto }>(
        '/api/admin/ai/settings'
      );
      setShared(response.settings);
      setSearchDraft(response.settings.search || null);
    } catch (error) {
      notify((error as Error).message || t('aiSettingsLoadFailed'), 'error');
    } finally {
      setLoading(false);
    }
  }, [actorUuid, browserEnabled, isAdmin, notify, providers, serverConfig?.webAiShared, serverEnabled, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const effective = resolveEffectiveAiSource({
    serverEnabled,
    browserEnabled,
    preferred,
    serverConfigured: shared?.configured ?? serverConfig?.webAiShared?.configured,
    browserConfigured: personal ? isPersonalAiStoreConfigured(personal) : false,
  });

  const openEdit = (scope: 'server' | 'browser', profile?: WebAiProfileDto | PersonalAiProfile) => {
    setDrawerScope(scope);
    if (scope === 'browser') {
      const source = (profile as PersonalAiProfile) || defaultPersonalAiProfile(providers, personal?.profiles.length || 0);
      setDraft({ ...source, apiKeyInput: '' });
    } else {
      const source = profile as WebAiProfileDto | undefined;
      setDraft({
        id: source?.id || `new-${Date.now()}`,
        name: source?.name || 'New profile',
        enabled: source?.enabled ?? true,
        order: source?.order ?? (shared?.profiles?.length || 0),
        providerId: source?.providerId || providers[0]?.id || 'deepseek',
        protocol: (source?.protocol as PersonalAiProfile['protocol']) || providers[0]?.protocol || 'openai-compatible',
        baseUrl: source?.baseUrl || providers[0]?.defaultBaseUrl || '',
        model: source?.model || providers[0]?.defaultModel || '',
        apiKey: '',
        apiKeyInput: '',
        timeoutSeconds: source?.timeoutSeconds || 45,
        temperature: source?.temperature ?? 0.1,
        maxTokens: source?.maxTokens || 1200,
      });
    }
    setDrawerOpen(true);
  };

  const saveDraft = async () => {
    if (!draft) return;
    setSaving(true);
    try {
      if (drawerScope === 'browser') {
        if (!personal) return;
        const nextProfiles = [...personal.profiles];
        const index = nextProfiles.findIndex((item) => item.id === draft.id);
        const saved: PersonalAiProfile = {
          ...draft,
          apiKey: draft.apiKeyInput?.trim() || draft.apiKey,
        };
        if (!saved.apiKey && saved.enabled) throw new Error(t('aiApiKeyRequired'));
        if (index >= 0) nextProfiles[index] = saved;
        else nextProfiles.push(saved);
        const next = savePersonalAiStore({
          ...personal,
          preferredSource: preferred,
          profiles: nextProfiles.map((item, order) => ({ ...item, order })),
        }, providers, undefined, actorUuid || '');
        setPersonal(next);
        notify(t('aiSettingsSaved'), 'success');
      } else {
        if (!isAdmin || !shared) return;
        const profiles = [...(shared.profiles || [])];
        const index = profiles.findIndex((item) => item.id === draft.id);
        const bodyProfile = {
          id: draft.id.startsWith('new-') ? undefined : draft.id,
          name: draft.name,
          enabled: draft.enabled,
          order: draft.order,
          providerId: draft.providerId,
          baseUrl: draft.baseUrl,
          model: draft.model,
          timeoutSeconds: draft.timeoutSeconds,
          temperature: draft.temperature,
          maxTokens: draft.maxTokens,
          apiKey: draft.apiKeyInput?.trim() || undefined,
        };
        const nextProfiles = profiles.map((item) => ({
          id: item.id,
          name: item.name,
          enabled: item.enabled,
          order: item.order,
          providerId: item.providerId,
          baseUrl: item.baseUrl,
          model: item.model,
          timeoutSeconds: item.timeoutSeconds,
          temperature: item.temperature,
          maxTokens: item.maxTokens,
        }));
        if (index >= 0) nextProfiles[index] = { ...nextProfiles[index], ...bodyProfile, id: draft.id };
        else nextProfiles.push({ ...bodyProfile, id: draft.id });
        const response = await getApiClient().put<{ success: boolean; settings: WebAiSettingsDto }>(
          '/api/admin/ai/settings',
          { profiles: nextProfiles.map((item, order) => ({ ...item, order })) }
        );
        setShared(response.settings);
        setSearchDraft(response.settings.search || null);
        notify(t('aiSettingsSaved'), 'success');
      }
      setDrawerOpen(false);
    } catch (error) {
      notify((error as Error).message || t('aiSettingsSaveFailed'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const saveSearch = async () => {
    if (!isAdmin || !searchDraft) return;
    setSaving(true);
    try {
      const response = await getApiClient().put<{ success: boolean; settings: WebAiSettingsDto }>(
        '/api/admin/ai/settings',
        {
          search: {
            enabled: searchDraft.enabled,
            mode: searchDraft.mode,
            baseUrl: searchDraft.baseUrl,
            maxResults: searchDraft.maxResults,
            fallback: searchDraft.fallback,
            apiKey: searchKeyInput.trim() || undefined,
          },
        }
      );
      setShared(response.settings);
      setSearchDraft(response.settings.search || null);
      setSearchKeyInput('');
      notify(t('aiSettingsSaved'), 'success');
    } catch (error) {
      notify((error as Error).message || t('aiSettingsSaveFailed'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const removeProfile = async (scope: 'server' | 'browser', id: string) => {
    try {
      if (scope === 'browser' && personal) {
        const next = savePersonalAiStore({
          ...personal,
          profiles: personal.profiles.filter((item) => item.id !== id).map((item, order) => ({ ...item, order })),
        }, providers, undefined, actorUuid || '');
        setPersonal(next);
        notify(t('aiSettingsSaved'), 'success');
        return;
      }
      if (!isAdmin || !shared?.profiles) return;
      const response = await getApiClient().put<{ success: boolean; settings: WebAiSettingsDto }>(
        '/api/admin/ai/settings',
        {
          profiles: shared.profiles
            .filter((item) => item.id !== id)
            .map((item, order) => ({
              id: item.id,
              name: item.name,
              enabled: item.enabled,
              order,
              providerId: item.providerId,
              baseUrl: item.baseUrl,
              model: item.model,
              timeoutSeconds: item.timeoutSeconds,
              temperature: item.temperature,
              maxTokens: item.maxTokens,
            })),
        }
      );
      setShared(response.settings);
      notify(t('aiSettingsSaved'), 'success');
    } catch (error) {
      notify((error as Error).message || t('aiSettingsSaveFailed'), 'error');
    }
  };

  const moveProfile = async (scope: 'server' | 'browser', id: string, delta: number) => {
    if (scope === 'browser' && personal) {
      const list = [...personal.profiles].sort((a, b) => a.order - b.order);
      const index = list.findIndex((item) => item.id === id);
      const target = index + delta;
      if (index < 0 || target < 0 || target >= list.length) return;
      const swap = list[index];
      list[index] = list[target];
      list[target] = swap;
      const next = savePersonalAiStore({
        ...personal,
        profiles: list.map((item, order) => ({ ...item, order })),
      }, providers, undefined, actorUuid || '');
      setPersonal(next);
      return;
    }
    if (!isAdmin || !shared?.profiles) return;
    const list = [...shared.profiles].sort((a, b) => a.order - b.order);
    const index = list.findIndex((item) => item.id === id);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= list.length) return;
    const swap = list[index];
    list[index] = list[target];
    list[target] = swap;
    const response = await getApiClient().put<{ success: boolean; settings: WebAiSettingsDto }>(
      '/api/admin/ai/settings',
      {
        profiles: list.map((item, order) => ({
          id: item.id,
          name: item.name,
          enabled: item.enabled,
          order,
          providerId: item.providerId,
          baseUrl: item.baseUrl,
          model: item.model,
          timeoutSeconds: item.timeoutSeconds,
          temperature: item.temperature,
          maxTokens: item.maxTokens,
        })),
      }
    );
    setShared(response.settings);
  };

  const toggleEnabled = async (scope: 'server' | 'browser', id: string, enabled: boolean) => {
    if (scope === 'browser' && personal) {
      const next = savePersonalAiStore({
        ...personal,
        profiles: personal.profiles.map((item) => (item.id === id ? { ...item, enabled } : item)),
      }, providers, undefined, actorUuid || '');
      setPersonal(next);
      return;
    }
    if (!isAdmin || !shared?.profiles) return;
    const response = await getApiClient().put<{ success: boolean; settings: WebAiSettingsDto }>(
      '/api/admin/ai/settings',
      {
        profiles: shared.profiles.map((item, order) => ({
          id: item.id,
          name: item.name,
          enabled: item.id === id ? enabled : item.enabled,
          order,
          providerId: item.providerId,
          baseUrl: item.baseUrl,
          model: item.model,
          timeoutSeconds: item.timeoutSeconds,
          temperature: item.temperature,
          maxTokens: item.maxTokens,
        })),
      }
    );
    setShared(response.settings);
  };

  const testProfile = async () => {
    if (!draft) return;
    setTesting(true);
    try {
      if (drawerScope === 'browser') {
        await completePersonalAi({
          ...draft,
          apiKey: draft.apiKeyInput?.trim() || draft.apiKey,
        }, [{ role: 'user', content: 'Reply with exactly: OK' }]);
      } else {
        await getApiClient().post('/api/admin/ai/test', {
          profileId: draft.id.startsWith('new-') ? undefined : draft.id,
        });
      }
      notify(t('aiSettingsTestOk'), 'success');
    } catch (error) {
      notify((error as Error).message || t('aiSettingsTestFailed'), 'error');
    } finally {
      setTesting(false);
    }
  };

  const onPreferredChange = (value: AiKeySource) => {
    setPreferred(value);
    savePreferredAiSource(value, undefined, actorUuid || '');
    if (personal) {
      setPersonal({ ...personal, preferredSource: value });
    }
  };

  const providerOptions = providers.map((provider) => ({
    value: provider.id,
    label: provider.displayName,
  }));

  const renderProfileList = (
    scope: 'server' | 'browser',
    profiles: Array<WebAiProfileDto | PersonalAiProfile>,
    editable: boolean
  ) => (
    <List
      loading={loading && scope === 'server'}
      dataSource={[...profiles].sort((a, b) => a.order - b.order)}
      locale={{ emptyText: t('aiProfilesEmpty') }}
      header={editable ? (
        <Button type="dashed" icon={<PlusOutlined />} onClick={() => openEdit(scope)}>
          {t('aiProfileAdd')}
        </Button>
      ) : null}
      renderItem={(item) => (
        <List.Item
          actions={editable ? [
            <Button key="up" size="small" onClick={() => void moveProfile(scope, item.id, -1)}>↑</Button>,
            <Button key="down" size="small" onClick={() => void moveProfile(scope, item.id, 1)}>↓</Button>,
            <Button key="edit" size="small" icon={<EditOutlined />} onClick={() => openEdit(scope, item)} />,
            <Popconfirm key="del" title={t('aiProfileDeleteConfirm')} onConfirm={() => void removeProfile(scope, item.id)}>
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>,
          ] : undefined}
        >
          <List.Item.Meta
            title={(
              <Space wrap>
                <Text strong>{item.name}</Text>
                <Tag>{item.providerId}</Tag>
                <Tag>{item.model}</Tag>
                {'configured' in item && item.configured ? <Tag color="green">{t('aiProfileReady')}</Tag> : null}
                {'hasApiKey' in item && item.hasApiKey ? <Tag>{item.apiKeyHint || '••••'}</Tag> : null}
                {'apiKey' in item && item.apiKey ? <Tag>{`••••${item.apiKey.slice(-4)}`}</Tag> : null}
              </Space>
            )}
            description={t('aiProfileFailoverHint')}
          />
          {editable ? (
            <Switch
              checked={item.enabled}
              onChange={(checked) => void toggleEnabled(scope, item.id, checked)}
            />
          ) : (
            <Tag color={item.enabled ? 'blue' : 'default'}>{item.enabled ? t('aiProfileEnabled') : t('aiProfileDisabled')}</Tag>
          )}
        </List.Item>
      )}
    />
  );

  if (!serverEnabled && !browserEnabled) {
    return <Alert type="warning" showIcon message={t('aiBothSourcesDisabled')} />;
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message={t('aiSettingsBoundaryTitle')}
        description={t('aiSettingsBoundaryBody')}
      />
      <Card size="small" title={t('aiSourceStatus')}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space wrap>
            <Tag color={serverEnabled ? 'blue' : 'default'}>
              {t('aiServerSource')}: {serverEnabled ? t('aiSourceOn') : t('aiSourceOff')}
            </Tag>
            <Tag color={browserEnabled ? 'blue' : 'default'}>
              {t('aiBrowserSource')}: {browserEnabled ? t('aiSourceOn') : t('aiSourceOff')}
            </Tag>
            <Tag color="green">{t('aiEffectiveSource')}: {effective === 'none' ? t('aiSourceOff') : effective}</Tag>
          </Space>
          {serverEnabled && browserEnabled ? (
            <Radio.Group
              value={preferred}
              onChange={(event) => onPreferredChange(event.target.value)}
              optionType="button"
              options={[
                { label: t('aiPreferServer'), value: 'server' },
                { label: t('aiPreferBrowser'), value: 'browser' },
              ]}
            />
          ) : null}
        </Space>
      </Card>

      <Tabs
        items={[
          serverEnabled ? {
            key: 'server',
            label: t('aiTabServer'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {!isAdmin ? <Alert type="warning" showIcon message={t('aiSettingsAdminOnly')} /> : null}
                {!secureSecretContext && isAdmin ? (
                  <Alert type="error" showIcon message={t('aiInsecureContext')} />
                ) : null}
                {renderProfileList('server', shared?.profiles || [], isAdmin && secureSecretContext)}
                <Collapse
                  items={[{
                    key: 'search',
                    label: t('aiSearchPanel'),
                    children: (
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <Alert type="info" showIcon message={t('aiSearchSharedHint')} />
                        {isAdmin && searchDraft ? (
                          <>
                            <Row gutter={[12, 12]}>
                              <Col xs={24} md={8}>
                                <Text type="secondary">{t('aiSearchEnabled')}</Text>
                                <div><Switch checked={searchDraft.enabled} onChange={(enabled) => setSearchDraft({ ...searchDraft, enabled })} /></div>
                              </Col>
                              <Col xs={24} md={8}>
                                <Text type="secondary">{t('aiSearchMode')}</Text>
                                <Select
                                  style={{ width: '100%' }}
                                  value={searchDraft.mode}
                                  options={SEARCH_MODES.map((mode) => ({ value: mode, label: mode }))}
                                  onChange={(mode) => setSearchDraft({ ...searchDraft, mode })}
                                />
                              </Col>
                              <Col xs={24} md={8}>
                                <Text type="secondary">{t('aiSearchMaxResults')}</Text>
                                <InputNumber
                                  style={{ width: '100%' }}
                                  min={1}
                                  max={10}
                                  value={searchDraft.maxResults}
                                  onChange={(value) => setSearchDraft({ ...searchDraft, maxResults: Number(value) || 5 })}
                                />
                              </Col>
                              <Col xs={24} md={12}>
                                <Text type="secondary">{t('aiSearchApiKey')}</Text>
                                <Input.Password
                                  value={searchKeyInput}
                                  placeholder={searchDraft.hasApiKey ? searchDraft.apiKeyHint : t('aiSearchApiKeyPlaceholder')}
                                  onChange={(event) => setSearchKeyInput(event.target.value)}
                                  disabled={!secureSecretContext}
                                />
                              </Col>
                              <Col xs={24} md={12}>
                                <Text type="secondary">{t('aiSearchBaseUrl')}</Text>
                                <Input
                                  value={searchDraft.baseUrl}
                                  onChange={(event) => setSearchDraft({ ...searchDraft, baseUrl: event.target.value })}
                                  placeholder="https://searx.example"
                                />
                              </Col>
                              <Col span={24}>
                                <Space>
                                  <Text type="secondary">{t('aiSearchFallback')}</Text>
                                  <Switch
                                    checked={searchDraft.fallback}
                                    onChange={(fallback) => setSearchDraft({ ...searchDraft, fallback })}
                                  />
                                  <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void saveSearch()}>
                                    {t('aiSearchSave')}
                                  </Button>
                                </Space>
                              </Col>
                            </Row>
                          </>
                        ) : (
                          <Text type="secondary">
                            {shared?.search?.enabled
                              ? `${t('aiSearchMode')}: ${shared.search.mode}`
                              : t('aiSearchDisabled')}
                          </Text>
                        )}
                      </Space>
                    ),
                  }]}
                />
              </Space>
            ),
          } : null,
          browserEnabled ? {
            key: 'browser',
            label: t('aiTabBrowser'),
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                <Alert type="info" showIcon message={t('aiBrowserTabHint')} />
                {!secureSecretContext ? <Alert type="error" showIcon message={t('aiInsecureContext')} /> : null}
                {renderProfileList('browser', personal?.profiles || [], secureSecretContext)}
                <Alert type="warning" showIcon message={t('aiBrowserSearchProxyHint')} />
              </Space>
            ),
          } : null,
        ].filter(Boolean) as Array<{ key: string; label: string; children: ReactNode }>}
      />

      <Drawer
        title={t('aiProfileEdit')}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
        extra={(
          <Space>
            <Button icon={<ThunderboltOutlined />} loading={testing} onClick={() => void testProfile()}>
              {t('aiSettingsTest')}
            </Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => void saveDraft()}>
              {t('aiProfileSave')}
            </Button>
          </Space>
        )}
      >
        {draft ? (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div>
              <Text type="secondary">{t('aiProfileName')}</Text>
              <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
            </div>
            <div>
              <Text type="secondary">{t('aiProvider')}</Text>
              <Select
                style={{ width: '100%' }}
                value={draft.providerId}
                options={providerOptions}
                onChange={(providerId) => {
                  const provider = providers.find((item) => item.id === providerId);
                  setDraft({
                    ...draft,
                    providerId,
                    protocol: provider?.protocol || draft.protocol,
                    baseUrl: provider?.defaultBaseUrl || draft.baseUrl,
                    model: provider?.defaultModel || draft.model,
                  });
                }}
              />
            </div>
            <div>
              <Text type="secondary">{t('aiBaseUrl')}</Text>
              <Input value={draft.baseUrl} onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })} />
            </div>
            <div>
              <Text type="secondary">{t('aiModel')}</Text>
              <AutoComplete
                style={{ width: '100%' }}
                value={draft.model}
                options={(providers.find((item) => item.id === draft.providerId)?.models || []).map((model) => ({ value: model }))}
                onChange={(model) => setDraft({ ...draft, model })}
              />
            </div>
            <div>
              <Text type="secondary">{t('aiApiKey')}</Text>
              <Input.Password
                value={draft.apiKeyInput}
                placeholder={draft.apiKey ? `••••${draft.apiKey.slice(-4)}` : t('aiApiKeyPlaceholder')}
                onChange={(event) => setDraft({ ...draft, apiKeyInput: event.target.value })}
                disabled={!secureSecretContext}
              />
            </div>
            <Row gutter={12}>
              <Col span={8}>
                <Text type="secondary">{t('aiTimeout')}</Text>
                <InputNumber
                  style={{ width: '100%' }}
                  min={5}
                  max={120}
                  value={draft.timeoutSeconds}
                  onChange={(value) => setDraft({ ...draft, timeoutSeconds: Number(value) || 45 })}
                />
              </Col>
              <Col span={8}>
                <Text type="secondary">{t('aiTemperature')}</Text>
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  max={2}
                  step={0.1}
                  value={draft.temperature}
                  onChange={(value) => setDraft({ ...draft, temperature: Number(value) || 0 })}
                />
              </Col>
              <Col span={8}>
                <Text type="secondary">{t('aiMaxTokens')}</Text>
                <InputNumber
                  style={{ width: '100%' }}
                  min={64}
                  max={8192}
                  value={draft.maxTokens}
                  onChange={(value) => setDraft({ ...draft, maxTokens: Number(value) || 1200 })}
                />
              </Col>
            </Row>
            <Space>
              <Text type="secondary">{t('aiProfileEnabled')}</Text>
              <Switch checked={draft.enabled} onChange={(enabled) => setDraft({ ...draft, enabled })} />
            </Space>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
