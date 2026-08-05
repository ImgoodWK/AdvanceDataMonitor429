package com.imgood.textech.webae.dto;

/** Single quest task step. */
public class QuestTaskDto {

    public int index;
    public String taskId;
    public String factoryId;
    public String name;
    public String description;
    /** SUBMIT | DETECT | IN_GAME_ONLY */
    public String webAction;
    public String reasonKey;
    public boolean complete;
    public String itemId;
    public String registryName;
    public int meta;
    /**
     * Localized item/fluid name for UI (e.g. GT filled cell {@code Oxygen Cell} / {@code 氧气单元}).
     * Prefer over {@link #registryName} in frontend labels; does not affect AE matching.
     */
    public String displayName;
    /**
     * Display-only icon cache id (e.g. {@code fluid:lava} for FluidDisplay / true fluids;
     * filled cells keep {@code mod:id:meta} like recipe NEI).
     * Does not affect AE item/fluid matching or submit semantics.
     */
    public String iconItemId;
    public long required;
    public long progress;
    public String fluidName;
    public long fluidRequired;
    public long fluidProgress;
    /** Additional required item variants beyond the first (multi-item tasks). */
    public int extraItemCount;
    /** True if this task accepts items of any meta (e.g. retrieval tasks with oreDict wildcard). */
    public boolean acceptAnyMeta;
}
