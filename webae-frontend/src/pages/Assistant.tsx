import { useCallback, useState } from 'react';
import { Alert, Button, Card, Input, Space, Spin, Typography } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import { PageShell } from '@/components/Layout/PageShell';
import { useI18n } from '@/i18n';
import type { WebAssistantResponse } from '@/types/dto';

const { TextArea } = Input;
const { Paragraph, Text } = Typography;

export function AssistantPage() {
  const { lang, notify } = useAppContext();
  const { t } = useI18n();
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<WebAssistantResponse | null>(null);

  const submit = useCallback(async () => {
    if (!text.trim()) return;
    setLoading(true);
    try {
      const data = await getApiClient().post<WebAssistantResponse>('/api/assistant/query', {
        text: text.trim(),
        locale: lang === 'zh' ? 'zh_CN' : 'en_US',
      });
      setResponse(data);
      if (!data.success && data.code === 'rate_limited') {
        notify(t('assistantRateLimited'), 'warning');
      }
    } catch (e) {
      notify((e as Error).message, 'error');
    } finally {
      setLoading(false);
    }
  }, [text, lang, notify, t]);

  return (
    <PageShell title={t('assistantPage')} description={t('assistantPageDesc')}>
      <Card>
        <Alert type="info" showIcon message={t('assistantWebHint')} style={{ marginBottom: 16 }} />
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <TextArea
            rows={3}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={t('assistantInputPlaceholder')}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                void submit();
              }
            }}
          />
          <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => void submit()}>
            {t('assistantSubmit')}
          </Button>
          <Spin spinning={loading}>
            {response && (
              <Card size="small" title={response.intentType || t('assistantResponse')}>
                {response.code && (
                  <Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                    {response.code}
                  </Text>
                )}
                <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{response.message}</Paragraph>
              </Card>
            )}
          </Spin>
        </Space>
      </Card>
    </PageShell>
  );
}
