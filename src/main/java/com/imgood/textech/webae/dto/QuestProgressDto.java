package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Player/party progress snapshot. */
public class QuestProgressDto {

    public String questingUuid;
    public long updatedAt;
    public List<QuestProgressEntryDto> entries = new ArrayList<QuestProgressEntryDto>();
    public Map<String, Integer> lineSubmittableCounts = new HashMap<String, Integer>();
}
