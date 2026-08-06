import { useEffect, useRef } from 'react';
import { useAppContext } from '@/context/AppContext';
import {
  SERVER_SYNC_PACK_NAME,
  setActiveLocalPack,
  syncServerIconPack,
} from '@/utils/localIconPack';
import { debugLog } from '@/utils/debugLog';

/** After login, bulk-sync server icon pack into IndexedDB when revision changes. */
export function useIconPackAutoSync() {
  const {
    isLoggedIn,
    token,
    iconPack,
    iconRenderMode,
    iconCacheEnabled,
    iconAutoSyncEnabled,
    refreshLocalIconPacks,
    setLocalIconPack,
  } = useAppContext();
  const syncingRef = useRef(false);

  useEffect(() => {
    if (!isLoggedIn || !token || !iconCacheEnabled || !iconAutoSyncEnabled) return;
    if (syncingRef.current) return;

    syncingRef.current = true;
    let cancelled = false;

    (async () => {
      try {
        const result = await syncServerIconPack({
          pack: iconPack || 'default',
          mode: iconRenderMode || 'nei',
          token,
        });
        if (cancelled) return;
        if (result.updated || result.iconCount > 0) {
          setActiveLocalPack(SERVER_SYNC_PACK_NAME);
          setLocalIconPack(SERVER_SYNC_PACK_NAME);
          await refreshLocalIconPacks();
          debugLog(
            'icons',
            'info',
            'icon auto-sync complete: count={} version={} updated={}',
            result.iconCount,
            result.version,
            result.updated
          );
        }
      } catch (e) {
        if (!cancelled) {
          debugLog('icons', 'warn', 'icon auto-sync failed: {}', (e as Error).message);
        }
      } finally {
        syncingRef.current = false;
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [
    isLoggedIn,
    token,
    iconPack,
    iconRenderMode,
    iconCacheEnabled,
    iconAutoSyncEnabled,
    refreshLocalIconPacks,
    setLocalIconPack,
  ]);
}
