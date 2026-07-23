import { randomBytes, randomUUID } from 'node:crypto';
import path from 'node:path';
import { readJsonFile, runtimeDataRoot, writeJsonAtomic } from '../storage/paths.js';
import { assertPasswordPolicy, hashPassword, verifyPassword } from './password.js';

export type AccountRole = 'user' | 'admin' | 'superadmin';

export interface AccountUser {
  id: string;
  username: string;
  displayName: string;
  passwordHash: string;
  role: AccountRole;
  mcUuid: string | null;
  mcName: string | null;
  boundAt: number | null;
  createdAt: number;
  updatedAt: number;
  disabled: boolean;
}

export interface AccountSession {
  token: string;
  userId: string;
  createdAt: number;
  expiresAt: number;
  lastUsedAt: number;
}

export interface BindCode {
  code: string;
  mcUuid: string;
  mcName: string;
  createdAt: number;
  expiresAt: number;
  consumedAt: number | null;
}

interface UsersFile {
  schemaVersion: 1;
  users: AccountUser[];
}

interface SessionsFile {
  schemaVersion: 1;
  sessions: AccountSession[];
}

interface BindCodesFile {
  schemaVersion: 1;
  codes: BindCode[];
}

const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const BIND_TTL_MS = 10 * 60 * 1000;
const USERNAME_RE = /^[a-zA-Z0-9_]{3,32}$/;

function accountsDir(): string {
  return path.join(runtimeDataRoot(), 'accounts');
}

function usersPath(): string {
  return path.join(accountsDir(), 'users.json');
}

function sessionsPath(): string {
  return path.join(accountsDir(), 'sessions.json');
}

function bindCodesPath(): string {
  return path.join(accountsDir(), 'bind-codes.json');
}

function loadUsers(): AccountUser[] {
  return readJsonFile<UsersFile>(usersPath())?.users ?? [];
}

function saveUsers(users: AccountUser[]): void {
  writeJsonAtomic(usersPath(), { schemaVersion: 1, users } satisfies UsersFile);
}

function loadSessions(): AccountSession[] {
  return readJsonFile<SessionsFile>(sessionsPath())?.sessions ?? [];
}

function saveSessions(sessions: AccountSession[]): void {
  writeJsonAtomic(sessionsPath(), { schemaVersion: 1, sessions } satisfies SessionsFile);
}

function loadBindCodes(): BindCode[] {
  return readJsonFile<BindCodesFile>(bindCodesPath())?.codes ?? [];
}

function saveBindCodes(codes: BindCode[]): void {
  writeJsonAtomic(bindCodesPath(), { schemaVersion: 1, codes } satisfies BindCodesFile);
}

function normalizeUsername(username: string): string {
  return username.trim().toLowerCase();
}

function publicUser(user: AccountUser) {
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    role: user.role,
    mcUuid: user.mcUuid,
    mcName: user.mcName,
    boundAt: user.boundAt,
    createdAt: user.createdAt,
    disabled: user.disabled,
  };
}

function purgeSessions(sessions: AccountSession[], now = Date.now()): AccountSession[] {
  return sessions.filter((s) => s.expiresAt > now);
}

function purgeBindCodes(codes: BindCode[], now = Date.now()): BindCode[] {
  return codes.filter((c) => !c.consumedAt && c.expiresAt > now);
}

function createSession(userId: string): AccountSession {
  const now = Date.now();
  return {
    token: randomBytes(32).toString('hex'),
    userId,
    createdAt: now,
    expiresAt: now + SESSION_TTL_MS,
    lastUsedAt: now,
  };
}

export function listUsers(): ReturnType<typeof publicUser>[] {
  return loadUsers().map(publicUser);
}

export function findUserById(id: string): AccountUser | null {
  return loadUsers().find((u) => u.id === id) ?? null;
}

export function findUserByUsername(username: string): AccountUser | null {
  const key = normalizeUsername(username);
  return loadUsers().find((u) => u.username === key) ?? null;
}

export function findUserByMcUuid(mcUuid: string): AccountUser | null {
  const key = mcUuid.trim().toLowerCase();
  return loadUsers().find((u) => u.mcUuid && u.mcUuid.toLowerCase() === key) ?? null;
}

