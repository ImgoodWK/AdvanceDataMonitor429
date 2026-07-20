import { useCallback, useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Select,
  Button,
  Tooltip,
  Segmented,
  Badge,
  Space,
  Input,
  Modal,
} from 'antd';
import {
  ReloadOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ThunderboltOutlined,
  SettingOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { useAppContext, type PageId } from '@/context/AppContext';
import { useI18n } from '@/i18n';
import {
  formatNetworkOptionLabel,
  isNetworkHealthy,
} from '@/utils/networkHealth';
import type { NavPageEntry } from './navConfig';

const PRESENT_CLASS = 'webae-dashboard-present';

function setPresentClass(on: boolean) {
  document.documentElement.classList.toggle(PRESENT_CLASS, on);
  document.body.classList.toggle(PRESENT_CLASS, on);
}

interface TopBarProps {
  pages?: NavPageEntry[];
  activePage: PageId;
  setActivePage: (p: PageId) => void;
  topnavMode?: boolean;
}

export function TopBar({ pages, activePage, setActivePage, topnavMode }: TopBarProps) {
  const {
    networks,
    selectedNetworks,
    setSelectedNetworks,
    online,
    refreshCountdown,
    autoRefresh,
    setAutoRefresh,
    refreshPaused,
    triggerRefresh,
    refreshTick,
    lang,
    setLang,
    presets,
    applyPreset,
    savePreset,
    serverConfig,
    isAdmin,
    isOnlineOp,
    notify,
  } = useAppContext();
  const { t } = useI18n();
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [savePresetOpen, setSavePresetOpen] = useState(false);
  const [presetName, setPresetName] = useState('');

  const maxNetworks = serverConfig?.maxNetworksDisplayed ?? 5;
  const dashboardPresent = activePage === 'dashboard';

  const exitFullscreen = useCallback(() => {
    setPresentClass(false);
    if (document.fullscreenElement) {
      document.exitFullscreen?.().catch(() => {});
    }
    setIsFullscreen(false);
  }, []);

  const handleFullscreen = useCallback(() => {
    if (document.fullscreenElement) {
      exitFullscreen();
      return;
    }
    if (dashboardPresent) {
      setPresentClass(true);
    }
    document.documentElement.requestFullscreen?.().catch(() => {
      // Still keep present chrome-hide even if Fullscreen API is denied.
      if (dashboardPresent) setIsFullscreen(true);
    });
    setIsFullscreen(true);
  }, [dashboardPresent, exitFullscreen]);

  useEffect(() => {
    const onFsChange = () => {
      const fs = !!document.fullscreenElement;
      if (!fs) {
        setPresentClass(false);
        setIsFullscreen(false);
      } else {
        setIsFullscreen(true);
      }
    };
    document.addEventListener('fullscreenchange', onFsChange);
    return () => {
      document.removeEventListener('fullscreenchange', onFsChange);
      setPresentClass(false);
    };
  }, []);

  // Leaving the dashboard page must drop present chrome-hide.
  useEffect(() => {
    if (!dashboardPresent && document.documentElement.classList.contains(PRESENT_CLASS)) {
      exitFullscreen();
    }
  }, [dashboardPresent, exitFullscreen]);

  const refreshStatusText = () => {
    if (autoRefresh) return t('auto');
    return t('idle');
  };

  const presetOptions = presets.map((p) => ({
    label: p.name,
    value: p.id,
  }));

  const handleSavePreset = () => {
    if (presetName.trim()) {
      savePreset(presetName.trim());
      setPresetName('');
      setSavePresetOpen(false);
    }
  };

  // In topnav mode, show horizontal page navigation
  const visiblePages = pages?.filter((item) => {
    if (item.id === 'admin') return isAdmin || isOnlineOp;
    return true;
  });

  const navButtons = topnavMode && visiblePages ? (
    <Space size="small" wrap>
      {visiblePages.map((item) => (
        <Button
          key={item.id}
          type={activePage === item.id ? 'primary' : 'text'}
          size="small"
          onClick={() => setActivePage(item.id)}
        >
          {t(item.labelKey)}
        </Button>
      ))}
    </Space>
  ) : null;

  return (
    <div
      className="webae-topbar"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '8px 16px',
        flexWrap: 'wrap',
        background: 'var(--bg-secondary)',
        borderBottom: '1px solid var(--border)',
      }}
    >
      {navButtons}

      {/* Network selector */}
      <Select
        mode="multiple"
        maxTagCount="responsive"
        style={{ minWidth: 200, maxWidth: 300 }}
        placeholder={t('selectNetwork')}
        value={selectedNetworks}
        onChange={(vals: number[]) => {
          if (vals.length <= maxNetworks) {
            setSelectedNetworks(vals);
          } else {
            notify(t('maxNetworksReached'), 'warning');
          }
        }}
        options={networks.map((n) => ({
          label: formatNetworkOptionLabel(n, t('networkUnavailable')),
          value: n.networkId,
          disabled: !isNetworkHealthy(n),
        }))}
        optionRender={(opt) => {
          const net = networks.find((n) => n.networkId === opt.value);
          const unhealthy = net != null && !isNetworkHealthy(net);
          return (
            <span
              style={
                unhealthy
                  ? { color: 'var(--text-secondary)', opacity: 0.65 }
                  : undefined
              }
            >
              {opt.label}
            </span>
          );
        }}
        aria-label={t('selectNetwork')}
        size="middle"
      />

      {/* Refresh — icon-only button; countdown in separate aria-live span */}
      <Tooltip title={t('refresh')}>
        <Button
          icon={<ReloadOutlined spin={refreshTick > 0} />}
          onClick={triggerRefresh}
          aria-label={t('refresh')}
          style={{ minWidth: 32 }}
        />
      </Tooltip>

      <Tooltip title={t('auto')}>
        <Button
          type={autoRefresh ? 'primary' : 'default'}
          icon={<ThunderboltOutlined />}
          onClick={() => setAutoRefresh(!autoRefresh)}
          aria-label={t('auto') + ': ' + refreshStatusText()}
          aria-pressed={autoRefresh}
          style={{ minWidth: 32 }}
        />
      </Tooltip>

      {autoRefresh && refreshPaused && (
        <span
          aria-live="polite"
          style={{
            fontSize: '0.75rem',
            color: 'var(--text-secondary)',
            minWidth: 28,
            textAlign: 'center',
          }}
        >
          {t('refreshPaused')}
        </span>
      )}

      {autoRefresh && !refreshPaused && refreshCountdown > 0 && (
        <span
          aria-live="polite"
          style={{
            fontSize: '0.75rem',
            color: 'var(--text-secondary)',
            minWidth: 28,
            textAlign: 'center',
          }}
        >
          {refreshCountdown}s
        </span>
      )}

      <div style={{ flex: 1 }} />

      {/* Preset selector */}
      {presetOptions.length > 0 && (
        <Select
          size="small"
          style={{ minWidth: 120 }}
          placeholder={t('preset')}
          options={presetOptions}
          onChange={(id: string) => applyPreset(id)}
          aria-label={t('preset')}
          suffixIcon={<SettingOutlined />}
        />
      )}

      {/* Save preset button */}
      <Tooltip title={t('presetSave')}>
        <Button
          size="small"
          icon={<SaveOutlined />}
          onClick={() => setSavePresetOpen(true)}
          aria-label={t('presetSave')}
        />
      </Tooltip>

      {/* Language switch */}
      <Segmented
        size="small"
        options={[
          { label: '中', value: 'zh' },
          { label: 'EN', value: 'en' },
        ]}
        value={lang}
        onChange={(v) => setLang(v as 'zh' | 'en')}
        aria-label={t('language')}
      />

      {/* Connection status badge (aria-live) */}
      <Badge
        status={online ? 'success' : 'error'}
        text={
          <span style={{ fontSize: '0.75rem', color: online ? 'var(--success)' : 'var(--danger)' }}>
            {online ? t('online') : t('offline')}
          </span>
        }
      />

      {/* Fullscreen — on dashboard: presentation mode (grid only) */}
      <Tooltip title={dashboardPresent ? t('fullscreenDashboardHint') : t('fullscreen')}>
        <Button
          size="small"
          icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
          onClick={handleFullscreen}
          aria-label={dashboardPresent ? t('fullscreenDashboard') : t('fullscreen')}
        />
      </Tooltip>

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

      {/* Exit control stays visible while chrome is hidden in present mode */}
      {isFullscreen &&
        dashboardPresent &&
        createPortal(
          <Button
            className="webae-present-exit"
            type="primary"
            size="small"
            icon={<FullscreenExitOutlined />}
            onClick={exitFullscreen}
            aria-label={t('fullscreenExit')}
          >
            {t('fullscreenExit')}
          </Button>,
          document.body
        )}
    </div>
  );
}
