package com.imgood.textech.webae.worldmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Tracks per-chunk terrain/ae tile render progress for {@code GET /api/worldmap/progress}.
 * Progress is scoped by {@code network + dim + view + quality} so unrelated networks or
 * sessions do not inflate totals.
 */
public final class WorldMapTileProgressTracker {

    public static final String QUEUED = "queued";
    public static final String RENDERING = "rendering";
    public static final String DONE = "done";
    public static final String EMPTY = "empty";
    public static final String FAILED = "failed";

    private static final int MAX_SESSIONS = 64;
    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final WorldMapTileProgressTracker INSTANCE = new WorldMapTileProgressTracker();

    /** LRU-ordered session map: key = network|dim|view|quality */
    private final LinkedHashMap<String, SessionState> sessions = new LinkedHashMap<String, SessionState>(16, 0.75f,
        true);

    private WorldMapTileProgressTracker() {}

    public static WorldMapTileProgressTracker instance() {
        return INSTANCE;
    }

    /**
     * Clears progress for one scoped session (e.g. before prefetch for that view).
     */
    public void beginSession(int networkId, String view, WorldMapQualityTier quality, int dim) {
        String key = sessionKey(networkId, view, quality, dim);
        synchronized (sessions) {
            SessionState state = new SessionState();
            state.networkId = normNetwork(networkId);
            state.view = normalizeView(view);
            state.quality = quality != null ? quality.id : WorldMapQualityTier.MEDIUM.id;
            state.dim = dim;
            sessions.put(key, state);
            trimSessions();
        }
    }

    public void markQueued(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer) {
        setLayerState(networkId, view, quality, dim, chunkX, chunkZ, layer, QUEUED);
    }

    public void markRendering(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer) {
        setLayerState(networkId, view, quality, dim, chunkX, chunkZ, layer, RENDERING);
    }

    public void markDone(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer) {
        setLayerState(networkId, view, quality, dim, chunkX, chunkZ, layer, DONE);
    }

    public void markEmpty(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer) {
        setLayerState(networkId, view, quality, dim, chunkX, chunkZ, layer, EMPTY);
    }

    public void markFailed(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer) {
        setLayerState(networkId, view, quality, dim, chunkX, chunkZ, layer, FAILED);
    }

    public String toJson(int networkId, String viewFilter, WorldMapQualityTier qualityFilter, int dimFilter) {
        ProgressSnapshot snap = snapshot(networkId, viewFilter, qualityFilter, dimFilter);
        mergeAeOverlaySession(networkId, viewFilter, qualityFilter, dimFilter, snap);
        recomputeTotals(snap);
        return GSON.toJson(snap);
    }

    /** AE overlay uses its own quality tier session; merge into terrain progress response. */
    private void mergeAeOverlaySession(int networkId, String viewFilter, WorldMapQualityTier terrainQuality,
        int dimFilter, ProgressSnapshot snap) {
        WorldMapQualityTier aeTier = WorldMapQualityTier.fromConfigAeOverlay();
        if (terrainQuality != null && aeTier.equals(terrainQuality)) {
            return;
        }
        String aeKey = sessionKey(networkId, viewFilter, aeTier, dimFilter);
        synchronized (sessions) {
            SessionState aeSession = sessions.get(aeKey);
            if (aeSession == null) {
                return;
            }
            for (Map.Entry<String, ChunkProgress> e : aeSession.chunks.entrySet()) {
                if (e.getValue() == null || e.getValue().ae == null) {
                    continue;
                }
                ChunkProgress merged = snap.chunks.get(e.getKey());
                if (merged == null) {
                    merged = new ChunkProgress();
                    snap.chunks.put(e.getKey(), merged);
                }
                merged.ae = e.getValue().ae;
            }
        }
    }

    private static void recomputeTotals(ProgressSnapshot snap) {
        int total = 0;
        int completed = 0;
        if (snap.chunks != null) {
            for (ChunkProgress cp : snap.chunks.values()) {
                if (cp == null) {
                    continue;
                }
                if (cp.terrain != null) {
                    total++;
                    if (isTerminal(cp.terrain)) {
                        completed++;
                    }
                }
                if (cp.ae != null) {
                    total++;
                    if (isTerminal(cp.ae)) {
                        completed++;
                    }
                }
            }
        }
        snap.total = total;
        snap.completed = completed;
    }

