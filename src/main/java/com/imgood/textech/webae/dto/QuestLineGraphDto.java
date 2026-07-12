package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/** Graph payload for a single quest line. */
public class QuestLineGraphDto {

    public String lineId;
    public String name;
    public List<QuestLineNodeDto> nodes = new ArrayList<QuestLineNodeDto>();
    public List<QuestLineEdgeDto> edges = new ArrayList<QuestLineEdgeDto>();
}
