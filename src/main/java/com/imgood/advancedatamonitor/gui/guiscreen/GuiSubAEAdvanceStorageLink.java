package com.imgood.advancedatamonitor.gui.guiscreen;

import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidDouble;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidInteger;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.wrapText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.utils.ContentsHelper;

public class GuiSubAEAdvanceStorageLink extends ADM_GuiScreen {

    private final TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonotor;
    private final EntityPlayer player;
    private final World world;
    private final int index;

    private final List<ADM_GuiTextField> textFieldsLeft = new ArrayList<>();
    private final List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();
    private final Map<ADM_GuiTextField, String> fieldHints = new HashMap<>();
    private final List<String> contents = new ArrayList<>();

    private ADM_GuiTextField focusedField;
    private ADM_GuiTextField hoveredTextField;
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
    private ADM_GuiTextField textFieldTextScale;
    private ADM_GuiTextField textFieldColumns;
    private ADM_GuiTextField textFieldSpacing;
    private ADM_GuiTextField textFieldIconScale;
    private ADM_GuiTextField textFieldLineSpacing;
    private ADM_GuiTextField textFieldTextColor;
    private ADM_GuiTextField textFieldStorageIndex;

    private boolean showItemCount;
    private boolean showItemDelta;
    private boolean showItemName;
    private int itemCountOrder;
    private int itemDeltaOrder;
    private int itemNameOrder;
    private int storageSortMode;

    private boolean showItems = true;
    private boolean showFluids = true;
    private boolean showEssentia = true;

    private static final ResourceLocation button_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation button_hover_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation guiScreenHolographicDisplay_Main_Background = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_AdvanceDataMonitor_Main.png");
    private static final ResourceLocation textField_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_ADM_8020.png");
    private static final ResourceLocation textField_selected_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_selected_ADM.png");
    private static final ResourceLocation getGuiScreenHolographicDisplay_Sub_Background = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");

    private int offsetX = 100;
    private int offsetY = 100;
    private int startOffsetX = -270;
    private int startOffsetY = -200;
    private final int textColor = 0x00FFFF;
    private final int textHoverColor = 0x0055FF;
    private final int buttonRowYOffset1 = 370;
    private final int buttonRow1Width = 60;
    private String errorTips = "";
    private boolean isInitialized = false;
    private boolean isEnabled;

