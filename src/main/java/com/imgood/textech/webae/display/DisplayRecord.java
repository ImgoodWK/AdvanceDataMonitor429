package com.imgood.textech.webae.display;

import com.google.gson.JsonObject;

/**
 * Persisted WebAE display document: dashboard layout + settings for live embed / capture.
 * Secrets (tokens, AI keys, webhooks) must never be stored in {@link #layout}.
 */
public final class DisplayRecord {

    public String id;
    public String viewToken;
    public String ownerUuid;
    public String title;
    public long createdAt;
    public long updatedAt;
    public int viewportWidth = 960;
    public int viewportHeight = 720;
    /** Sanitized dashboard settings JSON (widgets + visual settings). */
    public JsonObject layout;

    public DisplayRecord() {}
}
