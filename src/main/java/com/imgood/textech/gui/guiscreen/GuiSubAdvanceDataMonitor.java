package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidDouble;
import static com.imgood.textech.utils.ContentsHelper.isValidHexColor;
import static com.imgood.textech.utils.ContentsHelper.isValidInteger;

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
import com.imgood.textech.utils.ContentsHelper;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: Data Config (chart binding sub GUI)
 * - ZH: 数据配置（图表绑定子界面）
 * Lang keys: adm.title.data_config
 */
public class GuiSubAdvanceDataMonitor extends AbstractMonitorSubGui {

    private ADM_GuiTextField textFieldTileEntityXYZ;
    private ADM_GuiTextField textFieldxOffset;
    private ADM_GuiTextField textFieldyOffset;
    private ADM_GuiTextField textFieldzOffset;
    private ADM_GuiTextField textFieldRotationX;
    private ADM_GuiTextField textFieldRotationY;
    private ADM_GuiTextField textFieldRotationZ;
    private ADM_GuiTextField textFieldXRange;
    private ADM_GuiTextField textFieldYRange;
    private ADM_GuiTextField textFieldDataLimit;
    private ADM_GuiTextField textFieldInterval;
    private ADM_GuiTextField textFieldYMin;
    private ADM_GuiTextField textFieldYMax;

    private ADM_GuiTextField textFieldName;
    private ADM_GuiTextField textFieldDisplayName;
    private ADM_GuiTextField textFieldDisplayNameScale;
    private ADM_GuiTextField textFieldDisplayNameColor;
    private ADM_GuiTextField textFieldAxisLineColor;
    private ADM_GuiTextField textFieldAxisFontColor;
    private ADM_GuiTextField textFieldLineColor;
    private ADM_GuiTextField textFieldLineWidth;
    private ADM_GuiTextField textFieldScaled;
    private ADM_GuiTextField textFieldAxisFontScaled;

    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 30;
    private int buttonRowConfigXoffset1 = 360;
    private int buttonRow2Width = 60;

    private String dataType;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;

