package com.imgood.textech.webae.api.handler;

import java.io.ByteArrayInputStream;
import java.util.Map;

import com.imgood.textech.Config;
import com.imgood.textech.webae.topology.AeCableTextureLoader;

import fi.iki.elonen.NanoHTTPD;

/**
 * GET /api/ae2/cable-texture?type=smart|covered|dense — AE2 default Fluix cable PNGs
 * from classpath (topology simulated cable tiles; independent of icon pack cache).
 *
 * @deprecated Cable simulation is off by default ({@code topologySimulatedEnabled=false}).
 */
@Deprecated
public final class AeCableTextureHandler {

    private AeCableTextureHandler() {}

    public static NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.GET) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "application/json",
                "{\"success\":false,\"message\":\"Use GET\"}");
        }
        if (!Config.webTopologyEnabled || !Config.webTopologySimulatedEnabled) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                "application/json",
                "{\"success\":false,\"message\":\"Topology cable simulation is disabled\",\"code\":\"topology_simulated_disabled\"}");
        }
        Map<String, String> params = session.getParms();
        String type = params.get("type");
        byte[] png = AeCableTextureLoader.loadDefaultPng(type);
        if (png == null || png.length == 0) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain",
                "404 AE2 cable texture not found");
        }
        String normalized = AeCableTextureLoader.normalizeType(type);
        NanoHTTPD.Response resp = NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "image/png",
            new ByteArrayInputStream(png),
            png.length);
        resp.addHeader("Cache-Control", "max-age=86400");
        resp.addHeader("ETag", "\"ae2-cable-" + normalized + "-" + png.length + "\"");
        resp.addHeader("X-Ae-Cable-Type", normalized);
        resp.addHeader("X-Ae-Cable-Texture", AeCableTextureLoader.textureFileFor(normalized));
        return resp;
    }
}
