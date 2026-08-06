package com.imgood.textech.webae.dto;

/** Craft-then-submit job status (Phase C). */
public class QuestCraftJobDto {

    public String jobId;
    public String questId;
    public String phase;
    public boolean complete;
    public boolean success;
    public String message;
    public int ordersTotal;
    public int ordersDone;
    public QuestSubmitResultDto submitResult;
}
