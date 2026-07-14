package com.imgood.textech.webae.api.handler;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.WebConsoleUrlHelper;
import com.imgood.textech.webae.access.WebAeNetworkKeys;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.player.WebAePlayerStateStore;

import fi.iki.elonen.NanoHTTPD;

/**
 * POST /api/auth/guest-invite — owner generates a shareable guest token link.
 * Optional body {@code networkKeys: string[]} limits guest to those networks
 * ({@code null}/omitted = all nets).
 */
public final class AuthGuestInviteHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private AuthGuestInviteHandler() {}

    public static NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, WebAuthSession auth) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return json(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "{\"success\":false,\"message\":\"Use POST /api/auth/guest-invite\"}");
        }
        if (auth.isGuest()) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Guest tokens cannot invite others.\"}");
        }
        String ownerUuid = auth.ownerUuid;
        if (WebAePlayerStateStore.getInstance().isDisabled(ownerUuid)) {
            return json(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                "{\"success\":false,\"code\":\"webae_disabled\",\"error\":\"webae_disabled\","
                    + "\"message\":\"WebAE has been disabled for this player.\"}");
        }
        if (WebAeOwnerContext.countMonitors(ownerUuid) <= 0) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Owner has no Advance Data Monitor bound.\"}");
        }
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        if (ownerName == null || ownerName.isEmpty()) {
            ownerName = auth.actorName != null && !auth.actorName.isEmpty() ? auth.actorName : "Owner";
        }
        List<String> networkKeys = parseNetworkKeys(readBody(session));
        if (networkKeys != null) {
            for (int i = 0; i < networkKeys.size(); i++) {
                if (!WebAeNetworkKeys.isValidKeyFormat(networkKeys.get(i))) {
                    return json(
                        NanoHTTPD.Response.Status.BAD_REQUEST,
                        "{\"success\":false,\"code\":\"bad_network_key\",\"message\":\"Invalid networkKeys entry.\"}");
                }
            }
        }
        WebAuthToken token = WebAuthToken.generateShareGuestToken(ownerUuid, ownerName, networkKeys);
        WebAeOwnerContext.invalidateConnectors(ownerUuid);
        InviteOk ok = new InviteOk();
        ok.success = true;
        ok.token = token.token;
        ok.url = WebConsoleUrlHelper.tokenLoginUrl(token.token);
        ok.tokenType = WebAuthSession.TYPE_GUEST;
        ok.allowedNetworkKeys = networkKeys;
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(ok));
    }

    private static List<String> parseNetworkKeys(String body) {
        if (body == null || body.trim()
            .isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            if (!obj.has("networkKeys") || obj.get("networkKeys")
                .isJsonNull()) {
                return null;
            }
            JsonArray arr = obj.getAsJsonArray("networkKeys");
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i)
                    .isJsonNull()) {
                    out.add(arr.get(i)
                        .getAsString());
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBody(NanoHTTPD.IHTTPSession session) {
        try {
            int contentLength = 0;
            String cl = session.getHeaders()
                .get("content-length");
            if (cl != null) {
                contentLength = Integer.parseInt(cl.trim());
            }
            if (contentLength <= 0) {
                return "";
            }
            byte[] buffer = new byte[contentLength];
            DataInputStream dis = new DataInputStream(session.getInputStream());
            dis.readFully(buffer);
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class InviteOk {

        boolean success;
        String token;
        String url;
        String tokenType;
        List<String> allowedNetworkKeys;
    }
}
