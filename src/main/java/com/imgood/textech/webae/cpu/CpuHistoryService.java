package com.imgood.textech.webae.cpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.dto.StorageDto.CpuEntry;

/**
 * In-memory CPU-history cache and lifecycle bridge.
 *
 * <p>
 * Collection-side callers load and mutate the backing state. Read APIs use
 * {@link #getHistory} and {@link #getCapacity}, which deliberately never load
 * files, resolve an AE grid, or otherwise leave the in-memory cache.
 * </p>
 */
public final class CpuHistoryService {

    public static final int MAX_JOBS_PER_NETWORK = 1000;
    public static final int MAX_SNAPSHOTS_PER_NETWORK = 4096;
    public static final int MAX_HISTORY_JOBS_RESPONSE = 500;
    public static final int MAX_HISTORY_SNAPSHOTS_RESPONSE = 1000;
    public static final long RETENTION_MS = 14L * 24L * 60L * 60L * 1000L;

    private static final long FLUSH_INTERVAL_MS = 30_000L;
    private static final CpuHistoryService INSTANCE = new CpuHistoryService(new CpuHistoryStore());

    private final CpuHistoryStore store;
    private final Map<String, CpuHistoryState> states = new HashMap<String, CpuHistoryState>();
    private long lastFlushAt;

    CpuHistoryService(CpuHistoryStore store) {
        this.store = store;
    }

    public static CpuHistoryService instance() {
        return INSTANCE;
    }

