package com.imgood.textech.webae.dto;

/** Node on the quest line cytoscape graph. */
public class QuestLineNodeDto {

    public String questId;
    public String name;
    public int x;
    public int y;
    public int sizeX = 24;
    public int sizeY = 24;
    /** LOCKED | UNLOCKED | UNCLAIMED | COMPLETED | REPEATABLE */
    public String state;
    public boolean mainQuest;
    public boolean canSubmit;
    public String iconItemId;
    public int iconMeta;
    /** True when this node is a cross-line prerequisite placeholder. */
    public boolean ghost;
    /** Source quest-line id when {@link #ghost} is true (may be empty). */
    public String sourceLineId;
}
