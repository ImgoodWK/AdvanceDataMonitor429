package com.imgood.textech.utils;

import java.nio.charset.Charset;
import java.security.MessageDigest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parses and validates {@code textech-webae-display-binding} v1 JSON used by live web surfaces.
 */
public final class WebDisplayBindingCodec {

    public static final String FORMAT = "textech-webae-display-binding";
    public static final int VERSION = 1;
    public static final String MODE_DASHBOARD_LIVE = "dashboard_live";
    public static final String MODE_LIVE_URL = "live_url";
    public static final String MODE_DASHBOARD_SNAPSHOT = "dashboard_snapshot";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private WebDisplayBindingCodec() {}

    public static final class Binding {

        public final String mode;
        public final String displayId;
        public final String viewToken;
        public final String title;
        public final String webaeOrigin;
        public final String url;
        public final String embedPath;
        public final int viewportWidth;
        public final int viewportHeight;
        public final String bindingHash;

        private Binding(String mode, String displayId, String viewToken, String title, String webaeOrigin, String url,
            String embedPath, int viewportWidth, int viewportHeight, String bindingHash) {
            this.mode = mode;
            this.displayId = displayId;
            this.viewToken = viewToken;
            this.title = title;
            this.webaeOrigin = webaeOrigin;
            this.url = url;
            this.embedPath = embedPath;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.bindingHash = bindingHash;
        }
    }

    public static class BindingException extends Exception {

        public BindingException(String message) {
            super(message);
        }
    }

    public static boolean looksLikeBinding(String json) {
        if (json == null) return false;
        String trimmed = json.trim();
        return trimmed.contains("\"" + FORMAT + "\"") || trimmed.contains("'textech-webae-display-binding'");
    }

    public static Binding parse(String json) throws BindingException {
        if (json == null || json.trim()
            .isEmpty()) {
            throw new BindingException("empty");
        }
        JsonObject root;
        try {
            root = new JsonParser().parse(json)
                .getAsJsonObject();
        } catch (Exception e) {
            throw new BindingException("invalid_json");
        }
        if (root == null || !FORMAT.equals(asString(root, "format"))) {
            throw new BindingException("bad_format");
        }
        int version = root.has("version") ? root.get("version")
            .getAsInt() : 0;
        if (version != VERSION) throw new BindingException("bad_version");
        String mode = asString(root, "mode");
        if (!MODE_DASHBOARD_LIVE.equals(mode) && !MODE_LIVE_URL.equals(mode) && !MODE_DASHBOARD_SNAPSHOT.equals(mode)) {
            throw new BindingException("bad_mode");
        }
        String displayId = asString(root, "displayId");
        String viewToken = asString(root, "viewToken");
        String url = asString(root, "url");
        if (MODE_DASHBOARD_LIVE.equals(mode)) {
            if (displayId.isEmpty() || viewToken.isEmpty()) throw new BindingException("missing_display");
            if (!displayId.matches("[a-zA-Z0-9_-]{8,64}")) throw new BindingException("bad_display_id");
            if (!viewToken.matches("[a-f0-9]{16,128}")) throw new BindingException("bad_view_token");
        }
        if (MODE_LIVE_URL.equals(mode)) {
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw new BindingException("bad_url");
            }
            if (url.length() > 512) throw new BindingException("url_too_long");
        }
        String title = asString(root, "title");
        if (title.isEmpty()) title = "WebAE Dashboard";
        if (title.length() > 96) title = title.substring(0, 96);
        String webaeOrigin = asString(root, "webaeOrigin");
        if (webaeOrigin.length() > 256) webaeOrigin = webaeOrigin.substring(0, 256);
        String embedPath = asString(root, "embedPath");
        int viewportWidth = 960;
        int viewportHeight = 720;
        if (root.has("viewportHint") && root.get("viewportHint")
            .isJsonObject()) {
            JsonObject vp = root.getAsJsonObject("viewportHint");
            viewportWidth = clamp(asInt(vp, "width", 960), 64, 1600);
            viewportHeight = clamp(asInt(vp, "height", 720), 64, 1200);
        }
        String hash = sha256Hex(
            (mode + "|" + displayId + "|" + viewToken + "|" + url + "|" + webaeOrigin).getBytes(UTF8));
        return new Binding(
            mode,
            displayId,
            viewToken,
            title,
            webaeOrigin,
            url,
            embedPath,
            viewportWidth,
            viewportHeight,
            hash);
    }

    private static String asString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key)
            .isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key)
                .getAsString()
                .trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int asInt(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key)
            .isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key)
                .getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", Integer.valueOf(b & 0xff)));
            }
            return sb.toString();
        } catch (Exception e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }
}