export function registerUser(input: {
  username: string;
  password: string;
  displayName?: string;
  role?: AccountRole;
}): { user: ReturnType<typeof publicUser>; token: string } {
  const username = normalizeUsername(input.username);
  if (!USERNAME_RE.test(username)) {
    throw new Error('用户名需为 3–32 位字母数字或下划线');
  }
  assertPasswordPolicy(input.password);
  const users = loadUsers();
  if (users.some((u) => u.username === username)) {
    throw new Error('用户名已被占用');
  }
  const now = Date.now();
  const user: AccountUser = {
    id: randomUUID(),
    username,
    displayName: (input.displayName?.trim() || username).slice(0, 48),
    passwordHash: hashPassword(input.password),
    role: input.role ?? 'user',
    mcUuid: null,
    mcName: null,
    boundAt: null,
    createdAt: now,
    updatedAt: now,
    disabled: false,
  };
  users.push(user);
  saveUsers(users);
  const session = createSession(user.id);
  const sessions = purgeSessions(loadSessions());
  sessions.push(session);
  saveSessions(sessions);
  return { user: publicUser(user), token: session.token };
}

export function loginUser(username: string, password: string): { user: ReturnType<typeof publicUser>; token: string } {
  const user = findUserByUsername(username);
  if (!user || user.disabled) throw new Error('用户名或密码错误');
  if (!verifyPassword(password, user.passwordHash)) throw new Error('用户名或密码错误');
  const session = createSession(user.id);
  const sessions = purgeSessions(loadSessions());
  sessions.push(session);
  saveSessions(sessions);
  return { user: publicUser(user), token: session.token };
}

export function logoutSession(token: string): void {
  const sessions = purgeSessions(loadSessions()).filter((s) => s.token !== token);
  saveSessions(sessions);
}

export function resolveAccountSession(token: string): AccountUser | null {
  const sessions = purgeSessions(loadSessions());
  const hit = sessions.find((s) => s.token === token);
  if (!hit) {
    if (sessions.length !== loadSessions().length) saveSessions(sessions);
    return null;
  }
  hit.lastUsedAt = Date.now();
  saveSessions(sessions);
  const user = findUserById(hit.userId);
  if (!user || user.disabled) return null;
  return user;
}

export function changePassword(userId: string, currentPassword: string, newPassword: string): void {
  assertPasswordPolicy(newPassword);
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) throw new Error('用户不存在');
  const user = users[idx];
  if (!verifyPassword(currentPassword, user.passwordHash)) throw new Error('当前密码不正确');
  user.passwordHash = hashPassword(newPassword);
  user.updatedAt = Date.now();
  users[idx] = user;
  saveUsers(users);
  // Invalidate other sessions except keep caller handled by client refresh if needed —
  // revoke all sessions for safety after password change.
  saveSessions(purgeSessions(loadSessions()).filter((s) => s.userId !== userId));
}

export function adminResetPassword(userId: string, newPassword: string): void {
  assertPasswordPolicy(newPassword);
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) throw new Error('用户不存在');
  users[idx].passwordHash = hashPassword(newPassword);
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  saveSessions(purgeSessions(loadSessions()).filter((s) => s.userId !== userId));
}

export function updateProfile(userId: string, displayName: string): ReturnType<typeof publicUser> {
  const name = displayName.trim().slice(0, 48);
  if (!name) throw new Error('显示名不能为空');
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) throw new Error('用户不存在');
  users[idx].displayName = name;
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  return publicUser(users[idx]);
}

export function setUserRole(actor: AccountUser, targetId: string, role: AccountRole): ReturnType<typeof publicUser> {
  if (actor.role !== 'superadmin') {
    throw new Error('仅超管可更改角色');
  }
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === targetId);
  if (idx < 0) throw new Error('用户不存在');
  if (users[idx].id === actor.id) throw new Error('不能修改自己的角色');
  users[idx].role = role;
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  return publicUser(users[idx]);
}

export function setUserDisabled(actor: AccountUser, targetId: string, disabled: boolean): ReturnType<typeof publicUser> {
  if (actor.role !== 'admin' && actor.role !== 'superadmin') throw new Error('权限不足');
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === targetId);
  if (idx < 0) throw new Error('用户不存在');
  if (users[idx].role === 'superadmin') throw new Error('不能禁用超管');
  if (users[idx].id === actor.id) throw new Error('不能禁用自己');
  users[idx].disabled = disabled;
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  if (disabled) {
    saveSessions(purgeSessions(loadSessions()).filter((s) => s.userId !== targetId));
  }
  return publicUser(users[idx]);
}

function randomBindCode(): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let out = '';
  const bytes = randomBytes(8);
  for (let i = 0; i < 8; i += 1) out += alphabet[bytes[i]! % alphabet.length];
  return out;
}

