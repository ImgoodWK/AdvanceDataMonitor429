package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** Result of POST /api/quests/{id}/claim. */
public class QuestClaimResultDto {

    public boolean success;
    /** Machine-readable failure / status code. */
    public String code = "";
    public String message = "";
    public String questId = "";
    public String newState = "";
    public int networkId;
    public List<String> delivered = new ArrayList<String>();
}
