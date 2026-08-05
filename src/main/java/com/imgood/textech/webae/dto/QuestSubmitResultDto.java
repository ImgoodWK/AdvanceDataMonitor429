package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** Result of quest submit / detect. */
public class QuestSubmitResultDto {

    public boolean success;
    public boolean dryRun;
    public String message;
    public String questId;
    public String newState;
    public List<QuestSubmitStepResultDto> steps = new ArrayList<QuestSubmitStepResultDto>();
}
