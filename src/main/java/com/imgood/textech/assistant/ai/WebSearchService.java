package com.imgood.textech.assistant.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;

/**
 * Unified web search layer: multi-engine adapter, result normalization, and auto-fallback.
 * Search runs on the client before the LLM request; results are injected into the user message.
 */
public final class WebSearchService {

    public static final String PROVIDER_AUTO = "auto";
    public static final String PROVIDER_TAVILY_KEYLESS = "tavily_keyless";
    public static final String PROVIDER_DUCKDUCKGO = "duckduckgo";
    public static final String PROVIDER_TAVILY = "tavily";
    public static final String PROVIDER_BRAVE = "brave";
    public static final String PROVIDER_SERPER = "serper";
    public static final String PROVIDER_SEARXNG = "searxng";

    private static final String[] AUTO_CHAIN = { PROVIDER_TAVILY_KEYLESS, PROVIDER_DUCKDUCKGO, PROVIDER_TAVILY,
        PROVIDER_BRAVE, PROVIDER_SERPER, PROVIDER_SEARXNG };

    private static final String[] ALL_PROVIDERS = { PROVIDER_AUTO, PROVIDER_TAVILY_KEYLESS, PROVIDER_DUCKDUCKGO,
        PROVIDER_TAVILY, PROVIDER_BRAVE, PROVIDER_SERPER, PROVIDER_SEARXNG };

    /** Maximum response body retained from a search provider. */
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private WebSearchService() {}

    public static String[] allProviders() {
        return ALL_PROVIDERS.clone();
    }

    public static boolean isProvider(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim()
            .toLowerCase();
        for (String provider : ALL_PROVIDERS) {
            if (provider.equals(normalized)) {
                return true;
            }
        }
        return isLegacyProviderMode(normalized);
    }

    public static boolean isLegacyProviderMode(String value) {
        return AiProviderProfiles.MODE_OPENAI.equals(value) || AiProviderProfiles.MODE_OPENROUTER.equals(value)
            || AiProviderProfiles.MODE_DASHSCOPE.equals(value)
            || AiProviderProfiles.MODE_ZHIPU.equals(value)
            || AiProviderProfiles.MODE_GENERIC_TOOLS.equals(value);
    }

    public static String normalizeProvider(String value) {
        if (value == null || value.trim()
            .isEmpty()) {
            return PROVIDER_AUTO;
        }
        String normalized = value.trim()
            .toLowerCase();
        if (isProvider(normalized) && !isLegacyProviderMode(normalized)) {
            return normalized;
        }
        if (isLegacyProviderMode(normalized)) {
            return PROVIDER_AUTO;
        }
        return PROVIDER_AUTO;
    }

    public static String nextProvider(String current) {
        String normalized = normalizeProvider(current);
        for (int i = 0; i < ALL_PROVIDERS.length; i++) {
            if (ALL_PROVIDERS[i].equals(normalized)) {
                return ALL_PROVIDERS[(i + 1) % ALL_PROVIDERS.length];
            }
        }
        return PROVIDER_AUTO;
    }

    public static WebSearchData performWebSearch(String query) throws WebSearchException {
        return performWebSearch(query, SearchRuntime.fromClientConfig());
    }

