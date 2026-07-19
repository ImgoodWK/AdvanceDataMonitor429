import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  List,
  message,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CodeOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { ApiClientError, getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type {
  AdminConsoleBootstrapResponse,
  AdminConsoleExecuteResponse,
  AdminConsoleHistoryEntry,
  AdminConsoleHistoryResponse,
  AdminConsolePlayer,
  AdminConsolePlayersResponse,
  AdminConsolePreset,
  AdminConsolePresetResponse,
} from '@/types/dto';
import {
  insertCommandToken,
  isHighRiskAdminCommand,
  normalizeAdminCommand,
  type AdminConsolePlayerFilter,
} from '@/utils/adminConsole';

const { Text, Paragraph } = Typography;

interface ServerConsolePanelProps {
  active: boolean;
}

interface SelectionRange {
  start: number;
  end: number;
}

function statusTag(status: AdminConsoleHistoryEntry['status'], t: (key: string) => string) {
  if (status === 'completed') return <Tag color="success">{t('adminConsoleStatusCompleted')}</Tag>;
  if (status === 'failed') return <Tag color="error">{t('adminConsoleStatusFailed')}</Tag>;
  return <Tag color="processing">{t('adminConsoleStatusQueued')}</Tag>;
}

export function ServerConsolePanel({ active }: ServerConsolePanelProps) {
  const { t } = useI18n();
  const [command, setCommand] = useState('');
  const [selection, setSelection] = useState<SelectionRange>({ start: 0, end: 0 });
  const commandRef = useRef<HTMLTextAreaElement | null>(null);
  const [presets, setPresets] = useState<AdminConsolePreset[]>([]);
  const [history, setHistory] = useState<AdminConsoleHistoryEntry[]>([]);
  const [players, setPlayers] = useState<AdminConsolePlayer[]>([]);
  const [latest, setLatest] = useState<AdminConsoleHistoryEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const [playersLoading, setPlayersLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [playerFilter, setPlayerFilter] = useState<AdminConsolePlayerFilter>('online');
  const [playerSearch, setPlayerSearch] = useState('');
  const [presetModalOpen, setPresetModalOpen] = useState(false);
  const [editingPreset, setEditingPreset] = useState<AdminConsolePreset | null>(null);
  const [presetLabel, setPresetLabel] = useState('');
  const [presetCommand, setPresetCommand] = useState('');
  const [presetDescription, setPresetDescription] = useState('');
  const [presetSaving, setPresetSaving] = useState(false);

  const loadBootstrap = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getApiClient().get<AdminConsoleBootstrapResponse>('/api/admin/server-console');
      setPresets(response.presets || []);
      setHistory(response.history || []);
      setLoaded(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsoleLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const loadPlayers = useCallback(async () => {
    setPlayersLoading(true);
    try {
      const response = await getApiClient().get<AdminConsolePlayersResponse>(
        '/api/admin/server-console/players',
      );
      setPlayers(response.players || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsolePlayersLoadFailed'));
    } finally {
      setPlayersLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (!active || loaded) return;
    void loadBootstrap();
    void loadPlayers();
  }, [active, loaded, loadBootstrap, loadPlayers]);

  const playerCounts = useMemo(() => ({
    all: players.length,
    online: players.filter((player) => player.online).length,
    offline: players.filter((player) => !player.online).length,
  }), [players]);

  const filteredPlayers = useMemo(() => {
    const needle = playerSearch.trim().toLowerCase();
    return players.filter((player) => {
      if (playerFilter === 'online' && !player.online) return false;
      if (playerFilter === 'offline' && player.online) return false;
      return !needle || player.name.toLowerCase().includes(needle) || player.uuid.toLowerCase().includes(needle);
    });
  }, [playerFilter, playerSearch, players]);

  const addToken = useCallback((token: string) => {
    const result = insertCommandToken(command, token, selection.start, selection.end);
    setCommand(result.value);
    setSelection({ start: result.cursor, end: result.cursor });
    window.setTimeout(() => {
      const textarea = commandRef.current;
      if (!textarea) return;
      textarea.focus();
      textarea.setSelectionRange(result.cursor, result.cursor);
    }, 0);
  }, [command, selection]);

  const loadCommand = useCallback((value: string) => {
    const normalized = normalizeAdminCommand(value);
    setCommand(normalized);
    setSelection({ start: normalized.length, end: normalized.length });
    window.setTimeout(() => {
      const textarea = commandRef.current;
      if (!textarea) return;
      textarea.focus();
      textarea.setSelectionRange(normalized.length, normalized.length);
    }, 0);
  }, []);

  const mergeHistory = useCallback((entry: AdminConsoleHistoryEntry) => {
    setHistory((current) => {
      const summary = { ...entry, output: null };
      return [summary, ...current.filter((item) => item.id !== entry.id)].slice(0, 40);
    });
  }, []);

  const submitCommand = useCallback(async (confirmed = false) => {
    const normalized = normalizeAdminCommand(command);
    if (!normalized) {
      message.warning(t('adminConsoleCommandRequired'));
      return;
    }
    if (isHighRiskAdminCommand(normalized) && !confirmed) {
      Modal.confirm({
        title: t('adminConsoleRiskConfirmTitle'),
        content: t('adminConsoleRiskConfirmDesc'),
        okText: t('adminConsoleRiskConfirmRun'),
        okButtonProps: { danger: true },
        cancelText: t('cancel'),
        onOk: () => submitCommand(true),
      });
      return;
    }
    setExecuting(true);
    try {
      const response = await getApiClient().post<AdminConsoleExecuteResponse>(
        '/api/admin/server-console/execute',
        { command: normalized, confirmed },
      );
      setLatest(response.entry);
      mergeHistory(response.entry);
      if (response.pending) message.warning(t('adminConsoleExecutePending'));
      else if (response.entry.status === 'failed') message.error(t('adminConsoleExecuteFailed'));
      else message.success(t('adminConsoleExecuteSuccess'));
    } catch (error) {
      if (error instanceof ApiClientError && error.code === 'confirmation_required') {
        Modal.confirm({
          title: t('adminConsoleRiskConfirmTitle'),
          content: t('adminConsoleRiskConfirmDesc'),
          okText: t('adminConsoleRiskConfirmRun'),
          okButtonProps: { danger: true },
          cancelText: t('cancel'),
          onOk: () => submitCommand(true),
        });
      } else {
        message.error(error instanceof Error ? error.message : t('adminConsoleExecuteFailed'));
      }
    } finally {
      setExecuting(false);
    }
  }, [command, mergeHistory, t]);

  const openPresetModal = useCallback((preset?: AdminConsolePreset) => {
    setEditingPreset(preset ?? null);
    setPresetLabel(preset?.label ?? '');
    setPresetCommand(preset?.command ?? normalizeAdminCommand(command));
    setPresetDescription(preset?.description ?? '');
    setPresetModalOpen(true);
  }, [command]);

  const savePreset = useCallback(async () => {
    if (!presetLabel.trim() || !normalizeAdminCommand(presetCommand)) {
      message.warning(t('adminConsolePresetRequired'));
      return;
    }
    setPresetSaving(true);
    try {
      const response = await getApiClient().put<AdminConsolePresetResponse>(
        '/api/admin/server-console/presets',
        {
          id: editingPreset?.id,
          label: presetLabel.trim(),
          command: normalizeAdminCommand(presetCommand),
          description: presetDescription.trim(),
        },
      );
      setPresets((current) => [
        response.preset,
        ...current.filter((item) => item.id !== response.preset.id),
      ].sort((left, right) => left.label.localeCompare(right.label)));
      setPresetModalOpen(false);
      message.success(t('adminConsolePresetSaved'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsolePresetSaveFailed'));
    } finally {
      setPresetSaving(false);
    }
  }, [editingPreset, presetCommand, presetDescription, presetLabel, t]);

  const deletePreset = useCallback(async (id: string) => {
    try {
      await getApiClient().delete(`/api/admin/server-console/presets/${encodeURIComponent(id)}`);
      setPresets((current) => current.filter((item) => item.id !== id));
      message.success(t('adminConsolePresetDeleted'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsolePresetDeleteFailed'));
    }
  }, [t]);

  const openHistory = useCallback(async (id: string) => {
    try {
      const response = await getApiClient().get<AdminConsoleHistoryResponse>(
        `/api/admin/server-console/history/${encodeURIComponent(id)}`,
      );
      setLatest(response.entry);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsoleHistoryLoadFailed'));
    }
  }, [t]);

  const clearHistory = useCallback(async () => {
    try {
      await getApiClient().post('/api/admin/server-console/history/clear');
      setHistory([]);
      setLatest(null);
      message.success(t('adminConsoleHistoryCleared'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('adminConsoleHistoryClearFailed'));
    }
  }, [t]);

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="warning"
          showIcon
          message={t('adminConsoleSecurityTitle')}
          description={t('adminConsoleSecurityDesc')}
        />

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={15}>
            <Card title={<Space><CodeOutlined />{t('adminConsoleCommandTitle')}</Space>} size="small">
              <Input.TextArea
                aria-label={t('adminConsoleCommandTitle')}
                ref={(node) => {
                  commandRef.current = node?.resizableTextArea?.textArea ?? null;
                }}
                value={command}
                onChange={(event) => setCommand(event.target.value)}
                onSelect={(event) => {
                  const target = event.currentTarget;
                  setSelection({ start: target.selectionStart, end: target.selectionEnd });
                }}
                onKeyDown={(event) => {
                  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                    event.preventDefault();
                    void submitCommand();
                  }
                }}
                placeholder={t('adminConsoleCommandPlaceholder')}
                autoSize={{ minRows: 4, maxRows: 8 }}
                maxLength={512}
                showCount
              />
              <Space wrap style={{ marginTop: 12 }}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  loading={executing}
                  onClick={() => void submitCommand()}
                >
                  {t('adminConsoleExecute')}
                </Button>
                <Button
                  icon={<SaveOutlined />}
                  disabled={!normalizeAdminCommand(command)}
                  onClick={() => openPresetModal()}
                >
                  {t('adminConsoleSavePreset')}
                </Button>
                <Text type="secondary">{t('adminConsoleShortcut')}</Text>
              </Space>
            </Card>

            <Card
              title={<Space><HistoryOutlined />{t('adminConsoleOutputTitle')}</Space>}
              size="small"
              style={{ marginTop: 16 }}
              extra={latest ? statusTag(latest.status, t) : undefined}
            >
              {!latest ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('adminConsoleNoOutput')} />
              ) : (
                <Space direction="vertical" size="small" style={{ width: '100%' }}>
                  <Text code>{latest.command}</Text>
                  <Text type="secondary">
                    {t('adminConsoleAffected')}: {latest.affected} · {latest.durationMs} ms · {latest.actorName || 'WebAE'}
                  </Text>
                  {latest.error && <Alert type="error" message={latest.error} />}
                  <pre style={{
                    margin: 0,
                    maxHeight: 320,
                    overflow: 'auto',
                    padding: 12,
                    borderRadius: 6,
                    background: 'rgba(0, 0, 0, 0.78)',
                    color: '#d9f7be',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}>
                    {(latest.output || []).join('\n') || t('adminConsoleNoCommandMessages')}
                  </pre>
                  {latest.outputTruncated && <Text type="warning">{t('adminConsoleOutputTruncated')}</Text>}
                </Space>
              )}
            </Card>
          </Col>

          <Col xs={24} xl={9}>
            <Card
              title={<Space><UserOutlined />{t('adminConsolePlayersTitle')}</Space>}
              size="small"
              extra={(
                <Tooltip title={t('adminConsoleRefreshPlayers')}>
                  <Button
                    type="text"
                    size="small"
                    icon={<ReloadOutlined />}
                    aria-label={t('adminConsoleRefreshPlayers')}
                    loading={playersLoading}
                    onClick={() => void loadPlayers()}
                  />
                </Tooltip>
              )}
            >
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Segmented
                  block
                  value={playerFilter}
                  onChange={(value) => setPlayerFilter(value as AdminConsolePlayerFilter)}
                  options={[
                    { value: 'online', label: `${t('adminConsoleFilterOnline')} (${playerCounts.online})` },
                    { value: 'offline', label: `${t('adminConsoleFilterOffline')} (${playerCounts.offline})` },
                    { value: 'all', label: `${t('adminConsoleFilterAll')} (${playerCounts.all})` },
                  ]}
                />
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  value={playerSearch}
                  onChange={(event) => setPlayerSearch(event.target.value)}
                  placeholder={t('adminConsolePlayerSearch')}
                />
                <Spin spinning={playersLoading}>
                  <List
                    size="small"
                    dataSource={filteredPlayers}
                    locale={{ emptyText: t('adminConsoleNoPlayers') }}
                    style={{ maxHeight: 430, overflowY: 'auto' }}
                    renderItem={(player) => (
                      <List.Item
                        actions={[
                          <Button key="name" size="small" type="link" onClick={() => addToken(player.name)}>
                            {t('adminConsoleInsertName')}
                          </Button>,
                          <Button key="uuid" size="small" type="link" onClick={() => addToken(player.uuid)}>
                            UUID
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          avatar={(
                            <Tag color={player.online ? 'green' : 'default'}>
                              {player.online ? t('adminConsoleFilterOnline') : t('adminConsoleFilterOffline')}
                            </Tag>
                          )}
                          title={<Text>{player.name}</Text>}
                          description={<Text type="secondary" copyable style={{ fontSize: 11 }}>{player.uuid}</Text>}
                        />
                      </List.Item>
                    )}
                  />
                </Spin>
              </Space>
            </Card>
          </Col>
        </Row>

        <Card title={t('adminConsolePresetsTitle')} size="small">
          <Table
            rowKey="id"
            size="small"
            pagination={{ pageSize: 8, hideOnSinglePage: true }}
            dataSource={presets}
            locale={{ emptyText: t('adminConsoleNoPresets') }}
            columns={[
              { title: t('adminConsolePresetName'), dataIndex: 'label' },
              {
                title: t('adminConsolePresetCommand'),
                dataIndex: 'command',
                render: (value: string) => <Text code>{value}</Text>,
              },
              {
                title: t('adminConsolePresetDescription'),
                dataIndex: 'description',
                responsive: ['lg'],
                render: (value: string) => value || '—',
              },
              {
                title: t('adminConsoleActions'),
                key: 'actions',
                width: 240,
                render: (_: unknown, preset: AdminConsolePreset) => (
                  <Space size="small">
                    <Button size="small" icon={<PlayCircleOutlined />} onClick={() => loadCommand(preset.command)}>
                      {t('adminConsoleLoadCommand')}
                    </Button>
                    <Tooltip title={t('adminConsoleEditPreset')}>
                      <Button
                        size="small"
                        icon={<EditOutlined />}
                        aria-label={t('adminConsoleEditPreset')}
                        onClick={() => openPresetModal(preset)}
                      />
                    </Tooltip>
                    <Popconfirm
                      title={t('adminConsoleDeletePresetConfirm')}
                      onConfirm={() => void deletePreset(preset.id)}
                    >
                      <Tooltip title={t('adminConsoleDeletePresetConfirm')}>
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          aria-label={t('adminConsoleDeletePresetConfirm')}
                        />
                      </Tooltip>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        </Card>

        <Card
          title={t('adminConsoleHistoryTitle')}
          size="small"
          extra={history.length > 0 ? (
            <Popconfirm title={t('adminConsoleClearHistoryConfirm')} onConfirm={() => void clearHistory()}>
              <Button size="small" danger icon={<DeleteOutlined />}>{t('adminConsoleClearHistory')}</Button>
            </Popconfirm>
          ) : undefined}
        >
          <Table
            rowKey="id"
            size="small"
            pagination={{ pageSize: 10, hideOnSinglePage: true }}
            dataSource={history}
            locale={{ emptyText: t('adminConsoleNoHistory') }}
            columns={[
              {
                title: t('adminConsoleHistoryTime'),
                dataIndex: 'createdAt',
                width: 180,
                render: (value: number) => new Date(value).toLocaleString(),
              },
              {
                title: t('adminConsolePresetCommand'),
                dataIndex: 'command',
                render: (value: string) => <Text code>{value}</Text>,
              },
              {
                title: t('status'),
                dataIndex: 'status',
                width: 110,
                render: (value: AdminConsoleHistoryEntry['status']) => statusTag(value, t),
              },
              { title: t('adminConsoleHistoryActor'), dataIndex: 'actorName', responsive: ['md'] },
              {
                title: t('adminConsoleActions'),
                key: 'actions',
                width: 190,
                render: (_: unknown, entry: AdminConsoleHistoryEntry) => (
                  <Space size="small">
                    <Button size="small" icon={<EyeOutlined />} onClick={() => void openHistory(entry.id)}>
                      {t('adminConsoleViewOutput')}
                    </Button>
                    <Button size="small" onClick={() => loadCommand(entry.command)}>
                      {t('adminConsoleLoadCommand')}
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </Card>
      </Space>

      <Modal
        open={presetModalOpen}
        title={editingPreset ? t('adminConsoleEditPreset') : t('adminConsoleCreatePreset')}
        okText={t('save')}
        cancelText={t('cancel')}
        confirmLoading={presetSaving}
        onOk={() => void savePreset()}
        onCancel={() => setPresetModalOpen(false)}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong>{t('adminConsolePresetName')}</Text>
            <Input
              aria-label={t('adminConsolePresetName')}
              value={presetLabel}
              maxLength={64}
              onChange={(event) => setPresetLabel(event.target.value)}
            />
          </div>
          <div>
            <Text strong>{t('adminConsolePresetCommand')}</Text>
            <Input.TextArea
              aria-label={t('adminConsolePresetCommand')}
              value={presetCommand}
              maxLength={512}
              autoSize={{ minRows: 3, maxRows: 6 }}
              onChange={(event) => setPresetCommand(event.target.value)}
            />
          </div>
          <div>
            <Text strong>{t('adminConsolePresetDescription')}</Text>
            <Input.TextArea
              aria-label={t('adminConsolePresetDescription')}
              value={presetDescription}
              maxLength={200}
              autoSize={{ minRows: 2, maxRows: 4 }}
              onChange={(event) => setPresetDescription(event.target.value)}
            />
          </div>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t('adminConsolePresetSharedHint')}
          </Paragraph>
        </Space>
      </Modal>
    </Spin>
  );
}
