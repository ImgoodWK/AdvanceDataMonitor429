package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** GET /api/quests/{id}/chain-plan */
public class QuestChainPlanDto {

    public String targetQuestId;
    public int networkId;
    public boolean chainEnabled = true;
    public List<QuestChainStepDto> steps = new ArrayList<QuestChainStepDto>();
}
