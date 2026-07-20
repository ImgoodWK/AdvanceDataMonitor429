package com.imgood.textech.webae.api.handler;

import com.imgood.textech.webae.WebUiDefaultsStore;
import com.imgood.textech.webae.WebUiDefaultsStore.LoadedDefaults;
import com.imgood.textech.webae.WebUiDefaultsStore.Source;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/ui-defaults — returns pack/mod default WebAE UI settings JSON for
 * first-time browser visitors.
 */
public final class WebUiDefaultsHandler {

    private WebUiDefaultsHandler() {}

    public static NanoHTTPD.Response handleGet() {
        LoadedDefaults loaded = WebUiDefaultsStore.load();
        if (loaded.json == null) {
            return json(NanoHTTPD.Response.Status.OK, "{\"success\":true,\"defaults\":null,\"source\":null}");
        }
        String source = loaded.source == Source.INSTANCE ? "instance" : "jar";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"source\":\"")
            .append(source)
            .append("\",\"defaults\":");
        sb.append(loaded.json);
        sb.append('}');
        return json(NanoHTTPD.Response.Status.OK, sb.toString());
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }
}
