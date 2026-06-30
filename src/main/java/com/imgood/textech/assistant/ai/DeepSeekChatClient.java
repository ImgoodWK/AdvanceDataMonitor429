package com.imgood.textech.assistant.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.assistant.ai.AiProviderProfiles.SearchCapability;
import com.imgood.textech.assistant.ai.ChatResponse.Source;

public class DeepSeekChatClient {

    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;

    public String chat(List<ChatMessage> messages) throws IOException {
        return chat(messages, new ChatRequestOptions(Config.aiWebSearchEnabled, Config.aiWebSearchMode, false), null)
            .contentWithSources();
    }

    public ChatResponse chat(List<ChatMessage> messages, ChatRequestOptions options, ChatStreamListener listener)
        throws IOException {
        this.cancelled = false;
        String apiKey = Config.getAiApiKey();
        if (apiKey.isEmpty()) {
            throw new IOException("Missing AI API key. Set ai.apiKey in the config file or DEEPSEEK_API_KEY.");
        }

        ChatRequestOptions requestOptions = options == null
            ? new ChatRequestOptions(Config.aiWebSearchEnabled, Config.aiWebSearchMode, Config.aiStreamingEnabled)
            : options;
        SearchCapability capability = AiProviderProfiles.searchCapability(
            Config.aiApiBaseUrl,
            Config.aiModel,
            requestOptions.webSearchMode,
            requestOptions.webSearchEnabled);
        String[] modes = capability.enabled ? AiProviderProfiles.fallbackModes(capability.mode)
            : new String[] { AiProviderProfiles.MODE_OFF };
        IOException firstFailure = null;
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            try {
                return execute(messages, apiKey, mode, requestOptions.stream, listener, false);
            } catch (IOException failure) {
                if (isCancelled()) {
                    throw failure;
                }
                if (isSearchCompatibilityError(failure) && i + 1 < modes.length) {
                    firstFailure = firstFailure == null ? failure : firstFailure;
                    debug("Retrying AI request with fallback web search mode: " + modes[i + 1]);
                    continue;
                }
                if (requestOptions.stream && isStreamCompatibilityError(failure)) {
                    firstFailure = firstFailure == null ? failure : firstFailure;
                    debug("Retrying AI request without streaming after stream compatibility error.");
                    return execute(messages, apiKey, mode, false, null, false);
                }
                if (isSearchCompatibilityError(failure) && capability.enabled) {
                    firstFailure = firstFailure == null ? failure : firstFailure;
                    break;
                }
                throw failure;
            }
        }

