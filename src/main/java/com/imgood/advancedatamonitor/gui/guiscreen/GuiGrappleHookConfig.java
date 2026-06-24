package com.imgood.advancedatamonitor.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleHookConfig;

/**
 * Display names / 显示名称:
 * - EN: Grapple Hook Settings
 * - ZH: 挂索器设置
 * Lang keys: adm.title.grappleHookConfig
 */
public class GuiGrappleHookConfig extends ADM_GuiScreen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/background_ADM_Sub.png");

    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/button_ADM.png");

    private static final ResourceLocation BUTTON_HOVER_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/button_hover_ADM.png");

    private static final ResourceLocation TEXTFIELD_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/textfield_ADM_8020.png");

    private static final ResourceLocation TEXTFIELD_HOVER_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/textfield_hover_ADM_8020.png");

    private final ItemStack hookStack;

    private final EntityPlayer player;

    private ADM_GuiTextField speedField;

    private boolean showNodeName;

    private boolean showNodeDistance;

    private String errorTips = "";

    private int buttonSaveId = 0;

    private int buttonCancelId = 1;

    private int buttonShowNameId = 2;

    private int buttonShowDistanceId = 3;

    public GuiGrappleHookConfig(ItemStack hookStack, EntityPlayer player) {

        this.hookStack = hookStack;

        this.player = player;

        this.showNodeName = ItemGrappleHook.getShowNodeName(hookStack);

        this.showNodeDistance = ItemGrappleHook.getShowNodeDistance(hookStack);

        this.setBackgroundTexture(BACKGROUND_TEXTURE);

        this.setSize(360, 220);

        this.setStretch(false);

    }

    @Override

    public void initGui() {

        Keyboard.enableRepeatEvents(true);

        this.setPosition((this.width - 360) / 2, (this.height - 220) / 2);

        this.buttonList.clear();

        int centerX = this.width / 2;

        int centerY = this.height / 2;

        speedField = new ADM_GuiTextField(this.fontRendererObj, centerX - 10, centerY - 38, 80, 20);

        speedField.setMaxStringLength(6);

        speedField.setBackgroundTexture(TEXTFIELD_TEXTURE);

        speedField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);

        speedField.setText(String.format("%.1f", ItemGrappleHook.getTravelSpeed(hookStack)));

        speedField.setFocused(true);

        this.buttonList.add(

            new ADM_GuiButton(

                buttonShowNameId,

                centerX - 150,

                centerY - 2,

                120,

                20,

                I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable")

                    + " "

                    + I18n.format("adm.label.grapple.show_node_name_short"))

                        .setTexture(BUTTON_TEXTURE)

                        .setHoverTexture(BUTTON_HOVER_TEXTURE)

                        .setUseHoverEffect(true)

                        .setTextColor(0x00FFFF)

                        .setTextHoverColor(0x55FFFF));

        this.buttonList.add(

            new ADM_GuiButton(

                buttonShowDistanceId,

                centerX + 30,

                centerY - 2,

                120,

                20,

                I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable")

                    + " "

                    + I18n.format("adm.label.grapple.show_node_distance_short"))

                        .setTexture(BUTTON_TEXTURE)

                        .setHoverTexture(BUTTON_HOVER_TEXTURE)

                        .setUseHoverEffect(true)

                        .setTextColor(0x00FFFF)

                        .setTextHoverColor(0x55FFFF));

        this.buttonList.add(

            new ADM_GuiButton(buttonSaveId, centerX - 60, centerY + 72, 50, 20, I18n.format("adm.button.save"))

                .setTexture(BUTTON_TEXTURE)

                .setHoverTexture(BUTTON_HOVER_TEXTURE)

                .setUseHoverEffect(true)

                .setTextColor(0x00FF00)

                .setTextHoverColor(0x55FF55));

        this.buttonList.add(

            new ADM_GuiButton(buttonCancelId, centerX + 10, centerY + 72, 50, 20, I18n.format("adm.button.cancel"))

                .setTexture(BUTTON_TEXTURE)

                .setHoverTexture(BUTTON_HOVER_TEXTURE)

                .setUseHoverEffect(true)

                .setTextColor(0xFF5555)

                .setTextHoverColor(0xFF0000));

        updateScreen();

    }

    @Override

    protected void actionPerformed(GuiButton button) {

        if (button.id == buttonSaveId) {

            saveConfig();

        } else if (button.id == buttonCancelId) {

            this.mc.displayGuiScreen(null);

        } else if (button.id == buttonShowNameId) {

            showNodeName = !showNodeName;

            button.displayString = I18n.format(showNodeName ? "adm.button.disable" : "adm.button.enable")

                + " "

                + I18n.format("adm.label.grapple.show_node_name_short");

        } else if (button.id == buttonShowDistanceId) {

            showNodeDistance = !showNodeDistance;

            button.displayString = I18n.format(showNodeDistance ? "adm.button.disable" : "adm.button.enable")

                + " "

                + I18n.format("adm.label.grapple.show_node_distance_short");

        }

    }

    private void saveConfig() {

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

            AdvanceDataMonitor.ADMCHANEL.sendToServer(

                new PacketGrappleHookConfig(speed, showNodeName, showNodeDistance));

            this.mc.displayGuiScreen(null);

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

        int centerX = this.width / 2;

        int centerY = this.height / 2;

        this.drawCenteredString(

            this.fontRendererObj,

            I18n.format("adm.title.grappleHookConfig"),

            centerX,

            centerY - 78,

            0x00FFFF);

        this.drawString(

            this.fontRendererObj,

            I18n.format("adm.label.grapple.travel_speed_setting"),

            centerX - 150,

            centerY - 34,

            0xAAAAAA);

        speedField.drawTextBox();

        if (!errorTips.isEmpty()) {

            this.drawCenteredString(this.fontRendererObj, errorTips, centerX, centerY + 48, 0xFF5555);

        }

        this.drawCenteredString(

            this.fontRendererObj,

            I18n.format("adm.grapple.speed_hint"),

            centerX,

            centerY + 96,

            0x666666);

    }

    @Override

    public void onGuiClosed() {

        Keyboard.enableRepeatEvents(false);

        super.onGuiClosed();

    }

    @Override

    public boolean doesGuiPauseGame() {

        return false;

    }

}
