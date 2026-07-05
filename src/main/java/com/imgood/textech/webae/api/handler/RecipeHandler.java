package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.recipe.RecipeCacheStore;
import com.imgood.textech.webae.recipe.RecipeCacheStore.BrowseResult;
import com.imgood.textech.webae.recipe.RecipeCacheStore.CacheStatus;
import com.imgood.textech.webae.recipe.RecipeCacheStore.HandlerInfo;
import com.imgood.textech.webae.recipe.RecipeCacheStore.ItemSuggest;
import com.imgood.textech.webae.recipe.RecipeCacheStore.QuerySearchResult;
import com.imgood.textech.webae.search.RecipeSearchRateLimiter;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for recipe cache queries.
 */
public class RecipeHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    public static NanoHTTPD.Response handle(String uri, java.util.Map<String, String> params, String playerUuid) {
        if ("/api/recipes/handlers".equals(uri)) {
            return handleHandlers();
        }
        if ("/api/recipes/status".equals(uri)) {
            return handleStatus();
        }
        if ("/api/recipes/browse".equals(uri)) {
            return handleBrowse(params);
        }
        if ("/api/recipes/suggest".equals(uri)) {
            return handleSuggest(params);
        }
        if (uri.startsWith("/api/recipes/search")) {
            if (params.containsKey("q") && params.get("q") != null
                && !params.get("q")
                    .isEmpty()) {
                return handleQuerySearch(params, playerUuid);
            }
            return handleSearch(params);
        }
        if (uri.startsWith("/api/recipes/") && countPathSegments(uri) == 4) {
            return handleGetRecipe(uri);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown recipe endpoint: " + uri + "\"}");
    }

    private static NanoHTTPD.Response handleHandlers() {
        List<HandlerInfo> handlers = RecipeCacheStore.instance()
            .listHandlers();
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"handlers\":" + GSON.toJson(handlers) + "}");
    }

    private static NanoHTTPD.Response handleStatus() {
        CacheStatus status = RecipeCacheStore.instance()
            .getStatus();
        return jsonResponse(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"status\":" + GSON.toJson(status) + "}");
    }

    private static NanoHTTPD.Response handleBrowse(java.util.Map<String, String> params) {
        List<String> handlers = parseHandlersParam(params);
        String handler = handlers.isEmpty() ? params.get("handler") : joinHandlers(handlers);
        if (handler == null || handler.isEmpty()) {
            handler = "all";
        }
        int offset = parseIntParam(params.get("offset"), 0);
        int limit = parseIntParam(params.get("limit"), 50);
        BrowseResult browse = RecipeCacheStore.instance()
            .browseByHandlers(handlers.isEmpty() ? null : handlers, offset, limit);
        if (handlers.isEmpty()) {
            browse = RecipeCacheStore.instance()
                .browseByHandler(handler, offset, limit);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"results\":" + GSON.toJson(browse.results)
                + ",\"count\":"
                + browse.results.size()
                + ",\"total\":"
                + browse.total
                + ",\"offset\":"
                + browse.offset
                + ",\"limit\":"
                + browse.limit
                + "}");
    }

    private static NanoHTTPD.Response handleSuggest(java.util.Map<String, String> params) {
        String q = params.get("q");
        if (q == null || q.isEmpty()) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'q' parameter\"}");
        }
        int limit = parseIntParam(params.get("limit"), 20);
        List<ItemSuggest> suggestions = RecipeCacheStore.instance()
            .suggestItems(q, limit);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"suggestions\":" + GSON.toJson(suggestions) + ",\"count\":" + suggestions.size() + "}");
    }

    private static NanoHTTPD.Response handleQuerySearch(java.util.Map<String, String> params, String ownerUuid) {
        if (!RecipeSearchRateLimiter.tryAcquire(ownerUuid)) {
            long wait = RecipeSearchRateLimiter.remainingCooldownMs(ownerUuid);
            return jsonResponse(
                NanoHTTPD.Response.Status.TOO_MANY_REQUESTS,
                "{\"success\":false,\"message\":\"Rate limited\",\"retryAfterMs\":" + wait + "}");
        }
        String q = params.get("q");
        String handler = params.get("handler");
        List<String> handlers = parseHandlersParam(params);
        String scope = params.get("scope");
        if (scope == null || scope.isEmpty()) {
            scope = "all";
        }
        int offset = parseIntParam(params.get("offset"), 0);
        int limit = parseIntParam(params.get("limit"), 100);
        QuerySearchResult result;
        if (!handlers.isEmpty()) {
            result = RecipeCacheStore.instance()
                .searchByQuery(q, handler, handlers, scope, offset, limit);
        } else {
            result = RecipeCacheStore.instance()
                .searchByQuery(q, handler, scope, offset, limit);
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"results\":" + GSON.toJson(result.results)
                + ",\"count\":"
                + result.results.size()
                + ",\"total\":"
                + result.total
                + ",\"offset\":"
                + result.offset
                + ",\"limit\":"
                + result.limit
                + "}");
    }

    private static NanoHTTPD.Response handleSearch(java.util.Map<String, String> params) {
        String output = params.get("output");
        String input = params.get("input");
        if ((output == null || output.isEmpty()) && (input == null || input.isEmpty())) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'output', 'input', or 'q' parameter\"}");
        }
        String handler = params.get("handler");
        List<String> handlers = parseHandlersParam(params);
        boolean searchOutput = output != null && !output.isEmpty();
        String term = searchOutput ? output : input;
        List<RecipeDto> results;
        if (searchOutput) {
            results = RecipeCacheStore.instance()
                .searchByOutput(term, handler);
        } else {
            results = RecipeCacheStore.instance()
                .searchByInput(term, handler);
        }
        if (!handlers.isEmpty()) {
            List<RecipeDto> filtered = new ArrayList<RecipeDto>();
            for (RecipeDto dto : results) {
                if (handlers.contains(dto.handlerId)) filtered.add(dto);
            }
            results = filtered;
        }
        if (results.isEmpty()) {
            String scope = searchOutput ? "output" : "input";
            QuerySearchResult fuzzy;
            if (!handlers.isEmpty()) {
                fuzzy = RecipeCacheStore.instance()
                    .searchByQuery(term, handler, handlers, scope, 0, 200);
            } else {
                fuzzy = RecipeCacheStore.instance()
                    .searchByQuery(term, handler, scope, 0, 200);
            }
            results = fuzzy.results;
        }
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"results\":" + GSON
                .toJson(results) + ",\"count\":" + results.size() + ",\"total\":" + results.size() + "}");
    }

    private static List<String> parseHandlersParam(java.util.Map<String, String> params) {
        List<String> out = new ArrayList<String>();
        String handlers = params.get("handlers");
        if (handlers == null || handlers.isEmpty()) return out;
        for (String part : handlers.split(",")) {
            if (part != null && !part.isEmpty() && !"all".equals(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static String joinHandlers(List<String> handlers) {
        if (handlers == null || handlers.isEmpty()) return "all";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < handlers.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(handlers.get(i));
        }
        return sb.toString();
    }

    private static NanoHTTPD.Response handleGetRecipe(String uri) {
        String[] parts = uri.split("/");
        if (parts.length < 5) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid recipe path\"}");
        }
        String handlerId = parts[3];
        int recipeIndex;
        try {
            recipeIndex = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid recipe index\"}");
        }
        RecipeDto recipe = RecipeCacheStore.instance()
            .getRecipe(handlerId, recipeIndex);
        if (recipe == null) {
            return jsonResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "{\"success\":false,\"message\":\"Recipe not found\"}");
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"recipe\":" + GSON.toJson(recipe) + "}");
    }

    private static int parseIntParam(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int countPathSegments(String uri) {
        int count = 0;
        for (int i = 0; i < uri.length(); i++) {
            if (uri.charAt(i) == '/') count++;
        }
        return count;
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}