        if (capability.enabled) {
            ChatResponse fallback = execute(messages, apiKey, AiProviderProfiles.MODE_OFF, false, null, true);
            return new ChatResponse(
                "Web search was not accepted by the provider, so I retried without web search.\n\n" + fallback.content,
                fallback.sources,
                true);
        }
        throw firstFailure == null ? new IOException("AI request failed.") : firstFailure;
    }

    public void cancel() {
        this.cancelled = true;
        HttpURLConnection connection = this.activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private boolean isCancelled() {
        return this.cancelled;
    }

    private ChatResponse execute(List<ChatMessage> messages, String apiKey, String webSearchMode, boolean stream,
        ChatStreamListener listener, boolean fallbackUsed) throws IOException {
        HttpURLConnection connection = openConnection(apiKey);
        this.activeConnection = connection;
        try {
            writeRequest(connection, messages, webSearchMode, stream);
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream()
                : connection.getErrorStream();
            if (responseCode < 200 || responseCode >= 300) {
                String response = readResponse(inputStream);
                throw new IOException("AI request failed (HTTP " + responseCode + "): " + response);
            }
            if (stream) {
                return readStreamingResponse(inputStream, listener, fallbackUsed);
            }
            String response = readResponse(inputStream);
            return parseResponse(response, fallbackUsed);
        } finally {
            this.activeConnection = null;
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String apiKey) throws IOException {
        String baseUrl = trimTrailingSlash(Config.aiApiBaseUrl == null ? "" : Config.aiApiBaseUrl.trim());
        if (baseUrl.isEmpty()) {
            baseUrl = "https://api.deepseek.com";
        }
        URL url = new URL(baseUrl + "/v1/chat/completions");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeout = Math.max(5, Config.aiTimeoutSeconds) * 1000;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        return connection;
    }

    private void writeRequest(HttpURLConnection connection, List<ChatMessage> messages, String webSearchMode,
        boolean stream) throws IOException {
        JsonObject payload = buildPayload(messages, webSearchMode, stream);
        debug(
            "AI request: provider=" + AiProviderProfiles.detectProfile().displayName
                + ", model="
                + Config.aiModel
                + ", search="
                + webSearchMode
                + ", stream="
                + stream);
        byte[] body = payload.toString()
            .getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body);
        }
    }

    public JsonObject buildPayloadForTest(List<ChatMessage> messages, String webSearchMode) {
        return buildPayload(messages, webSearchMode, false);
    }

    public JsonObject buildPayloadForTest(List<ChatMessage> messages, String webSearchMode, boolean stream) {
        return buildPayload(messages, webSearchMode, stream);
    }

    private JsonObject buildPayload(List<ChatMessage> messages, String webSearchMode, boolean stream) {
        JsonObject payload = new JsonObject();
        payload.addProperty(
            "model",
            Config.aiModel == null || Config.aiModel.trim()
                .isEmpty() ? "deepseek-chat" : Config.aiModel.trim());
        payload.addProperty("temperature", Config.aiTemperature);
        payload.addProperty("max_tokens", Config.aiMaxTokens);
        if (stream) {
            payload.addProperty("stream", true);
        }
        addWebSearchOptions(payload, webSearchMode);

        JsonArray jsonMessages = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject jsonMessage = new JsonObject();
            jsonMessage.addProperty("role", message.role);
            jsonMessage.addProperty("content", message.content);
            jsonMessages.add(jsonMessage);
        }
        payload.add("messages", jsonMessages);
        return payload;
    }

    private void addWebSearchOptions(JsonObject payload, String mode) {
        if (AiProviderProfiles.MODE_OFF.equals(mode) || AiProviderProfiles.MODE_UNSUPPORTED.equals(mode)) {
            return;
        }
        if (AiProviderProfiles.MODE_OPENROUTER.equals(mode)) {
            addOpenRouterWebPlugin(payload);
        } else if (AiProviderProfiles.MODE_DASHSCOPE.equals(mode)) {
            payload.addProperty("enable_search", true);
        } else if (AiProviderProfiles.MODE_ZHIPU.equals(mode)) {
            addZhipuWebSearchTool(payload);
        } else if (AiProviderProfiles.MODE_OPENAI.equals(mode)) {
            payload.add("web_search_options", new JsonObject());
        } else if (AiProviderProfiles.MODE_GENERIC_TOOLS.equals(mode)) {
            addGenericWebSearchTool(payload);
        }
    }

    private void addOpenRouterWebPlugin(JsonObject payload) {
        JsonArray plugins = new JsonArray();
        JsonObject web = new JsonObject();
        web.addProperty("id", "web");
        plugins.add(web);
        payload.add("plugins", plugins);
    }

    private void addZhipuWebSearchTool(JsonObject payload) {
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        JsonObject webSearch = new JsonObject();
        webSearch.addProperty("enable", true);
        tool.addProperty("type", "web_search");
        tool.add("web_search", webSearch);
        tools.add(tool);
        payload.add("tools", tools);
    }

    private void addGenericWebSearchTool(JsonObject payload) {
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "web_search_preview");
        tools.add(tool);
        payload.add("tools", tools);
    }

    private ChatResponse readStreamingResponse(InputStream stream, ChatStreamListener listener, boolean fallbackUsed)
        throws IOException {
        StringBuilder builder = new StringBuilder();
        List<Source> sources = new ArrayList<>();
        Set<String> sourceUrls = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled()) {
                    throw new IOException("AI request cancelled.");
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5)
                    .trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                if (data.isEmpty()) {
                    continue;
                }
                JsonObject event = new JsonParser().parse(data)
                    .getAsJsonObject();
                addSources(event, sources, sourceUrls);
                String delta = parseStreamingDelta(event);
                if (!delta.isEmpty()) {
                    builder.append(delta);
                    if (listener != null) {
                        listener.onDelta(delta);
                    }
                }
            }
        }
        return new ChatResponse(builder.toString(), sources, fallbackUsed);
    }

    private String parseStreamingDelta(JsonObject event) {
        JsonArray choices = event.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            return "";
        }
        JsonObject choice = choices.get(0)
            .getAsJsonObject();
        JsonObject delta = choice.getAsJsonObject("delta");
        if (delta != null && delta.has("content")
            && !delta.get("content")
                .isJsonNull()) {
            return delta.get("content")
                .getAsString();
        }
        JsonObject message = choice.getAsJsonObject("message");
        if (message != null && message.has("content")
            && !message.get("content")
                .isJsonNull()) {
            return message.get("content")
                .getAsString();
        }
        return "";
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCancelled()) {
                    throw new IOException("AI request cancelled.");
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private ChatResponse parseResponse(String response, boolean fallbackUsed) throws IOException {
        JsonObject root = new JsonParser().parse(response)
            .getAsJsonObject();
        List<Source> sources = new ArrayList<>();
        addSources(root, sources, new HashSet<>());
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new IOException("AI response did not contain choices: " + response);
        }
        JsonObject message = choices.get(0)
            .getAsJsonObject()
            .getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IOException("AI response did not contain message content: " + response);
        }
        return new ChatResponse(
            message.get("content")
                .getAsString(),
            sources,
            fallbackUsed);
    }

    private void addSources(JsonElement element, List<Source> sources, Set<String> urls) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                addSources(child, sources, urls);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String url = getString(object, "url");
        if (url.isEmpty()) {
            url = getString(object, "uri");
        }
        if (url.startsWith("http") && urls.add(url)) {
            String title = getString(object, "title");
            if (title.isEmpty()) {
                title = getString(object, "name");
            }
            sources.add(new Source(title, url));
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            addSources(entry.getValue(), sources, urls);
        }
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private boolean isSearchCompatibilityError(IOException failure) {
        String text = failure.getMessage() == null ? ""
            : failure.getMessage()
                .toLowerCase();
        return text.contains("http 400") || text.contains("http 404") || text.contains("http 422")
            ? text.contains("tool") || text.contains("plugin")
                || text.contains("web_search")
                || text.contains("web search")
                || text.contains("enable_search")
                || text.contains("unsupported")
                || text.contains("unknown parameter")
                || text.contains("invalid parameter")
            : false;
    }

    private boolean isStreamCompatibilityError(IOException failure) {
        String text = failure.getMessage() == null ? ""
            : failure.getMessage()
                .toLowerCase();
        return text.contains("stream") || text.contains("sse") || text.contains("event-stream");
    }

    private void debug(String message) {
        if (Config.aiDebugLogging) {
            AdvanceDataMonitor.LOG.info("[AI] " + message);
        }
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static class ChatMessage {

        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
