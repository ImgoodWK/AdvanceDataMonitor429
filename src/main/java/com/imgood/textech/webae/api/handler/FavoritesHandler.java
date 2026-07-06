package com.imgood.textech.webae.api.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.webae.favorites.WebFavoritesStore;
import com.imgood.textech.webae.favorites.WebFavoritesStore.OwnerFavorites;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/favorites — list favorites for the authenticated owner.
 * PUT /api/favorites — replace favorites (owner only).
 */
public final class FavoritesHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private FavoritesHandler() {}

    public static NanoHTTPD.Response handleGet(String ownerUuid) {
        OwnerFavorites fav = WebFavoritesStore.instance()
            .getForOwner(ownerUuid);
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"favorites\":" + GSON.toJson(fav) + "}");
    }

    public static NanoHTTPD.Response handlePut(String body, String ownerUuid) {
        if (body == null || body.trim()
            .isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing request body\"}");
        }
        PutBody incoming;
        try {
            incoming = GSON.fromJson(body, PutBody.class);
        } catch (Exception e) {
            return json(NanoHTTPD.Response.Status.BAD_REQUEST, "{\"success\":false,\"message\":\"Invalid JSON body\"}");
        }
        OwnerFavorites fav = incoming != null && incoming.favorites != null ? incoming.favorites : new OwnerFavorites();
        boolean ok = WebFavoritesStore.instance()
            .saveForOwner(ownerUuid, fav);
        if (!ok) {
            return json(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "{\"success\":false,\"message\":\"Failed to save favorites\"}");
        }
        return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"favorites\":" + GSON.toJson(fav) + "}");
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class PutBody {

        public OwnerFavorites favorites;
    }
}
