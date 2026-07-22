import { useEffect, useMemo, useState } from 'react';
import {
  clearToken,
  client,
  getToken,
  setToken,
  type BattleState,
  type CardDef,
  type RunState,
} from '../api/client';
import {
  addLifetimeVictories,
  getLifetimeVictories,
  getSelectedSkinId,
  isSkinUnlocked,
  resolveSkin,
  setSelectedSkinId,
} from '../lib/skins';
import { BattleScreen } from './BattleScreen';
import { LobbyScreen } from './LobbyScreen';
import { LoginScreen } from './LoginScreen';

type Page = 'login' | 'lobby' | 'battle';

export function App() {
  const [page, setPage] = useState<Page>(getToken() ? 'lobby' : 'login');
  const [tokenInput, setTokenInput] = useState(getToken() ?? '');
  const [name, setName] = useState('');
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

  const cardMap = useMemo(() => new Map(cards.map((c) => [c.id, c])), [cards]);
  const skin = resolveSkin(skinId);

  useEffect(() => {
    document.documentElement.style.setProperty('--skin-tint', skin.frameTint);
  }, [skin.frameTint]);

  useEffect(() => {
    client.meta().then(setMeta).catch(() => undefined);
    client
      .cards()
      .then((r) => setCards(r.cards))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    const existing = getToken();
    if (existing) {
      client
        .me()
        .then((m) => {
          setName(m.actorName);
          setPage('lobby');
        })
        .catch(() => {
          clearToken();
          setToken('local');
          setTokenInput('local');
          client
            .me()
            .then((m) => {
              setName(m.actorName);
              setPage('lobby');
            })
            .catch(() => setPage('login'));
        });
      return;
    }
    setToken('local');
    setTokenInput('local');
    client
      .me()
      .then((m) => {
        setName(m.actorName);
        setPage('lobby');
      })
      .catch(() => setPage('login'));
  }, []);

  async function login() {
    setError('');
    setToken(tokenInput.trim());
    try {
      const me = await client.me();
      setName(me.actorName);
      setPage('lobby');
    } catch (e) {
      clearToken();
      setError((e as Error).message);
    }
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
      const stage = await client.beginStage(r.runId);
      setRun(stage.run);
      setMatchId(stage.matchId);
      setMatch(stage.match);
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
      if (claimed.run.completed) {
        setPage('lobby');
        setMatch(null);
        setMatchId(null);
        return;
      }
      const stage = await client.beginStage(claimed.run.runId, choiceId);
      setRun(stage.run);
      setMatchId(stage.matchId);
      setMatch(stage.match);
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
    <div className="app-shell">
      <div className="topbar">
        <div className="brand">TeXTech Card Battle</div>
        <div className="row">
          {name && <span className="tag">{name}</span>}
          <span className="tag">{skin.nameZh}</span>
          {page !== 'login' && (
            <button
              className="secondary"
              onClick={() => {
                clearToken();
                setPage('login');
              }}
            >
              退出
            </button>
          )}
        </div>
      </div>

      {error && <div className="panel error">{error}</div>}

      {page === 'login' && (
        <LoginScreen tokenInput={tokenInput} onTokenChange={setTokenInput} onLogin={() => void login()} />
      )}

      {page === 'lobby' && meta && (
        <LobbyScreen
          meta={meta}
          themes={themes}
          voltage={voltage}
          equipmentIds={equipmentIds}
          skinId={skinId}
          victories={victories}
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
          }}
        />
      )}
    </div>
  );
}
