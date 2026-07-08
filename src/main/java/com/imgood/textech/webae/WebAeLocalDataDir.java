package com.imgood.textech.webae;

import java.io.File;

import com.imgood.textech.Config;
import com.imgood.textech.TeXTechDataDir;

/**
 * Shared local data folder under the Minecraft instance root ({@code .minecraft} on client,
 * server root on dedicated server): {@code TeXTech/WebAE/}.
 *
 * <p>
 * Used by {@code /admweb icons import-nesql} (server) and client NEI recipe export after
 * {@code /admweb recipes upload*}. Legacy {@code TeXTechWebAE/} is migrated on first access.
 * </p>
 */
public final class WebAeLocalDataDir {

    public static final String RECIPE_GZ_FILENAME = "web-recipes.json.gz";

    private WebAeLocalDataDir() {}

    /** {@code TeXTech/WebAE/}; creates the directory and migrates legacy {@code TeXTechWebAE/} files. */
    public static File resolve(File instanceRoot) {
        File root = instanceRoot != null ? instanceRoot : TeXTechDataDir.instanceRoot();
        File dir = new File(new File(root, TeXTechDataDir.ROOT_DIR_NAME), TeXTechDataDir.WEBAE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            migrateLegacyRootContents(dir);
        }
        return dir;
    }

    /** Server / integrated-server instance root ({@code .} = same level as {@code config/}). */
    public static File serverDir() {
        return resolve(TeXTechDataDir.instanceRoot());
    }

    /**
     * NESQL repository root for {@code /admweb icons import-nesql}.
     * Empty config → {@link #serverDir()}.
     */
    public static String resolveNesqlRepositoryPath() {
        String configured = Config.webNesqlRepositoryPath;
        if (configured != null && !configured.trim()
            .isEmpty()) {
            return configured.trim();
        }
        return serverDir().getAbsolutePath();
    }

    public static File serverRecipeExportFile() {
        return TeXTechDataDir.webAeFile(RECIPE_GZ_FILENAME);
    }

    private static void migrateLegacyRootContents(File targetDir) {
        File legacyRoot = TeXTechDataDir.legacyWebAeRoot();
        if (!legacyRoot.isDirectory()) {
            return;
        }
        File[] children = legacyRoot.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File dest = new File(targetDir, child.getName());
            if (!dest.exists()) {
                TeXTechDataDir.migrateIfNeeded(child, dest);
            }
        }
    }
}
