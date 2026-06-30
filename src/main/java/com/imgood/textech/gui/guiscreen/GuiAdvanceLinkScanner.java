package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.items.ItemAdvanceLinkScanner;
import com.imgood.textech.items.LinkScanEntry;
import com.imgood.textech.network.packet.PacketLinkScannerAction;

/**
 * Display names / 显示名称:
 * - EN: Advance Link Scanner
 * - ZH: 高级链接扫描器
 * Lang keys: adm.scanner.title
 */
public class GuiAdvanceLinkScanner extends ADM_GuiScreen {

    private final ItemStack scannerStack;
    private final EntityPlayer player;

    private List<LinkScanEntry> visibleEntries = new ArrayList<LinkScanEntry>();
    private int scrollOffset = 0;
    private int selectedSlotIndex = -1;

    private int listStartX;
    private int listStartY;
    private int listWidth;
    private int visibleAreaHeight;
    private int rowHeight = 20;

    private ADM_GuiTextField aliasField;

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

    private static final int BUTTON_OWNER_FILTER = 100;
    private static final int BUTTON_NAME_FILTER = 101;
    private static final int BUTTON_EXIT = 102;
    private static final int BUTTON_SAVE_ALIAS = 103;

    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;
    private int slotNumberColor = 0x888888;
    private int selectedBgColor = 0x4000AAFF;
    private int aliasEmptyColor = 0x666666;

