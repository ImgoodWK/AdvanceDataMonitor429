package com.imgood.textech.webae.dto;

/**
 * Quest reward row for WebAE. Choice rewards emit one row per option with the same {@link #rewardId}.
 */
public class QuestRewardDto {

    public int index;
    /** Reward entity id (int id from BQ RewardStorage). */
    public String rewardId;
    public String factoryId;
    public String name;
    public String description;
    public String itemId;
    public String registryName;
    public int meta;
    /**
     * Display-only icon cache id (e.g. {@code fluid:lava} for FluidDisplay,
     * or {@code mod:id:meta} for filled cells — same rules as quest node icons).
     */
    public String iconItemId;
    public long amount;
    /**
     * {@code item} — deterministic item reward; {@code choice} — pick-one option row;
     * {@code unsupported} — non-item / unknown factory (WebAE will not claim).
     */
    public String kind = "unsupported";
    /** Index within a choice group; {@code -1} when not a choice option. */
    public int choiceIndex = -1;
    /** True when this row is one option of a {@code bq_standard:choice} reward. */
    public boolean choiceOption;
    /** True when this reward group is eligible for WebAE claim (item or choice). */
    public boolean webClaimable;
}
