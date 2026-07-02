package com.imgood.textech.handler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.textech.tileentity.TileEntityAdvanceStorageLink;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Debounced refresh scheduler for AE connector tile entities (network / crafting / storage links).
 */
public final class ConnectorTickService {

    public static final int NETWORK_DEBOUNCE_TICKS = 5;
    public static final int CRAFTING_DEBOUNCE_TICKS = 2;
    public static final int STORAGE_AUTO_REFRESH_TICKS = 20;

    private static int networkDebounceTicks = 0;
    private static int craftingDebounceTicks = 0;
    private static int storageRefreshCounter = 0;

    private static final List<WeakReference<TileEntityAdvanceCraftingLink>> PENDING_CRAFTING = new ArrayList<>();

    public static long networkRefreshCount = 0L;
    public static long craftingRefreshCount = 0L;
    public static long storageRefreshCount = 0L;
    public static long networkRefreshNanos = 0L;
    public static long craftingRefreshNanos = 0L;
    public static long storageRefreshNanos = 0L;

    public ConnectorTickService() {}

    public static void requestNetworkLinkFlush() {
        networkDebounceTicks = NETWORK_DEBOUNCE_TICKS;
    }

    public static void scheduleCraftingRefresh(TileEntityAdvanceCraftingLink link) {
        if (link == null || link.getWorldObj() == null || link.getWorldObj().isRemote) {
            return;
        }
        synchronized (PENDING_CRAFTING) {
            for (WeakReference<TileEntityAdvanceCraftingLink> ref : PENDING_CRAFTING) {
                TileEntityAdvanceCraftingLink existing = ref.get();
                if (existing == link) {
                    return;
                }
            }
            PENDING_CRAFTING.add(new WeakReference<TileEntityAdvanceCraftingLink>(link));
        }
        craftingDebounceTicks = CRAFTING_DEBOUNCE_TICKS;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
            return;
        }
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        long worldTick = server.worldServers[0] != null ? server.worldServers[0].getTotalWorldTime() : 0L;

        if (networkDebounceTicks > 0) {
            networkDebounceTicks--;
            if (networkDebounceTicks == 0) {
                long start = System.nanoTime();
                NetworkLinkGridStatsCache.flushPendingLinks(worldTick);
                networkRefreshNanos += System.nanoTime() - start;
                networkRefreshCount++;
            }
        }

        if (craftingDebounceTicks > 0) {
            craftingDebounceTicks--;
            if (craftingDebounceTicks == 0) {
                long start = System.nanoTime();
                flushPendingCraftingLinks();
                craftingRefreshNanos += System.nanoTime() - start;
                craftingRefreshCount++;
            }
        }

        storageRefreshCounter++;
        if (storageRefreshCounter >= STORAGE_AUTO_REFRESH_TICKS) {
            storageRefreshCounter = 0;
            long start = System.nanoTime();
            refreshActiveStorageLinks(worldTick);
            storageRefreshNanos += System.nanoTime() - start;
            storageRefreshCount++;
        }

        if (Config.debugConnectorProfile && worldTick % 200 == 0) {
            AdvanceDataMonitor.LOG.info(
                String.format(
                    "[connector-profile] network refreshes=%d avg=%.2fms crafting refreshes=%d avg=%.2fms storage refreshes=%d avg=%.2fms",
                    networkRefreshCount,
                    networkRefreshCount > 0 ? networkRefreshNanos / 1_000_000.0 / networkRefreshCount : 0.0,
                    craftingRefreshCount,
                    craftingRefreshCount > 0 ? craftingRefreshNanos / 1_000_000.0 / craftingRefreshCount : 0.0,
                    storageRefreshCount,
                    storageRefreshCount > 0 ? storageRefreshNanos / 1_000_000.0 / storageRefreshCount : 0.0));
        }
    }

    private static void flushPendingCraftingLinks() {
        List<TileEntityAdvanceCraftingLink> links = new ArrayList<>();
        synchronized (PENDING_CRAFTING) {
            Iterator<WeakReference<TileEntityAdvanceCraftingLink>> it = PENDING_CRAFTING.iterator();
            while (it.hasNext()) {
                TileEntityAdvanceCraftingLink link = it.next()
                    .get();
                it.remove();
                if (link != null && link.getWorldObj() != null && !link.getWorldObj().isRemote) {
                    links.add(link);
                }
            }
        }
        for (TileEntityAdvanceCraftingLink link : links) {
            link.updateCraftingStats();
        }
    }

    private static void refreshActiveStorageLinks(long worldTick) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (net.minecraft.world.WorldServer world : server.worldServers) {
            if (world == null) {
                continue;
            }
            for (Object obj : world.loadedTileEntityList) {
                if (!(obj instanceof TileEntityAdvanceStorageLink)) {
                    continue;
                }
                TileEntityAdvanceStorageLink link = (TileEntityAdvanceStorageLink) obj;
                if (link.shouldAutoRefreshStorage()) {
                    link.refreshStorageCache(worldTick);
                }
            }
        }
    }
}
