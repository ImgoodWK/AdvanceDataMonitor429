import { useEffect, useMemo, useState } from 'react';
import {
  clearToken,
  client,
  getToken,
  setToken,
  type BattleState,
  type CardDef,
  type PendingReward,
  type RewardDeliveryStatus,
  type RunState,
} from '../api/client';
import {
  addLifetimeVictories,
  getLifetimeVictories,
  getSelectedSkinId,
  isSkinUnlocked,
  resolveSkin,
  setSelectedSkinId,
  unlockRewardSkins,
} from '../lib/skins';
import { AdminScreen } from './AdminScreen';
import { AdventureMapScreen } from './AdventureMapScreen';
import { BattleScreen } from './BattleScreen';
import { LobbyScreen } from './LobbyScreen';
import { LoginScreen } from './LoginScreen';
import { SettingsScreen } from './SettingsScreen';

type Page = 'login' | 'lobby' | 'map' | 'battle' | 'settings' | 'admin';
type LoginMode = 'login' | 'register' | 'token';
const ACTIVE_RUN_KEY = 'textech_cardbattle_active_run';
const ACTIVE_MATCH_KEY = 'textech_cardbattle_active_match';

export function App() {
  const [page, setPage] = useState<Page>(getToken() ? 'lobby' : 'login');
  const [loginMode, setLoginMode] = useState<LoginMode>('login');
  const [tokenInput, setTokenInput] = useState(getToken() ?? '');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [name, setName] = useState('');
  const [me, setMe] = useState<Awaited<ReturnType<typeof client.me>> | null>(null);
  const [error, setError] = useState('');
  const [meta, setMeta] = useState<Awaited<ReturnType<typeof client.meta>> | null>(null);
  const [cards, setCards] = useState<CardDef[]>([]);
  const [themes, setThemes] = useState<string[]>(['vanilla']);
  const [voltage, setVoltage] = useState('LV');
  const [equipmentIds, setEquipmentIds] = useState<string[]>([]);
  const [run, setRun] = useState<RunState | null>(null);
  const [matchId, setMatchId] = useState<string | null>(null);
  const [match, setMatch] = useState<BattleState | null>(null);
  const [busy, setBusy] = useState(false);
  const [skinId, setSkinId] = useState(getSelectedSkinId());
  const [victories, setVictories] = useState(getLifetimeVictories());
  const [pendingRewards, setPendingRewards] = useState<PendingReward[]>([]);
  const [rewardDelivery, setRewardDelivery] = useState<RewardDeliveryStatus | null>(null);

  const cardMap = useMemo(() => new Map(cards.map((c) => [c.id, c])), [cards]);
  const skin = resolveSkin(skinId);

  useEffect(() => {
    document.documentElement.style.setProperty('--skin-tint', skin.frameTint);
  }, [skin.frameTint]);

  useEffect(() => {
    client
      .health()
      .then((health) => setRewardDelivery(health.rewardDelivery))
      .catch(() => undefined);
    if (page !== 'login') {
      client
        .pendingRewards()
        .then((result) => setPendingRewards(result.entries))
        .catch(() => undefined);
    }
  }, [page]);

  useEffect(() => {
    client.meta().then(setMeta).catch(() => undefined);
    client
      .cards()
      .then((r) => setCards(r.cards))
      .catch(() => undefined);
  }, []);

  async function applyMe(m: Awaited<ReturnType<typeof client.me>>) {
    setMe(m);
    setName(m.actorName);
  }

  useEffect(() => {
    const existing = getToken();
    if (!existing) {
      setPage('login');
      return;
    }
    client
      .me()
      .then(async (m) => {
        await applyMe(m);
        if (!(await resumeProgress())) setPage('lobby');
      })
      .catch(() => {
        clearToken();
        setPage('login');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function enterWithToken(token: string) {
    setError('');
    setToken(token.trim());
    setTokenInput(token.trim());
    const m = await client.me();
    await applyMe(m);
    if (!(await resumeProgress())) setPage('lobby');
  }

  async function tokenLogin() {
    setBusy(true);
    try {
      await enterWithToken(tokenInput);
    } catch (e) {
      clearToken();
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function accountSubmit() {
    setBusy(true);
    setError('');
    try {
      const result =
        loginMode === 'register'
          ? await client.register({
              username,
              password,
              displayName: displayName || undefined,
            })
          : await client.login({ username, password });
      await enterWithToken(result.token);
      setPassword('');
    } catch (e) {
      clearToken();
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    try {
      await client.logout();
    } catch {
      /* ignore */
    }
    clearToken();
    setMe(null);
    setPendingRewards([]);
    localStorage.removeItem(ACTIVE_RUN_KEY);
    localStorage.removeItem(ACTIVE_MATCH_KEY);
    setPage('login');
  }

  function toggleTheme(t: string) {
    const max = meta?.themeSlotsByVoltage[voltage] ?? 1;
    setThemes((prev) => {
      if (prev.includes(t)) return prev.filter((x) => x !== t);
      if (prev.length >= max) return [...prev.slice(1), t];
      return [...prev, t];
    });
  }

  async function startAdventure() {
    setBusy(true);
    setError('');
    try {
      const { run: r } = await client.startRun({ themes, voltage, equipmentIds });
      setRun(r);
      localStorage.setItem(ACTIVE_RUN_KEY, r.runId);
      localStorage.removeItem(ACTIVE_MATCH_KEY);
      setPage('map');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function resumeProgress(): Promise<boolean> {
    const storedRunId = localStorage.getItem(ACTIVE_RUN_KEY);
    const storedMatchId = localStorage.getItem(ACTIVE_MATCH_KEY);
    let restoredRun: RunState | null = null;
    if (storedRunId) {
      try {
        restoredRun = (await client.getRun(storedRunId)).run;
        setRun(restoredRun);
      } catch {
        localStorage.removeItem(ACTIVE_RUN_KEY);
      }
    }
    if (storedMatchId) {
      try {
        const restoredMatch = (await client.getMatch(storedMatchId)).match;
        setMatchId(storedMatchId);
        setMatch(restoredMatch);
        setPage('battle');
        return true;
      } catch {
        localStorage.removeItem(ACTIVE_MATCH_KEY);
      }
    }
    if (restoredRun && !restoredRun.completed) {
      setPage('map');
      return true;
    }
    return false;
  }

  async function enterStage(stageId: string) {
    if (!run) return;
    setBusy(true);
    setError('');
    try {
      const stage = await client.beginStage(run.runId, stageId);
      setRun(stage.run);
      setMatchId(stage.matchId);
      setMatch(stage.match);
      localStorage.setItem(ACTIVE_MATCH_KEY, stage.matchId);
      setPage('battle');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function sendAction(action: unknown) {
    if (!matchId) return;
    setBusy(true);
    setError('');
    try {
      const res = await client.action(matchId, action, run?.runId);
      setMatch(res.match);
      if (res.run) setRun(res.run);
      if (res.match.phase === 'game_over' && res.match.winner === 0) {
        setVictories(addLifetimeVictories(1));
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function nextAfterWin(choiceId: string) {
    if (!run) return;
    setBusy(true);
    try {
      const claimed = await client.claimReward(run.runId, choiceId);
      setRun(claimed.run);
      if (claimed.unlockedSkinIds.length) unlockRewardSkins(claimed.unlockedSkinIds);
      client
        .pendingRewards()
        .then((pending) => setPendingRewards(pending.entries))
        .catch(() => undefined);
      if (claimed.run.completed) {
        localStorage.removeItem(ACTIVE_RUN_KEY);
        localStorage.removeItem(ACTIVE_MATCH_KEY);
        setPage('lobby');
        setMatch(null);
        setMatchId(null);
        return;
      }
      setMatch(null);
      setMatchId(null);
      localStorage.removeItem(ACTIVE_MATCH_KEY);
      setPage('map');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function selectSkin(id: string) {
    const skinDef = resolveSkin(id);
    if (!isSkinUnlocked(skinDef, victories)) return;
    setSelectedSkinId(id);
    setSkinId(id);
  }

  return (
    <div className={`app-shell${page === 'battle' ? ' in-battle' : ''}`}>
      <div className="topbar">
        <div className="brand">TeXTech Card Battle</div>
        <div className="row">
          {name && <span className="tag">{name}</span>}
          <span className="tag">{skin.nameZh}</span>
          {page !== 'login' && page !== 'battle' && (
            <button className="secondary" type="button" onClick={() => setPage('settings')}>
              设置
            </button>
          )}
          {page !== 'login' && (
            <button className="secondary" type="button" onClick={() => void logout()}>
              退出
            </button>
          )}
        </div>
      </div>

      {error && <div className="panel error">{error}</div>}

      {page === 'login' && (
        <LoginScreen
          mode={loginMode}
          onMode={setLoginMode}
          username={username}
          password={password}
          displayName={displayName}
          tokenInput={tokenInput}
          busy={busy}
          onUsername={setUsername}
          onPassword={setPassword}
          onDisplayName={setDisplayName}
          onTokenChange={setTokenInput}
          onAccountSubmit={() => void accountSubmit()}
          onTokenLogin={() => void tokenLogin()}
        />
      )}

      {page === 'settings' && me && (
        <SettingsScreen
          me={{
            username: me.username,
            displayName: me.actorName,
            role: me.role,
            accountId: me.accountId,
            binding: me.binding,
          }}
          busy={busy}
          onBack={() => setPage('lobby')}
          onOpenAdmin={() => setPage('admin')}
          onSaveDisplayName={async (value) => {
            setBusy(true);
            setError('');
            try {
              await client.updateProfile({ displayName: value });
              await applyMe(await client.me());
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
          onChangePassword={async (currentPassword, newPassword) => {
            setBusy(true);
            setError('');
            try {
              await client.changePassword({ currentPassword, newPassword });
              await logout();
              setError('密码已更新，请重新登录');
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
          onBind={async (code) => {
            setBusy(true);
            setError('');
            try {
              await client.bindMc(code);
              await applyMe(await client.me());
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
          onUnbind={async () => {
            setBusy(true);
            setError('');
            try {
              await client.unbindMc();
              await applyMe(await client.me());
            } catch (e) {
              setError((e as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        />
      )}

      {page === 'admin' && (
        <AdminScreen
          busy={busy}
          setBusy={setBusy}
          setError={setError}
          onBack={() => setPage('settings')}
        />
      )}

      {page === 'lobby' && meta && (
        <LobbyScreen
          meta={meta}
          themes={themes}
          voltage={voltage}
          equipmentIds={equipmentIds}
          skinId={skinId}
          victories={victories}
          pendingRewards={pendingRewards}
          rewardDelivery={rewardDelivery}
          busy={busy}
          onVoltage={setVoltage}
          onToggleTheme={toggleTheme}
          onToggleEquipment={(id) =>
            setEquipmentIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
          }
          onSelectSkin={selectSkin}
          onStart={() => void startAdventure()}
        />
      )}

      {page === 'map' && run && (
        <AdventureMapScreen
          run={run}
          busy={busy}
          onEnterStage={(stageId) => void enterStage(stageId)}
          onAbandon={() => {
            setRun(null);
            setMatch(null);
            setMatchId(null);
            localStorage.removeItem(ACTIVE_RUN_KEY);
            localStorage.removeItem(ACTIVE_MATCH_KEY);
            setPage('lobby');
          }}
        />
      )}

      {page === 'battle' && match && (
        <BattleScreen
          match={match}
          run={run}
          cardMap={cardMap}
          skinId={skinId}
          busy={busy}
          onAction={sendAction}
          onNextAfterWin={nextAfterWin}
          onBackLobby={() => {
            setPage('lobby');
            setMatch(null);
            setMatchId(null);
            localStorage.removeItem(ACTIVE_RUN_KEY);
            localStorage.removeItem(ACTIVE_MATCH_KEY);
          }}
        />
      )}
    </div>
  );
}
