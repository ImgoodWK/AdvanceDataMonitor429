package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.network.packet.PacketSuperOrangeConfig;

public class GuiSuperOrangeConfig extends ADM_GuiScreen {

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

    private static final int BUTTON_SAVE = 0;
    private static final int BUTTON_CANCEL = 1;
    private static final int BUTTON_MATTER = 2;
    private static final int BUTTON_PICKUP = 3;
    private static final int BUTTON_DROP = 4;

    private final ItemStack orangeStack;
    private final EntityPlayer player;

    private ADM_GuiTextField nameField;
    private ADM_GuiTextField multiplierField;
    private boolean matterBallEnabled;
    private boolean pickupMatterBallEnabled;
    private boolean dropMatterBallEnabled;
    private String errorTips = "";

    public GuiSuperOrangeConfig(ItemStack orangeStack, EntityPlayer player) {
        this.orangeStack = orangeStack;
        this.player = player;
        this.matterBallEnabled = ItemSuperOrange.isMatterBallEnabled(orangeStack);
        this.pickupMatterBallEnabled = ItemSuperOrange.isPickupMatterBallEnabled(orangeStack);
        this.dropMatterBallEnabled = ItemSuperOrange.isDropMatterBallEnabled(orangeStack);
        setBackgroundTexture(BACKGROUND_TEXTURE);
        setSize(380, 240);
        setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        setPosition((width - 380) / 2, (height - 240) / 2);
        buttonList.clear();
        int centerX = width / 2;
        int centerY = height / 2;

        String currentName = ItemSuperOrange.getNameplateText(orangeStack);
        nameField = new ADM_GuiTextField(fontRendererObj, centerX - 90, centerY - 58, 180, 20);
        nameField.setMaxStringLength(64);
        nameField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        nameField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        nameField.setText(currentName != null ? currentName : "");

        multiplierField = new ADM_GuiTextField(fontRendererObj, centerX - 30, centerY - 18, 60, 20);
        multiplierField.setMaxStringLength(4);
        multiplierField.setBackgroundTexture(TEXTFIELD_TEXTURE);
        multiplierField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        multiplierField.setText(String.valueOf(ItemSuperOrange.getDropMultiplier(orangeStack)));

        buttonList.add(toggleButton(BUTTON_MATTER, centerX - 180, centerY + 18, 118, matterBallLabel()));
        buttonList.add(toggleButton(BUTTON_PICKUP, centerX - 56, centerY + 18, 118, pickupMatterBallLabel()));
        buttonList.add(toggleButton(BUTTON_DROP, centerX + 68, centerY + 18, 118, dropMatterBallLabel()));
        buttonList.add(actionButton(BUTTON_SAVE, centerX - 60, centerY + 72, I18n.format("adm.button.save"), 0x00FF00, 0x55FF55));
        buttonList.add(actionButton(BUTTON_CANCEL, centerX + 10, centerY + 72, I18n.format("adm.button.cancel"), 0xFF5555, 0xFF0000));
    }

    private ADM_GuiButton toggleButton(int id, int x, int y, int width, String label) {
        return new ADM_GuiButton(id, x, y, width, 20, label).setTexture(BUTTON_TEXTURE)
            .setHoverTexture(BUTTON_HOVER_TEXTURE)
            .setUseHoverEffect(true)
            .setTextColor(0x00FFFF)
            .setTextHoverColor(0x55FFFF);
    }

    private ADM_GuiButton actionButton(int id, int x, int y, String label, int color, int hoverColor) {
        return new ADM_GuiButton(id, x, y, 50, 20, label).setTexture(BUTTON_TEXTURE)
            .setHoverTexture(BUTTON_HOVER_TEXTURE)
            .setUseHoverEffect(true)
            .setTextColor(color)
            .setTextHoverColor(hoverColor);
    }

    private String matterBallLabel() {
        String state = matterBallEnabled ? I18n.format("adm.button.disable") : I18n.format("adm.button.enable");
        return state + " " + I18n.format("adm.label.super_orange.matter_ball_short");
    }

