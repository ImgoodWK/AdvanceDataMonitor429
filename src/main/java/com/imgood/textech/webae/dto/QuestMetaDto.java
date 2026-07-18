package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** GET /api/quests/meta */
public class QuestMetaDto {

    public boolean questsAvailable;
    public boolean questEnabled;
    public boolean questSubmitEnabled;
    public boolean questClaimEnabled;
    public boolean questChainSubmitEnabled;
    public boolean questFluidAllContainersOption;
    public String modVersion = "";
    public int lineCount;
    public boolean standardExpansionLoaded;
}
