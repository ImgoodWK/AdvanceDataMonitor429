import { useState } from 'react';

type Mode = 'login' | 'register' | 'token';

export function LoginScreen(props: {
  mode: Mode;
  onMode: (m: Mode) => void;
  username: string;
  password: string;
  displayName: string;
  tokenInput: string;
  busy: boolean;
  onUsername: (v: string) => void;
  onPassword: (v: string) => void;
  onDisplayName: (v: string) => void;
  onTokenChange: (v: string) => void;
  onAccountSubmit: () => void;
  onTokenLogin: () => void;
}) {
  const [showPass, setShowPass] = useState(false);
  return (
    <div className="panel">
      <h2>卡牌对战</h2>
      <div className="row" style={{ marginBottom: '0.75rem' }}>
        {(
          [
            ['login', '登录'],
            ['register', '注册'],
            ['token', 'Token'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            className={props.mode === id ? undefined : 'secondary'}
            onClick={() => props.onMode(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {props.mode === 'token' ? (
        <>
          <p className="muted">
            高级：粘贴 WebAE Bearer，或本机开发 Token「dev」/「local」。已绑定 MC 的 WebAE Token 会自动对应卡牌账号。
          </p>
          <div className="row" style={{ marginTop: '0.75rem' }}>
            <input
              className="token-input"
              style={{ flex: 1, minWidth: 240 }}
              value={props.tokenInput}
              onChange={(e) => props.onTokenChange(e.target.value)}
              placeholder="粘贴 Bearer Token"
            />
            <button disabled={props.busy} onClick={props.onTokenLogin}>
              进入
            </button>
          </div>
        </>
      ) : (
        <>
          <p className="muted">
            {props.mode === 'register'
              ? '注册独立卡牌账号。即使 GTNH / WebAE 未开启也可登录；之后可在设置中绑定存档角色。'
              : '使用用户名与密码登录。不依赖 Minecraft 在线。'}
          </p>
          <div className="stack" style={{ marginTop: '0.75rem', gap: '0.5rem' }}>
            <input
              value={props.username}
              onChange={(e) => props.onUsername(e.target.value)}
              placeholder="用户名（字母数字下划线）"
              autoComplete="username"
            />
            {props.mode === 'register' && (
              <input
                value={props.displayName}
                onChange={(e) => props.onDisplayName(e.target.value)}
                placeholder="显示名（可选）"
              />
            )}
            <div className="row">
              <input
                style={{ flex: 1 }}
                type={showPass ? 'text' : 'password'}
                value={props.password}
                onChange={(e) => props.onPassword(e.target.value)}
                placeholder="密码（至少 8 位）"
                autoComplete={props.mode === 'register' ? 'new-password' : 'current-password'}
              />
              <button type="button" className="secondary" onClick={() => setShowPass((v) => !v)}>
                {showPass ? '隐藏' : '显示'}
              </button>
            </div>
            <button disabled={props.busy} onClick={props.onAccountSubmit}>
              {props.mode === 'register' ? '注册并进入' : '登录'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
