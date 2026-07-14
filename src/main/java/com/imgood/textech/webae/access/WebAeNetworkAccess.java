package com.imgood.textech.webae.access;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.snapshot.AeSnapshotCollector.NetworkInfo;

import fi.iki.elonen.NanoHTTPD;

/**
 * Unified WebAE network visibility / access checks (suspend → guest allowlist → ACL deny).
 */
public final class WebAeNetworkAccess {

    private WebAeNetworkAccess() {}

    /**
     * @return denial response, or {@code null} if access is allowed
     */
    public static NanoHTTPD.Response assertCanAccess(WebAuthSession session, String ownerUuid, int networkId) {
        String key = WebAeNetworkKeys.fromNetworkId(ownerUuid, networkId);
        if (key == null) {
            return null;
        }
        return assertCanAccessKey(session, ownerUuid, key);
    }

    /**
     * @return denial response, or {@code null} if access is allowed
     */
    public static NanoHTTPD.Response assertCanAccessKey(WebAuthSession session, String ownerUuid, String networkKey) {
        if (networkKey == null || networkKey.isEmpty()) {
            return null;
        }
        if (WebAeNetworkSuspendStore.isSuspended(ownerUuid, networkKey)) {
            return denial(403, "network_suspended", "This AE network has been suspended in WebAE.");
        }
        if (session != null && session.isGuest()) {
            if (!isInGuestAllowlist(session, networkKey)) {
                return denial(403, "network_access_denied", "Guest session cannot access this network.");
            }
            if (WebAeNetworkAclStore.isDenied(ownerUuid, session.actorUuid, networkKey)) {
                return denial(403, "network_access_denied", "Access to this network was revoked by an admin.");
            }
        }
        return null;
    }

    public static boolean canAccess(WebAuthSession session, String ownerUuid, String networkKey) {
        return assertCanAccessKey(session, ownerUuid, networkKey) == null;
    }

    public static List<NetworkInfo> filterVisible(WebAuthSession session, String ownerUuid, List<NetworkInfo> networks) {
        List<NetworkInfo> out = new ArrayList<NetworkInfo>();
        if (networks == null) {
            return out;
        }
        for (int i = 0; i < networks.size(); i++) {
            NetworkInfo info = networks.get(i);
            if (info == null) {
                continue;
            }
            String key = WebAeNetworkKeys.fromNetworkInfo(info);
            if (key == null) {
                continue;
            }
            if (canAccess(session, ownerUuid, key)) {
                out.add(info);
            }
        }
        return out;
    }

    public static boolean isInGuestAllowlist(WebAuthSession session, String networkKey) {
        if (session == null || !session.isGuest()) {
            return true;
        }
        List<String> keys = session.allowedNetworkKeys;
        // null = legacy all nets
        if (keys == null) {
            return true;
        }
        if (keys.isEmpty()) {
            return false;
        }
        for (int i = 0; i < keys.size(); i++) {
            if (networkKey.equals(keys.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static NanoHTTPD.Response denial(int status, String code, String message) {
        String body = "{\"success\":false,\"code\":\"" + escape(code) + "\",\"error\":\"" + escape(code)
            + "\",\"message\":\"" + escape(message) + "\"}";
        NanoHTTPD.Response.Status st = status == 403 ? NanoHTTPD.Response.Status.FORBIDDEN
            : NanoHTTPD.Response.Status.UNAUTHORIZED;
        return NanoHTTPD.newFixedLengthResponse(st, "application/json", body);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }
}
