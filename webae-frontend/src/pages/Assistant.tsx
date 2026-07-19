import { useCallback, useState } from 'react';
import { Alert, Button, Card, Input, InputNumber, List, Space, Spin, Tag, Typography } from 'antd';
import { CheckOutlined, SendOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { PageShell } from '@/components/Layout/PageShell';
import { useI18n } from '@/i18n';
import type { WebAssistantResponse, WebAssistantTaskResult } from '@/types/dto';
import {
  completePersonalAiWithFailover,
  isChatOnlyAssistantPlan,
  isPersonalAiStoreConfigured,
  loadPersonalAiStore,
  loadPreferredAiSource,
  redactPersonalAiSecret,
  resolveEffectiveAiSource,
  type PersonalAiMessage,
} from '@/utils/personalAi';

const { TextArea } = Input;
const { Paragraph, Text } = Typography;

interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

interface ClientAiContext {
  keyMode: 'browser';
  aiSource?: string;
  intentSystemPrompt: string;
  chatSystemPrompt: string;
  intentUserPrompt?: string;
  chatUserPrompt?: string;
  searchInjected?: boolean;
}

export function AssistantPage() {
  const { lang, notify, serverConfig, actorUuid } = useAppContext();
  const { t } = useI18n();
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<WebAssistantResponse | null>(null);
  const [history, setHistory] = useState<ChatTurn[]>([]);
  const [actionAmount, setActionAmount] = useState<number | null>(null);
  const serverEnabled = serverConfig?.webAiServerKeyEnabled ?? (serverConfig?.webAiKeyMode !== 'browser');
  const browserEnabled = serverConfig?.webAiBrowserKeyEnabled ?? (serverConfig?.webAiKeyMode === 'browser');
  const preferred = loadPreferredAiSource(
    undefined,
    actorUuid || '',
    browserEnabled && !serverEnabled ? 'browser' : 'server'
  );
  const personalStore = loadPersonalAiStore(serverConfig?.webAiProviders || [], undefined, actorUuid || '');
  const effectiveSource = resolveEffectiveAiSource({
    serverEnabled,
    browserEnabled,
    preferred,
    serverConfigured: serverConfig?.webAiShared?.configured,
    browserConfigured: isPersonalAiStoreConfigured(personalStore),
  });
  const browserKeyMode = effectiveSource === 'browser';

  const appendResult = useCallback((prompt: string | null, data: WebAssistantResponse) => {
    setHistory((current) => {
      const next = [...current];
      if (prompt) next.push({ role: 'user', content: prompt });
      if (data.message) next.push({ role: 'assistant', content: data.message });
      return next.slice(-30);
    });
    setResponse(data);
  }, []);

  const submit = useCallback(async () => {
    const prompt = text.trim();
    if (!prompt) return;
    setLoading(true);
    try {
      let clientAiPlan = '';
      let clientAiReply = '';
      let usedApiKey = '';
      if (browserKeyMode) {
        const store = loadPersonalAiStore(serverConfig?.webAiProviders || [], undefined, actorUuid || '');
        if (isPersonalAiStoreConfigured(store)) {
          try {
            const contextResponse = await getApiClient().post<{ success: boolean; context: ClientAiContext }>(
              '/api/assistant/ai-context',
              { locale: lang === 'zh' ? 'zh_CN' : 'en_US', text: prompt }
            );
            const intentUser = contextResponse.context.intentUserPrompt || prompt;
            const planResult = await completePersonalAiWithFailover(store, [
              { role: 'system', content: contextResponse.context.intentSystemPrompt },
              { role: 'user', content: intentUser },
            ]);
            usedApiKey = planResult.apiKey;
            clientAiPlan = redactPersonalAiSecret(planResult.content, usedApiKey);
            if (isChatOnlyAssistantPlan(clientAiPlan)) {
              const chatUser = contextResponse.context.chatUserPrompt || prompt;
              const chatMessages: PersonalAiMessage[] = [
                { role: 'system', content: contextResponse.context.chatSystemPrompt },
                ...history.slice(-12),
                { role: 'user', content: chatUser },
              ];
              const chatResult = await completePersonalAiWithFailover(store, chatMessages);
              usedApiKey = chatResult.apiKey;
              clientAiReply = redactPersonalAiSecret(chatResult.content, usedApiKey);
            }
          } catch (error) {
            notify((error as Error).message || t('aiConnectionFailed'), 'warning');
            clientAiPlan = '';
            clientAiReply = '';
          }
        }
      }
      const data = await getApiClient().post<WebAssistantResponse>('/api/assistant/query', {
        text: prompt,
        locale: lang === 'zh' ? 'zh_CN' : 'en_US',
        history: history.slice(-12),
        aiSource: effectiveSource === 'none' ? undefined : effectiveSource,
        ...(clientAiPlan ? { clientAiPlan } : {}),
        ...(clientAiReply ? { clientAiReply } : {}),
      });
      appendResult(prompt, data);
      setText('');
      setActionAmount(null);
      if (!data.success && data.code === 'rate_limited') {
        notify(t('assistantRateLimited'), 'warning');
      }
    } catch (error) {
      notify((error as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  }, [text, lang, history, appendResult, notify, t, browserKeyMode, effectiveSource, serverConfig?.webAiProviders, actorUuid]);

  const confirmAction = useCallback(async (task: WebAssistantTaskResult, optionNumber: number) => {
    if (!task.actionToken) return;
    setLoading(true);
    try {
      const data = await getApiClient().post<WebAssistantResponse>('/api/assistant/action', {
        actionToken: task.actionToken,
        optionNumber,
        amount: actionAmount || 0,
      });
      setHistory((current) => data.message
        ? [...current, { role: 'assistant' as const, content: data.message }].slice(-30)
        : current);
      setResponse((current) => ({
        ...data,
        tasks: [
          ...(current?.tasks || []).filter((item) => item.actionToken !== task.actionToken),
          ...(data.tasks || []),
        ],
      }));
      setActionAmount(null);
      if (!data.success) notify(data.message || t('assistantActionFailed'), 'error');
    } catch (error) {
      notify((error as Error).message || t('assistantActionFailed'), 'error');
    } finally {
      setLoading(false);
    }
  }, [actionAmount, notify, t]);

  const actionableTasks = response?.tasks?.filter((task) => !!task.actionToken) || [];

  return (
    <PageShell title={t('assistantPage')} description={t('assistantPageDesc')}>
      <Card>
        <Alert
          type="info"
          showIcon
          message={t(browserKeyMode ? 'assistantPersonalAiHint' : 'assistantWebHint')}
          style={{ marginBottom: 16 }}
        />
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {history.length > 0 && (
            <List
              size="small"
              dataSource={history}
              renderItem={(turn) => (
                <List.Item style={{ alignItems: 'flex-start' }}>
                  <Space align="start">
                    <Tag color={turn.role === 'user' ? 'blue' : 'purple'}>
                      {turn.role === 'user' ? t('assistantYou') : t('assistantName')}
                    </Tag>
                    <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{turn.content}</Paragraph>
                  </Space>
                </List.Item>
              )}
            />
          )}

          <TextArea
            rows={3}
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder={t('assistantInputPlaceholder')}
            maxLength={4000}
            showCount
            onPressEnter={(event) => {
              if (!event.shiftKey) {
                event.preventDefault();
                void submit();
              }
            }}
          />
          <Space wrap>
            <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => void submit()}>
              {t('assistantSubmit')}
            </Button>
            {response?.source && (
              <Tag color={response.aiUsed ? 'purple' : 'default'}>
                {response.aiUsed ? t('assistantSourceAi') : t('assistantSourceRules')}
              </Tag>
            )}
            {response?.fallbackReason
              && response.fallbackReason !== 'ai_not_configured'
              && response.fallbackReason !== 'browser_ai_not_configured' && (
              <Text type="secondary">{t('assistantFallback')}: {response.fallbackReason}</Text>
            )}
          </Space>

          <Spin spinning={loading}>
            {actionableTasks.map((actionableTask) => (
              <Card key={actionableTask.actionToken} size="small" title={t('assistantConfirmation')}>
                {actionableTask.candidates && actionableTask.candidates.length > 0 && (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Space wrap>
                      <Text>{t('assistantAmountOverride')}</Text>
                      <InputNumber
                        min={1}
                        value={actionAmount}
                        onChange={(value) => setActionAmount(value)}
                        placeholder={t('assistantAmountDefault')}
                      />
                    </Space>
                    <List
                      dataSource={actionableTask.candidates}
                      renderItem={(candidate) => (
                        <List.Item
                          actions={[
                            <Button
                              key="confirm"
                              type="primary"
                              size="small"
                              icon={<CheckOutlined />}
                              onClick={() => void confirmAction(actionableTask, candidate.optionNumber)}
                            >
                              {t('assistantConfirm')}
                            </Button>,
                          ]}
                        >
                          <List.Item.Meta
                            title={`${candidate.optionNumber}. ${candidate.displayName}`}
                            description={`${candidate.registryName}:${candidate.meta} · ${t('assistantAvailable')} ${candidate.availableAmount}`}
                          />
                        </List.Item>
                      )}
                    />
                  </Space>
                )}

                {actionableTask.teleportDestinations && actionableTask.teleportDestinations.length > 0 && (
                  <List
                    dataSource={actionableTask.teleportDestinations}
                    renderItem={(destination) => (
                      <List.Item
                        actions={[
                          <Button
                            key="teleport"
                            type="primary"
                            size="small"
                            icon={<CheckOutlined />}
                            onClick={() => void confirmAction(actionableTask, destination.optionNumber)}
                          >
                            {t('assistantConfirmTeleport')}
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          title={`${destination.optionNumber}. ${destination.name || destination.dimensionName}`}
                          description={`${destination.dimensionName} · ${destination.x}, ${destination.y}, ${destination.z}`}
                        />
                      </List.Item>
                    )}
                  />
                )}
              </Card>
            ))}

            {response && actionableTasks.length === 0 && response.tasks?.some((task) => task.teleportDestinations?.length) && (
              <Card size="small" title={t('assistantTeleportList')}>
                <List
                  dataSource={response.tasks.flatMap((task) => task.teleportDestinations || [])}
                  renderItem={(destination) => (
                    <List.Item>
                      <Text>{destination.optionNumber}. {destination.name || destination.dimensionName}</Text>
                      <Text type="secondary">{destination.dimensionName} · {destination.x}, {destination.y}, {destination.z}</Text>
                    </List.Item>
                  )}
                />
              </Card>
            )}
          </Spin>
        </Space>
      </Card>
    </PageShell>
  );
}
