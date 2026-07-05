package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.Config;
import com.imgood.textech.webae.icon.IconRenderMode;

import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the public client-readable configuration endpoint.
 *
 * GET /api/config — returns refreshIntervalMs / gtRefreshIntervalMs /
 * maxNetworksDisplayed / tokenLifetimeHours /
 * themePresets (legacy, mirrors themeColors) /
 * themeColors (Phase 3.2: 24 schemes, Phase 8 +5 sci-fi) /
 * themeLayouts (Phase 2.1: 5 presets) /
 * debugFlags (Phase 3.3: per-feature server debug switches, read-only).
 *
 * Although the endpoint is "public" (no OP requirement), it still goes through
 * the standard Bearer auth so only logged-in users can read it.
 */
public class WebConfigHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    public static NanoHTTPD.Response handle(String uri, java.util.Map<String, String> params, String playerUuid) {
        if ("/api/config".equals(uri)) {
            return handleConfig();
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "application/json",
            "{\"success\":false,\"message\":\"Unknown config endpoint\"}");
    }

    private static NanoHTTPD.Response handleConfig() {
        List<String> themeColors = new ArrayList<String>();
        themeColors.add("dark");
        themeColors.add("light");
        themeColors.add("nord");
        themeColors.add("solarized");
        themeColors.add("gtnh-blue");
        themeColors.add("midnight");
        themeColors.add("dracula");
        themeColors.add("cyberpunk");
        themeColors.add("aurora");
        themeColors.add("matrix");
        themeColors.add("sunset");
        themeColors.add("ocean");
        themeColors.add("forest");
        themeColors.add("lavender");
        themeColors.add("rose");
        themeColors.add("carbon");
        themeColors.add("tokyonight");
        themeColors.add("catppuccin-mocha");
        themeColors.add("gruvbox");
        themeColors.add("hologram");
        themeColors.add("plasma");
        themeColors.add("neon-pulse");
        themeColors.add("quantum");
        themeColors.add("crystal");
        List<String> themeLayouts = new ArrayList<String>();
        themeLayouts.add("standard");
        themeLayouts.add("compact");
        themeLayouts.add("wide");
        themeLayouts.add("sidebar-right");
        themeLayouts.add("topnav");
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"config\":{");
        sb.append("\"refreshIntervalMs\":")
            .append(Config.webRefreshIntervalMs)
            .append(',');
        sb.append("\"gtRefreshIntervalMs\":")
            .append(Config.webGtRefreshIntervalMs)
            .append(',');
        sb.append("\"maxNetworksDisplayed\":")
            .append(Config.webMaxNetworksDisplayed)
            .append(',');
        sb.append("\"tokenLifetimeHours\":")
            .append(Config.webTokenLifetimeHours)
            .append(',');
        // themePresets kept for backward compat (now mirrors themeColors)
        sb.append("\"themePresets\":")
            .append(GSON.toJson(themeColors))
            .append(',');
        sb.append("\"themeColors\":")
            .append(GSON.toJson(themeColors))
            .append(',');
        sb.append("\"themeLayouts\":")
            .append(GSON.toJson(themeLayouts))
            .append(',');
        sb.append("\"iconCacheEnabled\":")
            .append(Config.webIconCacheEnabled)
            .append(',');
        sb.append("\"iconUploadEnabled\":")
            .append(Config.webIconUploadEnabled)
            .append(',');
        sb.append("\"iconPackEnabled\":")
            .append(Config.webIconPackEnabled)
            .append(',');
        sb.append("\"iconRenderModes\":[");
        appendIconRenderModes(sb);
        sb.append("],");
        sb.append("\"iconRenderPerTick\":")
            .append(Config.webIconRenderPerTick)
            .append(',');
        sb.append("\"iconRenderPerTickAll\":")
            .append(Config.webIconRenderPerTickAll)
            .append(',');
        // Per-feature debug flag mirror (read-only display in Settings); gates logs/textech/webae-<feature>.log
        sb.append("\"debugFlags\":{");
        sb.append("\"icons\":")
            .append(Config.webDebugIcons)
            .append(',');
        sb.append("\"chat\":")
            .append(Config.webDebugChat)
            .append(',');
        sb.append("\"dashboard\":")
            .append(Config.webDebugDashboard)
            .append(',');
        sb.append("\"synthesis\":")
            .append(Config.webDebugSynthesis)
            .append(',');
        sb.append("\"patterns\":")
            .append(Config.webDebugPatterns);
        sb.append("},");
        sb.append("\"topologyEnabled\":")
            .append(Config.webTopologyEnabled)
            .append(',');
        sb.append("\"topologyCacheTtlMs\":")
            .append(Config.webTopologyCacheTtlMs)
            .append(',');
        sb.append("\"alertsEnabled\":")
            .append(com.imgood.textech.config.ConfigWebAlertsLoader.get().enabled)
            .append(',');
        sb.append("\"alertsPollIntervalSeconds\":")
            .append(com.imgood.textech.config.ConfigWebAlertsLoader.get().pollIntervalSeconds);
        sb.append("}}");
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", sb.toString());
    }

    private static void appendIconRenderModes(StringBuilder sb) {
        boolean first = true;
        for (IconRenderMode mode : IconRenderMode.exportModes()) {
            if (!first) sb.append(',');
            first = false;
            appendModeJson(sb, mode, true);
        }
        for (IconRenderMode mode : IconRenderMode.allModes()) {
            if (mode.isImplemented()) continue;
            if (!first) sb.append(',');
            first = false;
            appendModeJson(sb, mode, false);
        }
    }

    private static void appendModeJson(StringBuilder sb, IconRenderMode mode, boolean implemented) {
        sb.append("{\"id\":")
            .append(GSON.toJson(mode.getId()))
            .append(",\"labelKey\":")
            .append(GSON.toJson(mode.getLabelKey()))
            .append(",\"descriptionKey\":")
            .append(GSON.toJson(mode.getTooltipKey()))
            .append(",\"implemented\":")
            .append(implemented)
            .append('}');
    }
}
