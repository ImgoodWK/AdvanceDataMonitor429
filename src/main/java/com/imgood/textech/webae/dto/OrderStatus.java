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
    /** 完成订单的最终进度（历史订单恒为 100）。 */
    public int finalProgress;

    public static class CpuInfo {

        public int coProcessors;
        public long storage;
        public int parallelism;
    }
}