    /**
     * Runs the same multi-engine search chain using an explicit runtime (WebAE shared search settings).
     */
    public static WebSearchData performWebSearch(String query, SearchRuntime runtime) throws WebSearchException {
        if (query == null || query.trim()
            .isEmpty()) {
            throw new WebSearchException("Search query is empty.");
        }
        SearchRuntime effective = runtime == null ? SearchRuntime.fromClientConfig() : runtime;
        String trimmedQuery = query.trim();
        String configured = normalizeProvider(effective.mode);
        int maxResults = clampMaxResults(effective.maxResults);
        List<String> chain = buildChain(configured, effective.fallback);
        if (chain.isEmpty()) {
            throw new WebSearchException("No search engine is configured.");
        }

        WebSearchException lastFailure = null;
        for (int i = 0; i < chain.size(); i++) {
            String provider = chain.get(i);
            if (!isProviderAvailable(provider, effective)) {
                debug("Skipping unavailable search provider: " + provider);
                continue;
            }
            try {
                List<WebSearchResult> results = searchWithProvider(provider, trimmedQuery, maxResults, effective);
                if (results.isEmpty()) {
                    throw new WebSearchException("Search provider returned no results: " + provider);
                }
                String context = formatContext(results);
                debug("Web search succeeded via " + provider + ", results=" + results.size());
                return new WebSearchData(provider, context, results);
            } catch (WebSearchException failure) {
                lastFailure = failure;
                debug("Web search failed via " + provider + ": " + failure.getMessage());
                if (!effective.fallback || i + 1 >= chain.size()) {
                    throw failure;
                }
            }
        }
        throw lastFailure == null ? new WebSearchException("All search providers failed.") : lastFailure;
    }

    /** Injects search context into a plain user prompt (WebAE / Spark). */
    public static String injectSearchIntoUserText(String userText, WebSearchData searchData) {
        if (searchData == null || searchData.context == null || searchData.context.isEmpty()) {
            return userText == null ? "" : userText;
        }
        String original = userText == null ? "" : userText;
        return searchData.context + "\n\n---\n\n用户问题：\n" + original;
    }

    public static List<DeepSeekChatClient.ChatMessage> injectSearchIntoMessages(
        List<DeepSeekChatClient.ChatMessage> messages, WebSearchData searchData) {
        if (searchData == null || searchData.context.isEmpty()) {
            return messages;
        }
        List<DeepSeekChatClient.ChatMessage> copy = new ArrayList<DeepSeekChatClient.ChatMessage>();
        for (DeepSeekChatClient.ChatMessage message : messages) {
            copy.add(new DeepSeekChatClient.ChatMessage(message.role, message.content));
        }
        for (int i = copy.size() - 1; i >= 0; i--) {
            if ("user".equals(copy.get(i).role)) {
                String injected = searchData.context + "\n\n---\n\n用户问题：\n" + copy.get(i).content;
                copy.set(i, new DeepSeekChatClient.ChatMessage("user", injected));
                break;
            }
        }
        return copy;
    }

    public static List<ChatResponse.Source> toSources(WebSearchData searchData) {
        List<ChatResponse.Source> sources = new ArrayList<>();
        if (searchData == null) {
            return sources;
        }
        for (WebSearchResult result : searchData.results) {
            if (result.url.startsWith("http")) {
                sources.add(new ChatResponse.Source(result.title, result.url));
            }
        }
        return sources;
    }

    public static String capabilityMessage(String provider, boolean enabled) {
        if (!enabled) {
            return "Web search is off.";
        }
        String normalized = normalizeProvider(provider);
        if (PROVIDER_AUTO.equals(normalized)) {
            return "Built-in web search: auto (tavily_keyless -> duckduckgo -> tavily -> brave -> serper -> searxng).";
        }
        if (isProviderAvailable(normalized, SearchRuntime.fromClientConfig())) {
            return "Built-in web search: " + normalized + ".";
        }
        return "Built-in web search: " + normalized + " (missing API key or base URL).";
    }

    private static List<String> buildChain(String configured, boolean fallback) {
        List<String> chain = new ArrayList<String>();
        if (PROVIDER_AUTO.equals(configured)) {
            for (String provider : AUTO_CHAIN) {
                chain.add(provider);
            }
            return chain;
        }
        chain.add(configured);
        if (fallback) {
            for (String provider : AUTO_CHAIN) {
                if (!provider.equals(configured)) {
                    chain.add(provider);
                }
            }
        }
        return chain;
    }

