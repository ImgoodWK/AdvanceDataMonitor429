import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Card,
  Input,
  Button,
  Space,
  Avatar,
  Tag,
  Empty,
  Spin,
  Switch,
  Typography,
  Row,
  Col,
  Drawer,
  Tooltip,
  Segmented,
  Image,
} from 'antd';
import {
  SendOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { useAppContext } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import { getApiClient } from '@/api/client';
import { PageShell } from '@/components/Layout/PageShell';
import { formatTime, formatDuration } from '@/utils/format';
import { useLocalStorage, useLocalStorageString } from '@/hooks/useLocalStorage';
import { useVisibilityAwarePolling } from '@/hooks/useVisibilityAwarePolling';
import {
  getAvatarUrl,
  avatarInitial,
  avatarColorSeed,
  isValidMinecraftUuid,
} from '@/utils/avatar';
import type {
  ChatHistoryResponse,
  ChatSendResponse,
  ChatSinceResponse,
  ChatMessageDto,
  PlayersResponse,
  PlayerDto,
} from '@/types/dto';
import { hasScreenshotAttachment, screenshotSizeKiB } from '@/utils/chatAttachments';

const { Text } = Typography;

type ChatMode = 'list' | 'bubble';

/**
 * 头像组件：浏览器直连 Crafatar CDN 获取正版皮肤头像，失败回退首字母圆形占位。
 * 内网环境无法访问 crafatar.com 时，会触发 onError 回退到首字母占位。
 */
function PlayerAvatar({
  uuid,
  name,
  size,
  showAvatar,
}: {
  uuid?: string;
  name: string;
  size: number;
  showAvatar: boolean;
}) {
  const url = useMemo(() => (showAvatar ? getAvatarUrl(uuid, size) : null), [uuid, size, showAvatar]);
  if (!showAvatar) {
    return null;
  }
  if (!url || !isValidMinecraftUuid(uuid)) {
    // 离线玩家或无 UUID —— 直接首字母占位
    const seed = avatarColorSeed(uuid, name);
    const hue = seed % 360;
    return (
      <span
        className="chat-avatar-initial"
        style={{
          width: size,
          height: size,
          background: `hsl(${hue}, 55%, 45%)`,
          fontSize: size * 0.45,
        }}
        aria-label={name}
        title={name}
      >
        {avatarInitial(name)}
      </span>
    );
  }
  return (
    <Avatar
      size={size}
      src={url}
      onError={() => true /* 加载失败时由 antd 回退到 icon，下面用 icon 渲染首字母 */}
    >
      {/* Crafatar 加载失败时 antd 会渲染这个 children 作为回退 */}
      <span style={{ fontSize: size * 0.4 }}>{avatarInitial(name)}</span>
    </Avatar>
  );
}

function ScreenshotAttachment({ message }: { message: ChatMessageDto }) {
  const { t } = useI18n();
  const [blobUrl, setBlobUrl] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    let objectUrl = '';
    if (!hasScreenshotAttachment(message)) return undefined;
    const attachmentId = message.attachmentId as string;
    setFailed(false);
    getApiClient()
      .getBlob(`/api/chat/attachment?id=${encodeURIComponent(attachmentId)}`)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [message.attachmentId]);

  if (failed) return <Text type="danger">{t('chatScreenshotLoadFailed')}</Text>;
  if (!blobUrl) return <Spin size="small" />;
  const sizeKb = screenshotSizeKiB(message);
  return (
    <div className="chat-screenshot-attachment">
      <Image
        src={blobUrl}
        alt={message.attachmentName || t('chatScreenshot')}
        preview={{ mask: t('chatScreenshotPreview') }}
        style={{ maxWidth: 'min(520px, 100%)', maxHeight: 320, objectFit: 'contain' }}
      />
      <Text type="secondary" className="chat-screenshot-meta">
        {t('chatScreenshotMeta', {
          width: message.attachmentWidth || 0,
          height: message.attachmentHeight || 0,
          size: sizeKb,
        })}
      </Text>
    </div>
  );
}

