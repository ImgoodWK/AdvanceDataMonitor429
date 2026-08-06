package com.imgood.textech.webae.dto;

/** Rich prerequisite or dependent quest link for quest detail panel. */
public class QuestRelationDto {

    public String questId;
    public String name;
    /** Owning quest line id for cross-line navigation. */
    public String lineId;
    public String state;
    /** NORMAL | IMPLICIT | HIDDEN (prerequisite edge type). */
    public String requirementType;
}