    private static boolean isProviderAvailable(String provider, SearchRuntime runtime) {
        if (PROVIDER_TAVILY_KEYLESS.equals(provider) || PROVIDER_DUCKDUCKGO.equals(provider)) {
            return true;
        }
        if (PROVIDER_TAVILY.equals(provider) || PROVIDER_BRAVE.equals(provider) || PROVIDER_SERPER.equals(provider)) {
            return runtime != null && runtime.apiKey != null
                && !runtime.apiKey.trim()
                    .isEmpty();
        }
        if (PROVIDER_SEARXNG.equals(provider)) {
            return runtime != null && runtime.baseUrl != null
                && !runtime.baseUrl.trim()
                    .isEmpty();
        }
        return false;
    }

    private static List<WebSearchResult> searchWithProvider(String provider, String query, int maxResults,
        SearchRuntime runtime) throws WebSearchException {
        try {
            if (PROVIDER_TAVILY_KEYLESS.equals(provider)) {
                return searchTavilyKeyless(query, maxResults);
            }
            if (PROVIDER_DUCKDUCKGO.equals(provider)) {
                return searchDuckDuckGo(query, maxResults);
            }
            if (PROVIDER_TAVILY.equals(provider)) {
                return searchTavily(query, maxResults, runtime);
            }
            if (PROVIDER_BRAVE.equals(provider)) {
                return searchBrave(query, maxResults, runtime);
            }
            if (PROVIDER_SERPER.equals(provider)) {
                return searchSerper(query, maxResults, runtime);
            }
            if (PROVIDER_SEARXNG.equals(provider)) {
                return searchSearxng(query, maxResults, runtime);
            }
            throw new WebSearchException("Unknown search provider: " + provider);
        } catch (WebSearchException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new WebSearchException("Search request failed (" + provider + "): " + failure.getMessage());
        }
    }

