import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { hashPassword, verifyPassword } from './password.js';

describe('password hashing', () => {
  it('round-trips pbkdf2 encoding', () => {
    const encoded = hashPassword('correct-horse-battery');
    expect(encoded.startsWith('pbkdf2$sha256$')).toBe(true);
    expect(verifyPassword('correct-horse-battery', encoded)).toBe(true);
    expect(verifyPassword('wrong', encoded)).toBe(false);
  });
});

describe('accounts store', () => {
  it('registers, binds, and admin lists', async () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cb-accounts-'));
    process.env.CARDBATTLE_DATA_DIR = root;
    const accounts = await import('./accounts.js');
    const reg = accounts.registerUser({ username: 'Player_One', password: 'password123', displayName: '壹号' });
    expect(reg.user.username).toBe('player_one');
    expect(reg.token.length).toBeGreaterThan(20);
    const code = accounts.issueBindCode('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'Steve');
    const bound = accounts.bindAccountWithCode(reg.user.id, code.code);
    expect(bound.mcName).toBe('Steve');
    expect(accounts.listBindings()).toHaveLength(1);
    const admin = accounts.registerUser({
      username: 'boss',
      password: 'password123',
      role: 'superadmin',
    });
    accounts.setUserRole(accounts.findUserById(admin.user.id)!, reg.user.id, 'admin');
    expect(accounts.findUserById(reg.user.id)?.role).toBe('admin');
  });
});
