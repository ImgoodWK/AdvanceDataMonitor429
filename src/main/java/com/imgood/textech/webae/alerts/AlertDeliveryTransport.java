package com.imgood.textech.webae.alerts;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Blocking HTTP/SMTP transports used only by the bounded alert delivery workers.
 * No method in this class may be called from the Minecraft server tick thread.
 */
final class AlertDeliveryTransport {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Charset ASCII = Charset.forName("ISO-8859-1");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final String USER_AGENT = "TeXTech-WebAE-Alerts/2.0";
    private static final String QQ_API_BASE = "https://api.sgroup.qq.com";
    private static final String QQ_TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";
    private static final String WECHAT_API_BASE = "https://api.weixin.qq.com";
    private static final String WECOM_API_BASE = "https://qyapi.weixin.qq.com";

    private final ConcurrentHashMap<String, AccessToken> accessTokens = new ConcurrentHashMap<String, AccessToken>();

    void sendTarget(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        String type = safe(target.type).toLowerCase();
        if ("qq_official".equals(type)) {
            sendQqOfficial(target, ownerUuid, alert, cfg);
        } else if ("wechat_official".equals(type)) {
            sendWechatOfficial(target, ownerUuid, alert, cfg);
        } else if ("email".equals(type)) {
            sendEmail(target, ownerUuid, alert, cfg);
        } else if ("wecom_bot".equals(type)) {
            sendWecomBot(target, ownerUuid, alert, cfg);
        } else if ("wecom_app".equals(type)) {
            sendWecomApp(target, ownerUuid, alert, cfg);
        } else {
            throw DeliveryException.permanent("unsupported target type");
        }
    }

    void postLegacyWebhook(String endpoint, String body, WebAlertsConfig cfg) throws DeliveryException {
        HttpResult result = request("POST", endpoint, body, null, cfg);
        requireHttpSuccess(result);
    }

    private void sendQqOfficial(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        String tokenKey = "qq|" + target.appId + "|" + secretFingerprint(target.appSecret);
        String token = token(tokenKey, new TokenLoader() {

            @Override
            public AccessToken load() throws DeliveryException {
                String endpoint = firstNonEmpty(target.tokenUrl, QQ_TOKEN_URL);
                JsonObject request = new JsonObject();
                request.addProperty("appId", target.appId);
                request.addProperty("clientSecret", target.appSecret);
                HttpResult result = AlertDeliveryTransport.this.request("POST", endpoint, GSON.toJson(request), null,
                    cfg);
                requireHttpSuccess(result);
                JsonObject json = parseObject(result.body, "QQ access token");
                String value = jsonString(json, "access_token");
                if (value.isEmpty()) {
                    throw DeliveryException.permanent("QQ access token missing");
                }
                return new AccessToken(value, expiresAt(jsonLong(json, "expires_in", 7200L)));
            }
        }).value;

        String base = trimTrailingSlash(firstNonEmpty(target.baseUrl, QQ_API_BASE));
        String kind = safe(target.targetType).toLowerCase();
        String endpoint;
        JsonObject body = new JsonObject();
        body.addProperty("content", truncate(formatMessage(ownerUuid, alert), 1800));
        if ("channel".equals(kind)) {
            endpoint = base + "/channels/" + pathSegment(target.targetId) + "/messages";
        } else {
            endpoint = base + ("c2c".equals(kind) ? "/v2/users/" : "/v2/groups/")
                + pathSegment(target.targetId)
                + "/messages";
            body.addProperty("msg_type", 0);
            body.addProperty("msg_seq", (int) (System.currentTimeMillis() & 0x7FFFFFFF));
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "QQBot " + token);
        HttpResult result = request("POST", endpoint, GSON.toJson(body), headers, cfg);
        if (result.code == 401) {
            accessTokens.remove(tokenKey);
            throw DeliveryException.retryable("QQ authorization expired", 0L);
        }
        requireHttpSuccess(result);
        requirePlatformSuccess(result.body, "QQ", "code");
    }

