package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** AE inventory analysis for a quest (Phase B). */
public class QuestAnalysisDto {

    public String questId;
    public int networkId;
    public String state;
    public boolean canSubmit;
    public List<QuestAnalysisStepDto> steps = new ArrayList<QuestAnalysisStepDto>();
}
