package com.imgood.advancedatamonitor.gui.manual;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single page in the manual.
 * Supports types: "text", "item_showcase", "config_ref".
 */
public class ManualPage {

    public String type;
    public String titleKey;
    public String textKey;
    public String category;
    public List<ManualItemEntry> items = new ArrayList<>();

    public ManualPage() {}

    public boolean isTextPage() {
        return "text".equals(type);
    }

    public boolean isItemShowcase() {
        return "item_showcase".equals(type);
    }

    public boolean isConfigRef() {
        return "config_ref".equals(type);
    }

    /**
     * A single item entry inside an item_showcase page.
     */
    public static class ManualItemEntry {

        public String registryName;
        public String descKey;

        public ManualItemEntry() {}

        public ManualItemEntry(String registryName, String descKey) {
            this.registryName = registryName;
            this.descKey = descKey;
        }
    }
}
