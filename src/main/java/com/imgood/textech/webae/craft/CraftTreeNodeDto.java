package com.imgood.textech.webae.craft;

import java.util.ArrayList;
import java.util.List;

/**
 * One node in a recursive craft/material tree (Phase 6).
 */
public final class CraftTreeNodeDto {

    public String itemId = "";
    public String registryName = "";
    public String displayName = "";
    public int meta;
    /** Total amount required at this tree level (including nested crafts). */
    public long required;
    /** Amount available in AE storage snapshot (0 if unknown). */
    public long available;
    /** Shortfall = max(0, required - available). */
    public long missing;
    /** True when this item has no craft recipe (leaf / raw material). */
    public boolean leaf;
    /** Handler id of chosen recipe, empty if leaf. */
    public String recipeHandlerId = "";
    public int recipeIndex = -1;
    public List<CraftTreeNodeDto> children = new ArrayList<CraftTreeNodeDto>();
}
