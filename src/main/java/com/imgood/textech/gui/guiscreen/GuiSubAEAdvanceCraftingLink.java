package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidDouble;
import static com.imgood.textech.utils.ContentsHelper.isValidInteger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AbstractMonitorSubGui;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.ContentsHelper;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: Crafting CPU Config (per-binding sub GUI)
 * - ZH: 合成处理器配置（绑定子界面）
 * Lang keys: adm.title.data_config_ae_crafting
 */
public class GuiSubAEAdvanceCraftingLink extends AbstractMonitorSubGui {

    private ADM_GuiTextField textFieldTileEntityXYZ;
    private ADM_GuiTextField textFieldxOffset;
    private ADM_GuiTextField textFieldyOffset;
    private ADM_GuiTextField textFieldzOffset;
    private ADM_GuiTextField textFieldRotationX;
    private ADM_GuiTextField textFieldRotationY;
    private ADM_GuiTextField textFieldRotationZ;
    private ADM_GuiTextField textFieldInterval;

    private ADM_GuiTextField textFieldDisplayName;
    private ADM_GuiTextField textFieldDisplayNameScale;
    private ADM_GuiTextField textFieldScaled;
    private ADM_GuiTextField textScale;
    private ADM_GuiTextField textFieldCraftingTemplate;

    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 25;
    private int buttonRowConfigXoffset1 = 360;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow2Width = 60;

    private String dataType;
    private boolean monitorNetworkWide;
    private int textAlign;

    private Set<Integer> usedButtonIds = new HashSet<>();

    public GuiSubAEAdvanceCraftingLink(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        super(player, world, tileEntity, index);
        this.setSize(600, 450);
    }

    @Override
    protected void assignTextField(String row, int fieldIndex, ADM_GuiTextField field) {
        if ("Left".equals(row)) {
            assignLeftField(fieldIndex, field);
        } else if ("Right".equals(row)) {
            assignRightField(fieldIndex, field);
        }
    }

    private void checkUsedButtonIds() {
        usedButtonIds.clear();
        for (GuiButton btn : this.buttonList) {
            usedButtonIds.add(btn.id);
        }
        Set<Integer> uniqueIds = new HashSet<>();
        List<Integer> duplicateIds = new ArrayList<>();
        for (GuiButton btn : this.buttonList) {
            if (!uniqueIds.add(btn.id)) {
                duplicateIds.add(btn.id);
            }
        }
        if (!duplicateIds.isEmpty()) {
            System.err.println("警告：发现重复的按钮ID: " + duplicateIds);
            this.errorTips = I18n.format("adm.error.duplicateButtonIds") + duplicateIds;
        }
    }

    private void saveCurrentState() {
        contents.clear();
        for (ADM_GuiTextField field : textFieldsLeft) contents.add(field.getText());
        for (ADM_GuiTextField field : textFieldsRight) contents.add(field.getText());
    }

