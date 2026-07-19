package com.imgood.textech.webae.assistant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.webae.assistant.WebAiConfigStore.RuntimeConfig;

/** Bounded server-side chat client for WebAE-managed AI providers. */
public final class WebAiHttpClient {

    private static final int MAX_RESPONSE_CHARS = 1_048_576;

    private final RuntimeConfig config;

    public WebAiHttpClient(RuntimeConfig config) {
        if (config == null || config.apiKey == null || config.apiKey.isEmpty()) {
            throw new IllegalArgumentException("Web AI is not configured.");
        }
        this.config = config;
    }

    public String complete(String systemPrompt, String userPrompt) throws IOException {
        List<Message> messages = new ArrayList<Message>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userPrompt == null ? "" : userPrompt));
        return complete(messages);
    }

    public String complete(List<Message> messages) throws IOException {
        if (WebAiConfigStore.PROTOCOL_ANTHROPIC.equals(config.protocol)) {
            return executeAnthropic(messages);
        }
        if (WebAiConfigStore.PROTOCOL_GEMINI.equals(config.protocol)) {
            return executeGemini(messages);
        }
        return executeOpenAiCompatible(messages);
    }

    private String executeOpenAiCompatible(List<Message> messages) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("temperature", config.temperature);
        body.addProperty("max_tokens", config.maxTokens);
        JsonArray jsonMessages = new JsonArray();
        for (Message message : safeMessages(messages)) {
            JsonObject json = new JsonObject();
            json.addProperty("role", message.role);
            json.addProperty("content", message.content);
            jsonMessages.add(json);
        }
        body.add("messages", jsonMessages);
        JsonObject response = post(openAiEndpoint(), body, "Authorization", "Bearer " + config.apiKey, null, null);
        JsonArray choices = response.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) throw new IOException("AI response did not contain choices.");
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) throw new IOException("AI response did not contain a message.");
        String content = string(message, "content");
        if (content.isEmpty()) content = string(message, "reasoning_content");
        if (content.isEmpty()) throw new IOException("AI response content was empty.");
        return content;
    }

    private String executeAnthropic(List<Message> messages) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model);
        body.addProperty("max_tokens", config.maxTokens);
        body.addProperty("temperature", config.temperature);
        JsonArray jsonMessages = new JsonArray();
        StringBuilder system = new StringBuilder();
        for (Message message : safeMessages(messages)) {
            if ("system".equals(message.role)) {
                if (system.length() > 0) system.append('\n');
                system.append(message.content);
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("role", "assistant".equals(message.role) ? "assistant" : "user");
            json.addProperty("content", message.content);
            jsonMessages.add(json);
        }
        if (system.length() > 0) body.addProperty("system", system.toString());
        body.add("messages", jsonMessages);
        JsonObject response = post(appendPath(config.baseUrl, "/v1/messages"), body,
            "x-api-key", config.apiKey, "anthropic-version", "2023-06-01");
        JsonArray content = response.getAsJsonArray("content");
        if (content == null || content.size() == 0) throw new IOException("AI response content was empty.");
        StringBuilder text = new StringBuilder();
        for (JsonElement element : content) {
            if (!element.isJsonObject()) continue;
            String value = string(element.getAsJsonObject(), "text");
            if (!value.isEmpty()) text.append(value);
        }
        if (text.length() == 0) throw new IOException("AI response content was empty.");
        return text.toString();
    }

    private String executeGemini(List<Message> messages) throws IOException {
        JsonObject body = new JsonObject();
        JsonArray contents = new JsonArray();
        StringBuilder system = new StringBuilder();
        for (Message message : safeMessages(messages)) {
            if ("system".equals(message.role)) {
                if (system.length() > 0) system.append('\n');
                system.append(message.content);
                continue;
            }
            JsonObject content = new JsonObject();
            content.addProperty("role", "assistant".equals(message.role) ? "model" : "user");
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", message.content);
            parts.add(part);
            content.add("parts", parts);
            contents.add(content);
        }
        if (system.length() > 0) {
            JsonObject instruction = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject part = new JsonObject();
            part.addProperty("text", system.toString());
            parts.add(part);
            instruction.add("parts", parts);
            body.add("systemInstruction", instruction);
        }
        body.add("contents", contents);
        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", config.temperature);
        generation.addProperty("maxOutputTokens", config.maxTokens);
        body.add("generationConfig", generation);
        String model = URLEncoder.encode(config.model, "UTF-8").replace("+", "%20");
        String endpoint = appendPath(config.baseUrl, "/v1beta/models/" + model + ":generateContent");
        JsonObject response = post(endpoint, body, "x-goog-api-key", config.apiKey, null, null);
        JsonArray candidates = response.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) throw new IOException("AI response had no candidates.");
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
        if (parts == null) throw new IOException("AI response content was empty.");
        StringBuilder text = new StringBuilder();
        for (JsonElement element : parts) {
            if (element.isJsonObject()) text.append(string(element.getAsJsonObject(), "text"));
        }
        if (text.length() == 0) throw new IOException("AI response content was empty.");
        return text.toString();
    }

    private JsonObject post(String endpoint, JsonObject body, String headerName, String headerValue,
        String secondHeaderName, String secondHeaderValue) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            int timeout = Math.max(5, Math.min(120, config.timeoutSeconds)) * 1000;
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(headerName, headerValue);
            if (secondHeaderName != null) connection.setRequestProperty(secondHeaderName, secondHeaderValue);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int status = connection.getResponseCode();
            String response = readBounded(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IOException("AI provider request failed (HTTP " + status + "): "
                    + safeProviderError(response));
            }
            try {
                return new JsonParser().parse(response).getAsJsonObject();
            } catch (Exception e) {
                throw new IOException("AI provider returned invalid JSON.");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String openAiEndpoint() {
        String base = trimSlash(config.baseUrl);
        String lower = base.toLowerCase();
        if (lower.endsWith("/chat/completions")) return base;
        if ("zhipu".equals(config.providerId)) return appendPath(base, "/v4/chat/completions");
        if ("volcengine".equals(config.providerId)) return appendPath(base, "/v3/chat/completions");
        if (lower.endsWith("/v1") || lower.endsWith("/v3") || lower.endsWith("/v4")) {
            return appendPath(base, "/chat/completions");
        }
        return appendPath(base, "/v1/chat/completions");
    }

    private String safeProviderError(String response) {
        String message = "provider rejected the request";
        try {
            JsonObject root = new JsonParser().parse(response).getAsJsonObject();
            JsonElement error = root.get("error");
            if (error != null && error.isJsonObject()) message = string(error.getAsJsonObject(), "message");
            else if (error != null && error.isJsonPrimitive()) message = error.getAsString();
            if (message.isEmpty()) message = string(root, "message");
        } catch (Exception ignored) {}
        if (message == null || message.isEmpty()) message = "provider rejected the request";
        message = message.replace(config.apiKey, "[REDACTED]").replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private static List<Message> safeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("AI messages are empty.");
        List<Message> result = new ArrayList<Message>();
        int start = Math.max(0, messages.size() - 20);
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message == null) continue;
            String role = "system".equals(message.role) || "assistant".equals(message.role) ? message.role : "user";
            String content = message.content == null ? "" : message.content;
            if (content.length() > 32_000) content = content.substring(0, 32_000);
            result.add(new Message(role, content));
        }
        return result;
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (value.length() + read > MAX_RESPONSE_CHARS) {
                    throw new IOException("AI provider response exceeded the size limit.");
                }
                value.append(buffer, 0, read);
            }
        }
        return value.toString();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String appendPath(String base, String path) {
        return trimSlash(base) + (path.startsWith("/") ? path : "/" + path);
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static final class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role == null ? "user" : role;
            this.content = content == null ? "" : content;
        }
    }
}
