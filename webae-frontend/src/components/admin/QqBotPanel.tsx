import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Divider,
  Input,
  InputNumber,
  message,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApiOutlined,
  ClearOutlined,
  CloudSyncOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  ScheduleOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type {
  QqBotAuditEntryDto,
  QqBotAuditResponse,
  QqBotSettingsDto,
  QqBotSettingsResponse,
  QqBotStatusDto,
  QqBotStatusResponse,
} from '@/types/dto';
import { joinQqBotList, qqBotConnectionColor, splitQqBotList } from '@/utils/qqBot';

const { Text, Paragraph } = Typography;

const DEFAULT_WEBAE_PREFIXES = ['webae', '游戏', 'mc', 'gtnh', '服务器'];
const DEFAULT_ASTRBOT_PREFIXES = ['tt'];
const DEFAULT_WEBAE_KEYWORDS = [
  'webae', 'textech', 'gtnh', 'tps', 'mspt', '仪表盘', '告警', '在线玩家', '服务器状态',
  'adm', '高级数据', '监视器', '内存', '开服', '谁在线',
];

function withIntentDefaults(settings: QqBotSettingsDto): QqBotSettingsDto {
  return {
    ...settings,
    astrBotCompatEnabled: !!settings.astrBotCompatEnabled,
    webaeExplicitPrefixes: settings.webaeExplicitPrefixes?.length
      ? settings.webaeExplicitPrefixes
      : [...DEFAULT_WEBAE_PREFIXES],
    astrBotExplicitPrefixes: settings.astrBotExplicitPrefixes?.length
      ? settings.astrBotExplicitPrefixes
      : [...DEFAULT_ASTRBOT_PREFIXES],
    webaeIntentKeywords: settings.webaeIntentKeywords?.length
      ? settings.webaeIntentKeywords
      : [...DEFAULT_WEBAE_KEYWORDS],
  };
}

interface QqBotPanelProps {
  active: boolean;
}

function time(value: number): string {
  return value > 0 ? new Date(value).toLocaleString() : '—';
}

function shortId(value: string): string {
  if (!value) return '—';
  return value.length <= 20 ? value : `${value.slice(0, 8)}…${value.slice(-6)}`;
}

