package com.imgood.textech.webae.dto;

public class OrderStatus {

    public String craftJobId;
    public String status; // "pending" / "crafting" / "completed" / "cancelled" / "failed"
    public int progressPercent; // 0-100
    public String message;
    public long submittedAt;
    public long completedAt; // -1 表示未完成
    /** 下单时指定的 CPU 名称（可选）。 */
    public String cpuName;
    /** 下单时快照的 CPU 详情（协同/存储/并行数）。 */
    public CpuInfo cpuInfo;
    /** 完成订单的最终进度（历史订单恒为 100；取消时为取消前进度）。 */
    public int finalProgress;
    /** 下单物品显示名（用于再次下单）。 */
    public String itemName;
    /** 下单数量。 */
    public long amount;
    /** 按样板下单时的 patternId（可选）。 */
    public String patternId;
    /** Owner AE network id used for progress resolution. */
    public int networkId;
    /** AE2 crafting link id when bound. */
    public String craftingId;
    /** AE2 craft-tree step counters (not final-output count). */
    public long startItems;
    public long remainingItems;
    public long elapsedMs;
    public String failReason;
    public String cancelReason;
    /** Always "steps" — AE2 startItemCount/remainingItemCount progress. */
    public String progressKind = "steps";

    public static class CpuInfo {

        public int coProcessors;
        public long storage;
        public int parallelism;
    }
}
