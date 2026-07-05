package com.imgood.textech.webae.recipe;

import java.util.List;

import com.imgood.textech.webae.dto.RecipeDto;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side collector for snapshot-scoped recipe uploads (storage snapshot items).
 */
@SideOnly(Side.CLIENT)
public final class RecipeSnapshotCollector {

    private RecipeSnapshotCollector() {}

    public static List<RecipeDto> collectForItems(List<String> itemIds) {
        return NeiRecipeCollector.collectForItemIds(itemIds);
    }
}
