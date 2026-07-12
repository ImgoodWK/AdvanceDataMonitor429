package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** Player/party progress snapshot. */
public class QuestProgressDto {

    public String questingUuid;
    public long updatedAt;
    public List<QuestProgressEntryDto> entries = new ArrayList<QuestProgressEntryDto>();
}
