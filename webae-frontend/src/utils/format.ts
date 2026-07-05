// Number formatting utilities — supports multiple display formats
export type NumberFormat =
  | 'full'
  | 'thousands'
  | 'scientific'
  | 'ae'
  | 'engineering'
  | 'short';

const AE_SUFFIXES = [
  { v: 1e12, s: 'T' },
  { v: 1e9, s: 'B' },
  { v: 1e6, s: 'M' },
  { v: 1e3, s: 'K' },
];

const ENG_SUFFIXES = [
  { v: 1e12, s: 'T' },
  { v: 1e9, s: 'G' },
  { v: 1e6, s: 'M' },
  { v: 1e3, s: 'K' },
];

/**
 * Format a numeric value according to the selected format mode.
 * Returns a string ready for display.
 */
export function formatNumber(
  value: number | undefined | null,
  format: NumberFormat = 'thousands'
): string {
  if (value === undefined || value === null || isNaN(value)) return '0';
  const n = Number(value);
  if (!isFinite(n)) return n > 0 ? '∞' : '-∞';

  switch (format) {
    case 'full':
      return String(Math.trunc(n));

    case 'thousands':
      return Math.trunc(n).toLocaleString('en-US');

    case 'scientific':
      return n.toPrecision(4).replace(/e\+?/, 'E+');

    case 'ae':
      return formatSuffixed(n, AE_SUFFIXES);

    case 'engineering':
      return formatSuffixed(n, ENG_SUFFIXES);

    case 'short':
      if (Math.abs(n) < 1000) return String(Math.trunc(n));
      return formatSuffixed(n, AE_SUFFIXES);

    default:
      return Math.trunc(n).toLocaleString('en-US');
  }
}

function formatSuffixed(
  n: number,
  suffixes: { v: number; s: string }[]
): string {
  const abs = Math.abs(n);
  for (const { v, s } of suffixes) {
    if (abs >= v) {
      const scaled = n / v;
      // Show 2 decimal places, strip trailing zeros
      const formatted = scaled.toFixed(2).replace(/\.?0+$/, '');
      return formatted + s;
    }
  }
  return String(Math.trunc(n));
}

/**
 * Format byte amounts (AE storage bytes) into human-readable sizes.
 */
export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const idx = Math.min(i, units.length - 1);
  return (bytes / Math.pow(1024, idx)).toFixed(2).replace(/\.?0+$/, '') + ' ' + units[idx];
}

/**
 * Format with optional delta line vs previous sample.
 */
export function formatLargeWithDelta(
  value: number,
  prevValue: number | undefined | null,
  format: NumberFormat = 'ae'
): { main: string; delta: string | null; deltaPositive: boolean | null } {
  const main = formatSignificant(value, format, 5);
  if (prevValue === undefined || prevValue === null || isNaN(prevValue)) {
    return { main, delta: null, deltaPositive: null };
  }
  const diff = value - prevValue;
  if (diff === 0 || !isFinite(diff)) {
    return { main, delta: null, deltaPositive: null };
  }
  const sign = diff > 0 ? '+' : '';
  const delta = sign + formatSignificant(diff, format, 4);
  return { main, delta, deltaPositive: diff > 0 };
}

/**
 * Format large numbers preserving significant digits (not truncated suffix).
 */
export function formatSignificant(
  value: number | undefined | null,
  format: NumberFormat = 'ae',
  sigDigits = 5
): string {
  if (value === undefined || value === null || isNaN(value)) return '0';
  const n = Number(value);
  if (!isFinite(n)) return n > 0 ? '∞' : '-∞';
  if (format === 'full' || format === 'thousands') {
    return formatNumber(n, format);
  }
  const abs = Math.abs(n);
  if (abs < 1000) return String(Math.trunc(n));
  for (const { v, s } of AE_SUFFIXES) {
    if (abs >= v) {
      const scaled = n / v;
      const digits = Math.max(1, sigDigits);
      const formatted = scaled.toPrecision(digits).replace(/\.?0+$/, '');
      return formatted + s;
    }
  }
  return formatNumber(n, format);
}

/**
 * Format a duration in milliseconds to a human-readable string.
 */
export function formatDuration(ms: number): string {
  if (ms <= 0) return '0s';
  const s = Math.floor(ms / 1000);
  if (s < 60) return s + 's';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm ' + (s % 60) + 's';
  const h = Math.floor(m / 60);
  return h + 'h ' + (m % 60) + 'm';
}

/**
 * Format a timestamp to a locale-aware time string.
 */
export function formatTime(ts: number, lang: 'zh' | 'en' = 'en'): string {
  if (!ts || ts <= 0) return '--';
  const locale = lang === 'zh' ? 'zh-CN' : 'en-US';
  return new Date(ts).toLocaleTimeString(locale, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/**
 * Format a timestamp to a locale-aware date-time string.
 */
export function formatDateTime(ts: number, lang: 'zh' | 'en' = 'en'): string {
  if (!ts || ts <= 0) return '--';
  const locale = lang === 'zh' ? 'zh-CN' : 'en-US';
  return new Date(ts).toLocaleString(locale, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