export function QqBotPanel({ active }: QqBotPanelProps) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<QqBotSettingsDto | null>(null);
  const [status, setStatus] = useState<QqBotStatusDto | null>(null);
  const [audit, setAudit] = useState<QqBotAuditEntryDto[]>([]);
  const [secretInput, setSecretInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [action, setAction] = useState('');
  const [sendType, setSendType] = useState('group');
  const [sendTarget, setSendTarget] = useState('');
  const [sendContent, setSendContent] = useState('');

  const load = useCallback(async (withSettings = true) => {
    if (withSettings) setLoading(true);
    try {
      if (withSettings) {
        const response = await getApiClient().get<QqBotSettingsResponse>('/api/admin/qq-bot/settings');
        setDraft(withIntentDefaults(response.settings));
        setStatus(response.status);
      } else {
        const response = await getApiClient().get<QqBotStatusResponse>('/api/admin/qq-bot/status');
        setStatus(response.status);
      }
      const auditResponse = await getApiClient().get<QqBotAuditResponse>('/api/admin/qq-bot/audit');
      setAudit(auditResponse.audit || []);
    } catch (error: any) {
      if (withSettings) message.error(error?.message || t('qqBotLoadFailed'));
    } finally {
      if (withSettings) setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!active) return;
    load(true);
    const timer = window.setInterval(() => load(false), 5000);
    return () => window.clearInterval(timer);
  }, [active, load]);

  const update = useCallback(<K extends keyof QqBotSettingsDto>(key: K, value: QqBotSettingsDto[K]) => {
    setDraft((current) => current ? { ...current, [key]: value } : current);
  }, []);

  const save = useCallback(async () => {
    if (!draft) return;
    setSaving(true);
    try {
      const payload: QqBotSettingsDto = { ...draft };
      if (secretInput.trim()) payload.appSecret = secretInput.trim();
      else delete payload.appSecret;
      const response = await getApiClient().put<QqBotSettingsResponse>('/api/admin/qq-bot/settings', payload);
      setDraft(withIntentDefaults(response.settings));
      setStatus(response.status);
      setSecretInput('');
      message.success(t('qqBotSaved'));
    } catch (error: any) {
      message.error(error?.message || t('qqBotSaveFailed'));
    } finally {
      setSaving(false);
    }
  }, [draft, secretInput, t]);

  const runAction = useCallback(async (name: string, endpoint: string) => {
    setAction(name);
    try {
      const response = await getApiClient().post<QqBotStatusResponse>(endpoint);
      setStatus(response.status);
      message.success(t(`${name}Ok`));
      await load(false);
    } catch (error: any) {
      message.error(error?.message || t(`${name}Failed`));
    } finally {
      setAction('');
    }
  }, [load, t]);

  const clearSecret = useCallback(async () => {
    setAction('qqBotClearSecret');
    try {
      const response = await getApiClient().delete<QqBotSettingsResponse>('/api/admin/qq-bot/secret');
      setDraft(withIntentDefaults(response.settings));
      setStatus(response.status);
      setSecretInput('');
      message.success(t('qqBotClearSecretOk'));
    } catch (error: any) {
      message.error(error?.message || t('qqBotClearSecretFailed'));
    } finally {
      setAction('');
    }
  }, [t]);

  const send = useCallback(async () => {
    if (!sendTarget.trim() || !sendContent.trim()) {
      message.warning(t('qqBotSendRequired'));
      return;
    }
    setAction('qqBotSend');
    try {
      const response = await getApiClient().post<QqBotStatusResponse>('/api/admin/qq-bot/send', {
        targetType: sendType,
        targetId: sendTarget.trim(),
        content: sendContent.trim(),
      });
      setStatus(response.status);
      setSendContent('');
      message.success(t('qqBotSendOk'));
      await load(false);
    } catch (error: any) {
      message.error(error?.message || t('qqBotSendFailed'));
    } finally {
      setAction('');
    }
  }, [load, sendContent, sendTarget, sendType, t]);

  const connectionTag = useMemo(() => {
    if (!status) return <Tag>{t('loading')}</Tag>;
    return (
      <Tag color={qqBotConnectionColor(status.connected, status.phase)}>
        {status.connected ? t('qqBotConnected') : t(`qqBotPhase_${status.phase}`) || status.phase}
      </Tag>
    );
  }, [status, t]);

  if (loading && !draft) return <Card loading />;
  if (!draft) return <Alert type="error" showIcon message={t('qqBotLoadFailed')} />;

  const capabilityRows: Array<[keyof QqBotSettingsDto, string, string]> = [
    ['statusCommandEnabled', 'qqBotCapabilityStatus', 'qqBotCapabilityStatusDesc'],
    ['playersCommandEnabled', 'qqBotCapabilityPlayers', 'qqBotCapabilityPlayersDesc'],
    ['playerListCommandEnabled', 'qqBotCapabilityPlayerList', 'qqBotCapabilityPlayerListDesc'],
    ['tpsCommandEnabled', 'qqBotCapabilityTps', 'qqBotCapabilityTpsDesc'],
    ['memoryCommandEnabled', 'qqBotCapabilityMemory', 'qqBotCapabilityMemoryDesc'],
    ['uptimeCommandEnabled', 'qqBotCapabilityUptime', 'qqBotCapabilityUptimeDesc'],
    ['aboutCommandEnabled', 'qqBotCapabilityAbout', 'qqBotCapabilityAboutDesc'],
  ];

  const statusCard = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={<Space><RobotOutlined />{t('qqBotStatusTitle')}</Space>}
        extra={<Space>{connectionTag}<Button size="small" icon={<ReloadOutlined />} onClick={() => load(false)}>{t('refresh')}</Button></Space>}
      >
        <Row gutter={[12, 12]}>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotReceived')} value={status?.received || 0} /></Col>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotReplied')} value={status?.replied || 0} /></Col>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotAiReplies')} value={status?.aiReplies || 0} /></Col>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotFailed')} value={status?.failed || 0} /></Col>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotQueue')} value={status?.queueDepth || 0} suffix={`/ ${status?.queueCapacity || 0}`} /></Col>
          <Col xs={12} sm={8} lg={4}><Statistic title={t('qqBotSessions')} value={status?.conversationCount || 0} /></Col>
        </Row>
        <Divider />
        <Descriptions size="small" column={{ xs: 1, md: 2, xl: 3 }}>
          <Descriptions.Item label={t('qqBotPhase')}>{status?.phase || '—'}</Descriptions.Item>
          <Descriptions.Item label={t('qqBotLastConnected')}>{time(status?.lastConnectedAtMs || 0)}</Descriptions.Item>
          <Descriptions.Item label={t('qqBotLastMessage')}>{time(status?.lastMessageAtMs || 0)}</Descriptions.Item>
          <Descriptions.Item label={t('qqBotLastReply')}>{time(status?.lastReplyAtMs || 0)}</Descriptions.Item>
          <Descriptions.Item label={t('qqBotNextReconnect')}>{time(status?.nextReconnectAtMs || 0)}</Descriptions.Item>
          <Descriptions.Item label={t('qqBotNextReport')}>{time(status?.nextScheduledReportAtMs || 0)}</Descriptions.Item>
        </Descriptions>
        {status?.snapshot && (
          <Alert
            style={{ marginTop: 16 }}
            type={status.snapshot.tps >= 18 ? 'success' : status.snapshot.tps >= 15 ? 'warning' : 'error'}
            showIcon
            message={`${status.snapshot.motd || t('qqBotServerSnapshot')} · TPS ${status.snapshot.tps.toFixed(1)} · MSPT ${status.snapshot.mspt.toFixed(1)} · ${status.snapshot.onlinePlayers}/${status.snapshot.maxPlayers}`}
          />
        )}
        {!!status?.lastError && <Alert style={{ marginTop: 12 }} type="error" showIcon message={t('qqBotLastError')} description={status.lastError} />}
      </Card>

      <Space wrap>
        <Button icon={<CloudSyncOutlined />} loading={action === 'qqBotRestart'} onClick={() => runAction('qqBotRestart', '/api/admin/qq-bot/restart')}>
          {t('qqBotRestart')}
        </Button>
        <Popconfirm title={t('qqBotClearSessionsConfirm')} onConfirm={() => runAction('qqBotClearSessions', '/api/admin/qq-bot/conversations/clear')}>
          <Button icon={<ClearOutlined />} loading={action === 'qqBotClearSessions'}>{t('qqBotClearSessions')}</Button>
        </Popconfirm>
      </Space>
    </Space>
  );

  const connectionSettings = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Alert type="info" showIcon message={t('qqBotOfficialNote')} description={t('qqBotOfficialNoteDesc')} />
      <Card title={<Space><ApiOutlined />{t('qqBotCredentials')}</Space>}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Text strong>{t('qqBotEnabled')}</Text><br />
            <Switch checked={draft.enabled} onChange={(value) => update('enabled', value)} />
          </Col>
          <Col xs={24} md={8}>
            <Text strong>{t('qqBotAppId')}</Text>
            <Input value={draft.appId} onChange={(event) => update('appId', event.target.value)} placeholder={t('qqBotAppIdPlaceholder')} />
          </Col>
          <Col xs={24} md={8}>
            <Text strong>{t('qqBotClientSecret')}</Text>
            <Input.Password value={secretInput} onChange={(event) => setSecretInput(event.target.value)} placeholder={draft.appSecretConfigured ? draft.appSecretHint || t('qqBotSecretConfigured') : t('qqBotSecretPlaceholder')} />
          </Col>
          <Col xs={24} md={8}>
            <Text strong>{t('qqBotName')}</Text>
            <Input value={draft.botName} onChange={(event) => update('botName', event.target.value)} />
          </Col>
          <Col xs={24} md={8}>
            <Text strong>{t('qqBotPrefix')}</Text>
            <Input value={draft.commandPrefix} onChange={(event) => update('commandPrefix', event.target.value)} placeholder="/" />
          </Col>
          <Col xs={24} md={8}>
            <Space style={{ marginTop: 22 }} wrap>
              <Checkbox checked={draft.requireMention} onChange={(event) => update('requireMention', event.target.checked)}>{t('qqBotRequireMention')}</Checkbox>
              <Checkbox checked={draft.replyUnknownWithHelp} onChange={(event) => update('replyUnknownWithHelp', event.target.checked)}>{t('qqBotUnknownHelp')}</Checkbox>
            </Space>
          </Col>
        </Row>
        <Divider orientation="left">{t('qqBotAdvancedEndpoints')}</Divider>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={12}><Text>{t('qqBotApiBase')}</Text><Input value={draft.apiBase} onChange={(event) => update('apiBase', event.target.value)} placeholder="https://api.sgroup.qq.com" /></Col>
          <Col xs={24} md={12}><Text>{t('qqBotTokenUrl')}</Text><Input value={draft.tokenUrl} onChange={(event) => update('tokenUrl', event.target.value)} placeholder="https://bots.qq.com/app/getAppAccessToken" /></Col>
        </Row>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>{t('qqBotEndpointSecurity')}</Paragraph>
        <Space style={{ marginTop: 16 }} wrap>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>{t('save')}</Button>
          <Popconfirm title={t('qqBotClearSecretConfirm')} onConfirm={clearSecret}>
            <Button danger disabled={!draft.appSecretConfigured} loading={action === 'qqBotClearSecret'}>{t('qqBotClearSecret')}</Button>
          </Popconfirm>
        </Space>
      </Card>
    </Space>
  );

  const capabilities = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title={t('qqBotCapabilities')}>
        <Row gutter={[16, 16]}>
          {capabilityRows.map(([key, label, desc]) => (
            <Col xs={24} md={12} xl={8} key={String(key)}>
              <Card size="small">
                <Space align="start">
                  <Switch checked={Boolean(draft[key])} onChange={(value) => update(key, value as never)} />
                  <span><Text strong>{t(label)}</Text><br /><Text type="secondary">{t(desc)}</Text></span>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      </Card>
      <Card title={<Space><RobotOutlined />{t('qqBotAiTitle')}</Space>}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space wrap>
            <Checkbox checked={draft.aiEnabled} onChange={(event) => update('aiEnabled', event.target.checked)}>{t('qqBotAiEnabled')}</Checkbox>
            <Checkbox checked={draft.aiAutoReply} disabled={!draft.aiEnabled} onChange={(event) => update('aiAutoReply', event.target.checked)}>{t('qqBotAiAutoReply')}</Checkbox>
            <Checkbox checked={draft.aiWebSearch} disabled={!draft.aiEnabled} onChange={(event) => update('aiWebSearch', event.target.checked)}>{t('qqBotAiWebSearch')}</Checkbox>
          </Space>
          <Alert type="warning" showIcon message={t('qqBotAiBoundary')} description={t('qqBotAiBoundaryDesc')} />
          <Text strong>{t('qqBotAiPrompt')}</Text>
          <Input.TextArea rows={4} value={draft.aiSystemPrompt} onChange={(event) => update('aiSystemPrompt', event.target.value)} placeholder={t('qqBotAiPromptPlaceholder')} />
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}><Text>{t('qqBotConversationTurns')}</Text><InputNumber min={1} max={20} value={draft.maxConversationTurns} onChange={(value) => update('maxConversationTurns', value || 1)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotConversationTtl')}</Text><InputNumber min={5} max={1440} value={draft.conversationTtlMinutes} onChange={(value) => update('conversationTtlMinutes', value || 5)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotUserCooldown')}</Text><InputNumber min={0} max={60} value={draft.userCooldownSeconds} onChange={(value) => update('userCooldownSeconds', value || 0)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotAiCooldown')}</Text><InputNumber min={1} max={300} value={draft.aiCooldownSeconds} onChange={(value) => update('aiCooldownSeconds', value || 1)} style={{ width: '100%' }} /></Col>
          </Row>
        </Space>
      </Card>
      <Card title={<Space><RobotOutlined />{t('qqBotIntentTitle')}</Space>}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space align="center" wrap>
            <Text strong>{t('qqBotIntentEnabled')}</Text>
            <Switch
              checked={!!draft.astrBotCompatEnabled}
              onChange={(checked) => update('astrBotCompatEnabled', checked)}
            />
          </Space>
          <Alert type="info" showIcon message={t('qqBotIntentNote')} description={t('qqBotIntentNoteDesc')} />
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={8}>
              <Text strong>{t('qqBotIntentWebaePrefixes')}</Text>
              <Input.TextArea
                rows={6}
                disabled={!draft.astrBotCompatEnabled}
                value={joinQqBotList(draft.webaeExplicitPrefixes || [])}
                onChange={(event) => update('webaeExplicitPrefixes', splitQqBotList(event.target.value))}
                placeholder={t('qqBotIntentWebaePrefixesPlaceholder')}
              />
            </Col>
            <Col xs={24} lg={8}>
              <Text strong>{t('qqBotIntentAstrPrefixes')}</Text>
              <Input.TextArea
                rows={6}
                disabled={!draft.astrBotCompatEnabled}
                value={joinQqBotList(draft.astrBotExplicitPrefixes || [])}
                onChange={(event) => update('astrBotExplicitPrefixes', splitQqBotList(event.target.value))}
                placeholder={t('qqBotIntentAstrPrefixesPlaceholder')}
              />
            </Col>
            <Col xs={24} lg={8}>
              <Text strong>{t('qqBotIntentKeywords')}</Text>
              <Input.TextArea
                rows={6}
                disabled={!draft.astrBotCompatEnabled}
                value={joinQqBotList(draft.webaeIntentKeywords || [])}
                onChange={(event) => update('webaeIntentKeywords', splitQqBotList(event.target.value))}
                placeholder={t('qqBotIntentKeywordsPlaceholder')}
              />
            </Col>
          </Row>
        </Space>
      </Card>
      <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>{t('save')}</Button>
    </Space>
  );

  const securityAndSchedule = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title={<Space><SafetyCertificateOutlined />{t('qqBotSecurity')}</Space>}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space wrap>
            <Checkbox checked={draft.allowGroups} onChange={(event) => update('allowGroups', event.target.checked)}>{t('qqBotAllowGroups')}</Checkbox>
            <Checkbox checked={draft.allowC2c} onChange={(event) => update('allowC2c', event.target.checked)}>{t('qqBotAllowC2c')}</Checkbox>
            <Checkbox checked={draft.allowChannels} onChange={(event) => update('allowChannels', event.target.checked)}>{t('qqBotAllowChannels')}</Checkbox>
          </Space>
          <Row gutter={[16, 16]}>
            <Col xs={24} lg={8}><Text strong>{t('qqBotAllowedGroups')}</Text><Input.TextArea rows={7} value={joinQqBotList(draft.allowedGroupIds)} onChange={(event) => update('allowedGroupIds', splitQqBotList(event.target.value))} placeholder={t('qqBotAllowedGroupsPlaceholder')} /></Col>
            <Col xs={24} lg={8}><Text strong>{t('qqBotAllowedUsers')}</Text><Input.TextArea rows={7} value={joinQqBotList(draft.allowedUserIds)} onChange={(event) => update('allowedUserIds', splitQqBotList(event.target.value))} placeholder={t('qqBotAllowedUsersPlaceholder')} /></Col>
            <Col xs={24} lg={8}><Text strong>{t('qqBotAdminUsers')}</Text><Input.TextArea rows={7} value={joinQqBotList(draft.adminUserIds)} onChange={(event) => update('adminUserIds', splitQqBotList(event.target.value))} placeholder={t('qqBotAdminUsersPlaceholder')} /></Col>
          </Row>
          <Alert type="info" showIcon message={t('qqBotIdProbeHint')} />
          <Row gutter={[16, 16]}>
            <Col xs={12} md={6}><Text>{t('qqBotMaxInput')}</Text><InputNumber min={64} max={4000} value={draft.maxInputChars} onChange={(value) => update('maxInputChars', value || 64)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotMaxReply')}</Text><InputNumber min={200} max={2000} value={draft.maxReplyChars} onChange={(value) => update('maxReplyChars', value || 200)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotMaxQueue')}</Text><InputNumber min={16} max={512} value={draft.maxQueuedRequests} onChange={(value) => update('maxQueuedRequests', value || 16)} style={{ width: '100%' }} /></Col>
            <Col xs={12} md={6}><Text>{t('qqBotAuditLimit')}</Text><InputNumber min={20} max={1000} value={draft.auditMaxEntries} onChange={(value) => update('auditMaxEntries', value || 20)} style={{ width: '100%' }} /></Col>
          </Row>
        </Space>
      </Card>
      <Card title={<Space><ScheduleOutlined />{t('qqBotSchedule')}</Space>}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space wrap>
            <Checkbox checked={draft.scheduledReportEnabled} onChange={(event) => update('scheduledReportEnabled', event.target.checked)}>{t('qqBotScheduleEnabled')}</Checkbox>
            <Checkbox checked={draft.scheduledReportIncludePlayers} onChange={(event) => update('scheduledReportIncludePlayers', event.target.checked)}>{t('qqBotSchedulePlayers')}</Checkbox>
            <Checkbox checked={draft.scheduledReportIncludeMemory} onChange={(event) => update('scheduledReportIncludeMemory', event.target.checked)}>{t('qqBotScheduleMemory')}</Checkbox>
          </Space>
          <Text>{t('qqBotScheduleInterval')}</Text>
          <InputNumber min={5} max={10080} value={draft.scheduledReportIntervalMinutes} onChange={(value) => update('scheduledReportIntervalMinutes', value || 5)} />
          <Text strong>{t('qqBotScheduleTargets')}</Text>
          <Input.TextArea rows={5} value={joinQqBotList(draft.scheduledReportTargets)} onChange={(event) => update('scheduledReportTargets', splitQqBotList(event.target.value))} placeholder={t('qqBotScheduleTargetsPlaceholder')} />
          <Paragraph type="secondary">{t('qqBotScheduleTargetsDesc')}</Paragraph>
        </Space>
      </Card>
      <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>{t('save')}</Button>
    </Space>
  );

  const operations = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title={<Space><SendOutlined />{t('qqBotManualSend')}</Space>}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Row gutter={[12, 12]}>
            <Col xs={24} md={5}><Select value={sendType} onChange={setSendType} style={{ width: '100%' }} options={[{ value: 'group', label: t('qqBotTargetGroup') }, { value: 'c2c', label: t('qqBotTargetC2c') }, { value: 'channel', label: t('qqBotTargetChannel') }]} /></Col>
            <Col xs={24} md={19}><Input value={sendTarget} onChange={(event) => setSendTarget(event.target.value)} placeholder={t('qqBotSendTargetPlaceholder')} /></Col>
          </Row>
          <Input.TextArea rows={4} value={sendContent} maxLength={draft.maxReplyChars} showCount onChange={(event) => setSendContent(event.target.value)} placeholder={t('qqBotSendContentPlaceholder')} />
          <Tooltip title={!status?.connected ? t('qqBotSendDisconnected') : undefined}>
            <Button type="primary" icon={<SendOutlined />} disabled={!status?.connected} loading={action === 'qqBotSend'} onClick={send}>{t('qqBotSend')}</Button>
          </Tooltip>
        </Space>
      </Card>
      <Card title={t('qqBotAudit')} extra={<Space><Checkbox checked={draft.auditEnabled} onChange={(event) => update('auditEnabled', event.target.checked)}>{t('qqBotAuditEnabled')}</Checkbox><Button size="small" icon={<ReloadOutlined />} onClick={() => load(false)}>{t('refresh')}</Button></Space>}>
        <Table
          rowKey="id"
          size="small"
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ x: 900 }}
          dataSource={audit}
          columns={[
            { title: t('qqBotAuditTime'), dataIndex: 'timestampMs', width: 170, render: (value: number) => time(value) },
            { title: t('qqBotAuditDirection'), dataIndex: 'direction', width: 80, render: (value: string) => <Tag>{value}</Tag> },
            { title: t('qqBotAuditTarget'), key: 'target', width: 170, render: (_: unknown, row: QqBotAuditEntryDto) => `${row.targetType || '—'} · ${shortId(row.targetId)}` },
            { title: t('qqBotAuditSender'), dataIndex: 'senderId', width: 140, render: shortId },
            { title: t('qqBotAuditCommand'), dataIndex: 'command', width: 130 },
            { title: t('qqBotAuditOutcome'), dataIndex: 'outcome', width: 130, render: (value: string) => <Tag color={value === 'sent' || value === 'ok' || value === 'accepted' ? 'green' : value.includes('error') ? 'red' : 'orange'}>{value}</Tag> },
            { title: t('qqBotAuditPreview'), dataIndex: 'preview', ellipsis: true },
            { title: t('qqBotAuditLatency'), dataIndex: 'latencyMs', width: 100, render: (value: number) => `${value} ms` },
          ]}
        />
      </Card>
    </Space>
  );

  return (
    <Tabs
      defaultActiveKey="status"
      items={[
        { key: 'status', label: t('qqBotTabStatus'), children: statusCard },
        { key: 'connection', label: t('qqBotTabConnection'), children: connectionSettings },
        { key: 'capabilities', label: t('qqBotTabCapabilities'), children: capabilities },
        { key: 'security', label: t('qqBotTabSecurity'), children: securityAndSchedule },
        { key: 'operations', label: t('qqBotTabOperations'), children: operations },
      ]}
    />
  );
}
