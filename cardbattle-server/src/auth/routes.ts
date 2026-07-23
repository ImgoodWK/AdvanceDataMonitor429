import type { Express, Request, Response } from 'express';
import type { AuthSession } from '../battle/types.js';
import {
  adminForceBind,
  adminResetPassword,
  bindAccountWithCode,
  bootstrapSuperadmin,
  changePassword,
  findUserById,
  hasAnyUser,
  isAdminRole,
  issueBindCode,
  listBindings,
  listUsers,
  loginUser,
  logoutSession,
  registerUser,
  setUserDisabled,
  setUserRole,
  unbindAccount,
  updateProfile,
  type AccountRole,
} from './accounts.js';
import { validateBearer } from './validate.js';

function bearerToken(req: Request): string | null {
  const header = req.header('authorization');
  if (!header) return null;
  const prefix = 'Bearer ';
  if (!header.startsWith(prefix) && !header.startsWith('bearer ')) return null;
  return header.slice(prefix.length).trim() || null;
}

function requireSession(req: Request, res: Response): AuthSession | null {
  const session = validateBearer(req.header('authorization') ?? undefined);
  if (!session) {
    res.status(401).json({
      status: 'error',
      code: 'unauthorized',
      message: '请先登录（账号密码或 Bearer Token）',
    });
    return null;
  }
  return session;
}

function requireAccount(req: Request, res: Response): AuthSession | null {
  const session = requireSession(req, res);
  if (!session) return null;
  if (!session.accountId) {
    res.status(400).json({
      status: 'error',
      code: 'account_required',
      message: '此操作需要卡牌账号登录（不能仅用 WebAE/dev Token）',
    });
    return null;
  }
  return session;
}

function requireAdmin(req: Request, res: Response): AuthSession | null {
  const session = requireAccount(req, res);
  if (!session) return null;
  if (!isAdminRole(session.role as AccountRole | undefined)) {
    res.status(403).json({ status: 'error', code: 'forbidden', message: '需要管理员权限' });
    return null;
  }
  return session;
}

function requireBridge(req: Request, res: Response): boolean {
  const bridgeToken = process.env.CARDBATTLE_BRIDGE_TOKEN?.trim();
  if (!bridgeToken) {
    res.status(503).json({
      status: 'error',
      code: 'bridge_not_configured',
      message: '未配置 CARDBATTLE_BRIDGE_TOKEN',
    });
    return false;
  }
  if (req.header('x-cardbattle-bridge-token') !== bridgeToken) {
    res.status(403).json({ status: 'error', code: 'bridge_forbidden', message: 'Bridge token required' });
    return false;
  }
  return true;
}

function mePayload(session: AuthSession) {
  return {
    ownerUuid: session.ownerUuid,
    actorUuid: session.actorUuid,
    actorName: session.actorName,
    type: session.type,
    accountId: session.accountId ?? null,
    username: session.username ?? null,
    role: session.role ?? null,
    mcUuid: session.mcUuid ?? null,
    mcName: session.mcName ?? null,
    authSource: session.authSource ?? null,
    binding: session.mcUuid
      ? { mcUuid: session.mcUuid, mcName: session.mcName, bound: true }
      : { bound: false },
  };
}

export function maybeBootstrapSuperadmin(): void {
  const user = process.env.CARDBATTLE_BOOTSTRAP_ADMIN_USER?.trim();
  const pass = process.env.CARDBATTLE_BOOTSTRAP_ADMIN_PASSWORD?.trim();
  if (!user || !pass) return;
  try {
    if (!hasAnyUser()) {
      const created = bootstrapSuperadmin(user, pass);
      console.log(`[cardbattle] bootstrapped superadmin username=${created.username}`);
    }
  } catch (error) {
    console.warn('[cardbattle] bootstrap superadmin skipped:', (error as Error).message);
  }
}

