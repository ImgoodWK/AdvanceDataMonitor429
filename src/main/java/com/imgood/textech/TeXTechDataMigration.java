package com.imgood.textech;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * One-time startup migration: moves runtime data from legacy locations into
 * {@code <instance>/TeXTech/<feature>/}. Forge {@code .cfg} files stay in {@code config/textech/}.
 */
public final class TeXTechDataMigration {

    private static final Set<String> CONFIG_WHITELIST = new HashSet<String>(
        Arrays.asList("textech.cfg", "ai-client-local.cfg", "advancedatamonitor.cfg"));

    private static final String[] WEBAE_FILES = { "web-alerts.json", "web-favorites.json", "web-order-templates.json",
        "web-tokens.json", "web-chat.json", "web-players.json", "web-recipes.json", "web-recipes.json.gz" };

    /** legacyDirName → targetDirName under TeXTech/WebAE/ */
    private static final String[][] WEBAE_DIR_RENAMES = { { "web-icons", "icons" }, { "web-map-tiles", "map-tiles" },
        { "web-topology", "topology" } };

    private static final String[] ASSISTANT_FILES = { "plans.json", "order-memory.json", "assistant-features.json",
        "assistant-lexicon.json", "assistant-dialog-debug.log" };

    private static final String[] GRAPPLE_FILES = { "grapple-paths.json" };

    private TeXTechDataMigration() {}

    /** Invoked once from {@link CommonProxy#preInit} after main config load. */
    public static void run() {
        TeXTechDataDir.webAeRoot();
        TeXTechDataDir.assistantRoot();
        TeXTechDataDir.grappleRoot();

        File[] configSources = { TeXTechDataDir.legacyConfigDir(), TeXTechDataDir.legacyModConfigDir() };
        for (String fileName : WEBAE_FILES) {
            moveFileFromSources(configSources, new File(TeXTechDataDir.webAeRoot(), fileName));
        }
        for (String[] mapping : WEBAE_DIR_RENAMES) {
            moveDirFromSources(configSources, mapping[0], new File(TeXTechDataDir.webAeRoot(), mapping[1]));
        }
        for (String fileName : ASSISTANT_FILES) {
            moveFileFromSources(configSources, new File(TeXTechDataDir.assistantRoot(), fileName));
        }
        for (String fileName : GRAPPLE_FILES) {
            moveFileFromSources(configSources, new File(TeXTechDataDir.grappleRoot(), fileName));
        }

        migratePocketFallbackFiles(configSources);
        migrateTeXTechWebAeRoot();
        warnRemainingConfigArtifacts();
        cleanupEmptyLegacyRoot(TeXTechDataDir.legacyWebAeRoot());
    }

    private static void moveFileFromSources(File[] sources, File target) {
        if (target.exists()) {
            return;
        }
        for (File sourceRoot : sources) {
            if (sourceRoot == null) {
                continue;
            }
            File legacy = new File(sourceRoot, target.getName());
            if (TeXTechDataDir.moveIfNeeded(legacy, target)) {
                return;
            }
        }
    }

    private static void moveDirFromSources(File[] sources, String legacyDirName, File target) {
        if (target.exists()) {
            return;
        }
        for (File sourceRoot : sources) {
            if (sourceRoot == null) {
                continue;
            }
            File legacy = new File(sourceRoot, legacyDirName);
            if (TeXTechDataDir.moveIfNeeded(legacy, target)) {
                return;
            }
        }
    }

    /** Pre-world-load pocket files that incorrectly landed in config/textech/. */
    private static void migratePocketFallbackFiles(File[] sources) {
        for (File sourceRoot : sources) {
            if (sourceRoot == null || !sourceRoot.isDirectory()) {
                continue;
            }
            File[] children = sourceRoot.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                String name = child.getName();
                if (!name.startsWith("pocket-")) {
                    continue;
                }
                File target = new File(TeXTechDataDir.assistantRoot(), name);
                TeXTechDataDir.moveIfNeeded(child, target);
            }
        }
    }

    private static void migrateTeXTechWebAeRoot() {
        File legacyRoot = TeXTechDataDir.legacyWebAeRoot();
        if (!legacyRoot.isDirectory()) {
            return;
        }
        File webAeRoot = TeXTechDataDir.webAeRoot();
        File[] children = legacyRoot.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String targetName = remapWebAeChildName(child.getName());
            File target = new File(webAeRoot, targetName);
            if (!target.exists()) {
                TeXTechDataDir.moveIfNeeded(child, target);
            } else if (child.isDirectory() && target.isDirectory()) {
                mergeDirectoryChildren(child, target);
                cleanupEmptyLegacyRoot(child);
            } else {
                AdvanceDataMonitor.LOG.debug(
                    "[TeXTech] Skipped TeXTechWebAE merge (target exists): {} -> {}",
                    child.getAbsolutePath(),
                    target.getAbsolutePath());
            }
        }
    }

    private static String remapWebAeChildName(String name) {
        for (String[] mapping : WEBAE_DIR_RENAMES) {
            if (mapping[0].equals(name)) {
                return mapping[1];
            }
        }
        return name;
    }

    private static void mergeDirectoryChildren(File sourceDir, File targetDir) {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File dest = new File(targetDir, child.getName());
            if (!dest.exists()) {
                TeXTechDataDir.moveIfNeeded(child, dest);
            } else if (child.isDirectory() && dest.isDirectory()) {
                mergeDirectoryChildren(child, dest);
                cleanupEmptyLegacyRoot(child);
            }
        }
    }

    private static void warnRemainingConfigArtifacts() {
        warnConfigDir(TeXTechDataDir.legacyConfigDir());
        warnConfigDir(TeXTechDataDir.legacyModConfigDir());
    }

    private static void warnConfigDir(File configDir) {
        if (configDir == null || !configDir.isDirectory()) {
            return;
        }
        File[] children = configDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                AdvanceDataMonitor.LOG.warn(
                    "[TeXTech] Legacy runtime data directory remains in config (move to TeXTech/ manually or delete after backup): {}",
                    child.getAbsolutePath());
                continue;
            }
            String name = child.getName();
            if (name.endsWith(".cfg") && CONFIG_WHITELIST.contains(name)) {
                continue;
            }
            if (CONFIG_WHITELIST.contains(name)) {
                continue;
            }
            AdvanceDataMonitor.LOG.warn(
                "[TeXTech] Legacy runtime data file remains in config (move to TeXTech/ manually or delete after backup): {}",
                child.getAbsolutePath());
        }
    }

    private static void cleanupEmptyLegacyRoot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null && children.length > 0) {
            return;
        }
        if (dir.delete()) {
            AdvanceDataMonitor.LOG.info("[TeXTech] Removed empty legacy directory {}", dir.getAbsolutePath());
        }
    }
}
