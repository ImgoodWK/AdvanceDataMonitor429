import express from 'express';
import cors from 'cors';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Minimal .env loader (no dependency). Does not override existing env. */
function loadDotEnv(): void {
  const envPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    if (process.env[key] === undefined) process.env[key] = val;
  }
}

loadDotEnv();

import { authStatus, validateBearer } from './auth/validate.js';
import { CARD_CATALOG } from './data/catalog.js';
import { THEME_SLOTS_BY_VOLTAGE, VOLTAGE_ORDER } from './battle/types.js';
import type { ThemeId, VoltageTier } from './battle/types.js';
import {
  actOnMatch,
  beginStage,
  claimStageReward,
  finishMatchIfWon,
  getRun,
  listEquipment,
  listThemes,
  startRun,
  viewMatch,
} from './pve/session.js';
import { listPending, markClaimed, rewardsDataRoot } from './rewards/pending.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use('/card-art', express.static(path.join(__dirname, '..', 'public', 'card-art')));

function requireAuth(req: express.Request, res: express.Response): ReturnType<typeof validateBearer> {
  const session = validateBearer(req.header('authorization') ?? undefined);
  if (!session) {
    res.status(401).json({
      status: 'error',
      code: 'unauthorized',
      message: 'Use Authorization: Bearer <WebAE token> (or CARDBATTLE_DEV_TOKEN)',
    });
    return null;
  }
  return session;
}

app.get('/api/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'textech-cardbattle',
    auth: authStatus(),
    dataRoot: rewardsDataRoot(),
  });
});

app.get('/api/meta', (_req, res) => {
  res.json({
    themes: listThemes(),
    voltages: VOLTAGE_ORDER,
    themeSlotsByVoltage: THEME_SLOTS_BY_VOLTAGE,
    equipment: listEquipment(),
    cardCount: CARD_CATALOG.length,
  });
});

app.get('/api/cards', (_req, res) => {
  res.json({ cards: CARD_CATALOG });
});

app.get('/api/me', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  res.json({
    ownerUuid: session.ownerUuid,
    actorUuid: session.actorUuid,
    actorName: session.actorName,
    type: session.type,
  });
});

app.post('/api/run', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  try {
    const themes = (req.body?.themes ?? []) as ThemeId[];
    const voltage = (req.body?.voltage ?? 'LV') as VoltageTier;
    const equipmentIds = (req.body?.equipmentIds ?? []) as string[];
    const run = startRun({
      ownerUuid: session.ownerUuid,
      playerName: session.actorName,
      themes,
      voltage,
      equipmentIds,
      seed: req.body?.seed,
    });
    res.json({ run });
  } catch (e) {
    res.status(400).json({ status: 'error', message: (e as Error).message });
  }
});

app.get('/api/run/:runId', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  const run = getRun(req.params.runId);
  if (!run || run.ownerUuid !== session.ownerUuid) {
    res.status(404).json({ status: 'error', message: 'Not found' });
    return;
  }
  res.json({ run });
});

app.post('/api/run/:runId/stage', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  try {
    const { run, match, choice } = beginStage(
      req.params.runId,
      session.ownerUuid,
      session.actorName,
      req.body?.rewardChoiceId,
    );
    res.json({ run, matchId: match.matchId, match, selectedReward: choice ?? null });
  } catch (e) {
    res.status(400).json({ status: 'error', message: (e as Error).message });
  }
});

app.get('/api/match/:matchId', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  try {
    const match = viewMatch(req.params.matchId, session.ownerUuid);
    res.json({ match });
  } catch (e) {
    res.status(400).json({ status: 'error', message: (e as Error).message });
  }
});

app.post('/api/match/:matchId/action', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  try {
    const match = actOnMatch(req.params.matchId, session.ownerUuid, req.body?.action);
    const finished = finishMatchIfWon(req.params.matchId, req.body?.runId);
    res.json({ match: finished.match, run: finished.run ?? null });
  } catch (e) {
    res.status(400).json({ status: 'error', message: (e as Error).message });
  }
});

app.post('/api/run/:runId/claim-reward', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  try {
    const result = claimStageReward(req.params.runId, session.ownerUuid, req.body?.choiceId);
    res.json(result);
  } catch (e) {
    res.status(400).json({ status: 'error', message: (e as Error).message });
  }
});

app.get('/api/rewards/pending', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  res.json({ entries: listPending(session.ownerUuid) });
});

app.post('/api/rewards/:id/mark-claimed', (req, res) => {
  const session = requireAuth(req, res);
  if (!session) return;
  const entry = markClaimed(session.ownerUuid, req.params.id);
  if (!entry) {
    res.status(404).json({ status: 'error', message: 'Not found' });
    return;
  }
  res.json({
    entry,
    note: 'Stub only — Minecraft item grant is not implemented in V1',
  });
});

function resolveFrontendDir(): string | null {
  const fromEnv = process.env.CARDBATTLE_FRONTEND_DIR?.trim();
  if (fromEnv && fs.existsSync(fromEnv)) return fromEnv;
  const sibling = path.resolve(__dirname, '..', '..', 'cardbattle-frontend', 'dist');
  if (fs.existsSync(sibling)) return sibling;
  return null;
}

const frontendDir = resolveFrontendDir();
if (frontendDir) {
  app.use(express.static(frontendDir));
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api') || req.path.startsWith('/card-art')) {
      next();
      return;
    }
    const indexHtml = path.join(frontendDir, 'index.html');
    if (fs.existsSync(indexHtml)) {
      res.sendFile(indexHtml);
      return;
    }
    next();
  });
  console.log(`[cardbattle] serving SPA from ${frontendDir}`);
} else {
  console.log('[cardbattle] no SPA dist found (set CARDBATTLE_FRONTEND_DIR or build cardbattle-frontend)');
}

const port = Number(process.env.CARDBATTLE_PORT || 8787);
app.listen(port, () => {
  console.log(`[cardbattle] listening on http://127.0.0.1:${port}`);
  console.log(`[cardbattle] auth:`, authStatus());
});
