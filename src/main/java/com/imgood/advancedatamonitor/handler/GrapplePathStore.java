package com.imgood.advancedatamonitor.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.assistant.AssistantDataFiles;
import com.imgood.advancedatamonitor.items.GrappleRouteEntry;
import com.imgood.advancedatamonitor.utils.BlockPos;

public final class GrapplePathStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final GrapplePathStore INSTANCE = new GrapplePathStore();
    private final List<GrappleRouteEntry> routes = new ArrayList<GrappleRouteEntry>();
    private boolean loaded;

    private GrapplePathStore() {}

    public static GrapplePathStore instance() {
        return INSTANCE;
    }

    public synchronized List<GrappleRouteEntry> getRoutesForPlayer(EntityPlayerMP player) {
        load();
        String owner = ownerUuid(player);
        List<GrappleRouteEntry> result = new ArrayList<GrappleRouteEntry>();
        for (GrappleRouteEntry route : routes) {
            if (owner.equals(route.ownerUuid)) {
                result.add(route.copy());
            }
        }
        return result;
    }

    public synchronized List<GrappleRouteEntry> getRoutesForPlayerDimension(EntityPlayerMP player, int dimension) {
        List<GrappleRouteEntry> all = getRoutesForPlayer(player);
        List<GrappleRouteEntry> result = new ArrayList<GrappleRouteEntry>();
        for (GrappleRouteEntry route : all) {
            if (route.dimension == dimension) {
                result.add(route);
            }
        }
        return result;
    }

    public synchronized GrappleRouteEntry findRoute(EntityPlayerMP player, String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            return null;
        }
        load();
        String owner = ownerUuid(player);
        for (GrappleRouteEntry route : routes) {
            if (owner.equals(route.ownerUuid) && routeId.equals(route.routeId)) {
                return route.copy();
            }
        }
        return null;
    }

    public synchronized String saveRoute(EntityPlayerMP player, String name, int dimension, List<BlockPos> nodes) {
        load();
        if (player == null || nodes == null || nodes.isEmpty()) {
            return null;
        }
        String owner = ownerUuid(player);
        int count = 0;
        for (GrappleRouteEntry route : routes) {
            if (owner.equals(route.ownerUuid)) {
                count++;
            }
        }
        if (count >= Config.grappleMaxSavedRoutes) {
            return null;
        }
        if (nodes.size() > Config.grappleMaxNodesPerRoute) {
            return null;
        }
        GrappleRouteEntry entry = new GrappleRouteEntry();
        entry.routeId = nextRouteId(owner);
        entry.ownerUuid = owner;
        entry.name = sanitizeName(name);
        entry.dimension = dimension;
        entry.createdAt = System.currentTimeMillis();
        entry.nodes.addAll(nodes);
        routes.add(entry);
        save();
        return entry.routeId;
    }

    public synchronized boolean deleteRoute(EntityPlayerMP player, String routeId) {
        load();
        if (player == null || routeId == null) {
            return false;
        }
        String owner = ownerUuid(player);
        Iterator<GrappleRouteEntry> it = routes.iterator();
        while (it.hasNext()) {
            GrappleRouteEntry route = it.next();
            if (owner.equals(route.ownerUuid) && routeId.equals(route.routeId)) {
                it.remove();
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean renameRoute(EntityPlayerMP player, String routeId, String newName) {
        load();
        if (player == null || routeId == null) {
            return false;
        }
        String owner = ownerUuid(player);
        for (GrappleRouteEntry route : routes) {
            if (owner.equals(route.ownerUuid) && routeId.equals(route.routeId)) {
                route.name = sanitizeName(newName);
                save();
                return true;
            }
        }
        return false;
    }

    private String nextRouteId(String owner) {
        int max = 0;
        for (GrappleRouteEntry route : routes) {
            if (!owner.equals(route.ownerUuid) || route.routeId == null) {
                continue;
            }
            if (route.routeId.startsWith("r")) {
                try {
                    int id = Integer.parseInt(route.routeId.substring(1));
                    if (id > max) {
                        max = id;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return "r" + (max + 1);
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() > 32) {
            return trimmed.substring(0, 32);
        }
        return trimmed;
    }

    private static String ownerUuid(EntityPlayerMP player) {
        UUID uuid = player == null ? new UUID(0L, 0L) : player.getUniqueID();
        return uuid.toString();
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = AssistantDataFiles.dataFile("grapple-paths.json");
        if (!file.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
            List<GrappleRouteEntry> loadedRoutes = GSON
                .fromJson(reader, new TypeToken<List<GrappleRouteEntry>>() {}.getType());
            if (loadedRoutes != null) {
                routes.clear();
                routes.addAll(loadedRoutes);
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to load grapple paths", e);
        }
    }

    private void save() {
        File file = AssistantDataFiles.dataFile("grapple-paths.json");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            GSON.toJson(routes, writer);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to save grapple paths", e);
        }
    }
}
