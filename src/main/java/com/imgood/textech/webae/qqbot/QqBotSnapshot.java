package com.imgood.textech.webae.qqbot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.imgood.textech.webae.health.ServerHealthSampler;

/** Immutable-enough server status snapshot captured on the server tick thread. */
public final class QqBotSnapshot {

    public long capturedAtMs;
    public double tps = 20.0D;
    public double mspt = 50.0D;
    public long uptimeSeconds;
    public int onlinePlayers;
    public int maxPlayers;
    public String motd = "";
    public long usedMemoryMb;
    public long maxMemoryMb;
    public List<String> playerNames = new ArrayList<String>();

    public static QqBotSnapshot capture() {
        QqBotSnapshot snapshot = new QqBotSnapshot();
        snapshot.capturedAtMs = System.currentTimeMillis();
        ServerHealthSampler.HealthSnapshot health = ServerHealthSampler.instance()
            .snapshot();
        snapshot.tps = health.tps;
        snapshot.mspt = health.mspt;
        snapshot.uptimeSeconds = health.uptimeSeconds;

        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            snapshot.motd = safe(server.getMOTD());
            if (server.getConfigurationManager() != null) {
                snapshot.maxPlayers = server.getConfigurationManager()
                    .getMaxPlayers();
                for (Object value : server.getConfigurationManager().playerEntityList) {
                    if (value instanceof EntityPlayerMP) {
                        EntityPlayerMP player = (EntityPlayerMP) value;
                        snapshot.playerNames.add(player.getCommandSenderName());
                    }
                }
            }
        }
        snapshot.onlinePlayers = snapshot.playerNames.size();
        Runtime runtime = Runtime.getRuntime();
        snapshot.usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        snapshot.maxMemoryMb = runtime.maxMemory() / (1024L * 1024L);
        return snapshot;
    }

    public QqBotSnapshot copy() {
        QqBotSnapshot copy = new QqBotSnapshot();
        copy.capturedAtMs = capturedAtMs;
        copy.tps = tps;
        copy.mspt = mspt;
        copy.uptimeSeconds = uptimeSeconds;
        copy.onlinePlayers = onlinePlayers;
        copy.maxPlayers = maxPlayers;
        copy.motd = motd;
        copy.usedMemoryMb = usedMemoryMb;
        copy.maxMemoryMb = maxMemoryMb;
        copy.playerNames = new ArrayList<String>(playerNames);
        return copy;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
