package com.imgood.textech.webae.api.handler;

import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.dto.SearchResultDto;
import com.imgood.textech.webae.search.WebSearchRateLimiter;
import com.imgood.textech.webae.search.WebSearchService;
import com.imgood.textech.webae.search.WebSearchService.SearchResponse;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/search — aggregated read-only search across storage snapshots, recipes, GT machines, and patterns.
 *
 * Query params:
 * <ul>
 * <li>{@code q} — required search text (min 1 non-space char)</li>
 * <li>{@code limit} — page size (default 20, max 50)</li>
 * <li>{@code offset} — global offset across merged results (default 0)</li>
 * <li>{@code types} — comma-separated: storage, recipe, gt, pattern (default all)</li>
 * <li>{@code network} — optional single network id filter</li>
 * </ul>
 */
public final class SearchHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private SearchHandler() {}

    public static NanoHTTPD.Response handle(Map<String, String> params, String ownerUuid) {
        if (!WebSearchRateLimiter.tryAcquire(ownerUuid)) {
            long cooldown = WebSearchRateLimiter.remainingCooldownMs(ownerUuid);
            return json(
                NanoHTTPD.Response.Status.TOO_MANY_REQUESTS,
                "{\"success\":false,\"code\":\"rate_limited\",\"message\":\"Search rate limit exceeded\",\"cooldownMs\":"
                    + cooldown
                    + "}");
        }

        String q = params.get("q");
        if (q == null || q.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or empty 'q' parameter\"}");
        }

        int offset = parseIntParam(params.get("offset"), 0);
        int limit = WebSearchService.clampLimit(parseIntParam(params.get("limit"), WebSearchService.DEFAULT_LIMIT));
        Set<String> types = WebSearchService.parseTypes(params.get("types"));
        Integer networkFilter = parseNetwork(params.get("network"));

        SearchResponse response = WebSearchService.search(ownerUuid, q, offset, limit, types, networkFilter);
        if (response == null) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"No search result\"}");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true");
        sb.append(",\"query\":")
            .append(GSON.toJson(response.query));
        sb.append(",\"offset\":")
            .append(response.offset);
        sb.append(",\"limit\":")
            .append(response.limit);
        sb.append(",\"total\":")
            .append(response.total);
        sb.append(",\"results\":")
            .append(GSON.toJson(response.results != null ? response.results : new SearchResultDto[0]));
        sb.append(",\"countsByType\":")
            .append(GSON.toJson(response.countsByType));
        sb.append("}");
        return json(NanoHTTPD.Response.Status.OK, sb.toString());
    }

    private static Integer parseNetwork(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