    @Override
    public void initGui() {
        beginInitGui();
        monitorNetworkWide = tileEntity.getMonitorNetworkWide(index);
        textAlign = tileEntity.getTextAlign(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
            contents.clear();
            contents.add(tileEntity.getXYZ(index));
            contents.add(String.valueOf(tileEntity.getXOffset(index)));
            contents.add(String.valueOf(tileEntity.getYOffset(index)));
            contents.add(String.valueOf(tileEntity.getZOffset(index)));
            contents.add(String.valueOf(tileEntity.getRotationX(index)));
            contents.add(String.valueOf(tileEntity.getRotationY(index)));
            contents.add(String.valueOf(tileEntity.getRotationZ(index)));
            contents.add(String.valueOf(tileEntity.getInterval(index)));
            contents.add(tileEntity.getDisplayName(index));
            contents.add(String.valueOf(tileEntity.getDisplayNameScale(index)));
            contents.add(String.valueOf(tileEntity.getScale(index)));
            contents.add(String.valueOf(tileEntity.getTextScale(index)));
            contents.add(tileEntity.getCraftingTemplate(index));
            isInitialized = true;
        }

        layoutMonitorPanel();

        textFieldsLeft.clear();
        textFieldsLeft.add(textFieldTileEntityXYZ);
        textFieldsLeft.add(textFieldxOffset);
        textFieldsLeft.add(textFieldyOffset);
        textFieldsLeft.add(textFieldzOffset);
        textFieldsLeft.add(textFieldRotationX);
        textFieldsLeft.add(textFieldRotationY);
        textFieldsLeft.add(textFieldRotationZ);
        textFieldsLeft.add(textFieldInterval);
        autoTextField("Left", textFieldsLeft, 0, 25, offsetX + 90, offsetY + 10, 80, 20);

        textFieldsRight.clear();
        textFieldsRight.add(textFieldDisplayName);
        textFieldsRight.add(textFieldDisplayNameScale);
        textFieldsRight.add(textFieldScaled);
        textFieldsRight.add(textScale);
        textFieldsRight.add(textFieldCraftingTemplate);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 275, offsetY + 10, 80, 20);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
    }

    private void fillFieldsFromContents() {
        ADM_GuiTextField[] fields = { textFieldTileEntityXYZ, textFieldxOffset, textFieldyOffset, textFieldzOffset,
            textFieldRotationX, textFieldRotationY, textFieldRotationZ, textFieldInterval, textFieldDisplayName,
            textFieldDisplayNameScale, textFieldScaled, textScale, textFieldCraftingTemplate };
        for (int i = 0; i < fields.length; i++) {
            fields[i].setMaxStringLength(i == 12 ? 400 : 100);
            fields[i].setText(contents.size() > i ? contents.get(i) : "");
        }
        textFieldTileEntityXYZ.setFocused(true);
        focusedField = textFieldTileEntityXYZ;
    }

    private void initFieldHints() {
        fieldHints.clear();
        fieldHints.put(textFieldTileEntityXYZ, "adm.hint.xyz");
        fieldHints.put(textFieldxOffset, "adm.hint.xoffset");
        fieldHints.put(textFieldyOffset, "adm.hint.yoffset");
        fieldHints.put(textFieldzOffset, "adm.hint.zoffset");
        fieldHints.put(textFieldRotationX, "adm.hint.rotationx");
        fieldHints.put(textFieldRotationY, "adm.hint.rotationy");
        fieldHints.put(textFieldRotationZ, "adm.hint.rotationz");
        fieldHints.put(textFieldInterval, "adm.hint.interval");
        fieldHints.put(textFieldDisplayName, "adm.hint.displayname");
        fieldHints.put(textFieldDisplayNameScale, "adm.hint.displayscale");
        fieldHints.put(textFieldScaled, "adm.hint.scale");
        fieldHints.put(textScale, "adm.hint.textscale");
        fieldHints.put(textFieldCraftingTemplate, "adm.hint.craftingtemplate");
    }

    private void initButtons() {
        this.buttonList.add(button(0, offsetX, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.save"));
        this.buttonList
            .add(button(1, offsetX + 70, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.cancel"));
        this.buttonList.add(
            button(
                7,
                offsetX + 140,
                offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                isEnabled ? "adm.button.disable" : "adm.button.enable"));

        int configY = buttonRowConfigYoffset1;
        this.buttonList.add(
            button(
                8,
                offsetX + buttonRowConfigXoffset1,
                offsetY + configY,
                buttonRow2Width,
                20,
                monitorNetworkWide ? "adm.button.monitorScope.network" : "adm.button.monitorScope.single"));
        configY += buttonRowConfigYinterval1;

        String alignKey = switch (textAlign) {
            case 0 -> "adm.button.textAlign.left";
            case 1 -> "adm.button.textAlign.center";
            case 2 -> "adm.button.textAlign.right";
            default -> "adm.button.textAlign.left";
        };
        this.buttonList.add(
            button(9, offsetX + buttonRowConfigXoffset1, offsetY + configY, buttonRow2Width, 20, alignKey));

        configY = buttonRowConfigYoffset1;
        this.buttonList.add(button(20, offsetX + buttonRowConfigXoffset1 + 100, offsetY + configY, 10, 10, "+"));
        this.buttonList.add(button(21, offsetX + buttonRowConfigXoffset1 + 150, offsetY + configY, 10, 10, "-"));
        configY += buttonRowConfigYinterval1;
        this.buttonList.add(button(22, offsetX + buttonRowConfigXoffset1 + 100, offsetY + configY, 10, 10, "+"));
        this.buttonList.add(button(23, offsetX + buttonRowConfigXoffset1 + 150, offsetY + configY, 10, 10, "-"));

        checkUsedButtonIds();
        getButtonByid(7).displayString = I18n.format(!isEnabled ? "adm.button.disable" : "adm.button.enable");
        ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
    }

    private ADM_GuiButton button(int id, int x, int y, int width, int height, String key) {
        return monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
    }

    private void assignLeftField(int index, ADM_GuiTextField field) {
        switch (index) {
            case 0 -> textFieldTileEntityXYZ = field;
            case 1 -> textFieldxOffset = field;
            case 2 -> textFieldyOffset = field;
            case 3 -> textFieldzOffset = field;
            case 4 -> textFieldRotationX = field;
            case 5 -> textFieldRotationY = field;
            case 6 -> textFieldRotationZ = field;
            case 7 -> textFieldInterval = field;
        }
    }

    private void assignRightField(int index, ADM_GuiTextField field) {
        switch (index) {
            case 0 -> textFieldDisplayName = field;
            case 1 -> textFieldDisplayNameScale = field;
            case 2 -> textFieldScaled = field;
            case 3 -> textScale = field;
            case 4 -> textFieldCraftingTemplate = field;
        }
    }

    private GuiButton getButtonByid(int id) {
        for (GuiButton btn : this.buttonList) {
            if (btn.id == id) {
                return btn;
            }
        }
        return null;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        isInitialized = false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound existingNbt = tileEntity.getDataBound(index);
        NBTTagCompound nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);

        switch (button.id) {
            case 0 -> save(nbt, existingDataValues);
            case 1 -> openMainGui();
            case 7 -> {
                isEnabled = !isEnabled;
                ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enable", !existingNbt.getBoolean("enable"));
                tileEntity.setEnable(index, !existingNbt.getBoolean("enable"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enable") ? "adm.button.disable" : "adm.button.enable");
                saveAndSync(nbt);
            }
            case 8 -> {
                monitorNetworkWide = !monitorNetworkWide;
                nbt.setBoolean("monitorNetworkWide", monitorNetworkWide);
                tileEntity.setMonitorNetworkWide(index, monitorNetworkWide);
                button.displayString = I18n
                    .format(monitorNetworkWide ? "adm.button.monitorScope.network" : "adm.button.monitorScope.single");
                saveAndSync(nbt);
            }
            case 9 -> {
                textAlign = (textAlign + 1) % 3;
                nbt.setInteger("textAlign", textAlign);
                tileEntity.setTextAlign(index, textAlign);
                String alignKey = switch (textAlign) {
                    case 0 -> "adm.button.textAlign.left";
                    case 1 -> "adm.button.textAlign.center";
                    case 2 -> "adm.button.textAlign.right";
                    default -> "adm.button.textAlign.left";
                };
                button.displayString = I18n.format(alignKey);
                saveAndSync(nbt);
            }
            case 20 -> updateNameAlpha(stepAlpha(tileEntity.getNameAlpha(index), true), nbt);
            case 21 -> updateNameAlpha(stepAlpha(tileEntity.getNameAlpha(index), false), nbt);
            case 22 -> updateTextAlpha(stepAlpha(tileEntity.getTextAlpha(index), true), nbt);
            case 23 -> updateTextAlpha(stepAlpha(tileEntity.getTextAlpha(index), false), nbt);
        }
    }

    private void save(NBTTagCompound nbt, NBTTagList existingDataValues) {
        nbt.setString("displayName", textFieldDisplayName.getText());
        nbt.setTag("dataValues", existingDataValues.copy());
        this.dataType = DataBound.DataType.crafting.name();
        nbt.setString("dataType", this.dataType);

        String xyz = textFieldTileEntityXYZ.getText()
            .replace("，", ",")
            .replace(" ", "");
        if (!ContentsHelper.isValidPosFormat(xyz)) {
            errorTips = I18n.format("adm.error.xyz");
            return;
        }
        nbt.setString("XYZ", xyz);

        if (!validateNumbers(nbt)) return;

        nbt.setString("craftingTemplate", textFieldCraftingTemplate.getText());
        nbt.setBoolean("monitorNetworkWide", monitorNetworkWide);
        nbt.setInteger("textAlign", textAlign);
        nbt.setBoolean("enable", isEnabled);
        saveAndSync(nbt);
        isInitialized = false;
        errorTips = "";
        openMainGui();
    }

    private boolean validateNumbers(NBTTagCompound nbt) {
        if (!isValidDouble(textFieldxOffset.getText())) {
            errorTips = I18n.format("adm.error.xoffset");
            return false;
        }
        nbt.setDouble("xOffset", Double.parseDouble(textFieldxOffset.getText()));
        if (!isValidDouble(textFieldyOffset.getText())) {
            errorTips = I18n.format("adm.error.yoffset");
            return false;
        }
        nbt.setDouble("yOffset", Double.parseDouble(textFieldyOffset.getText()));
        if (!isValidDouble(textFieldzOffset.getText())) {
            errorTips = I18n.format("adm.error.zoffset");
            return false;
        }
        nbt.setDouble("zOffset", Double.parseDouble(textFieldzOffset.getText()));
        if (!isValidDouble(textFieldRotationX.getText())) {
            errorTips = I18n.format("adm.error.rotationx");
            return false;
        }
        nbt.setDouble("rotationX", Double.parseDouble(textFieldRotationX.getText()));
        if (!isValidDouble(textFieldRotationY.getText())) {
            errorTips = I18n.format("adm.error.rotationy");
            return false;
        }
        nbt.setDouble("rotationY", Double.parseDouble(textFieldRotationY.getText()));
        if (!isValidDouble(textFieldRotationZ.getText())) {
            errorTips = I18n.format("adm.error.rotationz");
            return false;
        }
        nbt.setDouble("rotationZ", Double.parseDouble(textFieldRotationZ.getText()));
        if (!isValidInteger(textFieldInterval.getText())) {
            errorTips = I18n.format("adm.error.interval");
            return false;
        }
        int interval = Integer.parseInt(textFieldInterval.getText());
        nbt.setInteger("interval", interval <= 2 ? 1 : interval);
        if (!isValidDouble(textFieldDisplayNameScale.getText())) {
            errorTips = I18n.format("adm.error.displayscale");
            return false;
        }
        nbt.setDouble("displayNameScale", Double.parseDouble(textFieldDisplayNameScale.getText()));
        if (!isValidDouble(textFieldScaled.getText())) {
            errorTips = I18n.format("adm.error.scale");
            return false;
        }
        nbt.setDouble("scale", Double.parseDouble(textFieldScaled.getText()));
        if (!isValidDouble(textScale.getText())) {
            errorTips = I18n.format("adm.error.textscale");
            return false;
        }
        nbt.setDouble("textScale", Double.parseDouble(textScale.getText()));
        return true;
    }

    private double stepAlpha(double current, boolean increase) {
        int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
        int alphaInt = (int) Math.round(current * 100) + (increase ? step : -step);
        if (alphaInt > 100) alphaInt = 0;
        if (alphaInt < 0) alphaInt = 100;
        return alphaInt / 100.0;
    }

    private void updateNameAlpha(double alpha, NBTTagCompound nbt) {
        tileEntity.setNameAlpha(index, alpha);
        nbt.setDouble("nameAlpha", alpha);
        saveAndSync(nbt);
    }

    private void updateTextAlpha(double alpha, NBTTagCompound nbt) {
        tileEntity.setTextAlpha(index, alpha);
        nbt.setDouble("textAlpha", alpha);
        saveAndSync(nbt);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String[] label1 = { I18n.format("adm.label.xyz"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.zoffset"), I18n.format("adm.label.xrotation"),
            I18n.format("adm.label.yrotation"), I18n.format("adm.label.zrotation"), I18n.format("adm.label.interval") };
        autoText(label1, 0, 25, offsetX + 20, offsetY + 10, textColor, false);

        String[] label2 = { I18n.format("adm.label.displayname"), I18n.format("adm.label.displaynamescale"),
            I18n.format("adm.label.scaled"), I18n.format("adm.label.textscale"),
            I18n.format("adm.label.craftingtemplate") };
        autoText(label2, 0, 25, offsetX + 170, offsetY + 10, textColor, false);

        String[] label3 = { I18n.format("adm.label.namealpha"), I18n.format("adm.label.textalpha") };
        autoText(label3, 0, 25, offsetX + 490, offsetY + 10, textColor, true);
        String[] label4 = { (int) (tileEntity.getNameAlpha(index) * 100) + "%",
            (int) (tileEntity.getTextAlpha(index) * 100) + "%" };
        autoText(label4, 0, 25, offsetX + 490, offsetY + 20, textColor, true);

        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.data_config_ae_crafting", index + 1),
            offsetX + 322,
            offsetY - 35,
            textColor);
        fontRendererObj.drawString(errorTips, offsetX + 230, offsetY + 380, 0xff0000);
        drawTextFieldsWithHover(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
        drawFocusedFieldHint(offsetX + 10, offsetY + 280);
    }
}
