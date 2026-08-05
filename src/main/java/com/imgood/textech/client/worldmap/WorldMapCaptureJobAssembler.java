package com.imgood.textech.client.worldmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.imgood.textech.webae.network.PacketWorldMapCaptureJob;

/**
 * Bounded, order-independent client reassembly for paged capture jobs.
 */
public final class WorldMapCaptureJobAssembler {

    private static final int DEFAULT_MAX_PENDING = 8;
    private static final long DEFAULT_TTL_MS = 2L * 60L * 1000L;

    private final int maxPending;
    private final long ttlMs;
    private final Map<String, PendingAssembly> pending = new HashMap<String, PendingAssembly>();

    public WorldMapCaptureJobAssembler() {
        this(DEFAULT_MAX_PENDING, DEFAULT_TTL_MS);
    }

    WorldMapCaptureJobAssembler(int maxPending, long ttlMs) {
        if (maxPending < 1 || ttlMs < 1L) {
            throw new IllegalArgumentException("Capture-job assembler limits must be positive");
        }
        this.maxPending = maxPending;
        this.ttlMs = ttlMs;
    }

    synchronized AssembledJob acceptPage(PacketWorldMapCaptureJob page) {
        return acceptPage(page, System.currentTimeMillis());
    }

    synchronized AssembledJob acceptPage(PacketWorldMapCaptureJob page, long nowMs) {
        pruneExpired(nowMs);
        if (page == null || !page.isValid()) {
            return null;
        }
        String key = key(page.ownerUuid, page.networkId, page.snapshotVersion);
        PendingAssembly assembly = pending.get(key);
        if (assembly == null) {
            if (pending.size() >= maxPending) {
                return null;
            }
            assembly = new PendingAssembly(page, expiresAt(nowMs));
            pending.put(key, assembly);
        } else if (!assembly.matches(page)) {
            pending.remove(key);
            return null;
        }

        PageData incoming = new PageData(page.chunkOffset, page.chunks);
        PageData existing = assembly.pages.get(Integer.valueOf(page.pageIndex));
        if (existing != null) {
            if (!existing.equals(incoming)) {
                pending.remove(key);
            } else {
                assembly.expiresAtMs = expiresAt(nowMs);
            }
            return null;
        }
        assembly.pages.put(Integer.valueOf(page.pageIndex), incoming);
        assembly.expiresAtMs = expiresAt(nowMs);
        if (assembly.pages.size() != assembly.pageCount) {
            return null;
        }

        List<String> chunks = new ArrayList<String>(assembly.totalChunks);
        int expectedOffset = 0;
        for (int index = 0; index < assembly.pageCount; index++) {
            PageData pageData = assembly.pages.get(Integer.valueOf(index));
            if (pageData == null || pageData.chunkOffset != expectedOffset
                || pageData.chunks.size() > assembly.totalChunks - expectedOffset) {
                pending.remove(key);
                return null;
            }
            chunks.addAll(pageData.chunks);
            expectedOffset += pageData.chunks.size();
        }
        pending.remove(key);
        if (expectedOffset != assembly.totalChunks || chunks.size() != assembly.totalChunks) {
            return null;
        }
        return new AssembledJob(
            assembly.ownerUuid,
            assembly.networkId,
            assembly.snapshotVersion,
            assembly.tilePx,
            assembly.sourcePriority,
            chunks);
    }

    synchronized int pruneExpired() {
        return pruneExpired(System.currentTimeMillis());
    }

    synchronized int pruneExpired(long nowMs) {
        int removed = 0;
        Iterator<Map.Entry<String, PendingAssembly>> iterator = pending.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            PendingAssembly assembly = iterator.next()
                .getValue();
            if (assembly == null || nowMs >= assembly.expiresAtMs) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    synchronized void clear() {
        pending.clear();
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    private long expiresAt(long nowMs) {
        return nowMs > Long.MAX_VALUE - ttlMs ? Long.MAX_VALUE : nowMs + ttlMs;
    }

    private static String key(String ownerUuid, int networkId, int snapshotVersion) {
        return ownerUuid + '|' + networkId + '|' + snapshotVersion;
    }

    public static final class AssembledJob {

        public final String ownerUuid;
        public final int networkId;
        public final int snapshotVersion;
        public final int tilePx;
        public final String sourcePriority;
        public final List<String> chunks;

        private AssembledJob(String ownerUuid, int networkId, int snapshotVersion, int tilePx, String sourcePriority,
            List<String> chunks) {
            this.ownerUuid = ownerUuid;
            this.networkId = networkId;
            this.snapshotVersion = snapshotVersion;
            this.tilePx = tilePx;
            this.sourcePriority = sourcePriority;
            this.chunks = Collections.unmodifiableList(new ArrayList<String>(chunks));
        }
    }

    private static final class PendingAssembly {

        final String ownerUuid;
        final int networkId;
        final int snapshotVersion;
        final int tilePx;
        final String sourcePriority;
        final int pageCount;
        final int totalChunks;
        final Map<Integer, PageData> pages = new HashMap<Integer, PageData>();
        long expiresAtMs;

        PendingAssembly(PacketWorldMapCaptureJob page, long expiresAtMs) {
            ownerUuid = page.ownerUuid;
            networkId = page.networkId;
            snapshotVersion = page.snapshotVersion;
            tilePx = page.tilePx;
            sourcePriority = page.sourcePriority;
            pageCount = page.pageCount;
            totalChunks = page.totalChunks;
            this.expiresAtMs = expiresAtMs;
        }

        boolean matches(PacketWorldMapCaptureJob page) {
            return ownerUuid.equals(page.ownerUuid) && networkId == page.networkId
                && snapshotVersion == page.snapshotVersion && tilePx == page.tilePx && sourcePriority.equals(
                    page.sourcePriority) && pageCount == page.pageCount && totalChunks == page.totalChunks;
        }
    }

    private static final class PageData {

        final int chunkOffset;
        final List<String> chunks;

        PageData(int chunkOffset, List<String> chunks) {
            this.chunkOffset = chunkOffset;
            this.chunks = new ArrayList<String>(chunks);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PageData)) {
                return false;
            }
            PageData page = (PageData) other;
            return chunkOffset == page.chunkOffset && chunks.equals(page.chunks);
        }

        @Override
        public int hashCode() {
            return 31 * chunkOffset + chunks.hashCode();
        }
    }
}
