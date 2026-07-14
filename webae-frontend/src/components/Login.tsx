import { useEffect, useRef, useState } from 'react';

import { Card, Input, Button, Checkbox, Typography, Alert, Space, Divider } from 'antd';

import { LockOutlined, LinkOutlined, NumberOutlined } from '@ant-design/icons';

import { useAppContext } from '@/context/AppContext';

import { useI18n } from '@/i18n';



const { Title, Text } = Typography;



export function Login() {

  const { login, exchangeLoginCode, autoLogin, setAutoLogin, authError, lang, token, isLoggedIn } = useAppContext();

  const { t } = useI18n();

  const [tokenInput, setTokenInput] = useState('');

  const [codeInput, setCodeInput] = useState('');

  const [connecting, setConnecting] = useState(false);

  const [exchanging, setExchanging] = useState(false);

  const autoCodeHandled = useRef(false);
  const autoTokenHandled = useRef(false);

  useEffect(() => {
    if (token) setTokenInput(token);
  }, [token]);

  useEffect(() => {
    if (autoTokenHandled.current || isLoggedIn) return;
    const params = new URLSearchParams(window.location.search);
    const urlToken = params.get('token');
    if (!urlToken || urlToken.trim().length < 8) return;
    autoTokenHandled.current = true;
    setTokenInput(urlToken.trim());
    setConnecting(true);
    void login(urlToken.trim()).then((ok) => {
      setConnecting(false);
      if (ok) {
        const url = new URL(window.location.href);
        url.searchParams.delete('token');
        window.history.replaceState({}, '', url.pathname + url.search + url.hash);
      }
    });
  }, [login, isLoggedIn]);

  useEffect(() => {

    if (autoCodeHandled.current || isLoggedIn) return;

    const params = new URLSearchParams(window.location.search);

    const code = params.get('code');

    if (!code || !/^\d{6}$/.test(code.trim())) return;

    autoCodeHandled.current = true;

    setCodeInput(code.trim());

    setExchanging(true);

    void exchangeLoginCode(code.trim()).then((ok) => {

      setExchanging(false);

      if (ok) {

        const url = new URL(window.location.href);

        url.searchParams.delete('code');

        window.history.replaceState({}, '', url.pathname + url.search + url.hash);

      }

    });

  }, [exchangeLoginCode, isLoggedIn]);



  const handleConnect = async () => {

    if (!tokenInput.trim()) return;

    setConnecting(true);

    await login(tokenInput.trim());

    setConnecting(false);

  };



  const handleExchange = async () => {

    if (!codeInput.trim()) return;

    setExchanging(true);

    await exchangeLoginCode(codeInput.trim());

    setExchanging(false);

  };



  const errorMap: Record<string, string> = {

    missing_token: t('tokenInvalid'),

    invalid_token: t('tokenInvalid'),

    token_expired: t('tokenExpired'),

    auth_failed: t('authFailed'),

    empty_token: t('tokenInvalid'),

    invalid_code: t('loginCodeInvalid'),

    invalid_or_used: t('loginCodeInvalid'),

    expired: t('loginCodeExpired'),

    missing_code: t('loginCodeInvalid'),

    no_monitor: t('loginCodeNoMonitor'),

    webae_disabled: t('webaeDisabled'),

  };

  const errorMsg = authError ? errorMap[authError] || t('authFailed') : null;

  const hasSavedToken = !!tokenInput.trim();

  const busy = connecting || exchanging;



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



          <Input

            size="large"

            placeholder={t('loginCodePlaceholder')}

            prefix={<NumberOutlined style={{ color: 'var(--text-dim)' }} />}

            value={codeInput}

            onChange={(e) => setCodeInput(e.target.value.replace(/\D/g, '').slice(0, 6))}

            onPressEnter={handleExchange}

            aria-label={t('loginCodePlaceholder')}

            disabled={busy}

            maxLength={6}

            inputMode="numeric"

          />



          <Button

            type="default"

            size="large"

            block

            loading={exchanging}

            onClick={handleExchange}

            disabled={codeInput.trim().length !== 6 || busy}

          >

            {exchanging ? t('connecting') : t('loginCodeExchange')}

          </Button>



          <Divider plain>{lang === 'zh' ? '或使用 Token' : 'Or use token'}</Divider>



          <Input.Password

            size="large"

            placeholder={t('tokenPlaceholder')}

            prefix={<LockOutlined style={{ color: 'var(--text-dim)' }} />}

            value={tokenInput}

            onChange={(e) => setTokenInput(e.target.value)}

            onPressEnter={handleConnect}

            aria-label={t('tokenPlaceholder')}

            disabled={busy}

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

            disabled={!tokenInput.trim() || busy}

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

