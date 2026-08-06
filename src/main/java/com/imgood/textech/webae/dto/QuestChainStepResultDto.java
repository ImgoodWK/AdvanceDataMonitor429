package com.imgood.textech.webae.dto;

/** Result for one quest processed during chain submit. */
public class QuestChainStepResultDto {

    public String questId;
    public String name = "";
    /** submitted | skipped | failed | pending */
    public String action = "pending";
    public String message = "";
    public QuestSubmitResultDto submitResult;
}
