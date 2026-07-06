package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.WebConsoleUrlHelper;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.auth.WebAuthToken;
import com.imgood.textech.webae.context.WebAeOwnerContext;

import fi.iki.elonen.NanoHTTPD;

/**
 * POST /api/auth/guest-invite — owner generates a shareable guest token link (Phase 6.2).
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
        if (WebAeOwnerContext.countMonitors(ownerUuid) <= 0) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"message\":\"Owner has no Advance Data Monitor bound.\"}");
        }
        String ownerName = WebAeOwnerContext.resolveOwnerName(ownerUuid);
        if (ownerName == null || ownerName.isEmpty()) {
            ownerName = auth.actorName != null && !auth.actorName.isEmpty() ? auth.actorName : "Owner";
        }
        WebAuthToken token = WebAuthToken.generateShareGuestToken(ownerUuid, ownerName);
        WebAeOwnerContext.invalidateConnectors(ownerUuid);
        InviteOk ok = new InviteOk();
        ok.success = true;
        ok.token = token.token;
        ok.url = WebConsoleUrlHelper.tokenLoginUrl(token.token);
        ok.tokenType = WebAuthSession.TYPE_GUEST;
        return json(NanoHTTPD.Response.Status.OK, GSON.toJson(ok));
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class InviteOk {

        boolean success;
        String token;
        String url;
        String tokenType;
    }
}
