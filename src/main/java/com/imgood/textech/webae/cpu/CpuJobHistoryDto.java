package com.imgood.textech.webae.cpu;

/**
 * Persisted, privacy-bounded lifecycle record for one WebAE crafting job.
 *
 * <p>This deliberately contains identifiers and numeric observations only.
 * It never stores a request's free-form input, item display text, or input
 * item list.</p>
 */
public class CpuJobHistoryDto {

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_STUCK = "stuck";
    public static final String STATUS_UNKNOWN = "unknown";

    public String jobId;
    public String ownerUuid;
    public int networkId;
    public String networkKey;
    public String cpuName;
    public String status = STATUS_UNKNOWN;
    public long queuedAt;
    public long startedAt;
    public long finishedAt;
    public Long durationMs;
    public Long queueMs;
    public Integer coProcessors;
    public Long storageUsed;
    public Long storageMax;
    /** 0..100 when observed; null when there is no reliable progress value. */
    public Integer progress;
    /** Safe, normalized recipe/pattern identifier; never arbitrary request text. */
    public String recipeKey;

    public CpuJobHistoryDto copy() {
        CpuJobHistoryDto copy = new CpuJobHistoryDto();
        copy.jobId = jobId;
        copy.ownerUuid = ownerUuid;
        copy.networkId = networkId;
        copy.networkKey = networkKey;
        copy.cpuName = cpuName;
        copy.status = status;
        copy.queuedAt = queuedAt;
        copy.startedAt = startedAt;
        copy.finishedAt = finishedAt;
        copy.durationMs = durationMs;
        copy.queueMs = queueMs;
        copy.coProcessors = coProcessors;
        copy.storageUsed = storageUsed;
        copy.storageMax = storageMax;
        copy.progress = progress;
        copy.recipeKey = recipeKey;
        return copy;
    }

    public long latestTimestamp() {
        long latest = queuedAt;
        if (startedAt > latest) {
            latest = startedAt;
        }
        if (finishedAt > latest) {
            latest = finishedAt;
        }
        return latest;
    }

    public boolean isTerminal() {
        return STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status);
    }

    public static boolean isKnownStatus(String value) {
        return STATUS_QUEUED.equals(value) || STATUS_RUNNING.equals(value) || STATUS_COMPLETED.equals(value)
            || STATUS_FAILED.equals(value) || STATUS_CANCELLED.equals(value) || STATUS_STUCK.equals(value)
            || STATUS_UNKNOWN.equals(value);
    }
}