    private String pickupMatterBallLabel() {
        String state = pickupMatterBallEnabled ? I18n.format("adm.button.disable") : I18n.format("adm.button.enable");
        return state + " " + I18n.format("adm.label.super_orange.pickup_matter_short");
    }

    private String dropMatterBallLabel() {
        String state = dropMatterBallEnabled ? I18n.format("adm.button.disable") : I18n.format("adm.button.enable");
        return state + " " + I18n.format("adm.label.super_orange.drop_matter_short");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_SAVE) {
            saveConfig();
        } else if (button.id == BUTTON_CANCEL) {
            mc.displayGuiScreen(null);
        } else if (button.id == BUTTON_MATTER) {
            matterBallEnabled = !matterBallEnabled;
            button.displayString = matterBallLabel();
        } else if (button.id == BUTTON_PICKUP) {
            pickupMatterBallEnabled = !pickupMatterBallEnabled;
            button.displayString = pickupMatterBallLabel();
        } else if (button.id == BUTTON_DROP) {
            dropMatterBallEnabled = !dropMatterBallEnabled;
            button.displayString = dropMatterBallLabel();
        }
    }

    private void saveConfig() {
        try {
            int multiplier = Integer.parseInt(multiplierField.getText()
                .trim());
            int max = Math.max(1, Config.superOrangeDropMultiplierMax);
            if (multiplier < 1 || multiplier > max) {
                errorTips = I18n.format("adm.error.super_orange.multiplier_range", max);
                return;
            }
            AdvanceDataMonitor.ADMCHANEL.sendToServer(
                new PacketSuperOrangeConfig(
                    nameField.getText(),
                    matterBallEnabled,
                    pickupMatterBallEnabled,
                    dropMatterBallEnabled,
                    multiplier));
            mc.displayGuiScreen(null);
        } catch (NumberFormatException e) {
            errorTips = I18n.format("adm.error.invalid_number");
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (nameField.isFocused() || multiplierField.isFocused()) {
            if (nameField.isFocused()) {
                nameField.textboxKeyTyped(typedChar, keyCode);
            } else {
                multiplierField.textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        multiplierField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        int centerX = width / 2;
        int centerY = height / 2;
        drawCenteredString(fontRendererObj, I18n.format("adm.title.superOrangeConfig"), centerX, centerY - 82, 0x00FFFF);
        drawString(
            fontRendererObj,
            I18n.format("adm.label.super_orange.rename"),
            centerX - 170,
            centerY - 54,
            0xAAAAAA);
        drawString(
            fontRendererObj,
            I18n.format("adm.label.super_orange.multiplier", Config.superOrangeDropMultiplierMax),
            centerX - 170,
            centerY - 14,
            0xAAAAAA);
        nameField.drawTextBox();
        multiplierField.drawTextBox();
        if (!errorTips.isEmpty()) {
            drawCenteredString(fontRendererObj, errorTips, centerX, centerY + 98, 0xFF5555);
        }
        drawButtonTooltip(BUTTON_MATTER, mouseX, mouseY, I18n.format("adm.tooltip.super_orange.matter_ball_toggle"));
        drawButtonTooltip(BUTTON_PICKUP, mouseX, mouseY, I18n.format("adm.tooltip.super_orange.pickup_matter_toggle"));
        drawButtonTooltip(BUTTON_DROP, mouseX, mouseY, I18n.format("adm.tooltip.super_orange.drop_matter_toggle"));
    }

    private void drawButtonTooltip(int buttonId, int mouseX, int mouseY, String text) {
        for (Object obj : buttonList) {
            GuiButton button = (GuiButton) obj;
            if (button.id == buttonId && button.mousePressed(mc, mouseX, mouseY)) {
                java.util.ArrayList<String> lines = new java.util.ArrayList<>();
                lines.add(text);
                drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
                break;
            }
        }
    }
}
