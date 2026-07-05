package com.imgood.textech.webae;

import java.io.File;

import com.imgood.textech.Config;

/**
 * Shared local data folder under the Minecraft instance root ({@code .minecraft} on client,
 * server root on dedicated server): {@code TeXTechWebAE}.
 *
 * <p>Used by {@code /admweb icons import-nesql} (server) and client NEI recipe export after
 * {@code /admweb recipes upload*}.</p>
 */
public final class WebAeLocalDataDir {

    public static final String DIR_NAME = "TeXTechWebAE";
    public static final String RECIPE_GZ_FILENAME = "web-recipes.json.gz";

    private WebAeLocalDataDir() {}

    /** {@code TeXTechWebAE} under the given instance root; creates the directory if missing. */
    public static File resolve(File instanceRoot) {
        File root = instanceRoot != null ? instanceRoot : new File(".").getAbsoluteFile();
        File dir = new File(root, DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Server / integrated-server instance root ({@code .} = same level as {@code config/}). */
    public static File serverDir() {
        return resolve(new File(".").getAbsoluteFile());
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
        return new File(serverDir(), RECIPE_GZ_FILENAME);
    }
}
