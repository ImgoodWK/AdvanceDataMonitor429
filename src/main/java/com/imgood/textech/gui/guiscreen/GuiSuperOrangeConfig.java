package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmItemConfigScreen;
import com.imgood.textech.items.ItemSuperOrange;
import com.imgood.textech.network.packet.PacketSuperOrangeConfig;

public class GuiSuperOrangeConfig extends AdmItemConfigScreen {

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

    public GuiSuperOrangeConfig(ItemStack orangeStack, EntityPlayer player) {
        super(380, 240);
        this.orangeStack = orangeStack;
        this.player = player;
        this.matterBallEnabled = ItemSuperOrange.isMatterBallEnabled(orangeStack);
        this.pickupMatterBallEnabled = ItemSuperOrange.isPickupMatterBallEnabled(orangeStack);
        this.dropMatterBallEnabled = ItemSuperOrange.isDropMatterBallEnabled(orangeStack);
    }

    @Override
    protected void initConfigContent() {
        int cx = centerX();
        int cy = centerY();

        String currentName = ItemSuperOrange.getNameplateText(orangeStack);
        nameField = createTextField(cx - 90, cy - 58, 180, 20);
        nameField.setMaxStringLength(64);
        nameField.setText(currentName != null ? currentName : "");

        multiplierField = createTextField(cx - 30, cy - 18, 60, 20);
        multiplierField.setMaxStringLength(4);
        multiplierField.setText(String.valueOf(ItemSuperOrange.getDropMultiplier(orangeStack)));

        buttonList.add(createToggleButton(BUTTON_MATTER, cx - 180, cy + 18, 118, matterBallLabel()));
        buttonList.add(createToggleButton(BUTTON_PICKUP, cx - 56, cy + 18, 118, pickupMatterBallLabel()));
        buttonList.add(createToggleButton(BUTTON_DROP, cx + 68, cy + 18, 118, dropMatterBallLabel()));
        buttonList.add(createSaveButton(cx - 60, cy + 72));
        buttonList.add(createCancelButton(cx + 10, cy + 72));
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
    protected void onConfigButton(GuiButton button) {
        if (button.id == BUTTON_MATTER) {
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

    @Override
    protected void onSave() {
        try {
            int multiplier = Integer.parseInt(multiplierField.getText().trim());
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
            closeScreen();
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
        int cx = centerX();
        int cy = centerY();
        drawCenteredString(fontRendererObj, I18n.format("adm.title.superOrangeConfig"), cx, cy - 82, 0x00FFFF);
        drawString(fontRendererObj, I18n.format("adm.label.super_orange.rename"), cx - 170, cy - 54, 0xAAAAAA);
        drawString(
            fontRendererObj,
            I18n.format("adm.label.super_orange.multiplier", Config.superOrangeDropMultiplierMax),
            cx - 170,
            cy - 14,
            0xAAAAAA);
        nameField.drawTextBox();
        multiplierField.drawTextBox();
        drawErrorTips(cy + 98);
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