/** Bridge or MC: issue a one-time bind code for a Minecraft player. */
export function issueBindCode(mcUuid: string, mcName: string): BindCode {
  const uuid = mcUuid.trim();
  if (!uuid) throw new Error('缺少 mcUuid');
  const now = Date.now();
  let codes = purgeBindCodes(loadBindCodes(), now).filter((c) => c.mcUuid.toLowerCase() !== uuid.toLowerCase());
  let code = randomBindCode();
  for (let i = 0; i < 16 && codes.some((c) => c.code === code); i += 1) {
    code = randomBindCode();
  }
  const entry: BindCode = {
    code,
    mcUuid: uuid,
    mcName: (mcName || 'Player').slice(0, 32),
    createdAt: now,
    expiresAt: now + BIND_TTL_MS,
    consumedAt: null,
  };
  codes.push(entry);
  saveBindCodes(codes);
  return entry;
}

export function bindAccountWithCode(userId: string, codeRaw: string): ReturnType<typeof publicUser> {
  const code = codeRaw.trim().toUpperCase();
  if (!code) throw new Error('请输入绑定码');
  const now = Date.now();
  const codes = loadBindCodes();
  const idx = codes.findIndex((c) => c.code === code);
  if (idx < 0) throw new Error('绑定码无效或已使用');
  const entry = codes[idx];
  if (entry.consumedAt || entry.expiresAt <= now) {
    throw new Error('绑定码已过期');
  }
  const users = loadUsers();
  const userIdx = users.findIndex((u) => u.id === userId);
  if (userIdx < 0) throw new Error('用户不存在');
  if (users[userIdx].mcUuid) throw new Error('已绑定角色，请先解绑');
  if (users.some((u) => u.mcUuid && u.mcUuid.toLowerCase() === entry.mcUuid.toLowerCase())) {
    throw new Error('该 MC 角色已绑定其他账号');
  }
  users[userIdx].mcUuid = entry.mcUuid;
  users[userIdx].mcName = entry.mcName;
  users[userIdx].boundAt = now;
  users[userIdx].updatedAt = now;
  codes.splice(idx, 1);
  saveUsers(users);
  saveBindCodes(purgeBindCodes(codes, now));
  return publicUser(users[userIdx]);
}

export function unbindAccount(userId: string): ReturnType<typeof publicUser> {
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === userId);
  if (idx < 0) throw new Error('用户不存在');
  users[idx].mcUuid = null;
  users[idx].mcName = null;
  users[idx].boundAt = null;
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  return publicUser(users[idx]);
}

export function adminForceBind(
  targetId: string,
  mcUuid: string,
  mcName: string,
): ReturnType<typeof publicUser> {
  const uuid = mcUuid.trim();
  if (!uuid) throw new Error('缺少 mcUuid');
  const users = loadUsers();
  const idx = users.findIndex((u) => u.id === targetId);
  if (idx < 0) throw new Error('用户不存在');
  const other = users.find(
    (u) => u.id !== targetId && u.mcUuid && u.mcUuid.toLowerCase() === uuid.toLowerCase(),
  );
  if (other) throw new Error(`该角色已绑定账号 ${other.username}`);
  users[idx].mcUuid = uuid;
  users[idx].mcName = (mcName || 'Player').slice(0, 32);
  users[idx].boundAt = Date.now();
  users[idx].updatedAt = Date.now();
  saveUsers(users);
  return publicUser(users[idx]);
}

export function listBindings(): {
  userId: string;
  username: string;
  displayName: string;
  mcUuid: string;
  mcName: string;
  boundAt: number;
}[] {
  return loadUsers()
    .filter((u) => u.mcUuid)
    .map((u) => ({
      userId: u.id,
      username: u.username,
      displayName: u.displayName,
      mcUuid: u.mcUuid!,
      mcName: u.mcName || '',
      boundAt: u.boundAt || 0,
    }));
}

export function hasAnyUser(): boolean {
  return loadUsers().length > 0;
}

export function bootstrapSuperadmin(username: string, password: string, displayName?: string): ReturnType<typeof publicUser> {
  if (hasAnyUser()) {
    const existing = findUserByUsername(username);
    if (existing) return publicUser(existing);
    throw new Error('已有用户，拒绝 bootstrap（避免重复建超管）');
  }
  const result = registerUser({
    username,
    password,
    displayName: displayName || username,
    role: 'superadmin',
  });
  return result.user;
}

export function isAdminRole(role: AccountRole | undefined): boolean {
  return role === 'admin' || role === 'superadmin';
}
