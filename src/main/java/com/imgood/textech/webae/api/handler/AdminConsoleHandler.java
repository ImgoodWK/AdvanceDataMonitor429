package com.imgood.textech.webae.api.handler;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.console.AdminCommandService;
import com.imgood.textech.webae.console.AdminCommandService.Submission;
import com.imgood.textech.webae.console.AdminConsoleStore;
import com.imgood.textech.webae.console.AdminConsoleStore.CommandAuditEntry;
import com.imgood.textech.webae.console.AdminConsoleStore.CommandPreset;
import com.imgood.textech.webae.player.PlayerInfo;
import com.imgood.textech.webae.player.PlayerInfoStore;

import fi.iki.elonen.NanoHTTPD;

/** Admin-only command execution, preset, audit, and lightweight player APIs. */
public final class AdminConsoleHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long PLAYER_CACHE_MS = 3000L;
    private static volatile long playerCacheAt;
    private static volatile List<ConsolePlayer> playerCache = Collections.emptyList();

    private AdminConsoleHandler() {}

    public static NanoHTTPD.Response handle(
        String uri,
        NanoHTTPD.Method method,
        String body,
        WebAuthSession auth,
        String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return error(NanoHTTPD.Response.Status.FORBIDDEN, "admin_required",
                "Admin permission is required to use the server console.");
        }
        try {
            if ("/api/admin/server-console".equals(uri) && method == NanoHTTPD.Method.GET) {
                JsonObject response = success();
                response.add("presets", GSON.toJsonTree(AdminConsoleStore.instance().presets()));
                response.add("history", GSON.toJsonTree(AdminConsoleStore.instance().historySummaries()));
                return json(NanoHTTPD.Response.Status.OK, response);
            }
            if ("/api/admin/server-console/players".equals(uri) && method == NanoHTTPD.Method.GET) {
                JsonObject response = success();
                response.add("players", GSON.toJsonTree(players()));
                response.addProperty("cachedAt", playerCacheAt);
                return json(NanoHTTPD.Response.Status.OK, response);
            }
            if ("/api/admin/server-console/execute".equals(uri) && method == NanoHTTPD.Method.POST) {
                ExecuteRequest request = GSON.fromJson(body == null ? "{}" : body, ExecuteRequest.class);
                if (request == null) request = new ExecuteRequest();
                Submission submission = AdminCommandService.submit(
                    request.command, request.confirmed, auth.actorUuid, auth.actorName);
                if (!submission.accepted) {
                    NanoHTTPD.Response.Status status = "confirmation_required".equals(submission.code)
                        ? NanoHTTPD.Response.Status.CONFLICT : NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE;
                    return error(status, submission.code, submission.message);
                }
                JsonObject response = success();
                response.addProperty("pending", submission.pending);
                response.add("entry", GSON.toJsonTree(submission.entry));
                return json(NanoHTTPD.Response.Status.OK, response);
            }
            if ("/api/admin/server-console/presets".equals(uri) && method == NanoHTTPD.Method.PUT) {
                PresetRequest request = GSON.fromJson(body == null ? "{}" : body, PresetRequest.class);
                if (request == null) request = new PresetRequest();
                CommandPreset preset = AdminConsoleStore.instance().savePreset(
                    request.id, request.label, request.command, request.description, auth.actorName);
                JsonObject response = success();
                response.add("preset", GSON.toJsonTree(preset));
                return json(NanoHTTPD.Response.Status.OK, response);
            }
            if (uri.startsWith("/api/admin/server-console/presets/") && method == NanoHTTPD.Method.DELETE) {
                String id = decode(uri.substring("/api/admin/server-console/presets/".length()));
                if (!AdminConsoleStore.instance().deletePreset(id)) {
                    return error(NanoHTTPD.Response.Status.NOT_FOUND, "preset_not_found", "Preset not found.");
                }
                return json(NanoHTTPD.Response.Status.OK, success());
            }
            if ("/api/admin/server-console/history/clear".equals(uri) && method == NanoHTTPD.Method.POST) {
                AdminConsoleStore.instance().clearHistory();
                return json(NanoHTTPD.Response.Status.OK, success());
            }
            if (uri.startsWith("/api/admin/server-console/history/") && method == NanoHTTPD.Method.GET) {
                String id = decode(uri.substring("/api/admin/server-console/history/".length()));
                CommandAuditEntry entry = AdminConsoleStore.instance().historyEntry(id);
                if (entry == null) {
                    return error(NanoHTTPD.Response.Status.NOT_FOUND, "history_not_found", "History entry not found.");
                }
                JsonObject response = success();
                response.add("entry", GSON.toJsonTree(entry));
                return json(NanoHTTPD.Response.Status.OK, response);
            }
            return error(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed",
                "Unsupported server console endpoint or method.");
        } catch (IllegalArgumentException e) {
            return error(NanoHTTPD.Response.Status.BAD_REQUEST, "invalid_request", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "request_interrupted",
                "Command wait was interrupted; check audit history before retrying.");
        } catch (Exception e) {
            return error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "server_console_failed", safe(e.getMessage()));
        }
    }

    private static List<ConsolePlayer> players() {
        long now = System.currentTimeMillis();
        List<ConsolePlayer> cached = playerCache;
        if (now - playerCacheAt < PLAYER_CACHE_MS && cached != null) return cached;
        synchronized (AdminConsoleHandler.class) {
            now = System.currentTimeMillis();
            if (now - playerCacheAt < PLAYER_CACHE_MS && playerCache != null) return playerCache;
            List<ConsolePlayer> fresh = new ArrayList<ConsolePlayer>();
            for (PlayerInfo info : PlayerInfoStore.instance().getAllPlayers()) {
                if (info == null || info.uuid == null || info.uuid.isEmpty()) continue;
                ConsolePlayer player = new ConsolePlayer();
                player.uuid = info.uuid;
                player.name = info.name == null || info.name.isEmpty() ? "?" : info.name;
                player.online = info.online;
                player.lastLogin = info.lastLogin;
                player.lastLogout = info.lastLogout;
                fresh.add(player);
            }
            Collections.sort(fresh, new Comparator<ConsolePlayer>() {
                @Override
                public int compare(ConsolePlayer left, ConsolePlayer right) {
                    if (left.online != right.online) return left.online ? -1 : 1;
                    return left.name.compareToIgnoreCase(right.name);
                }
            });
            playerCache = Collections.unmodifiableList(fresh);
            playerCacheAt = now;
            return playerCache;
        }
    }

    private static JsonObject success() {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        return response;
    }

    private static NanoHTTPD.Response error(NanoHTTPD.Response.Status status, String code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("code", code);
        response.addProperty("message", safe(message));
        return json(status, response);
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, JsonObject body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body.toString());
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "Request failed.";
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 500 ? clean : clean.substring(0, 500);
    }

    private static final class ExecuteRequest {
        String command = "";
        boolean confirmed;
    }

    private static final class PresetRequest {
        String id;
        String label = "";
        String command = "";
        String description = "";
    }

    private static final class ConsolePlayer {
        String uuid;
        String name;
        boolean online;
        long lastLogin;
        long lastLogout;
    }
}
