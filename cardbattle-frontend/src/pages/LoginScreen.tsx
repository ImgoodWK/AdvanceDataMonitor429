export function LoginScreen(props: {
  tokenInput: string;
  onTokenChange: (v: string) => void;
  onLogin: () => void;
}) {
  return (
    <div className="panel">
      <h2>登录</h2>
      <p className="muted">
        默认可用本机 Token「local」（仅 127.0.0.1）。也可粘贴 WebAE Bearer Token。
      </p>
      <div className="row" style={{ marginTop: '0.75rem' }}>
        <input
          className="token-input"
          style={{ flex: 1, minWidth: 240 }}
          value={props.tokenInput}
          onChange={(e) => props.onTokenChange(e.target.value)}
          placeholder="粘贴 WebAE token"
        />
        <button onClick={props.onLogin}>进入</button>
      </div>
    </div>
  );
}
