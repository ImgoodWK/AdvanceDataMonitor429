import { ConfigProvider, App as AntdApp, notification } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { AppProvider, useAppContext } from '@/context/AppContext';
import { buildAntdThemeSync } from '@/theme/antdTheme';
import { Login } from '@/components/Login';
import { AppLayout } from '@/components/Layout/AppLayout';
import { useEffect, useRef } from 'react';
import { useIconPackAutoSync } from '@/hooks/useIconPackAutoSync';

function Inner() {
  const { isLoggedIn, lang, themeColor, themeLayout, notify, online } = useAppContext();
  useIconPackAutoSync();
  const compact = themeLayout === 'compact';
  const theme = buildAntdThemeSync(themeColor, compact);
  const locale = lang === 'zh' ? zhCN : enUS;

  // aria-live region for connection status (WCAG)
  const liveRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (liveRef.current) {
      liveRef.current.textContent = online ? 'connected' : 'disconnected';
    }
  }, [online]);

  return (
    <ConfigProvider theme={theme} locale={locale}>
      <AntdApp>
        {/* Skip link for keyboard users (WCAG 2.4.1) */}
        <a href="#main-content" className="skip-link" style={{ position: 'absolute', left: -9999, top: 0, zIndex: 10000 }}>
          {lang === 'zh' ? '跳转到主内容' : 'Skip to content'}
        </a>
        {/* aria-live region for screen readers */}
        <div ref={liveRef} aria-live="polite" style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }} />
        {isLoggedIn ? <AppLayout /> : <Login />}
      </AntdApp>
    </ConfigProvider>
  );
}

export function App() {
  return (
    <AppProvider>
      <Inner />
    </AppProvider>
  );
}
