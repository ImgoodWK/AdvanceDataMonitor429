import { useEffect, useState } from 'react';
import { client, type AccountBinding, type AccountUser } from '../api/client';

export function AdminScreen(props: {
  busy: boolean;
  setBusy: (v: boolean) => void;
  setError: (v: string) => void;
  onBack: () => void;
}) {
  const [users, setUsers] = useState<AccountUser[]>([]);
  const [bindings, setBindings] = useState<AccountBinding[]>([]);
  const [forceUserId, setForceUserId] = useState('');
  const [forceUuid, setForceUuid] = useState('');
  const [forceName, setForceName] = useState('');
  const [resetId, setResetId] = useState('');
  const [resetPass, setResetPass] = useState('');

  async function reload() {
    const [u, b] = await Promise.all([client.adminUsers(), client.adminBindings()]);
    setUsers(u.users);
    setBindings(b.bindings);
  }

  useEffect(() => {
    reload().catch((e) => props.setError(e instanceof Error ? e.message : String(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function run(fn: () => Promise<void>) {
    props.setBusy(true);
    props.setError('');
    try {
      await fn();
      await reload();
    } catch (e) {
      props.setError(e instanceof Error ? e.message : String(e));
    } finally {
      props.setBusy(false);
    }
  }

  return (
    <div className="panel">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>管理后台</h2>
        <button type="button" className="secondary" onClick={props.onBack}>
          返回
        </button>
      </div>

      <h3>用户</h3>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>用户名</th>
              <th>显示名</th>
              <th>角色</th>
              <th>禁用</th>
              <th>MC</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.username}</td>
                <td>{u.displayName}</td>
                <td>{u.role}</td>
                <td>{u.disabled ? '是' : '否'}</td>
                <td>{u.mcName || '—'}</td>
                <td className="row">
                  {u.role !== 'superadmin' && (
                    <>
                      <button
                        type="button"
                        className="secondary"
                        disabled={props.busy}
                        onClick={() =>
                          run(async () => {
                            await client.adminPatchUser(u.id, {
                              role: u.role === 'admin' ? 'user' : 'admin',
                            });
                          })
                        }
                      >
                        {u.role === 'admin' ? '降为用户' : '提权管理'}
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        disabled={props.busy}
                        onClick={() =>
                          run(async () => {
                            await client.adminPatchUser(u.id, { disabled: !u.disabled });
                          })
                        }
                      >
                        {u.disabled ? '启用' : '禁用'}
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 style={{ marginTop: '1rem' }}>绑定关系</h3>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>账号</th>
              <th>角色名</th>
              <th>UUID</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {bindings.map((b) => (
              <tr key={b.userId}>
                <td>
                  {b.displayName} (@{b.username})
                </td>
                <td>{b.mcName}</td>
                <td>
                  <code>{b.mcUuid}</code>
                </td>
                <td>
                  <button
                    type="button"
                    className="secondary"
                    disabled={props.busy}
                    onClick={() =>
                      run(async () => {
                        await client.adminUnbind(b.userId);
                      })
                    }
                  >
                    解绑
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 style={{ marginTop: '1rem' }}>强制换绑</h3>
      <div className="stack" style={{ gap: '0.5rem' }}>
        <select value={forceUserId} onChange={(e) => setForceUserId(e.target.value)}>
          <option value="">选择账号</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.username}
            </option>
          ))}
        </select>
        <input placeholder="MC UUID" value={forceUuid} onChange={(e) => setForceUuid(e.target.value)} />
        <input placeholder="MC 名称" value={forceName} onChange={(e) => setForceName(e.target.value)} />
        <button
          disabled={props.busy || !forceUserId || !forceUuid}
          onClick={() =>
            run(async () => {
              await client.adminForceBind(forceUserId, { mcUuid: forceUuid, mcName: forceName });
            })
          }
        >
          换绑 / 强制绑定
        </button>
      </div>

      <h3 style={{ marginTop: '1rem' }}>重置密码</h3>
      <div className="stack" style={{ gap: '0.5rem' }}>
        <select value={resetId} onChange={(e) => setResetId(e.target.value)}>
          <option value="">选择账号</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.username}
            </option>
          ))}
        </select>
        <input
          type="password"
          placeholder="新密码"
          value={resetPass}
          onChange={(e) => setResetPass(e.target.value)}
        />
        <button
          disabled={props.busy || !resetId || resetPass.length < 8}
          onClick={() =>
            run(async () => {
              await client.adminResetPassword(resetId, resetPass);
              setResetPass('');
            })
          }
        >
          重置
        </button>
      </div>
    </div>
  );
}
