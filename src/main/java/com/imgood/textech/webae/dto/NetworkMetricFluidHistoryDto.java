package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-fluid amount history for pinned / requested fluids (Phase 3.1).
 */
public final class NetworkMetricFluidHistoryDto {

    public int networkId;
    /** fluidName (lowercase key) → series */
    public Map<String, FluidSeries> fluids = new HashMap<String, FluidSeries>();

    public static final class FluidSeries {

        public List<Long> timestamps = new ArrayList<Long>();
        public List<Long> amounts = new ArrayList<Long>();
    }
}