    private void sendWechatOfficial(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        final String tokenKey = "wechat|" + target.appId + "|" + secretFingerprint(target.appSecret);
        String token = token(tokenKey, new TokenLoader() {

            @Override
            public AccessToken load() throws DeliveryException {
                String base = trimTrailingSlash(firstNonEmpty(target.baseUrl, WECHAT_API_BASE));
                String tokenBase = firstNonEmpty(target.tokenUrl, base + "/cgi-bin/token");
                String endpoint = tokenBase + (tokenBase.contains("?") ? "&" : "?")
                    + "grant_type=client_credential&appid="
                    + query(target.appId)
                    + "&secret="
                    + query(target.appSecret);
                HttpResult result = AlertDeliveryTransport.this.request("GET", endpoint, null, null, cfg);
                requireHttpSuccess(result);
                JsonObject json = parseObject(result.body, "WeChat access token");
                String value = jsonString(json, "access_token");
                if (value.isEmpty()) {
                    throw platformFailure("WeChat token", json);
                }
                return new AccessToken(value, expiresAt(jsonLong(json, "expires_in", 7200L)));
            }
        }).value;
        String base = trimTrailingSlash(firstNonEmpty(target.baseUrl, WECHAT_API_BASE));
        JsonObject body = new JsonObject();
        body.addProperty("touser", target.targetId);
        String endpoint;
        if ("template".equalsIgnoreCase(target.mode)) {
            endpoint = base + "/cgi-bin/message/template/send?access_token=" + query(token);
            body.addProperty("template_id", target.templateId);
            if (!safe(target.templateUrl).isEmpty()) {
                body.addProperty("url", target.templateUrl);
            }
            body.add("data", buildWechatTemplateData(alert));
        } else {
            endpoint = base + "/cgi-bin/message/custom/send?access_token=" + query(token);
            body.addProperty("msgtype", "text");
            JsonObject text = new JsonObject();
            text.addProperty("content", truncate(formatMessage(ownerUuid, alert), 1900));
            body.add("text", text);
        }
        HttpResult result = request("POST", endpoint, GSON.toJson(body), null, cfg);
        requireHttpSuccess(result);
        JsonObject response = parseObject(result.body, "WeChat send");
        long errcode = jsonLong(response, "errcode", 0L);
        if (errcode == 40001L || errcode == 40014L || errcode == 42001L) {
            accessTokens.remove(tokenKey);
            throw DeliveryException.retryable("WeChat access token expired", 0L);
        }
        if (errcode != 0L) {
            throw platformFailure("WeChat send", response);
        }
    }

