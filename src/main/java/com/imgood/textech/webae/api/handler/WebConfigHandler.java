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
 * themeColors (classic + Phase 8 + composition companions + bold batch) /
 * themeLayouts (8 presets: standard/compact/wide/sidebar-right/topnav/bottomnav/floating/split-chrome) /
 * pageStyles (7 chrome + 12 + 20 bold + 20 batch2 composition styles) /
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
        themeColors.add("rhodes-ink");
        themeColors.add("yorha-black");
        themeColors.add("brutal-poster");
        themeColors.add("ink-paper");
        themeColors.add("reactor-cyan");
        themeColors.add("win-teal");
        themeColors.add("deck-magenta");
        themeColors.add("hex-amber");
        themeColors.add("vapor-dusk");
        themeColors.add("dmg-olive");
        themeColors.add("blueprint-navy");
        themeColors.add("arcade-pink");
        themeColors.add("stripe-indigo");
        themeColors.add("doom-steel");
        themeColors.add("sakura-mist");
        themeColors.add("pcb-green");
        themeColors.add("warp-void");
        themeColors.add("ember-crimson");
        themeColors.add("frost-ice");
        themeColors.add("noir-silver");
        themeColors.add("emerald-teal");
        themeColors.add("desert-sand");
        themeColors.add("lunar-grey");
        themeColors.add("reef-coral");
        themeColors.add("kraft-brown");
        themeColors.add("tokyo-neon");
        themeColors.add("parchment-gold");
        themeColors.add("bio-green");
        themeColors.add("stardust-violet");
        themeColors.add("copper-rust");
        themeColors.add("lab-white");
        themeColors.add("abyss-deep");
        themeColors.add("candy-pastel");
        themeColors.add("mil-olive");
        themeColors.add("crt-green");
        themeColors.add("clay-terra");
        themeColors.add("prism-spectrum");
        List<String> themeLayouts = new ArrayList<String>();
        themeLayouts.add("standard");
        themeLayouts.add("compact");
        themeLayouts.add("wide");
        themeLayouts.add("sidebar-right");
        themeLayouts.add("topnav");
        themeLayouts.add("bottomnav");
        themeLayouts.add("floating");
        themeLayouts.add("split-chrome");
        List<String> pageStyles = new ArrayList<String>();
        pageStyles.add("classic");
        pageStyles.add("linear");
        pageStyles.add("viz");
        pageStyles.add("glass");
        pageStyles.add("industrial");
        pageStyles.add("soft");
        pageStyles.add("terminal");
        pageStyles.add("rhodes");
        pageStyles.add("cupertino");
        pageStyles.add("vercel");
        pageStyles.add("grafana");
        pageStyles.add("swiss");
        pageStyles.add("yorha");
        pageStyles.add("hsr");
        pageStyles.add("bloomberg");
        pageStyles.add("raycast");
        pageStyles.add("brutal");
        pageStyles.add("steam");
        pageStyles.add("inkwash");
        pageStyles.add("arc-reactor");
        pageStyles.add("winclassic");
        pageStyles.add("cyberdeck");
        pageStyles.add("origami");
        pageStyles.add("hexcell");
        pageStyles.add("vaporwave");
        pageStyles.add("broadsheet");
        pageStyles.add("dmg");
        pageStyles.add("liquid");
        pageStyles.add("blueprint");
        pageStyles.add("screentone");
        pageStyles.add("arcade");
        pageStyles.add("bauhaus");
        pageStyles.add("obsidian");
        pageStyles.add("mesh");
        pageStyles.add("doomhud");
        pageStyles.add("sakura");
        pageStyles.add("pcb");
        pageStyles.add("polaroid");
        pageStyles.add("warp");
        pageStyles.add("emberforge");
        pageStyles.add("frostglass");
        pageStyles.add("noirfilm");
        pageStyles.add("emerald-circuit");
        pageStyles.add("desert-terminal");
        pageStyles.add("lunar");
        pageStyles.add("coral-reef");
        pageStyles.add("papercraft");
        pageStyles.add("neon-tokyo");
        pageStyles.add("medieval");
        pageStyles.add("biotank");
        pageStyles.add("stardust");
        pageStyles.add("coppersteam");
        pageStyles.add("cleanlab");
        pageStyles.add("abyss");
        pageStyles.add("candypop");
        pageStyles.add("military");
        pageStyles.add("retrocrit");
        pageStyles.add("terracotta");
        pageStyles.add("prism");
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
        sb.append("\"pageStyles\":")
            .append(GSON.toJson(pageStyles))
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
            .append(Config.webDebugPatterns)
            .append(',');
        sb.append("\"perf\":")
            .append(Config.webDebugPerf);
        sb.append("},");
        sb.append("\"topologyEnabled\":")
            .append(Config.webTopologyEnabled)
            .append(',');
        sb.append("\"topologyCacheTtlMs\":")
            .append(Config.webTopologyCacheTtlMs)
            .append(',');
        sb.append("\"worldMapSnapshotCooldownMs\":")
            .append(Config.worldMapSnapshotCooldownMs)
            .append(',');
        sb.append("\"topologySnapshotPersist\":")
            .append(Config.webTopologySnapshotPersist)
            .append(',');
        sb.append("\"topologySimulatedEnabled\":")
            .append(Config.webTopologySimulatedEnabled && Config.webTopologyEnabled)
            .append(',');
        sb.append("\"worldMapEnabled\":")
            .append(Config.webWorldMapEnabled && Config.webTopologyEnabled)
            .append(',');
        sb.append("\"worldMapMaxQualityTier\":")
            .append(GSON.toJson(Config.webWorldMapMaxQualityTier != null ? Config.webWorldMapMaxQualityTier : "ultra"))
            .append(',');
        sb.append("\"worldMapDefaultQualityTier\":")
            .append(
                GSON.toJson(
                    Config.webWorldMapDefaultQualityTier != null ? Config.webWorldMapDefaultQualityTier : "medium"))
            .append(',');
        sb.append("\"worldMapSnapshotMode\":")
            .append(GSON.toJson(com.imgood.textech.webae.worldmap.WorldMapSnapshotMode.normalized()))
            .append(',');
        sb.append("\"worldMapBrowserCacheEnabled\":")
            .append(Config.worldMapBrowserCacheEnabled)
            .append(',');
        sb.append("\"worldMapJourneyMapEnabled\":")
            .append(Config.worldMapJourneyMapEnabled)
            .append(',');
        sb.append("\"worldMapSnapshotSourcePriority\":")
            .append(GSON.toJson(Config.worldMapSnapshotSourcePriority != null ? Config.worldMapSnapshotSourcePriority : ""))
            .append(',');
        sb.append("\"alertsEnabled\":")
            .append(com.imgood.textech.config.ConfigWebAlertsLoader.get().enabled)
            .append(',');
        sb.append("\"alertsPollIntervalSeconds\":")
            .append(com.imgood.textech.config.ConfigWebAlertsLoader.get().pollIntervalSeconds)
            .append(',');
        sb.append("\"dynmapBaseUrl\":")
            .append(GSON.toJson(Config.webDynmapBaseUrl != null ? Config.webDynmapBaseUrl : ""))
            .append(',');
        sb.append("\"questEnabled\":")
            .append(Config.webQuestEnabled && com.imgood.textech.compat.bq.BqCompat.isModLoaded())
            .append(',');
        sb.append("\"questSubmitEnabled\":")
            .append(Config.webQuestSubmitEnabled)
            .append(',');
        sb.append("\"questChainSubmitEnabled\":")
            .append(Config.webQuestChainSubmitEnabled)
            .append(',');
        sb.append("\"dashboardMaxTracksPerWidget\":")
            .append(Config.webDashboardMaxTracksPerWidget)
            .append(',');
        sb.append("\"dashboardMaxTracksGlobal\":")
            .append(Config.webDashboardMaxTracksGlobal)
            .append(',');
        sb.append("\"dashboardMaxItemTracks\":")
            .append(Config.webDashboardMaxItemTracks)
            .append(',');
        sb.append("\"dashboardMaxFluidTracks\":")
            .append(Config.webDashboardMaxFluidTracks)
            .append(',');
        sb.append("\"dashboardMaxEntityTracks\":")
            .append(Config.webDashboardMaxEntityTracks);
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
