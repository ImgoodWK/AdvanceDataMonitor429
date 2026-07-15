package com.imgood.textech.webae.recipe;

import java.util.ArrayList;
import java.util.List;

import com.imgood.textech.webae.recipe.RecipeCacheStore.HandlerInfo;

/**
 * Lightweight on-disk recipe catalog metadata ({@code web-recipes.meta.json}).
 * Used by browser sync without loading the full cache into server memory.
 */
public class RecipeDiskMeta {

    public static final int META_SCHEMA_VERSION = 1;

    public int schemaVersion = META_SCHEMA_VERSION;
    /** Opaque revision; browsers skip re-download when unchanged. */
    public String revision = "";
    public int recipeCount;
    public int chunkSize;
    public int chunkCount;
    public long estimatedBytes;
    public long savedAt;
    public List<HandlerInfo> handlers = new ArrayList<HandlerInfo>();

    public RecipeDiskMeta() {}

    public static String makeRevision(int recipeCount, long estimatedBytes, long savedAt) {
        return recipeCount + "-" + estimatedBytes + "-" + savedAt;
    }
}
