package com.imgood.advancedatamonitor.renders;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.imgood.advancedatamonitor.gui.manual.ManualDataLoader;
import com.imgood.advancedatamonitor.gui.manual.ManualDataLoader.ConfigEntry;
import com.imgood.advancedatamonitor.gui.manual.ManualPage;
import com.imgood.advancedatamonitor.gui.manual.ManualPage.ManualItemEntry;
import com.imgood.advancedatamonitor.gui.manual.ManualTextHighlighter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Handles rendering different page types inside the manual GUI.
 */
@SideOnly(Side.CLIENT)
public class ManualPageRenderer {

    private static final RenderItem itemRenderer = new RenderItem();
    private static final int ITEM_ICON_SIZE = 16;
    private static final int LINE_HEIGHT = 10;
    // Blue-cyan scheme: light text on dark bg
    private static final int TEXT_COLOR = 0xFFD0E8FF;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SEPARATOR_COLOR = 0xFF3068A0;
    private static final int CONFIG_KEY_COLOR = 0xFF50D0FF;
    private static final int CONFIG_VAL_COLOR = 0xFFB0D0F0;
    private static final int CONFIG_DESC_COLOR = 0xFF90C0E8;
    private static final int ITEM_NAME_COLOR = 0xFFC0E8FF;

    /**
     * Convert literal \n in lang file values to actual newlines.
     * Minecraft 1.7.10 .lang files store \n as two characters, not a newline.
     */
    private static String fixNewlines(String text) {
        if (text == null) return "";
        return text.replace("\\n", "\n");
    }

    /**
     * Render a text page with title and body.
     */
    public static void renderTextPage(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight) {
        renderTextPage(font, page, x, y, maxWidth, contentHeight, "");
    }

    public static void renderTextPage(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight, String searchQuery) {
        int currentY = y;

        if (page.titleKey != null && !page.titleKey.isEmpty()) {
            String title = fixNewlines(I18n.format(page.titleKey));
            drawHighlightedLine(font, title, x, currentY, searchQuery, TITLE_COLOR);
            currentY += LINE_HEIGHT + 4;
            font.drawString("------------------------", x, currentY, SEPARATOR_COLOR);
            currentY += LINE_HEIGHT + 6;
        }

        if (page.textKey != null && !page.textKey.isEmpty()) {
            String text = fixNewlines(I18n.format(page.textKey));
            List<String> lines = font.listFormattedStringToWidth(text, maxWidth);
            for (String line : lines) {
                if (currentY + LINE_HEIGHT > y + contentHeight) break;
                drawHighlightedLine(font, line, x, currentY, searchQuery, TEXT_COLOR);
                currentY += LINE_HEIGHT;
            }
        }
    }

    /**
     * Render an item showcase page with item icons and descriptions.
     */
    public static void renderItemShowcase(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight) {
        renderItemShowcase(font, page, x, y, maxWidth, contentHeight, "");
    }

    public static void renderItemShowcase(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight, String searchQuery) {
        int currentY = y;

        if (page.titleKey != null && !page.titleKey.isEmpty()) {
            String title = fixNewlines(I18n.format(page.titleKey));
            drawHighlightedLine(font, title, x, currentY, searchQuery, TITLE_COLOR);
            currentY += LINE_HEIGHT + 4;
            font.drawString("------------------------", x, currentY, SEPARATOR_COLOR);
            currentY += LINE_HEIGHT + 6;
        }

        if (page.items == null) return;

        for (ManualItemEntry entry : page.items) {
            if (currentY + ITEM_ICON_SIZE > y + contentHeight) break;

            ItemStack stack = getItemStack(entry.registryName);
            if (stack != null) {
                renderItemIcon(stack, x, currentY);
            }

            String itemName = stack != null ? stack.getDisplayName() : entry.registryName;
            drawHighlightedLine(font, itemName, x + ITEM_ICON_SIZE + 4, currentY, searchQuery, ITEM_NAME_COLOR);

            if (entry.descKey != null && !entry.descKey.isEmpty()) {
                String desc = fixNewlines(I18n.format(entry.descKey));
                List<String> descLines = font.listFormattedStringToWidth(desc, maxWidth - ITEM_ICON_SIZE - 4);
                int descY = currentY + LINE_HEIGHT;
                for (String line : descLines) {
                    if (descY + LINE_HEIGHT > y + contentHeight) break;
                    drawHighlightedLine(font, line, x + ITEM_ICON_SIZE + 4, descY, searchQuery, CONFIG_DESC_COLOR);
                    descY += LINE_HEIGHT;
                }
                currentY = descY + 4;
            } else {
                currentY += ITEM_ICON_SIZE + 4;
            }
        }
    }

