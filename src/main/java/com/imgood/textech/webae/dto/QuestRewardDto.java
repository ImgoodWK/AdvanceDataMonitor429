package com.imgood.textech.webae.dto;

/** Reward preview (read-only). */
public class QuestRewardDto {

    public int index;
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
}
