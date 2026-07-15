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
}
