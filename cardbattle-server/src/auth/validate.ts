import fs from 'node:fs';
import path from 'node:path';
import type { AuthSession } from '../battle/types.js';

interface TokenRecord {
  token: string;
  type?: string;
  ownerUuid?: string;
  actorUuid?: string;
  actorName?: string;
  playerUuid?: string;
  issuedAt?: number;
  lastUsedAt?: number;
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

/** Validate Bearer token against WebAE web-tokens.json or dev bypass. */
export function validateBearer(authHeader: string | undefined): AuthSession | null {
  if (!authHeader) return null;
  const prefix = 'Bearer ';
  if (!authHeader.startsWith(prefix) && !authHeader.startsWith('bearer ')) return null;
  const token = authHeader.slice(prefix.length).trim();
  if (!token) return null;

  const devToken = process.env.CARDBATTLE_DEV_TOKEN?.trim();
  if (devToken && token === devToken) {
    return {
      token,
      ownerUuid: process.env.CARDBATTLE_DEV_OWNER_UUID || '00000000-0000-0000-0000-000000000001',
      actorUuid: process.env.CARDBATTLE_DEV_OWNER_UUID || '00000000-0000-0000-0000-000000000001',
      actorName: process.env.CARDBATTLE_DEV_OWNER_NAME || 'DevPlayer',
      type: 'owner',
    };
  }

  const tokens = loadTokens();
  const hit = tokens.find((t) => t.token === token);
  if (!hit) return null;
  const ownerUuid = hit.ownerUuid || hit.playerUuid || hit.actorUuid;
  if (!ownerUuid) return null;
  return {
    token,
    ownerUuid,
    actorUuid: hit.actorUuid || ownerUuid,
    actorName: hit.actorName || 'Player',
    type: hit.type || 'owner',
  };
}

export function authStatus(): { tokenFile: string | null; tokenCount: number; devBypass: boolean } {
  const file = resolveTokenFile();
  return {
    tokenFile: file,
    tokenCount: loadTokens().length,
    devBypass: Boolean(process.env.CARDBATTLE_DEV_TOKEN?.trim()),
  };
}
