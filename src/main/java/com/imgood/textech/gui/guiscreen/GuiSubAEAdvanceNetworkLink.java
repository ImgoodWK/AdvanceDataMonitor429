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

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AbstractMonitorSubGui;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.ContentsHelper;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: AE Network Config (per-binding sub GUI)
 * - ZH: AE网络数据配置（绑定子界面）
 * Lang keys: adm.title.data_config_ae_network
 */
public class GuiSubAEAdvanceNetworkLink extends AbstractMonitorSubGui {

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

    private ADM_GuiTextField textFieldDisplayName;
    private ADM_GuiTextField textFieldDisplayNameScale;
    private ADM_GuiTextField textFieldLineWidth;
    private ADM_GuiTextField textFieldScaled;
    private ADM_GuiTextField textFieldAxisFontScaled;
    private ADM_GuiTextField textFieldGridLineWidth;
    private ADM_GuiTextField textFieldAxisLineWidth;
    private ADM_GuiTextField textFieldTickLengthFactor;

    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 25;
    private int buttonRowConfigXoffset1 = 360;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow2Width = 60;

    private String dataType;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;
    private boolean isTypeItem;
    private boolean isUsed;
    private boolean isValue;
    private boolean isBytes;

    private final Set<Integer> usedButtonIds = new HashSet<>();

    public GuiSubAEAdvanceNetworkLink(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
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
        initDataType();
        beginInitGui();
        isEnabledAxis = tileEntity.getEnableAxis(index);
        isEnabledData = tileEntity.getEnableData(index);
        isEnabledAxisFont = tileEntity.getEnableAxisFont(index);
        isValue = tileEntity.getIsValue(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
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
            contents.add(String.valueOf(tileEntity.getDisplayName(index)));
            contents.add(String.valueOf(tileEntity.getDisplayNameScale(index)));
            contents.add(String.valueOf(tileEntity.getLineWidth(index)));
            contents.add(String.valueOf(tileEntity.getScale(index)));
            contents.add(String.valueOf(tileEntity.getAxisFontScale(index)));
            contents.add(String.valueOf(tileEntity.getGridLineWidth(index)));
            contents.add(String.valueOf(tileEntity.getAxisLineWidth(index)));
            contents.add(String.valueOf(tileEntity.getTickLengthFactor(index)));
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
        textFieldsRight.add(textFieldDisplayName);
        textFieldsRight.add(textFieldDisplayNameScale);
        textFieldsRight.add(textFieldLineWidth);
        textFieldsRight.add(textFieldScaled);
        textFieldsRight.add(textFieldAxisFontScaled);
        textFieldsRight.add(textFieldGridLineWidth);
        textFieldsRight.add(textFieldAxisLineWidth);
        textFieldsRight.add(textFieldTickLengthFactor);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 275, offsetY + 10, 80, 20);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
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
        contents.add(textFieldDisplayName.getText());
        contents.add(textFieldDisplayNameScale.getText());
        contents.add(textFieldLineWidth.getText());
        contents.add(textFieldScaled.getText());
        contents.add(textFieldAxisFontScaled.getText());
        contents.add(textFieldGridLineWidth.getText());
        contents.add(textFieldAxisLineWidth.getText());
        contents.add(textFieldTickLengthFactor.getText());
    }

    private void fillFieldsFromContents() {
        ADM_GuiTextField[] fields = { textFieldTileEntityXYZ, textFieldxOffset, textFieldyOffset, textFieldzOffset,
            textFieldRotationX, textFieldRotationY, textFieldRotationZ, textFieldXRange, textFieldYRange,
            textFieldDataLimit, textFieldInterval, textFieldYMin, textFieldYMax, textFieldDisplayName,
            textFieldDisplayNameScale, textFieldLineWidth, textFieldScaled, textFieldAxisFontScaled,
            textFieldGridLineWidth, textFieldAxisLineWidth, textFieldTickLengthFactor };
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
        fieldHints.put(textFieldDisplayName, "adm.hint.displayname");
        fieldHints.put(textFieldDisplayNameScale, "adm.hint.displayscale");
        fieldHints.put(textFieldLineWidth, "adm.hint.linewidth");
        fieldHints.put(textFieldScaled, "adm.hint.scale");
        fieldHints.put(textFieldAxisFontScaled, "adm.hint.axisfontscale");
        fieldHints.put(textFieldGridLineWidth, "adm.hint.gridlinewidth");
        fieldHints.put(textFieldAxisLineWidth, "adm.hint.axislinewidth");
        fieldHints.put(textFieldTickLengthFactor, "adm.hint.ticklengthfactor");
    }

