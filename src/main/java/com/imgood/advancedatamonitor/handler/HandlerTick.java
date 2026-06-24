package com.imgood.advancedatamonitor.handler;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.assistant.AssistantCraftJobManager;
import com.imgood.advancedatamonitor.assistant.PlanStore;
import com.imgood.advancedatamonitor.assistant.PlanStore.PlanEntry;
import com.imgood.advancedatamonitor.entity.EntitySuperOrangeDrone;
import com.imgood.advancedatamonitor.items.ItemDataImprint;
import com.imgood.advancedatamonitor.items.ItemSuperOrange;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantResponse;
import com.imgood.advancedatamonitor.utils.BlockPos;

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

    private long lastOutput = 0;
    private long lastReminderScan = 0;

    public static void enqueueServerTask(Runnable task) {
        if (task != null) {
            SERVER_TASKS.offer(task);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Runnable task;
        int processed = 0;
        while ((task = SERVER_TASKS.poll()) != null && processed++ < 64) {
            task.run();
        }
        scanPlanReminders();
        AssistantCraftJobManager.instance()
            .tickPendingJobs();
        com.imgood.advancedatamonitor.items.cell.DataLoomWeaveScheduler.onServerTick();
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
