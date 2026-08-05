package com.imgood.textech.webae.dto;

/** Analysis for one task step. */
public class QuestAnalysisStepDto {

    public int index;
    public String webAction;
    public String reasonKey;
    public boolean complete;
    public boolean webCapable;
    public String itemId;
    public String registryName;
    public int meta;
    /** Localized item/fluid label copied from {@link QuestTaskDto#displayName}. */
    public String displayName;
    /** Display-only icon id copied from {@link QuestTaskDto#iconItemId}. */
    public String iconItemId;
    public long required;
    public long available;
    /** Still needed for quest completion (required minus progress). */
    public long remaining;
    public long craftable;
    public long missing;
    public String fluidName;
    public long fluidRequired;
    public long fluidAvailable;
    /** Fluid still needed for quest completion (fluidRequired minus fluidProgress). */
    public long fluidRemaining;
    public long fluidMissing;
    /** Free AE fluid mB contributing to availability (true fluid or cell DETECT). */
    public long fluidFromFreeMb;
    /** Fluid mB inside cells/containers contributing to availability. */
    public long fluidFromCellsMb;
    /** True when this item step is a filled fluid cell (equivalence applies). */
    public boolean fluidCellTask;
    /** Empty cells available for SUBMIT fill-from-fluid. */
    public long emptyCellAvailable;
    /** Cell capacity mB used for free-fluid equivalence. */
    public int fluidCellCapacityMb;
}
