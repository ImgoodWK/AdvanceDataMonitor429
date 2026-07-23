import type { AuthSession } from '../battle/types.js';
import { findUserByMcUuid, resolveAccountSession, type AccountUser } from './accounts.js';
import fs from 'node:fs';
import path from 'node:path';

interface TokenRecord {
  token: string;
  type?: string;
  ownerUuid?: string;
  actorUuid?: string;
  actorName?: string;
  playerUuid?: string;
}

function resolveTokenFile(): string | null {
  const root = process.env.TEXTECH_INSTANCE_ROOT?.trim();
  if (!root) return null;
  return path.join(root, 'TeXTech', 'WebAE', 'web-tokens.json');
}

function loadTokens(): TokenRecord[] {
  const file = resolveTokenFile();
  if (!file || !fs.existsSync(file)) return [];
  try {
    const raw = fs.readFileSync(file, 'utf8');
    const data = JSON.parse(raw) as TokenRecord[] | { tokens?: TokenRecord[] };
    if (Array.isArray(data)) return data;
    if (Array.isArray(data.tokens)) return data.tokens;
    return [];
  } catch (e) {
    console.warn('[auth] failed to read web-tokens.json', e);
    return [];
  }
}

function sessionFromUser(token: string, user: AccountUser, authSource: AuthSession['authSource']): AuthSession {
  return {
    token,
    ownerUuid: user.id,
    actorUuid: user.mcUuid || user.id,
    actorName: user.displayName,
    type: user.role === 'user' ? 'owner' : user.role,
    accountId: user.id,
    username: user.username,
    role: user.role,
    mcUuid: user.mcUuid,
    mcName: user.mcName,
    authSource,
  };
}

function extractBearer(authHeader: string | undefined): string | null {
  if (!authHeader) return null;
  const prefix = 'Bearer ';
  if (!authHeader.startsWith(prefix) && !authHeader.startsWith('bearer ')) return null;
  const token = authHeader.slice(prefix.length).trim();
  return token || null;
}

/** Validate Bearer: account session → dev → WebAE (bound account or legacy MC identity). */
export function validateBearer(authHeader: string | undefined): AuthSession | null {
  const token = extractBearer(authHeader);
  if (!token) return null;

  const accountUser = resolveAccountSession(token);
  if (accountUser) return sessionFromUser(token, accountUser, 'account');

  const devToken = process.env.CARDBATTLE_DEV_TOKEN?.trim();
  if (devToken && token === devToken) {
    return {
      token,
      ownerUuid: process.env.CARDBATTLE_DEV_OWNER_UUID || '00000000-0000-0000-0000-000000000001',
      actorUuid: process.env.CARDBATTLE_DEV_OWNER_UUID || '00000000-0000-0000-0000-000000000001',
      actorName: process.env.CARDBATTLE_DEV_OWNER_NAME || 'DevPlayer',
      type: 'owner',
      authSource: 'dev',
    };
  }

  const tokens = loadTokens();
  const hit = tokens.find((t) => t.token === token);
  if (!hit) return null;
  const mcUuid = hit.ownerUuid || hit.playerUuid || hit.actorUuid;
  if (!mcUuid) return null;

  const bound = findUserByMcUuid(mcUuid);
  if (bound && !bound.disabled) {
    return sessionFromUser(token, bound, 'webae');
  }

  return {
    token,
    ownerUuid: mcUuid,
    actorUuid: hit.actorUuid || mcUuid,
    actorName: hit.actorName || 'Player',
    type: hit.type || 'owner',
    mcUuid,
    mcName: hit.actorName || null,
    authSource: 'webae',
  };
}

export function authStatus(): {
  tokenFile: string | null;
  tokenCount: number;
  devBypass: boolean;
  accountsEnabled: boolean;
} {
  const file = resolveTokenFile();
  return {
    tokenFile: file,
    tokenCount: loadTokens().length,
    devBypass: Boolean(process.env.CARDBATTLE_DEV_TOKEN?.trim()),
    accountsEnabled: true,
  };
}
