package com.imgood.advancedatamonitor.gui.guiscreen;

import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidDouble;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidInteger;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.wrapText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.utils.ContentsHelper;
import com.imgood.advancedatamonitor.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: Crafting CPU Config (per-binding sub GUI)
 * - ZH: 合成处理器配置（绑定子界面）
 * Lang keys: adm.title.data_config_ae_crafting
 */
public class GuiSubAEAdvanceCraftingLink extends ADM_GuiScreen {

    private final TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonotor;
    @SuppressWarnings("unused")
    private EntityPlayer player;
    private World world;

    private int index;
    private List<ADM_GuiTextField> textFieldsLeft = new ArrayList<>();
    private List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();
    private final Map<ADM_GuiTextField, String> fieldHints = new HashMap<>();

    private ADM_GuiTextField hoveredTextField;
    private ADM_GuiTextField focusedField = null;

    // 左侧文本框
    private ADM_GuiTextField textFieldTileEntityXYZ;
    private ADM_GuiTextField textFieldxOffset;
    private ADM_GuiTextField textFieldyOffset;
    private ADM_GuiTextField textFieldzOffset;
    private ADM_GuiTextField textFieldRotationX;
    private ADM_GuiTextField textFieldRotationY;
    private ADM_GuiTextField textFieldRotationZ;
    private ADM_GuiTextField textFieldInterval;

    // 右侧文本框
    private ADM_GuiTextField textFieldDisplayName;
    private ADM_GuiTextField textFieldDisplayNameScale;
    private ADM_GuiTextField textFieldScaled;
    private ADM_GuiTextField textScale;
    private ADM_GuiTextField textFieldCraftingTemplate; // 新增：craftingTemplate

    private List<String> contents = new ArrayList<>();

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
    private static final ResourceLocation textField_hover_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_hover_ADM_8020.png");
    private static final ResourceLocation getGuiScreenHolographicDisplay_Sub_Background = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");

    private int offsetX = 100;
    private int offsetY = 100;
    private int startOffsetX = -270;
    private int startOffsetY = -200;
    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;

    private int buttonRowYOffset1 = 370;
    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 25;
    private int buttonRowConfigXoffset1 = 360;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 60;
    private int buttonRow2Width = 60;

    private String errorTips = "";

    private boolean isInitialized = false;
    private String dataType;
    private boolean isEnabled;
    // 新增：监测范围和文字对齐状态
    private boolean monitorNetworkWide; // false=单处理器，true=全网络
    private int textAlign; // 0=左对齐，1=居中，2=右对齐

    private Set<Integer> usedButtonIds = new HashSet<>();

    public GuiSubAEAdvanceCraftingLink(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.index = index;
        this.setBackgroundTexture(getGuiScreenHolographicDisplay_Sub_Background);
        this.setSize(600, 450);
        this.setStretch(false);
    }

