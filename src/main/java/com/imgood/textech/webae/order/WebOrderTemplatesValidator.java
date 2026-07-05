package com.imgood.textech.webae.order;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates order templates before persisting.
 */
public final class WebOrderTemplatesValidator {

    public static final int MAX_TEMPLATES_PER_OWNER = 50;
    public static final int MAX_ITEMS_PER_TEMPLATE = 50;

    private WebOrderTemplatesValidator() {}

    /**
     * @return error message, or {@code null} when valid
     */
    public static String validateOwnerTemplates(List<WebOrderTemplate> templates) {
        if (templates == null) {
            return "Missing templates array";
        }
        if (templates.size() > MAX_TEMPLATES_PER_OWNER) {
            return "At most " + MAX_TEMPLATES_PER_OWNER + " templates allowed";
        }
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < templates.size(); i++) {
            String err = validateTemplate(templates.get(i), i);
            if (err != null) {
                return err;
            }
            String id = trimOrEmpty(templates.get(i).id);
            if (ids.contains(id)) {
                return "Duplicate template id: " + id;
            }
            ids.add(id);
        }
        return null;
    }

    /** Normalize ids, trim strings, clamp amounts, assign updatedAt when missing. */
    public static List<WebOrderTemplate> normalize(List<WebOrderTemplate> templates) {
        List<WebOrderTemplate> out = new ArrayList<WebOrderTemplate>();
        if (templates == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        for (WebOrderTemplate src : templates) {
            if (src == null) {
                continue;
            }
            WebOrderTemplate t = new WebOrderTemplate();
            t.id = trimOrEmpty(src.id);
            if (t.id.isEmpty()) {
                t.id = UUID.randomUUID()
                    .toString();
            }
            t.name = trimOrEmpty(src.name);
            t.cpuName = trimOrEmpty(src.cpuName);
            t.networkId = src.networkId < 0 ? 0 : src.networkId;
            t.updatedAt = src.updatedAt > 0L ? src.updatedAt : now;
            t.items = normalizeItems(src.items);
            if (t.name.isEmpty() || t.items.isEmpty()) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    private static String validateTemplate(WebOrderTemplate t, int index) {
        if (t == null) {
            return "templates[" + index + "] is null";
        }
        if (trimOrEmpty(t.name).isEmpty()) {
            return "templates[" + index + "]: name required";
        }
        if (t.items == null || t.items.isEmpty()) {
            return "templates[" + index + "]: at least one item required";
        }
        if (t.items.size() > MAX_ITEMS_PER_TEMPLATE) {
            return "templates[" + index + "]: at most " + MAX_ITEMS_PER_TEMPLATE + " items";
        }
        for (int j = 0; j < t.items.size(); j++) {
            String err = validateItem(t.items.get(j), index, j);
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    private static String validateItem(WebOrderTemplateItem item, int tIndex, int iIndex) {
        if (item == null) {
            return "templates[" + tIndex + "].items[" + iIndex + "] is null";
        }
        String itemName = trimOrEmpty(item.itemName);
        String patternId = trimOrEmpty(item.patternId);
        if (itemName.isEmpty() && patternId.isEmpty()) {
            return "templates[" + tIndex + "].items[" + iIndex + "]: itemName or patternId required";
        }
        if (item.amount < 1) {
            return "templates[" + tIndex + "].items[" + iIndex + "]: amount must be >= 1";
        }
        return null;
    }

    private static List<WebOrderTemplateItem> normalizeItems(List<WebOrderTemplateItem> items) {
        List<WebOrderTemplateItem> out = new ArrayList<WebOrderTemplateItem>();
        if (items == null) {
            return out;
        }
        for (WebOrderTemplateItem src : items) {
            if (src == null) {
                continue;
            }
            WebOrderTemplateItem copy = new WebOrderTemplateItem();
            copy.itemName = trimOrEmpty(src.itemName);
            copy.patternId = trimOrEmpty(src.patternId);
            if (copy.patternId.isEmpty()) {
                copy.patternId = null;
            }
            copy.amount = src.amount < 1 ? 1 : src.amount;
            if (copy.itemName.isEmpty() && (copy.patternId == null || copy.patternId.isEmpty())) {
                continue;
            }
            out.add(copy);
        }
        return out;
    }

    private static String trimOrEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.trim();
    }
}
