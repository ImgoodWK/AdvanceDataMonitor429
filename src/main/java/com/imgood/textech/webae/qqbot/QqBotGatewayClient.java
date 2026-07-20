package com.imgood.textech.webae.qqbot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.bind.DatatypeConverter;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Persistent outbound QQ Open Platform Gateway connection. */
final class QqBotGatewayClient {

    static final String DEFAULT_API_BASE = "https://api.sgroup.qq.com";
    static final String DEFAULT_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final int INTENT_GUILDS = 1 << 0;
    private static final int INTENT_GROUP_C2C = 1 << 25;
    private static final int INTENT_PUBLIC_GUILD_MESSAGES = 1 << 30;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final Gson GSON = new GsonBuilder().create();

    interface Listener {

        void onPhase(String phase);

        void onReady();

        void onMessage(QqBotMessage message);

        void onError(String message);

        void onClosed(String reason);
    }

    private final String appId;
    private final String appSecret;
    private final String apiBase;
    private final String tokenUrl;
    private final boolean channelIntents;
    private final Listener listener;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicLong lastSeq = new AtomicLong(0L);
    private final AtomicInteger sendSeq = new AtomicInteger(1);
    private final AtomicReference<WebSocketClient> socketRef = new AtomicReference<WebSocketClient>();
    private final AtomicReference<Thread> heartbeatThread = new AtomicReference<Thread>();
    private final Object tokenLock = new Object();
    private volatile AccessToken accessToken;
    private volatile long heartbeatIntervalMs = 45000L;

    QqBotGatewayClient(QqBotConfigStore.RuntimeConfig runtime, Listener listener) {
        QqBotConfig cfg = runtime.settings;
        this.appId = safe(cfg.appId);
        this.appSecret = safe(runtime.appSecret);
        this.apiBase = trimTrailingSlash(first(cfg.apiBase, DEFAULT_API_BASE));
        this.tokenUrl = first(cfg.tokenUrl, DEFAULT_TOKEN_URL);
        this.channelIntents = cfg.allowChannels;
        this.listener = listener;
    }

    void start() {
        Thread worker = new Thread(new Runnable() {

            @Override
            public void run() {
                connect();
            }
        }, "WebAE-QQBot-Gateway");
        worker.setDaemon(true);
        worker.start();
    }

    void stop() {
        stopRequested.set(true);
        stopHeartbeat();
        WebSocketClient socket = socketRef.getAndSet(null);
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    boolean isOpen() {
        WebSocketClient socket = socketRef.get();
        return socket != null && socket.isOpen();
    }

    SendResult sendMessage(String targetType, String targetId, String replyMessageId, String eventId, String content) {
        String kind = safe(targetType).toLowerCase();
        String id = safe(targetId);
        if (id.isEmpty()) return SendResult.fail("QQ target id is empty");
        try {
            AccessToken token = token();
            String endpoint;
            JsonObject body = new JsonObject();
            body.addProperty("content", content == null ? "" : content);
            if ("channel".equals(kind)) {
                endpoint = apiBase + "/channels/" + path(id) + "/messages";
                if (!safe(replyMessageId).isEmpty()) body.addProperty("msg_id", replyMessageId);
            } else if ("c2c".equals(kind) || "group".equals(kind)) {
                endpoint = apiBase + ("c2c".equals(kind) ? "/v2/users/" : "/v2/groups/") + path(id) + "/messages";
                body.addProperty("msg_type", 0);
                body.addProperty("msg_seq", nextSendSeq());
                if (!safe(replyMessageId).isEmpty()) body.addProperty("msg_id", replyMessageId);
                else if (!safe(eventId).isEmpty()) body.addProperty("event_id", eventId);
            } else {
                return SendResult.fail("Unsupported QQ target type: " + kind);
            }
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "QQBot " + token.value);
            HttpResult response = http("POST", endpoint, GSON.toJson(body), headers);
            if (response.code == 401) accessToken = null;
            if (response.code < 200 || response.code >= 300) {
                return SendResult.fail("QQ send HTTP " + response.code + ": " + safePlatformError(response.body));
            }
            JsonObject responseJson = parseObjectOrNull(response.body);
            if (responseJson != null && responseJson.has("code")) {
                long code = jsonLong(responseJson, "code", 0L);
                if (code != 0L)
                    return SendResult.fail("QQ send code " + code + ": " + safePlatformError(response.body));
            }
            return SendResult.ok();
        } catch (Exception e) {
            return SendResult.fail(safeMessage(e));
        }
    }

