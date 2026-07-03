package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.network.packet.PacketGrapplePathAction;

/**
 * Grapple hook settings with three modes and saved route list.
 */
public class GuiGrappleSavePrompt extends ADM_GuiScreen {

    private final EntityPlayer player;
    private final Runnable onCancel;
    private final Runnable onDiscardAndContinue;

    private ADM_GuiTextField nameField;
    private int buttonSaveId = 10;
    private int buttonDiscardId = 11;
    private int buttonCancelId = 12;

    public GuiGrappleSavePrompt(EntityPlayer player, Runnable onCancel, Runnable onDiscardAndContinue) {
        this.player = player;
        this.onCancel = onCancel;
        this.onDiscardAndContinue = onDiscardAndContinue;
        this.setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        this.setSize(360, 180);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.setPosition((this.width - 360) / 2, (this.height - 180) / 2);
        this.buttonList.clear();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        nameField = new ADM_GuiTextField(this.fontRendererObj, centerX - 90, centerY - 10, 180, 20);
        nameField.setMaxStringLength(32);
        nameField.setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020);
        nameField.setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020);
        nameField.setText(I18n.format("adm.grapple.default_route_name"));
        nameField.setFocused(true);

        this.buttonList.add(
            new ADM_GuiButton(buttonSaveId, centerX - 150, centerY + 40, 90, 20, I18n.format("adm.button.save"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseHoverEffect(true)
                .setTextColor(0x00FF00)
                .setTextHoverColor(0x55FF55));
        this.buttonList.add(
            new ADM_GuiButton(buttonDiscardId, centerX - 45, centerY + 40, 90, 20, I18n.format("adm.grapple.discard"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseHoverEffect(true)
                .setTextColor(0xFFAA00)
                .setTextHoverColor(0xFFCC55));
        this.buttonList.add(
            new ADM_GuiButton(buttonCancelId, centerX + 60, centerY + 40, 90, 20, I18n.format("adm.button.cancel"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseHoverEffect(true)
                .setTextColor(0xFF5555)
                .setTextHoverColor(0xFF0000));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == buttonSaveId) {
            String name = nameField.getText()
                .trim();
            if (name.isEmpty()) {
                name = I18n.format("adm.grapple.default_route_name");
            }
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.saveRoute(name));
            if (onDiscardAndContinue != null) {
                onDiscardAndContinue.run();
            }
            this.mc.displayGuiScreen(null);
        } else if (button.id == buttonDiscardId) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrapplePathAction.discardBuffer());
            if (onDiscardAndContinue != null) {
                onDiscardAndContinue.run();
            }
            this.mc.displayGuiScreen(null);
        } else if (button.id == buttonCancelId) {
            if (onCancel != null) {
                onCancel.run();
            }
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (nameField.isFocused()) {
            nameField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.grapple.save_prompt_title"),
            centerX,
            centerY - 58,
            0x00FFFF);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.grapple.save_prompt_hint"),
            centerX,
            centerY - 38,
            0xAAAAAA);
        this.drawString(
            this.fontRendererObj,
            I18n.format("adm.grapple.route_name"),
            centerX - 90,
            centerY - 22,
            0x888888);
        nameField.drawTextBox();
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
