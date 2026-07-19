package com.imgood.textech.webae.alerts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Extracts QQ alert target IDs from official bot gateway Dispatch payloads.
 */
public final class QqIdProbeParser {

    private QqIdProbeParser() {}

    /**
     * @param eventType gateway {@code t} field (e.g. {@code C2C_MESSAGE_CREATE})
     * @param data gateway {@code d} object; may be null
     * @return discovery or {@code null} when the event has no usable target id
     */
    public static QqIdDiscovery fromDispatch(String eventType, JsonObject data, long nowMs) {
        String type = safe(eventType).trim();
        if (type.isEmpty() || data == null) {
            return null;
        }
        if ("C2C_MESSAGE_CREATE".equals(type) || "FRIEND_ADD".equals(type) || "C2C_MSG_RECEIVE".equals(type)
            || "C2C_MSG_REJECT".equals(type)) {
            String openid = firstNonEmpty(
                jsonString(data, "openid"),
                jsonString(data, "user_openid"),
                authorField(data, "user_openid"),
                authorField(data, "id"),
                jsonString(data, "author_id"));
            if (openid.isEmpty()) {
                return null;
            }
            return new QqIdDiscovery("c2c", openid, type, previewFrom(data), nowMs);
        }
        if ("GROUP_AT_MESSAGE_CREATE".equals(type) || "GROUP_ADD_ROBOT".equals(type)
            || "GROUP_MSG_RECEIVE".equals(type) || "GROUP_MSG_REJECT".equals(type)
            || "GROUP_DEL_ROBOT".equals(type)) {
            String groupOpenid = firstNonEmpty(jsonString(data, "group_openid"), jsonString(data, "group_id"));
            if (groupOpenid.isEmpty()) {
                return null;
            }
            return new QqIdDiscovery("group", groupOpenid, type, previewFrom(data), nowMs);
        }
        if ("AT_MESSAGE_CREATE".equals(type) || "DIRECT_MESSAGE_CREATE".equals(type)
            || "CHANNEL_CREATE".equals(type) || "MESSAGE_CREATE".equals(type)) {
            String channelId = firstNonEmpty(jsonString(data, "channel_id"), jsonString(data, "id"));
            // CHANNEL_CREATE uses id as the new channel; MESSAGE events use channel_id.
            if ("CHANNEL_CREATE".equals(type)) {
                channelId = firstNonEmpty(jsonString(data, "id"), jsonString(data, "channel_id"));
            }
            if (channelId.isEmpty()) {
                return null;
            }
            return new QqIdDiscovery("channel", channelId, type, previewFrom(data), nowMs);
        }
        return null;
    }

    private static String previewFrom(JsonObject data) {
        String content = firstNonEmpty(jsonString(data, "content"), jsonString(data, "username"));
        if (content.length() > 80) {
            return content.substring(0, 79) + "…";
        }
        return content;
    }

    private static String authorField(JsonObject data, String key) {
        try {
            if (data == null || !data.has("author") || data.get("author").isJsonNull()) {
                return "";
            }
            JsonElement author = data.get("author");
            if (!author.isJsonObject()) {
                return "";
            }
            return jsonString(author.getAsJsonObject(), key);
        } catch (Exception e) {
            return "";
        }
    }

    private static String jsonString(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
                return "";
            }
            return object.get(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
