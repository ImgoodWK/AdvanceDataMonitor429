import { Alert, Button, Card, List, Modal, Space, Steps, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  ExportOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useI18n } from '@/i18n';
import type { AlertNotificationTargetType } from '@/types/dto';
import type { BrowserNotificationPermission } from '@/utils/alertChannels';

const { Paragraph, Text } = Typography;

type GuideRoute = AlertNotificationTargetType | 'webhook';

interface PlatformGuide {
  key: GuideRoute;
  url?: string;
}

const PLATFORM_GUIDES: PlatformGuide[] = [
  { key: 'wecom_bot', url: 'https://developer.work.weixin.qq.com/document/path/91770' },
  { key: 'qq_official', url: 'https://q.qq.com/' },
  { key: 'wechat_official', url: 'https://mp.weixin.qq.com/' },
  { key: 'email' },
  { key: 'wecom_app', url: 'https://work.weixin.qq.com/wework_admin/frame' },
  {
    key: 'webhook',
    url: 'https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks',
  },
];

interface AlertDeliveryGuideModalProps {
  open: boolean;
  canEdit: boolean;
  serverFeatureEnabled: boolean;
  builtInEnabledCount: number;
  browserPermission: BrowserNotificationPermission;
  quickEnabling: boolean;
  onClose: () => void;
  onQuickEnable: () => void;
  onAddRoute: (route: GuideRoute) => void;
}

export function AlertDeliveryGuideModal({
  open,
  canEdit,
  serverFeatureEnabled,
  builtInEnabledCount,
  browserPermission,
  quickEnabling,
  onClose,
  onQuickEnable,
  onAddRoute,
}: AlertDeliveryGuideModalProps) {
  const { t } = useI18n();

  return (
    <Modal
      open={open}
      width={860}
      title={t('alertsGuideTitle')}
      onCancel={onClose}
      footer={
        <Button type="primary" onClick={onClose}>
          {t('close')}
        </Button>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message={t('alertsGuideIntro')}
          description={t('alertsGuideSecretNote')}
        />

        <Steps
          size="small"
          responsive
          items={[
            { title: t('alertsGuideStep1'), description: t('alertsGuideStep1Desc') },
            { title: t('alertsGuideStep2'), description: t('alertsGuideStep2Desc') },
            { title: t('alertsGuideStep3'), description: t('alertsGuideStep3Desc') },
          ]}
        />

        <Card
          size="small"
          title={
            <Space>
              <ThunderboltOutlined />
              <span>{t('alertsQuickStartTitle')}</span>
            </Space>
          }
          extra={<Tag color={builtInEnabledCount === 3 ? 'success' : 'default'}>{t('alertsBuiltInStatus', { count: builtInEnabledCount })}</Tag>}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Paragraph style={{ marginBottom: 0 }}>{t('alertsQuickStartDesc')}</Paragraph>
            <Space wrap>
              <Tag icon={<CheckCircleOutlined />} color="blue">
                {t('alertsBrowserNotifications')}
              </Tag>
              <Tag icon={<CheckCircleOutlined />} color="cyan">
                {t('alertsPlayerChat')}
              </Tag>
              <Tag icon={<CheckCircleOutlined />} color="purple">
                {t('alertsPlayerHud')}
              </Tag>
              <Tag>{t(`alertsBrowserPermission_${browserPermission}`)}</Tag>
            </Space>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={quickEnabling}
              disabled={!canEdit || !serverFeatureEnabled}
              onClick={onQuickEnable}
            >
              {t('alertsQuickEnable')}
            </Button>
            {!serverFeatureEnabled && <Text type="warning">{t('alertsGuideServerSwitch')}</Text>}
            {!canEdit && <Text type="secondary">{t('alertsReadOnlyHint')}</Text>}
          </Space>
        </Card>

        <div>
          <Text strong>{t('alertsGuidePlatformsTitle')}</Text>
          <Paragraph type="secondary">{t('alertsGuidePlatformsDesc')}</Paragraph>
          <List
            bordered
            size="small"
            dataSource={PLATFORM_GUIDES}
            renderItem={(item) => (
              <List.Item
                actions={[
                  ...(item.url
                    ? [
                        <Button
                          key="console"
                          type="link"
                          href={item.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          icon={<ExportOutlined />}
                        >
                          {t('alertsGuideOpenPlatform')}
                        </Button>,
                      ]
                    : []),
                  <Button
                    key="add"
                    size="small"
                    icon={<PlusOutlined />}
                    disabled={!canEdit}
                    onClick={() => onAddRoute(item.key)}
                  >
                    {t('alertsGuideConfigureNow')}
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={t(`alertsGuidePlatform_${item.key}`)}
                  description={
                    <Space direction="vertical" size={2}>
                      <Text>{t(`alertsGuidePlatform_${item.key}_where`)}</Text>
                      <Text type="secondary">{t(`alertsGuidePlatform_${item.key}_steps`)}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </div>

        <Alert type="warning" showIcon message={t('alertsGuidePlatformLimits')} />
      </Space>
    </Modal>
  );
}
