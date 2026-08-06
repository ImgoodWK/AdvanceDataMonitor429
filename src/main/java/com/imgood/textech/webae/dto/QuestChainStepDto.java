package com.imgood.textech.webae.dto;

/** One quest in a chain-submit plan (topological order). */
public class QuestChainStepDto {

    public String questId;
    public String name = "";
    /** LOCKED | UNLOCKED | UNCLAIMED | COMPLETED | REPEATABLE */
    public String state = "LOCKED";
    public boolean canSubmit;
    public boolean target;
    /** skipped because already done / unclaimed / locked / in-game-only */
    public boolean skipped;
    public String skipReason = "";
    public boolean fullySatisfied;
    public boolean craftable;
    public int missingItemKinds;
    public QuestAnalysisDto analysis;
}
