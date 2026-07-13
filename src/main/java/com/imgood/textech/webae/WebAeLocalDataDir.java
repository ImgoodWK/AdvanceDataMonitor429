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
 * {@code /admweb recipes upload*}. Legacy {@code TeXTechWebAE/} is migrated at startup by
 * {@link com.imgood.textech.TeXTechDataMigration}.
 * </p>
 */
public final class WebAeLocalDataDir {

    public static final String RECIPE_JSON_FILENAME = "web-recipes.json";
    public static final String RECIPE_GZ_FILENAME = "web-recipes.json.gz";

    private WebAeLocalDataDir() {}

    /** {@code TeXTech/WebAE/}; creates the directory if missing. */
    public static File resolve(File instanceRoot) {
        File root = instanceRoot != null ? instanceRoot : TeXTechDataDir.instanceRoot();
        File dir = new File(new File(root, TeXTechDataDir.ROOT_DIR_NAME), TeXTechDataDir.WEBAE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
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
        String fmt = Config.webRecipeDiskFormat;
        boolean gzip = fmt != null && "gzip".equalsIgnoreCase(fmt.trim());
        return TeXTechDataDir.webAeFile(gzip ? RECIPE_GZ_FILENAME : RECIPE_JSON_FILENAME);
    }
}
