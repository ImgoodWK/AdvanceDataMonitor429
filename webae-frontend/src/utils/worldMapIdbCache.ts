const DB_NAME = 'textech-worldmap';
const DB_VERSION = 1;
const STORE = 'tiles';

export interface WorldMapIdbKeyParts {
  ownerKey: string;
  networkId: number;
  version: number;
  layer: string;
  dim: number;
  chunkX: number;
  chunkZ: number;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onerror = () => reject(req.error);
    req.onsuccess = () => resolve(req.result);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE);
      }
    };
  });
}

export function buildIdbKey(parts: WorldMapIdbKeyParts): string {
  return `${parts.ownerKey}:${parts.networkId}:v${parts.version}:${parts.layer}:${parts.dim}:${parts.chunkX}:${parts.chunkZ}`;
}

export async function getCachedTileBlob(key: string): Promise<Blob | null> {
  try {
    const db = await openDb();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, 'readonly');
      const store = tx.objectStore(STORE);
      const req = store.get(key);
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const val = req.result;
        resolve(val instanceof Blob ? val : null);
      };
    });
  } catch {
    return null;
  }
}

export async function putCachedTileBlob(key: string, blob: Blob): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      const req = store.put(blob, key);
      req.onerror = () => reject(req.error);
      req.onsuccess = () => resolve();
    });
  } catch {
    // ignore quota / private mode
  }
}

export async function purgeOldSnapshotVersions(
  ownerKey: string,
  networkId: number,
  keepVersions: number[]
): Promise<void> {
  const keep = new Set(keepVersions.filter((v) => v > 0));
  if (keep.size === 0) {
    return;
  }
  try {
    const db = await openDb();
    const prefix = `${ownerKey}:${networkId}:v`;
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      const req = store.openCursor();
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const cursor = req.result;
        if (cursor) {
          const key = String(cursor.key);
          if (key.startsWith(prefix)) {
            const rest = key.slice(prefix.length);
            const versionEnd = rest.indexOf(':');
            const versionStr = versionEnd >= 0 ? rest.slice(0, versionEnd) : rest;
            const version = parseInt(versionStr, 10);
            if (!Number.isNaN(version) && !keep.has(version)) {
              cursor.delete();
            }
          }
          cursor.continue();
        } else {
          resolve();
        }
      };
    });
  } catch {
    // ignore
  }
}

export async function clearNetworkCache(ownerKey: string, networkId: number): Promise<void> {
  try {
    const db = await openDb();
    const prefix = `${ownerKey}:${networkId}:`;
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      const store = tx.objectStore(STORE);
      const req = store.openCursor();
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const cursor = req.result;
        if (cursor) {
          const key = String(cursor.key);
          if (key.startsWith(prefix)) {
            cursor.delete();
          }
          cursor.continue();
        } else {
          resolve();
        }
      };
    });
  } catch {
    // ignore
  }
}
