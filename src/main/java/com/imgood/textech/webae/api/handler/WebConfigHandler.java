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
 * themeColors (141: classic through authored media flagship Batch7) /
 * themeLayouts (36 presets: classic 8 + batch3 structural 22 + flagship Batch6 6) /
 * pageStyles (138: chrome/composition packs through flagship Batch6) /
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
        themeColors.add("notion-warm");
        themeColors.add("figma-violet");
        themeColors.add("spotify-green");
        themeColors.add("discord-blurple");
        themeColors.add("netflix-red");
        themeColors.add("github-canvas");
        themeColors.add("stripe-violet");
        themeColors.add("openai-sage");
        themeColors.add("tesla-crimson");
        themeColors.add("uber-carbon");
        themeColors.add("adobe-red");
        themeColors.add("airbnb-rausch");
        themeColors.add("switch-neon");
        themeColors.add("aperture-orange");
        themeColors.add("sheikah-cyan");
        themeColors.add("valorant-red");
        themeColors.add("persona-duo");
        themeColors.add("teyvat-gold");
        themeColors.add("nerv-purple");
        themeColors.add("shell-teal");
        themeColors.add("styx-laurel");
        themeColors.add("edgerunner-yellow");
        themeColors.add("hallownest-bone");
        themeColors.add("celeste-dash");
        themeColors.add("hextech-blue");
        themeColors.add("mc-grass");
        themeColors.add("eorzea-gold");
        themeColors.add("kaer-morhen");
        themeColors.add("engram-violet");
        themeColors.add("ow-orange");
        themeColors.add("acnh-leaf");
        themeColors.add("stardew-spring");
        themeColors.add("elden-gold");
        themeColors.add("metroid-orange");
        themeColors.add("smash-impact");
        themeColors.add("terraria-night");
        themeColors.add("ghibli-soft");
        themeColors.add("nichirin-orange");
        themeColors.add("jjk-navy");
        themeColors.add("op-wanted");
        themeColors.add("sxf-pastel");
        themeColors.add("aot-green");
        themeColors.add("sailor-pastel");
        themeColors.add("monogatari-yellow");
        themeColors.add("bebop-noir");
        themeColors.add("frieren-mint");
        themeColors.add("bocchi-pink");
        themeColors.add("meshi-amber");
        themeColors.add("nvidia-green");
        themeColors.add("linear-indigo");
        themeColors.add("printstream");
        themeColors.add("printstream-void");
        themeColors.add("printstream-pearl");
        themeColors.add("printstream-cyan");
        themeColors.add("printstream-magenta");
        themeColors.add("printstream-spectrum");
        themeColors.add("printstream-ascii");
        themeColors.add("printstream-cross");
        themeColors.add("printstream-rect");
        themeColors.add("printstream-neon");
        themeColors.add("printstream-mono");
        themeColors.add("printstream-gloss");
        themeColors.add("aura");
        themeColors.add("aura-front");
        themeColors.add("aura-design");
        themeColors.add("aura-sys");
        themeColors.add("aura-interact");
        themeColors.add("terra-amber");
        themeColors.add("terra-danger");
        themeColors.add("cyber-lime");
        themeColors.add("cyber-redline");
        themeColors.add("ueg-orange");
        themeColors.add("lunar-ice");
        themeColors.add("gtnh-stargate");
        themeColors.add("gregtech-steel");
        themeColors.add("gregtech-bronze");
        themeColors.add("gt-cleanroom");
        themeColors.add("gt-fusion");
        themeColors.add("textech-quantum");
        themeColors.add("bridges-white");
        List<String> themeLayouts = new ArrayList<String>();
        themeLayouts.add("standard");
        themeLayouts.add("compact");
        themeLayouts.add("wide");
        themeLayouts.add("sidebar-right");
        themeLayouts.add("topnav");
        themeLayouts.add("bottomnav");
        themeLayouts.add("floating");
        themeLayouts.add("split-chrome");
        themeLayouts.add("dual-rail");
        themeLayouts.add("rail-only");
        themeLayouts.add("dock");
        themeLayouts.add("island");
        themeLayouts.add("theater");
        themeLayouts.add("dense-ops");
        themeLayouts.add("magazine");
        themeLayouts.add("split-pane");
        themeLayouts.add("top-tabs");
        themeLayouts.add("zen");
        themeLayouts.add("command");
        themeLayouts.add("tri-chrome");
        themeLayouts.add("card-stack");
        themeLayouts.add("hud-frame");
        themeLayouts.add("pipeline");
        themeLayouts.add("hero-header");
        themeLayouts.add("status-strip");
        themeLayouts.add("drawer-peek");
        themeLayouts.add("corner-hub");
        themeLayouts.add("widescreen");
        themeLayouts.add("right-drawer");
        themeLayouts.add("frame");
        themeLayouts.add("tactical-grid");
        themeLayouts.add("mission-control");
        themeLayouts.add("engine-room");
        themeLayouts.add("orbital-console");
        themeLayouts.add("assembly-line");
        themeLayouts.add("quantum-frame");
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
        pageStyles.add("notion-paper");
        pageStyles.add("figma-canvas");
        pageStyles.add("spotify-now");
        pageStyles.add("discord-guild");
        pageStyles.add("netflix-stage");
        pageStyles.add("github-primer");
        pageStyles.add("stripe-ledger");
        pageStyles.add("openai-atelier");
        pageStyles.add("tesla-cockpit");
        pageStyles.add("uber-dispatch");
        pageStyles.add("adobe-spectrum");
        pageStyles.add("airbnb-stay");
        pageStyles.add("nintendo-switch");
        pageStyles.add("portal-chamber");
        pageStyles.add("zelda-sheikah");
        pageStyles.add("valorant-spike");
        pageStyles.add("persona-velvet");
        pageStyles.add("genshin-teyvat");
        pageStyles.add("evangelion-nerv");
        pageStyles.add("ghost-shell");
        pageStyles.add("hades-styx");
        pageStyles.add("cyberpunk-edge");
        pageStyles.add("hollow-knight");
        pageStyles.add("celeste-summit");
        pageStyles.add("lol-rift");
        pageStyles.add("minecraft-craft");
        pageStyles.add("ff14-scion");
        pageStyles.add("witcher-path");
        pageStyles.add("destiny-light");
        pageStyles.add("overwatch-wp");
        pageStyles.add("acnh-horizon");
        pageStyles.add("stardew-farm");
        pageStyles.add("elden-grace");
        pageStyles.add("metroid-suit");
        pageStyles.add("smash-blast");
        pageStyles.add("terraria-torch");
        pageStyles.add("ghibli-sky");
        pageStyles.add("hashira-blade");
        pageStyles.add("jjk-domain");
        pageStyles.add("onepiece-log");
        pageStyles.add("sxf-forger");
        pageStyles.add("aot-survey");
        pageStyles.add("sailor-crystal");
        pageStyles.add("monogatari-pop");
        pageStyles.add("bebop-jazz");
        pageStyles.add("frieren-journey");
        pageStyles.add("bocchi-stage");
        pageStyles.add("meshi-feast");
        pageStyles.add("nvidia-greenroom");
        pageStyles.add("linear-opsdesk");
        pageStyles.add("printstream-panel");
        pageStyles.add("printstream-void");
        pageStyles.add("printstream-pearl");
        pageStyles.add("printstream-cyan");
        pageStyles.add("printstream-magenta");
        pageStyles.add("printstream-spectrum");
        pageStyles.add("printstream-ascii");
        pageStyles.add("printstream-cross");
        pageStyles.add("printstream-rect");
        pageStyles.add("printstream-neon");
        pageStyles.add("printstream-mono");
        pageStyles.add("printstream-gloss");
        pageStyles.add("aura-voxel");
        pageStyles.add("aura-spore");
        pageStyles.add("aura-dome");
        pageStyles.add("aura-sparks");
        pageStyles.add("aura-bubble");
        pageStyles.add("terra-command");
        pageStyles.add("terra-contract");
        pageStyles.add("terra-originium");
        pageStyles.add("cyber-grid");
        pageStyles.add("cyber-chrome");
        pageStyles.add("earth-engine");
        pageStyles.add("lunar-orbit");
        pageStyles.add("gtnh-cosmos");
        pageStyles.add("gt-assembly");
        pageStyles.add("gt-cleanroom");
        pageStyles.add("gt-fusion");
        pageStyles.add("textech-quantum");
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
        sb.append("\"iconLazyCaptureEnabled\":")
            .append(Config.webIconLazyCaptureEnabled)
            .append(',');
        sb.append("\"iconPackEnabled\":")
            .append(Config.webIconPackEnabled)
            .append(',');
        String lazyProvider = com.imgood.textech.webae.icon.IconMissingQueue.instance()
            .getProviderName();
        if (lazyProvider != null && !lazyProvider.isEmpty()) {
            sb.append("\"iconLazyProviderName\":")
                .append(GSON.toJson(lazyProvider))
                .append(',');
            sb.append("\"iconCapturedWithClientTextures\":")
                .append(
                    com.imgood.textech.webae.icon.IconMissingQueue.instance()
                        .isCapturedWithClientTextures())
                .append(',');
        }
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
            .append(
                GSON.toJson(Config.worldMapSnapshotSourcePriority != null ? Config.worldMapSnapshotSourcePriority : ""))
            .append(',');
        sb.append("\"alertsEnabled\":")
            .append(Config.webAlertsEnabled && com.imgood.textech.config.ConfigWebAlertsLoader.get().enabled)
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
        sb.append("\"questClaimEnabled\":")
            .append(Config.webQuestClaimEnabled)
            .append(',');
        sb.append("\"questChainSubmitEnabled\":")
            .append(Config.webQuestChainSubmitEnabled)
            .append(',');
        sb.append("\"questFluidAllContainersOption\":")
            .append(Config.webQuestFluidAllContainersOption)
            .append(',');
        sb.append("\"webAiKeyMode\":")
            .append(GSON.toJson(legacyWebAiKeyMode()))
            .append(',');
        sb.append("\"webAiServerKeyEnabled\":")
            .append(Config.webAiServerKeyEnabled)
            .append(',');
        sb.append("\"webAiBrowserKeyEnabled\":")
            .append(Config.webAiBrowserKeyEnabled)
            .append(',');
        sb.append("\"webAiProviders\":")
            .append(GSON.toJson(com.imgood.textech.webae.assistant.WebAiConfigStore.publicProviderViews()))
            .append(',');
        sb.append("\"webAiShared\":")
            .append(
                GSON.toJson(
                    com.imgood.textech.webae.assistant.WebAiConfigStore.instance()
                        .publicSharedView()))
            .append(',');
        sb.append("\"sparkEnabled\":")
            .append(com.imgood.textech.webae.spark.SparkService.isEnabled())
            .append(',');
        sb.append("\"sparkAvailable\":")
            .append(com.imgood.textech.webae.spark.SparkService.isAvailable())
            .append(',');
        sb.append("\"sparkDefaultDurationSeconds\":")
            .append(Config.webSparkDefaultDurationSeconds)
            .append(',');
        sb.append("\"sparkMaxDurationSeconds\":")
            .append(Config.webSparkMaxDurationSeconds)
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

    /** Compat field for older frontends: both enabled → browser preference side; else the single enabled side. */
    private static String legacyWebAiKeyMode() {
        if (Config.webAiBrowserKeyEnabled && !Config.webAiServerKeyEnabled) return "browser";
        if (Config.webAiServerKeyEnabled && !Config.webAiBrowserKeyEnabled) return "server";
        if (Config.webAiBrowserKeyEnabled && Config.webAiServerKeyEnabled) return "browser";
        return "server";
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
