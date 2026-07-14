package com.imgood.textech.webae.api.handler;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.access.WebAeNetworkAclStore;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.access.WebAeNetworkSuspendStore;
import com.imgood.textech.webae.access.WebAeNetworkSuspendStore.SuspendEntry;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.auth.WebLoginCodeStore;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotCache.StorageSnapshotConsumer;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.context.NetworkRegistry;
import com.imgood.textech.webae.context.NetworkRegistry.RegisteredNetwork;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.StorageDto;
import com.imgood.textech.webae.player.PlayerInfo;
import com.imgood.textech.webae.player.PlayerInfoStore;
import com.imgood.textech.webae.player.WebAePlayerState;
import com.imgood.textech.webae.player.WebAePlayerStateStore;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;
import com.imgood.textech.webae.topology.TopologyCache;
import com.imgood.textech.webae.worldmap.WorldMapTileInvalidator;

import fi.iki.elonen.NanoHTTPD;

/**
 * Admin player management API handler.
 *
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /api/admin/players} — list player summaries</li>
 *   <li>{@code GET /api/admin/players/:uuid/access} — owned nets + guest access detail</li>
 *   <li>{@code POST /api/admin/players/:uuid/disable|enable|clear-cache}</li>
 *   <li>{@code POST .../networks/:networkKey/suspend|resume}</li>
 *   <li>{@code POST /api/admin/players/:uuid/acl} — deny/allow guest network</li>
 *   <li>{@code POST /api/admin/players/:uuid/guest-tokens/revoke}</li>
 *   <li>{@code POST /api/admin/players/:uuid/guest-tokens/allowlist}</li>
 * </ul>
 * </p>
 */
public final class AdminPlayerHandler {

    private AdminPlayerHandler() {}

    public static NanoHTTPD.Response handle(
        String uri,
        Map<String, String> params,
        NanoHTTPD.Method method,
        WebAuthSession auth,
        String adminHeader,
        String body) {

        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(403, "admin_required", "Admin privileges required.");
        }

        Map<String, String> merged = mergeBodyParams(params, body);

