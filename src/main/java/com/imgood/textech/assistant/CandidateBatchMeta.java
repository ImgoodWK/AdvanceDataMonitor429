package com.imgood.textech.assistant;

import com.imgood.textech.Config;

public final class CandidateBatchMeta {

    public final int batchIndex;
    public final int batchCount;
    public final int totalCount;
    public final boolean append;

    public CandidateBatchMeta(int batchIndex, int batchCount, int totalCount, boolean append) {
        this.batchIndex = batchIndex;
        this.batchCount = batchCount;
        this.totalCount = totalCount;
        this.append = append;
    }

    public static CandidateBatchMeta single(int totalCount) {
        return new CandidateBatchMeta(0, 1, totalCount, false);
    }

    public int rangeStart() {
        if (totalCount <= 0 || batchIndex < 0) {
            return 1;
        }
        int batchSize = Math.max(1, Config.assistantQueryCandidateBatchSize);
        return batchIndex * batchSize + 1;
    }

    public int rangeEnd() {
        if (totalCount <= 0 || batchIndex < 0) {
            return totalCount;
        }
        int batchSize = Math.max(1, Config.assistantQueryCandidateBatchSize);
        return Math.min(totalCount, (batchIndex + 1) * batchSize);
    }
}