    private void setLayerState(int networkId, String view, WorldMapQualityTier quality, int dim, int chunkX, int chunkZ,
        String layer, String state) {
        String normLayer = WorldMapTileLayer.normalize(layer);
        String chunkKey = chunkX + "," + chunkZ;
        String session = sessionKey(networkId, view, quality, dim);
        synchronized (sessions) {
            SessionState sessionState = sessions.get(session);
            if (sessionState == null) {
                sessionState = new SessionState();
                sessionState.networkId = normNetwork(networkId);
                sessionState.view = normalizeView(view);
                sessionState.quality = quality != null ? quality.id : WorldMapQualityTier.MEDIUM.id;
                sessionState.dim = dim;
                sessions.put(session, sessionState);
                trimSessions();
            }
            ChunkProgress cp = sessionState.chunks.get(chunkKey);
            if (cp == null) {
                cp = new ChunkProgress();
                sessionState.chunks.put(chunkKey, cp);
            }
            if (WorldMapTileLayer.AE.equals(normLayer)) {
                cp.ae = state;
            } else {
                cp.terrain = state;
            }
        }
    }

    private ProgressSnapshot snapshot(int networkId, String viewFilter, WorldMapQualityTier qualityFilter,
        int dimFilter) {
        ProgressSnapshot out = new ProgressSnapshot();
        out.success = true;
        out.networkId = normNetwork(networkId);
        out.view = normalizeView(viewFilter);
        out.quality = qualityFilter != null ? qualityFilter.id : WorldMapQualityTier.MEDIUM.id;
        out.dim = dimFilter;
        int total = 0;
        int completed = 0;
        String key = sessionKey(networkId, viewFilter, qualityFilter, dimFilter);
        synchronized (sessions) {
            SessionState sessionState = sessions.get(key);
            if (sessionState == null) {
                out.chunks = new HashMap<String, ChunkProgress>();
                out.total = 0;
                out.completed = 0;
                return out;
            }
            out.chunks = new HashMap<String, ChunkProgress>(sessionState.chunks);
            for (Map.Entry<String, ChunkProgress> e : sessionState.chunks.entrySet()) {
                ChunkProgress cp = e.getValue();
                if (cp.terrain != null) {
                    total++;
                    if (isTerminal(cp.terrain)) {
                        completed++;
                    }
                }
                if (cp.ae != null) {
                    total++;
                    if (isTerminal(cp.ae)) {
                        completed++;
                    }
                }
            }
        }
        out.total = total;
        out.completed = completed;
        return out;
    }

    private static boolean isTerminal(String state) {
        return DONE.equals(state) || EMPTY.equals(state) || FAILED.equals(state);
    }

    private static int normNetwork(int networkId) {
        return networkId < 0 ? 0 : networkId;
    }

    private static String normalizeView(String view) {
        return view != null && !view.isEmpty() ? view : WorldMapView.FLAT.id;
    }

    private static String sessionKey(int networkId, String view, WorldMapQualityTier quality, int dim) {
        String q = quality != null ? quality.id : WorldMapQualityTier.MEDIUM.id;
        return normNetwork(networkId) + "|" + dim + "|" + normalizeView(view) + "|" + q;
    }

    private void trimSessions() {
        while (sessions.size() > MAX_SESSIONS) {
            Iterator<Map.Entry<String, SessionState>> it = sessions.entrySet()
                .iterator();
            if (!it.hasNext()) {
                break;
            }
            it.next();
            it.remove();
        }
    }

    public static final class ProgressSnapshot {

        public boolean success = true;
        public int networkId;
        public String quality;
        public String view;
        public int dim;
        public int total;
        public int completed;
        public Map<String, ChunkProgress> chunks = new HashMap<String, ChunkProgress>();
    }

    public static final class ChunkProgress {

        public String terrain;
        public String ae;
    }

    private static final class SessionState {

        int networkId;
        String view;
        String quality;
        int dim;
        final Map<String, ChunkProgress> chunks = new HashMap<String, ChunkProgress>();
    }
}

