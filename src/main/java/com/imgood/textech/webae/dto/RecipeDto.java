package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * NEI recipe DTO for WebAE console JSON serialization.
 * Each instance represents one recipe from an NEI recipe handler.
 */
public class RecipeDto {

    /** NEI handler identifier (e.g. "codechicken.nei.recipe.ShapedRecipeHandler"). */
    public String handlerId;
    /** Recipe index within the handler. */
    public int recipeIndex;
    /** Human-readable recipe type name (e.g. "Shaped Crafting"). */
    public String handlerName;
    /** Input item stacks. */
    public List<ItemEntry> inputs;
    /** Output item stacks. */
    public List<ItemEntry> outputs;
    /** Serialized full recipe data for frontend display (JSON string). */
    public String rawJson;

    /** Crafting grid width (columns), 0 if unknown. */
    public int gridWidth;
    /** Crafting grid height (rows), 0 if unknown. */
    public int gridHeight;
    /** Positioned slots for shaped/grid recipes. */
    public List<GridSlot> gridSlots;

    /** GT / machine: EU per tick (may be negative for generators). */
    public Long euPerTick;
    /** GT / machine: duration in ticks. */
    public Integer durationTicks;
    /** GT voltage tier label (e.g. "LV", "MV"). */
    public String voltageTier;

    /** Source type hint: crafting, smelting, gt, nei. */
    public String recipeType;

    public RecipeDto() {
        this.inputs = new ArrayList<ItemEntry>();
        this.outputs = new ArrayList<ItemEntry>();
        this.gridSlots = new ArrayList<GridSlot>();
    }

    public RecipeDto(String handlerId, int recipeIndex, String handlerName) {
        this();
        this.handlerId = handlerId;
        this.recipeIndex = recipeIndex;
        this.handlerName = handlerName;
    }

    /**
     * Simplified item entry for recipe display.
     */
    public static class ItemEntry {

        public String itemId;
        public String displayName;
        public String registryName;
        public int meta;
        public int stackSize;

        public ItemEntry() {}

        public ItemEntry(String itemId, String displayName, String registryName, int meta, int stackSize) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.registryName = registryName;
            this.meta = meta;
            this.stackSize = stackSize;
        }
    }

    /** A single cell in a crafting / machine input grid. */
    public static class GridSlot {

        public int col;
        public int row;
        public ItemEntry item;

        public GridSlot() {}

        public GridSlot(int col, int row, ItemEntry item) {
            this.col = col;
            this.row = row;
            this.item = item;
        }
    }
}
