package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.AdmGuiTextures;

public class GuiScreenMessage extends ADM_GuiScreen {

    private static final int PANEL_WIDTH = 300;

    public enum MessageType {
        INFO,
        WARNING,
        CUSTOM
    }

    private final MessageType messageType;
    private final String message;
    private final GuiScreen previousScreen;
    private List<String> messageLines = new ArrayList<String>();

    public GuiScreenMessage(EntityPlayer player, World world, MessageType messageType, String message,
        GuiScreen previousScreen) {
        this.messageType = messageType;
        this.message = message;
        this.previousScreen = previousScreen;
        setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        setSize(PANEL_WIDTH, messageType == MessageType.CUSTOM ? 140 : 112);
        setStretch(false);
    }

    @Override
    public void initGui() {
        super.initGui();
        messageLines = fontRendererObj.listFormattedStringToWidth(message != null ? message : "", PANEL_WIDTH - 32);
        if (messageLines.isEmpty()) {
            messageLines.add("");
        }
        int actionHeight = messageType == MessageType.CUSTOM ? 45 : 20;
        setSize(
            PANEL_WIDTH,
            Math.max(messageType == MessageType.CUSTOM ? 140 : 112, 82 + messageLines.size() * 10 + actionHeight));
        setPosition((width - panelWidth()) / 2, (height - panelHeight()) / 2);
        this.buttonList.clear();

        int offsetX = panelX() + 30;
        int offsetY = panelY() + panelHeight() - actionHeight - 12;

        switch (messageType) {
            case INFO -> {
                this.buttonList
                    .add(new ADM_GuiButton(0, offsetX, offsetY, PANEL_WIDTH - 60, 20, I18n.format("adm.button.ok")));
            }
            case WARNING -> {
                this.buttonList.add(
                    new ADM_GuiButton(1, offsetX, offsetY, PANEL_WIDTH - 60, 20, I18n.format("adm.button.confirm")));
            }
            case CUSTOM -> {
                this.buttonList.add(
                    new ADM_GuiButton(2, offsetX, offsetY, PANEL_WIDTH - 60, 20, I18n.format("adm.button.proceed")));
                this.buttonList.add(
                    new ADM_GuiButton(
                        3,
                        offsetX,
                        offsetY + 25,
                        PANEL_WIDTH - 60,
                        20,
                        I18n.format("adm.button.cancel")));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0, 1, 2, 3 -> mc.displayGuiScreen(previousScreen);
        }
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawAdmScreen(mouseX, mouseY, partialTicks);

        String title = switch (messageType) {
            case INFO -> I18n.format("adm.message.info");
            case WARNING -> I18n.format("adm.message.warning");
            case CUSTOM -> I18n.format("adm.message.custom");
        };

        this.drawCenteredString(this.fontRendererObj, title, panelX() + panelWidth() / 2, panelY() + 14, 0xFFFFFF);
        int textY = panelY() + 38;
        for (String line : messageLines) {
            this.drawCenteredString(this.fontRendererObj, line, panelX() + panelWidth() / 2, textY, 0xFFFFFF);
            textY += 10;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