    private void sendWecomBot(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "markdown");
        JsonObject markdown = new JsonObject();
        markdown.addProperty("content", truncate(formatWecomMarkdown(ownerUuid, alert), 4000));
        body.add("markdown", markdown);
        HttpResult result = request("POST", target.url, GSON.toJson(body), null, cfg);
        requireHttpSuccess(result);
        requirePlatformSuccess(result.body, "WeCom bot", "errcode");
    }

    private void sendWecomApp(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        final String tokenKey = "wecom|" + target.corpId + "|" + target.agentId + "|"
            + secretFingerprint(target.corpSecret);
        String token = token(tokenKey, new TokenLoader() {

            @Override
            public AccessToken load() throws DeliveryException {
                String base = trimTrailingSlash(firstNonEmpty(target.baseUrl, WECOM_API_BASE));
                String tokenBase = firstNonEmpty(target.tokenUrl, base + "/cgi-bin/gettoken");
                String endpoint = tokenBase + (tokenBase.contains("?") ? "&" : "?")
                    + "corpid="
                    + query(target.corpId)
                    + "&corpsecret="
                    + query(target.corpSecret);
                HttpResult result = AlertDeliveryTransport.this.request("GET", endpoint, null, null, cfg);
                requireHttpSuccess(result);
                JsonObject json = parseObject(result.body, "WeCom access token");
                if (jsonLong(json, "errcode", 0L) != 0L) {
                    throw platformFailure("WeCom token", json);
                }
                String value = jsonString(json, "access_token");
                if (value.isEmpty()) {
                    throw DeliveryException.permanent("WeCom access token missing");
                }
                return new AccessToken(value, expiresAt(jsonLong(json, "expires_in", 7200L)));
            }
        }).value;
        String base = trimTrailingSlash(firstNonEmpty(target.baseUrl, WECOM_API_BASE));
        String endpoint = base + "/cgi-bin/message/send?access_token=" + query(token);
        JsonObject body = new JsonObject();
        if (!safe(target.toUser).isEmpty()) body.addProperty("touser", target.toUser);
        if (!safe(target.toParty).isEmpty()) body.addProperty("toparty", target.toParty);
        if (!safe(target.toTag).isEmpty()) body.addProperty("totag", target.toTag);
        body.addProperty("msgtype", "text");
        body.addProperty("agentid", target.agentId);
        body.addProperty("safe", 0);
        JsonObject text = new JsonObject();
        text.addProperty("content", truncate(formatMessage(ownerUuid, alert), 2000));
        body.add("text", text);
        HttpResult result = request("POST", endpoint, GSON.toJson(body), null, cfg);
        requireHttpSuccess(result);
        JsonObject response = parseObject(result.body, "WeCom send");
        long errcode = jsonLong(response, "errcode", 0L);
        if (errcode == 40014L || errcode == 42001L) {
            accessTokens.remove(tokenKey);
            throw DeliveryException.retryable("WeCom access token expired", 0L);
        }
        if (errcode != 0L) {
            throw platformFailure("WeCom send", response);
        }
    }

    private void sendEmail(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert,
        WebAlertsConfig cfg) throws DeliveryException {
        Socket socket = null;
        try {
            socket = connectSmtp(target, cfg.notificationConnectTimeoutMs, cfg.notificationReadTimeoutMs);
            SmtpSession smtp = new SmtpSession(socket);
            smtp.expect(220);
            smtp.command("EHLO textech-webae", 250);
            if ("starttls".equalsIgnoreCase(target.smtpSecurity)) {
                smtp.command("STARTTLS", 220);
                socket = wrapTls(socket, target.smtpHost, target.smtpPort, cfg.notificationReadTimeoutMs);
                smtp = new SmtpSession(socket);
                smtp.command("EHLO textech-webae", 250);
            }
            if (!safe(target.smtpUsername).isEmpty()) {
                smtp.command("AUTH LOGIN", 334);
                smtp.command(base64(target.smtpUsername.getBytes(StandardCharsets.UTF_8)), 334);
                smtp.command(base64(safe(target.smtpPassword).getBytes(StandardCharsets.UTF_8)), 235);
            }
            smtp.command("MAIL FROM:<" + extractAddress(target.mailFrom) + ">", 250);
            for (String recipient : allRecipients(target)) {
                smtp.command("RCPT TO:<" + extractAddress(recipient) + ">", 250, 251);
            }
            smtp.command("DATA", 354);
            smtp.writeData(buildMail(target, ownerUuid, alert));
            smtp.expect(250);
            try {
                smtp.command("QUIT", 221);
            } catch (Exception ignored) {}
        } catch (DeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw DeliveryException.retryable("SMTP transport failure: " + safeMessage(e), 0L);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private Socket connectSmtp(WebAlertsConfig.NotificationTarget target, int connectTimeoutMs, int readTimeoutMs)
        throws Exception {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(target.smtpHost, target.smtpPort), connectTimeoutMs);
        plain.setSoTimeout(readTimeoutMs);
        if ("ssl".equalsIgnoreCase(target.smtpSecurity)) {
            return wrapTls(plain, target.smtpHost, target.smtpPort, readTimeoutMs);
        }
        return plain;
    }

    private Socket wrapTls(Socket socket, String host, int port, int readTimeoutMs) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(socket, host, port, true);
        ssl.setSoTimeout(readTimeoutMs);
        ssl.startHandshake();
        return ssl;
    }

    private String buildMail(WebAlertsConfig.NotificationTarget target, String ownerUuid, WebAlertDto alert) {
        String subject = sanitizeHeader(target.subjectPrefix) + " [" + safe(alert.severity).toUpperCase() + "] "
            + safe(alert.title);
        String body = formatMessage(ownerUuid, alert);
        String encodedBody = wrapBase64(base64(body.getBytes(StandardCharsets.UTF_8)));
        StringBuilder mail = new StringBuilder();
        mail.append("Date: ").append(rfc2822(alert.timestamp)).append("\r\n");
        mail.append("From: ").append(sanitizeHeader(target.mailFrom)).append("\r\n");
        mail.append("To: ").append(sanitizeHeader(join(target.mailTo, ", "))).append("\r\n");
        if (target.mailCc != null && !target.mailCc.isEmpty()) {
            mail.append("Cc: ").append(sanitizeHeader(join(target.mailCc, ", "))).append("\r\n");
        }
        mail.append("Subject: =?UTF-8?B?").append(base64(subject.getBytes(StandardCharsets.UTF_8))).append("?=\r\n");
        mail.append("MIME-Version: 1.0\r\n");
        mail.append("Content-Type: text/plain; charset=UTF-8\r\n");
        mail.append("Content-Transfer-Encoding: base64\r\n\r\n");
        mail.append(encodedBody).append("\r\n");
        return mail.toString();
    }

    private HttpResult request(String method, String endpoint, String body, Map<String, String> headers,
        WebAlertsConfig cfg) throws DeliveryException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(cfg.notificationConnectTimeoutMs);
            connection.setReadTimeout(cfg.notificationReadTimeoutMs);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(bytes);
                } finally {
                    output.close();
                }
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String response = readLimited(input);
            long retryAfterMs = parseRetryAfter(connection.getHeaderField("Retry-After"));
            return new HttpResult(code, response, retryAfterMs);
        } catch (Exception e) {
            throw DeliveryException.retryable("HTTP transport failure: " + safeMessage(e), 0L);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private AccessToken token(String key, TokenLoader loader) throws DeliveryException {
        AccessToken cached = accessTokens.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMs - now > 60_000L) {
            return cached;
        }
        synchronized (accessTokens) {
            cached = accessTokens.get(key);
            if (cached != null && cached.expiresAtMs - now > 60_000L) {
                return cached;
            }
            AccessToken loaded = loader.load();
            if (accessTokens.size() >= 64) {
                for (Map.Entry<String, AccessToken> entry : accessTokens.entrySet()) {
                    AccessToken value = entry.getValue();
                    if (value != null && value.expiresAtMs <= now) {
                        accessTokens.remove(entry.getKey(), value);
                    }
                }
            }
            accessTokens.put(key, loaded);
            return loaded;
        }
    }

    private static JsonObject buildWechatTemplateData(WebAlertDto alert) {
        JsonObject data = new JsonObject();
        addTemplateValue(data, "first", safe(alert.title));
        addTemplateValue(data, "keyword1", safe(alert.type));
        addTemplateValue(data, "keyword2", safe(alert.severity));
        addTemplateValue(data, "keyword3", alert.networkId >= 0 ? String.valueOf(alert.networkId) : "server");
        addTemplateValue(data, "keyword4", iso8601(alert.timestamp));
        addTemplateValue(data, "remark", safe(alert.message));
        return data;
    }

    private static void addTemplateValue(JsonObject data, String key, String value) {
        JsonObject entry = new JsonObject();
        entry.addProperty("value", value);
        data.add(key, entry);
    }

    private static String formatMessage(String ownerUuid, WebAlertDto alert) {
        StringBuilder out = new StringBuilder();
        out.append("[WebAE][").append(safe(alert.severity).toUpperCase()).append("] ")
            .append(safe(alert.title));
        if (!safe(alert.message).isEmpty()) {
            out.append('\n').append(alert.message);
        }
        out.append("\nType: ").append(safe(alert.type));
        if (alert.networkId >= 0) {
            out.append("\nNetwork: ").append(alert.networkId);
        }
        out.append("\nOwner: ").append(safe(ownerUuid));
        out.append("\nTime: ").append(iso8601(alert.timestamp));
        return out.toString();
    }

    private static String formatWecomMarkdown(String ownerUuid, WebAlertDto alert) {
        String color = "error".equals(alert.severity) ? "warning" : "warning".equals(alert.severity) ? "warning" : "info";
        return "### WebAE Alert\n> Severity: <font color=\"" + color + "\">" + escapeMarkdown(alert.severity)
            + "</font>\n> "
            + escapeMarkdown(alert.title)
            + "\n> "
            + escapeMarkdown(alert.message)
            + "\n> Owner: `"
            + escapeMarkdown(ownerUuid)
            + "`";
    }

    private static void requireHttpSuccess(HttpResult result) throws DeliveryException {
        if (result.code >= 200 && result.code < 300) {
            return;
        }
        if (result.code == 408 || result.code == 425 || result.code == 429 || result.code >= 500) {
            throw DeliveryException.retryable("HTTP " + result.code, result.retryAfterMs);
        }
        throw DeliveryException.permanent("HTTP " + result.code);
    }

    private static void requirePlatformSuccess(String body, String platform, String errorField)
        throws DeliveryException {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        JsonObject json = parseObject(body, platform + " response");
        if (json.has(errorField)) {
            long code = jsonLong(json, errorField, 0L);
            if (code != 0L) {
                throw platformFailure(platform, json);
            }
        }
    }

    private static DeliveryException platformFailure(String platform, JsonObject json) {
        long code = jsonLong(json, "errcode", jsonLong(json, "code", -1L));
        String message = firstNonEmpty(jsonString(json, "errmsg"), jsonString(json, "message"));
        boolean retryable = code == -1L || code == 45009L || code == 45011L || code == 429L || code >= 50000L;
        return new DeliveryException(platform + " error " + code + (message.isEmpty() ? "" : ": " + message),
            retryable, 0L);
    }

    private static JsonObject parseObject(String json, String label) throws DeliveryException {
        try {
            JsonElement element = new JsonParser().parse(json == null ? "{}" : json);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignored) {}
        throw DeliveryException.permanent(label + " returned invalid JSON");
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

    private static List<String> allRecipients(WebAlertsConfig.NotificationTarget target) {
        List<String> out = new ArrayList<String>();
        if (target.mailTo != null) out.addAll(target.mailTo);
        if (target.mailCc != null) out.addAll(target.mailCc);
        return out;
    }

    private static String extractAddress(String value) {
        String text = sanitizeHeader(value).trim();
        int start = text.lastIndexOf('<');
        int end = text.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return text.substring(start + 1, end).trim();
        }
        return text;
    }

    private static String base64(byte[] value) {
        return DatatypeConverter.printBase64Binary(value);
    }

    private static String wrapBase64(String value) {
        StringBuilder out = new StringBuilder(value.length() + value.length() / 76 * 2);
        for (int i = 0; i < value.length(); i += 76) {
            if (i > 0) out.append("\r\n");
            out.append(value.substring(i, Math.min(value.length(), i + 76)));
        }
        return out.toString();
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder out = new StringBuilder();
        if (values == null) return "";
        for (String value : values) {
            if (out.length() > 0) out.append(delimiter);
            out.append(value);
        }
        return out.toString();
    }

    private static String truncate(String value, int maxChars) {
        String text = safe(value);
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private static String pathSegment(String value) throws DeliveryException {
        String text = safe(value).trim();
        if (text.isEmpty() || text.contains("/") || text.contains("?") || text.contains("#")) {
            throw DeliveryException.permanent("invalid target id");
        }
        return text;
    }

    private static String query(String value) throws DeliveryException {
        try {
            return URLEncoder.encode(safe(value), "UTF-8");
        } catch (Exception e) {
            throw DeliveryException.permanent("failed to encode request parameter");
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
        return first != null && !first.trim().isEmpty() ? first.trim() : safe(second).trim();
    }

    private static String jsonString(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString()
                : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static long jsonLong(JsonObject object, String key, long fallback) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong()
                : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long expiresAt(long expiresInSeconds) {
        long safeSeconds = Math.max(120L, Math.min(expiresInSeconds, 86400L));
        return System.currentTimeMillis() + safeSeconds * 1000L;
    }

    private static long parseRetryAfter(String value) {
        if (value == null) return 0L;
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String iso8601(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp > 0L ? timestamp : System.currentTimeMillis()));
    }

    private static String rfc2822(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US);
        return format.format(new Date(timestamp > 0L ? timestamp : System.currentTimeMillis()));
    }

    private static String escapeMarkdown(String value) {
        return safe(value).replace("`", "'").replace("\r", " ").replace("\n", " ");
    }

    private static String safeMessage(Exception error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sanitizeHeader(String value) {
        return safe(value).replace("\r", "").replace("\n", "");
    }

    private static String secretFingerprint(String value) {
        return Integer.toHexString(safe(value).hashCode());
    }

    private interface TokenLoader {

        AccessToken load() throws DeliveryException;
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
        final long retryAfterMs;

        HttpResult(int code, String body, long retryAfterMs) {
            this.code = code;
            this.body = body;
            this.retryAfterMs = retryAfterMs;
        }
    }

    static final class DeliveryException extends Exception {

        final boolean retryable;
        final long retryAfterMs;

        DeliveryException(String message, boolean retryable, long retryAfterMs) {
            super(message);
            this.retryable = retryable;
            this.retryAfterMs = retryAfterMs;
        }

        static DeliveryException retryable(String message, long retryAfterMs) {
            return new DeliveryException(message, true, retryAfterMs);
        }

        static DeliveryException permanent(String message) {
            return new DeliveryException(message, false, 0L);
        }
    }

    private static final class SmtpSession {

        private final BufferedReader reader;
        private final Writer writer;

        SmtpSession(Socket socket) throws Exception {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), ASCII));
            this.writer = new OutputStreamWriter(socket.getOutputStream(), ASCII);
        }

        void command(String command, int... expectedCodes) throws Exception {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            expect(expectedCodes);
        }

        void writeData(String message) throws Exception {
            String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
            String[] lines = normalized.split("\n", -1);
            for (String line : lines) {
                if (line.startsWith(".")) writer.write('.');
                writer.write(line);
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();
        }

        void expect(int... expectedCodes) throws Exception {
            String line = reader.readLine();
            if (line == null || line.length() < 3) {
                throw DeliveryException.retryable("SMTP connection closed", 0L);
            }
            int code;
            try {
                code = Integer.parseInt(line.substring(0, 3));
            } catch (NumberFormatException e) {
                throw DeliveryException.permanent("invalid SMTP response");
            }
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) break;
            }
            for (int expected : expectedCodes) {
                if (code == expected) return;
            }
            if (code >= 400 && code < 500) {
                throw DeliveryException.retryable("SMTP " + code, 0L);
            }
            throw DeliveryException.permanent("SMTP " + code);
        }
    }
}
