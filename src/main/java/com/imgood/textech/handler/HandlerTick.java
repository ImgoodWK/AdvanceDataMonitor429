package com.imgood.textech.handler;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantCraftJobManager;
import com.imgood.textech.assistant.PlanStore;
import com.imgood.textech.assistant.PlanStore.PlanEntry;
import com.imgood.textech.entity.EntitySuperOrangeDrone;
import com.imgood.textech.items.ItemDataImprint;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.network.packet.PacketAssistantResponse;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.webae.perf.WebAePerfProfiler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 15:02
 **/
public class HandlerTick {

    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<Runnable>();

    /** Server tick thread; used by WebAE to avoid off-thread World access. */
    private static volatile Thread serverTickThread;

    private long lastOutput = 0;
    private long lastReminderScan = 0;
    /** Rotating tick counter for subsystem staggering. */
    private int tickCounter;

    /** True when the current thread is the Minecraft server tick thread. */
    public static boolean isServerThread() {
        Thread known = serverTickThread;
        return known != null && Thread.currentThread() == known;
    }

    public static void enqueueServerTask(Runnable task) {
        if (task != null) {
            SERVER_TASKS.offer(task);
            WebAePerfProfiler.instance()
                .onTaskEnqueued();
        }
    }

    public static int getServerTaskQueueDepth() {
        return WebAePerfProfiler.instance()
            .getQueueDepth();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTickThread = Thread.currentThread();
        tickCounter++;
        WebAePerfProfiler perf = WebAePerfProfiler.instance();
        long now = System.currentTimeMillis();

        // --- Server task queue (process every tick) ---
        long t0 = perf.begin();
        Runnable task;
        int processed = 0;
        while (processed < 64 && (task = SERVER_TASKS.poll()) != null) {
            perf.onTaskDequeued();
            task.run();
            processed++;
        }
        perf.setLastTasksProcessed(processed);
        perf.endPhase(WebAePerfProfiler.PHASE_SERVER_TASKS, t0);

        // Resolve terminal crafting links independently of HTTP polling so
        // CPU history receives completed/cancelled jobs even when no browser
        // is connected. The observer is internally rate-limited and only
        // reads ICraftingLink terminal flags.
        com.imgood.textech.webae.api.handler.OrderHandler.onServerTick(now);

        // --- Misc subsystems: staggered across ticks ---
        long tMisc = perf.begin();
        scanPlanReminders();
        AssistantCraftJobManager.instance()
            .tickPendingJobs();
        com.imgood.textech.items.cell.DataLoomWeaveScheduler.onServerTick();
        com.imgood.textech.webae.quest.QuestInventoryEscrow.tickCleanup();
        // Player tracker: every 2 ticks; health MSPT tracking every tick, sample collection every 2
        if ((tickCounter & 1) == 0) {
            com.imgood.textech.handler.HandlerWebPlayerTracker.onServerTick(now);
            com.imgood.textech.webae.health.ServerHealthSampler.instance()
                .collectSample();
        }
        com.imgood.textech.webae.health.ServerHealthSampler.instance()
            .onServerTick(); // lightweight MSPT tracking every tick
        com.imgood.textech.webae.events.EventStreamHub.instance()
            .tickHeartbeats();
        com.imgood.textech.webae.chat.ChatMessageStore.instance()
            .tickSave(now);
        perf.endPhase(WebAePerfProfiler.PHASE_MISC, tMisc);

        long tQqBot = perf.begin();
        com.imgood.textech.webae.qqbot.QqBotService.instance()
            .onServerTick(now);
        perf.endPhase(WebAePerfProfiler.PHASE_QQ_BOT, tQqBot);

        // --- Snapshot scheduler: every tick (internal spread logic) ---
        long t1 = perf.begin();
        com.imgood.textech.webae.cache.SnapshotScheduler.onServerTick();
        perf.endPhase(WebAePerfProfiler.PHASE_SNAPSHOT_SCHEDULER, t1);

        // --- Power / metrics / alerts / icons: every 4 ticks (~250ms spacing) ---
        int phase = tickCounter & 3;
        if (phase == 0) {
            long tGroup = perf.begin();
            com.imgood.textech.webae.power.PowerSampler.getInstance()
                .onServerTick();
            com.imgood.textech.webae.metric.NetworkMetricSampler.getInstance()
                .onServerTick();
            perf.endPhase(WebAePerfProfiler.PHASE_METRIC_SAMPLER, tGroup);
        }
        if (phase == 2) {
            long tAlerts = perf.begin();
            com.imgood.textech.webae.alerts.WebAlertEngine.onServerTick(now);
            perf.endPhase(WebAePerfProfiler.PHASE_ALERT_ENGINE, tAlerts);
            long tIcons = perf.begin();
            com.imgood.textech.webae.icon.IconMissingQueue.instance()
                .onServerTick();
            perf.endPhase(WebAePerfProfiler.PHASE_ICON_QUEUE, tIcons);
        }

        // --- World map: every 2 ticks ---
        if ((tickCounter & 1) == 0) {
            long t6 = perf.begin();
            com.imgood.textech.webae.worldmap.WorldMapTileQueue.instance()
                .onServerTick();
            perf.endPhase(WebAePerfProfiler.PHASE_WORLD_MAP_TILE, t6);

            long t7 = perf.begin();
            com.imgood.textech.webae.worldmap.WorldMapCaptureCoordinator.instance()
                .onServerTick();
            perf.endPhase(WebAePerfProfiler.PHASE_WORLD_MAP_CAPTURE, t7);
        }

        perf.onTickEnd();
    }