    private static List<WebSearchResult> searchTavilyKeyless(String query, int maxResults) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.addProperty("max_results", maxResults);
        String response = postJson(
            "https://api.tavily.com/search",
            body.toString(),
            new String[] { "Content-Type: application/json", "X-Tavily-Access-Mode: keyless" });
        return parseTavilyResults(response, maxResults);
    }

    private static List<WebSearchResult> searchTavily(String query, int maxResults, SearchRuntime runtime)
        throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("api_key", safeKey(runtime));
        body.addProperty("query", query);
        body.addProperty("max_results", maxResults);
        String response = postJson(
            "https://api.tavily.com/search",
            body.toString(),
            new String[] { "Content-Type: application/json" });
        return parseTavilyResults(response, maxResults);
    }

    private static List<WebSearchResult> parseTavilyResults(String response, int maxResults) {
        List<WebSearchResult> results = new ArrayList<>();
        JsonObject root = new JsonParser().parse(response)
            .getAsJsonObject();
        JsonArray items = root.getAsJsonArray("results");
        if (items == null) {
            return results;
        }
        for (JsonElement element : items) {
            if (results.size() >= maxResults) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            results.add(
                new WebSearchResult(
                    getJsonString(item, "title"),
                    getJsonString(item, "url"),
                    getJsonString(item, "content")));
        }
        return results;
    }

    private static List<WebSearchResult> searchDuckDuckGo(String query, int maxResults) throws IOException {
        String body = "q=" + urlEncode(query) + "&b=&kl=&df=";
        String html = postForm("https://html.duckduckgo.com/html/", body);
        return parseDuckDuckGoResults(html, maxResults);
    }

    /**
     * Parses DuckDuckGo markup using a monotonic literal scanner. This avoids regex backtracking on
     * malformed HTML while preserving the existing result and snippet pairing behavior.
     */
    static List<WebSearchResult> parseDuckDuckGoResults(String html, int maxResults) {
        List<WebSearchResult> results = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        int resultLimit = clampMaxResults(maxResults);
        if (html == null || html.isEmpty()) {
            return results;
        }
        int cursor = 0;
        while (urls.size() < resultLimit) {
            int marker = indexOfIgnoreCase(html, "class=\"result__a\"", cursor);
            if (marker < 0) {
                break;
            }
            int openEnd = html.indexOf('>', marker);
            if (openEnd < 0) {
                break;
            }
            int hrefStart = indexOfIgnoreCase(html, "href=\"", marker);
            if (hrefStart >= 0 && hrefStart < openEnd) {
                hrefStart += 6;
                int hrefEnd = html.indexOf('"', hrefStart);
                if (hrefEnd >= 0 && hrefEnd < openEnd) {
                    int contentStart = openEnd + 1;
                    int contentEnd = indexOfIgnoreCase(html, "</a>", contentStart);
                    if (contentEnd >= 0) {
                        urls.add(decodeDuckDuckGoUrl(html.substring(hrefStart, hrefEnd)));
                        titles.add(stripHtml(html.substring(contentStart, contentEnd)));
                        cursor = contentEnd + 4;
                        continue;
                    }
                }
            }
            // Always advance past malformed markup so the same marker cannot be revisited.
            cursor = Math.max(marker + 1, openEnd + 1);
        }
        List<String> snippets = new ArrayList<>();
        cursor = 0;
        while (snippets.size() < resultLimit) {
            int marker = indexOfIgnoreCase(html, "class=\"result__snippet\"", cursor);
            if (marker < 0) {
                break;
            }
            int openEnd = html.indexOf('>', marker);
            if (openEnd < 0) {
                break;
            }
            int contentStart = openEnd + 1;
            int closeEnd = findSnippetEnd(html, contentStart);
            if (closeEnd >= 0) {
                snippets.add(stripHtml(html.substring(contentStart, closeEnd)));
                cursor = closeEnd + 4;
            } else {
                cursor = Math.max(marker + 1, openEnd + 1);
            }
        }
        for (int i = 0; i < urls.size(); i++) {
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            String title = i < titles.size() ? titles.get(i) : urls.get(i);
            results.add(new WebSearchResult(title, urls.get(i), snippet));
        }
        return results;
    }

    private static int findSnippetEnd(String html, int from) {
        for (int i = Math.max(0, from); i < html.length(); i++) {
            if (html.charAt(i) == '<') {
                if (matchesIgnoreCase(html, i, "</a>")) {
                    return i;
                }
                if (matchesIgnoreCase(html, i, "</td>")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indexOfIgnoreCase(String value, String needle, int from) {
        int limit = value.length() - needle.length();
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (matchesIgnoreCase(value, i, needle)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesIgnoreCase(String value, int offset, String needle) {
        return offset >= 0 && offset + needle.length() <= value.length()
            && value.regionMatches(true, offset, needle, 0, needle.length());
    }

    private static List<WebSearchResult> searchBrave(String query, int maxResults, SearchRuntime runtime)
        throws IOException {
        String url = "https://api.search.brave.com/res/v1/web/search?q=" + urlEncode(query) + "&count=" + maxResults;
        String response = getJson(
            url,
            new String[] { "Accept: application/json", "X-Subscription-Token: " + safeKey(runtime) });
        List<WebSearchResult> results = new ArrayList<WebSearchResult>();
        JsonObject root = new JsonParser().parse(response)
            .getAsJsonObject();
        JsonObject web = root.getAsJsonObject("web");
        if (web == null) {
            return results;
        }
        JsonArray items = web.getAsJsonArray("results");
        if (items == null) {
            return results;
        }
        for (JsonElement element : items) {
            if (results.size() >= maxResults) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            results.add(
                new WebSearchResult(
                    getJsonString(item, "title"),
                    getJsonString(item, "url"),
                    getJsonString(item, "description")));
        }
        return results;
    }

    private static List<WebSearchResult> searchSerper(String query, int maxResults, SearchRuntime runtime)
        throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("q", query);
        body.addProperty("num", maxResults);
        String response = postJson(
            "https://google.serper.dev/search",
            body.toString(),
            new String[] { "Content-Type: application/json", "X-API-KEY: " + safeKey(runtime) });
        List<WebSearchResult> results = new ArrayList<WebSearchResult>();
        JsonObject root = new JsonParser().parse(response)
            .getAsJsonObject();
        JsonArray organic = root.getAsJsonArray("organic");
        if (organic == null) {
            return results;
        }
        for (JsonElement element : organic) {
            if (results.size() >= maxResults) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            results.add(
                new WebSearchResult(
                    getJsonString(item, "title"),
                    getJsonString(item, "link"),
                    getJsonString(item, "snippet")));
        }
        return results;
    }

    private static List<WebSearchResult> searchSearxng(String query, int maxResults, SearchRuntime runtime)
        throws IOException {
        String baseUrl = trimTrailingSlash(safeBaseUrl(runtime));
        String url = baseUrl + "/search?q=" + urlEncode(query) + "&format=json";
        String response = getJson(url, new String[] { "Accept: application/json" });
        List<WebSearchResult> results = new ArrayList<WebSearchResult>();
        JsonObject root = new JsonParser().parse(response)
            .getAsJsonObject();
        JsonArray items = root.getAsJsonArray("results");
        if (items == null) {
            return results;
        }
        for (JsonElement element : items) {
            if (results.size() >= maxResults) {
                break;
            }
            JsonObject item = element.getAsJsonObject();
            results.add(
                new WebSearchResult(
                    getJsonString(item, "title"),
                    getJsonString(item, "url"),
                    getJsonString(item, "content")));
        }
        return results;
    }

    private static String formatContext(List<WebSearchResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下是与用户问题相关的网页检索结果，请优先依据这些内容回答，并在回答中引用编号来源：\n");
        int index = 1;
        for (WebSearchResult result : results) {
            builder.append("\n[")
                .append(index++)
                .append("] ")
                .append(result.title.isEmpty() ? result.url : result.title);
            if (!result.url.isEmpty()) {
                builder.append("\n链接: ")
                    .append(result.url);
            }
            if (!result.snippet.isEmpty()) {
                builder.append("\n摘要: ")
                    .append(result.snippet);
            }
        }
        return builder.toString();
    }

    private static String postJson(String endpoint, String body, String[] headers) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "POST");
        try {
            for (String header : headers) {
                applyHeader(connection, header);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String postForm(String endpoint, String body) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "POST");
        try {
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (compatible; AdvanceDataMonitor/1.0; +https://github.com/)");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String getJson(String endpoint, String[] headers) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "GET");
        try {
            for (String header : headers) {
                applyHeader(connection, header);
            }
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String endpoint, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        int timeout = Math.max(5, Config.aiTimeoutSeconds) * 1000;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod(method);
        connection.setDoInput(true);
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
        }
        connection
            .setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; AdvanceDataMonitor/1.0; +https://github.com/)");
        return connection;
    }

    private static void applyHeader(HttpURLConnection connection, String header) {
        int split = header.indexOf(':');
        if (split <= 0) {
            return;
        }
        connection.setRequestProperty(
            header.substring(0, split)
                .trim(),
            header.substring(split + 1)
                .trim());
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        InputStream stream = responseCode >= 200 && responseCode < 300 ? connection.getInputStream()
            : connection.getErrorStream();
        String response = readStream(stream);
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("HTTP " + responseCode);
        }
        return response;
    }

    static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        try (InputStream input = stream) {
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_RESPONSE_BYTES - total) {
                    throw new IOException("HTTP response exceeds maximum size.");
                }
                output.write(buffer, 0, count);
                total += count;
            }
        }
        // The previous implementation used BufferedReader.readLine(), which deliberately
        // removed line terminators from provider responses. Preserve that public behavior
        // after decoding the bounded byte buffer so JSON/HTML parsing remains unchanged.
        String decoded = new String(output.toByteArray(), StandardCharsets.UTF_8);
        StringBuilder withoutLineTerminators = new StringBuilder(decoded.length());
        for (int i = 0; i < decoded.length(); i++) {
            char current = decoded.charAt(i);
            if (current != '\r' && current != '\n') {
                withoutLineTerminators.append(current);
            }
        }
        return withoutLineTerminators.toString();
    }

    private static String getJsonString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String safeKey(SearchRuntime runtime) {
        return runtime == null || runtime.apiKey == null ? "" : runtime.apiKey.trim();
    }

    private static String safeBaseUrl(SearchRuntime runtime) {
        return runtime == null || runtime.baseUrl == null ? "" : runtime.baseUrl.trim();
    }

    private static int clampMaxResults(int value) {
        if (value < 1) {
            return 1;
        }
        if (value > 10) {
            return 10;
        }
        return value;
    }

    /** Explicit search settings so WebAE can use shared encrypted keys without mutating client Config. */
    public static final class SearchRuntime {

        public String mode = PROVIDER_AUTO;
        public String apiKey = "";
        public String baseUrl = "";
        public int maxResults = 5;
        public boolean fallback = true;

        public static SearchRuntime fromClientConfig() {
            SearchRuntime runtime = new SearchRuntime();
            runtime.mode = Config.aiWebSearchMode;
            runtime.apiKey = Config.getAiSearchApiKey();
            runtime.baseUrl = Config.aiSearchBaseUrl;
            runtime.maxResults = Config.aiSearchMaxResults;
            runtime.fallback = Config.aiSearchFallback;
            return runtime;
        }
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException failure) {
            return value;
        }
    }

    private static String decodeDuckDuckGoUrl(String url) {
        if (url == null) {
            return "";
        }
        String decoded = url.trim();
        if (decoded.startsWith("//")) {
            decoded = "https:" + decoded;
        }
        int uddgIndex = decoded.indexOf("uddg=");
        if (uddgIndex >= 0) {
            String encoded = decoded.substring(uddgIndex + 5);
            int amp = encoded.indexOf('&');
            if (amp >= 0) {
                encoded = encoded.substring(0, amp);
            }
            try {
                return URLDecoder.decode(encoded, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
                return decoded;
            }
        }
        return decoded;
    }

    private static String stripHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(value.length());
        int tagStart = -1;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (tagStart >= 0) {
                if (current == '>') {
                    tagStart = -1;
                }
                continue;
            }
            if (current == '<') {
                tagStart = i;
                continue;
            }
            text.append(current);
        }
        if (tagStart >= 0) {
            // Keep an unmatched '<' sequence, matching the old <[^>]+> behavior.
            text.append(value, tagStart, value.length());
        }
        StringBuilder decoded = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                if (text.indexOf("&amp;", i) == i) {
                    decoded.append('&');
                    i += 4;
                    continue;
                }
                if (text.indexOf("&quot;", i) == i) {
                    decoded.append('"');
                    i += 5;
                    continue;
                }
                if (text.indexOf("&#39;", i) == i) {
                    decoded.append('\'');
                    i += 4;
                    continue;
                }
                if (text.indexOf("&lt;", i) == i) {
                    decoded.append('<');
                    i += 3;
                    continue;
                }
                if (text.indexOf("&gt;", i) == i) {
                    decoded.append('>');
                    i += 3;
                    continue;
                }
            }
            decoded.append(text.charAt(i));
        }
        return decoded.toString()
            .trim();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void debug(String message) {
        if (Config.aiDebugLogging) {
            AdvanceDataMonitor.LOG.info("[AI Search] " + message);
        }
    }

    public static final class WebSearchException extends Exception {

        public WebSearchException(String message) {
            super(message);
        }
    }
}
