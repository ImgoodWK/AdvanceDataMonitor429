package com.imgood.advancedatamonitor.assistant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.handler.HandlerTick;

import appeng.api.networking.crafting.ICraftingJob;

public final class AssistantCraftJobManager {

    private static final AssistantCraftJobManager INSTANCE = new AssistantCraftJobManager();
    private final List<PendingJob> jobs = new ArrayList<PendingJob>();

    private AssistantCraftJobManager() {}

    public static AssistantCraftJobManager instance() {
        return INSTANCE;
    }

    public synchronized String checkCanStart(EntityPlayerMP player) {
        return checkCanStart(player, "zh_CN");
    }

    public synchronized String checkCanStart(EntityPlayerMP player, String locale) {
        pruneFinished();
        UUID owner = owner(player);
        int count = 0;
        for (PendingJob job : jobs) {
            if (job.owner.equals(owner)) {
                count++;
            }
        }
        if (count >= Math.max(1, Config.assistantMaxConcurrentCraftJobs)) {
            return zh(locale) ? "订单被拒绝：你已经有 " + count + " 个 AE2 合成计算正在运行。输入取消可停止待处理计算。"
                : "Order rejected: you already have " + count
                    + " AE2 crafting calculation(s) running. Say cancel to stop pending calculations.";
        }
        return null;
    }

    public synchronized int availableSlots(EntityPlayerMP player) {
        pruneFinished();
        UUID owner = owner(player);
        int count = 0;
        for (PendingJob job : jobs) {
            if (job.owner.equals(owner)) {
                count++;
            }
        }
        return Math.max(0, Math.max(1, Config.assistantMaxConcurrentCraftJobs) - count);
    }

    public synchronized void register(EntityPlayerMP player, Future<ICraftingJob> future, String displayName,
        long amount, Runnable onTimeout) {
        pruneFinished();
        if (future == null) {
            return;
        }
        PendingJob job = new PendingJob();
        job.owner = owner(player);
        job.playerName = player == null ? "" : player.getCommandSenderName();
        job.future = future;
        job.displayName = displayName == null ? "" : displayName;
        job.amount = amount;
        job.createdAt = System.currentTimeMillis();
        job.onTimeout = onTimeout;
        jobs.add(job);
    }

    /**
     * Removes a pending job if still tracked. Returns true when the job was present (not already timed out).
     */
    public synchronized boolean complete(EntityPlayerMP player, Future<ICraftingJob> future) {
        UUID owner = owner(player);
        Iterator<PendingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            PendingJob job = iterator.next();
            if (job.owner.equals(owner) && job.future == future) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized void tickPendingJobs() {
        pruneFinished();
        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(1, Config.assistantCraftJobTimeoutSeconds) * 1000L;
        Iterator<PendingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            PendingJob job = iterator.next();
            if (job.future != null && job.future.isDone()) {
                continue;
            }
            if (now - job.createdAt <= timeoutMs) {
                continue;
            }
            if (job.future != null) {
                try {
                    job.future.cancel(true);
                } catch (Throwable ignored) {}
            }
            iterator.remove();
            if (job.onTimeout != null) {
                HandlerTick.enqueueServerTask(job.onTimeout);
            }
        }
    }

    public synchronized String cancel(EntityPlayerMP player) {
        return cancel(player, "zh_CN");
    }

    public synchronized String summary(EntityPlayerMP player, String locale) {
        pruneFinished();
        UUID owner = owner(player);
        StringBuilder builder = new StringBuilder(zh(locale) ? "待处理 AE2 合成计算：" : "Pending AE2 crafting calculations:");
        int count = 0;
        long now = System.currentTimeMillis();
        for (PendingJob job : jobs) {
            if (!job.owner.equals(owner)) {
                continue;
            }
            count++;
            builder.append("\n")
                .append(count)
                .append(". ")
                .append(job.displayName)
                .append(" x")
                .append(job.amount)
                .append(zh(locale) ? "，已等待 " : ", waiting ")
                .append(Math.max(0L, (now - job.createdAt) / 1000L))
                .append("s");
        }
        if (count == 0) {
            return zh(locale) ? "没有待处理的服务器端 AE2 合成计算。" : "No server-side AE2 crafting calculation is pending.";
        }
        return builder.toString();
    }

    public synchronized String cancel(EntityPlayerMP player, String locale) {
        UUID owner = owner(player);
        int cancelled = 0;
        Iterator<PendingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            PendingJob job = iterator.next();
            if (!job.owner.equals(owner)) {
                continue;
            }
            try {
                job.future.cancel(true);
            } catch (Throwable ignored) {}
            iterator.remove();
            cancelled++;
        }
        return cancelled == 0
            ? (zh(locale) ? "没有待处理的服务器端 AE2 合成计算。" : "No server-side AE2 crafting calculation was pending.")
            : (zh(locale) ? "已取消 " + cancelled + " 个待处理的 AE2 合成计算。"
                : "Cancelled " + cancelled + " pending AE2 crafting calculation(s).");
    }

    private void pruneFinished() {
        long now = System.currentTimeMillis();
        long maxAge = Math.max(1, Config.assistantCraftJobTimeoutSeconds) * 1000L + 30000L;
        Iterator<PendingJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            PendingJob job = iterator.next();
            if (job.future == null || job.future.isDone() || job.future.isCancelled() || now - job.createdAt > maxAge) {
                iterator.remove();
            }
        }
    }

    private boolean zh(String locale) {
        return locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
    }

    private UUID owner(EntityPlayerMP player) {
        return player == null ? new UUID(0L, 0L) : player.getUniqueID();
    }

    private static final class PendingJob {

        private UUID owner;
        private String playerName;
        private Future<ICraftingJob> future;
        private String displayName;
        private long amount;
        private long createdAt;
        private Runnable onTimeout;
    }
}
