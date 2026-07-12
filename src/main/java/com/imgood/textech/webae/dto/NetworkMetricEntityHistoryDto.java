package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-entity (CPU / GT machine) numeric history for dashboard pins.
 * Entity keys: {@code cpu:&lt;name&gt;} or {@code gt:&lt;dim&gt;:&lt;x&gt;:&lt;y&gt;:&lt;z&gt;}.
 */
public final class NetworkMetricEntityHistoryDto {

    public int networkId;
    /** entityKey → series */
    public Map<String, EntitySeries> entities = new HashMap<String, EntitySeries>();

    public static final class EntitySeries {

        public String field;
        public List<Long> timestamps = new ArrayList<Long>();
        public List<Double> values = new ArrayList<Double>();
    }
}
