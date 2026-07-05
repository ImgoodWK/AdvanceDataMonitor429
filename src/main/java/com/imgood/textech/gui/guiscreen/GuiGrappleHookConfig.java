package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmItemConfigScreen;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.packet.PacketGrappleHookConfig;

/**
 * Display names / 显示名称:
 * - EN: Grapple Hook Settings
 * - ZH: 挂索器设置
 * Lang keys: adm.title.grappleHookConfig
 */
public class GuiGrappleHookConfig extends AdmItemConfigScreen {

    private final ItemStack hookStack;
    private final EntityPlayer player;

    private ADM_GuiTextField speedField;
    private boolean showNodeName;
    private boolean showNodeDistance;

    private static final int BUTTON_SHOW_NAME = 2;
    private static final int BUTTON_SHOW_DISTANCE = 3;

    public GuiGrappleHookConfig(ItemStack hookStack, EntityPlayer player) {
        super(360, 220);
        this.hookStack = hookStack;
        this.player = player;
        this.showNodeName = ItemGrappleHook.getShowNodeName(hookStack);
        this.showNodeDistance = ItemGrappleHook.getShowNodeDistance(hookStack);
    }

    @Override
    protected void initConfigContent() {
        int cx = centerX();
        int cy = centerY();

        speedField = createTextField(cx - 10, cy - 38, 80, 20);
        speedField.setMaxStringLength(6);
        speedField.setText(String.format("%.1f", ItemGrappleHook.getTravelSpeed(hookStack)));
        speedField.setFocused(true);

        buttonList.add(
            createToggleButton(
                BUTTON_SHOW_NAME,
                cx - 150,
                cy - 2,
                120,
                I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable") + " "
                    + I18n.format("adm.label.grapple.show_node_name_short")));
        buttonList.add(
            createToggleButton(
                BUTTON_SHOW_DISTANCE,
                cx + 30,
                cy - 2,
                120,
                I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable") + " "
                    + I18n.format("adm.label.grapple.show_node_distance_short")));
        buttonList.add(createSaveButton(cx - 60, cy + 72));
        buttonList.add(createCancelButton(cx + 10, cy + 72));
    }

    @Override
    protected void onConfigButton(GuiButton button) {
        if (button.id == BUTTON_SHOW_NAME) {
            showNodeName = !showNodeName;
            button.displayString = I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable") + " "
                + I18n.format("adm.label.grapple.show_node_name_short");
        } else if (button.id == BUTTON_SHOW_DISTANCE) {
            showNodeDistance = !showNodeDistance;
            button.displayString = I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable") + " "
                + I18n.format("adm.label.grapple.show_node_distance_short");
        }
    }

    @Override
    protected void onSave() {
        try {
            double speed = Double.parseDouble(
                speedField.getText()
                    .trim());
            if (speed < 0.1D || speed > 5.0D) {
                errorTips = I18n.format("adm.grapple.speed_hint");
                return;
            }
            ItemGrappleHook.setTravelSpeed(hookStack, speed);
            ItemGrappleHook.setShowNodeName(hookStack, showNodeName);
            ItemGrappleHook.setShowNodeDistance(hookStack, showNodeDistance);
            AdvanceDataMonitor.ADMCHANEL
                .sendToServer(new PacketGrappleHookConfig(speed, showNodeName, showNodeDistance));
            closeScreen();
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (speedField.isFocused()) {
            speedField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        speedField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int cx = centerX();
        int cy = centerY();
        drawCenteredString(fontRendererObj, I18n.format("adm.title.grappleHookConfig"), cx, cy - 78, 0x00FFFF);
        drawString(fontRendererObj, I18n.format("adm.label.grapple.travel_speed_setting"), cx - 150, cy - 34, 0xAAAAAA);
        speedField.drawTextBox();
        drawErrorTips(cy + 48);
        drawCenteredString(fontRendererObj, I18n.format("adm.grapple.speed_hint"), cx, cy + 96, 0x666666);
    }
}
