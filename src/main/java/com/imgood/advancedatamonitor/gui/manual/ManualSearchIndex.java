package com.imgood.advancedatamonitor.gui.manual;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.advancedatamonitor.gui.manual.ManualDataLoader.ConfigEntry;
import com.imgood.advancedatamonitor.gui.manual.ManualPage.ManualItemEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Builds searchable plain-text blobs for manual chapters (titles + page bodies).
 */
@SideOnly(Side.CLIENT)
public final class ManualSearchIndex {

    private ManualSearchIndex() {}

    public static String buildChapterSearchText(ManualChapter chapter) {
        if (chapter == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, I18n.format(chapter.chapterTitleKey));
        if (chapter.pages != null) {
            for (ManualPage page : chapter.pages) {
                appendPage(sb, page);
            }
        }
        return sb.toString();
    }

    private static void appendPage(StringBuilder sb, ManualPage page) {
        if (page == null) {
            return;
        }
        if (page.titleKey != null && !page.titleKey.isEmpty()) {
            appendLine(sb, I18n.format(page.titleKey));
        }
        if (page.isTextPage() && page.textKey != null && !page.textKey.isEmpty()) {
            appendLine(sb, I18n.format(page.textKey));
        } else if (page.isItemShowcase() && page.items != null) {
            for (ManualItemEntry entry : page.items) {
                ItemStack stack = resolveItem(entry == null ? null : entry.registryName);
                if (stack != null) {
                    appendLine(sb, stack.getDisplayName());
                } else if (entry != null && entry.registryName != null) {
                    appendLine(sb, entry.registryName);
                }
                if (entry != null && entry.descKey != null && !entry.descKey.isEmpty()) {
                    appendLine(sb, I18n.format(entry.descKey));
                }
            }
        } else if (page.isConfigRef() && page.category != null) {
            List<ConfigEntry> entries = ManualDataLoader.getConfigMetadata()
                .get(page.category);
            if (entries != null) {
                for (ConfigEntry entry : entries) {
                    appendLine(sb, entry.category + "." + entry.key);
                    appendLine(sb, entry.type);
                    appendLine(sb, entry.defaultValue);
                    appendLine(sb, entry.description);
                }
            }
        }
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(fixNewlines(text));
    }

    private static String fixNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    private static ItemStack resolveItem(String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return null;
        }
        try {
            Object obj = Item.itemRegistry.getObject(registryName);
            if (obj instanceof Item) {
                return new ItemStack((Item) obj);
            }
            obj = net.minecraft.block.Block.blockRegistry.getObject(registryName);
            if (obj instanceof net.minecraft.block.Block) {
                return new ItemStack((net.minecraft.block.Block) obj);
            }
            if (registryName.contains(":")) {
                String bareName = registryName.substring(registryName.indexOf(':') + 1);
                obj = Item.itemRegistry.getObject(bareName);
                if (obj instanceof Item) {
                    return new ItemStack((Item) obj);
                }
                obj = net.minecraft.block.Block.blockRegistry.getObject(bareName);
                if (obj instanceof net.minecraft.block.Block) {
                    return new ItemStack((net.minecraft.block.Block) obj);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
