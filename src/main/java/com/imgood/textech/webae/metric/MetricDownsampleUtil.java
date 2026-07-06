package com.imgood.textech.webae.metric;

import java.util.ArrayList;
import java.util.List;

/**
 * Downsample time-series arrays for HTTP responses (Phase 3.2).
 * When {@code size > maxPoints}, pairs of consecutive samples are merged by taking
 * the maximum value in each pair (simple, JVM-8 friendly).
 */
public final class MetricDownsampleUtil {

    public static final int DEFAULT_MAX_POINTS = 120;

    private MetricDownsampleUtil() {}

    public static List<Long> downsampleTimestamps(List<Long> timestamps, int maxPoints) {
        if (timestamps == null || timestamps.size() <= maxPoints) {
            return timestamps;
        }
        List<Long> out = new ArrayList<Long>(maxPoints);
        int n = timestamps.size();
        double step = (double) (n - 1) / (double) (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            if (idx >= n) {
                idx = n - 1;
            }
            out.add(timestamps.get(idx));
        }
        return out;
    }

    public static List<Double> downsampleValuesMax(List<Double> values, int maxPoints) {
        if (values == null || values.size() <= maxPoints) {
            return values;
        }
        int n = values.size();
        List<Double> out = new ArrayList<Double>(maxPoints);
        int bucketSize = (int) Math.ceil((double) n / (double) maxPoints);
        if (bucketSize < 1) {
            bucketSize = 1;
        }
        for (int start = 0; start < n; start += bucketSize) {
            int end = start + bucketSize;
            if (end > n) {
                end = n;
            }
            double max = values.get(start);
            for (int i = start + 1; i < end; i++) {
                double v = values.get(i);
                if (v > max) {
                    max = v;
                }
            }
            out.add(max);
        }
        while (out.size() > maxPoints) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static List<Long> downsampleLongValuesMax(List<Long> values, int maxPoints) {
        if (values == null || values.size() <= maxPoints) {
            return values;
        }
        List<Double> asDouble = new ArrayList<Double>(values.size());
        for (Long v : values) {
            asDouble.add(v != null ? (double) v.longValue() : 0.0);
        }
        List<Double> down = downsampleValuesMax(asDouble, maxPoints);
        List<Long> out = new ArrayList<Long>(down.size());
        for (Double d : down) {
            out.add(d != null ? d.longValue() : 0L);
        }
        return out;
    }
}