export function ChatPage() {
  const { notify, actorUuid, pauseRefreshWhenHidden } = useAppContext();
  const { t } = useI18n();
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [players, setPlayers] = useState<PlayerDto[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  // 持久化开关：玩家头像、玩家信息（按计划用 useLocalStorage）
  const [showAvatars, setShowAvatars] = useLocalStorage<boolean>('webae_chat_showAvatars', true);
  const [showPlayerInfo, setShowPlayerInfo] = useLocalStorage<boolean>(
    'webae_chat_showPlayerInfo',
    true
  );
  // 消息样式：列表 vs 气泡，持久化
  const [chatMode, setChatMode] = useLocalStorageString('webae_chat_mode', 'bubble') as [
    ChatMode,
    (v: ChatMode) => void
  ];
  // 玩家列表折叠状态：true=展开（侧栏可见），false=折叠（仅留展开按钮）
  const [playerListCollapsed, setPlayerListCollapsed] = useLocalStorage<boolean>(
    'webae_chat_players_collapsed',
    false
  );
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [lastId, setLastId] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  const fetchHistory = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getApiClient().get<ChatHistoryResponse>('/api/chat/history');
      if (data.success && data.messages) {
        setMessages(data.messages);
        const maxId = data.messages.reduce((m, msg) => Math.max(m, msg.id), 0);
        setLastId(maxId);
      }
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  }, []);

  const pollChat = useCallback(async () => {
    try {
      const data = await getApiClient().get<ChatSinceResponse>(`/api/chat/since?id=${Math.max(0, lastId)}`);
      if (data.success && data.messages && data.messages.length > 0) {
        // 防御性按 msg.id 去重：后端已支持 getAfterId 增量，但客户端可能因
        // 重新挂载/时钟漂移收到重复 id，这里再用 Set 过滤避免气泡重复渲染。
        setMessages((prev) => {
          const seen = new Set(prev.map((m) => m.id));
          const fresh = data.messages.filter((m) => !seen.has(m.id));
          return fresh.length ? [...prev, ...fresh] : prev;
        });
        const maxId = data.messages.reduce((m, msg) => Math.max(m, msg.id), lastId);
        setLastId(maxId);
      }
    } catch {
      /* ignore */
    }
  }, [lastId]);

  const pollPlayers = useCallback(async () => {
    try {
      const data = await getApiClient().get<PlayersResponse>('/api/players');
      if (!data.success) return;
      // 后端返回 {online:[...], offline:[...]}；前端合并为 players 数组（每项已带 online 字段）。
      if (data.players && data.players.length > 0) {
        setPlayers(data.players);
        return;
      }
      const online = data.online || [];
      const offline = data.offline || [];
      setPlayers([...online, ...offline]);
    } catch {
      /* ignore */
    }
  }, []);

  const sendMessage = useCallback(async () => {
    if (!input.trim()) return;
    setSending(true);
    try {
      const data = await getApiClient().post<ChatSendResponse>('/api/chat/send', {
        content: input.trim(),
      });
      if (data.success) {
        setInput('');
        pollChat();
      } else {
        notify(t('chatSendFailed'), 'error');
      }
    } catch {
      notify(t('chatSendFailed'), 'error');
    } finally {
      setSending(false);
    }
  }, [input, pollChat, notify, t]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  useVisibilityAwarePolling(pollChat, 2500, pauseRefreshWhenHidden);
  useVisibilityAwarePolling(pollPlayers, 10000, pauseRefreshWhenHidden);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages]);

  const sourceColor: Record<string, string> = {
    web: 'var(--accent-dim)',
    game: 'var(--bg-hover)',
    system: 'transparent',
  };

  // 判断消息是否为「自己」：web 来源且 UUID 匹配当前登录玩家。
  const isSelf = useCallback(
    (msg: ChatMessageDto) => {
      if (msg.source !== 'web') return false;
      if (actorUuid && msg.senderUuid && msg.senderUuid === actorUuid) return true;
      return false;
    },
    [actorUuid]
  );

  // 玩家列表标题栏 extra：两个 Switch（玩家头像 / 玩家信息）+ 折叠按钮
  const playerListExtra = (
    <Space size="small" wrap>
      <Tooltip title={t('chatShowAvatars')}>
        <Switch
          checked={showAvatars}
          onChange={setShowAvatars}
          size="small"
          aria-label={t('chatShowAvatars')}
        />
      </Tooltip>
      <Tooltip title={t('chatShowPlayerInfo')}>
        <Switch
          checked={showPlayerInfo}
          onChange={setShowPlayerInfo}
          size="small"
          aria-label={t('chatShowPlayerInfo')}
        />
      </Tooltip>
      <Tooltip title={t('chatCollapsePlayers')}>
        <Button
          size="small"
          icon={<MenuFoldOutlined />}
          onClick={() => setPlayerListCollapsed(true)}
          aria-label={t('chatCollapsePlayers')}
        />
      </Tooltip>
    </Space>
  );

  const renderPlayerList = () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {players.length === 0 ? (
        <Empty description={t('chatNoPlayers')} />
      ) : (
        players.map((p) => (
          <div
            key={p.uuid || p.name}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '6px 8px',
              borderRadius: 4,
              background: p.online ? 'var(--success-dim)' : 'transparent',
            }}
          >
            <PlayerAvatar
              uuid={p.uuid}
              name={p.name}
              size={28}
              showAvatar={showAvatars}
            />
            <div style={{ flex: 1, minWidth: 0 }}>
              <Text strong style={{ fontSize: '0.8rem' }}>
                {p.name}
              </Text>
              <div>
                <Tag color={p.online ? 'success' : 'default'} style={{ fontSize: '0.6rem' }}>
                  {p.online ? t('chatOnline') : t('chatOffline')}
                </Tag>
                {showPlayerInfo && (
                  <Text type="secondary" style={{ fontSize: '0.7rem' }}>
                    {p.online
                      ? `${t('chatOnlineDuration')} ${formatDuration(p.onlineMs)}`
                      : `${t('chatLastSeen')} ${formatTime(p.lastLogout)}`}
                  </Text>
                )}
              </div>
            </div>
          </div>
        ))
      )}
    </div>
  );

  const renderMessage = (msg: ChatMessageDto) => {
    const content = (
      <>
        {msg.content ? <div>{msg.content}</div> : hasScreenshotAttachment(msg) ? <div>{t('chatScreenshot')}</div> : null}
        {hasScreenshotAttachment(msg) ? <ScreenshotAttachment message={msg} /> : null}
      </>
    );
    if (chatMode === 'bubble') {
      if (msg.source === 'system') {
        return (
          <div key={msg.id} className="chat-bubble-system">
            {msg.content}
          </div>
        );
      }
      const self = isSelf(msg);
      return (
        <div
          key={msg.id}
          className={`chat-bubble-row ${self ? 'chat-bubble-self' : 'chat-bubble-other'}`}
        >
          <div className="chat-bubble-avatar">
            <PlayerAvatar
              uuid={msg.senderUuid}
              name={msg.senderName}
              size={32}
              showAvatar={showAvatars}
            />
          </div>
          <div className="chat-bubble-body">
            <div className="chat-bubble-meta">
              <span style={{ fontWeight: 500 }}>
                {self ? `${msg.senderName} (${t('chatYou')})` : msg.senderName}
              </span>
              <Tag style={{ fontSize: '0.6rem' }}>
                {t('chatSource' + msg.source.charAt(0).toUpperCase() + msg.source.slice(1))}
              </Tag>
              <span>{formatTime(msg.timestamp)}</span>
            </div>
            <div className="chat-bubble-content">{content}</div>
          </div>
        </div>
      );
    }
    // 列表模式（保留原有样式）
    return (
      <div
        key={msg.id}
        className={`chat-message ${msg.source}`}
        style={{ background: sourceColor[msg.source] || 'transparent' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {showAvatars && msg.source !== 'system' && (
            <PlayerAvatar
              uuid={msg.senderUuid}
              name={msg.senderName}
              size={24}
              showAvatar={showAvatars}
            />
          )}
          <Text strong style={{ fontSize: '0.8rem' }}>
            {msg.senderName}
          </Text>
          <Tag style={{ fontSize: '0.6rem' }}>
            {t('chatSource' + msg.source.charAt(0).toUpperCase() + msg.source.slice(1))}
          </Tag>
          <Text type="secondary" style={{ fontSize: '0.7rem' }}>
            {formatTime(msg.timestamp)}
          </Text>
        </div>
        <div
          style={{ marginTop: 2, marginLeft: showAvatars && msg.source !== 'system' ? 32 : 0 }}
        >
          {content}
        </div>
      </div>
    );
  };

  // 消息区标题栏 extra：消息样式切换
  const messageCardExtra = (
    <Segmented
      size="small"
      value={chatMode}
      onChange={(v) => setChatMode(v as ChatMode)}
      options={[
        { label: t('chatModeList'), value: 'list' },
        { label: t('chatModeBubble'), value: 'bubble' },
      ]}
      aria-label={t('chatSelectMode')}
    />
  );

  const onlineCount = players.filter((p) => p.online).length;

  // Vertical "players" tab that appears on the left edge of the messages area
  // when the player list is collapsed — a more visible entry point than the
  // title button alone. Clicking opens the player-list Drawer.
  const playerListTab = playerListCollapsed ? (
    <Tooltip
      title={`${t('chatExpandPlayers')} (${onlineCount}/${players.length})`}
      placement="right"
    >
      <button
        onClick={() => setDrawerOpen(true)}
        aria-label={`${t('chatExpandPlayers')} (${onlineCount}/${players.length})`}
        style={{
          position: 'absolute',
          left: 0,
          top: '50%',
          transform: 'translateY(-50%)',
          width: 22,
          height: 96,
          padding: 0,
          borderRadius: '0 6px 6px 0',
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border)',
          borderLeft: 'none',
          color: 'var(--text-secondary)',
          cursor: 'pointer',
          zIndex: 5,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 6,
          fontSize: '0.65rem',
          boxShadow: 'var(--shadow-elev, 0 2px 8px rgba(0,0,0,0.15))',
          transition: 'background 0.2s, color 0.2s',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = 'var(--sidebar-hover)';
          e.currentTarget.style.color = 'var(--accent)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = 'var(--bg-secondary)';
          e.currentTarget.style.color = 'var(--text-secondary)';
        }}
      >
        <MessageOutlined style={{ fontSize: '0.9rem' }} />
        <span
          style={{
            writingMode: 'vertical-rl',
            textOrientation: 'upright',
            letterSpacing: 1,
            whiteSpace: 'nowrap',
          }}
        >
          {t('chatPlayers')}
        </span>
        {onlineCount > 0 && (
          <span
            style={{
              minWidth: 16,
              height: 16,
              padding: '0 4px',
              borderRadius: 8,
              background: 'var(--success)',
              color: '#fff',
              fontSize: '0.6rem',
              lineHeight: '16px',
              textAlign: 'center',
            }}
          >
            {onlineCount}
          </span>
        )}
      </button>
    </Tooltip>
  ) : null;

  return (
    <PageShell title={t('chat')}>
      <Row gutter={[12, 12]} style={{ height: 'calc(100vh - 120px)' }}>
        <Col
          xs={24}
          sm={playerListCollapsed ? 24 : 16}
          style={{
            display: 'flex',
            flexDirection: 'column',
            transition: 'all 0.2s',
            position: 'relative',
          }}
        >
          {playerListTab}
          <Card
            title={
              <Space>
                {t('chatMessages')}
                <Tag>{messages.length}</Tag>
                {playerListCollapsed && (
                  <Tooltip title={t('chatExpandPlayers')}>
                    <Button
                      size="small"
                      icon={<MenuUnfoldOutlined />}
                      onClick={() => setDrawerOpen(true)}
                      aria-label={t('chatExpandPlayers')}
                    >
                      {t('chatPlayers')} ({onlineCount}/{players.length})
                    </Button>
                  </Tooltip>
                )}
              </Space>
            }
            extra={messageCardExtra}
            style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
            styles={{
              body: { flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' },
            }}
          >
            <div
              ref={listRef}
              style={{ flex: 1, overflowY: 'auto', padding: '4px 0' }}
              aria-live="polite"
            >
              {loading ? (
                <div style={{ textAlign: 'center', padding: 40 }}>
                  <Spin tip={t('chatLoading')} />
                </div>
              ) : messages.length === 0 ? (
                <Empty description={t('chatNoMessages')} />
              ) : (
                messages.map(renderMessage)
              )}
            </div>
            <div
              style={{ borderTop: '1px solid var(--border)', paddingTop: 8, marginTop: 8 }}
            >
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder={t('chatInputPlaceholder')}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onPressEnter={sendMessage}
                  disabled={sending}
                  aria-label={t('chatInputPlaceholder')}
                />
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  onClick={sendMessage}
                  loading={sending}
                >
                  {t('chatSend')}
                </Button>
              </Space.Compact>
            </div>
          </Card>
        </Col>

        {!playerListCollapsed && (
          <Col xs={24} sm={8} style={{ display: 'flex', flexDirection: 'column' }}>
            <Card
              title={
                <Space>
                  {t('chatPlayers')}
                  <Tag color="success">
                    {onlineCount} {t('chatOnline')}
                  </Tag>
                </Space>
              }
              extra={playerListExtra}
              style={{ height: '100%', overflow: 'auto' }}
            >
              {renderPlayerList()}
            </Card>
          </Col>
        )}
      </Row>

      {/* 折叠后的玩家列表 Drawer */}
      <Drawer
        title={
          <Space>
            {t('chatPlayerList')}
            <Tag color="success">
              {onlineCount} {t('chatOnline')}
            </Tag>
          </Space>
        }
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={320}
        className="chat-player-drawer"
        extra={
          <Space size="small">
            <Tooltip title={t('chatShowAvatars')}>
              <Switch
                checked={showAvatars}
                onChange={setShowAvatars}
                size="small"
                aria-label={t('chatShowAvatars')}
              />
            </Tooltip>
            <Tooltip title={t('chatShowPlayerInfo')}>
              <Switch
                checked={showPlayerInfo}
                onChange={setShowPlayerInfo}
                size="small"
                aria-label={t('chatShowPlayerInfo')}
              />
            </Tooltip>
            <Tooltip title={t('chatExpandPlayers')}>
              <Button
                size="small"
                icon={<MenuUnfoldOutlined />}
                onClick={() => {
                  setPlayerListCollapsed(false);
                  setDrawerOpen(false);
                }}
                aria-label={t('chatExpandPlayers')}
              />
            </Tooltip>
          </Space>
        }
      >
        {renderPlayerList()}
      </Drawer>
    </PageShell>
  );
}