    /** Upload a JPEG to QQ group/C2C media storage, then send the returned file_info as an image message. */
    SendResult sendImage(String targetType, String targetId, String content, byte[] jpeg) {
        String kind = safe(targetType).toLowerCase();
        String id = safe(targetId);
        if (!("group".equals(kind) || "c2c".equals(kind))) {
            return SendResult.fail("QQ image delivery supports group or c2c targets only");
        }
        if (id.isEmpty()) return SendResult.fail("QQ target id is empty");
        if (jpeg == null || jpeg.length == 0) return SendResult.fail("QQ image is empty");
        try {
            AccessToken token = token();
            String root = apiBase + ("c2c".equals(kind) ? "/v2/users/" : "/v2/groups/") + path(id);
            JsonObject upload = new JsonObject();
            upload.addProperty("file_type", 1);
            upload.addProperty("srv_send_msg", false);
            upload.addProperty("file_data", DatatypeConverter.printBase64Binary(jpeg));
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "QQBot " + token.value);
            HttpResult uploaded = http("POST", root + "/files", GSON.toJson(upload), headers);
            if (uploaded.code == 401) accessToken = null;
            if (uploaded.code < 200 || uploaded.code >= 300) {
                return SendResult
                    .fail("QQ media upload HTTP " + uploaded.code + ": " + safePlatformError(uploaded.body));
            }
            JsonObject uploadJson = parseObjectOrNull(uploaded.body);
            String fileInfo = jsonString(uploadJson, "file_info");
            if (fileInfo.isEmpty()) {
                return SendResult.fail("QQ media upload did not return file_info: " + safePlatformError(uploaded.body));
            }
            JsonObject media = new JsonObject();
            media.addProperty("file_info", fileInfo);
            JsonObject message = new JsonObject();
            message.addProperty("content", content == null ? "" : content);
            message.addProperty("msg_type", 7);
            message.addProperty("msg_seq", nextSendSeq());
            message.add("media", media);
            HttpResult sent = http("POST", root + "/messages", GSON.toJson(message), headers);
            if (sent.code == 401) accessToken = null;
            if (sent.code < 200 || sent.code >= 300) {
                return SendResult.fail("QQ image send HTTP " + sent.code + ": " + safePlatformError(sent.body));
            }
            JsonObject sentJson = parseObjectOrNull(sent.body);
            if (sentJson != null && sentJson.has("code") && jsonLong(sentJson, "code", 0L) != 0L) {
                return SendResult.fail("QQ image send rejected: " + safePlatformError(sent.body));
            }
            return SendResult.ok();
        } catch (Exception error) {
            return SendResult.fail(safeMessage(error));
        }
    }

    private void connect() {
        try {
            listener.onPhase("token");
            String token = token().value;
            if (stopRequested.get()) return;
            listener.onPhase("gateway");
            String gatewayUrl = fetchGatewayUrl(token);
            if (stopRequested.get()) return;
            listener.onPhase("connecting");
            openSocket(gatewayUrl, token);
        } catch (Exception e) {
            if (!stopRequested.get()) {
                listener.onError(safeMessage(e));
                listener.onClosed("error");
            }
        }
    }

    private void openSocket(String gatewayUrl, final String token) throws Exception {
        WebSocketClient socket = new WebSocketClient(new URI(gatewayUrl)) {

            @Override
            public void onOpen(ServerHandshake handshake) {
                listener.onPhase("connected");
            }

            @Override
            public void onMessage(String message) {
                handleGatewayPayload(message, token);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                stopHeartbeat();
                socketRef.compareAndSet(this, null);
                listener.onClosed(stopRequested.get() ? "stopped" : safe(reason).isEmpty() ? "code=" + code : reason);
            }

            @Override
            public void onError(Exception error) {
                if (!stopRequested.get()) listener.onError(safeMessage(error));
            }
        };
        socketRef.set(socket);
        socket.connectBlocking();
    }

    private void handleGatewayPayload(String raw, String token) {
        JsonObject payload = parseObjectOrNull(raw);
        if (payload == null) return;
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
            JsonObject hello = jsonObject(payload, "d");
            heartbeatIntervalMs = clamp(jsonLong(hello, "heartbeat_interval", 45000L), 5000L, 120000L);
            sendIdentify(token);
            return;
        }
        if (op == 0) {
            String eventType = jsonString(payload, "t");
            if ("READY".equals(eventType)) {
                listener.onPhase("ready");
                listener.onReady();
                startHeartbeat();
                return;
            }
            QqBotMessage message = QqBotMessage.fromDispatch(
                eventType,
                jsonObject(payload, "d"),
                jsonString(payload, "id"),
                System.currentTimeMillis());
            if (message != null) listener.onMessage(message);
            return;
        }
        if (op == 7) {
            listener.onError("QQ gateway requested reconnect");
            closeSocket();
        } else if (op == 9) {
            listener.onError("QQ gateway invalid session; verify AppID, ClientSecret, bot intents, and sandbox scope");
            closeSocket();
        }
    }

    private void sendIdentify(String token) {
        WebSocketClient socket = socketRef.get();
        if (socket == null || !socket.isOpen()) return;
        JsonObject data = new JsonObject();
        data.addProperty("token", "QQBot " + token);
        int intents = INTENT_GROUP_C2C;
        if (channelIntents) intents |= INTENT_GUILDS | INTENT_PUBLIC_GUILD_MESSAGES;
        data.addProperty("intents", intents);
        com.google.gson.JsonArray shard = new com.google.gson.JsonArray();
        shard.add(new com.google.gson.JsonPrimitive(0));
        shard.add(new com.google.gson.JsonPrimitive(1));
        data.add("shard", shard);
        JsonObject properties = new JsonObject();
        properties.addProperty("$os", "java");
        properties.addProperty("$browser", "TeXTech-WebAE-QQBot");
        properties.addProperty("$device", "TeXTech-WebAE-QQBot");
        data.add("properties", properties);
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 2);
        payload.add("d", data);
        socket.send(GSON.toJson(payload));
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
                    WebSocketClient socket = socketRef.get();
                    if (socket == null || !socket.isOpen()) return;
                    JsonObject payload = new JsonObject();
                    payload.addProperty("op", 1);
                    long seq = lastSeq.get();
                    if (seq > 0L) payload.addProperty("d", seq);
                    else payload.add("d", JsonNull.INSTANCE);
                    try {
                        socket.send(GSON.toJson(payload));
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        }, "WebAE-QQBot-Heartbeat");
        thread.setDaemon(true);
        heartbeatThread.set(thread);
        thread.start();
    }

    private void stopHeartbeat() {
        Thread thread = heartbeatThread.getAndSet(null);
        if (thread != null) thread.interrupt();
    }

    private void closeSocket() {
        WebSocketClient socket = socketRef.get();
        if (socket != null) socket.close();
    }

    private AccessToken token() throws Exception {
        AccessToken current = accessToken;
        long now = System.currentTimeMillis();
        if (current != null && current.expiresAtMs - 60000L > now) return current;
        synchronized (tokenLock) {
            current = accessToken;
            if (current != null && current.expiresAtMs - 60000L > now) return current;
            JsonObject body = new JsonObject();
            body.addProperty("appId", appId);
            body.addProperty("clientSecret", appSecret);
            HttpResult response = http("POST", tokenUrl, GSON.toJson(body), null);
            if (response.code < 200 || response.code >= 300) {
                throw new IllegalStateException(
                    "QQ token HTTP " + response.code + ": " + safePlatformError(response.body));
            }
            JsonObject json = parseObject(response.body);
            String value = jsonString(json, "access_token");
            if (value.isEmpty()) throw new IllegalStateException("QQ access_token missing");
            long expiresSeconds = Math.max(60L, jsonLong(json, "expires_in", 7200L));
            current = new AccessToken(value, now + expiresSeconds * 1000L);
            accessToken = current;
            return current;
        }
    }

    private String fetchGatewayUrl(String token) throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "QQBot " + token);
        HttpResult response = http("GET", apiBase + "/gateway", null, headers);
        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException(
                "QQ gateway HTTP " + response.code + ": " + safePlatformError(response.body));
        }
        String url = jsonString(parseObject(response.body), "url");
        if (url.isEmpty()) throw new IllegalStateException("QQ gateway URL missing");
        return url;
    }

    private static HttpResult http(String method, String endpoint, String body, Map<String, String> headers)
        throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "TeXTech-WebAE-QQBot/1.0");
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
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = readLimited(input);
        connection.disconnect();
        return new HttpResult(code, responseBody);
    }

    private static String readLimited(InputStream input) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        try {
            int read;
            while ((read = input.read(buffer)) >= 0 && total < MAX_RESPONSE_BYTES) {
                int accepted = Math.min(read, MAX_RESPONSE_BYTES - total);
                output.write(buffer, 0, accepted);
                total += accepted;
            }
        } finally {
            input.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private int nextSendSeq() {
        int value = sendSeq.getAndIncrement();
        if (value <= 0) {
            sendSeq.set(2);
            return 1;
        }
        return value;
    }

    private static String path(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20");
    }

    private static JsonObject parseObject(String raw) throws Exception {
        JsonObject value = parseObjectOrNull(raw);
        if (value == null) throw new IllegalStateException("QQ returned invalid JSON");
        return value;
    }

    private static JsonObject parseObjectOrNull(String raw) {
        try {
            JsonElement element = new JsonParser().parse(raw == null || raw.isEmpty() ? "{}" : raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject jsonObject(JsonObject object, String key) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonString(JsonObject object, String key) {
        try {
            return object == null || !object.has(key)
                || object.get(key)
                    .isJsonNull() ? ""
                        : object.get(key)
                            .getAsString()
                            .trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object == null || !object.has(key) ? fallback
                : object.get(key)
                    .getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long jsonLong(JsonObject object, String key, long fallback) {
        try {
            return object == null || !object.has(key) ? fallback
                : object.get(key)
                    .getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String safePlatformError(String raw) {
        String value = safe(raw).replace('\r', ' ')
            .replace('\n', ' ')
            .trim();
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static String safeMessage(Exception error) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.isEmpty() ? error == null ? "unknown error"
            : error.getClass()
                .getSimpleName()
            : value;
    }

    private static String trimTrailingSlash(String value) {
        String result = safe(value);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private static String first(String preferred, String fallback) {
        return safe(preferred).isEmpty() ? safe(fallback) : safe(preferred);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AccessToken {

        final String value;
        final long expiresAtMs;

        AccessToken(String value, long expiresAtMs) {
            this.value = value;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class HttpResult {

        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    static final class SendResult {

        final boolean success;
        final String error;

        private SendResult(boolean success, String error) {
            this.success = success;
            this.error = error == null ? "" : error;
        }

        static SendResult ok() {
            return new SendResult(true, "");
        }

        static SendResult fail(String error) {
            return new SendResult(false, error);
        }
    }
}
