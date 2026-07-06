package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.dto.PlayerDto;
import com.imgood.textech.webae.dto.PlayerLocationDto;
import com.imgood.textech.webae.player.PlayerInfo;
import com.imgood.textech.webae.player.PlayerInfoStore;
import com.imgood.textech.webae.player.PlayerOnlineSampler;
import com.imgood.textech.webae.player.SkinUrlResolver;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the WebAE players endpoints.
 *
 * <p>
 * Endpoints (all require auth):
 * </p>
 * <ul>
 * <li>{@code GET /api/players} → {@code {online:[...], offline:[...]}} where each
 * item contains {@code uuid / name / online / onlineMs / lastLogin / lastLogout / skinUrl}</li>
 * <li>{@code GET /api/players/since=<ts>} — incremental pull (returns all known
 * players whose online status / onlineMs changed since {@code ts})</li>
 * <li>{@code GET /api/players/online/history} — 在线人数趋势历史（p2-dashboard），
 * 返回 {@code {success, history:[{ts, count}, ...]}}</li>
 * <li>{@code GET /api/players/locations} — 在线玩家世界坐标（Phase 6.1）</li>
 * </ul>
 */
public class PlayerHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params,
        String playerUuid) {
        if (method != NanoHTTPD.Method.GET) {
            return json(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "{\"success\":false,\"message\":\"Use GET on " + uri + "\"}");
        }
        if ("/api/players".equals(uri)) {
            return handleList(params);
        }
        if ("/api/players/since".equals(uri)) {
            return handleSince(params);
        }
        if ("/api/players/online/history".equals(uri)) {
            return handleOnlineHistory();
        }
        if ("/api/players/locations".equals(uri)) {
            return handleLocations();
        }
        return json(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown players endpoint: " + uri + "\"}");
    }

    // ---- GET /api/players ----
    private static NanoHTTPD.Response handleList(Map<String, String> params) {
        long now = System.currentTimeMillis();
        List<PlayerDto> online = new ArrayList<PlayerDto>();
        List<PlayerDto> offline = new ArrayList<PlayerDto>();
        for (PlayerInfo info : PlayerInfoStore.instance()
            .getAllPlayers()) {
            PlayerDto dto = toDto(info, now);
            if (dto.online) {
                online.add(dto);
            } else {
                offline.add(dto);
            }
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"online\":" + GSON.toJson(online) + ",\"offline\":" + GSON.toJson(offline) + "}");
    }

    // ---- GET /api/players/since=<ts> ----
    private static NanoHTTPD.Response handleSince(Map<String, String> params) {
        long since = parseLongOrDefault(params.get("since"), 0L);
        long now = System.currentTimeMillis();
        // Incremental: return any player whose lastLogin or lastLogout >= since,
        // plus all currently-online players (their onlineMs keeps growing).
        List<PlayerDto> changed = new ArrayList<PlayerDto>();
        for (PlayerInfo info : PlayerInfoStore.instance()
            .getAllPlayers()) {
            boolean changedSince = (info.lastLogin >= since) || (info.lastLogout >= since) || info.online;
            if (changedSince) {
                changed.add(toDto(info, now));
            }
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"players\":" + GSON.toJson(changed) + "}");
    }

    // ---- GET /api/players/online/history (p2-dashboard) ----
    private static NanoHTTPD.Response handleOnlineHistory() {
        List<PlayerOnlineSampler.HistoryPoint> history = PlayerOnlineSampler.instance()
            .history();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"history\":[");
        for (int i = 0; i < history.size(); i++) {
            PlayerOnlineSampler.HistoryPoint p = history.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"ts\":")
                .append(p.ts)
                .append(",\"count\":")
                .append(p.count)
                .append('}');
        }
        sb.append("],\"currentCount\":")
            .append(
                PlayerOnlineSampler.instance()
                    .currentOnlineCount())
            .append('}');
        return json(NanoHTTPD.Response.Status.OK, sb.toString());
    }

    // ---- GET /api/players/locations (Phase 6.1) ----
    private static NanoHTTPD.Response handleLocations() {
        List<PlayerLocationDto> locations = new ArrayList<PlayerLocationDto>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.getConfigurationManager() != null) {
            for (Object obj : server.getConfigurationManager().playerEntityList) {
                if (!(obj instanceof EntityPlayerMP)) {
                    continue;
                }
                EntityPlayerMP player = (EntityPlayerMP) obj;
                String name = player.getDisplayName();
                if (name == null || name.isEmpty()) {
                    name = player.getCommandSenderName();
                }
                locations.add(
                    new PlayerLocationDto(
                        player.getUniqueID()
                            .toString(),
                        name,
                        (int) Math.floor(player.posX),
                        (int) Math.floor(player.posY),
                        (int) Math.floor(player.posZ),
                        player.dimension,
                        true));
            }
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"locations\":" + GSON.toJson(locations) + "}");
    }

    private static PlayerDto toDto(PlayerInfo info, long now) {
        long onlineMs;
        try {
            UUID uuid = UUID.fromString(info.uuid);
            onlineMs = PlayerInfoStore.instance()
                .effectiveOnlineMs(uuid, now);
        } catch (IllegalArgumentException e) {
            onlineMs = info.totalOnlineMs;
        }
        String skinUrl = SkinUrlResolver.resolveByUuid(info.uuid);
        return new PlayerDto(info.uuid, info.name, info.online, onlineMs, info.lastLogin, info.lastLogout, skinUrl);
    }

    private static long parseLongOrDefault(String s, long def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
