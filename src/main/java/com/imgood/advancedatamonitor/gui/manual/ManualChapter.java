package com.imgood.advancedatamonitor.gui.manual;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a manual chapter with its pages.
 */
public class ManualChapter {

    public String id;
    public String chapterTitleKey;
    public String icon;
    public List<ManualPage> pages = new ArrayList<>();

    public ManualChapter() {}

    public ManualChapter(String id, String chapterTitleKey, String icon) {
        this.id = id;
        this.chapterTitleKey = chapterTitleKey;
        this.icon = icon;
    }

    public int getPageCount() {
        return pages != null ? pages.size() : 0;
    }

    public ManualPage getPage(int index) {
        if (pages == null || index < 0 || index >= pages.size()) return null;
        return pages.get(index);
    }
}
