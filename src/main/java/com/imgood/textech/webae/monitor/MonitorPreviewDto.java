package com.imgood.textech.webae.monitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only, kind-discriminated preview for one monitor binding. */
public final class MonitorPreviewDto {

    public int monitorDim;
    public int monitorX;
    public int monitorY;
    public int monitorZ;
    public int slotIndex;
    public String dataType = "";
    public String kind = "";
    public String sourceKind = "";
    public String metricKey = "";
    public String title = "";
    public String displayName = "";
    public String previewType = "series";
    public boolean enabled;
    public Scalar scalar;
    public List<Series> series = new ArrayList<Series>();
    public List<Category> categories = new ArrayList<Category>();
    public List<Row> rows = new ArrayList<Row>();
    /** Null means default columns; an empty list means explicitly hide all columns. */
    public List<String> columns;
    /** Legacy line-preview compatibility. */
    public List<Double> values = new ArrayList<Double>();
    public double yMin;
    public double yMax;
    public int dataLimit;
    public int pointBudget;
    public long timestamp;

    public static final class Scalar {

        public double value;
        public double max;
        public double percentage;
        public boolean maxKnown;
    }

    public static final class Series {

        public String id = "";
        public String label = "";
        public List<Double> values = new ArrayList<Double>();
    }

    public static final class Category {

        public String label = "";
        public double value;
        public String color = "";
    }

    public static final class Row {

        public Map<String, String> cells = new LinkedHashMap<String, String>();
    }
}
