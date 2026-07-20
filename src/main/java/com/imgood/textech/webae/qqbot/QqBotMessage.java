package com.imgood.textech.webae.qqbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Normalized QQ Gateway inbound message. */
public final class QqBotMessage {

    public String eventType = "";
    public String targetType = "";
    public String targetId = "";
    public String senderId = "";
    public String senderName = "";
    public String messageId = "";
    public String eventId = "";
    public String content = "";
    public long timestampMs;

    public static QqBotMessage fromDispatch(String eventType, JsonObject data, String eventId, long nowMs) {
        String type = safe(eventType).trim();
        if (data == null) return null;
        QqBotMessage message = new QqBotMessage();
        message.eventType = type;
        message.eventId = safe(eventId).trim();
        message.timestampMs = nowMs;
        message.messageId = first(jsonString(data, "id"), jsonString(data, "message_id"));
        message.content = cleanContent(jsonString(data, "content"));
        JsonObject author = child(data, "author");
        message.senderName = first(
            jsonString(author, "username"),
            jsonString(author, "nick"),
            jsonString(data, "author_name"));

        if ("GROUP_AT_MESSAGE_CREATE".equals(type)) {
            message.targetType = "group";
            message.targetId = first(jsonString(data, "group_openid"), jsonString(data, "group_id"));
            message.senderId = first(
                jsonString(author, "member_openid"),
                jsonString(author, "user_openid"),
                jsonString(author, "id"),
                jsonString(data, "openid"));
        } else if ("C2C_MESSAGE_CREATE".equals(type)) {
            message.targetType = "c2c";
            message.senderId = first(
                jsonString(data, "openid"),
                jsonString(data, "user_openid"),
                jsonString(author, "user_openid"),
                jsonString(author, "id"));
            message.targetId = message.senderId;
        } else if ("AT_MESSAGE_CREATE".equals(type) || "MESSAGE_CREATE".equals(type)
            || "DIRECT_MESSAGE_CREATE".equals(type)) {
                message.targetType = "channel";
                message.targetId = first(jsonString(data, "channel_id"), jsonString(data, "guild_id"));
                message.senderId = first(jsonString(author, "id"), jsonString(author, "user_openid"));
            } else {
                return null;
            }
        if (message.targetId.isEmpty() || message.senderId.isEmpty() || message.content.isEmpty()) return null;
        return message;
    }

    public String sessionKey() {
        return targetType + "|" + targetId + "|" + senderId;
    }

    public String dedupeKey() {
        return messageId.isEmpty() ? eventType + "|" + eventId + "|" + senderId + "|" + timestampMs : messageId;
    }

    static String cleanContent(String value) {
        String result = safe(value).replace('\r', ' ')
            .replace('\n', ' ')
            .trim();
        result = result.replaceAll("<@!?[A-Za-z0-9_-]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return result;
    }

    private static JsonObject child(JsonObject object, String key) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonString(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull())
                return "";
            return object.get(key)
                .getAsString()
                .trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String first(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim()
                    .isEmpty()) return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
