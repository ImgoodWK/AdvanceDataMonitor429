package com.imgood.textech.webae.spark;

import java.util.ArrayList;
import java.util.List;

/** Persisted metadata and captured output for one WebAE-triggered Spark run. */
public final class SparkProfile {

    public String id;
    public String status;
    public String initiatedBy;
    public long startedAt;
    /** Timestamp when sampling stopped; viewer upload may finish afterwards. */
    public long samplingStoppedAt;
    public long completedAt;
    public int durationSeconds;
    /** server, lagSpikes, or allThreads. Null on records written by older builds. */
    public String mode;
    /** Effective Spark sampling interval selected by the server. */
    public int intervalMillis;
    /** Slow-tick threshold for lagSpikes mode; zero for other modes. */
    public int onlyTicksOverMillis;
    /** True when Spark samples all JVM threads instead of only the game thread. */
    public boolean includeAllThreads;
    public String resultUrl;
    public String error;
    public List<String> messages = new ArrayList<String>();
    /** Point-in-time `spark tps` output captured near profiler startup. */
    public List<String> baselineMessages = new ArrayList<String>();
    /** Point-in-time `spark tps` output captured when sampling stops. */
    public List<String> completionMessages = new ArrayList<String>();
    /** pending, ready, empty, unavailable, or legacy. */
    public String analysisStatus;
    /** Version of the bounded local analysis payload stored with this run. */
    public int analysisVersion;
    /** Sum of sampled thread time represented by the local Spark tree. */
    public double sampledTimeMillis;
    /** Approximate weighted sample count derived from the effective interval. */
    public int sampleCount;
    /** Number of stack nodes inspected while building the bounded analysis. */
    public int analyzedNodeCount;
    public List<Hotspot> hotspots = new ArrayList<Hotspot>();
    public List<CategoryImpact> categories = new ArrayList<CategoryImpact>();
    public List<ThreadImpact> threads = new ArrayList<ThreadImpact>();

    /** One method aggregated across every matching call path and sampled thread. */
    public static final class Hotspot {

        public String className;
        public String methodName;
        public int lineNumber;
        public String category;
        public String dominantThread;
        public double selfTimeMillis;
        public double totalTimeMillis;
        public double percent;
    }

    /** Exclusive sampled time grouped into a stable diagnostic category. */
    public static final class CategoryImpact {

        public String id;
        public double timeMillis;
        public double percent;
        public String topClassName;
        public String topMethodName;
    }

    /** Sampled time for one Spark thread group. */
    public static final class ThreadImpact {

        public String name;
        public double timeMillis;
        public double percent;
    }

    public boolean isActive() {
        return "running".equals(status) || "stopping".equals(status);
    }
}
