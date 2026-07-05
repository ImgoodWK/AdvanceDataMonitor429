// Per-feature debug logging for the WebAE frontend.
//
// Each feature (icons/chat/dashboard/synthesis/patterns) can be toggled:
//   - locally via localStorage `webae_debug_<feature>` (user override, Settings UI)
//   - remotely via /api/config `debugFlags` (server-side gate, read-only mirror)
//
// A log line is emitted when EITHER source is true. Output goes to console
// .debug / .info / .warn so browsers can filter by verbosity. Replaces the
// temporary 127.0.0.1:7665 ingest calls used during debugging.

export type DebugFeature = 'icons' | 'chat' | 'dashboard' | 'synthesis' | 'patterns';
export type DebugLevel = 'debug' | 'info' | 'warn';

const LOCAL_KEY = (feature: DebugFeature) => `webae_debug_${feature}`;

// Server-side flags mirrored from /api/config (read-only display in Settings).
const serverFlags: Record<DebugFeature, boolean> = {
  icons: false,
  chat: false,
  dashboard: false,
  synthesis: false,
  patterns: false,
};

/** Update the cached server-side debug flags. Called by AppContext after /api/config. */
export function setServerDebugFlags(flags: Partial<Record<DebugFeature, boolean>>): void {
  if (!flags) return;
  for (const f of Object.keys(flags) as DebugFeature[]) {
    if (flags[f] !== undefined) {
      serverFlags[f] = !!flags[f];
    }
  }
}

/** Read the local override flag (returns null when unset). */
export function getLocalDebugFlag(feature: DebugFeature): boolean | null {
  try {
    const raw = localStorage.getItem(LOCAL_KEY(feature));
    if (raw === null) return null;
    return raw === 'true';
  } catch {
    return null;
  }
}

/** Persist a local override (Settings UI toggle). */
export function setLocalDebugFlag(feature: DebugFeature, enabled: boolean): void {
  try {
    localStorage.setItem(LOCAL_KEY(feature), enabled ? 'true' : 'false');
  } catch {
    /* ignore quota / private mode */
  }
}

/** True when either the local override or the server flag enables this feature. */
export function isDebugEnabled(feature: DebugFeature): boolean {
  const local = getLocalDebugFlag(feature);
  if (local !== null) return local;
  return serverFlags[feature] === true;
}

/** Read-only snapshot of the server-side flags (for Settings display). */
export function getServerDebugFlags(): Record<DebugFeature, boolean> {
  return { ...serverFlags };
}

/** Emit a debug log line when the feature is enabled. */
export function debugLog(feature: DebugFeature, level: DebugLevel, ...args: unknown[]): void {
  if (!isDebugEnabled(feature)) return;
  const tag = `[WebAE/${feature}]`;
  if (level === 'warn') {
    // eslint-disable-next-line no-console
    console.warn(tag, ...args);
  } else if (level === 'info') {
    // eslint-disable-next-line no-console
    console.info(tag, ...args);
  } else {
    // eslint-disable-next-line no-console
    console.debug(tag, ...args);
  }
}