export function mountAuthRoutes(app: Express): void {
  app.post('/api/auth/register', (req, res) => {
    try {
      const result = registerUser({
        username: String(req.body?.username ?? ''),
        password: String(req.body?.password ?? ''),
        displayName: req.body?.displayName ? String(req.body.displayName) : undefined,
      });
      res.status(201).json({ token: result.token, user: result.user });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/auth/login', (req, res) => {
    try {
      const result = loginUser(String(req.body?.username ?? ''), String(req.body?.password ?? ''));
      res.json({ token: result.token, user: result.user });
    } catch (e) {
      res.status(401).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/auth/logout', (req, res) => {
    const token = bearerToken(req);
    if (token) logoutSession(token);
    res.json({ status: 'ok' });
  });

  app.post('/api/auth/change-password', (req, res) => {
    const session = requireAccount(req, res);
    if (!session?.accountId) return;
    try {
      changePassword(
        session.accountId,
        String(req.body?.currentPassword ?? ''),
        String(req.body?.newPassword ?? ''),
      );
      res.json({ status: 'ok', message: '密码已更新，请重新登录' });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.patch('/api/me', (req, res) => {
    const session = requireAccount(req, res);
    if (!session?.accountId) return;
    try {
      const user = updateProfile(session.accountId, String(req.body?.displayName ?? ''));
      res.json({ user });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/me/bind', (req, res) => {
    const session = requireAccount(req, res);
    if (!session?.accountId) return;
    try {
      const user = bindAccountWithCode(session.accountId, String(req.body?.code ?? ''));
      res.json({ user, binding: { mcUuid: user.mcUuid, mcName: user.mcName, bound: true } });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.delete('/api/me/bind', (req, res) => {
    const session = requireAccount(req, res);
    if (!session?.accountId) return;
    try {
      const user = unbindAccount(session.accountId);
      res.json({ user, binding: { bound: false } });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/bridge/bind-codes', (req, res) => {
    if (!requireBridge(req, res)) return;
    try {
      const entry = issueBindCode(String(req.body?.mcUuid ?? ''), String(req.body?.mcName ?? ''));
      res.status(201).json({
        code: entry.code,
        mcUuid: entry.mcUuid,
        mcName: entry.mcName,
        expiresAt: entry.expiresAt,
        ttlSeconds: Math.round((entry.expiresAt - entry.createdAt) / 1000),
      });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.get('/api/admin/users', (req, res) => {
    if (!requireAdmin(req, res)) return;
    res.json({ users: listUsers() });
  });

  app.patch('/api/admin/users/:id', (req, res) => {
    const session = requireAdmin(req, res);
    if (!session?.accountId) return;
    const actor = findUserById(session.accountId);
    if (!actor) {
      res.status(401).json({ status: 'error', message: 'unauthorized' });
      return;
    }
    try {
      let user = null as ReturnType<typeof listUsers>[number] | null;
      if (req.body?.role != null) {
        user = setUserRole(actor, req.params.id, String(req.body.role) as AccountRole);
      }
      if (req.body?.disabled != null) {
        user = setUserDisabled(actor, req.params.id, Boolean(req.body.disabled));
      }
      if (req.body?.displayName != null) {
        user = updateProfile(req.params.id, String(req.body.displayName));
      }
      if (!user) {
        const found = listUsers().find((u) => u.id === req.params.id);
        if (!found) throw new Error('用户不存在');
        user = found;
      }
      res.json({ user });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/admin/users/:id/reset-password', (req, res) => {
    if (!requireAdmin(req, res)) return;
    try {
      adminResetPassword(req.params.id, String(req.body?.password ?? ''));
      res.json({ status: 'ok' });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.get('/api/admin/bindings', (req, res) => {
    if (!requireAdmin(req, res)) return;
    res.json({ bindings: listBindings() });
  });

  app.delete('/api/admin/bindings/:userId', (req, res) => {
    if (!requireAdmin(req, res)) return;
    try {
      const user = unbindAccount(req.params.userId);
      res.json({ user });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });

  app.post('/api/admin/bindings/:userId', (req, res) => {
    if (!requireAdmin(req, res)) return;
    try {
      const user = adminForceBind(
        req.params.userId,
        String(req.body?.mcUuid ?? ''),
        String(req.body?.mcName ?? ''),
      );
      res.json({ user });
    } catch (e) {
      res.status(400).json({ status: 'error', message: (e as Error).message });
    }
  });
}

export { mePayload, requireSession };
