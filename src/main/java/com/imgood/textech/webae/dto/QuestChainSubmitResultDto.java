package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** POST /api/quests/{id}/submit-chain response (or async job envelope). */
public class QuestChainSubmitResultDto {

    public boolean success;
    public boolean dryRun;
    public String message = "";
    public String targetQuestId;
    /** Non-empty when craft+submit is running asynchronously. */
    public String jobId = "";
    public boolean complete = true;
    public String phase = "done";
    public List<QuestChainStepResultDto> steps = new ArrayList<QuestChainStepResultDto>();
}
