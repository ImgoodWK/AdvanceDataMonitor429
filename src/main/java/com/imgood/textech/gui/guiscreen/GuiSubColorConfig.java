package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidHexColor;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AbstractMonitorSubGui;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: Color Config (per-binding sub GUI)
 * - ZH: 颜色数据配置（绑定子界面）
 * Lang keys: adm.title.data_config_color
 */
public class GuiSubColorConfig extends AbstractMonitorSubGui {

    private ADM_GuiTextField textFieldDisplayNameColor;
    private ADM_GuiTextField textFieldAxisLineColor;
    private ADM_GuiTextField textFieldAxisFontColor;
    private ADM_GuiTextField textFieldLineColor;

    private String dataType;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;

    public GuiSubColorConfig(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity, int index) {
        super(player, world, tileEntity, index);
        this.startOffsetX = -150;
        this.startOffsetY = -100;
        this.buttonRowYOffset1 = 180;
        this.buttonRow1Width = 45;
        this.setSize(400, 250);
    }

    @Override
    protected void assignTextField(String row, int fieldIndex, ADM_GuiTextField field) {
        if ("Right".equals(row)) {
            assignRightField(fieldIndex, field);
        }
    }

    private void saveCurrentState() {
        contents.clear();
        contents.add(textFieldDisplayNameColor.getText());
        contents.add(textFieldAxisLineColor.getText());
        contents.add(textFieldAxisFontColor.getText());
        contents.add(textFieldLineColor.getText());
    }

