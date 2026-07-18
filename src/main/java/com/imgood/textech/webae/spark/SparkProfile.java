package com.imgood.textech.webae.spark;

import java.util.ArrayList;
import java.util.List;

/** Persisted metadata and captured output for one WebAE-triggered Spark run. */
public final class SparkProfile {

    public String id;
    public String status;
    public String initiatedBy;
    public long startedAt;
    public long completedAt;
    public int durationSeconds;
    public String resultUrl;
    public String error;
    public List<String> messages = new ArrayList<String>();

    public boolean isActive() {
        return "running".equals(status) || "stopping".equals(status);
    }
}
