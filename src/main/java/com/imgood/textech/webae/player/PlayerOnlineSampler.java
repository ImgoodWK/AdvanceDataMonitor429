package com.imgood.textech.webae.player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * 在线玩家数采样器 —— 复用 {@link com.imgood.textech.webae.power.PowerSampler} 的滑动窗口思路，
 * 在服务端 tick 定期采样当前在线玩家数，保留滚动窗口供前端趋势图展示。
 *
 * <p>
 * 线程安全：所有公开方法 synchronized。采样由
 * {@link com.imgood.textech.handler.HandlerWebPlayerTracker#onServerTick(long)} 在主线程调用，
 * HTTP 读取由 {@link com.imgood.textech.webae.api.handler.PlayerHandler} 在工作线程调用。
 * </p>
 *
 * <p>
 * 采样间隔 {@value #SAMPLE_INTERVAL_MS} ms，窗口 {@value #WINDOW_MS} ms（约 6 小时），
 * 上限 {@value #MAX_POINTS} 个点，避免内存膨胀。
 * </p>
 */
public class PlayerOnlineSampler {

    private static final PlayerOnlineSampler INSTANCE = new PlayerOnlineSampler();

    /** 采样间隔：每 30 秒记录一次在线人数。 */
    private static final long SAMPLE_INTERVAL_MS = 30_000L;
    /** 滚动窗口：保留最近 6 小时的数据。 */
    private static final long WINDOW_MS = 6 * 60 * 60 * 1000L;
    /** 最大点数上限（防御性，6h/30s = 720 点，留余量到 1000）。 */
    private static final int MAX_POINTS = 1000;

    private static final class SamplePoint {

        final long ts;
        final int count;

        SamplePoint(long ts, int count) {
            this.ts = ts;
            this.count = count;
        }
    }

    private final Deque<SamplePoint> samples = new ArrayDeque<SamplePoint>();
    private long lastSampleTime;

    private PlayerOnlineSampler() {}

    public static PlayerOnlineSampler instance() {
        return INSTANCE;
    }

    /**
     * 由服务端 tick 调用。每 {@link #SAMPLE_INTERVAL_MS} 采样一次当前在线玩家数。
     * 在服务器未启动 / 无玩家时也采样（count=0），保证趋势线连续。
     */
    public synchronized void onServerTick(long now) {
        if (now - lastSampleTime < SAMPLE_INTERVAL_MS) {
            return;
        }
        lastSampleTime = now;
        int count = countOnlinePlayers();
        samples.addLast(new SamplePoint(now, count));
        // 滚动窗口裁剪
        long cutoff = now - WINDOW_MS;
        while (!samples.isEmpty() && samples.peekFirst().ts < cutoff) {
            samples.pollFirst();
        }
        // 点数上限裁剪
        while (samples.size() > MAX_POINTS) {
            samples.pollFirst();
        }
    }

    /** @return 当前在线玩家数（主线程安全读取 playerEntityList）。 */
    private static int countOnlinePlayers() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return 0;
        int count = 0;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) count++;
        }
        return count;
    }

    /** @return 当前在线玩家数（轻量读取，HTTP 端点直接调用）。 */
    public synchronized int currentOnlineCount() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return 0;
        int count = 0;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) count++;
        }
        return count;
    }

    /**
     * @return 趋势历史点列表（ oldest-first ），每项 {@code {ts, count}}。
     *         返回快照副本，调用方可安全遍历。
     */
    public synchronized List<HistoryPoint> history() {
        List<HistoryPoint> out = new ArrayList<HistoryPoint>(samples.size());
        for (SamplePoint sp : samples) {
            out.add(new HistoryPoint(sp.ts, sp.count));
        }
        return out;
    }

    /** 趋势历史点 DTO（与前端 PlayerOnlineHistoryPoint 对应）。 */
    public static final class HistoryPoint {

        public final long ts;
        public final int count;

        public HistoryPoint(long ts, int count) {
            this.ts = ts;
            this.count = count;
        }
    }
}
