import { randomBytes, pbkdf2Sync, timingSafeEqual } from 'node:crypto';

const ITERATIONS = 120_000;
const KEYLEN = 32;
const DIGEST = 'sha256';

/** Encode: pbkdf2$sha256$iterations$saltB64$hashB64 (shared with Java). */
export function hashPassword(password: string): string {
  const salt = randomBytes(16);
  const hash = pbkdf2Sync(password, salt, ITERATIONS, KEYLEN, DIGEST);
  return `pbkdf2$${DIGEST}$${ITERATIONS}$${salt.toString('base64')}$${hash.toString('base64')}`;
}

export function verifyPassword(password: string, encoded: string): boolean {
  const parts = encoded.split('$');
  if (parts.length !== 5 || parts[0] !== 'pbkdf2' || parts[1] !== DIGEST) return false;
  const iterations = Number(parts[2]);
  if (!Number.isFinite(iterations) || iterations < 10_000) return false;
  const salt = Buffer.from(parts[3], 'base64');
  const expected = Buffer.from(parts[4], 'base64');
  const actual = pbkdf2Sync(password, salt, iterations, expected.length, DIGEST);
  if (actual.length !== expected.length) return false;
  return timingSafeEqual(actual, expected);
}

export function assertPasswordPolicy(password: string): void {
  if (typeof password !== 'string' || password.length < 8) {
    throw new Error('密码至少 8 位');
  }
  if (password.length > 128) {
    throw new Error('密码过长');
  }
}
