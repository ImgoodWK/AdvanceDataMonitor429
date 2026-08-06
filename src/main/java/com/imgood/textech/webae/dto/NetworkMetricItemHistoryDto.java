package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-item amount history for pinned / requested items (dashboard pins).
 */
public final class NetworkMetricItemHistoryDto {

    public int networkId;
    /** itemId key → series */
    public Map<String, ItemSeries> items = new HashMap<String, ItemSeries>();

    public static final class ItemSeries {

        public List<Long> timestamps = new ArrayList<Long>();
        public List<Long> amounts = new ArrayList<Long>();
    }
}
