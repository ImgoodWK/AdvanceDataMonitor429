package com.imgood.advancedatamonitor.gui.guiscreen;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.items.ItemAdvancePlanner;
import com.imgood.advancedatamonitor.items.PlannerMergeMode;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerMerge;

public class GuiPlannerMergeConfirm extends ADM_GuiScreen {

    private final ItemStack currentStack;
    private final EntityPlayer player;

    private List<ItemStack> plannerStacks;
    private int totalPlanners;
    private int totalEntries;
    private PlannerMergeMode selectedMode = PlannerMergeMode.BY_TIME;

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation BUTTON_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");

    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;
    private int warningColor = 0xFFFF55;

    private int buttonTimeId = 200;
    private int buttonIndexId = 201;
    private int buttonConfirmId = 202;
    private int buttonCancelId = 203;

    public GuiPlannerMergeConfirm(ItemStack currentStack, EntityPlayer player) {
        this.currentStack = currentStack;
        this.player = player;
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(350, 220);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        plannerStacks = ItemAdvancePlanner.getPlannerStacksInInventory(player);
        totalPlanners = plannerStacks.size();
        totalEntries = 0;
        for (ItemStack stack : plannerStacks) {
            totalEntries += ItemAdvancePlanner.getEntryCount(stack);
        }

        this.setPosition((this.width - 350) / 2, (this.height - 220) / 2);

        this.buttonList.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.buttonList.add(
            new ADM_GuiButton(
                buttonTimeId,
                centerX - 120,
                centerY - 10,
                110,
                20,
                I18n.format("adm.planner.merge_by_time")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setUseRGBEffect(selectedMode == PlannerMergeMode.BY_TIME)
                    .setTextColor(selectedMode == PlannerMergeMode.BY_TIME ? 0x00FF00 : textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                buttonIndexId,
                centerX + 10,
                centerY - 10,
                110,
                20,
                I18n.format("adm.planner.merge_by_index")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setUseRGBEffect(selectedMode == PlannerMergeMode.BY_INDEX)
                    .setTextColor(selectedMode == PlannerMergeMode.BY_INDEX ? 0x00FF00 : textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                buttonConfirmId,
                centerX - 80,
                centerY + 50,
                70,
                20,
                I18n.format("adm.planner.confirm_merge")).setTexture(BUTTON_TEXTURE)
                    .setHoverTexture(BUTTON_HOVER_TEXTURE)
                    .setUseHoverEffect(true)
                    .setTextColor(0x00FF00)
                    .setTextHoverColor(0x55FF55));

        this.buttonList.add(
            new ADM_GuiButton(buttonCancelId, centerX + 10, centerY + 50, 70, 20, I18n.format("adm.planner.cancel"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(0xFF5555)
                .setTextHoverColor(0xFF0000));

        updateScreen();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == buttonTimeId) {
            selectedMode = PlannerMergeMode.BY_TIME;
            initGui();
        } else if (button.id == buttonIndexId) {
            selectedMode = PlannerMergeMode.BY_INDEX;
            initGui();
        } else if (button.id == buttonConfirmId) {
            executeMerge();
        } else if (button.id == buttonCancelId) {
            this.mc.displayGuiScreen(new GuiAdvancePlanner(currentStack, player));
        }
    }

    private void executeMerge() {
        if (totalPlanners < 2) {
            return;
        }

        AdvanceDataMonitor.ADMCHANEL.sendToServer(new PacketPlannerMerge(selectedMode));

        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.planner.merge_confirm_title"),
            centerX,
            centerY - 70,
            textColor);

        String prompt = I18n.format("adm.planner.merge_prompt", totalPlanners, totalEntries);
        this.drawCenteredString(this.fontRendererObj, prompt, centerX, centerY - 45, warningColor);

        // Draw individual planner details
        int detailY = centerY - 30;
        for (int i = 0; i < plannerStacks.size() && i < 3; i++) {
            ItemStack stack = plannerStacks.get(i);
            int count = ItemAdvancePlanner.getEntryCount(stack);
            boolean isCurrent = (stack == currentStack);
            String label = I18n.format("adm.planner.planner_detail", i + 1, count);
            if (isCurrent) {
                label += " " + I18n.format("adm.planner.current");
            }
            this.drawCenteredString(this.fontRendererObj, label, centerX, detailY, isCurrent ? 0x55FF55 : 0xAAAAAA);
            detailY += 12;
        }

        String modeLabel = I18n.format("adm.planner.selected_mode") + " "
            + I18n.format(
                selectedMode == PlannerMergeMode.BY_TIME ? "adm.planner.merge_by_time" : "adm.planner.merge_by_index");
        this.drawCenteredString(this.fontRendererObj, modeLabel, centerX, centerY + 25, textColor);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        // nothing to clean up
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
