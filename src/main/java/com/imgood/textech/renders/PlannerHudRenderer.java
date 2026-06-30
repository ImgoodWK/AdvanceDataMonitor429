package com.imgood.textech.renders;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.imgood.textech.items.ItemAdvancePlanner;
import com.imgood.textech.items.PlannerEntry;
import com.imgood.textech.items.PlannerMergeMode;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class PlannerHudRenderer {

    private static final int HUD_COLOR_TITLE = 0x00FFFF;
    private static final int HUD_COLOR_COMPLETED = 0x55FF55;
    private static final int HUD_COLOR_PENDING = 0xFFFFFF;
    private static final int HUD_COLOR_SLOT = 0x888888;
    private static final int LINE_HEIGHT = 12;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        // Search entire inventory for a HUD-enabled planner
        ItemStack planner = findEnabledPlanner(player);
        if (planner == null) return;

        List<PlannerEntry> allEntries = ItemAdvancePlanner.getEntriesSorted(planner, PlannerMergeMode.BY_INDEX);
        int maxDisplay = ItemAdvancePlanner.getHudMaxDisplay(planner);
        float posX = ItemAdvancePlanner.getHudPosX(planner);
        float posY = ItemAdvancePlanner.getHudPosY(planner);
        float scale = ItemAdvancePlanner.getHudScale(planner);
        int hudWidth = ItemAdvancePlanner.getHudWidth(planner);

        // Filter out completed entries
        List<PlannerEntry> entries = new ArrayList<>();
        for (PlannerEntry e : allEntries) {
            if (!e.isCompleted()) {
                entries.add(e);
            }
        }

        if (entries.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        FontRenderer fr = mc.fontRenderer;

        int lineHeight = (int) (LINE_HEIGHT * scale);
        int padding = (int) (4 * scale);
        int maxWidth = (int) (hudWidth * scale);
        int titleHeight = (int) (14 * scale);
        int textStartX = (int) (padding + 18 * scale);
        int textMaxWidth = maxWidth - (int) (40 * scale);

        int displayCount = Math.min(entries.size(), maxDisplay);

        // Compute total height with multi-line entries
        int totalEntryHeight = 0;
        for (int i = 0; i < displayCount; i++) {
            PlannerEntry entry = entries.get(i);
            String text = getDisplayText(entry);
            List<String> lines = fr.listFormattedStringToWidth(text, textMaxWidth);
            totalEntryHeight += lineHeight * Math.max(1, lines.size());
        }

        int bgWidth = maxWidth + padding * 2;
        int bgHeight = padding + titleHeight + totalEntryHeight + padding;

        int x = (int) (posX * (sr.getScaledWidth() - bgWidth));
        int y = (int) (posY * (sr.getScaledHeight() - bgHeight));

        // Title (custom or default)
        String title = ItemAdvancePlanner.getHudTitle(planner);
        if (title.isEmpty()) {
            title = net.minecraft.client.resources.I18n.format("adm.planner.title");
        }
        fr.drawStringWithShadow(title, x + padding, y + padding, HUD_COLOR_TITLE);

        int lineY = y + padding + titleHeight;
        for (int i = 0; i < displayCount; i++) {
            PlannerEntry entry = entries.get(i);

            // Slot number
            String slot = "#" + entry.getSlotIndex();
            int slotX = (int) (x + padding);
            fr.drawStringWithShadow(slot, slotX, lineY, HUD_COLOR_SLOT);

            // Text (wrapped to multiple lines)
            String text = getDisplayText(entry);
            List<String> lines = fr.listFormattedStringToWidth(text, textMaxWidth);
            if (lines.isEmpty()) {
                lines.add("...");
            }
            for (int li = 0; li < lines.size(); li++) {
                fr.drawStringWithShadow(lines.get(li), textStartX + x, lineY, HUD_COLOR_PENDING);
                lineY += lineHeight;
            }
        }
    }

    private String getDisplayText(PlannerEntry entry) {
        String text = entry.getText();
        if (text == null || text.isEmpty()) {
            return "...";
        }
        return text;
    }

    private ItemStack findEnabledPlanner(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        // Check hotbar first (slots 0-8), then main inventory (slots 9-35)
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAdvancePlanner) {
                if (ItemAdvancePlanner.isHudEnabled(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }
}