    /**
     * Render a config reference page showing key-value pairs.
     */
    public static void renderConfigRef(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight) {
        renderConfigRef(font, page, x, y, maxWidth, contentHeight, "");
    }

    public static void renderConfigRef(FontRenderer font, ManualPage page, int x, int y, int maxWidth,
        int contentHeight, String searchQuery) {
        int currentY = y;

        if (page.titleKey != null && !page.titleKey.isEmpty()) {
            String title = fixNewlines(I18n.format(page.titleKey));
            drawHighlightedLine(font, title, x, currentY, searchQuery, TITLE_COLOR);
            currentY += LINE_HEIGHT + 4;
            font.drawString("------------------------", x, currentY, SEPARATOR_COLOR);
            currentY += LINE_HEIGHT + 6;
        }

        if (page.category == null) return;

        List<ConfigEntry> entries = ManualDataLoader.getConfigMetadata()
            .get(page.category);
        if (entries == null) return;

        for (ConfigEntry entry : entries) {
            if (currentY + LINE_HEIGHT * 3 > y + contentHeight) break;

            String keyLine = entry.category + "." + entry.key + "  [" + entry.type + "]";
            drawHighlightedLine(font, keyLine, x, currentY, searchQuery, CONFIG_KEY_COLOR);
            currentY += LINE_HEIGHT;

            String defaultLine = "  " + I18n.format("adm.manual.config_reference.default_label") + entry.defaultValue;
            drawHighlightedLine(font, defaultLine, x, currentY, searchQuery, CONFIG_VAL_COLOR);
            currentY += LINE_HEIGHT;

            String desc = fixNewlines(entry.description);
            List<String> descLines = font.listFormattedStringToWidth(desc, maxWidth - 4);
            for (String line : descLines) {
                if (currentY + LINE_HEIGHT > y + contentHeight) break;
                drawHighlightedLine(font, "  " + line, x, currentY, searchQuery, CONFIG_DESC_COLOR);
                currentY += LINE_HEIGHT;
            }
            currentY += 2;
        }
    }

    private static void drawHighlightedLine(FontRenderer font, String line, int x, int y, String searchQuery,
        int normalColor) {
        ManualTextHighlighter.drawLine(font, line, x, y, searchQuery, normalColor);
    }

    /**
     * Get an ItemStack for a registry name string.
     * Tries modid:name format first, then bare name.
     */
    private static ItemStack getItemStack(String registryName) {
        if (registryName == null || registryName.isEmpty()) return null;
        try {
            // Try full name first (e.g. "advancedatamonitor:data_imprint")
            Object obj = Item.itemRegistry.getObject(registryName);
            if (obj instanceof Item) return new ItemStack((Item) obj);
            // Try block registry with full name
            obj = net.minecraft.block.Block.blockRegistry.getObject(registryName);
            if (obj instanceof net.minecraft.block.Block) return new ItemStack((net.minecraft.block.Block) obj);
            // Try bare name (strip modid prefix)
            if (registryName.contains(":")) {
                String bareName = registryName.substring(registryName.indexOf(':') + 1);
                obj = Item.itemRegistry.getObject(bareName);
                if (obj instanceof Item) return new ItemStack((Item) obj);
                obj = net.minecraft.block.Block.blockRegistry.getObject(bareName);
                if (obj instanceof net.minecraft.block.Block) return new ItemStack((net.minecraft.block.Block) obj);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Render a 16x16 item icon at the given position.
     */
    private static void renderItemIcon(ItemStack stack, int x, int y) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRenderer.renderItemAndEffectIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        itemRenderer.renderItemOverlayIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}
