package com.imgood.textech.webae.recipe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.webae.WebAeLocalDataDir;
import com.imgood.textech.webae.dto.RecipeDto;
import com.imgood.textech.webae.recipe.RecipeCacheStore.RecipeCacheFile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Writes collected NEI recipes to {@code TeXTech/WebAE/web-recipes.json} or {@code .json.gz}
 * per {@link Config#webRecipeDiskFormat} on the client.
 */
public final class RecipeLocalExporter {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final int SAVE_SCHEMA_VERSION = 1;

    private RecipeLocalExporter() {}

    @SideOnly(Side.CLIENT)
    public static File exportRecipes(List<RecipeDto> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null) {
            return null;
        }
        boolean gzip = isGzipDiskFormat();
        String filename = gzip ? WebAeLocalDataDir.RECIPE_GZ_FILENAME : WebAeLocalDataDir.RECIPE_JSON_FILENAME;
        File dir = WebAeLocalDataDir.resolve(mc.mcDataDir);
        File file = new File(dir, filename);
        File tmp = new File(dir, filename + ".tmp");
        try {
            RecipeCacheFile cacheFile = new RecipeCacheFile(SAVE_SCHEMA_VERSION, recipes.size(), recipes);
            OutputStream fos = new FileOutputStream(tmp);
            try {
                OutputStream out = gzip ? new GZIPOutputStream(fos) : fos;
                Writer writer = new OutputStreamWriter(out, "UTF-8");
                try {
                    GSON.toJson(cacheFile, writer);
                } finally {
                    writer.flush();
                    writer.close();
                }
            } finally {
                fos.close();
            }
            if (file.exists()) {
                file.delete();
            }
            if (!tmp.renameTo(file)) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to rename local recipe export {}", tmp.getName());
                return null;
            }
            File alternate = new File(
                dir,
                gzip ? WebAeLocalDataDir.RECIPE_JSON_FILENAME : WebAeLocalDataDir.RECIPE_GZ_FILENAME);
            if (alternate.exists()) {
                alternate.delete();
            }
            AdvanceDataMonitor.LOG.info(
                "[WebAE] Exported {} recipes to {} ({})",
                recipes.size(),
                file.getAbsolutePath(),
                gzip ? "gzip" : "json");
            return file;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Failed to export recipes locally", e);
            if (tmp.exists()) {
                tmp.delete();
            }
            return null;
        }
    }

    private static boolean isGzipDiskFormat() {
        String fmt = Config.webRecipeDiskFormat;
        return fmt != null && "gzip".equalsIgnoreCase(fmt.trim());
    }
}