    @Override
    public void initGui() {
        beginInitGui();
        isEnabledAxis = tileEntity.getEnableAxis(index);
        isEnabledData = tileEntity.getEnableData(index);
        isEnabledAxisFont = tileEntity.getEnableAxisFont(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
            contents.clear();
            contents.add(tileEntity.getDisplayNameColor(index));
            contents.add(tileEntity.getAxisLineColor(index));
            contents.add(tileEntity.getAxisFontColor(index));
            contents.add(tileEntity.getLineColor(index));
            isInitialized = true;
        }

        layoutMonitorPanel();

        textFieldsRight.clear();
        textFieldsRight.add(textFieldDisplayNameColor);
        textFieldsRight.add(textFieldAxisLineColor);
        textFieldsRight.add(textFieldAxisFontColor);
        textFieldsRight.add(textFieldLineColor);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 90, offsetY + 10, 80, 20);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
    }

    private void fillFieldsFromContents() {
        textFieldDisplayNameColor.setFocused(true);
        textFieldDisplayNameColor.setMaxStringLength(100);
        textFieldDisplayNameColor
            .setText(contents.size() > 0 ? contents.get(0) : tileEntity.getDisplayNameColor(index));
        textFieldAxisLineColor.setMaxStringLength(100);
        textFieldAxisLineColor.setText(contents.size() > 1 ? contents.get(1) : tileEntity.getAxisLineColor(index));
        textFieldAxisFontColor.setMaxStringLength(100);
        textFieldAxisFontColor.setText(contents.size() > 2 ? contents.get(2) : tileEntity.getAxisFontColor(index));
        textFieldLineColor.setMaxStringLength(100);
        textFieldLineColor.setText(contents.size() > 3 ? contents.get(3) : tileEntity.getLineColor(index));
        focusedField = textFieldDisplayNameColor;
    }

    private void initFieldHints() {
        fieldHints.clear();
        fieldHints.put(textFieldDisplayNameColor, "adm.hint.displaycolor");
        fieldHints.put(textFieldAxisLineColor, "adm.hint.axislinecolor");
        fieldHints.put(textFieldAxisFontColor, "adm.hint.axisfontcolor");
        fieldHints.put(textFieldLineColor, "adm.hint.linecolor");
    }

    private void initButtons() {
        this.buttonList.add(button(0, offsetX, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.save"));
        this.buttonList
            .add(button(1, offsetX + 70, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.cancel"));
    }

    private ADM_GuiButton button(int id, int x, int y, int width, int height, String key) {
        return monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
    }

    private void assignRightField(int fieldIndex, ADM_GuiTextField field) {
        switch (fieldIndex) {
            case 0 -> textFieldDisplayNameColor = field;
            case 1 -> textFieldAxisLineColor = field;
            case 2 -> textFieldAxisFontColor = field;
            case 3 -> textFieldLineColor = field;
        }
    }

    private void setTileEntityDatatype(DataBound.DataType dataType) {
        GuiButton btn2 = getButtonByid(2);
        GuiButton btn3 = getButtonByid(3);
        GuiButton btn4 = getButtonByid(4);
        GuiButton btn5 = getButtonByid(5);
        GuiButton btn6 = getButtonByid(6);
        if (btn2 != null) ((ADM_GuiButton) btn2).setUseRGBEffect(false);
        if (btn3 != null) ((ADM_GuiButton) btn3).setUseRGBEffect(false);
        if (btn4 != null) ((ADM_GuiButton) btn4).setUseRGBEffect(false);
        if (btn5 != null) ((ADM_GuiButton) btn5).setUseRGBEffect(false);
        if (btn6 != null) ((ADM_GuiButton) btn6).setUseRGBEffect(false);
        switch (dataType) {
            case line -> {
                if (btn2 != null) ((ADM_GuiButton) btn2).setUseRGBEffect(true);
            }
            case bar -> {
                if (btn3 != null) ((ADM_GuiButton) btn3).setUseRGBEffect(true);
            }
            case bar3d -> {
                if (btn4 != null) ((ADM_GuiButton) btn4).setUseRGBEffect(true);
            }
            case waterfall -> {
                if (btn5 != null) ((ADM_GuiButton) btn5).setUseRGBEffect(true);
            }
            case diffrence -> {
                if (btn6 != null) ((ADM_GuiButton) btn6).setUseRGBEffect(true);
            }
        }
        tileEntity.setDataType(index, dataType);
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
            case 0 -> {
                nbt.setTag("dataValues", existingDataValues.copy());
                if (this.dataType == null) {
                    nbt.setString("dataType", "line");
                } else {
                    nbt.setString("dataType", this.dataType);
                }
                if (!isValidHexColor(textFieldDisplayNameColor.getText())) {
                    errorTips = I18n.format("adm.error.displaycolor");
                    return;
                }
                nbt.setString("displayNameColor", textFieldDisplayNameColor.getText());
                if (!isValidHexColor(textFieldAxisLineColor.getText())) {
                    errorTips = I18n.format("adm.error.axislinecolor");
                    return;
                }
                nbt.setString("axisLineColor", textFieldAxisLineColor.getText());
                if (!isValidHexColor(textFieldAxisFontColor.getText())) {
                    errorTips = I18n.format("adm.error.axisfontcolor");
                    return;
                }
                nbt.setString("axisFontColor", textFieldAxisFontColor.getText());
                if (!isValidHexColor(textFieldLineColor.getText())) {
                    errorTips = I18n.format("adm.error.linecolor");
                    return;
                }
                nbt.setString("lineColor", textFieldLineColor.getText());
                saveAndSync(nbt);
                isInitialized = false;
                errorTips = "";
                openMainGui();
            }
            case 1 -> openMainGui();
            case 2 -> {
                setTileEntityDatatype(DataBound.DataType.line);
                nbt.setString("dataType", DataBound.DataType.line.name());
                this.dataType = DataBound.DataType.line.name();
            }
            case 3 -> {
                setTileEntityDatatype(DataBound.DataType.bar);
                nbt.setString("dataType", DataBound.DataType.bar.name());
                this.dataType = DataBound.DataType.bar.name();
            }
            case 4 -> {
                setTileEntityDatatype(DataBound.DataType.bar3d);
                nbt.setString("dataType", DataBound.DataType.bar3d.name());
                this.dataType = DataBound.DataType.bar3d.name();
            }
            case 5 -> {
                setTileEntityDatatype(DataBound.DataType.waterfall);
                nbt.setString("dataType", DataBound.DataType.waterfall.name());
                this.dataType = DataBound.DataType.waterfall.name();
            }
            case 6 -> {
                setTileEntityDatatype(DataBound.DataType.diffrence);
                nbt.setString("dataType", DataBound.DataType.diffrence.name());
                this.dataType = DataBound.DataType.diffrence.name();
            }
            case 7 -> {
                isEnabled = !isEnabled;
                GuiButton btn7 = getButtonByid(7);
                if (btn7 != null) {
                    ((ADM_GuiButton) btn7).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
                    btn7.displayString = I18n
                        .format(!existingNbt.getBoolean("enable") ? "adm.button.disable" : "adm.button.enable");
                }
                nbt.setBoolean("enable", !existingNbt.getBoolean("enable"));
                tileEntity.setEnable(index, !existingNbt.getBoolean("enable"));
                saveAndSync(nbt);
            }
            case 8 -> {
                isEnabledAxis = !isEnabledAxis;
                GuiButton btn8 = getButtonByid(8);
                if (btn8 != null) {
                    ((ADM_GuiButton) btn8).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
                    btn8.displayString = I18n.format(
                        !existingNbt.getBoolean("enableAxis") ? "adm.button.disableAxis" : "adm.button.enableAxis");
                }
                nbt.setBoolean("enableAxis", !existingNbt.getBoolean("enableAxis"));
                tileEntity.setEnableAxis(index, !existingNbt.getBoolean("enableAxis"));
                saveAndSync(nbt);
            }
            case 9 -> {
                isEnabledData = !isEnabledData;
                GuiButton btn9 = getButtonByid(9);
                if (btn9 != null) {
                    ((ADM_GuiButton) btn9).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
                    btn9.displayString = I18n.format(
                        !existingNbt.getBoolean("enableData") ? "adm.button.disableData" : "adm.button.enableData");
                }
                nbt.setBoolean("enableData", !existingNbt.getBoolean("enableData"));
                tileEntity.setEnableData(index, !existingNbt.getBoolean("enableData"));
                saveAndSync(nbt);
            }
            case 10 -> {
                isEnabledAxisFont = !isEnabledAxisFont;
                GuiButton btn10 = getButtonByid(10);
                if (btn10 != null) {
                    ((ADM_GuiButton) btn10).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
                    btn10.displayString = I18n.format(
                        !existingNbt.getBoolean("enableAxisFont") ? "adm.button.disableAxisFont"
                            : "adm.button.enableAxisFont");
                }
                nbt.setBoolean("enableAxisFont", !existingNbt.getBoolean("enableAxisFont"));
                tileEntity.setEnableAxisFont(index, !existingNbt.getBoolean("enableAxisFont"));
                saveAndSync(nbt);
            }
            default -> {}
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        String[] label1 = { I18n.format("adm.label.displaynamecolor"), I18n.format("adm.label.axislinecolor"),
            I18n.format("adm.label.axisfontcolor"), I18n.format("adm.label.linecolor") };
        autoText(label1, 0, 25, offsetX + 20, offsetY + 10, textColor);
        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.data_config_color", index + 1),
            offsetX + 200,
            offsetY - 37,
            textColor);

        fontRendererObj.drawString(errorTips, offsetX + 150, offsetY + 380, 0xff0000);
        drawTextFieldsWithHover(mouseX, mouseY);

        if (isValidHexColor(textFieldDisplayNameColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 150,
                offsetY + 10,
                Integer.parseInt(textFieldDisplayNameColor.getText(), 16));
        }
        if (isValidHexColor(textFieldAxisLineColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 150,
                offsetY + 35,
                Integer.parseInt(textFieldAxisLineColor.getText(), 16));
        }
        if (isValidHexColor(textFieldAxisFontColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 150,
                offsetY + 60,
                Integer.parseInt(textFieldAxisFontColor.getText(), 16));
        }
        if (isValidHexColor(textFieldLineColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 150,
                offsetY + 85,
                Integer.parseInt(textFieldLineColor.getText(), 16));
        }

        drawFocusedFieldHint(offsetX + 10, offsetY + 280);
    }
}
