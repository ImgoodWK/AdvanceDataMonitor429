package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.chat.ChatMessage;
import com.imgood.textech.webae.chat.ChatMessageStore;
import com.imgood.textech.webae.dto.ChatMessageDto;

import cpw.mods.fml.common.FMLCommonHandler;
import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the WebAE chat endpoints.
 */
public class ChatHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params,
        String body, WebAuthSession auth) {
        if ("/api/chat/history".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET /api/chat/history\"}");
            }
            return handleHistory(params);
        }
        if ("/api/chat/since".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET /api/chat/since\"}");
            }
            return handleSince(params);
        }
        if ("/api/chat/send".equals(uri)) {
            if (method != NanoHTTPD.Method.POST) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use POST /api/chat/send\"}");
            }
            return handleSend(body, auth);
        }
        return json(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown chat endpoint: " + uri + "\"}");
    }

    private static NanoHTTPD.Response handleHistory(Map<String, String> params) {
        int limit = parseIntOrDefault(params.get("limit"), DEFAULT_LIMIT, 1, MAX_LIMIT);
        long since = parseLongOrDefault(params.get("since"), 0L);
        List<ChatMessage> msgs;
        if (since > 0) {
            msgs = ChatMessageStore.instance()
                .getSince(since);
            if (msgs.size() > limit) {
                msgs = msgs.subList(Math.max(0, msgs.size() - limit), msgs.size());
            }
        } else {
            msgs = ChatMessageStore.instance()
                .getRecent(limit);
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"messages\":" + GSON.toJson(toDtos(msgs))
                + ",\"latestTimestamp\":"
                + ChatMessageStore.instance()
                    .latestTimestamp()
                + "}");
    }

    private static NanoHTTPD.Response handleSince(Map<String, String> params) {
        String idParam = params.get("id");
        List<ChatMessage> msgs;
        if (idParam != null && !idParam.isEmpty()) {
            long afterId = parseLongOrDefault(idParam, 0L);
            msgs = ChatMessageStore.instance()
                .getAfterId(afterId);
        } else {
            long since = parseLongOrDefault(params.get("since"), 0L);
            msgs = ChatMessageStore.instance()
                .getSince(since);
        }
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"messages\":" + GSON.toJson(toDtos(msgs))
                + ",\"latestId\":"
                + ChatMessageStore.instance()
                    .latestId()
                + ",\"latestTimestamp\":"
                + ChatMessageStore.instance()
                    .latestTimestamp()
                + "}");
    }

    private static NanoHTTPD.Response handleSend(String body, WebAuthSession auth) {
        if (auth == null || auth.actorUuid == null || auth.actorUuid.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                "{\"success\":false,\"message\":\"No authenticated session\"}");
        }
        String content = extractContent(body);
        if (content == null || content.isEmpty()) {
            return json(NanoHTTPD.Response.Status.BAD_REQUEST, "{\"success\":false,\"message\":\"Missing 'content'\"}");
        }
        if (content.length() > 256) {
            content = content.substring(0, 256);
        }

        String displayName = auth.actorName;
        if (displayName == null || displayName.isEmpty()) {
            displayName = auth.actorUuid;
        }

        ChatMessage stored = ChatMessageStore.instance()
            .append(auth.actorUuid, displayName, content, System.currentTimeMillis(), ChatMessage.SOURCE_WEB);

        try {
            String broadcast;
            if (auth.isGuest()) {
                broadcast = "[Web\u00b7\u8bbf\u5ba2] " + displayName + ": " + content;
            } else {
                broadcast = "[Web] " + displayName + ": " + content;
            }
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null && server.getConfigurationManager() != null) {
                server.getConfigurationManager()
                    .sendChatMsg(new ChatComponentText(broadcast));
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to broadcast web chat message to game", t);
        }

        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"message\":" + GSON.toJson(toDto(stored)) + "}");
    }

    private static List<ChatMessageDto> toDtos(List<ChatMessage> msgs) {
        List<ChatMessageDto> out = new ArrayList<ChatMessageDto>();
        for (ChatMessage m : msgs) {
            out.add(toDto(m));
        }
        return out;
    }

    private static ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(m.id, m.senderUuid, m.senderName, m.content, m.timestamp, m.source);
    }

    private static String extractContent(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            if (obj.has("content") && !obj.get("content")
                .isJsonNull()) {
                return obj.get("content")
                    .getAsString();
            }
        } catch (Exception ignored) {}
        String trimmed = body.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseIntOrDefault(String s, int def, int min, int max) {
        if (s == null || s.isEmpty()) return def;
        try {
            int v = Integer.parseInt(s.trim());
            if (v < min) return min;
            if (v > max) return max;
            return v;
        } catch (NumberFormatException e) {
            return def;
        }
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
