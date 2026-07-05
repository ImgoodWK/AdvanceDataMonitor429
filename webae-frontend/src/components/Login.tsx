import { useEffect, useState } from 'react';

import { Card, Input, Button, Checkbox, Typography, Alert, Space } from 'antd';

import { LockOutlined, LinkOutlined } from '@ant-design/icons';

import { useAppContext } from '@/context/AppContext';

import { useI18n } from '@/i18n';



const { Title, Text } = Typography;



export function Login() {

  const { login, autoLogin, setAutoLogin, authError, lang, token } = useAppContext();

  const { t } = useI18n();

  const [tokenInput, setTokenInput] = useState('');

  const [connecting, setConnecting] = useState(false);



  useEffect(() => {

    if (token) setTokenInput(token);

  }, [token]);



  const handleConnect = async () => {

    if (!tokenInput.trim()) return;

    setConnecting(true);

    await login(tokenInput.trim());

    setConnecting(false);

  };



  const errorMap: Record<string, string> = {

    missing_token: t('tokenInvalid'),

    invalid_token: t('tokenInvalid'),

    token_expired: t('tokenExpired'),

    auth_failed: t('authFailed'),

    empty_token: t('tokenInvalid'),

  };

  const errorMsg = authError ? errorMap[authError] || t('authFailed') : null;

  const hasSavedToken = !!tokenInput.trim();



  return (

    <div

      style={{

        minHeight: '100vh',

        display: 'flex',

        alignItems: 'center',

        justifyContent: 'center',

        background: 'var(--bg-primary)',

        padding: '20px',

      }}

    >

      <Card

        style={{

          maxWidth: 440,

          width: '100%',

          background: 'var(--bg-card)',

          borderColor: 'var(--border)',

        }}

      >

        <Space direction="vertical" size="large" style={{ width: '100%' }}>

          <div style={{ textAlign: 'center' }}>

            <Title level={3} style={{ color: 'var(--accent)', marginBottom: 4 }}>

              TeXTech WebAE

            </Title>

            <Text type="secondary">{lang === 'zh' ? 'AE2 网络控制台' : 'AE2 Network Console'}</Text>

          </div>



          <Alert

            type="info"

            showIcon

            message={

              <span

                dangerouslySetInnerHTML={{

                  __html: t('loginHintHtml'),

                }}

              />

            }

            style={{

              background: 'var(--accent-dim)',

              borderColor: 'var(--accent)',

            }}

          />



          <Input.Password

            size="large"

            placeholder={t('tokenPlaceholder')}

            prefix={<LockOutlined style={{ color: 'var(--text-dim)' }} />}

            value={tokenInput}

            onChange={(e) => setTokenInput(e.target.value)}

            onPressEnter={handleConnect}

            aria-label={t('tokenPlaceholder')}

            disabled={connecting}

          />



          {errorMsg && (

            <Alert

              type="error"

              message={errorMsg}

              description={t('reissueHint')}

              showIcon

              closable

            />

          )}



          <Button

            type="primary"

            size="large"

            block

            icon={<LinkOutlined />}

            loading={connecting}

            onClick={handleConnect}

            disabled={!tokenInput.trim()}

          >

            {connecting ? t('connecting') : hasSavedToken ? t('reconnect') : t('connect')}

          </Button>



          <Checkbox

            checked={autoLogin}

            onChange={(e) => setAutoLogin(e.target.checked)}

          >

            {t('autoLogin')}

          </Checkbox>

        </Space>

      </Card>

    </div>

  );

}

