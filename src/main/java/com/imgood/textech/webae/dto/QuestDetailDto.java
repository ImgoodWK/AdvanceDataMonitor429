package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** Full quest detail for drawer. */
public class QuestDetailDto {

    public String questId;
    public String name;
    public String description;
    public String state;
    public boolean canSubmit;
    public boolean canClaim;
    public boolean hasClaimed;
    public boolean mainQuest;
    public boolean silent;
    public boolean repeatable;
    public String iconItemId;
    public int iconMeta;
    public List<String> requirementQuestIds = new ArrayList<String>();
    public List<QuestRelationDto> prerequisites = new ArrayList<QuestRelationDto>();
    public List<QuestRelationDto> dependents = new ArrayList<QuestRelationDto>();
    public List<QuestTaskDto> tasks = new ArrayList<QuestTaskDto>();
    public List<QuestRewardDto> rewards = new ArrayList<QuestRewardDto>();
}