    public GuiSubAdvanceDataMonitor(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
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

    @Override
    public void initGui() {
        beginInitGui();
        isEnabledAxis = tileEntity.getEnableAxis(index);
        isEnabledData = tileEntity.getEnableData(index);
        isEnabledAxisFont = tileEntity.getEnableAxisFont(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
            loadInitialContents();
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
        textFieldsLeft.add(textFieldXRange);
        textFieldsLeft.add(textFieldYRange);
        textFieldsLeft.add(textFieldDataLimit);
        textFieldsLeft.add(textFieldInterval);
        textFieldsLeft.add(textFieldYMin);
        textFieldsLeft.add(textFieldYMax);
        autoTextField("Left", textFieldsLeft, 0, 25, offsetX + 90, offsetY + 10, 80, 20);

        textFieldsRight.clear();
        textFieldsRight.add(textFieldName);
        textFieldsRight.add(textFieldDisplayName);
        textFieldsRight.add(textFieldDisplayNameScale);
        textFieldsRight.add(textFieldDisplayNameColor);
        textFieldsRight.add(textFieldAxisLineColor);
        textFieldsRight.add(textFieldAxisFontColor);
        textFieldsRight.add(textFieldLineColor);
        textFieldsRight.add(textFieldLineWidth);
        textFieldsRight.add(textFieldScaled);
        textFieldsRight.add(textFieldAxisFontScaled);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 275, offsetY + 10, 80, 20);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
        setTileEntityDatatype(tileEntity.getDataType(index));
        updateEnableButtonLabels();
    }

    private void saveCurrentState() {
        contents.clear();
        contents.add(textFieldTileEntityXYZ.getText());
        contents.add(textFieldxOffset.getText());
        contents.add(textFieldyOffset.getText());
        contents.add(textFieldzOffset.getText());
        contents.add(textFieldRotationX.getText());
        contents.add(textFieldRotationY.getText());
        contents.add(textFieldRotationZ.getText());
        contents.add(textFieldXRange.getText());
        contents.add(textFieldYRange.getText());
        contents.add(textFieldDataLimit.getText());
        contents.add(textFieldInterval.getText());
        contents.add(textFieldYMin.getText());
        contents.add(textFieldYMax.getText());
        contents.add(textFieldName.getText());
        contents.add(textFieldDisplayName.getText());
        contents.add(textFieldDisplayNameScale.getText());
        contents.add(textFieldDisplayNameColor.getText());
        contents.add(textFieldAxisLineColor.getText());
        contents.add(textFieldAxisFontColor.getText());
        contents.add(textFieldLineColor.getText());
        contents.add(textFieldLineWidth.getText());
        contents.add(textFieldScaled.getText());
        contents.add(textFieldAxisFontScaled.getText());
    }

    private void loadInitialContents() {
        contents.clear();
        contents.add(String.valueOf(tileEntity.getXYZ(index)));
        contents.add(String.valueOf(tileEntity.getXOffset(index)));
        contents.add(String.valueOf(tileEntity.getYOffset(index)));
        contents.add(String.valueOf(tileEntity.getZOffset(index)));
        contents.add(String.valueOf(tileEntity.getRotationX(index)));
        contents.add(String.valueOf(tileEntity.getRotationY(index)));
        contents.add(String.valueOf(tileEntity.getRotationZ(index)));
        contents.add(String.valueOf(tileEntity.getXRange(index)));
        contents.add(String.valueOf(tileEntity.getYRange(index)));
        contents.add(String.valueOf(tileEntity.getDataLimit(index)));
        contents.add(String.valueOf(tileEntity.getInterval(index)));
        contents.add(String.valueOf(tileEntity.getYMin(index)));
        contents.add(String.valueOf(tileEntity.getYMax(index)));
        contents.add(String.valueOf(tileEntity.getName(index)));
        contents.add(String.valueOf(tileEntity.getDisplayName(index)));
        contents.add(String.valueOf(tileEntity.getDisplayNameScale(index)));
        contents.add(String.valueOf(tileEntity.getDisplayNameColor(index)));
        contents.add(String.valueOf(tileEntity.getAxisLineColor(index)));
        contents.add(String.valueOf(tileEntity.getAxisFontColor(index)));
        contents.add(String.valueOf(tileEntity.getLineColor(index)));
        contents.add(String.valueOf(tileEntity.getLineWidth(index)));
        contents.add(String.valueOf(tileEntity.getScale(index)));
        contents.add(String.valueOf(tileEntity.getAxisFontScale(index)));
    }

    private void fillFieldsFromContents() {
        ADM_GuiTextField[] fields = { textFieldTileEntityXYZ, textFieldxOffset, textFieldyOffset, textFieldzOffset,
            textFieldRotationX, textFieldRotationY, textFieldRotationZ, textFieldXRange, textFieldYRange,
            textFieldDataLimit, textFieldInterval, textFieldYMin, textFieldYMax, textFieldName, textFieldDisplayName,
            textFieldDisplayNameScale, textFieldDisplayNameColor, textFieldAxisLineColor, textFieldAxisFontColor,
            textFieldLineColor, textFieldLineWidth, textFieldScaled, textFieldAxisFontScaled };
        for (int i = 0; i < fields.length; i++) {
            fields[i].setMaxStringLength(100);
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
        fieldHints.put(textFieldXRange, "adm.hint.xrange");
        fieldHints.put(textFieldYRange, "adm.hint.yrange");
        fieldHints.put(textFieldDataLimit, "adm.hint.datalimit");
        fieldHints.put(textFieldInterval, "adm.hint.interval");
        fieldHints.put(textFieldName, "adm.hint.name");
        fieldHints.put(textFieldDisplayName, "adm.hint.displayname");
        fieldHints.put(textFieldDisplayNameScale, "adm.hint.displayscale");
        fieldHints.put(textFieldDisplayNameColor, "adm.hint.displaycolor");
        fieldHints.put(textFieldAxisLineColor, "adm.hint.axislinecolor");
        fieldHints.put(textFieldAxisFontColor, "adm.hint.axisfontcolor");
        fieldHints.put(textFieldLineColor, "adm.hint.linecolor");
        fieldHints.put(textFieldLineWidth, "adm.hint.linewidth");
        fieldHints.put(textFieldScaled, "adm.hint.scale");
        fieldHints.put(textFieldAxisFontScaled, "adm.hint.axisfontscale");
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

        this.buttonList.add(button(2, offsetX, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.line"));
        this.buttonList.add(button(3, offsetX + 70, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.bar"));
        this.buttonList
            .add(button(4, offsetX + 140, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.bar3d"));
        this.buttonList
            .add(button(5, offsetX + 210, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.waterfall"));
        this.buttonList
            .add(button(6, offsetX + 280, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.difference"));

        int configY = buttonRowConfigYoffset1;
        this.buttonList
            .add(button(8, offsetX + buttonRowConfigXoffset1, offsetY + configY, buttonRow2Width, 20, "adm.button.enableAxis"));
        configY += buttonRowConfigYinterval1;
        this.buttonList
            .add(button(9, offsetX + buttonRowConfigXoffset1, offsetY + configY, buttonRow2Width, 20, "adm.button.enableData"));
        configY += buttonRowConfigYinterval1;
        this.buttonList.add(
            button(10, offsetX + buttonRowConfigXoffset1, offsetY + configY, buttonRow2Width, 20, "adm.button.enableAxisFont"));
    }

    private void updateEnableButtonLabels() {
        getButtonByid(7).displayString = I18n.format(!isEnabled ? "adm.button.disable" : "adm.button.enable");
        getButtonByid(8).displayString = I18n
            .format(!isEnabledAxis ? "adm.button.disableAxis" : "adm.button.enableAxis");
        getButtonByid(9).displayString = I18n
            .format(!isEnabledData ? "adm.button.disableData" : "adm.button.enableData");
        getButtonByid(10).displayString = I18n
            .format(!isEnabledAxisFont ? "adm.button.disableAxisFont" : "adm.button.enableAxisFont");

        ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(8)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(9)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(10)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
    }

    private ADM_GuiButton button(int id, int x, int y, int width, int height, String key) {
        return monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
    }

    private void assignLeftField(int fieldIndex, ADM_GuiTextField field) {
        switch (fieldIndex) {
            case 0 -> textFieldTileEntityXYZ = field;
            case 1 -> textFieldxOffset = field;
            case 2 -> textFieldyOffset = field;
            case 3 -> textFieldzOffset = field;
            case 4 -> textFieldRotationX = field;
            case 5 -> textFieldRotationY = field;
            case 6 -> textFieldRotationZ = field;
            case 7 -> textFieldXRange = field;
            case 8 -> textFieldYRange = field;
            case 9 -> textFieldDataLimit = field;
            case 10 -> textFieldInterval = field;
            case 11 -> textFieldYMin = field;
            case 12 -> textFieldYMax = field;
        }
    }

    private void assignRightField(int fieldIndex, ADM_GuiTextField field) {
        switch (fieldIndex) {
            case 0 -> textFieldName = field;
            case 1 -> textFieldDisplayName = field;
            case 2 -> textFieldDisplayNameScale = field;
            case 3 -> textFieldDisplayNameColor = field;
            case 4 -> textFieldAxisLineColor = field;
            case 5 -> textFieldAxisFontColor = field;
            case 6 -> textFieldLineColor = field;
            case 7 -> textFieldLineWidth = field;
            case 8 -> textFieldScaled = field;
            case 9 -> textFieldAxisFontScaled = field;
        }
    }

    private void setTileEntityDatatype(DataBound.DataType datatype) {
        ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(false);
        switch (datatype) {
            case line -> ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(true);
            case bar -> ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(true);
            case bar3d -> ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(true);
            case waterfall -> ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(true);
            case diffrence -> ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(true);
        }
        tileEntity.setDataType(index, datatype);
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
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound existingNbt = tileEntity.getDataBound(index);
        NBTTagCompound nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);
        switch (button.id) {
            case 0 -> save(nbt, existingDataValues);
            case 1 -> openMainGui();
            case 2 -> {
                setTileEntityDatatype(DataBound.DataType.line);
                nbt.setString("dataType", DataBound.DataType.line.name());
                dataType = DataBound.DataType.line.name();
            }
            case 3 -> {
                setTileEntityDatatype(DataBound.DataType.bar);
                nbt.setString("dataType", DataBound.DataType.bar.name());
                dataType = DataBound.DataType.bar.name();
            }
            case 4 -> {
                setTileEntityDatatype(DataBound.DataType.bar3d);
                nbt.setString("dataType", DataBound.DataType.bar3d.name());
                dataType = DataBound.DataType.bar3d.name();
            }
            case 5 -> {
                setTileEntityDatatype(DataBound.DataType.waterfall);
                nbt.setString("dataType", DataBound.DataType.waterfall.name());
                dataType = DataBound.DataType.waterfall.name();
            }
            case 6 -> {
                setTileEntityDatatype(DataBound.DataType.diffrence);
                nbt.setString("dataType", DataBound.DataType.diffrence.name());
                dataType = DataBound.DataType.diffrence.name();
            }
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
                isEnabledAxis = !isEnabledAxis;
                ((ADM_GuiButton) getButtonByid(8)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableAxis", !existingNbt.getBoolean("enableAxis"));
                tileEntity.setEnableAxis(index, !existingNbt.getBoolean("enableAxis"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableAxis") ? "adm.button.disableAxis" : "adm.button.enableAxis");
                saveAndSync(nbt);
            }
            case 9 -> {
                isEnabledData = !isEnabledData;
                ((ADM_GuiButton) getButtonByid(9)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableData", !existingNbt.getBoolean("enableData"));
                tileEntity.setEnableData(index, !existingNbt.getBoolean("enableData"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableData") ? "adm.button.disableData" : "adm.button.enableData");
                saveAndSync(nbt);
            }
            case 10 -> {
                isEnabledAxisFont = !isEnabledAxisFont;
                ((ADM_GuiButton) getButtonByid(10)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableAxisFont", !existingNbt.getBoolean("enableAxisFont"));
                tileEntity.setEnableAxisFont(index, !existingNbt.getBoolean("enableAxisFont"));
                button.displayString = I18n.format(
                    !existingNbt.getBoolean("enableAxisFont") ? "adm.button.disableAxisFont"
                        : "adm.button.enableAxisFont");
                saveAndSync(nbt);
            }
            default -> {}
        }
    }

    private void save(NBTTagCompound nbt, NBTTagList existingDataValues) {
        nbt.setTag("dataValues", existingDataValues.copy());
        if (dataType == null) {
            nbt.setString("dataType", "line");
        }

        String xyz = textFieldTileEntityXYZ.getText()
            .replace("，", ",")
            .replace(" ", "");
        if (!ContentsHelper.isValidPosFormat(xyz)) {
            errorTips = I18n.format("adm.error.xyz");
            return;
        }
        nbt.setString("XYZ", xyz);

        if (!isValidDouble(textFieldxOffset.getText())) {
            errorTips = I18n.format("adm.error.xoffset");
            return;
        }
        nbt.setDouble("xOffset", Double.parseDouble(textFieldxOffset.getText()));

        if (!isValidDouble(textFieldyOffset.getText())) {
            errorTips = I18n.format("adm.error.yoffset");
            return;
        }
        nbt.setDouble("yOffset", Double.parseDouble(textFieldyOffset.getText()));

        if (!isValidDouble(textFieldzOffset.getText())) {
            errorTips = I18n.format("adm.error.zoffset");
            return;
        }
        nbt.setDouble("zOffset", Double.parseDouble(textFieldzOffset.getText()));

        if (!isValidDouble(textFieldRotationX.getText())) {
            errorTips = I18n.format("adm.error.rotationx");
            return;
        }
        nbt.setDouble("rotationX", Double.parseDouble(textFieldRotationX.getText()));

        if (!isValidDouble(textFieldRotationY.getText())) {
            errorTips = I18n.format("adm.error.rotationy");
            return;
        }
        nbt.setDouble("rotationY", Double.parseDouble(textFieldRotationY.getText()));

        if (!isValidDouble(textFieldRotationZ.getText())) {
            errorTips = I18n.format("adm.error.rotationz");
            return;
        }
        nbt.setDouble("rotationZ", Double.parseDouble(textFieldRotationZ.getText()));

        if (!isValidDouble(textFieldXRange.getText())) {
            errorTips = I18n.format("adm.error.xrange");
            return;
        }
        nbt.setDouble("xRange", Double.parseDouble(textFieldXRange.getText()));

        if (!isValidDouble(textFieldYRange.getText())) {
            errorTips = I18n.format("adm.error.yrange");
            return;
        }
        nbt.setDouble("yRange", Double.parseDouble(textFieldYRange.getText()));

        if (!isValidInteger(textFieldDataLimit.getText())) {
            errorTips = I18n.format("adm.error.datalimit");
            return;
        }
        int dataLimit = Integer.parseInt(textFieldDataLimit.getText());
        if (dataLimit > 9999 || dataLimit < 2) {
            errorTips = I18n.format("adm.error.datalimit");
            return;
        }
        nbt.setInteger("dataLimit", Integer.parseInt(textFieldDataLimit.getText()));

        if (!isValidInteger(textFieldInterval.getText())) {
            errorTips = I18n.format("adm.error.interval");
            return;
        }
        int interval = Integer.parseInt(textFieldInterval.getText());
        interval = interval <= 2 ? 1 : interval;
        nbt.setInteger("interval", interval);

        if (!isValidDouble(textFieldYMin.getText())) {
            errorTips = I18n.format("adm.error.ymin");
            return;
        }
        nbt.setDouble("yMin", Double.parseDouble(textFieldYMin.getText()));

        if (!isValidDouble(textFieldYMax.getText())) {
            errorTips = I18n.format("adm.error.ymax");
            return;
        }
        nbt.setDouble("yMax", Double.parseDouble(textFieldYMax.getText()));

        nbt.setString("name", textFieldName.getText());
        nbt.setString("displayName", textFieldDisplayName.getText());

        if (!isValidDouble(textFieldDisplayNameScale.getText())) {
            errorTips = I18n.format("adm.error.displayscale");
            return;
        }
        nbt.setDouble("displayNameScale", Double.parseDouble(textFieldDisplayNameScale.getText()));

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

        if (!isValidDouble(textFieldLineWidth.getText())) {
            errorTips = I18n.format("adm.error.linewidth");
            return;
        }
        nbt.setDouble("lineWidth", Double.parseDouble(textFieldLineWidth.getText()));

        if (!isValidDouble(textFieldScaled.getText())) {
            errorTips = I18n.format("adm.error.scale");
            return;
        }
        nbt.setDouble("scale", Double.parseDouble(textFieldScaled.getText()));

        if (!isValidDouble(textFieldAxisFontScaled.getText())) {
            errorTips = I18n.format("adm.error.axisfontscale");
            return;
        }
        nbt.setDouble("axisFontScale", Double.parseDouble(textFieldAxisFontScaled.getText()));

        saveAndSync(nbt);
        isInitialized = false;
        errorTips = "";
        openMainGui();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        isInitialized = false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String[] label1 = { I18n.format("adm.label.xyz"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.zoffset"), I18n.format("adm.label.xrotation"),
            I18n.format("adm.label.yrotation"), I18n.format("adm.label.zrotation"), I18n.format("adm.label.xrange"),
            I18n.format("adm.label.yrange"), I18n.format("adm.label.datalimit"), I18n.format("adm.label.interval"),
            "yMin", "yMax" };
        autoText(label1, 0, 25, offsetX + 20, offsetY + 10, textColor);

        String[] label2 = { I18n.format("adm.label.nbtname"), I18n.format("adm.label.displayname"),
            I18n.format("adm.label.displaynamescale"), I18n.format("adm.label.displaynamecolor"),
            I18n.format("adm.label.axislinecolor"), I18n.format("adm.label.axisfontcolor"),
            I18n.format("adm.label.linecolor"), I18n.format("adm.label.linewidth"), I18n.format("adm.label.scaled"),
            I18n.format("adm.label.axisfontscaled") };
        autoText(label2, 0, 25, offsetX + 170, offsetY + 10, textColor);

        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.data_config", index + 1),
            offsetX + 322,
            offsetY - 35,
            textColor);

        fontRendererObj.drawString(errorTips, offsetX + 150, offsetY + 380, 0xff0000);

        if (isValidHexColor(textFieldDisplayNameColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 320,
                offsetY + 85,
                Integer.parseInt(textFieldDisplayNameColor.getText(), 16));
        }
        if (isValidHexColor(textFieldAxisLineColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 320,
                offsetY + 110,
                Integer.parseInt(textFieldAxisLineColor.getText(), 16));
        }
        if (isValidHexColor(textFieldAxisFontColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 320,
                offsetY + 136,
                Integer.parseInt(textFieldAxisFontColor.getText(), 16));
        }
        if (isValidHexColor(textFieldLineColor.getText())) {
            drawCenteredString(
                fontRendererObj,
                "§l■",
                offsetX + 320,
                offsetY + 160,
                Integer.parseInt(textFieldLineColor.getText(), 16));
        }

        drawTextFieldsWithHover(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawFocusedFieldHint(offsetX + 10, offsetY + 280);
    }
}