    public GuiAdvanceLinkScanner(ItemStack scannerStack, EntityPlayer player) {
        this.scannerStack = scannerStack;
        this.player = player;
        this.setBackgroundTexture(BACKGROUND_TEXTURE);
        this.setSize(430, 330);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        refreshVisibleEntries();

        this.setPosition((this.width - 400) / 2, (this.height - 300) / 2);
        this.listStartX = (this.width / 2) - 170;
        this.listStartY = (this.height / 2) - 90;
        this.listWidth = 340;
        this.visibleAreaHeight = 7 * rowHeight;

        this.buttonList.clear();
        int bottomY = this.height / 2 + 110;

        this.buttonList.add(
            new ADM_GuiButton(BUTTON_OWNER_FILTER, this.width / 2 - 170, bottomY, 100, 20, getOwnerFilterLabel())
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(BUTTON_NAME_FILTER, this.width / 2 - 60, bottomY, 100, 20, getNameFilterLabel())
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(BUTTON_EXIT, this.width / 2 + 50, bottomY, 60, 20, I18n.format("adm.scanner.exit"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        updateAliasField();
        updateScreen();
    }

    private void refreshVisibleEntries() {
        visibleEntries.clear();
        String playerName = player.getCommandSenderName();
        List<LinkScanEntry> all = ItemAdvanceLinkScanner.getAllEntries(scannerStack);
        for (int i = 0; i < all.size(); i++) {
            LinkScanEntry entry = all.get(i);
            if (ItemAdvanceLinkScanner.passesFilters(entry, scannerStack, playerName)) {
                visibleEntries.add(entry);
            }
        }
    }

    private String getOwnerFilterLabel() {
        int filter = ItemAdvanceLinkScanner.getOwnerFilter(scannerStack);
        if (filter == ItemAdvanceLinkScanner.OWNER_FILTER_SELF) {
            return I18n.format("adm.scanner.filter_owner_self");
        }
        if (filter == ItemAdvanceLinkScanner.OWNER_FILTER_OTHERS) {
            return I18n.format("adm.scanner.filter_owner_others");
        }
        return I18n.format("adm.scanner.filter_owner_all");
    }

    private String getNameFilterLabel() {
        int filter = ItemAdvanceLinkScanner.getNameFilter(scannerStack);
        if (filter == ItemAdvanceLinkScanner.NAME_FILTER_UNNAMED) {
            return I18n.format("adm.scanner.filter_name_unnamed");
        }
        if (filter == ItemAdvanceLinkScanner.NAME_FILTER_NAMED) {
            return I18n.format("adm.scanner.filter_name_named");
        }
        return I18n.format("adm.scanner.filter_name_all");
    }

    private void updateAliasField() {
        int fieldX = listStartX + 50;
        int fieldY = listStartY + visibleAreaHeight + 28;
        int fieldWidth = listWidth - 120;

        if (aliasField == null) {
            aliasField = new ADM_GuiTextField(this.fontRendererObj, fieldX, fieldY, fieldWidth, 16);
            aliasField.setMaxStringLength(48);
            aliasField.setBackgroundTexture(TEXTFIELD_TEXTURE);
            aliasField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
            aliasField.setHintText(I18n.format("adm.scanner.alias_hint"));
        }

        this.buttonList.removeIf(b -> b.id == BUTTON_SAVE_ALIAS);
        if (selectedSlotIndex >= 0) {
            LinkScanEntry entry = findEntry(selectedSlotIndex);
            if (entry != null && aliasField != null) {
                aliasField.setText(entry.alias == null ? "" : entry.alias);
            }
            this.buttonList.add(
                new ADM_GuiButton(
                    BUTTON_SAVE_ALIAS,
                    listStartX + listWidth - 62,
                    listStartY + visibleAreaHeight + 26,
                    58,
                    18,
                    I18n.format("adm.scanner.save")).setTexture(BUTTON_TEXTURE)
                        .setHoverTexture(BUTTON_HOVER_TEXTURE)
                        .setUseHoverEffect(true)
                        .setTextColor(0x00FF00)
                        .setTextHoverColor(0x55FF55));
        }
    }

    private LinkScanEntry findEntry(int slotIndex) {
        for (int i = 0; i < visibleEntries.size(); i++) {
            if (visibleEntries.get(i).slotIndex == slotIndex) {
                return visibleEntries.get(i);
            }
        }
        return ItemAdvanceLinkScanner.getEntry(scannerStack, slotIndex);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_EXIT) {
            commitAlias();
            this.mc.displayGuiScreen(null);
        } else if (button.id == BUTTON_OWNER_FILTER) {
            commitAlias();
            ItemAdvanceLinkScanner.cycleOwnerFilter(scannerStack);
            ItemAdvanceLinkScanner.syncToServer(player, scannerStack);
            initGui();
        } else if (button.id == BUTTON_NAME_FILTER) {
            commitAlias();
            ItemAdvanceLinkScanner.cycleNameFilter(scannerStack);
            ItemAdvanceLinkScanner.syncToServer(player, scannerStack);
            initGui();
        } else if (button.id == BUTTON_SAVE_ALIAS) {
            commitAlias();
        }
    }

    private void commitAlias() {
        if (selectedSlotIndex >= 0 && aliasField != null) {
            String alias = aliasField.getText()
                .trim();
            ItemAdvanceLinkScanner.setEntryAlias(scannerStack, selectedSlotIndex, alias);
            ItemAdvanceLinkScanner.syncToServer(player, scannerStack);
            refreshVisibleEntries();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int scrollDelta = Mouse.getEventDWheel();
        if (scrollDelta != 0) {
            int totalHeight = visibleEntries.size() * rowHeight;
            int maxScroll = Math.max(0, totalHeight - visibleAreaHeight);
            if (scrollDelta > 0) {
                scrollOffset = Math.max(0, scrollOffset - rowHeight);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + rowHeight);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (aliasField != null) {
            aliasField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton != 0 || !isMouseInListArea(mouseX, mouseY)) {
            return;
        }

        int rowIndex = (mouseY - listStartY + scrollOffset) / rowHeight;
        if (rowIndex < 0 || rowIndex >= visibleEntries.size()) {
            return;
        }

        LinkScanEntry entry = visibleEntries.get(rowIndex);
        int rowY = listStartY + rowIndex * rowHeight - scrollOffset;

        int tpX = listStartX + listWidth - 22;
        if (mouseX >= tpX && mouseX <= tpX + 18 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            commitAlias();
            int slot = player.inventory.currentItem;
            AdvanceDataMonitor.ADMCHANEL
                .sendToServer(PacketLinkScannerAction.teleport(slot, entry.dimension, entry.x, entry.y, entry.z));
            return;
        }

        if (selectedSlotIndex != entry.slotIndex) {
            commitAlias();
            selectedSlotIndex = entry.slotIndex;
            updateAliasField();
        }
    }

    private boolean isMouseInListArea(int mouseX, int mouseY) {
        return mouseX >= listStartX && mouseX <= listStartX + listWidth
            && mouseY >= listStartY
            && mouseY <= listStartY + visibleAreaHeight;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (aliasField != null && aliasField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                commitAlias();
                return;
            } else if (keyCode == Keyboard.KEY_ESCAPE) {
                aliasField.setFocused(false);
                return;
            }
            aliasField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (aliasField != null) {
            aliasField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.fontRendererObj.drawString(
            I18n.format("adm.scanner.title"),
            this.width / 2 - this.fontRendererObj.getStringWidth(I18n.format("adm.scanner.title")) / 2,
            this.listStartY - 28,
            textColor);

        int total = ItemAdvanceLinkScanner.getAllEntries(scannerStack)
            .size();
        String stats = I18n.format("adm.scanner.stats", total, visibleEntries.size());
        this.fontRendererObj.drawString(stats, listStartX, listStartY - 12, slotNumberColor);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawRect(
            listStartX - 2,
            listStartY - 2,
            listStartX + listWidth + 2,
            listStartY + visibleAreaHeight + 2,
            0x80000000);

        for (int i = 0; i < visibleEntries.size(); i++) {
            int rowY = listStartY + i * rowHeight - scrollOffset;
            if (rowY + rowHeight <= listStartY || rowY >= listStartY + visibleAreaHeight) {
                continue;
            }

            LinkScanEntry entry = visibleEntries.get(i);
            if (entry.slotIndex == selectedSlotIndex) {
                drawRect(listStartX, rowY, listStartX + listWidth, rowY + rowHeight, selectedBgColor);
            }

            String slotLabel = String.format("#%03d", entry.slotIndex);
            this.fontRendererObj.drawString(slotLabel, listStartX + 4, rowY + 6, slotNumberColor);

            String typeLabel = entry.getBlockType() != null ? I18n.format(
                entry.getBlockType()
                    .getLangKey())
                : "?";
            if (typeLabel.length() > 6) {
                typeLabel = typeLabel.substring(0, 6);
            }
            this.fontRendererObj.drawString(typeLabel, listStartX + 38, rowY + 6, textColor);

            String coord = entry.x + "," + entry.y + "," + entry.z;
            this.fontRendererObj.drawString(coord, listStartX + 88, rowY + 6, 0xCCCCCC);

            String owner = ItemAdvanceLinkScanner.formatOwnerDisplay(entry.owner);
            if (this.fontRendererObj.getStringWidth(owner) > 42) {
                owner = this.fontRendererObj.trimStringToWidth(owner, 40) + "..";
            }
            this.fontRendererObj.drawString(owner, listStartX + 168, rowY + 6, 0xAAAAFF);

            String alias = entry.hasAlias() ? entry.alias : I18n.format("adm.scanner.alias_empty");
            int aliasColor = entry.hasAlias() ? 0xFFFFFF : aliasEmptyColor;
            if (this.fontRendererObj.getStringWidth(alias) > 52) {
                alias = this.fontRendererObj.trimStringToWidth(alias, 50) + "..";
            }
            this.fontRendererObj.drawString(alias, listStartX + 218, rowY + 6, aliasColor);

            int tpX = listStartX + listWidth - 22;
            boolean hoverTp = mouseX >= tpX && mouseX <= tpX + 18 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
            this.fontRendererObj.drawString(">>", tpX + 2, rowY + 6, hoverTp ? 0x00FF00 : 0x55FF55);
        }

        if (selectedSlotIndex >= 0) {
            this.fontRendererObj.drawString(
                I18n.format("adm.scanner.alias_label"),
                listStartX,
                listStartY + visibleAreaHeight + 16,
                slotNumberColor);
            if (aliasField != null) {
                aliasField.drawTextBox();
            }
        } else {
            this.fontRendererObj.drawString(
                I18n.format("adm.scanner.select_hint"),
                listStartX,
                listStartY + visibleAreaHeight + 28,
                aliasEmptyColor);
        }

        drawScrollbar();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollbar() {
        int totalHeight = visibleEntries.size() * rowHeight;
        if (totalHeight <= visibleAreaHeight) {
            return;
        }
        int scrollbarX = listStartX + listWidth + 4;
        int maxScroll = totalHeight - visibleAreaHeight;
        int thumbH = Math.max(12, visibleAreaHeight * visibleAreaHeight / totalHeight);
        int thumbY = listStartY + (maxScroll == 0 ? 0 : scrollOffset * (visibleAreaHeight - thumbH) / maxScroll);
        drawRect(scrollbarX, listStartY, scrollbarX + 6, listStartY + visibleAreaHeight, 0x40FFFFFF);
        drawRect(scrollbarX, thumbY, scrollbarX + 6, thumbY + thumbH, 0xFF00AAFF);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        commitAlias();
        super.onGuiClosed();
    }
}
