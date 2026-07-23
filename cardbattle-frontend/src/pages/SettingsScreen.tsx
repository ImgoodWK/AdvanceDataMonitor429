import { useState } from 'react';
import type { AccountUser } from '../api/client';
import { COACH_STORAGE_KEY, isCoachEnabled, setCoachEnabled } from '../components/BattleCoach';
export function SettingsScreen(props: {
  me: {
    username: string | null;
    displayName: string;
    role: string | null;
    accountId: string | null;
    binding: { bound: boolean; mcUuid?: string | null; mcName?: string | null };
  };
  busy: boolean;
  onSaveDisplayName: (name: string) => Promise<void>;
  onChangePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  onBind: (code: string) => Promise<void>;
  onUnbind: () => Promise<void>;
  onBack: () => void;
  onOpenAdmin?: () => void;
}) {
  const [displayName, setDisplayName] = useState(props.me.displayName);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [bindCode, setBindCode] = useState('');
  const [coachEnabled, setCoachEnabledState] = useState(isCoachEnabled);
  const accountOk = Boolean(props.me.accountId);
  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>个人设置</h2>
        <button type="button" className="secondary" onClick={props.onBack}>
          返回大厅
        </button>
      </div>
      <p className="muted">
        {props.me.username ? `@${props.me.username}` : '（Token 登录，无卡牌账号）'}
        {props.me.role ? ` · ${props.me.role}` : ''}
      </p>

      {accountOk && (
        <>
          <h3>显示名</h3>
          <div className="row">
            <input style={{ flex: 1 }} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            <button disabled={props.busy} onClick={() => props.onSaveDisplayName(displayName)}>
              保存
            </button>
          </div>

          <h3 style={{ marginTop: '1rem' }}>修改密码</h3>
          <div className="stack" style={{ gap: '0.5rem' }}>
            <input
              type="password"
              placeholder="当前密码"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
            <input
              type="password"
              placeholder="新密码（至少 8 位）"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <button
              disabled={props.busy}
              onClick={() => props.onChangePassword(currentPassword, newPassword)}
            >
              更新密码
            </button>
          </div>
        </>
      )}

      <h3 style={{ marginTop: '1rem' }}>对战界面</h3>
      <label className="row" style={{ alignItems: 'center', gap: '0.65rem' }}>
        <input
          type="checkbox"
          checked={coachEnabled}
          onChange={(e) => {
            const next = e.target.checked;
            setCoachEnabledState(next);
            setCoachEnabled(next);
          }}
        />
        <span>对战操作提示</span>
      </label>
      <p className="muted">在对战界面顶部显示一行阶段操作提示（存于 {COACH_STORAGE_KEY}）。</p>

      <h3 style={{ marginTop: '1rem' }}>绑定 GTNH 角色</h3>
      <p className="muted">
        游戏内执行 <code>/textech card bind</code> 获取绑定码，填入下方。一账号仅绑一角色；WebAE
        未开启时仍可用账号密码登录。
      </p>
      {props.me.binding.bound ? (
        <div className="row">
          <span>
            已绑定：{props.me.binding.mcName} ({props.me.binding.mcUuid})
          </span>
          {accountOk && (
            <button className="secondary" disabled={props.busy} onClick={props.onUnbind}>
              解绑
            </button>
          )}
        </div>
      ) : accountOk ? (
        <div className="row">
          <input
            style={{ flex: 1 }}
            value={bindCode}
            onChange={(e) => setBindCode(e.target.value.toUpperCase())}
            placeholder="8 位绑定码"
          />
          <button disabled={props.busy} onClick={() => props.onBind(bindCode)}>
            绑定
          </button>
        </div>
      ) : (
        <p className="muted">请使用卡牌账号登录后再绑定。</p>
      )}

      {props.onOpenAdmin && (props.me.role === 'admin' || props.me.role === 'superadmin') && (
        <div className="row" style={{ marginTop: '1.25rem' }}>
          <button type="button" onClick={props.onOpenAdmin}>
            管理后台
          </button>
        </div>
      )}
    </div>
  );
}

export type { AccountUser };
