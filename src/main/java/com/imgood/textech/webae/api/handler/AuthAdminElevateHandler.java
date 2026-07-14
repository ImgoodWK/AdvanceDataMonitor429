package com.imgood.textech.webae.api.handler;

import java.util.Map;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.auth.WebAdminBootstrapStore;
import com.imgood.textech.webae.auth.WebAdminGrantStore;
import com.imgood.textech.webae.auth.WebAdminGrantStore.GrantEntry;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;

/**
 * POST /api/auth/admin/elevate — exchange a one-time bootstrap code for a
 * long-lived admin grant token.
 *
 * <p>
 * Constraints:
 * <ul>
 *   <li>Must be authenticated with a valid owner (non-guest) session.</li>
 *   <li>Rate-limited by IP and owner UUID (5 failures / 10 min).</li>
 *   <li>Bootstrap code is consumed on first use (unless --reuse was set).</li>
 * </ul>
 * </p>
 */
public final class AuthAdminElevateHandler {

    private static final int MAX_FAILURES_PER_WINDOW = 5;
    private static final long FAILURE_WINDOW_MS = 10 * 60_000L;

    private static final java.util.Map<String, java.util.List<Long>> ipFailureTimestamps = new java.util.HashMap<String, java.util.List<Long>>();
    private static final java.util.Map<String, java.util.List<Long>> ownerFailureTimestamps = new java.util.HashMap<String, java.util.List<Long>>();

    private AuthAdminElevateHandler() {}

    public static NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        // Guest cannot elevate
        if (auth.isGuest()) {
            return json(403, "admin_elevate_denied", "Guest tokens cannot request admin elevation.");
        }

        Map<String, String> params = session.getParms();
        Map<String, String> headers = session.getHeaders();

        String bootstrapCode = extractParam(params, headers, "code");
        String label = extractParam(params, headers, "label");

        if (bootstrapCode == null || bootstrapCode.isEmpty()) {
            return json(400, "missing_code", "Missing 'code' parameter (bootstrap code).");
        }

        // Extract client IP for rate limiting
        String clientIp = getClientIp(headers);

        // Rate limit check
        if (isRateLimited(clientIp, auth.ownerUuid)) {
            return json(429, "rate_limited", "Too many elevation attempts. Please wait.");
        }

        // Validate bootstrap code
        if (!WebAdminBootstrapStore.consume(bootstrapCode)) {
            recordFailure(clientIp, auth.ownerUuid);
            return json(403, "admin_elevate_denied", "Invalid or already used bootstrap code.");
        }

        // Generate grant
        GrantEntry grant = WebAdminGrantStore.createGrant(
            auth.ownerUuid,
            auth.actorUuid,
            auth.actorName,
            label);

        if (grant == null) {
            return json(500, "internal_error", "Failed to create admin grant.");
        }

        AdvanceDataMonitor.LOG.info(
            "[WebAE] Admin grant created: owner={} actor={} label={} from_ip={}",
            auth.ownerUuid, auth.actorUuid, label != null ? label : "", clientIp);

