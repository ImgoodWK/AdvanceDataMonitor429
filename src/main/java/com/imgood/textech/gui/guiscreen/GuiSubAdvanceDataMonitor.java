package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidDouble;
import static com.imgood.textech.utils.ContentsHelper.isValidHexColor;
import static com.imgood.textech.utils.ContentsHelper.isValidInteger;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AbstractMonitorSubGui;
import com.imgood.textech.monitor.MonitorWidgetSpec;
import com.imgood.textech.monitor.MonitorThresholdEvaluator;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.ContentsHelper;

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
    private ADM_GuiTextField textFieldMetricKey;
    private ADM_GuiTextField textFieldSourceKind;
    private ADM_GuiTextField textFieldTargetValue;
    private ADM_GuiTextField textFieldThresholdValue;
    private ADM_GuiTextField textFieldThresholdHysteresis;
    private ADM_GuiTextField textFieldThresholdOutputMin;
    private ADM_GuiTextField textFieldThresholdOutputMax;
    private final List<ADM_GuiTextField> thresholdFields = new ArrayList<ADM_GuiTextField>();

    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 30;
    private int buttonRowConfigXoffset1 = 360;
    private int buttonRow2Width = 65;

    private static final String[] SOURCE_KINDS = { MonitorWidgetSpec.SOURCE_TILE_METRIC,
        MonitorWidgetSpec.SOURCE_AE_METRIC, MonitorWidgetSpec.SOURCE_WIRELESS_EU,
        MonitorWidgetSpec.SOURCE_WIRELESS_STEAM, MonitorWidgetSpec.SOURCE_STORAGE_SUMMARY,
        MonitorWidgetSpec.SOURCE_GT_SUMMARY };

    private String dataType;
    private String kind;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;
    private boolean thresholdEnabled;
    private String thresholdOperator = MonitorThresholdEvaluator.OPERATOR_GTE;

    public GuiSubAdvanceDataMonitor(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        super(player, world, tileEntity, index);
        this.setSize(600, 480);
    }

    @Override
    protected void assignTextField(String row, int fieldIndex, ADM_GuiTextField field) {
        if ("Left".equals(row)) {
            assignLeftField(fieldIndex, field);
        } else if ("Right".equals(row)) {
            assignRightField(fieldIndex, field);
        } else if ("Threshold".equals(row)) {
            assignThresholdField(fieldIndex, field);
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
        textFieldsRight.add(textFieldMetricKey);
        textFieldsRight.add(textFieldSourceKind);
        textFieldsRight.add(textFieldTargetValue);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 275, offsetY + 10, 80, 20);

        thresholdFields.clear();
        thresholdFields.add(textFieldThresholdValue);
        thresholdFields.add(textFieldThresholdHysteresis);
        thresholdFields.add(textFieldThresholdOutputMin);
        thresholdFields.add(textFieldThresholdOutputMax);
        autoTextField("Threshold", thresholdFields, 0, 25, offsetX + 500, offsetY + 110, 80, 20);
        textFieldsRight.addAll(thresholdFields);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
        selectKind(MonitorWidgetSpec.getKind(tileEntity.getDataBound(index)));
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
        contents.add(textFieldMetricKey.getText());
        contents.add(textFieldSourceKind.getText());
        contents.add(textFieldTargetValue.getText());
        contents.add(textFieldThresholdValue.getText());
        contents.add(textFieldThresholdHysteresis.getText());
        contents.add(textFieldThresholdOutputMin.getText());
        contents.add(textFieldThresholdOutputMax.getText());
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
        contents.add(MonitorWidgetSpec.getMetricKey(tileEntity.getDataBound(index)));
        contents.add(MonitorWidgetSpec.getSourceKind(tileEntity.getDataBound(index)));
        contents.add(String.valueOf(MonitorWidgetSpec.getTargetValue(tileEntity.getDataBound(index))));
        NBTTagCompound threshold = tileEntity.getDataBound(index).getCompoundTag(MonitorWidgetSpec.THRESHOLD_KEY);
        thresholdEnabled = threshold.getBoolean("enabled");
        thresholdOperator = MonitorThresholdEvaluator.OPERATOR_LTE.equals(threshold.getString("operator"))
            ? MonitorThresholdEvaluator.OPERATOR_LTE
            : MonitorThresholdEvaluator.OPERATOR_GTE;
        contents.add(String.valueOf(threshold.getDouble("value")));
        contents.add(String.valueOf(threshold.getDouble("hysteresis")));
        contents.add(String.valueOf(threshold.getDouble("outputMin")));
        contents.add(String.valueOf(threshold.getDouble("outputMax")));
    }

    private void fillFieldsFromContents() {
        ADM_GuiTextField[] fields = { textFieldTileEntityXYZ, textFieldxOffset, textFieldyOffset, textFieldzOffset,
            textFieldRotationX, textFieldRotationY, textFieldRotationZ, textFieldXRange, textFieldYRange,
            textFieldDataLimit, textFieldInterval, textFieldYMin, textFieldYMax, textFieldName, textFieldDisplayName,
            textFieldDisplayNameScale, textFieldDisplayNameColor, textFieldAxisLineColor, textFieldAxisFontColor,
            textFieldLineColor, textFieldLineWidth, textFieldScaled, textFieldAxisFontScaled, textFieldMetricKey,
            textFieldSourceKind, textFieldTargetValue, textFieldThresholdValue, textFieldThresholdHysteresis,
            textFieldThresholdOutputMin, textFieldThresholdOutputMax };
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
        fieldHints.put(textFieldMetricKey, "adm.hint.metricKey");
        fieldHints.put(textFieldSourceKind, "adm.hint.sourceKind");
        fieldHints.put(textFieldTargetValue, "adm.hint.targetValue");
        fieldHints.put(textFieldThresholdValue, "adm.hint.threshold.value");
        fieldHints.put(textFieldThresholdHysteresis, "adm.hint.threshold.hysteresis");
        fieldHints.put(textFieldThresholdOutputMin, "adm.hint.threshold.output_min");
        fieldHints.put(textFieldThresholdOutputMax, "adm.hint.threshold.output_max");
    }

    private void initButtons() {
        this.buttonList.add(button(0, offsetX, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.save"));
        this.buttonList
            .add(button(1, offsetX + 70, offsetY + buttonRowYOffset1, buttonRow1Width, 20, "adm.button.cancel"));
        this.buttonList.add(
            button(
                23,
                offsetX + 140,
                offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                isEnabled ? "adm.button.disable" : "adm.button.enable"));

        this.buttonList
            .add(button(2, offsetX, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.statCard"));
        this.buttonList.add(
            button(3, offsetX + 70, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.progressBar"));
        this.buttonList
            .add(button(4, offsetX + 140, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.gauge"));
        this.buttonList.add(
            button(5, offsetX + 210, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.lineChart"));
        this.buttonList.add(
            button(6, offsetX + 280, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.barChart"));
        this.buttonList.add(
            button(7, offsetX + 350, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.pieChart"));
        this.buttonList.add(
            button(8, offsetX + 420, offsetY + buttonRowYOffset2, buttonRow2Width, 20, "adm.datatype.dataTable"));
        this.buttonList.add(
            button(30, offsetX + 490, offsetY + buttonRowYOffset2, 100, 20, "adm.button.sourceKind"));

        int configY = buttonRowConfigYoffset1;
        this.buttonList.add(
            button(
                20,
                offsetX + buttonRowConfigXoffset1,
                offsetY + configY,
                buttonRow2Width,
                20,
                "adm.button.enableAxis"));
        configY += buttonRowConfigYinterval1;
        this.buttonList.add(
            button(
                21,
                offsetX + buttonRowConfigXoffset1,
                offsetY + configY,
                buttonRow2Width,
                20,
                "adm.button.enableData"));
        configY += buttonRowConfigYinterval1;
        this.buttonList.add(
            button(
                22,
                offsetX + buttonRowConfigXoffset1,
                offsetY + configY,
                buttonRow2Width,
                20,
                "adm.button.enableAxisFont"));
        this.buttonList.add(
            button(
                40,
                offsetX + 435,
                offsetY + buttonRowConfigYoffset1,
                155,
                20,
                thresholdEnabled ? "adm.button.threshold_on" : "adm.button.threshold_off"));
        this.buttonList.add(
            monitorButton(
                41,
                offsetX + 435,
                offsetY + buttonRowConfigYoffset1 + buttonRowConfigYinterval1,
                155,
                20,
                MonitorThresholdEvaluator.OPERATOR_LTE.equals(thresholdOperator) ? "<=" : ">=",
                textColor,
                textHoverColor));
    }

    private void updateEnableButtonLabels() {
        getButtonByid(23).displayString = I18n.format(!isEnabled ? "adm.button.disable" : "adm.button.enable");
        getButtonByid(20).displayString = I18n
            .format(!isEnabledAxis ? "adm.button.disableAxis" : "adm.button.enableAxis");
        getButtonByid(21).displayString = I18n
            .format(!isEnabledData ? "adm.button.disableData" : "adm.button.enableData");
        getButtonByid(22).displayString = I18n
            .format(!isEnabledAxisFont ? "adm.button.disableAxisFont" : "adm.button.enableAxisFont");

        ((ADM_GuiButton) getButtonByid(23)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(20)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(21)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(22)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
        getButtonByid(40).displayString = I18n
            .format(thresholdEnabled ? "adm.button.threshold_on" : "adm.button.threshold_off");
        getButtonByid(41).displayString = MonitorThresholdEvaluator.OPERATOR_LTE.equals(thresholdOperator) ? "<=" : ">=";
        ((ADM_GuiButton) getButtonByid(40)).setTextColor(thresholdEnabled ? 0x00FFFF : 0xFF0000);
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
            case 10 -> textFieldMetricKey = field;
            case 11 -> textFieldSourceKind = field;
            case 12 -> textFieldTargetValue = field;
        }
    }

    private void assignThresholdField(int fieldIndex, ADM_GuiTextField field) {
        switch (fieldIndex) {
            case 0 -> textFieldThresholdValue = field;
            case 1 -> textFieldThresholdHysteresis = field;
            case 2 -> textFieldThresholdOutputMin = field;
            case 3 -> textFieldThresholdOutputMax = field;
        }
    }

    private void selectKind(String selectedKind) {
        kind = MonitorWidgetSpec.normalizeKind(selectedKind);
        if (kind.isEmpty()) kind = MonitorWidgetSpec.KIND_LINE_CHART;
        for (int id = 2; id <= 8; id++) {
            ((ADM_GuiButton) getButtonByid(id)).setUseRGBEffect(false);
        }
        int selectedId = switch (kind) {
            case MonitorWidgetSpec.KIND_STAT_CARD -> 2;
            case MonitorWidgetSpec.KIND_PROGRESS_BAR -> 3;
            case MonitorWidgetSpec.KIND_GAUGE -> 4;
            case MonitorWidgetSpec.KIND_BAR_CHART -> 6;
            case MonitorWidgetSpec.KIND_PIE_CHART -> 7;
            case MonitorWidgetSpec.KIND_DATA_TABLE -> 8;
            default -> 5;
        };
        ((ADM_GuiButton) getButtonByid(selectedId)).setUseRGBEffect(true);
        dataType = MonitorWidgetSpec.legacyDataTypeFromKind(kind);
    }

    private void applyKind(NBTTagCompound nbt, String selectedKind) {
        selectKind(selectedKind);
        nbt.setString("kind", kind);
        nbt.setString("renderType", MonitorWidgetSpec.KIND_LINE_CHART.equals(kind) ? "line" : kind);
        nbt.setString("dataType", dataType);
        if (!MonitorWidgetSpec.KIND_LINE_CHART.equals(kind)) nbt.setString("seriesTransform", "");
    }

    private void cycleSourceKind() {
        String current = textFieldSourceKind.getText().trim();
        int next = 0;
        for (int i = 0; i < SOURCE_KINDS.length; i++) {
            if (SOURCE_KINDS[i].equals(current)) {
                next = (i + 1) % SOURCE_KINDS.length;
                break;
            }
        }
        textFieldSourceKind.setText(SOURCE_KINDS[next]);
    }

    private static boolean isSupportedSourceKind(String value) {
        for (String sourceKind : SOURCE_KINDS) {
            if (sourceKind.equals(value)) return true;
        }
        return false;
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
            case 2 -> selectKind(MonitorWidgetSpec.KIND_STAT_CARD);
            case 3 -> selectKind(MonitorWidgetSpec.KIND_PROGRESS_BAR);
            case 4 -> selectKind(MonitorWidgetSpec.KIND_GAUGE);
            case 5 -> selectKind(MonitorWidgetSpec.KIND_LINE_CHART);
            case 6 -> selectKind(MonitorWidgetSpec.KIND_BAR_CHART);
            case 7 -> selectKind(MonitorWidgetSpec.KIND_PIE_CHART);
            case 8 -> selectKind(MonitorWidgetSpec.KIND_DATA_TABLE);
            case 23 -> {
                isEnabled = !isEnabled;
                ((ADM_GuiButton) getButtonByid(23)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enable", !existingNbt.getBoolean("enable"));
                tileEntity.setEnable(index, !existingNbt.getBoolean("enable"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enable") ? "adm.button.disable" : "adm.button.enable");
                saveAndSync(nbt);
            }
            case 20 -> {
                isEnabledAxis = !isEnabledAxis;
                ((ADM_GuiButton) getButtonByid(20)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableAxis", !existingNbt.getBoolean("enableAxis"));
                tileEntity.setEnableAxis(index, !existingNbt.getBoolean("enableAxis"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableAxis") ? "adm.button.disableAxis" : "adm.button.enableAxis");
                saveAndSync(nbt);
            }
            case 21 -> {
                isEnabledData = !isEnabledData;
                ((ADM_GuiButton) getButtonByid(21)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableData", !existingNbt.getBoolean("enableData"));
                tileEntity.setEnableData(index, !existingNbt.getBoolean("enableData"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableData") ? "adm.button.disableData" : "adm.button.enableData");
                saveAndSync(nbt);
            }
            case 22 -> {
                isEnabledAxisFont = !isEnabledAxisFont;
                ((ADM_GuiButton) getButtonByid(22)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableAxisFont", !existingNbt.getBoolean("enableAxisFont"));
                tileEntity.setEnableAxisFont(index, !existingNbt.getBoolean("enableAxisFont"));
                button.displayString = I18n.format(
                    !existingNbt.getBoolean("enableAxisFont") ? "adm.button.disableAxisFont"
                        : "adm.button.enableAxisFont");
                saveAndSync(nbt);
            }
            case 30 -> cycleSourceKind();
            case 40 -> {
                thresholdEnabled = !thresholdEnabled;
                updateEnableButtonLabels();
            }
            case 41 -> {
                thresholdOperator = MonitorThresholdEvaluator.OPERATOR_LTE.equals(thresholdOperator)
                    ? MonitorThresholdEvaluator.OPERATOR_GTE
                    : MonitorThresholdEvaluator.OPERATOR_LTE;
                updateEnableButtonLabels();
            }
            default -> {}
        }
    }

    private void save(NBTTagCompound nbt, NBTTagList existingDataValues) {
        beginValidation();
        nbt.setTag("dataValues", existingDataValues.copy());
        applyKind(nbt, kind == null ? MonitorWidgetSpec.KIND_LINE_CHART : kind);

        String metricKey = textFieldMetricKey.getText().trim();
        if (metricKey.isEmpty()) {
            rejectField(textFieldMetricKey, I18n.format("adm.error.metricKey"));
            return;
        }
        nbt.setString("metricKey", metricKey);
        nbt.setString("name", metricKey);

        String sourceKind = textFieldSourceKind.getText().trim();
        if (!isSupportedSourceKind(sourceKind)) {
            rejectField(textFieldSourceKind, I18n.format("adm.error.sourceKind"));
            return;
        }
        nbt.setString("sourceKind", sourceKind);

        if (!isValidDouble(textFieldTargetValue.getText())) {
            rejectField(textFieldTargetValue, I18n.format("adm.error.targetValue"));
            return;
        }
        double targetValue = Double.parseDouble(textFieldTargetValue.getText());
        if (targetValue < 0.0D) {
            rejectField(textFieldTargetValue, I18n.format("adm.error.targetValue"));
            return;
        }
        nbt.setDouble("targetValue", targetValue);

        if (!isFiniteDouble(textFieldThresholdValue.getText())) {
            rejectField(textFieldThresholdValue, I18n.format("adm.error.threshold.number"));
            return;
        }
        if (!isFiniteDouble(textFieldThresholdHysteresis.getText())) {
            rejectField(textFieldThresholdHysteresis, I18n.format("adm.error.threshold.number"));
            return;
        }
        if (!isFiniteDouble(textFieldThresholdOutputMin.getText())) {
            rejectField(textFieldThresholdOutputMin, I18n.format("adm.error.threshold.number"));
            return;
        }
        if (!isFiniteDouble(textFieldThresholdOutputMax.getText())) {
            rejectField(textFieldThresholdOutputMax, I18n.format("adm.error.threshold.number"));
            return;
        }
        NBTTagCompound threshold = new NBTTagCompound();
        threshold.setBoolean("enabled", thresholdEnabled);
        threshold.setString("operator", thresholdOperator);
        threshold.setDouble("value", Double.parseDouble(textFieldThresholdValue.getText()));
        threshold.setDouble(
            "hysteresis",
            Math.max(0.0D, Double.parseDouble(textFieldThresholdHysteresis.getText())));
        threshold.setDouble("outputMin", Double.parseDouble(textFieldThresholdOutputMin.getText()));
        threshold.setDouble("outputMax", Double.parseDouble(textFieldThresholdOutputMax.getText()));
        nbt.setTag(MonitorWidgetSpec.THRESHOLD_KEY, threshold);

        String xyz = textFieldTileEntityXYZ.getText()
            .replace("，", ",")
            .replace(" ", "");
        if (!ContentsHelper.isValidPosFormat(xyz)) {
            rejectField(textFieldTileEntityXYZ, I18n.format("adm.error.xyz"));
            return;
        }
        nbt.setString("XYZ", xyz);

        if (!isValidDouble(textFieldxOffset.getText())) {
            rejectField(textFieldxOffset, I18n.format("adm.error.xoffset"));
            return;
        }
        nbt.setDouble("xOffset", Double.parseDouble(textFieldxOffset.getText()));

        if (!isValidDouble(textFieldyOffset.getText())) {
            rejectField(textFieldyOffset, I18n.format("adm.error.yoffset"));
            return;
        }
        nbt.setDouble("yOffset", Double.parseDouble(textFieldyOffset.getText()));

        if (!isValidDouble(textFieldzOffset.getText())) {
            rejectField(textFieldzOffset, I18n.format("adm.error.zoffset"));
            return;
        }
        nbt.setDouble("zOffset", Double.parseDouble(textFieldzOffset.getText()));

        if (!isValidDouble(textFieldRotationX.getText())) {
            rejectField(textFieldRotationX, I18n.format("adm.error.rotationx"));
            return;
        }
        nbt.setDouble("rotationX", Double.parseDouble(textFieldRotationX.getText()));

        if (!isValidDouble(textFieldRotationY.getText())) {
            rejectField(textFieldRotationY, I18n.format("adm.error.rotationy"));
            return;
        }
        nbt.setDouble("rotationY", Double.parseDouble(textFieldRotationY.getText()));

        if (!isValidDouble(textFieldRotationZ.getText())) {
            rejectField(textFieldRotationZ, I18n.format("adm.error.rotationz"));
            return;
        }
        nbt.setDouble("rotationZ", Double.parseDouble(textFieldRotationZ.getText()));

        if (!isValidDouble(textFieldXRange.getText())) {
            rejectField(textFieldXRange, I18n.format("adm.error.xrange"));
            return;
        }
        nbt.setDouble("xRange", Double.parseDouble(textFieldXRange.getText()));

        if (!isValidDouble(textFieldYRange.getText())) {
            rejectField(textFieldYRange, I18n.format("adm.error.yrange"));
            return;
        }
        nbt.setDouble("yRange", Double.parseDouble(textFieldYRange.getText()));

        if (!isValidInteger(textFieldDataLimit.getText())) {
            rejectField(textFieldDataLimit, I18n.format("adm.error.datalimit"));
            return;
        }
        int dataLimit = Integer.parseInt(textFieldDataLimit.getText());
        if (dataLimit > 9999 || dataLimit < 2) {
            rejectField(textFieldDataLimit, I18n.format("adm.error.datalimit"));
            return;
        }
        nbt.setInteger("dataLimit", Integer.parseInt(textFieldDataLimit.getText()));

        if (!isValidInteger(textFieldInterval.getText())) {
            rejectField(textFieldInterval, I18n.format("adm.error.interval"));
            return;
        }
        int interval = Integer.parseInt(textFieldInterval.getText());
        interval = interval <= 2 ? 1 : interval;
        nbt.setInteger("interval", interval);

        if (!isValidDouble(textFieldYMin.getText())) {
            rejectField(textFieldYMin, I18n.format("adm.error.ymin"));
            return;
        }
        nbt.setDouble("yMin", Double.parseDouble(textFieldYMin.getText()));

        if (!isValidDouble(textFieldYMax.getText())) {
            rejectField(textFieldYMax, I18n.format("adm.error.ymax"));
            return;
        }
        nbt.setDouble("yMax", Double.parseDouble(textFieldYMax.getText()));

        nbt.setString("name", textFieldName.getText());
        nbt.setString("displayName", textFieldDisplayName.getText());

        if (!isValidDouble(textFieldDisplayNameScale.getText())) {
            rejectField(textFieldDisplayNameScale, I18n.format("adm.error.displayscale"));
            return;
        }
        nbt.setDouble("displayNameScale", Double.parseDouble(textFieldDisplayNameScale.getText()));

        if (!isValidHexColor(textFieldDisplayNameColor.getText())) {
            rejectField(textFieldDisplayNameColor, I18n.format("adm.error.displaycolor"));
            return;
        }
        nbt.setString("displayNameColor", textFieldDisplayNameColor.getText());

        if (!isValidHexColor(textFieldAxisLineColor.getText())) {
            rejectField(textFieldAxisLineColor, I18n.format("adm.error.axislinecolor"));
            return;
        }
        nbt.setString("axisLineColor", textFieldAxisLineColor.getText());

        if (!isValidHexColor(textFieldAxisFontColor.getText())) {
            rejectField(textFieldAxisFontColor, I18n.format("adm.error.axisfontcolor"));
            return;
        }
        nbt.setString("axisFontColor", textFieldAxisFontColor.getText());

        if (!isValidHexColor(textFieldLineColor.getText())) {
            rejectField(textFieldLineColor, I18n.format("adm.error.linecolor"));
            return;
        }
        nbt.setString("lineColor", textFieldLineColor.getText());

        if (!isValidDouble(textFieldLineWidth.getText())) {
            rejectField(textFieldLineWidth, I18n.format("adm.error.linewidth"));
            return;
        }
        nbt.setDouble("lineWidth", Double.parseDouble(textFieldLineWidth.getText()));

        if (!isValidDouble(textFieldScaled.getText())) {
            rejectField(textFieldScaled, I18n.format("adm.error.scale"));
            return;
        }
        nbt.setDouble("scale", Double.parseDouble(textFieldScaled.getText()));

        if (!isValidDouble(textFieldAxisFontScaled.getText())) {
            rejectField(textFieldAxisFontScaled, I18n.format("adm.error.axisfontscale"));
            return;
        }
        nbt.setDouble("axisFontScale", Double.parseDouble(textFieldAxisFontScaled.getText()));

        saveAndSync(nbt);
        isInitialized = false;
        errorTips = "";
        openMainGui();
    }

    private static boolean isFiniteDouble(String text) {
        if (!isValidDouble(text)) {
            return false;
        }
        double value = Double.parseDouble(text);
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        isInitialized = false;
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawAdmScreen(mouseX, mouseY, partialTicks);
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
            I18n.format("adm.label.axisfontscaled"), I18n.format("adm.label.metricKey"),
            I18n.format("adm.label.sourceKind"), I18n.format("adm.label.targetValue") };
        autoText(label2, 0, 25, offsetX + 170, offsetY + 10, textColor);

        String[] thresholdLabels = { I18n.format("adm.label.threshold.value"),
            I18n.format("adm.label.threshold.hysteresis"), I18n.format("adm.label.threshold.output_min"),
            I18n.format("adm.label.threshold.output_max") };
        autoText(thresholdLabels, 0, 25, offsetX + 360, offsetY + 110, textColor);
        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.label.threshold.title"),
            offsetX + 475,
            offsetY + 95,
            textColor);

        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.data_config", index + 1),
            offsetX + 322,
            offsetY - 35,
            textColor);

        drawMonitorFeedbackBand();
        if (isValidInteger(textFieldDataLimit.getText()) && Integer.parseInt(textFieldDataLimit.getText()) > 512) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("adm.warning.dataLimitLarge"),
                offsetX + 295,
                offsetY + 322,
                0xFFCC33);
        }
        if (isFiniteDouble(textFieldThresholdOutputMin.getText())
            && isFiniteDouble(textFieldThresholdOutputMax.getText())
            && Double.parseDouble(textFieldThresholdOutputMax.getText())
                <= Double.parseDouble(textFieldThresholdOutputMin.getText())) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("adm.warning.threshold.binary_fallback"),
                offsetX + 475,
                offsetY + 220,
                0xFFCC33);
        }

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

        drawFocusedFieldHint(offsetX + 10, offsetY + 280);
    }
}
