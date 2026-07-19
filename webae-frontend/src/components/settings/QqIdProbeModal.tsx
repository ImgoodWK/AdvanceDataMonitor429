import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, List, Modal, Space, Tag, Typography } from 'antd';
import { getApiClient } from '@/api/client';
import { useI18n } from '@/i18n';
import type { AlertNotificationTargetDto, QqIdDiscoveryDto, QqIdProbeStatusResponse } from '@/types/dto';

const { Paragraph, Text } = Typography;

interface QqIdProbeModalProps {
  open: boolean;
  target: AlertNotificationTargetDto | null;
  canEdit: boolean;
  onClose: () => void;
  onApply: (kind: 'c2c' | 'group' | 'channel', targetId: string) => void;
}

function isMaskedSecret(value: string | undefined): boolean {
  return !!value && value.startsWith('***');
}

export function QqIdProbeModal({ open, target, canEdit, onClose, onApply }: QqIdProbeModalProps) {
  const { t } = useI18n();
  const [status, setStatus] = useState<QqIdProbeStatusResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const pollRef = useRef<number | null>(null);

  const clearPoll = useCallback(() => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const refreshStatus = useCallback(async () => {
    try {
      const response = await getApiClient().get<QqIdProbeStatusResponse>('/api/alerts/qq-id-probe');
      setStatus(response);
      setLocalError(null);
      if (!response.running) {
        clearPoll();
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : t('alertsQqProbeStatusFailed');
      setLocalError(message);
    }
  }, [clearPoll, t]);

  useEffect(() => {
    if (!open) {
      clearPoll();
      setStatus(null);
      setLocalError(null);
      setBusy(false);
      return;
    }
    void refreshStatus();
    return () => clearPoll();
  }, [open, clearPoll, refreshStatus]);

  const startPolling = useCallback(() => {
    clearPoll();
    pollRef.current = window.setInterval(() => {
      void refreshStatus();
    }, 2000);
  }, [clearPoll, refreshStatus]);

  const startProbe = async () => {
    if (!target || !canEdit) return;
    const appId = (target.appId ?? '').trim();
    const appSecret = (target.appSecret ?? '').trim();
    const hasSavedSecret = !!target.appSecretConfigured || isMaskedSecret(appSecret);
    if (!appId || (!appSecret && !hasSavedSecret)) {
      setLocalError(t('alertsQqProbeNeedCredentials'));
      return;
    }
    setBusy(true);
    setLocalError(null);
    try {
      const body: Record<string, string> = {
        appId,
        appSecret: isMaskedSecret(appSecret) ? '' : appSecret,
      };
      if ((target.baseUrl ?? '').trim()) body.baseUrl = target.baseUrl!.trim();
      if ((target.tokenUrl ?? '').trim()) body.tokenUrl = target.tokenUrl!.trim();
      if ((target.id ?? '').trim()) body.targetConfigId = target.id.trim();
      const response = await getApiClient().post<QqIdProbeStatusResponse>(
        '/api/alerts/qq-id-probe/start',
        body,
      );
      setStatus(response);
      startPolling();
    } catch (e) {
      const message = e instanceof Error ? e.message : t('alertsQqProbeStartFailed');
      setLocalError(message);
    } finally {
      setBusy(false);
    }
  };

  const stopProbe = async () => {
    setBusy(true);
    try {
      const response = await getApiClient().post<QqIdProbeStatusResponse>('/api/alerts/qq-id-probe/stop', {});
      setStatus(response);
      clearPoll();
    } catch (e) {
      const message = e instanceof Error ? e.message : t('alertsQqProbeStopFailed');
      setLocalError(message);
    } finally {
      setBusy(false);
    }
  };

  const discoveries: QqIdDiscoveryDto[] = status?.discoveries ?? [];
  const running = !!status?.running;
  const phaseKey = status?.phase ? `alertsQqProbePhase_${status.phase}` : '';
  const phaseTranslated = phaseKey ? t(phaseKey) : '';
  const phaseLabel = phaseKey && phaseTranslated !== phaseKey ? phaseTranslated : status?.phase || '';

  return (
    <Modal
      open={open}
      title={t('alertsQqProbeTitle')}
      onCancel={onClose}
      width={720}
      footer={
        <Space>
          <Button onClick={onClose}>{t('close')}</Button>
          {running ? (
            <Button danger loading={busy} onClick={() => void stopProbe()}>
              {t('alertsQqProbeStop')}
            </Button>
          ) : (
            <Button type="primary" loading={busy} disabled={!canEdit} onClick={() => void startProbe()}>
              {t('alertsQqProbeStart')}
            </Button>
          )}
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Paragraph style={{ marginBottom: 0 }}>{t('alertsQqProbeIntro')}</Paragraph>
        <Alert type="info" showIcon message={t('alertsQqProbeSteps')} />
        {(localError || status?.error) && (
          <Alert type="error" showIcon message={localError || status?.error} />
        )}
        <Space wrap>
          <Tag color={running ? 'processing' : 'default'}>
            {running ? t('alertsQqProbeRunning') : t('alertsQqProbeIdle')}
          </Tag>
          {phaseLabel ? <Tag>{phaseLabel}</Tag> : null}
          {status?.expiresAtMs ? (
            <Text type="secondary">
              {t('alertsQqProbeExpires', {
                time: new Date(status.expiresAtMs).toLocaleTimeString(),
              })}
            </Text>
          ) : null}
        </Space>
        <List
          size="small"
          bordered
          locale={{ emptyText: t('alertsQqProbeEmpty') }}
          dataSource={discoveries}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button
                  key="apply"
                  type="link"
                  disabled={!canEdit}
                  onClick={() => onApply(item.kind, item.targetId)}
                >
                  {t('alertsQqProbeApply')}
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space wrap>
                    <Tag color="blue">{t(`alertsQqTargetType_${item.kind}`)}</Tag>
                    <Text code copyable>
                      {item.targetId}
                    </Text>
                  </Space>
                }
                description={
                  <Space direction="vertical" size={0}>
                    <Text type="secondary">{item.eventType}</Text>
                    {item.preview ? <Text type="secondary">{item.preview}</Text> : null}
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Space>
    </Modal>
  );
}