    private void checkUsedButtonIds() {
        usedButtonIds.clear();
        for (GuiButton button : this.buttonList) {
            usedButtonIds.add(button.id);
        }
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

    private void saveCurrentState() {
        this.contents.clear();
        // 左侧
        this.contents.add(textFieldTileEntityXYZ.getText());
        this.contents.add(textFieldxOffset.getText());
        this.contents.add(textFieldyOffset.getText());
        this.contents.add(textFieldzOffset.getText());
        this.contents.add(textFieldRotationX.getText());
        this.contents.add(textFieldRotationY.getText());
        this.contents.add(textFieldRotationZ.getText());
        this.contents.add(textFieldInterval.getText());
        // 右侧
        this.contents.add(textFieldDisplayName.getText());
        this.contents.add(textFieldDisplayNameScale.getText());
        this.contents.add(textFieldScaled.getText());
        this.contents.add(textScale.getText());
        this.contents.add(textFieldCraftingTemplate.getText()); // 新增保存内容
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        isEnabled = tileEntityAdvanceDataMonotor.getEnable(index);
        // 读取新字段
        monitorNetworkWide = tileEntityAdvanceDataMonotor.getMonitorNetworkWide(index);
        textAlign = tileEntityAdvanceDataMonotor.getTextAlign(index);

        if (isInitialized) {
            saveCurrentState();
        } else {
            this.contents.clear();
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getXYZ(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getXOffset(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYOffset(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getZOffset(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationX(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationY(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationZ(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getInterval(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayName(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getTextScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getCraftingTemplate(this.index))); // 新增初始化内容
            isInitialized = true;
        }

        this.offsetX = (this.width / 2) + startOffsetX;
        this.offsetY = (this.height / 2) + startOffsetY;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 35);

        // ---- 左侧文本框 ----
        this.textFieldsLeft.clear();
        textFieldsLeft.add(this.textFieldTileEntityXYZ);
        textFieldsLeft.add(this.textFieldxOffset);
        textFieldsLeft.add(this.textFieldyOffset);
        textFieldsLeft.add(this.textFieldzOffset);
        textFieldsLeft.add(this.textFieldRotationX);
        textFieldsLeft.add(this.textFieldRotationY);
        textFieldsLeft.add(this.textFieldRotationZ);
        textFieldsLeft.add(this.textFieldInterval);
        try {
            autoTextField("Left", this.textFieldsLeft, 0, 25, this.offsetX + 90, this.offsetY + 10, 80, 20);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // ---- 右侧文本框 ----
        this.textFieldsRight.clear();
        textFieldsRight.add(this.textFieldDisplayName);
        textFieldsRight.add(this.textFieldDisplayNameScale);
        textFieldsRight.add(this.textFieldScaled);
        textFieldsRight.add(this.textScale);
        textFieldsRight.add(this.textFieldCraftingTemplate); // 新增
        try {
            autoTextField("Right", this.textFieldsRight, 0, 25, this.offsetX + 275, this.offsetY + 10, 80, 20);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // 设置文本框内容
        this.textFieldTileEntityXYZ.setMaxStringLength(100);
        this.textFieldTileEntityXYZ.setFocused(true);
        this.textFieldTileEntityXYZ
            .setText(isInitialized ? contents.get(0) : this.tileEntityAdvanceDataMonotor.getXYZ(this.index));

        this.textFieldxOffset.setMaxStringLength(100);
        this.textFieldxOffset.setText(
            isInitialized ? contents.get(1) : String.valueOf(this.tileEntityAdvanceDataMonotor.getXOffset(this.index)));
        this.textFieldyOffset.setMaxStringLength(100);
        this.textFieldyOffset.setText(
            isInitialized ? contents.get(2) : String.valueOf(this.tileEntityAdvanceDataMonotor.getYOffset(this.index)));
        this.textFieldzOffset.setMaxStringLength(100);
        this.textFieldzOffset.setText(
            isInitialized ? contents.get(3) : String.valueOf(this.tileEntityAdvanceDataMonotor.getZOffset(this.index)));

        this.textFieldRotationX.setMaxStringLength(100);
        this.textFieldRotationX.setText(
            isInitialized ? contents.get(4)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationX(this.index)));
        this.textFieldRotationY.setMaxStringLength(100);
        this.textFieldRotationY.setText(
            isInitialized ? contents.get(5)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationY(this.index)));
        this.textFieldRotationZ.setMaxStringLength(100);
        this.textFieldRotationZ.setText(
            isInitialized ? contents.get(6)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationZ(this.index)));

        this.textFieldInterval.setMaxStringLength(100);
        this.textFieldInterval.setText(
            isInitialized ? contents.get(7)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getInterval(this.index)));

        this.textFieldDisplayName.setMaxStringLength(100);
        this.textFieldDisplayName
            .setText(isInitialized ? contents.get(8) : this.tileEntityAdvanceDataMonotor.getDisplayName(this.index));
        this.textFieldDisplayNameScale.setMaxStringLength(100);
        this.textFieldDisplayNameScale.setText(
            isInitialized ? contents.get(9)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameScale(this.index)));
        this.textFieldScaled.setMaxStringLength(100);
        this.textFieldScaled.setText(
            isInitialized ? contents.get(10) : String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));
        this.textScale.setMaxStringLength(100);
        this.textScale.setText(
            isInitialized ? contents.get(11)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getTextScale(this.index)));

        // 新增 craftingTemplate 文本框
        this.textFieldCraftingTemplate.setMaxStringLength(400); // 可适当增加长度
        this.textFieldCraftingTemplate.setText(
            isInitialized ? contents.get(12) : this.tileEntityAdvanceDataMonotor.getCraftingTemplate(this.index));

        focusedField = textFieldTileEntityXYZ;

        // ------------------- 按钮初始化 -------------------
        // 底部保存/取消/启用
        this.buttonList.add(
            new ADM_GuiButton(
                0,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.save")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                1,
                this.offsetX + 70,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.cancel")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                7,
                this.offsetX + 140,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.enable")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        // ---- 新配置按钮区 ----
        // 监测范围按钮 (ID 8)
        this.buttonList.add(
            new ADM_GuiButton(
                8,
                this.offsetX + buttonRowConfigXoffset1,
                this.offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                I18n.format(monitorNetworkWide ? "adm.button.monitorScope.network" : "adm.button.monitorScope.single"))
                    .setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        buttonRowConfigYoffset1 += buttonRowConfigYinterval1; // 下一行

        // 文字对齐按钮 (ID 9)
        String alignKey = switch (textAlign) {
            case 0 -> "adm.button.textAlign.left";
            case 1 -> "adm.button.textAlign.center";
            case 2 -> "adm.button.textAlign.right";
            default -> "adm.button.textAlign.left";
        };
        this.buttonList.add(
            new ADM_GuiButton(
                9,
                this.offsetX + buttonRowConfigXoffset1,
                this.offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                I18n.format(alignKey)).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 -= buttonRowConfigYinterval1; // 下一行
        // ---- 透明度调节按钮 (nameAlpha 和 textAlpha) ----
        // nameAlpha
        this.buttonList.add(
            new ADM_GuiButton(
                20,
                this.offsetX + buttonRowConfigXoffset1 + 100,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("+")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                21,
                this.offsetX + buttonRowConfigXoffset1 + 150,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("-")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1; // 下一行
        // textAlpha
        this.buttonList.add(
            new ADM_GuiButton(
                22,
                this.offsetX + buttonRowConfigXoffset1 + 100,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("+")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                23,
                this.offsetX + buttonRowConfigXoffset1 + 150,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("-")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        // 不需要再递增 Yoffset，后面没有其他配置按钮

        checkUsedButtonIds();

        // 提示信息映射
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
        fieldHints.put(textFieldCraftingTemplate, "adm.hint.craftingtemplate"); // 新增提示

        // 设置启用按钮文本
        getButtonByid(7).displayString = I18n.format(!this.isEnabled ? "adm.button.disable" : "adm.button.enable");
        ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
    }

    private GuiButton getButtonByid(int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return button;
            }
        }
        return null;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        isInitialized = false;
    }

    public void autoTextField(String row, List<ADM_GuiTextField> textFields, int intervalX, int intervalY, int startX,
        int startY, int width, int height) throws NoSuchFieldException, IllegalAccessException {
        int intervalXCurrent = 0;
        int intervalYCurrent = 0;
        for (int i = 0; i < textFields.size(); i++) {
            ADM_GuiTextField textField = new ADM_GuiTextField(
                this.fontRendererObj,
                startX + intervalXCurrent,
                startY + intervalYCurrent,
                width,
                height).setBackgroundTexture(textField_texture)
                    .setFocusedBackgroundTexture(textField_hover_texture);
            textFields.set(i, textField);
            intervalXCurrent += intervalX;
            intervalYCurrent += intervalY;

            if (row.equals("Left")) {
                switch (i) {
                    case 0 -> this.textFieldTileEntityXYZ = textField;
                    case 1 -> this.textFieldxOffset = textField;
                    case 2 -> this.textFieldyOffset = textField;
                    case 3 -> this.textFieldzOffset = textField;
                    case 4 -> this.textFieldRotationX = textField;
                    case 5 -> this.textFieldRotationY = textField;
                    case 6 -> this.textFieldRotationZ = textField;
                    case 7 -> this.textFieldInterval = textField;
                }
            } else if (row.equals("Right")) {
                switch (i) {
                    case 0 -> this.textFieldDisplayName = textField;
                    case 1 -> this.textFieldDisplayNameScale = textField;
                    case 2 -> this.textFieldScaled = textField;
                    case 3 -> this.textScale = textField;
                    case 4 -> this.textFieldCraftingTemplate = textField; // 新增分配
                }
            }
        }
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

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color,
        boolean textCenter) {
        int curX = 0;
        int curY = 0;
        for (String t : text) {
            int x = startX + curX;
            int y = startY + curY;
            if (textCenter) {
                int tw = this.fontRendererObj.getStringWidth(t);
                x = startX - tw / 2 + curX;
            }
            this.fontRendererObj.drawString(t, x, y, color);
            curX += intervalX;
            curY += intervalY;
        }
    }

    private boolean isMouseOver(ADM_GuiTextField textField, int mouseX, int mouseY) {
        return mouseX >= textField.xPosition && mouseX < textField.xPosition + textField.width
            && mouseY >= textField.yPosition
            && mouseY < textField.yPosition + textField.height;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound existingNbt = this.tileEntityAdvanceDataMonotor.getDataBound(this.index);
        NBTTagCompound nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);

        switch (button.id) {
            case 0 -> { // 保存
                nbt.setString("displayName", this.textFieldDisplayName.getText());
                nbt.setTag("dataValues", existingDataValues.copy());
                this.dataType = DataBound.DataType.crafting.name();
                nbt.setString("dataType", this.dataType);

                String XYZ = this.textFieldTileEntityXYZ.getText()
                    .replace("，", ",")
                    .replace(" ", "");
                if (!ContentsHelper.isValidPosFormat(XYZ)) {
                    this.errorTips = I18n.format("adm.error.xyz");
                    return;
                } else nbt.setString("XYZ", XYZ);

                if (!isValidDouble(this.textFieldxOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.xoffset");
                    return;
                } else nbt.setDouble("xOffset", Double.parseDouble(this.textFieldxOffset.getText()));
                if (!isValidDouble(this.textFieldyOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.yoffset");
                    return;
                } else nbt.setDouble("yOffset", Double.parseDouble(this.textFieldyOffset.getText()));
                if (!isValidDouble(this.textFieldzOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.zoffset");
                    return;
                } else nbt.setDouble("zOffset", Double.parseDouble(this.textFieldzOffset.getText()));
                if (!isValidDouble(this.textFieldRotationX.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationx");
                    return;
                } else nbt.setDouble("rotationX", Double.parseDouble(this.textFieldRotationX.getText()));
                if (!isValidDouble(this.textFieldRotationY.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationy");
                    return;
                } else nbt.setDouble("rotationY", Double.parseDouble(this.textFieldRotationY.getText()));
                if (!isValidDouble(this.textFieldRotationZ.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationz");
                    return;
                } else nbt.setDouble("rotationZ", Double.parseDouble(this.textFieldRotationZ.getText()));
                if (!isValidInteger(this.textFieldInterval.getText())) {
                    this.errorTips = I18n.format("adm.error.interval");
                    return;
                } else {
                    int interval = Integer.parseInt(this.textFieldInterval.getText());
                    if (interval <= 2) interval = 1;
                    nbt.setInteger("interval", interval);
                }
                if (!isValidDouble(this.textFieldDisplayNameScale.getText())) {
                    this.errorTips = I18n.format("adm.error.displayscale");
                    return;
                } else nbt.setDouble("displayNameScale", Double.parseDouble(this.textFieldDisplayNameScale.getText()));
                if (!isValidDouble(this.textFieldScaled.getText())) {
                    this.errorTips = I18n.format("adm.error.scale");
                    return;
                } else nbt.setDouble("scale", Double.parseDouble(this.textFieldScaled.getText()));
                if (!isValidDouble(this.textScale.getText())) {
                    this.errorTips = I18n.format("adm.error.textscale");
                    return;
                } else nbt.setDouble("textScale", Double.parseDouble(this.textScale.getText()));

                // 新增 craftingTemplate 保存（直接保存字符串，无额外验证）
                nbt.setString("craftingTemplate", this.textFieldCraftingTemplate.getText());

                // 新字段写入
                nbt.setBoolean("monitorNetworkWide", monitorNetworkWide);
                nbt.setInteger("textAlign", textAlign);

                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonotor.xCoord,
                        tileEntityAdvanceDataMonotor.yCoord,
                        tileEntityAdvanceDataMonotor.zCoord,
                        nbt));
                isInitialized = false;
                errorTips = "";
                this.mc.displayGuiScreen(
                    new GuiMainAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor)
                        .setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
            }
            case 1 -> { // 取消
                this.mc.displayGuiScreen(
                    new GuiMainAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor)
                        .setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
            }
            case 7 -> { // 启用/禁用
                isEnabled = !isEnabled;
                ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enable", !existingNbt.getBoolean("enable"));
                tileEntityAdvanceDataMonotor.setEnable(index, !existingNbt.getBoolean("enable"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enable") ? "adm.button.disable" : "adm.button.enable");
                saveAndSync(nbt);
            }
            case 8 -> { // 切换监测范围
                monitorNetworkWide = !monitorNetworkWide;
                nbt.setBoolean("monitorNetworkWide", monitorNetworkWide);
                tileEntityAdvanceDataMonotor.setMonitorNetworkWide(index, monitorNetworkWide);
                button.displayString = I18n
                    .format(monitorNetworkWide ? "adm.button.monitorScope.network" : "adm.button.monitorScope.single");
                saveAndSync(nbt);
            }
            case 9 -> { // 循环文字对齐
                textAlign = (textAlign + 1) % 3;
                nbt.setInteger("textAlign", textAlign);
                tileEntityAdvanceDataMonotor.setTextAlign(index, textAlign);
                String alignKey = switch (textAlign) {
                    case 0 -> "adm.button.textAlign.left";
                    case 1 -> "adm.button.textAlign.center";
                    case 2 -> "adm.button.textAlign.right";
                    default -> "adm.button.textAlign.left";
                };
                button.displayString = I18n.format(alignKey);
                saveAndSync(nbt);
            }
            // ---------- 透明度调节 ----------
            case 20 -> { // nameAlpha +
                double nameAlpha = this.tileEntityAdvanceDataMonotor.getNameAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(nameAlpha * 100) + step;
                if (alphaInt > 100) alphaInt = 0;
                updateNameAlpha(alphaInt / 100.0, nbt);
            }
            case 21 -> { // nameAlpha -
                double nameAlpha = this.tileEntityAdvanceDataMonotor.getNameAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(nameAlpha * 100) - step;
                if (alphaInt < 0) alphaInt = 100;
                updateNameAlpha(alphaInt / 100.0, nbt);
            }
            case 22 -> { // textAlpha +
                double textAlpha = this.tileEntityAdvanceDataMonotor.getTextAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(textAlpha * 100) + step;
                if (alphaInt > 100) alphaInt = 0;
                updateTextAlpha(alphaInt / 100.0, nbt);
            }
            case 23 -> { // textAlpha -
                double textAlpha = this.tileEntityAdvanceDataMonotor.getTextAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(textAlpha * 100) - step;
                if (alphaInt < 0) alphaInt = 100;
                updateTextAlpha(alphaInt / 100.0, nbt);
            }
        }
    }

    private void saveAndSync(NBTTagCompound nbt) {
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        for (ADM_GuiTextField textField : textFieldsLeft) textField.textboxKeyTyped(typedChar, keyCode);
        for (ADM_GuiTextField textField : textFieldsRight) textField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (ADM_GuiTextField textField : textFieldsLeft) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
            if (textField.isFocused()) this.focusedField = textField;
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
            if (textField.isFocused()) this.focusedField = textField;
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (ADM_GuiTextField textField : textFieldsLeft) textField.updateCursorCounter();
        for (ADM_GuiTextField textField : textFieldsRight) textField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // 左侧标签
        String[] label1 = { I18n.format("adm.label.xyz"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.zoffset"), I18n.format("adm.label.xrotation"),
            I18n.format("adm.label.yrotation"), I18n.format("adm.label.zrotation"), I18n.format("adm.label.interval") };
        autoText(label1, 0, 25, this.offsetX + 20, this.offsetY + 10, this.textColor, false);

        // 右侧标签（新增 craftingtemplate）
        String[] label2 = { I18n.format("adm.label.displayname"), I18n.format("adm.label.displaynamescale"),
            I18n.format("adm.label.scaled"), I18n.format("adm.label.textscale"),
            I18n.format("adm.label.craftingtemplate") };
        autoText(label2, 0, 25, this.offsetX + 170, this.offsetY + 10, this.textColor, false);

        // 透明度标签
        String[] label3 = { I18n.format("adm.label.namealpha"), I18n.format("adm.label.textalpha") };
        autoText(label3, 0, 25, this.offsetX + 490, this.offsetY + 10, this.textColor, true);

        // 透明度数值
        String[] label4 = { (int) (tileEntityAdvanceDataMonotor.getNameAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getTextAlpha(index) * 100) + "%" };
        autoText(label4, 0, 25, this.offsetX + 490, this.offsetY + 20, this.textColor, true);

        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.data_config_ae_crafting", this.index + 1),
            this.offsetX + 322,
            this.offsetY - 35,
            this.textColor);

        this.fontRendererObj.drawString(errorTips, this.offsetX + 230, this.offsetY + 380, 0xff0000);
        drawTextFieldBackground(textFieldsLeft);
        drawTextFieldBackground(textFieldsRight);

        hoveredTextField = null;
        for (ADM_GuiTextField tf : textFieldsLeft) {
            tf.drawTextBox();
            if (isMouseOver(tf, mouseX, mouseY)) hoveredTextField = tf;
        }
        for (ADM_GuiTextField tf : textFieldsRight) {
            tf.drawTextBox();
            if (isMouseOver(tf, mouseX, mouseY)) hoveredTextField = tf;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 字段提示
        if (focusedField != null && fieldHints.containsKey(focusedField)) {
            String hint = I18n.format(fieldHints.get(focusedField));
            List<String> wrappedHint = wrapText(hint, 35);
            int yPos = this.offsetY + 280;
            this.fontRendererObj
                .drawStringWithShadow(I18n.format("adm.property.tips"), this.offsetX + 10, yPos, 0x00FFFF);
            for (String line : wrappedHint) {
                this.fontRendererObj.drawStringWithShadow("§l" + line, this.offsetX + 10, yPos + 10, 0x00FFFF);
                yPos += 10;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateNameAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("nameAlpha", newValue);
        tileEntityAdvanceDataMonotor.setNameAlpha(index, newValue);
        saveAndSync(nbt);
    }

    private void updateTextAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("textAlpha", newValue);
        tileEntityAdvanceDataMonotor.setTextAlpha(index, newValue);
        saveAndSync(nbt);
    }
}