        if ("/api/admin/players".equals(uri) && method == NanoHTTPD.Method.GET) {
            return handleListPlayers(auth);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/access") && method == NanoHTTPD.Method.GET) {
            String targetUuid = extractUuid(uri, "/access");
            return handleAccessDetail(targetUuid);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/disable")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleDisable(extractUuid(uri, "/disable"), merged);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/enable")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleEnable(extractUuid(uri, "/enable"));
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/clear-cache")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleClearCache(extractUuid(uri, "/clear-cache"));
        }

        if (uri.contains("/networks/") && uri.endsWith("/suspend")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleSuspend(uri, merged);
        }

        if (uri.contains("/networks/") && uri.endsWith("/resume")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleResume(uri);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/acl")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            String actorUuid = extractUuid(uri, "/acl");
            return handleAcl(actorUuid, merged);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/guest-tokens/revoke")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleRevokeGuestToken(extractUuid(uri, "/guest-tokens/revoke"), merged);
        }

        if (uri.startsWith("/api/admin/players/") && uri.endsWith("/guest-tokens/allowlist")) {
            if (method != NanoHTTPD.Method.POST) {
                return methodNotAllowed("Use POST");
            }
            return handleUpdateAllowlist(extractUuid(uri, "/guest-tokens/allowlist"), merged);
        }

        return json(404, "not_found", "Unknown admin player endpoint.");
    }

    // ---- List players ----

    private static NanoHTTPD.Response handleListPlayers(WebAuthSession auth) {
        java.util.Set<String> allOwners = new java.util.LinkedHashSet<String>();

        for (String owner : WebAuthToken.listActiveOwnerUuids()) {
            if (owner != null && !owner.isEmpty()) {
                allOwners.add(owner);
            }
        }
        for (PlayerInfo info : PlayerInfoStore.instance().getAllPlayers()) {
            if (info != null && info.uuid != null && !info.uuid.isEmpty()) {
                allOwners.add(info.uuid);
            }
        }
        for (String owner : NetworkRegistry.getAllOwnerUuids()) {
            if (owner != null && !owner.isEmpty()) {
                allOwners.add(owner);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"players\":[");
        boolean first = true;

        for (String ownerUuid : allOwners) {
            if (!first) sb.append(",");
            first = false;

            WebAePlayerState state = WebAePlayerStateStore.getInstance().getState(ownerUuid);
            PlayerInfo playerInfo = null;
            try {
                playerInfo = PlayerInfoStore.instance().getPlayer(java.util.UUID.fromString(ownerUuid));
            } catch (IllegalArgumentException ignored) {}

            String name = "?";
            boolean online = false;
            if (playerInfo != null) {
                name = playerInfo.name != null ? playerInfo.name : "?";
                online = playerInfo.online;
            }
            if (state != null && state.playerName != null && !state.playerName.isEmpty()) {
                name = state.playerName;
            }

            List<RegisteredNetwork> networks = NetworkRegistry.getRawNetworks(ownerUuid);
            int networkCount = networks != null ? networks.size() : 0;

            final long[] itemSum = {0L};
            final long[] fluidSum = {0L};
            SnapshotCache.instance().forEachStorageSnapshotForOwner(
                ownerUuid,
                new StorageSnapshotConsumer() {
                    public void accept(StorageDto dto) {
                        if (dto.items != null) {
                            for (StorageDto.ItemEntry e : dto.items) {
                                if (e.amount > 0) itemSum[0] += e.amount;
                            }
                        }
                        if (dto.fluids != null) {
                            for (StorageDto.FluidEntry e : dto.fluids) {
                                if (e.amount > 0) fluidSum[0] += e.amount;
                            }
                        }
                    }
                });

            sb.append("{");
            sb.append("\"uuid\":\"").append(escapeJson(ownerUuid)).append("\",");
            sb.append("\"name\":\"").append(escapeJson(name)).append("\",");
            sb.append("\"online\":").append(online).append(",");
            sb.append("\"lastActiveAt\":").append(state != null ? state.lastActiveAt : 0).append(",");
            sb.append("\"networkCount\":").append(networkCount).append(",");
            sb.append("\"disabled\":").append(state != null && state.disabled).append(",");
            if (state != null && state.disabled && state.disabledReason != null) {
                sb.append("\"disabledReason\":\"").append(escapeJson(state.disabledReason)).append("\",");
            }
            sb.append("\"requestCount\":").append(state != null ? state.requestCount : 0).append(",");
            long avgMs = 0;
            if (state != null && state.requestCount > 0) {
                avgMs = state.totalResponseMs / state.requestCount;
            }
            sb.append("\"avgResponseMs\":").append(avgMs).append(",");
            sb.append("\"totalItems\":").append(itemSum[0]).append(",");
            sb.append("\"totalFluids\":").append(fluidSum[0]);
            sb.append("}");
        }

        sb.append("]}");
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", sb.toString());
    }

    // ---- Access detail ----

    private static NanoHTTPD.Response handleAccessDetail(String targetUuid) {
        if (targetUuid == null || targetUuid.isEmpty()) {
            return json(400, "missing_uuid", "Missing player UUID.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"uuid\":\"").append(escapeJson(targetUuid)).append("\",");

        // Owned networks (admin metadata — includes suspended)
        sb.append("\"ownedNetworks\":[");
        List<NetworkInfo> owned = WebAeOwnerContext.findNetworksForOwner(targetUuid);
        boolean first = true;
        for (int i = 0; i < owned.size(); i++) {
            NetworkInfo n = owned.get(i);
            String key = WebAeNetworkKeys.fromNetworkInfo(n);
            SuspendEntry susp = WebAeNetworkSuspendStore.get(targetUuid, key);
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"networkId\":").append(n.networkId).append(",");
            sb.append("\"networkKey\":\"").append(escapeJson(key)).append("\",");
            sb.append("\"monitorDim\":").append(n.monitorDim).append(",");
            sb.append("\"monitorX\":").append(n.monitorX).append(",");
            sb.append("\"monitorY\":").append(n.monitorY).append(",");
            sb.append("\"monitorZ\":").append(n.monitorZ).append(",");
            sb.append("\"healthy\":").append(n.healthy).append(",");
            sb.append("\"suspended\":").append(susp != null);
            if (susp != null) {
                sb.append(",\"suspendReason\":\"").append(escapeJson(susp.reason != null ? susp.reason : "")).append("\"");
                sb.append(",\"suspendedAt\":").append(susp.suspendedAt);
            }
            sb.append("}");
        }
        sb.append("],");

        // Guest access: tokens where this uuid is the guest actor
        sb.append("\"guestAccess\":[");
        List<WebAuthToken> guestTokens = WebAuthToken.listGuestTokensForActor(targetUuid);
        first = true;
        for (int i = 0; i < guestTokens.size(); i++) {
            WebAuthToken t = guestTokens.get(i);
            if (!first) sb.append(",");
            first = false;
            String ownerName = WebAeOwnerContext.resolveOwnerName(t.ownerUuid);
            sb.append("{");
            sb.append("\"ownerUuid\":\"").append(escapeJson(t.ownerUuid)).append("\",");
            sb.append("\"ownerName\":\"").append(escapeJson(ownerName)).append("\",");
            sb.append("\"token\":\"").append(escapeJson(t.token)).append("\",");
            sb.append("\"tokenPrefix\":\"").append(escapeJson(t.token != null && t.token.length() >= 8
                ? t.token.substring(0, 8) : (t.token != null ? t.token : ""))).append("\",");
            if (t.allowedNetworkKeys == null) {
                sb.append("\"allowedNetworkKeys\":null,");
            } else {
                sb.append("\"allowedNetworkKeys\":[");
                for (int k = 0; k < t.allowedNetworkKeys.size(); k++) {
                    if (k > 0) sb.append(",");
                    sb.append("\"").append(escapeJson(t.allowedNetworkKeys.get(k))).append("\"");
                }
                sb.append("],");
            }
            sb.append("\"networks\":[");
            List<NetworkInfo> ownerNets = WebAeOwnerContext.findNetworksForOwner(t.ownerUuid);
            boolean nf = true;
            for (int n = 0; n < ownerNets.size(); n++) {
                NetworkInfo info = ownerNets.get(n);
                String key = WebAeNetworkKeys.fromNetworkInfo(info);
                boolean suspended = WebAeNetworkSuspendStore.isSuspended(t.ownerUuid, key);
                boolean denied = WebAeNetworkAclStore.isDenied(t.ownerUuid, targetUuid, key);
                boolean inAllowlist = t.allowedNetworkKeys == null
                    || (t.allowedNetworkKeys != null && containsKey(t.allowedNetworkKeys, key));
                if (!nf) sb.append(",");
                nf = false;
                sb.append("{");
                sb.append("\"networkKey\":\"").append(escapeJson(key)).append("\",");
                sb.append("\"networkId\":").append(info.networkId).append(",");
                sb.append("\"inAllowlist\":").append(inAllowlist).append(",");
                sb.append("\"deniedByAcl\":").append(denied).append(",");
                sb.append("\"suspended\":").append(suspended);
                sb.append("}");
            }
            sb.append("]}");
        }
        sb.append("]}");
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", sb.toString());
    }

    private static boolean containsKey(List<String> keys, String key) {
        for (int i = 0; i < keys.size(); i++) {
            if (key.equals(keys.get(i))) {
                return true;
            }
        }
        return false;
    }

    // ---- Disable ----

    private static NanoHTTPD.Response handleDisable(String targetUuid, Map<String, String> params) {
        if (targetUuid == null || targetUuid.isEmpty()) {
            return json(400, "missing_uuid", "Missing player UUID.");
        }
        String reason = params != null ? params.get("reason") : null;
        if (reason == null || reason.isEmpty()) {
            reason = "Disabled by admin";
        }
        WebAePlayerStateStore.getInstance().setDisabled(targetUuid, reason);
        int revoked = WebAuthToken.revokeAllForPlayer(targetUuid);
        WebLoginCodeStore.invalidateCodesForOwner(targetUuid);
        SnapshotCache.instance().invalidateAll(targetUuid);
        SnapshotScheduler.clearActiveForOwner(targetUuid);

        AdvanceDataMonitor.LOG.info(
            "[WebAE] Admin disabled WebAE for player {} reason={} revokedTokens={}",
            targetUuid, reason, Integer.valueOf(revoked));
        return json(200, "{\"success\":true,\"revokedTokens\":" + revoked + "}");
    }

    // ---- Enable ----

    private static NanoHTTPD.Response handleEnable(String targetUuid) {
        if (targetUuid == null || targetUuid.isEmpty()) {
            return json(400, "missing_uuid", "Missing player UUID.");
        }
        WebAePlayerStateStore.getInstance().setEnabled(targetUuid);

        AdvanceDataMonitor.LOG.info("[WebAE] Admin enabled WebAE for player {}", targetUuid);
        return json(200, "{\"success\":true}");
    }

    // ---- Clear cache ----

    private static NanoHTTPD.Response handleClearCache(String targetUuid) {
        if (targetUuid == null || targetUuid.isEmpty()) {
            return json(400, "missing_uuid", "Missing player UUID.");
        }
        int beforeSize = SnapshotCache.instance().size();
        SnapshotCache.instance().invalidateAll(targetUuid);
        int snapshotCleared = beforeSize - SnapshotCache.instance().size();

        TopologyCache.invalidateOwner(targetUuid);

        int mapCleared = 0;
        List<RegisteredNetwork> networks = NetworkRegistry.getRawNetworks(targetUuid);
        if (networks != null) {
            for (int i = 0; i < networks.size(); i++) {
                mapCleared += WorldMapTileInvalidator.invalidateNetwork(targetUuid, i, null);
            }
        }

        AdvanceDataMonitor.LOG.info(
            "[WebAE] Admin cleared cache for player {} (snapshots={}, mapTiles={})",
            targetUuid, Integer.valueOf(snapshotCleared), Integer.valueOf(mapCleared));

        return json(200, "{\"success\":true}");
    }

    // ---- Suspend / resume ----

    private static NanoHTTPD.Response handleSuspend(String uri, Map<String, String> params) {
        String[] parts = parseOwnerAndNetworkKey(uri, "/suspend");
        if (parts == null) {
            return json(400, "bad_path", "Expected /api/admin/players/:uuid/networks/:networkKey/suspend");
        }
        String ownerUuid = parts[0];
        String networkKey = parts[1];
        if (!WebAeNetworkKeys.isValidKeyFormat(networkKey)) {
            return json(400, "bad_network_key", "Invalid networkKey format.");
        }
        String reason = params != null ? params.get("reason") : null;
        if (reason == null || reason.isEmpty()) {
            reason = "Suspended by admin";
        }
        WebAeNetworkSuspendStore.suspend(ownerUuid, networkKey, reason);
        Integer nid = WebAeNetworkKeys.toNetworkId(ownerUuid, networkKey);
        if (nid != null) {
            SnapshotCache.instance().invalidateAll(ownerUuid, nid.intValue());
        }
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Admin suspended network {} for owner {} reason={}", networkKey, ownerUuid, reason);
        return json(200, "{\"success\":true}");
    }

    private static NanoHTTPD.Response handleResume(String uri) {
        String[] parts = parseOwnerAndNetworkKey(uri, "/resume");
        if (parts == null) {
            return json(400, "bad_path", "Expected /api/admin/players/:uuid/networks/:networkKey/resume");
        }
        String ownerUuid = parts[0];
        String networkKey = parts[1];
        boolean ok = WebAeNetworkSuspendStore.resume(ownerUuid, networkKey);
        AdvanceDataMonitor.LOG.info(
            "[WebAE] Admin resumed network {} for owner {} found={}", networkKey, ownerUuid, Boolean.valueOf(ok));
        return json(200, "{\"success\":true,\"resumed\":" + ok + "}");
    }

    // ---- ACL deny / allow ----

    private static NanoHTTPD.Response handleAcl(String actorUuid, Map<String, String> params) {
        if (actorUuid == null || actorUuid.isEmpty()) {
            return json(400, "missing_uuid", "Missing actor UUID.");
        }
        String ownerUuid = params != null ? params.get("ownerUuid") : null;
        String networkKey = params != null ? params.get("networkKey") : null;
        String effect = params != null ? params.get("effect") : null;
        if (ownerUuid == null || ownerUuid.isEmpty() || networkKey == null || networkKey.isEmpty()) {
            return json(400, "missing_fields", "Need ownerUuid and networkKey.");
        }
        if ("deny".equalsIgnoreCase(effect)) {
            WebAeNetworkAclStore.deny(ownerUuid, actorUuid, networkKey);
            return json(200, "{\"success\":true,\"effect\":\"deny\"}");
        }
        if ("allow".equalsIgnoreCase(effect)) {
            WebAeNetworkAclStore.clearDeny(ownerUuid, actorUuid, networkKey);
            return json(200, "{\"success\":true,\"effect\":\"allow\"}");
        }
        return json(400, "bad_effect", "effect must be deny or allow.");
    }

    private static NanoHTTPD.Response handleRevokeGuestToken(String actorUuid, Map<String, String> params) {
        String token = params != null ? params.get("token") : null;
        if (token == null || token.isEmpty()) {
            return json(400, "missing_token", "Missing token.");
        }
        boolean ok = WebAuthToken.revokeTokenByToken(token);
        return json(200, "{\"success\":" + ok + "}");
    }

    private static NanoHTTPD.Response handleUpdateAllowlist(String actorUuid, Map<String, String> params) {
        String token = params != null ? params.get("token") : null;
        String keysJson = params != null ? params.get("networkKeys") : null;
        if (token == null || token.isEmpty()) {
            return json(400, "missing_token", "Missing token.");
        }
        List<String> keys = parseNetworkKeysParam(keysJson);
        // null keysJson → legacy all (null allowlist); "[]" → empty; JSON array → list
        if (keysJson == null) {
            keys = null;
        }
        boolean ok = WebAuthToken.updateGuestAllowlist(token, keys);
        return json(200, "{\"success\":" + ok + "}");
    }

    private static List<String> parseNetworkKeysParam(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        List<String> out = new ArrayList<String>();
        if (trimmed.startsWith("[")) {
            try {
                JsonArray arr = new JsonParser().parse(trimmed)
                    .getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i)
                        .isJsonNull()) {
                        out.add(arr.get(i)
                            .getAsString());
                    }
                }
            } catch (Exception ignored) {}
            return out;
        }
        String[] parts = trimmed.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static String[] parseOwnerAndNetworkKey(String uri, String suffix) {
        String prefix = "/api/admin/players/";
        String mid = "/networks/";
        if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) {
            return null;
        }
        String midSection = uri.substring(prefix.length(), uri.length() - suffix.length());
        int midIdx = midSection.indexOf(mid);
        if (midIdx <= 0) {
            return null;
        }
        String ownerUuid = midSection.substring(0, midIdx);
        String networkKey = midSection.substring(midIdx + mid.length());
        try {
            networkKey = URLDecoder.decode(networkKey, "UTF-8");
        } catch (Exception ignored) {}
        if (ownerUuid.isEmpty() || networkKey.isEmpty()) {
            return null;
        }
        return new String[] { ownerUuid, networkKey };
    }

    // ---- Helpers ----

    private static Map<String, String> mergeBodyParams(Map<String, String> params, String body) {
        Map<String, String> merged = new HashMap<String, String>();
        if (params != null) {
            merged.putAll(params);
        }
        if (body == null || body.trim()
            .isEmpty()) {
            return merged;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue() == null || e.getValue()
                    .isJsonNull()) {
                    continue;
                }
                if (e.getValue()
                    .isJsonArray() || e.getValue()
                        .isJsonObject()) {
                    merged.put(e.getKey(), e.getValue()
                        .toString());
                } else {
                    merged.put(e.getKey(), e.getValue()
                        .getAsString());
                }
            }
        } catch (Exception ignored) {}
        return merged;
    }

    private static String extractUuid(String uri, String suffix) {
        String prefix = "/api/admin/players/";
        if (!uri.startsWith(prefix)) return null;
        String sub = uri.substring(prefix.length());
        int endIdx = sub.lastIndexOf(suffix);
        if (endIdx <= 0) return null;
        return sub.substring(0, endIdx);
    }

    private static NanoHTTPD.Response json(int status, String code, String message) {
        return json(status, "{\"success\":false,\"code\":\"" + escapeJson(code)
            + "\",\"error\":\"" + escapeJson(code)
            + "\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static NanoHTTPD.Response json(int status, String body) {
        return NanoHTTPD.newFixedLengthResponse(
            status == 200 ? NanoHTTPD.Response.Status.OK
                : status == 400 ? NanoHTTPD.Response.Status.BAD_REQUEST
                : status == 401 ? NanoHTTPD.Response.Status.UNAUTHORIZED
                : status == 403 ? NanoHTTPD.Response.Status.FORBIDDEN
                : status == 404 ? NanoHTTPD.Response.Status.NOT_FOUND
                : NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            body);
    }

    private static NanoHTTPD.Response methodNotAllowed(String message) {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
            "application/json",
            "{\"success\":false,\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
