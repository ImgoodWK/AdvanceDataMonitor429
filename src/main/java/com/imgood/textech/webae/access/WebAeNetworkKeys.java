package com.imgood.textech.webae.access;

import java.util.List;

import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.NetworkRegistry.RegisteredNetwork;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;

/**
 * Stable WebAE network identity: {@code "{dim}:{x}:{y}:{z}"} from the data monitor.
 * Runtime {@code networkId} is a sorted index and must not be persisted in ACL.
 *
 * <p>{@link #fromNetworkId} / {@link #toNetworkId} resolve via the registry key index only and must
 * never touch {@link net.minecraft.world.World} (WebAE HTTP threads call them on every
 * {@code ?network=} request).</p>
 */
public final class WebAeNetworkKeys {

    private WebAeNetworkKeys() {}

    public static String toKey(int dim, int x, int y, int z) {
        return dim + ":" + x + ":" + y + ":" + z;
    }

    public static String fromGroup(NetworkGroup group) {
        if (group == null) {
            return null;
        }
        return toKey(group.monitorDim, group.monitorX, group.monitorY, group.monitorZ);
    }

    public static String fromRegistered(RegisteredNetwork entry) {
        if (entry == null) {
            return null;
        }
        return toKey(entry.monitorDim, entry.monitorX, entry.monitorY, entry.monitorZ);
    }

    public static String fromNetworkInfo(NetworkInfo info) {
        if (info == null) {
            return null;
        }
        if (info.networkKey != null && !info.networkKey.isEmpty()) {
            return info.networkKey;
        }
        return toKey(info.monitorDim, info.monitorX, info.monitorY, info.monitorZ);
    }

    /** Resolve stable key for a runtime networkId (same index as API {@code ?network=}). */
    public static String fromNetworkId(String ownerUuid, int networkId) {
        return NetworkRegistry.keyForNetworkId(ownerUuid, networkId);
    }

    /** Map a stable key back to the current runtime networkId, or null if not registered. */
    public static Integer toNetworkId(String ownerUuid, String networkKey) {
        return NetworkRegistry.networkIdForKey(ownerUuid, networkKey);
    }

    public static boolean isValidKeyFormat(String networkKey) {
        if (networkKey == null || networkKey.isEmpty()) {
            return false;
        }
        String[] parts = networkKey.split(":");
        if (parts.length != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            try {
                Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /** Best-effort key from raw registry when groups are empty (admin metadata). */
    public static String fromRawIndex(String ownerUuid, int rawIndex) {
        List<RegisteredNetwork> raw = NetworkRegistry.getRawNetworks(ownerUuid);
        if (raw == null || rawIndex < 0 || rawIndex >= raw.size()) {
            return null;
        }
        return fromRegistered(raw.get(rawIndex));
    }
}
