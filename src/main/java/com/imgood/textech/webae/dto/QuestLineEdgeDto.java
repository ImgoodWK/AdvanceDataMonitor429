package com.imgood.textech.webae.dto;

/** Dependency edge between quests on a line. */
public class QuestLineEdgeDto {

    public String fromQuestId;
    public String toQuestId;
    /** NORMAL | IMPLICIT | HIDDEN */
    public String requirementType;
}
