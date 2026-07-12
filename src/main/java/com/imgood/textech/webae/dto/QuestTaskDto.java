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
    public long required;
    public long progress;
    public String fluidName;
    public long fluidRequired;
    public long fluidProgress;
    /** Additional required item variants beyond the first (multi-item tasks). */
    public int extraItemCount;
}