    public GuiSubAEAdvanceStorageLink(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.index = index;
        this.setBackgroundTexture(getGuiScreenHolographicDisplay_Sub_Background);
        this.setSize(600, 450);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        isEnabled = tileEntityAdvanceDataMonotor.getEnable(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
            contents.clear();
            contents.add(tileEntityAdvanceDataMonotor.getXYZ(index));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getXOffset(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getYOffset(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getZOffset(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getRotationX(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getRotationY(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getRotationZ(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getInterval(index)));
            contents.add(tileEntityAdvanceDataMonotor.getDisplayName(index));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getDisplayNameScale(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getScale(index)));
            contents.add(String.valueOf(tileEntityAdvanceDataMonotor.getTextScale(index)));
            contents.add(String.valueOf(getStorageInt("storageColumns", 4)));
            contents.add(String.valueOf(getStorageFloat("storageSpacing", 0.45f)));
            contents.add(String.valueOf(getStorageFloat("storageIconScale", 1.0f)));
            contents.add(String.valueOf(getStorageFloat("storageLineSpacing", 0.22f)));
            contents.add(getStorageString("textColor", "FFFFFF"));
            contents.add(getStorageString("storageCellIndex", ""));
            showItemCount = getStorageBoolean("showItemCount", true);
            showItemDelta = getStorageBoolean("showItemDelta", false);
            showItemName = getStorageBoolean("showItemName", false);
            itemCountOrder = getStorageInt("itemCountOrder", 0);
            itemDeltaOrder = getStorageInt("itemDeltaOrder", 1);
            itemNameOrder = getStorageInt("itemNameOrder", 2);
            storageSortMode = getStorageInt("storageSortMode", 0);
            showItems = getStorageBoolean("showItems", true);
            showFluids = getStorageBoolean("showFluids", true);
            showEssentia = getStorageBoolean("showEssentia", true);
            isInitialized = true;
        }

        this.offsetX = (this.width / 2) + startOffsetX;
        this.offsetY = (this.height / 2) + startOffsetY;
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 35);

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
        textFieldsRight.add(textFieldTextScale);
        textFieldsRight.add(textFieldColumns);
        textFieldsRight.add(textFieldSpacing);
        textFieldsRight.add(textFieldIconScale);
        textFieldsRight.add(textFieldLineSpacing);
        textFieldsRight.add(textFieldTextColor);
        textFieldsRight.add(textFieldStorageIndex);
        autoTextField("Right", textFieldsRight, 0, 25, offsetX + 275, offsetY + 10, 80, 20);

        fillFieldsFromContents();
        initFieldHints();
        initButtons();
    }

    private void saveCurrentState() {
        contents.clear();
        for (ADM_GuiTextField field : textFieldsLeft) contents.add(field.getText());
        for (ADM_GuiTextField field : textFieldsRight) contents.add(field.getText());
    }

    private int getStorageInt(String key, int fallback) {
        NBTTagCompound nbt = tileEntityAdvanceDataMonotor.getDataBound(index);
        return nbt.hasKey(key) ? nbt.getInteger(key) : fallback;
    }

    private float getStorageFloat(String key, float fallback) {
        NBTTagCompound nbt = tileEntityAdvanceDataMonotor.getDataBound(index);
        return nbt.hasKey(key) ? nbt.getFloat(key) : fallback;
    }

    private String getStorageString(String key, String fallback) {
        NBTTagCompound nbt = tileEntityAdvanceDataMonotor.getDataBound(index);
        return nbt.hasKey(key) ? nbt.getString(key) : fallback;
    }

    private boolean getStorageBoolean(String key, boolean fallback) {
        NBTTagCompound nbt = tileEntityAdvanceDataMonotor.getDataBound(index);
        return nbt.hasKey(key) ? nbt.getBoolean(key) : fallback;
    }

    private void fillFieldsFromContents() {
        ADM_GuiTextField[] fields = { textFieldTileEntityXYZ, textFieldxOffset, textFieldyOffset, textFieldzOffset,
            textFieldRotationX, textFieldRotationY, textFieldRotationZ, textFieldInterval, textFieldDisplayName,
            textFieldDisplayNameScale, textFieldScaled, textFieldTextScale, textFieldColumns, textFieldSpacing,
            textFieldIconScale, textFieldLineSpacing, textFieldTextColor, textFieldStorageIndex };
        for (int i = 0; i < fields.length; i++) {
            fields[i].setMaxStringLength(i == 8 ? 100 : 40);
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
        fieldHints.put(textFieldTextScale, "adm.hint.textscale");
        fieldHints.put(textFieldColumns, "adm.hint.storagecolumns");
        fieldHints.put(textFieldSpacing, "adm.hint.storagespacing");
        fieldHints.put(textFieldIconScale, "adm.hint.storageiconscale");
        fieldHints.put(textFieldLineSpacing, "adm.hint.storagelinespacing");
        fieldHints.put(textFieldTextColor, "adm.hint.storagecolor");
        fieldHints.put(textFieldStorageIndex, "adm.hint.storagecellindex");
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
        this.buttonList.add(button(20, offsetX + 360, offsetY + 20, 10, 10, "+"));
        this.buttonList.add(button(21, offsetX + 410, offsetY + 20, 10, 10, "-"));
        this.buttonList.add(button(22, offsetX + 360, offsetY + 45, 10, 10, "+"));
        this.buttonList.add(button(23, offsetX + 410, offsetY + 45, 10, 10, "-"));

        // Type filter buttons (above count/delta/name/sort row)
        int filterRowY = offsetY + buttonRowYOffset1 - 30;
        this.buttonList.add(button(34, offsetX + 220, filterRowY, 56, 20, getItemsButtonText()));
        this.buttonList.add(button(35, offsetX + 280, filterRowY, 56, 20, getFluidsButtonText()));
        this.buttonList.add(button(36, offsetX + 340, filterRowY, 56, 20, getEssentiaButtonText()));

        this.buttonList.add(button(30, offsetX + 220, offsetY + buttonRowYOffset1, 70, 20, getCountButtonText()));
        this.buttonList.add(button(31, offsetX + 300, offsetY + buttonRowYOffset1, 70, 20, getDeltaButtonText()));
        this.buttonList.add(button(33, offsetX + 380, offsetY + buttonRowYOffset1, 70, 20, getNameButtonText()));
        this.buttonList.add(button(32, offsetX + 460, offsetY + buttonRowYOffset1, 90, 20, getSortButtonText()));
    }

    private String getCountButtonText() {
        return I18n.format(showItemCount ? "adm.button.counton" : "adm.button.countoff");
    }

    private String getDeltaButtonText() {
        return I18n.format(showItemDelta ? "adm.button.deltaon" : "adm.button.deltaoff");
    }

    private String getNameButtonText() {
        return I18n.format(showItemName ? "adm.button.nameon" : "adm.button.nameoff");
    }

    private String getSortButtonText() {
        String key = storageSortMode == 1 ? "adm.button.sortcount"
            : storageSortMode == 2 ? "adm.button.sortmod" : "adm.button.sortdefault";
        return I18n.format(key);
    }

    private String getItemsButtonText() {
        return I18n.format(showItems ? "adm.button.itemson" : "adm.button.itemsoff");
    }

    private String getFluidsButtonText() {
        return I18n.format(showFluids ? "adm.button.fluidson" : "adm.button.fluidsoff");
    }

    private String getEssentiaButtonText() {
        return I18n.format(showEssentia ? "adm.button.essentiaon" : "adm.button.essentiaoff");
    }

    private ADM_GuiButton button(int id, int x, int y, int width, int height, String key) {
        return new ADM_GuiButton(id, x, y, width, height, I18n.format(key)).setTexture(button_texture)
            .setHoverTexture(button_hover_texture)
            .setUseHoverEffect(true)
            .setTextColor(textColor)
            .setTextHoverColor(textHoverColor);
    }

    public void autoTextField(String row, List<ADM_GuiTextField> textFields, int intervalX, int intervalY, int startX,
        int startY, int width, int height) {
        int curX = 0;
        int curY = 0;
        for (int i = 0; i < textFields.size(); i++) {
            ADM_GuiTextField field = new ADM_GuiTextField(
                this.fontRendererObj,
                startX + curX,
                startY + curY,
                width,
                height).setBackgroundTexture(textField_texture)
                    .setFocusedBackgroundTexture(textField_selected_texture);
            textFields.set(i, field);
            switch (row) {
                case "Left" -> assignLeftField(i, field);
                case "Right" -> assignRightField(i, field);
            }
            curX += intervalX;
            curY += intervalY;
        }
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
            case 3 -> textFieldTextScale = field;
            case 4 -> textFieldColumns = field;
            case 5 -> textFieldSpacing = field;
            case 6 -> textFieldIconScale = field;
            case 7 -> textFieldLineSpacing = field;
            case 8 -> textFieldTextColor = field;
            case 9 -> textFieldStorageIndex = field;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound existingNbt = tileEntityAdvanceDataMonotor.getDataBound(index);
        NBTTagCompound nbt = (NBTTagCompound) existingNbt.copy();
        switch (button.id) {
            case 0 -> save(nbt);
            case 1 -> openMain();
            case 7 -> {
                isEnabled = !isEnabled;
                nbt.setBoolean("enable", isEnabled);
                button.displayString = I18n.format(isEnabled ? "adm.button.disable" : "adm.button.enable");
                saveAndSync(nbt);
            }
            case 20 -> updateNameAlpha(stepAlpha(tileEntityAdvanceDataMonotor.getNameAlpha(index), true), nbt);
            case 21 -> updateNameAlpha(stepAlpha(tileEntityAdvanceDataMonotor.getNameAlpha(index), false), nbt);
            case 22 -> updateTextAlpha(stepAlpha(tileEntityAdvanceDataMonotor.getTextAlpha(index), true), nbt);
            case 23 -> updateTextAlpha(stepAlpha(tileEntityAdvanceDataMonotor.getTextAlpha(index), false), nbt);
            case 30 -> toggleItemCount(button, nbt);
            case 31 -> toggleItemDelta(button, nbt);
            case 33 -> toggleItemName(button, nbt);
            case 32 -> cycleSortMode(button, nbt);
            case 34 -> toggleShowItems(button, nbt);
            case 35 -> toggleShowFluids(button, nbt);
            case 36 -> toggleShowEssentia(button, nbt);
        }
    }

    private void toggleItemCount(GuiButton button, NBTTagCompound nbt) {
        showItemCount = !showItemCount;
        if (showItemCount) itemCountOrder = nextDisplayOrder();
        nbt.setBoolean("showItemCount", showItemCount);
        nbt.setInteger("itemCountOrder", itemCountOrder);
        button.displayString = getCountButtonText();
        saveAndSync(nbt);
    }

    private void toggleItemDelta(GuiButton button, NBTTagCompound nbt) {
        showItemDelta = !showItemDelta;
        if (showItemDelta) itemDeltaOrder = nextDisplayOrder();
        nbt.setBoolean("showItemDelta", showItemDelta);
        nbt.setInteger("itemDeltaOrder", itemDeltaOrder);
        button.displayString = getDeltaButtonText();
        saveAndSync(nbt);
    }

    private void toggleItemName(GuiButton button, NBTTagCompound nbt) {
        showItemName = !showItemName;
        if (showItemName) itemNameOrder = nextDisplayOrder();
        nbt.setBoolean("showItemName", showItemName);
        nbt.setInteger("itemNameOrder", itemNameOrder);
        button.displayString = getNameButtonText();
        saveAndSync(nbt);
    }

    private int nextDisplayOrder() {
        int maxOrder = -1;
        if (showItemCount) maxOrder = Math.max(maxOrder, itemCountOrder);
        if (showItemDelta) maxOrder = Math.max(maxOrder, itemDeltaOrder);
        if (showItemName) maxOrder = Math.max(maxOrder, itemNameOrder);
        return maxOrder + 1;
    }

    private void cycleSortMode(GuiButton button, NBTTagCompound nbt) {
        storageSortMode = (storageSortMode + 1) % 3;
        nbt.setInteger("storageSortMode", storageSortMode);
        button.displayString = getSortButtonText();
        saveAndSync(nbt);
    }

    private void toggleShowItems(GuiButton button, NBTTagCompound nbt) {
        showItems = !showItems;
        nbt.setBoolean("showItems", showItems);
        button.displayString = getItemsButtonText();
        saveAndSync(nbt);
    }

    private void toggleShowFluids(GuiButton button, NBTTagCompound nbt) {
        showFluids = !showFluids;
        nbt.setBoolean("showFluids", showFluids);
        button.displayString = getFluidsButtonText();
        saveAndSync(nbt);
    }

    private void toggleShowEssentia(GuiButton button, NBTTagCompound nbt) {
        showEssentia = !showEssentia;
        nbt.setBoolean("showEssentia", showEssentia);
        button.displayString = getEssentiaButtonText();
        saveAndSync(nbt);
    }

    private void save(NBTTagCompound nbt) {
        NBTTagList existingStorageItems = nbt.getTagList("storageItems", 10);
        String xyz = textFieldTileEntityXYZ.getText()
            .replace("，", ",")
            .replace(" ", "");
        if (!ContentsHelper.isValidPosFormat(xyz)) {
            errorTips = I18n.format("adm.error.xyz");
            return;
        }
        if (!validateNumbers()) return;
        if (!isValidInteger(textFieldColumns.getText())) {
            errorTips = I18n.format("adm.error.storagecolumns");
            return;
        }
        String storageCellIndexText = textFieldStorageIndex.getText()
            .trim();
        if (!storageCellIndexText.isEmpty()
            && (!isValidInteger(storageCellIndexText) || Integer.parseInt(storageCellIndexText) < 0)) {
            errorTips = I18n.format("adm.error.storagecellindex");
            return;
        }

        nbt.setString("XYZ", xyz);
        nbt.setString("dataType", "storage");
        nbt.setString("displayName", textFieldDisplayName.getText());
        nbt.setTag("storageItems", existingStorageItems.copy());
        nbt.setDouble("xOffset", Double.parseDouble(textFieldxOffset.getText()));
        nbt.setDouble("yOffset", Double.parseDouble(textFieldyOffset.getText()));
        nbt.setDouble("zOffset", Double.parseDouble(textFieldzOffset.getText()));
        nbt.setDouble("rotationX", Double.parseDouble(textFieldRotationX.getText()));
        nbt.setDouble("rotationY", Double.parseDouble(textFieldRotationY.getText()));
        nbt.setDouble("rotationZ", Double.parseDouble(textFieldRotationZ.getText()));
        int interval = Integer.parseInt(textFieldInterval.getText());
        nbt.setInteger("interval", interval <= 2 ? 1 : interval);
        nbt.setDouble("displayNameScale", Double.parseDouble(textFieldDisplayNameScale.getText()));
        nbt.setDouble("scale", Double.parseDouble(textFieldScaled.getText()));
        nbt.setDouble("textScale", Double.parseDouble(textFieldTextScale.getText()));
        nbt.setInteger("storageColumns", Math.max(1, Integer.parseInt(textFieldColumns.getText())));
        nbt.setDouble("storageSpacing", Double.parseDouble(textFieldSpacing.getText()));
        nbt.setDouble("storageIconScale", Double.parseDouble(textFieldIconScale.getText()));
        nbt.setDouble("storageLineSpacing", Double.parseDouble(textFieldLineSpacing.getText()));
        nbt.setString("textColor", textFieldTextColor.getText());
        nbt.setString("storageCellIndex", storageCellIndexText);
        nbt.removeTag("storageStatisticsInterval");
        nbt.setBoolean("showItemCount", showItemCount);
        nbt.setBoolean("showItemDelta", showItemDelta);
        nbt.setBoolean("showItemName", showItemName);
        nbt.setInteger("itemCountOrder", itemCountOrder);
        nbt.setInteger("itemDeltaOrder", itemDeltaOrder);
        nbt.setInteger("itemNameOrder", itemNameOrder);
        nbt.setInteger("storageSortMode", storageSortMode);
        nbt.setBoolean("showItems", showItems);
        nbt.setBoolean("showFluids", showFluids);
        nbt.setBoolean("showEssentia", showEssentia);
        nbt.setBoolean("enable", isEnabled);
        saveAndSync(nbt);
        isInitialized = false;
        errorTips = "";
        openMain();
    }

    private boolean validateNumbers() {
        ADM_GuiTextField[] doubleFields = { textFieldxOffset, textFieldyOffset, textFieldzOffset, textFieldRotationX,
            textFieldRotationY, textFieldRotationZ, textFieldDisplayNameScale, textFieldScaled, textFieldTextScale,
            textFieldSpacing, textFieldIconScale, textFieldLineSpacing };
        String[] errors = { "adm.error.xoffset", "adm.error.yoffset", "adm.error.zoffset", "adm.error.rotationx",
            "adm.error.rotationy", "adm.error.rotationz", "adm.error.displayscale", "adm.error.scale",
            "adm.error.textscale", "adm.error.storagespacing", "adm.error.storageiconscale",
            "adm.error.storagelinespacing" };
        for (int i = 0; i < doubleFields.length; i++) {
            if (!isValidDouble(doubleFields[i].getText())) {
                errorTips = I18n.format(errors[i]);
                return false;
            }
        }
        if (!isValidInteger(textFieldInterval.getText())) {
            errorTips = I18n.format("adm.error.interval");
            return false;
        }
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
        tileEntityAdvanceDataMonotor.setNameAlpha(index, alpha);
        nbt.setDouble("nameAlpha", alpha);
        saveAndSync(nbt);
    }

    private void updateTextAlpha(double alpha, NBTTagCompound nbt) {
        tileEntityAdvanceDataMonotor.setTextAlpha(index, alpha);
        nbt.setDouble("textAlpha", alpha);
        saveAndSync(nbt);
    }

    private void saveAndSync(NBTTagCompound nbt) {
        tileEntityAdvanceDataMonotor.setDisplayData(index, nbt);
        tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void openMain() {
        mc.displayGuiScreen(
            new GuiMainAdvanceDataMonitor(player, world, tileEntityAdvanceDataMonotor).setPosition(0, 0)
                .setSize(200, 200)
                .setStretch(true)
                .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        for (ADM_GuiTextField field : textFieldsLeft) field.textboxKeyTyped(typedChar, keyCode);
        for (ADM_GuiTextField field : textFieldsRight) field.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
            if (field.isFocused()) focusedField = field;
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
            if (field.isFocused()) focusedField = field;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (ADM_GuiTextField field : textFieldsLeft) field.updateCursorCounter();
        for (ADM_GuiTextField field : textFieldsRight) field.updateCursorCounter();
    }

    public void drawTextFieldBackground(ADM_GuiTextField textField, int x, int y, int width, int height) {
        this.drawImage(textField.getTextFieldTexture(), x, y, width, height);
    }

    public void drawTextFieldFocusBackground(ADM_GuiTextField textField, int x, int y, int width, int height) {
        this.drawImage(textField.getFocusedTextFieldTexture(), x, y, width, height);
    }

    public void drawTextFieldBackground(List<ADM_GuiTextField> textFields) {
        for (ADM_GuiTextField tf : textFields) {
            int xCoord = tf.xPosition;
            int yCoord = tf.yPosition + 2;
            if (tf.isFocused()) {
                drawTextFieldFocusBackground(tf, xCoord, yCoord, 100, 20);
            } else {
                drawTextFieldBackground(tf, xCoord, yCoord, 100, 20);
            }
        }
    }

    private boolean isMouseOver(ADM_GuiTextField textField, int mouseX, int mouseY) {
        return mouseX >= textField.xPosition && mouseX < textField.xPosition + textField.width
            && mouseY >= textField.yPosition
            && mouseY < textField.yPosition + textField.height;
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
            I18n.format("adm.label.storagecolumns"), I18n.format("adm.label.storagespacing"),
            I18n.format("adm.label.storageiconscale"), I18n.format("adm.label.storagelinespacing"),
            I18n.format("adm.label.storagecolor"),
            I18n.format("adm.label.storagecellindex") };
        autoText(label2, 0, 25, offsetX + 170, offsetY + 10, textColor, false);

        String[] label3 = { I18n.format("adm.label.namealpha"), I18n.format("adm.label.textalpha") };
        autoText(label3, 0, 25, offsetX + 490, offsetY + 10, textColor, true);
        String[] label4 = { (int) (tileEntityAdvanceDataMonotor.getNameAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getTextAlpha(index) * 100) + "%" };
        autoText(label4, 0, 25, offsetX + 490, offsetY + 20, textColor, true);

        drawCenteredString(
            fontRendererObj,
            I18n.format("adm.title.storage", index + 1),
            offsetX + 322,
            offsetY - 35,
            textColor);
        fontRendererObj.drawString(errorTips, offsetX + 230, offsetY + 380, 0xff0000);
        drawTextFieldBackground(textFieldsLeft);
        drawTextFieldBackground(textFieldsRight);

        hoveredTextField = null;
        for (ADM_GuiTextField field : textFieldsLeft) {
            field.drawTextBox();
            if (isMouseOver(field, mouseX, mouseY)) hoveredTextField = field;
        }
        for (ADM_GuiTextField field : textFieldsRight) {
            field.drawTextBox();
            if (isMouseOver(field, mouseX, mouseY)) hoveredTextField = field;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (focusedField != null && fieldHints.containsKey(focusedField)) {
            String hint = I18n.format(fieldHints.get(focusedField));
            List<String> wrappedHint = wrapText(hint, 35);
            int yPos = offsetY + 280;
            fontRendererObj.drawStringWithShadow(I18n.format("adm.property.tips"), offsetX + 10, yPos, 0x00FFFF);
            for (String line : wrappedHint) {
                fontRendererObj.drawStringWithShadow(
                    String.valueOf((char) 0x00A7) + "l" + line,
                    offsetX + 10,
                    yPos + 10,
                    0x00FFFF);
                yPos += 10;
            }
        }
    }

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color,
        boolean textCenter) {
        int curX = 0;
        int curY = 0;
        for (String t : text) {
            int x = startX + curX;
            int y = startY + curY;
            if (textCenter) x = startX - fontRendererObj.getStringWidth(t) / 2 + curX;
            fontRendererObj.drawString(t, x, y, color);
            curX += intervalX;
            curY += intervalY;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
