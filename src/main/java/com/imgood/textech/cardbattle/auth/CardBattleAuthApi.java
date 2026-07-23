package com.imgood.textech.cardbattle.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.auth.WebAuthSession;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Account auth endpoints for embedded Card Battle HTTP (register/login/bind).
 */
public final class CardBattleAuthApi {

    private CardBattleAuthApi() {}

    /** @return response, or null if URI is not an auth route */
    public static Response tryHandle(IHTTPSession session, String uri, Method method, String body,
        ResponseFactory factory) {
        try {
            JsonObject bodyJson = parseBody(body);
            if ("/api/auth/register".equals(uri) && method == Method.POST) {
                CardBattleAccounts.AuthResult result = CardBattleAccounts.register(
                    str(bodyJson, "username"),
                    str(bodyJson, "password"),
                    str(bodyJson, "displayName"));
                JsonObject o = new JsonObject();
                o.addProperty("token", result.token);
                o.add("user", CardBattleAccounts.publicUser(result.user));
                return factory.json(Response.Status.CREATED, o);
            }
            if ("/api/auth/login".equals(uri) && method == Method.POST) {
                CardBattleAccounts.AuthResult result = CardBattleAccounts.login(
                    str(bodyJson, "username"),
                    str(bodyJson, "password"));
                JsonObject o = new JsonObject();
                o.addProperty("token", result.token);
                o.add("user", CardBattleAccounts.publicUser(result.user));
                return factory.json(Response.Status.OK, o);
            }
            if ("/api/auth/logout".equals(uri) && method == Method.POST) {
                String token = bearer(session);
                if (token != null) CardBattleAccounts.logout(token);
                JsonObject o = new JsonObject();
                o.addProperty("status", "ok");
                return factory.json(Response.Status.OK, o);
            }
            if ("/api/auth/change-password".equals(uri) && method == Method.POST) {
                CardBattleAccounts.User user = requireAccount(session, factory);
                if (user == null) return factory.lastError();
                CardBattleAccounts.changePassword(
                    user.id,
                    str(bodyJson, "currentPassword"),
                    str(bodyJson, "newPassword"));
                JsonObject o = new JsonObject();
                o.addProperty("status", "ok");
                o.addProperty("message", "密码已更新，请重新登录");
                return factory.json(Response.Status.OK, o);
            }
            if ("/api/me/bind".equals(uri) && method == Method.POST) {
                CardBattleAccounts.User user = requireAccount(session, factory);
                if (user == null) return factory.lastError();
                CardBattleAccounts.User bound = CardBattleAccounts.bindWithCode(user.id, str(bodyJson, "code"));
                JsonObject o = new JsonObject();
                o.add("user", CardBattleAccounts.publicUser(bound));
                JsonObject binding = new JsonObject();
                binding.addProperty("bound", true);
                binding.addProperty("mcUuid", bound.mcUuid);
                binding.addProperty("mcName", bound.mcName);
                o.add("binding", binding);
                return factory.json(Response.Status.OK, o);
            }
            if ("/api/me/bind".equals(uri) && method == Method.DELETE) {
                CardBattleAccounts.User user = requireAccount(session, factory);
                if (user == null) return factory.lastError();
                CardBattleAccounts.User unbound = CardBattleAccounts.unbind(user.id);
                JsonObject o = new JsonObject();
                o.add("user", CardBattleAccounts.publicUser(unbound));
                JsonObject binding = new JsonObject();
                binding.addProperty("bound", false);
                o.add("binding", binding);
                return factory.json(Response.Status.OK, o);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return factory.jsonError(Response.Status.BAD_REQUEST, "bad_request", e.getMessage());
        } catch (Throwable t) {
            return factory.jsonError(Response.Status.INTERNAL_ERROR, "error", t.getMessage());
        }
    }

    public static JsonObject meJson(WebAuthSession auth) {
        JsonObject o = new JsonObject();
        o.addProperty("ownerUuid", auth.ownerUuid);
        o.addProperty("actorUuid", auth.actorUuid);
        o.addProperty("actorName", auth.actorName);
        o.addProperty("type", auth.type);
        CardBattleAccounts.User user = CardBattleAccounts.resolveSession(auth.token);
        if (user == null && auth.ownerUuid != null) {
            user = CardBattleAccounts.findById(auth.ownerUuid);
        }
        if (user == null && auth.actorUuid != null) {
            user = CardBattleAccounts.findByMcUuid(auth.actorUuid);
        }
        if (user != null) {
            o.addProperty("accountId", user.id);
            o.addProperty("username", user.username);
            o.addProperty("role", user.role);
            o.addProperty("mcUuid", user.mcUuid);
            o.addProperty("mcName", user.mcName);
            o.addProperty("authSource", "account");
            JsonObject binding = new JsonObject();
            if (user.mcUuid != null) {
                binding.addProperty("bound", true);
                binding.addProperty("mcUuid", user.mcUuid);
                binding.addProperty("mcName", user.mcName);
            } else {
                binding.addProperty("bound", false);
            }
            o.add("binding", binding);
        } else {
            o.add("accountId", null);
            o.add("username", null);
            o.add("role", null);
            o.addProperty("mcUuid", auth.actorUuid);
            o.add("mcName", null);
            o.addProperty("authSource", "webae");
            JsonObject binding = new JsonObject();
            binding.addProperty("bound", false);
            o.add("binding", binding);
        }
        return o;
    }

    private static CardBattleAccounts.User requireAccount(IHTTPSession session, ResponseFactory factory) {
        String token = bearer(session);
        CardBattleAccounts.User user = CardBattleAccounts.resolveSession(token);
        if (user == null) {
            factory.setLastError(
                factory.jsonError(
                    Response.Status.BAD_REQUEST,
                    "account_required",
                    "此操作需要卡牌账号登录"));
            return null;
        }
        return user;
    }

    private static String bearer(IHTTPSession session) {
        String header = session.getHeaders()
            .get("authorization");
        if (header == null) header = session.getHeaders()
            .get("Authorization");
        if (header == null) return null;
        String prefix = "Bearer ";
        if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return header.substring(prefix.length())
                .trim();
        }
        return null;
    }

    private static JsonObject parseBody(String body) {
        if (body == null || body.length() == 0) return new JsonObject();
        try {
            return new JsonParser().parse(body)
                .getAsJsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key)
            .isJsonNull() ? o.get(key)
                .getAsString() : "";
    }

    public interface ResponseFactory {
        Response json(Response.Status status, JsonObject o);

        Response jsonError(Response.Status status, String code, String message);

        void setLastError(Response response);

        Response lastError();
    }

    public static final class SimpleFactory implements ResponseFactory {
        private Response last;

        @Override
        public Response json(Response.Status status, JsonObject o) {
            Response r = NanoHTTPD.newFixedLengthResponse(
                status,
                "application/json; charset=utf-8",
                o.toString());
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        }

        @Override
        public Response jsonError(Response.Status status, String code, String message) {
            JsonObject o = new JsonObject();
            o.addProperty("status", "error");
            o.addProperty("code", code);
            o.addProperty("message", message != null ? message : code);
            return json(status, o);
        }

        @Override
        public void setLastError(Response response) {
            this.last = response;
        }

        @Override
        public Response lastError() {
            return last;
        }
    }
}
