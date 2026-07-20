package com.imgood.textech.webae.assistant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.ai.WebSearchData;
import com.imgood.textech.assistant.ai.WebSearchService;
import com.imgood.textech.assistant.ai.WebSearchService.SearchRuntime;
import com.imgood.textech.webae.assistant.WebAiConfigStore.RuntimeConfig;
import com.imgood.textech.webae.assistant.WebAiHttpClient.Message;

/**
 * Shared WebAE AI completion with ordered profile failover (provider-side errors only)
 * and optional server-side web-search injection.
 */
public final class WebAiCompletionService {

    private WebAiCompletionService() {}

    public static CompletionResult completeWithFailover(String systemPrompt, String userPrompt) throws IOException {
        List<Message> messages = new ArrayList<Message>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt == null ? "" : userPrompt));
        return completeWithFailover(messages);
    }

    public static CompletionResult completeWithFailover(List<Message> messages) throws IOException {
        List<RuntimeConfig> runtimes = WebAiConfigStore.instance()
            .runtimes();
        if (runtimes.isEmpty()) {
            throw new IllegalStateException("Web AI is not configured for server-side use.");
        }
        IOException lastFailure = null;
        for (int i = 0; i < runtimes.size(); i++) {
            RuntimeConfig runtime = runtimes.get(i);
            try {
                String content = new WebAiHttpClient(runtime).complete(copyMessages(messages));
                CompletionResult result = new CompletionResult();
                result.content = content;
                result.providerId = runtime.providerId;
                result.model = runtime.model;
                result.profileId = runtime.id;
                result.profileName = runtime.name;
                result.attempted = i + 1;
                return result;
            } catch (IOException failure) {
                lastFailure = failure;
                AdvanceDataMonitor.LOG.warn(
                    "[WebAE] AI profile {} failed ({}/{}): {}",
                    runtime.name,
                    Integer.valueOf(i + 1),
                    Integer.valueOf(runtimes.size()),
                    failure.getMessage());
                if (!isProviderSideFailure(failure) || i + 1 >= runtimes.size()) {
                    throw failure;
                }
            } catch (IllegalArgumentException failure) {
                lastFailure = new IOException(failure.getMessage(), failure);
                if (i + 1 >= runtimes.size()) throw lastFailure;
            }
        }
        throw lastFailure == null ? new IOException("All AI profiles failed.") : lastFailure;
    }

    /**
     * Optionally runs shared web search and injects context into the last user message.
     * Search failures are soft: the original messages are returned unchanged.
     */
    public static SearchAugmentResult maybeAugmentWithSearch(List<Message> messages, String searchQuery) {
        SearchAugmentResult result = new SearchAugmentResult();
        result.messages = copyMessages(messages);
        if (!WebAiConfigStore.instance()
            .isSearchEnabled()) {
            return result;
        }
        String query = searchQuery == null ? "" : searchQuery.trim();
        if (query.isEmpty()) query = lastUserText(messages);
        if (query.isEmpty()) return result;
        try {
            SearchRuntime runtime = WebAiConfigStore.instance()
                .searchRuntime();
            WebSearchData data = WebSearchService.performWebSearch(query, runtime);
            result.messages = injectIntoMessages(result.messages, data);
            result.searchUsed = true;
            result.searchProvider = data.provider;
            result.searchContext = data.context;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Shared web search skipped: {}", e.getMessage());
            result.searchError = e.getMessage() == null ? "search_failed" : e.getMessage();
        }
        return result;
    }

    public static String maybeAugmentUserPrompt(String userPrompt, String searchQuery) {
        if (!WebAiConfigStore.instance()
            .isSearchEnabled()) return userPrompt == null ? "" : userPrompt;
        String query = searchQuery == null || searchQuery.trim()
            .isEmpty() ? (userPrompt == null ? "" : userPrompt) : searchQuery.trim();
        if (query.isEmpty()) return userPrompt == null ? "" : userPrompt;
        try {
            SearchRuntime runtime = WebAiConfigStore.instance()
                .searchRuntime();
            WebSearchData data = WebSearchService.performWebSearch(query, runtime);
            return WebSearchService.injectSearchIntoUserText(userPrompt, data);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Shared web search skipped: {}", e.getMessage());
            return userPrompt == null ? "" : userPrompt;
        }
    }

    public static boolean isProviderSideFailure(Throwable failure) {
        if (failure == null) return false;
        String message = failure.getMessage() == null ? ""
            : failure.getMessage()
                .toLowerCase();
        if (message.contains("timeout") || message.contains("timed out")
            || message.contains("connection")
            || message.contains("unreachable")
            || message.contains("refused")) {
            return true;
        }
        if (message.contains("http 401") || message.contains("http 403")
            || message.contains("http 429")
            || message.contains("http 500")
            || message.contains("http 502")
            || message.contains("http 503")
            || message.contains("http 504")) {
            return true;
        }
        if (message.contains("quota") || message.contains("rate limit")
            || message.contains("insufficient")
            || message.contains("billing")
            || message.contains("balance")
            || message.contains("credit")
            || message.contains("exceeded")) {
            return true;
        }
        if (message.contains("empty") || message.contains("did not contain") || message.contains("invalid json")) {
            return true;
        }
        // Generic IOException without parse-specific wording → treat as provider-side.
        return failure instanceof IOException;
    }

    private static List<Message> injectIntoMessages(List<Message> messages, WebSearchData data) {
        List<Message> copy = copyMessages(messages);
        for (int i = copy.size() - 1; i >= 0; i--) {
            if ("user".equals(copy.get(i).role)) {
                copy.set(i, new Message("user", WebSearchService.injectSearchIntoUserText(copy.get(i).content, data)));
                break;
            }
        }
        return copy;
    }

    private static String lastUserText(List<Message> messages) {
        if (messages == null) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role)) return messages.get(i).content;
        }
        return "";
    }

    private static List<Message> copyMessages(List<Message> messages) {
        List<Message> copy = new ArrayList<Message>();
        if (messages == null) return copy;
        for (Message message : messages) {
            if (message == null) continue;
            copy.add(new Message(message.role, message.content));
        }
        return copy;
    }

    public static final class CompletionResult {

        public String content;
        public String providerId;
        public String model;
        public String profileId;
        public String profileName;
        public int attempted;
    }

    public static final class SearchAugmentResult {

        public List<Message> messages;
        public boolean searchUsed;
        public String searchProvider = "";
        public String searchContext = "";
        public String searchError = "";
    }
}
