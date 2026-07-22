package com.imgood.textech.cardbattle;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;
import com.imgood.textech.cardbattle.data.CardCatalog;
import com.imgood.textech.cardbattle.pve.CardBattleSessions;
import com.imgood.textech.cardbattle.pve.CardBattleSessions.EquipDef;
import com.imgood.textech.cardbattle.pve.CardBattleSessions.PendingEntry;
import com.imgood.textech.cardbattle.pve.CardBattleSessions.RunState;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.auth.WebAuthToken;

import fi.iki.elonen.NanoHTTPD;

/**
 * In-process Card Battle HTTP server (same pattern as WebAE). No Node.js required.
 */
public final class CardBattleHttpServer extends NanoHTTPD {

    private static final Gson GSON = new Gson();
    private final String bindAddress;

    public CardBattleHttpServer() {
        super(Config.cardBattleBindAddress, Config.cardBattlePort);
        this.bindAddress = Config.cardBattleBindAddress;
        CardCatalog.ensureLoaded();
    }

    public void startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            AdvanceDataMonitor.LOG
                .info("[CardBattle] HTTP server started on {}:{}", bindAddress, Config.cardBattlePort);
        } catch (IOException e) {
            AdvanceDataMonitor.LOG
                .error("[CardBattle] Failed to start on {}:{}", bindAddress, Config.cardBattlePort, e);
        }
    }

    public void stopServer() {
        stop();
        AdvanceDataMonitor.LOG.info("[CardBattle] HTTP server stopped.");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/api/")) {
            return handleApi(session);
        }
        return serveStatic(uri);
    }

    private Response handleApi(IHTTPSession session) {
        try {
            String uri = session.getUri();
            Method method = session.getMethod();
            if ("/api/health".equals(uri) && method == Method.GET) {
                JsonObject o = new JsonObject();
                o.addProperty("status", "ok");
                o.addProperty("service", "textech-cardbattle");
                o.addProperty("embedded", true);
                o.addProperty("dataRoot", TeXTechDataDir.cardBattleRoot()
                    .getAbsolutePath());
                o.addProperty("cardCount", CardCatalog.all()
                    .size());
                return json(Response.Status.OK, o);
            }
            if ("/api/meta".equals(uri) && method == Method.GET) {
                JsonObject o = new JsonObject();
                JsonArray themes = new JsonArray();
                for (String t : CardBattleSessions.ALL_THEMES) themes.add(new com.google.gson.JsonPrimitive(t));
                o.add("themes", themes);
                JsonArray voltages = new JsonArray();
                for (String v : CardBattleTypes.VOLTAGE_ORDER) voltages.add(new com.google.gson.JsonPrimitive(v));
                o.add("voltages", voltages);
                JsonObject slots = new JsonObject();
                for (String v : CardBattleTypes.VOLTAGE_ORDER) {
                    slots.addProperty(v, CardBattleTypes.themeSlots(v));
                }
                o.add("themeSlotsByVoltage", slots);
                JsonArray eq = new JsonArray();
                for (EquipDef e : CardBattleSessions.EQUIPMENT) {
                    JsonObject x = new JsonObject();
                    x.addProperty("id", e.id);
                    x.addProperty("nameZh", e.nameZh);
                    x.addProperty("attack", e.attack);
                    x.addProperty("health", e.health);
                    x.addProperty("armor", e.armor);
                    eq.add(x);
                }
                o.add("equipment", eq);
                o.addProperty("cardCount", CardCatalog.all()
                    .size());
                return json(Response.Status.OK, o);
            }
            if ("/api/cards".equals(uri) && method == Method.GET) {
                JsonObject o = new JsonObject();
                o.add("cards", GSON.toJsonTree(CardCatalog.all()));
                return json(Response.Status.OK, o);
            }

            WebAuthSession auth = requireAuth(session);
            if (auth == null) {
                return jsonError(Response.Status.UNAUTHORIZED, "unauthorized",
                    "Use Authorization: Bearer <WebAE token> (or [cardBattle] devToken)");
            }

            if ("/api/me".equals(uri) && method == Method.GET) {
                JsonObject o = new JsonObject();
                o.addProperty("ownerUuid", auth.ownerUuid);
                o.addProperty("actorUuid", auth.actorUuid);
                o.addProperty("actorName", auth.actorName);
                o.addProperty("type", auth.type);
                return json(Response.Status.OK, o);
            }

            String body = readBody(session);
            JsonObject bodyJson = body != null && body.length() > 0
                ? new JsonParser().parse(body)
                    .getAsJsonObject()
                : new JsonObject();

            if ("/api/run".equals(uri) && method == Method.POST) {
                java.util.List<String> themes = new java.util.ArrayList<String>();
                if (bodyJson.has("themes")) {
                    JsonArray arr = bodyJson.getAsJsonArray("themes");
                    for (int i = 0; i < arr.size(); i++) themes.add(arr.get(i)
                        .getAsString());
                }
                String voltage = bodyJson.has("voltage") ? bodyJson.get("voltage")
                    .getAsString() : "LV";
                java.util.List<String> eqIds = new java.util.ArrayList<String>();
                if (bodyJson.has("equipmentIds")) {
                    JsonArray arr = bodyJson.getAsJsonArray("equipmentIds");
                    for (int i = 0; i < arr.size(); i++) eqIds.add(arr.get(i)
                        .getAsString());
                }
                Integer seed = bodyJson.has("seed") ? Integer.valueOf(bodyJson.get("seed")
                    .getAsInt()) : null;
                RunState run = CardBattleSessions
                    .startRun(auth.ownerUuid, auth.actorName, themes, voltage, eqIds, seed);
                JsonObject o = new JsonObject();
                o.add("run", GSON.toJsonTree(run));
                return json(Response.Status.OK, o);
            }

            if (uri.startsWith("/api/run/") && method == Method.GET) {
                String runId = uri.substring("/api/run/".length());
                if (runId.contains("/")) return jsonError(Response.Status.NOT_FOUND, "not_found", "Not found");
                RunState run = CardBattleSessions.getRun(runId);
                if (run == null || !auth.ownerUuid.equals(run.ownerUuid)) {
                    return jsonError(Response.Status.NOT_FOUND, "not_found", "Not found");
                }
                JsonObject o = new JsonObject();
                o.add("run", GSON.toJsonTree(run));
                return json(Response.Status.OK, o);
            }

            if (uri.startsWith("/api/run/") && uri.endsWith("/stage") && method == Method.POST) {
                String runId = uri.substring("/api/run/".length(), uri.length() - "/stage".length());
                String stageId = bodyJson.has("stageId") ? bodyJson.get("stageId")
                    .getAsString() : null;
                return json(Response.Status.OK,
                    CardBattleSessions.beginStage(runId, auth.ownerUuid, auth.actorName, stageId));
            }

            if (uri.startsWith("/api/run/") && uri.endsWith("/claim-reward") && method == Method.POST) {
                String runId = uri.substring("/api/run/".length(), uri.length() - "/claim-reward".length());
                String choiceId = bodyJson.has("choiceId") ? bodyJson.get("choiceId")
                    .getAsString() : "";
                return json(Response.Status.OK, CardBattleSessions.claimReward(runId, auth.ownerUuid, choiceId));
            }

            if (uri.startsWith("/api/match/") && method == Method.GET) {
                String matchId = uri.substring("/api/match/".length());
                if (matchId.contains("/")) return jsonError(Response.Status.NOT_FOUND, "not_found", "Not found");
                JsonObject o = new JsonObject();
                o.add("match", GSON.toJsonTree(CardBattleSessions.getMatch(matchId, auth.ownerUuid)));
                return json(Response.Status.OK, o);
            }

            if (uri.startsWith("/api/match/") && uri.endsWith("/action") && method == Method.POST) {
                String matchId = uri.substring("/api/match/".length(), uri.length() - "/action".length());
                JsonObject action = bodyJson.has("action") ? bodyJson.getAsJsonObject("action") : new JsonObject();
                String runId = bodyJson.has("runId") ? bodyJson.get("runId")
                    .getAsString() : null;
                return json(Response.Status.OK, CardBattleSessions.act(matchId, auth.ownerUuid, action, runId));
            }

            if ("/api/rewards/pending".equals(uri) && method == Method.GET) {
                JsonObject o = new JsonObject();
                o.add("entries", GSON.toJsonTree(CardBattleSessions.listPending(auth.ownerUuid)));
                return json(Response.Status.OK, o);
            }

            if (uri.startsWith("/api/rewards/") && uri.endsWith("/mark-claimed") && method == Method.POST) {
                String id = uri.substring("/api/rewards/".length(), uri.length() - "/mark-claimed".length());
                PendingEntry e = CardBattleSessions.markClaimed(auth.ownerUuid, id);
                if (e == null) return jsonError(Response.Status.NOT_FOUND, "not_found", "Not found");
                JsonObject o = new JsonObject();
                o.add("entry", GSON.toJsonTree(e));
                o.addProperty("note", "Stub only — Minecraft item grant is not implemented in V1");
                return json(Response.Status.OK, o);
            }

            return jsonError(Response.Status.NOT_FOUND, "not_found", "Unknown API");
        } catch (IllegalArgumentException e) {
            return jsonError(Response.Status.BAD_REQUEST, "bad_request", e.getMessage());
        } catch (IllegalStateException e) {
            return jsonError(Response.Status.BAD_REQUEST, "bad_request", e.getMessage());
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[CardBattle] API error", t);
            return jsonError(Response.Status.INTERNAL_ERROR, "error", t.getMessage());
        }
    }

    private WebAuthSession requireAuth(IHTTPSession session) {
        String header = session.getHeaders()
            .get("authorization");
        if (header == null) header = session.getHeaders()
            .get("Authorization");
        String token = null;
        if (header != null) {
            String prefix = "Bearer ";
            if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
                token = header.substring(prefix.length())
                    .trim();
            }
        }
        if (token == null || token.isEmpty()) {
            Map<String, String> parms = session.getParms();
            if (parms != null) token = parms.get("token");
        }
        if (token == null || token.isEmpty()) return null;
        String dev = Config.cardBattleDevToken;
        if (dev != null && dev.trim()
            .length() > 0 && token.equals(dev.trim())) {
            return new WebAuthSession(
                token,
                WebAuthSession.TYPE_OWNER,
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000001",
                "DevPlayer");
        }
        return WebAuthToken.validateToken(token);
    }

    private String readBody(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<String, String>();
            session.parseBody(files);
            String post = files.get("postData");
            if (post != null) return post;
            // Some NanoHTTPD builds put body under empty key
            return files.get("content") != null ? files.get("content") : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private Response serveStatic(String uri) {
        if ("/".equals(uri) || uri.isEmpty()) uri = "/index.html";
        String resourcePath = "/assets/textech/cardbattle" + uri;
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null && !uri.contains(".")) {
            stream = getClass().getResourceAsStream("/assets/textech/cardbattle/index.html");
            if (stream != null) {
                return newChunkedResponse(Response.Status.OK, "text/html", stream);
            }
        }
        if (stream == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
        return newChunkedResponse(Response.Status.OK, mime(uri), stream);
    }

    private static String mime(String uri) {
        if (uri.endsWith(".html")) return "text/html";
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".json")) return "application/json";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static Response json(Response.Status status, JsonObject o) {
        Response r = newFixedLengthResponse(status, "application/json; charset=utf-8", GSON.toJson(o));
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }

    private static Response jsonError(Response.Status status, String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "error");
        o.addProperty("code", code);
        o.addProperty("message", message != null ? message : code);
        return json(status, o);
    }
}