        return json(200,
            "{\"status\":\"ok\",\"adminToken\":\"" + escapeJson(grant.adminToken)
                + "\",\"expiresAt\":"
                + grant.expiresAt
                + ",\"issuedAt\":"
                + grant.issuedAt
                + ",\"boundActorUuid\":\"" + escapeJson(grant.boundActorUuid) + "\"}");
    }

    /** GET /api/auth/admin/me — return current admin status and capabilities. */
    public static NanoHTTPD.Response handleMe(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        Map<String, String> headers = session.getHeaders();
        String adminHeader = getAdminHeader(headers);
        boolean isAdmin = com.imgood.textech.webae.auth.WebAuthAdminCheck.isAdmin(auth, adminHeader);
        boolean isOnlineOp = com.imgood.textech.webae.auth.WebAuthOpCheck.isOp(auth.actorUuid);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"ok\"");
        sb.append(",\"ownerUuid\":\"").append(escapeJson(auth.ownerUuid)).append("\"");
        sb.append(",\"actorUuid\":\"").append(escapeJson(auth.actorUuid)).append("\"");
        sb.append(",\"actorName\":\"").append(escapeJson(auth.actorName)).append("\"");
        sb.append(",\"tokenType\":\"").append(escapeJson(auth.type)).append("\"");
        sb.append(",\"isAdmin\":").append(isAdmin);
        sb.append(",\"isOnlineOp\":").append(isOnlineOp);
        sb.append(",\"capabilities\":{");
        sb.append("\"admin\":").append(isAdmin);
        sb.append(",\"canForceSnapshot\":").append(isAdmin);
        sb.append(",\"canEditRules\":").append(isAdmin);
        sb.append(",\"canManageTokens\":").append(isAdmin);
        sb.append(",\"canUploadPacks\":").append(isAdmin);
        sb.append(",\"canViewPocketOverview\":").append(isAdmin);
        sb.append(",\"canInvalidateWorldMap\":").append(isAdmin);
        sb.append(",\"isGuest\":").append(auth.isGuest());
        sb.append("}}");

        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", sb.toString());
    }

    /** GET /api/auth/admin/grants — list admin grants for this owner (self). */
    public static NanoHTTPD.Response handleListGrants(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        java.util.List<GrantEntry> grants = WebAdminGrantStore.listByOwner(auth.ownerUuid);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"ok\",\"grants\":[");
        boolean first = true;
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
        for (GrantEntry g : grants) {
            if (!first) sb.append(",");
            sb.append(gson.toJson(g));
            first = false;
        }
        sb.append("]}");
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, "application/json", sb.toString());
    }

    /** POST /api/auth/admin/revoke-self — revoke this device's admin grant. */
    public static NanoHTTPD.Response handleRevokeSelf(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        Map<String, String> headers = session.getHeaders();
        String adminHeader = getAdminHeader(headers);
        if (adminHeader == null || adminHeader.isEmpty()) {
            return json(400, "missing_header", "X-WebAE-Admin header required.");
        }
        boolean removed = WebAdminGrantStore.revokeByToken(adminHeader);
        if (removed) {
            return json(200, "{\"status\":\"ok\",\"revoked\":true}");
        }
        return json(404, "not_found", "Admin grant not found.");
    }

    // ---- helpers ----

    private static String extractParam(Map<String, String> params, Map<String, String> headers, String key) {
        if (params != null) {
            String val = params.get(key);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    private static String getClientIp(Map<String, String> headers) {
        if (headers == null) return "unknown";
        String xff = headers.get("x-forwarded-for");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return comma >= 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return "unknown";
    }

    private static String getAdminHeader(Map<String, String> headers) {
        if (headers == null) return null;
        String val = headers.get("x-webae-admin");
        if (val == null || val.isEmpty()) {
            val = headers.get("X-WebAE-Admin");
        }
        return val;
    }

    private static synchronized boolean isRateLimited(String ip, String ownerUuid) {
        long now = System.currentTimeMillis();
        long cutoff = now - FAILURE_WINDOW_MS;
        return countRecent(ipFailureTimestamps, ip, cutoff) >= MAX_FAILURES_PER_WINDOW
            || countRecent(ownerFailureTimestamps, ownerUuid, cutoff) >= MAX_FAILURES_PER_WINDOW;
    }

    private static synchronized void recordFailure(String ip, String ownerUuid) {
        long now = System.currentTimeMillis();
        addTimestamp(ipFailureTimestamps, ip, now);
        addTimestamp(ownerFailureTimestamps, ownerUuid, now);
    }

    private static int countRecent(java.util.Map<String, java.util.List<Long>> map, String key, long cutoff) {
        java.util.List<Long> list = map.get(key);
        if (list == null) return 0;
        int count = 0;
        for (long ts : list) {
            if (ts > cutoff) count++;
        }
        return count;
    }

    private static void addTimestamp(java.util.Map<String, java.util.List<Long>> map, String key, long now) {
        java.util.List<Long> list = map.get(key);
        if (list == null) {
            list = new java.util.ArrayList<Long>();
            map.put(key, list);
        }
        list.add(now);
        // trim old entries
        long cutoff = now - FAILURE_WINDOW_MS;
        java.util.Iterator<Long> iter = list.iterator();
        while (iter.hasNext()) {
            if (iter.next() <= cutoff) iter.remove();
        }
    }

    private static NanoHTTPD.Response json(int status, String code, String message) {
        return json(status, "{\"status\":\"error\",\"code\":\"" + escapeJson(code)
            + "\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private static NanoHTTPD.Response json(int status, String body) {
        return NanoHTTPD.newFixedLengthResponse(
            status == 200 ? NanoHTTPD.Response.Status.OK
                : status == 400 ? NanoHTTPD.Response.Status.BAD_REQUEST
                : status == 403 ? NanoHTTPD.Response.Status.FORBIDDEN
                : status == 404 ? NanoHTTPD.Response.Status.NOT_FOUND
                : status == 429 ? NanoHTTPD.Response.Status.INTERNAL_ERROR // NanoHTTPD has no 429, fallback
                : NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            body);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
