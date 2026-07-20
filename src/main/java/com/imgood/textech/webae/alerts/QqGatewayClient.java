package com.imgood.textech.webae.alerts;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Short-lived QQ Open Platform gateway client used only for ID probing.
 * Must not run on the Minecraft tick thread.
 */
final class QqGatewayClient {

    static final String DEFAULT_API_BASE = "https://api.sgroup.qq.com";
    static final String DEFAULT_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    /** GROUP_AND_C2C_EVENT | PUBLIC_GUILD_MESSAGES | GUILDS */
    static final int INTENTS_FULL = (1 << 25) | (1 << 30) | (1 << 0);
    /** GROUP_AND_C2C_EVENT only — fallback when broader intents are rejected. */
    static final int INTENTS_GROUP_C2C = 1 << 25;

    private static final Gson GSON = new GsonBuilder().create();
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    interface Listener {

        void onPhase(String phase);

        void onDiscovery(QqIdDiscovery discovery);

        void onError(String message);

        void onClosed(String reason);
    }

    private final String appId;
    private final String appSecret;
    private final String apiBase;
    private final String tokenUrl;
    private final Listener listener;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicInteger intents = new AtomicInteger(INTENTS_FULL);
    private final AtomicBoolean retriedNarrowIntents = new AtomicBoolean(false);
    private final AtomicLong lastSeq = new AtomicLong(0L);
    private final AtomicReference<WebSocketClient> socketRef = new AtomicReference<WebSocketClient>();
    private final AtomicReference<Thread> heartbeatThread = new AtomicReference<Thread>();
    private volatile long heartbeatIntervalMs = 45000L;

