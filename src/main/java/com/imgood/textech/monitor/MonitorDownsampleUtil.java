package com.imgood.textech.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared point-budget logic for native rendering and WebAE monitor previews. */
public final class MonitorDownsampleUtil {

    public static final int MAX_RENDER_POINTS = 240;

    private MonitorDownsampleUtil() {}

    public static int pointBudget(int historyPoints, int visibleWidth) {
        if (historyPoints <= 0 || visibleWidth <= 0) {
            return 0;
        }
        return Math.min(historyPoints, Math.min(Math.max(2, visibleWidth), MAX_RENDER_POINTS));
    }

    public static List<Double> downsample(List<Double> values, int visibleWidth) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        int budget = pointBudget(values.size(), visibleWidth);
        if (budget <= 0) {
            return Collections.emptyList();
        }
        if (values.size() <= budget) {
            return new ArrayList<Double>(values);
        }
        if (budget == 1) {
            return Collections.singletonList(values.get(values.size() - 1));
        }

        List<Double> result = new ArrayList<Double>(budget);
        result.add(values.get(0));
        double step = (values.size() - 1.0D) / (budget - 1.0D);
        for (int i = 1; i < budget - 1; i++) {
            int index = (int) Math.round(i * step);
            index = Math.max(1, Math.min(values.size() - 2, index));
            result.add(values.get(index));
        }
        result.add(values.get(values.size() - 1));
        return result;
    }

    public static List<Double> difference(List<Double> values) {
        if (values == null || values.size() < 2) {
            return Collections.emptyList();
        }
        List<Double> result = new ArrayList<Double>(values.size() - 1);
        for (int i = 1; i < values.size(); i++) {
            result.add(Double.valueOf(values.get(i).doubleValue() - values.get(i - 1).doubleValue()));
        }
        return result;
    }
}