    private void initButtons() {
        ADM_GuiButton saveBtn = row1Button(
            0,
            offsetX,
            offsetY + buttonRowYOffset1,
            buttonRow1Width,
            20,
            "adm.button.save");
        saveBtn.setLeftDecoration(AdmGuiTextures.BUTTON_HOVER)
            .setRightDecoration(AdmGuiTextures.BUTTON_HOVER)
            .setDecorationWidth(20);
        this.buttonList.add(saveBtn);
        this.buttonList
            .add(row1Button(1, offsetX + 70, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.cancel"));
        this.buttonList
            .add(row1Button(7, offsetX + 140, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.enable"));

        this.buttonList
            .add(row2Button(2, offsetX, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.line"));
        this.buttonList
            .add(row2Button(3, offsetX + 70, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.bar"));
        this.buttonList
            .add(row2Button(4, offsetX + 140, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.bar3d"));
        this.buttonList.add(
            row2Button(5, offsetX + 210, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.waterfall"));
        this.buttonList.add(
            row2Button(6, offsetX + 280, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.difference"));

        this.buttonList.add(
            row2Button(
                8,
                offsetX + buttonRowConfigXoffset1,
                offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                "adm.button.enableAxis"));

        this.buttonList
            .add(alphaButton(20, offsetX + buttonRowConfigXoffset1 + 100, offsetY + buttonRowConfigYoffset1));
        this.buttonList
            .add(alphaButton(21, offsetX + buttonRowConfigXoffset1 + 150, offsetY + buttonRowConfigYoffset1));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList
            .add(alphaButton(22, offsetX + buttonRowConfigXoffset1 + 100, offsetY + buttonRowConfigYoffset1));
        this.buttonList
            .add(alphaButton(23, offsetX + buttonRowConfigXoffset1 + 150, offsetY + buttonRowConfigYoffset1));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList
            .add(alphaButton(24, offsetX + buttonRowConfigXoffset1 + 100, offsetY + buttonRowConfigYoffset1));
        this.buttonList
            .add(alphaButton(25, offsetX + buttonRowConfigXoffset1 + 150, offsetY + buttonRowConfigYoffset1));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList
            .add(alphaButton(26, offsetX + buttonRowConfigXoffset1 + 100, offsetY + buttonRowConfigYoffset1));
        this.buttonList
            .add(alphaButton(27, offsetX + buttonRowConfigXoffset1 + 150, offsetY + buttonRowConfigYoffset1));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList
            .add(alphaButton(16, offsetX + buttonRowConfigXoffset1 + 100, offsetY + buttonRowConfigYoffset1));
        this.buttonList
            .add(alphaButton(17, offsetX + buttonRowConfigXoffset1 + 150, offsetY + buttonRowConfigYoffset1));

        buttonRowConfigYoffset1 = 20;
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList.add(
            row2Button(
                9,
                offsetX + buttonRowConfigXoffset1,
                offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                "adm.button.enableData"));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList.add(
            row2Button(
                10,
                offsetX + buttonRowConfigXoffset1,
                offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                "adm.button.enableAxisFont"));

        this.buttonList.add(plainButton(11, offsetX + 215, offsetY + 240, 40, 20, "adm.button.dataType.Item"));
        this.buttonList.add(plainButton(12, offsetX + 275, offsetY + 240, 40, 20, "adm.button.dataType.Used"));
        this.buttonList.add(plainButton(13, offsetX + 402, offsetY + 240, 50, 20, "adm.button.dataType.Value"));
        this.buttonList.add(plainButton(14, offsetX + 335, offsetY + 240, 40, 20, "adm.button.dataType.Bytes"));
        this.buttonList.add(plainButton(15, offsetX + 535, offsetY + 350, 10, 10, "×"));
        this.buttonList.add(plainButton(18, offsetX + 535, offsetY + 365, 10, 10, "+"));

        checkUsedButtonIds();
        setTileEntityDatatype(tileEntity.getDataType(index));
        refreshToggleButtons();
    }

    private ADM_GuiButton row1Button(int id, int x, int y, int width, int height, String key) {
        ADM_GuiButton btn = monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
        btn.setUseRGBEffect(buttonRow1RGB);
        return btn;
    }

    private ADM_GuiButton row2Button(int id, int x, int y, int width, int height, String key) {
        ADM_GuiButton btn = monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
        btn.setUseRGBEffect(buttonRow2RGB);
        return btn;
    }

    private ADM_GuiButton alphaButton(int id, int x, int y) {
        ADM_GuiButton btn = monitorButton(
            id,
            x,
            y,
            10,
            10,
            I18n.format(id % 2 == 0 ? "+" : "-"),
            textColor,
            textHoverColor);
        btn.setUseRGBEffect(false);
        return btn;
    }

    private ADM_GuiButton plainButton(int id, int x, int y, int width, int height, String key) {
        ADM_GuiButton btn = monitorButton(id, x, y, width, height, I18n.format(key), textColor, textHoverColor);
        btn.setUseRGBEffect(false);
        return btn;
    }

    private void refreshToggleButtons() {
        getButtonByid(7).displayString = I18n.format(!isEnabled ? "adm.button.disable" : "adm.button.enable");
        getButtonByid(8).displayString = I18n
            .format(!isEnabledAxis ? "adm.button.disableAxis" : "adm.button.enableAxis");
        getButtonByid(9).displayString = I18n
            .format(!isEnabledData ? "adm.button.disableData" : "adm.button.enableData");
        getButtonByid(10).displayString = I18n
            .format(!isEnabledAxisFont ? "adm.button.disableAxisFont" : "adm.button.enableAxisFont");
        getButtonByid(11).displayString = I18n
            .format(isTypeItem ? "adm.button.dataType.Item" : "adm.button.dataType.Fluid");
        getButtonByid(12).displayString = I18n
            .format(isUsed ? "adm.button.dataType.Used" : "adm.button.dataType.Total");
        getButtonByid(13).displayString = I18n
            .format(isValue ? "adm.button.dataType.Percent" : "adm.button.dataType.Value");
        getButtonByid(14).displayString = I18n
            .format(isBytes ? "adm.button.dataType.Bytes" : "adm.button.dataType.Type");

        ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(8)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(9)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(10)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
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
            case 0 -> textFieldDisplayName = field;
            case 1 -> textFieldDisplayNameScale = field;
            case 2 -> textFieldLineWidth = field;
            case 3 -> textFieldScaled = field;
            case 4 -> textFieldAxisFontScaled = field;
            case 5 -> textFieldGridLineWidth = field;
            case 6 -> textFieldAxisLineWidth = field;
            case 7 -> textFieldTickLengthFactor = field;
        }
    }

    private void checkUsedButtonIds() {
        usedButtonIds.clear();
        for (GuiButton button : this.buttonList) {
            usedButtonIds.add(button.id);
        }
        AdvanceDataMonitor.LOG.info("已使用的按钮ID: {}", usedButtonIds);

        Set<Integer> uniqueIds = new HashSet<>();
        List<Integer> duplicateIds = new ArrayList<>();
        for (GuiButton button : this.buttonList) {
            if (!uniqueIds.add(button.id)) {
                duplicateIds.add(button.id);
            }
        }
        if (!duplicateIds.isEmpty()) {
            System.err.println("警告：发现重复的按钮ID: " + duplicateIds);
            this.errorTips = I18n.format("adm.error.duplicateButtonIds") + duplicateIds;
        }
    }

    private GuiButton getButtonByid(int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return button;
            }
        }
        return null;
    }

    private void setTileEntityDatatype(DataBound.DataType dataType) {
        ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(false);
        switch (dataType) {
            case line -> ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(true);
            case bar -> ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(true);
            case bar3d -> ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(true);
            case waterfall -> ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(true);
            case diffrence -> ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(true);
        }
        tileEntity.setDataType(index, dataType);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        isInitialized = false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        AdvanceDataMonitor.LOG.info("Button ID: {}", button.id);
        NBTTagCompound existingNbt = tileEntity.getDataBound(index);
        NBTTagCompound nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);
        switch (button.id) {
            case 0 -> save(nbt, existingDataValues);
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
            case 11 -> {
                isTypeItem = !isTypeItem;
                getButtonByid(11).displayString = I18n
                    .format(isTypeItem ? "adm.button.dataType.Item" : "adm.button.dataType.Fluid");
            }
            case 12 -> {
                isUsed = !isUsed;
                getButtonByid(12).displayString = I18n
                    .format(isUsed ? "adm.button.dataType.Used" : "adm.button.dataType.Total");
            }
            case 13 -> {
                isValue = !isValue;
                getButtonByid(13).displayString = I18n
                    .format(isValue ? "adm.button.dataType.Percent" : "adm.button.dataType.Value");
            }
            case 14 -> {
                isBytes = !isBytes;
                getButtonByid(14).displayString = I18n
                    .format(isBytes ? "adm.button.dataType.Bytes" : "adm.button.dataType.Type");
            }
            case 15 -> {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    clearDataValues();
                    errorTips = I18n.format("adm.message.data_cleared");
                } else {
                    errorTips = I18n.format("adm.tooltip.clearData.shift");
                }
            }
            case 18 -> {
                NBTTagCompound bound = tileEntity.getDataBound(index);
                tileEntity.processDataImmediately(index, bound);
                errorTips = I18n.format("adm.message.data_collected");
            }
            case 16 -> updateGridLineAlpha(stepAlpha(tileEntity.getGridLineAlpha(index), true), nbt);
            case 17 -> updateGridLineAlpha(stepAlpha(tileEntity.getGridLineAlpha(index), false), nbt);
            case 20 -> updateNameAlpha(stepAlpha(tileEntity.getNameAlpha(index), true), nbt);
            case 21 -> updateNameAlpha(stepAlpha(tileEntity.getNameAlpha(index), false), nbt);
            case 22 -> updateAxisAlpha(stepAlpha(tileEntity.getAxisLineAlpha(index), true), nbt);
            case 23 -> updateAxisAlpha(stepAlpha(tileEntity.getAxisLineAlpha(index), false), nbt);
            case 24 -> updateAxisFontAlpha(stepAlpha(tileEntity.getAxisFontAlpha(index), true), nbt);
            case 25 -> updateAxisFontAlpha(stepAlpha(tileEntity.getAxisFontAlpha(index), false), nbt);
            case 26 -> updateDataLineAlpha(stepAlpha(tileEntity.getLineAlpha(index), true), nbt);
            case 27 -> updateDataLineAlpha(stepAlpha(tileEntity.getLineAlpha(index), false), nbt);
            default -> {}
        }
    }

    private void save(NBTTagCompound nbt, NBTTagList existingDataValues) {
        nbt.setString("displayName", textFieldDisplayName.getText());
        nbt.setTag("dataValues", existingDataValues.copy());
        if (this.dataType == null) {
            nbt.setString("dataType", "line");
        } else {
            nbt.setString("dataType", this.dataType);
        }

        String xyz = textFieldTileEntityXYZ.getText()
            .replace("，", ",")
            .replace(" ", "");
        if (!ContentsHelper.isValidPosFormat(xyz)) {
            this.errorTips = I18n.format("adm.error.xyz");
            return;
        }
        nbt.setString("XYZ", xyz);

        if (!isValidDouble(textFieldxOffset.getText())) {
            this.errorTips = I18n.format("adm.error.xoffset");
            return;
        }
        nbt.setDouble("xOffset", Double.parseDouble(textFieldxOffset.getText()));

        if (!isValidDouble(textFieldyOffset.getText())) {
            this.errorTips = I18n.format("adm.error.yoffset");
            return;
        }
        nbt.setDouble("yOffset", Double.parseDouble(textFieldyOffset.getText()));

        if (!isValidDouble(textFieldzOffset.getText())) {
            this.errorTips = I18n.format("adm.error.zoffset");
            return;
        }
        nbt.setDouble("zOffset", Double.parseDouble(textFieldzOffset.getText()));

        if (!isValidDouble(textFieldRotationX.getText())) {
            this.errorTips = I18n.format("adm.error.rotationx");
            return;
        }
        nbt.setDouble("rotationX", Double.parseDouble(textFieldRotationX.getText()));

        if (!isValidDouble(textFieldRotationY.getText())) {
            this.errorTips = I18n.format("adm.error.rotationy");
            return;
        }
        nbt.setDouble("rotationY", Double.parseDouble(textFieldRotationY.getText()));

        if (!isValidDouble(textFieldRotationZ.getText())) {
            this.errorTips = I18n.format("adm.error.rotationz");
            return;
        }
        nbt.setDouble("rotationZ", Double.parseDouble(textFieldRotationZ.getText()));

        if (!isValidDouble(textFieldXRange.getText())) {
            this.errorTips = I18n.format("adm.error.xrange");
            return;
        }
        nbt.setDouble("xRange", Double.parseDouble(textFieldXRange.getText()));

        if (!isValidDouble(textFieldYRange.getText())) {
            this.errorTips = I18n.format("adm.error.yrange");
            return;
        }
        nbt.setDouble("yRange", Double.parseDouble(textFieldYRange.getText()));

        if (!isValidInteger(textFieldDataLimit.getText())) {
            this.errorTips = I18n.format("adm.error.datalimit");
            return;
        }
        int dataLimit = Integer.parseInt(textFieldDataLimit.getText());
        if (dataLimit > 9999 || dataLimit < 2) {
            this.errorTips = I18n.format("adm.error.datalimit");
            return;
        }
        nbt.setInteger("dataLimit", dataLimit);

        if (!isValidInteger(textFieldInterval.getText())) {
            this.errorTips = I18n.format("adm.error.interval");
            return;
        }
        int interval = Integer.parseInt(textFieldInterval.getText());
        interval = interval <= 2 ? 1 : interval;
        nbt.setInteger("interval", interval);

        if (!isValidDouble(textFieldYMin.getText())) {
            this.errorTips = I18n.format("adm.error.ymin");
            return;
        }
        nbt.setDouble("yMin", Double.parseDouble(textFieldYMin.getText()));

        if (!isValidDouble(textFieldYMax.getText())) {
            this.errorTips = I18n.format("adm.error.ymax");
            return;
        }
        nbt.setDouble("yMax", Double.parseDouble(textFieldYMax.getText()));

        if (!isValidDouble(textFieldDisplayNameScale.getText())) {
            this.errorTips = I18n.format("adm.error.displayscale");
            return;
        }
        nbt.setDouble("displayNameScale", Double.parseDouble(textFieldDisplayNameScale.getText()));

        if (!isValidDouble(textFieldLineWidth.getText())) {
            this.errorTips = I18n.format("adm.error.linewidth");
            return;
        }
        nbt.setDouble("lineWidth", Double.parseDouble(textFieldLineWidth.getText()));

        if (!isValidDouble(textFieldScaled.getText())) {
            this.errorTips = I18n.format("adm.error.scale");
            return;
        }
        nbt.setDouble("scale", Double.parseDouble(textFieldScaled.getText()));

        if (!isValidDouble(textFieldAxisFontScaled.getText())) {
            this.errorTips = I18n.format("adm.error.axisfontscale");
            return;
        }
        nbt.setDouble("axisFontScale", Double.parseDouble(textFieldAxisFontScaled.getText()));

        if (!isValidDouble(textFieldGridLineWidth.getText())) {
            this.errorTips = I18n.format("adm.error.gridlinewidth");
            return;
        }
        nbt.setDouble("gridLineWidth", Double.parseDouble(textFieldGridLineWidth.getText()));

        if (!isValidDouble(textFieldAxisLineWidth.getText())) {
            this.errorTips = I18n.format("adm.error.axislinewidth");
            return;
        }
        nbt.setDouble("axisLineWidth", Double.parseDouble(textFieldAxisLineWidth.getText()));

        if (!isValidDouble(textFieldTickLengthFactor.getText())) {
            this.errorTips = I18n.format("adm.error.ticklengthfactor");
            return;
        }
        nbt.setDouble("tickLengthFactor", Double.parseDouble(textFieldTickLengthFactor.getText()));

        updateDataType(nbt);
        saveAndSync(nbt);
        isInitialized = false;
        errorTips = "";
        openMainGui();
    }

    private double stepAlpha(double current, boolean increase) {
        int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
        int alphaInt = (int) Math.round(current * 100) + (increase ? step : -step);
        if (alphaInt > 100) alphaInt = 0;
        if (alphaInt < 0) alphaInt = 100;
        return alphaInt / 100.0;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawImage(AdmGuiTextures.SUB_GUI_TYPE_BOX, offsetX + 165, offsetY + 225, 335, 110);

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            GuiButton btnClear = getButtonByid(15);
            if (btnClear != null) {
                String text = I18n.format("adm.tooltip.clear");
                int textWidth = fontRendererObj.getStringWidth(text);
                int xPos = btnClear.xPosition - 3 - textWidth;
                fontRendererObj.drawStringWithShadow(text, xPos, btnClear.yPosition, 0XFF0000);
            }
        }

        GuiButton btnCollect = getButtonByid(18);
        if (btnCollect != null && mouseX >= btnCollect.xPosition
            && mouseX < btnCollect.xPosition + btnCollect.width
            && mouseY >= btnCollect.yPosition
            && mouseY < btnCollect.yPosition + btnCollect.height) {
            drawCollectTooltip(I18n.format("adm.tooltip.collect"), mouseX, mouseY);
        }

        if (Config.debugGuiNetworkLink) {
            fontRendererObj
                .drawStringWithShadow("按钮ID数: " + usedButtonIds.size(), offsetX + 10, offsetY + 400, 0x00FF00);
        }

        String[] label1 = { I18n.format("adm.label.xyz"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.zoffset"), I18n.format("adm.label.xrotation"),
            I18n.format("adm.label.yrotation"), I18n.format("adm.label.zrotation"), I18n.format("adm.label.xrange"),
            I18n.format("adm.label.yrange"), I18n.format("adm.label.datalimit"), I18n.format("adm.label.interval"),
            "yMin", "yMax" };
        autoText(label1, 0, 25, offsetX + 20, offsetY + 10, textColor, false);

        String[] label2 = { I18n.format("adm.label.displayname"), I18n.format("adm.label.displaynamescale"),
            I18n.format("adm.label.linewidth"), I18n.format("adm.label.scaled"),
            I18n.format("adm.label.axisfontscaled"), I18n.format("adm.label.gridlinewidth"),
            I18n.format("adm.label.axislinewidth"), I18n.format("adm.label.ticklengthfactor") };
        autoText(label2, 0, 25, offsetX + 170, offsetY + 10, textColor, false);

        String[] label3 = { I18n.format("adm.label.namealpha"), I18n.format("adm.label.axisalpha"),
            I18n.format("adm.label.axisfontalpha"), I18n.format("adm.label.datalinealpha"),
            I18n.format("adm.label.gridlinealpha") };
        autoText(label3, 0, 25, offsetX + 490, offsetY + 10, textColor, true);

        String[] label4 = { (int) (tileEntity.getNameAlpha(index) * 100) + "%",
            (int) (tileEntity.getAxisLineAlpha(index) * 100) + "%",
            (int) (tileEntity.getAxisFontAlpha(index) * 100) + "%", (int) (tileEntity.getLineAlpha(index) * 100) + "%",
            (int) (tileEntity.getGridLineAlpha(index) * 100) + "%" };
        autoText(label4, 0, 25, offsetX + 490, offsetY + 20, textColor, true);

        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.data_config_ae_network", index + 1),
            offsetX + 322,
            offsetY - 35,
            textColor);

        fontRendererObj.drawString(errorTips, offsetX + 230, offsetY + 380, 0xff0000);
        drawTextFieldsWithHover(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
        drawFocusedFieldHint(offsetX + 10, offsetY + 280);
    }

    private void drawCollectTooltip(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        drawHoveringText(lines, x, y, fontRendererObj);
    }

    private void updateDataType(NBTTagCompound nbt) {
        String dataTypeName;
        if (isTypeItem) {
            if (isUsed) {
                dataTypeName = isBytes ? "UsedBytes" : "UsedItemTypes";
            } else {
                dataTypeName = isBytes ? "TotalBytes" : "TotalItemTypes";
            }
        } else {
            if (isUsed) {
                dataTypeName = isBytes ? "UsedFluidBytes" : "UsedFluidTypes";
            } else {
                dataTypeName = isBytes ? "TotalFluidBytes" : "TotalFluidTypes";
            }
        }
        AdvanceDataMonitor.LOG.info("Update data type: " + dataTypeName);
        nbt.setString("name", dataTypeName);
        AdvanceDataMonitor.LOG.info("Update isValue: " + isValue);
        nbt.setBoolean("isValue", isValue);
    }

    private void initDataType() {
        String name = tileEntity.getName(index);
        switch (name) {
            case "TotalBytes" -> {
                isTypeItem = true;
                isUsed = false;
                isBytes = true;
            }
            case "UsedBytes" -> {
                isTypeItem = true;
                isUsed = true;
                isBytes = true;
            }
            case "TotalItemTypes" -> {
                isTypeItem = true;
                isUsed = false;
                isBytes = false;
            }
            case "UsedItemTypes" -> {
                isTypeItem = true;
                isUsed = true;
                isBytes = false;
            }
            case "TotalFluidBytes" -> {
                isTypeItem = false;
                isUsed = false;
                isBytes = true;
            }
            case "UsedFluidBytes" -> {
                isTypeItem = false;
                isUsed = true;
                isBytes = true;
            }
            case "TotalFluidTypes" -> {
                isTypeItem = false;
                isUsed = false;
                isBytes = false;
            }
            case "UsedFluidTypes" -> {
                isTypeItem = false;
                isUsed = true;
                isBytes = false;
            }
            default -> {
                isTypeItem = true;
                isUsed = true;
                isBytes = true;
            }
        }
    }

    private void clearDataValues() {
        NBTTagCompound nbt = tileEntity.getDataBound(index);
        nbt.setTag("dataValues", new NBTTagList());
        saveAndSync(nbt);
    }

    private void updateGridLineAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("gridLineAlpha", newValue);
        tileEntity.setGridLineAlpha(index, newValue);
        saveAndSync(nbt);
    }

    private void updateNameAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("nameAlpha", newValue);
        tileEntity.setNameAlpha(index, newValue);
        saveAndSync(nbt);
    }

    private void updateAxisAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("axisLineAlpha", newValue);
        tileEntity.setAxisLineAlpha(index, newValue);
        saveAndSync(nbt);
    }

    private void updateAxisFontAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("axisFontAlpha", newValue);
        tileEntity.setAxisFontAlpha(index, newValue);
        saveAndSync(nbt);
    }

    private void updateDataLineAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("lineAlpha", newValue);
        tileEntity.setLineAlpha(index, newValue);
        saveAndSync(nbt);
    }
}