    QqGatewayClient(String appId, String appSecret, String apiBase, String tokenUrl, Listener listener) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiBase = trimTrailingSlash(firstNonEmpty(apiBase, DEFAULT_API_BASE));
        this.tokenUrl = firstNonEmpty(tokenUrl, DEFAULT_TOKEN_URL);
        this.listener = listener;
    }

    void start() {
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                connectLoop();
            }
        }, "WebAE-QQ-IdProbe");
        worker.setDaemon(true);
        worker.start();
    }

    void stop() {
        stopRequested.set(true);
        stopHeartbeat();
        WebSocketClient client = socketRef.getAndSet(null);
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
    }

    private void connectLoop() {
        try {
            listener.onPhase("token");
            String accessToken = fetchAccessToken();
            if (stopRequested.get()) {
                return;
            }
            listener.onPhase("gateway");
            String gatewayUrl = fetchGatewayUrl(accessToken);
            if (stopRequested.get()) {
                return;
            }
            listener.onPhase("connecting");
            openSocket(gatewayUrl, accessToken);
        } catch (Exception e) {
            if (!stopRequested.get()) {
                listener.onError(safeMessage(e));
                listener.onClosed("error");
            }
        }
    }

    private void openSocket(final String gatewayUrl, final String accessToken) throws Exception {
        URI uri = new URI(gatewayUrl);
        WebSocketClient client = new WebSocketClient(uri) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                listener.onPhase("connected");
            }

            @Override
            public void onMessage(String message) {
                handleGatewayMessage(message, accessToken);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                stopHeartbeat();
                if (stopRequested.get()) {
                    listener.onClosed("stopped");
                    return;
                }
                String detail = reason == null || reason.isEmpty() ? ("code=" + code) : reason;
                if (!retriedNarrowIntents.get() && intents.get() != INTENTS_GROUP_C2C
                    && (code == 4004 || code == 4014 || code == 4013 || looksLikeIntentError(detail))) {
                    retriedNarrowIntents.set(true);
                    intents.set(INTENTS_GROUP_C2C);
                    listener.onPhase("retry_intents");
                    try {
                        openSocket(gatewayUrl, accessToken);
                        return;
                    } catch (Exception e) {
                        listener.onError(safeMessage(e));
                        listener.onClosed("error");
                        return;
                    }
                }
                listener.onError("gateway closed: " + detail);
                listener.onClosed("closed");
            }

            @Override
            public void onError(Exception ex) {
                if (!stopRequested.get()) {
                    listener.onError(safeMessage(ex));
                }
            }
        };
        socketRef.set(client);
        client.connectBlocking();
    }

    private void handleGatewayMessage(String message, String accessToken) {
        if (stopRequested.get() || message == null || message.isEmpty()) {
            return;
        }
        JsonObject payload;
        try {
            JsonElement element = new JsonParser().parse(message);
            if (element == null || !element.isJsonObject()) {
                return;
            }
            payload = element.getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        int op = jsonInt(payload, "op", -1);
        if (payload.has("s") && !payload.get("s")
            .isJsonNull()) {
            try {
                lastSeq.set(
                    payload.get("s")
                        .getAsLong());
            } catch (Exception ignored) {}
        }
        if (op == 10) {
            // Hello
            JsonObject d = jsonObject(payload, "d");
            long interval = d == null ? 45000L : jsonLong(d, "heartbeat_interval", 45000L);
            heartbeatIntervalMs = Math.max(5000L, Math.min(interval, 120000L));
            sendIdentify(accessToken);
            return;
        }
        if (op == 0) {
            String eventType = jsonString(payload, "t");
            JsonObject d = jsonObject(payload, "d");
            if ("READY".equals(eventType)) {
                listener.onPhase("ready");
                startHeartbeat();
                return;
            }
            QqIdDiscovery discovery = QqIdProbeParser.fromDispatch(eventType, d, System.currentTimeMillis());
            if (discovery != null) {
                listener.onDiscovery(discovery);
            }
            return;
        }
        if (op == 9) {
            // Invalid session
            listener.onError("invalid session (check AppID/Secret and event intents on QQ Open Platform)");
            stop();
            listener.onClosed("invalid_session");
            return;
        }
        if (op == 7) {
            listener.onError("gateway requested reconnect");
            stop();
            listener.onClosed("reconnect");
        }
    }

    private void sendIdentify(String accessToken) {
        WebSocketClient client = socketRef.get();
        if (client == null || !client.isOpen()) {
            return;
        }
        JsonObject d = new JsonObject();
        d.addProperty("token", "QQBot " + accessToken);
        d.addProperty("intents", intents.get());
        com.google.gson.JsonArray shard = new com.google.gson.JsonArray();
        shard.add(new com.google.gson.JsonPrimitive(0));
        shard.add(new com.google.gson.JsonPrimitive(1));
        d.add("shard", shard);
        JsonObject properties = new JsonObject();
        properties.addProperty("$os", "java");
        properties.addProperty("$browser", "TeXTech-WebAE");
        properties.addProperty("$device", "TeXTech-WebAE");
        d.add("properties", properties);
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 2);
        payload.add("d", d);
        client.send(GSON.toJson(payload));
        listener.onPhase("identify");
    }

    private void startHeartbeat() {
        stopHeartbeat();
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (!stopRequested.get() && !Thread.currentThread()
                    .isInterrupted()) {
                    try {
                        Thread.sleep(heartbeatIntervalMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                            .interrupt();
                        return;
                    }
                    WebSocketClient client = socketRef.get();
                    if (client == null || !client.isOpen()) {
                        return;
                    }
                    JsonObject payload = new JsonObject();
                    payload.addProperty("op", 1);
                    long seq = lastSeq.get();
                    if (seq > 0L) {
                        payload.addProperty("d", seq);
                    } else {
                        payload.add("d", com.google.gson.JsonNull.INSTANCE);
                    }
                    try {
                        client.send(GSON.toJson(payload));
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        }, "WebAE-QQ-IdProbe-HB");
        thread.setDaemon(true);
        heartbeatThread.set(thread);
        thread.start();
    }

    private void stopHeartbeat() {
        Thread thread = heartbeatThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private String fetchAccessToken() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("appId", appId);
        body.addProperty("clientSecret", appSecret);
        HttpResult result = http("POST", tokenUrl, GSON.toJson(body), null);
        if (result.code < 200 || result.code >= 300) {
            throw new IllegalStateException("QQ token HTTP " + result.code);
        }
        JsonObject json = parseObject(result.body);
        String token = jsonString(json, "access_token");
        if (token.isEmpty()) {
            throw new IllegalStateException("QQ access_token missing");
        }
        return token;
    }

    private String fetchGatewayUrl(String accessToken) throws Exception {
        Map<String, String> headers = new java.util.HashMap<String, String>();
        headers.put("Authorization", "QQBot " + accessToken);
        HttpResult result = http("GET", apiBase + "/gateway", null, headers);
        if (result.code < 200 || result.code >= 300) {
            throw new IllegalStateException("QQ gateway HTTP " + result.code);
        }
        JsonObject json = parseObject(result.body);
        String url = jsonString(json, "url");
        if (url.isEmpty()) {
            throw new IllegalStateException("QQ gateway url missing");
        }
        return url;
    }

    private static HttpResult http(String method, String endpoint, String body, Map<String, String> headers)
        throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "TeXTech-WebAE-QQ-IdProbe/1.0");
        connection.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream out = connection.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = readLimited(stream);
        connection.disconnect();
        return new HttpResult(code, responseBody);
    }

    private static String readLimited(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        try {
            int read;
            while ((read = input.read(buffer)) >= 0 && total < MAX_RESPONSE_BYTES) {
                int accepted = Math.min(read, MAX_RESPONSE_BYTES - total);
                out.write(buffer, 0, accepted);
                total += accepted;
            }
        } finally {
            input.close();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean looksLikeIntentError(String detail) {
        String lower = safe(detail).toLowerCase();
        return lower.contains("intent") || lower.contains("identify")
            || lower.contains("4014")
            || lower.contains("4013");
    }

    private static JsonObject parseObject(String json) throws Exception {
        JsonElement element = new JsonParser().parse(json == null ? "{}" : json);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        throw new IllegalStateException("invalid JSON response");
    }

    private static JsonObject jsonObject(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull()) {
                return null;
            }
            JsonElement element = object.get(key);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonString(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull()) {
                return "";
            }
            return object.get(key)
                .getAsString()
                .trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            if (object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull()) {
                return fallback;
            }
            return object.get(key)
                .getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long jsonLong(JsonObject object, String key, long fallback) {
        try {
            if (object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull()) {
                return fallback;
            }
            return object.get(key)
                .getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String trimTrailingSlash(String value) {
        String out = safe(value).trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.trim()
            .isEmpty() ? first.trim() : safe(second).trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(Exception error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isEmpty() ? (error == null ? "unknown error"
            : error.getClass()
                .getSimpleName())
            : message;
    }

    private static final class HttpResult {

        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