    /**
     * Records a queued WebAE job using the registry-only stable network key
     * resolver. Call this from a server-side order task, not an HTTP handler.
     */
    public synchronized void recordQueued(String ownerUuid, int networkId, String jobId, String cpuName,
        String recipeKey) {
        recordQueued(
            ownerUuid,
            networkId,
            WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId),
            jobId,
            cpuName,
            recipeKey,
            System.currentTimeMillis());
    }

    public synchronized void recordQueued(String ownerUuid, int networkId, String networkKey, String jobId,
        String cpuName, String recipeKey, long now) {
        if (jobId == null || jobId.trim()
            .isEmpty()) {
            return;
        }
        CpuHistoryState state = stateForMutation(ownerUuid, networkId, networkKey, now);
        if (state == null) {
            return;
        }
        CpuJobHistoryDto job = findJob(state, jobId);
        if (job == null) {
            job = new CpuJobHistoryDto();
            job.jobId = jobId;
            job.ownerUuid = ownerUuid;
            job.networkId = networkId;
            job.networkKey = networkKey;
            job.queuedAt = now;
            state.jobs.add(job);
        }
        if (!job.isTerminal()) {
            if (job.queuedAt <= 0L) {
                job.queuedAt = now;
            }
            if (!CpuJobHistoryDto.STATUS_RUNNING.equals(job.status)
                && !CpuJobHistoryDto.STATUS_STUCK.equals(job.status)) {
                job.status = CpuJobHistoryDto.STATUS_QUEUED;
            }
            applyCpuAndRecipe(job, cpuName, recipeKey);
            markDirtyAndMaybeFlush(state, now);
        }
    }

    public synchronized void recordRunning(String ownerUuid, int networkId, String jobId, String cpuName,
        Integer progress, Integer coProcessors) {
        recordRunning(
            ownerUuid,
            networkId,
            WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId),
            jobId,
            cpuName,
            progress,
            coProcessors,
            System.currentTimeMillis());
    }

    public synchronized void recordRunning(String ownerUuid, int networkId, String networkKey, String jobId,
        String cpuName, Integer progress, Integer coProcessors, long now) {
        if (jobId == null || jobId.trim()
            .isEmpty()) {
            return;
        }
        CpuHistoryState state = stateForMutation(ownerUuid, networkId, networkKey, now);
        if (state == null) {
            return;
        }
        CpuJobHistoryDto job = findJob(state, jobId);
        if (job == null) {
            job = createObservedJob(ownerUuid, networkId, networkKey, jobId, now);
            state.jobs.add(job);
        }
        if (job.isTerminal()) {
            return;
        }
        if (job.startedAt <= 0L) {
            job.startedAt = now;
            if (job.queuedAt > 0L) {
                job.queueMs = Long.valueOf(Math.max(0L, now - job.queuedAt));
            }
        }
        if (!CpuJobHistoryDto.STATUS_STUCK.equals(job.status)) {
            job.status = CpuJobHistoryDto.STATUS_RUNNING;
        }
        applyCpu(job, cpuName);
        if (coProcessors != null && coProcessors.intValue() >= 0) {
            job.coProcessors = Integer.valueOf(coProcessors.intValue());
        }
        Integer safeProgress = normalizeProgress(progress);
        if (safeProgress != null) {
            job.progress = safeProgress;
        }
        markDirtyAndMaybeFlush(state, now);
    }

    public synchronized void recordTerminal(String ownerUuid, int networkId, String jobId, String status,
        String cpuName, Integer progress, long finishedAt) {
        recordTerminal(
            ownerUuid,
            networkId,
            WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId),
            jobId,
            status,
            cpuName,
            progress,
            finishedAt);
    }

    public synchronized void recordTerminal(String ownerUuid, int networkId, String networkKey, String jobId,
        String status, String cpuName, Integer progress, long finishedAt) {
        if (jobId == null || jobId.trim()
            .isEmpty() || !isTerminalStatus(status)) {
            return;
        }
        long now = finishedAt > 0L ? finishedAt : System.currentTimeMillis();
        CpuHistoryState state = stateForMutation(ownerUuid, networkId, networkKey, now);
        if (state == null) {
            return;
        }
        CpuJobHistoryDto job = findJob(state, jobId);
        if (job == null) {
            job = createObservedJob(ownerUuid, networkId, networkKey, jobId, now);
            state.jobs.add(job);
        }
        if (job.isTerminal()) {
            // A terminal observation is the first trusted result for this
            // job. Ignore both duplicate and contradictory callbacks so a
            // late AE/order callback cannot mutate the historical record.
            return;
        }
        job.status = status;
        if (job.finishedAt <= 0L) {
            job.finishedAt = now;
        }
        applyCpu(job, cpuName);
        Integer safeProgress = normalizeProgress(progress);
        if (CpuJobHistoryDto.STATUS_COMPLETED.equals(status)) {
            job.progress = Integer.valueOf(100);
        } else if (safeProgress != null) {
            job.progress = safeProgress;
        }
        if (job.startedAt > 0L && job.finishedAt >= job.startedAt) {
            job.durationMs = Long.valueOf(job.finishedAt - job.startedAt);
            if (job.queuedAt > 0L) {
                job.queueMs = Long.valueOf(Math.max(0L, job.startedAt - job.queuedAt));
            }
        }
        markDirtyAndMaybeFlush(state, now);
    }

    /**
     * Records a trusted CPU-stuck observation. If no one-to-one WebAE order
     * can be found, the local record is deliberately excluded from concurrency
     * estimates while remaining visible as a stuck observation.
     */
    public synchronized void markCpuStuck(String ownerUuid, int networkId, String cpuName, long now) {
        String networkKey = WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId);
        CpuHistoryState state = stateForMutation(ownerUuid, networkId, networkKey, now);
        if (state == null) {
            return;
        }
        String safeCpuName = sanitizeCpuName(cpuName);
        if (safeCpuName == null) {
            return;
        }
        CpuJobHistoryDto candidate = null;
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job == null || job.isTerminal() || !safeCpuName.equals(job.cpuName)) {
                continue;
            }
            if (!CpuJobHistoryDto.STATUS_QUEUED.equals(job.status)
                && !CpuJobHistoryDto.STATUS_RUNNING.equals(job.status)
                && !CpuJobHistoryDto.STATUS_STUCK.equals(job.status)) {
                continue;
            }
            if (candidate == null || job.latestTimestamp() > candidate.latestTimestamp()) {
                candidate = job;
            }
        }
        if (candidate == null) {
            candidate = createObservedJob(
                ownerUuid,
                networkId,
                networkKey,
                "local-stuck-" + now + "-" + Integer.toHexString(safeCpuName.hashCode()),
                now);
            candidate.cpuName = safeCpuName;
            // This is an observation, not an invented job interval.
            candidate.startedAt = 0L;
            state.jobs.add(candidate);
        }
        candidate.status = CpuJobHistoryDto.STATUS_STUCK;
        markDirtyAndMaybeFlush(state, now);
    }

    /**
     * Called only by the existing server-tick metric sampler after it has
     * copied an AE storage snapshot into {@link CpuEntry} DTOs.
     */
    public synchronized void recordCpuSnapshots(String ownerUuid, int networkId, List<CpuEntry> cpus, long now) {
        recordCpuSnapshots(ownerUuid, networkId, WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId), cpus, now);
    }

    public synchronized void recordCpuSnapshots(String ownerUuid, int networkId, String networkKey, List<CpuEntry> cpus,
        long now) {
        CpuHistoryState state = stateForMutation(ownerUuid, networkId, networkKey, now);
        if (state == null) {
            return;
        }
        if (cpus != null) {
            for (int i = 0; i < cpus.size(); i++) {
                CpuEntry cpu = cpus.get(i);
                CpuSnapshotHistoryDto snapshot = toSnapshot(cpu, now);
                if (snapshot == null) {
                    continue;
                }
                state.snapshots.add(snapshot);
                enrichMatchingJobs(state, snapshot);
            }
        }
        markDirtyAndMaybeFlush(state, now);
    }

    /**
     * Reads only the active in-memory state. A missing state is intentionally
     * empty until the normal server-tick sampler activates and loads it.
     */
    public synchronized CpuHistoryResponseDto getHistory(String ownerUuid, int networkId, String networkKey, long from,
        long to, int jobLimit) {
        CpuHistoryResponseDto response = new CpuHistoryResponseDto();
        response.networkId = networkId;
        response.networkKey = networkKey;
        response.from = from;
        response.to = to;

        CpuHistoryState state = stateForRead(ownerUuid, networkId, networkKey);
        if (state == null) {
            return response;
        }

        // HTTP reads are intentionally non-mutating. Retention is applied
        // to copied DTOs below so an expired entry is omitted from this
        // response without trimming the active cache or marking it dirty.
        long cutoff = retentionCutoff(System.currentTimeMillis());
        long effectiveFrom = Math.max(from, cutoff);

        List<CpuJobHistoryDto> jobs = new ArrayList<CpuJobHistoryDto>();
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job != null && job.latestTimestamp() >= cutoff && intersects(job, effectiveFrom, to)) {
                jobs.add(job.copy());
            }
        }
        Collections.sort(jobs, new Comparator<CpuJobHistoryDto>() {

            @Override
            public int compare(CpuJobHistoryDto a, CpuJobHistoryDto b) {
                return a.latestTimestamp() > b.latestTimestamp() ? -1
                    : a.latestTimestamp() == b.latestTimestamp() ? 0 : 1;
            }
        });

        int boundedJobLimit = Math.max(1, Math.min(MAX_HISTORY_JOBS_RESPONSE, jobLimit));
        if (jobs.size() > boundedJobLimit) {
            response.truncated = true;
            jobs = new ArrayList<CpuJobHistoryDto>(jobs.subList(0, boundedJobLimit));
        }
        response.jobs.addAll(jobs);

        List<CpuSnapshotHistoryDto> snapshots = new ArrayList<CpuSnapshotHistoryDto>();
        for (int i = 0; i < state.snapshots.size(); i++) {
            CpuSnapshotHistoryDto snapshot = state.snapshots.get(i);
            if (snapshot != null && snapshot.timestamp >= effectiveFrom && snapshot.timestamp <= to) {
                snapshots.add(snapshot.copy());
            }
        }
        Collections.sort(snapshots, new Comparator<CpuSnapshotHistoryDto>() {

            @Override
            public int compare(CpuSnapshotHistoryDto a, CpuSnapshotHistoryDto b) {
                return a.timestamp < b.timestamp ? -1 : a.timestamp == b.timestamp ? 0 : 1;
            }
        });
        if (snapshots.size() > MAX_HISTORY_SNAPSHOTS_RESPONSE) {
            response.truncated = true;
            snapshots = new ArrayList<CpuSnapshotHistoryDto>(
                snapshots.subList(snapshots.size() - MAX_HISTORY_SNAPSHOTS_RESPONSE, snapshots.size()));
        }
        response.snapshots.addAll(snapshots);
        return response;
    }

    /** Reads only the active in-memory state; see {@link #getHistory}. */
    public synchronized CpuCapacityPlanDto getCapacity(String ownerUuid, int networkId, String networkKey,
        String window, long from, long to) {
        CpuHistoryState state = stateForRead(ownerUuid, networkId, networkKey);
        if (state == null) {
            return CpuCapacityPlanner.plan(
                networkId,
                networkKey,
                window,
                from,
                to,
                Collections.<CpuJobHistoryDto>emptyList(),
                Collections.<CpuSnapshotHistoryDto>emptyList());
        }
        // As with getHistory, do not trim or otherwise mutate the active
        // state on an HTTP/read path. Feed the planner only copied records
        // which are still inside the retention window.
        long cutoff = retentionCutoff(System.currentTimeMillis());
        long effectiveFrom = Math.max(from, cutoff);
        List<CpuJobHistoryDto> jobs = new ArrayList<CpuJobHistoryDto>();
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job != null && job.latestTimestamp() >= cutoff && intersects(job, effectiveFrom, to)) {
                jobs.add(job.copy());
            }
        }
        List<CpuSnapshotHistoryDto> snapshots = new ArrayList<CpuSnapshotHistoryDto>();
        for (int i = 0; i < state.snapshots.size(); i++) {
            CpuSnapshotHistoryDto snapshot = state.snapshots.get(i);
            if (snapshot != null && snapshot.timestamp >= effectiveFrom && snapshot.timestamp <= to) {
                snapshots.add(snapshot.copy());
            }
        }
        return CpuCapacityPlanner.plan(networkId, networkKey, window, from, to, jobs, snapshots);
    }

    /** Flushes all dirty states before a WebAE or server shutdown. */
    public synchronized void flushAll() {
        flushDirty(System.currentTimeMillis());
    }

    // Test seam: the singleton itself remains production-safe because read APIs
    // never invoke this method.
    public synchronized void putForTests(CpuHistoryState state) {
        if (state == null || !isIdentityValid(state.ownerUuid, state.networkId, state.networkKey)) {
            return;
        }
        if (state.jobs == null) {
            state.jobs = new ArrayList<CpuJobHistoryDto>();
        }
        if (state.snapshots == null) {
            state.snapshots = new ArrayList<CpuSnapshotHistoryDto>();
        }
        state.dirty = false;
        states.put(stateKey(state.ownerUuid, state.networkKey), state);
    }

    public synchronized void clearForTests() {
        states.clear();
        lastFlushAt = 0L;
    }

    private CpuHistoryState stateForMutation(String ownerUuid, int networkId, String networkKey, long now) {
        if (!isIdentityValid(ownerUuid, networkId, networkKey)) {
            return null;
        }
        String key = stateKey(ownerUuid, networkKey);
        CpuHistoryState state = states.get(key);
        if (state == null) {
            state = store.load(ownerUuid, networkId, networkKey);
            recoverInterruptedJobs(state);
            if (trimState(state, now)) {
                state.dirty = true;
            }
            states.put(key, state);
        }
        return state;
    }

    private CpuHistoryState stateForRead(String ownerUuid, int networkId, String networkKey) {
        if (!isIdentityValid(ownerUuid, networkId, networkKey)) {
            return null;
        }
        return states.get(stateKey(ownerUuid, networkKey));
    }

    private static boolean isIdentityValid(String ownerUuid, int networkId, String networkKey) {
        return CpuHistoryStore.isSafeOwnerUuid(ownerUuid) && networkId >= 0
            && WebAeNetworkKeys.isValidKeyFormat(networkKey);
    }

    private static String stateKey(String ownerUuid, String networkKey) {
        return ownerUuid + "|" + networkKey;
    }

    private static CpuJobHistoryDto createObservedJob(String ownerUuid, int networkId, String networkKey, String jobId,
        long now) {
        CpuJobHistoryDto job = new CpuJobHistoryDto();
        job.jobId = jobId;
        job.ownerUuid = ownerUuid;
        job.networkId = networkId;
        job.networkKey = networkKey;
        job.queuedAt = now;
        job.status = CpuJobHistoryDto.STATUS_QUEUED;
        return job;
    }

    private static CpuJobHistoryDto findJob(CpuHistoryState state, String jobId) {
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job != null && jobId.equals(job.jobId)) {
                return job;
            }
        }
        return null;
    }

    private static void applyCpuAndRecipe(CpuJobHistoryDto job, String cpuName, String recipeKey) {
        applyCpu(job, cpuName);
        String safeRecipeKey = normalizeRecipeKey(recipeKey);
        if (safeRecipeKey != null) {
            job.recipeKey = safeRecipeKey;
        }
    }

    private static void applyCpu(CpuJobHistoryDto job, String cpuName) {
        String safeCpuName = sanitizeCpuName(cpuName);
        if (safeCpuName != null) {
            job.cpuName = safeCpuName;
        }
    }

    private static CpuSnapshotHistoryDto toSnapshot(CpuEntry cpu, long now) {
        if (cpu == null) {
            return null;
        }
        String cpuName = sanitizeCpuName(cpu.name);
        if (cpuName == null) {
            return null;
        }
        CpuSnapshotHistoryDto snapshot = new CpuSnapshotHistoryDto();
        snapshot.timestamp = now;
        snapshot.cpuName = cpuName;
        snapshot.busy = cpu.isBusy;
        snapshot.storageUsed = Math.max(0L, cpu.usedStorage);
        snapshot.storageMax = safeStorageMax(cpu.availableStorage, cpu.usedStorage);
        snapshot.progress = normalizeSnapshotProgress(cpu.craftingProgress);
        snapshot.coProcessors = Math.max(0, cpu.coProcessors);
        return snapshot;
    }

    private static long safeStorageMax(long availableStorage, long usedStorage) {
        long available = Math.max(0L, availableStorage);
        long used = Math.max(0L, usedStorage);
        if (Long.MAX_VALUE - used < available) {
            return Long.MAX_VALUE;
        }
        return used + available;
    }

    private static double normalizeSnapshotProgress(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static Integer normalizeProgress(Integer value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(Math.max(0, Math.min(100, value.intValue())));
    }

    private static String sanitizeCpuName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < trimmed.length() && out.length() < 128; i++) {
            char c = trimmed.charAt(i);
            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.length() > 0 ? out.toString() : null;
    }

    /**
     * Accept only canonical identifier-shaped recipe data. Order request text
     * and display names intentionally fail this filter and are never persisted.
     */
    static String normalizeRecipeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 128
            || !normalized.matches("[a-z0-9_-][a-z0-9._:/#-]{0,127}")) {
            return null;
        }
        if (normalized.indexOf(':') < 0 && normalized.indexOf('/') < 0 && normalized.indexOf('#') < 0) {
            return null;
        }
        return normalized;
    }

    private static void enrichMatchingJobs(CpuHistoryState state, CpuSnapshotHistoryDto snapshot) {
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job == null || job.isTerminal() || !snapshot.cpuName.equals(job.cpuName)) {
                continue;
            }
            if (!CpuJobHistoryDto.STATUS_QUEUED.equals(job.status)
                && !CpuJobHistoryDto.STATUS_RUNNING.equals(job.status)
                && !CpuJobHistoryDto.STATUS_STUCK.equals(job.status)) {
                continue;
            }
            job.coProcessors = Integer.valueOf(snapshot.coProcessors);
            job.storageUsed = Long.valueOf(snapshot.storageUsed);
            job.storageMax = Long.valueOf(snapshot.storageMax);
        }
    }

    private static boolean isTerminalStatus(String status) {
        return CpuJobHistoryDto.STATUS_COMPLETED.equals(status) || CpuJobHistoryDto.STATUS_FAILED.equals(status)
            || CpuJobHistoryDto.STATUS_CANCELLED.equals(status);
    }

    private static boolean intersects(CpuJobHistoryDto job, long from, long to) {
        long start = job.queuedAt > 0L ? job.queuedAt : job.startedAt;
        if (start <= 0L) {
            return false;
        }
        long end = job.finishedAt > 0L ? job.finishedAt : to;
        return start <= to && end >= from;
    }

    private static long retentionCutoff(long now) {
        if (now <= RETENTION_MS) {
            return 0L;
        }
        return now - RETENTION_MS;
    }

    private void recoverInterruptedJobs(CpuHistoryState state) {
        if (state == null || state.jobs == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < state.jobs.size(); i++) {
            CpuJobHistoryDto job = state.jobs.get(i);
            if (job == null) {
                continue;
            }
            if (!CpuJobHistoryDto.isKnownStatus(job.status) || CpuJobHistoryDto.STATUS_QUEUED.equals(job.status)
                || CpuJobHistoryDto.STATUS_RUNNING.equals(job.status)) {
                job.status = CpuJobHistoryDto.STATUS_UNKNOWN;
                changed = true;
            }
        }
        if (changed) {
            state.dirty = true;
        }
    }

    private static boolean trimState(CpuHistoryState state, long now) {
        if (state == null) {
            return false;
        }
        boolean changed = false;
        long cutoff = now - RETENTION_MS;
        if (state.jobs == null) {
            state.jobs = new ArrayList<CpuJobHistoryDto>();
            changed = true;
        }
        if (state.snapshots == null) {
            state.snapshots = new ArrayList<CpuSnapshotHistoryDto>();
            changed = true;
        }

        Iterator<CpuJobHistoryDto> jobIterator = state.jobs.iterator();
        while (jobIterator.hasNext()) {
            CpuJobHistoryDto job = jobIterator.next();
            if (job == null || job.latestTimestamp() <= 0L || job.latestTimestamp() < cutoff) {
                jobIterator.remove();
                changed = true;
            }
        }
        Iterator<CpuSnapshotHistoryDto> snapshotIterator = state.snapshots.iterator();
        while (snapshotIterator.hasNext()) {
            CpuSnapshotHistoryDto snapshot = snapshotIterator.next();
            if (snapshot == null || snapshot.timestamp <= 0L || snapshot.timestamp < cutoff) {
                snapshotIterator.remove();
                changed = true;
            }
        }

        while (state.jobs.size() > MAX_JOBS_PER_NETWORK) {
            sortJobsAscending(state.jobs);
            state.jobs.remove(0);
            changed = true;
        }
        while (state.snapshots.size() > MAX_SNAPSHOTS_PER_NETWORK) {
            sortSnapshotsAscending(state.snapshots);
            state.snapshots.remove(0);
            changed = true;
        }
        return changed;
    }

    private static void sortJobsAscending(List<CpuJobHistoryDto> jobs) {
        Collections.sort(jobs, new Comparator<CpuJobHistoryDto>() {

            @Override
            public int compare(CpuJobHistoryDto a, CpuJobHistoryDto b) {
                long at = a != null ? a.latestTimestamp() : Long.MIN_VALUE;
                long bt = b != null ? b.latestTimestamp() : Long.MIN_VALUE;
                return at < bt ? -1 : at == bt ? 0 : 1;
            }
        });
    }

    private static void sortSnapshotsAscending(List<CpuSnapshotHistoryDto> snapshots) {
        Collections.sort(snapshots, new Comparator<CpuSnapshotHistoryDto>() {

            @Override
            public int compare(CpuSnapshotHistoryDto a, CpuSnapshotHistoryDto b) {
                long at = a != null ? a.timestamp : Long.MIN_VALUE;
                long bt = b != null ? b.timestamp : Long.MIN_VALUE;
                return at < bt ? -1 : at == bt ? 0 : 1;
            }
        });
    }

    private void markDirtyAndMaybeFlush(CpuHistoryState state, long now) {
        state.dirty = true;
        if (trimState(state, now)) {
            state.dirty = true;
        }
        if (lastFlushAt <= 0L || now - lastFlushAt >= FLUSH_INTERVAL_MS) {
            flushDirty(now);
        }
    }

    private void flushDirty(long now) {
        for (CpuHistoryState state : states.values()) {
            if (state == null || !state.dirty) {
                continue;
            }
            if (store.save(state)) {
                state.dirty = false;
            }
        }
        lastFlushAt = now;
    }
}