    private void scanPlanReminders() {
        long now = System.currentTimeMillis();
        if (now - lastReminderScan < 1000L) {
            return;
        }
        lastReminderScan = now;
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        List players = server.getConfigurationManager().playerEntityList;
        for (Object value : players) {
            if (!(value instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP) value;
            for (PlanEntry plan : PlanStore.instance()
                .dueReminders(player)) {
                AdvanceDataMonitor.ADMCHANEL
                    .sendTo(PacketAssistantResponse.message("Reminder #" + plan.id + ": " + plan.title), player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            EntityPlayer player = event.player;

            // --- Super Orange drone: respawn original body every second to keep client position in sync ---
            if (ItemSuperOrange.isDroneActiveForPlayer(player)) {
                if (player.ticksExisted % EntitySuperOrangeDrone.RESPAWN_INTERVAL_TICKS == 0) {
                    EntitySuperOrangeDrone.refreshOriginalDrone(player);
                } else {
                    EntitySuperOrangeDrone.spawnForPlayer(player);
                }
            } else {
                despawnPlayerDrones(player);
            }

            ItemStack stack = player.getHeldItem();

            if (stack != null && stack.getItem() instanceof ItemDataImprint) {
                long now = System.currentTimeMillis();
                if (now - lastOutput > 1000) {
                    lastOutput = now;
                    NBTTagCompound nbt = stack.getTagCompound();
                    if (nbt != null && nbt.hasKey("Position")) {
                        BlockPos pos = new BlockPos(
                            nbt.getCompoundTag("Position")
                                .getInteger("x"),
                            nbt.getCompoundTag("Position")
                                .getInteger("y"),
                            nbt.getCompoundTag("Position")
                                .getInteger("z"));
                    }
                    /*
                     * if (nbt != null && nbt.hasKey("boundPos") && nbt.hasKey("enabledTags")) {
                     * // 获取绑定坐标
                     * NBTTagCompound posTag = nbt.getCompoundTag("boundPos");
                     * BlockPos pos = new BlockPos(
                     * posTag.getInteger("x"),
                     * posTag.getInteger("y"),
                     * posTag.getInteger("z")
                     * );
                     * // 获取TileEntity
                     * TileEntity te = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
                     * if (te != null) {
                     * NBTTagCompound teNbt = new NBTTagCompound();
                     * te.writeToNBT(teNbt);
                     * // 获取启用的标签
                     * NBTTagCompound enabledTags = nbt.getCompoundTag("enabledTags");
                     * for (Object key : enabledTags.func_150296_c()) {
                     * String tagPath = (String) key;
                     * if (enabledTags.getBoolean(tagPath)) {
                     * // 获取具体值
                     * String value = getNbtValue(teNbt, tagPath);
                     * System.out.println("[ADM] " + tagPath + ": " + value);
                     * }
                     * }
                     * }
                     * }
                     */
                }
            }
        }
    }

    private String getNbtValue(NBTTagCompound nbt, String path) {
        String[] parts = path.split("\\.");
        NBTTagCompound current = nbt;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.getCompoundTag(parts[i]);
        }
        return current.getTag(parts[parts.length - 1])
            .toString();
    }

    private void despawnPlayerDrones(EntityPlayer player) {
        if (player == null || player.worldObj == null) return;
        String uuid = player.getUniqueID()
            .toString();
        for (Object obj : player.worldObj.loadedEntityList) {
            if (obj instanceof EntitySuperOrangeDrone) {
                EntitySuperOrangeDrone drone = (EntitySuperOrangeDrone) obj;
                if (!drone.isDead && uuid.equals(drone.getOwnerUUID())) {
                    drone.setDead();
                }
            }
        }
    }
}
