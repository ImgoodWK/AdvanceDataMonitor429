package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidDouble;
import static com.imgood.textech.utils.ContentsHelper.isValidInteger;
import static com.imgood.textech.utils.ContentsHelper.wrapText;

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

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.ContentsHelper;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: AE Network Config (per-binding sub GUI)
 * - ZH: AE网络数据配置（绑定子界面）
 * Lang keys: adm.title.data_config_ae_network
 */
public class GuiSubAEAdvanceNetworkLink extends ADM_GuiScreen {

    private final TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonotor;
    @SuppressWarnings("unused")
    private EntityPlayer player;
    private World world;

    private int index;
    private List<ADM_GuiTextField> textFieldsLeft = new ArrayList<>();
    private List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();
    // 文本框与本地化键的映射
    private final Map<ADM_GuiTextField, String> fieldHints = new HashMap<>();

    private ADM_GuiTextField hoveredTextField;
    private ADM_GuiTextField focusedField = null;

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
    private static final ResourceLocation textField_selected_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_selected_ADM.png");
    private static final ResourceLocation textField_selected_texture_01 = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_selected_ADM_1.png");
    private static final ResourceLocation getGuiScreenHolographicDisplay_Sub_Background = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");
    private static final ResourceLocation guiScreenTypeBox = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/ADMSubGuiTypeBox.png");

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
    private int buttonRowConfigYoffset2 = 100;
    private int buttonRowConfigXoffset1 = 360;
    private int buttonRowConfigXoffset2 = 100;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 60;
    private int buttonRow2Width = 60;

    private String errorTips = "";

    private boolean isInitialized = false;
    private String dataType;
    private boolean isEnabled;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;
    private boolean isTypeItem;
    private boolean isUsed;
    private boolean isValue;
    private boolean isBytes;

    // 已使用的按钮ID集合
    private Set<Integer> usedButtonIds = new HashSet<>();

    public GuiSubAEAdvanceNetworkLink(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.index = index;
        this.setBackgroundTexture(getGuiScreenHolographicDisplay_Sub_Background);
        this.setSize(600, 450);
        this.setStretch(false);
    }

    /**
     * 检查并获取当前已使用的按钮ID集合
     * 这个方法会在每次打开GUI时调用，用于调试和避免ID冲突
     */
    private void checkUsedButtonIds() {
        usedButtonIds.clear();

        // 收集当前所有按钮的ID
        for (GuiButton button : this.buttonList) {
            usedButtonIds.add(button.id);
        }

        // 输出调试信息（可选）
        AdvanceDataMonitor.LOG.info("已使用的按钮ID: {}", usedButtonIds);

        // 检查是否有重复的ID
        Set<Integer> uniqueIds = new HashSet<>();
        List<Integer> duplicateIds = new ArrayList<>();

        for (GuiButton button : this.buttonList) {
            if (!uniqueIds.add(button.id)) {
                duplicateIds.add(button.id);
            }
        }

        if (!duplicateIds.isEmpty()) {
            System.err.println("警告：发现重复的按钮ID: " + duplicateIds);
            // 这里可以记录到日志文件或显示错误提示
            this.errorTips = I18n.format("adm.error.duplicateButtonIds") + duplicateIds;
        }
    }

    /**
     * 检查特定ID是否已被使用
     * 
     * @param buttonId 要检查的按钮ID
     * @return 如果ID已被使用返回true，否则返回false
     */
    private boolean isButtonIdUsed(int buttonId) {
        return usedButtonIds.contains(buttonId);
    }

    /**
     * 获取下一个可用的按钮ID
     * 
     * @param startId 起始ID
     * @return 可用的按钮ID
     */
    private int getNextAvailableButtonId(int startId) {
        int id = startId;
        while (isButtonIdUsed(id)) {
            id++;
        }
        return id;
    }

    private void saveCurrentState() {
        this.contents.clear();
        this.contents.add(textFieldTileEntityXYZ.getText());
        this.contents.add(textFieldxOffset.getText());
        this.contents.add(textFieldyOffset.getText());
        this.contents.add(textFieldzOffset.getText());
        this.contents.add(textFieldRotationX.getText());
        this.contents.add(textFieldRotationY.getText());
        this.contents.add(textFieldRotationZ.getText());
        this.contents.add(textFieldXRange.getText());
        this.contents.add(textFieldYRange.getText());
        this.contents.add(textFieldDataLimit.getText());
        this.contents.add(textFieldInterval.getText());
        this.contents.add(textFieldYMin.getText());
        this.contents.add(textFieldYMax.getText());

        this.contents.add(textFieldDisplayName.getText());
        this.contents.add(textFieldDisplayNameScale.getText());
        this.contents.add(textFieldLineWidth.getText());
        this.contents.add(textFieldScaled.getText());
        this.contents.add(textFieldAxisFontScaled.getText());
        this.contents.add(textFieldGridLineWidth.getText());
        this.contents.add(textFieldAxisLineWidth.getText());
        this.contents.add(textFieldTickLengthFactor.getText());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        initDataType();
        focusedField = textFieldTileEntityXYZ;
        isEnabled = tileEntityAdvanceDataMonotor.getEnable(index);
        isEnabledAxis = tileEntityAdvanceDataMonotor.getEnableAxis(index);
        isEnabledData = tileEntityAdvanceDataMonotor.getEnableData(index);
        isEnabledAxisFont = tileEntityAdvanceDataMonotor.getEnableAxisFont(index);
        isValue = tileEntityAdvanceDataMonotor.getIsValue(index);

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
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getXRange(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYRange(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDataLimit(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getInterval(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYMin(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYMax(this.index)));

            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayName(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getLineWidth(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisFontScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getGridLineWidth(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisLineWidth(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getTickLengthFactor(this.index)));

            isInitialized = true;
        }

        this.offsetX = (this.width / 2) + startOffsetX;
        this.offsetY = (this.height / 2) + startOffsetY;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 35);
        this.textFieldsLeft.clear();
        textFieldsLeft.add(this.textFieldTileEntityXYZ);
        textFieldsLeft.add(this.textFieldxOffset);
        textFieldsLeft.add(this.textFieldyOffset);
        textFieldsLeft.add(this.textFieldzOffset);
        textFieldsLeft.add(this.textFieldRotationX);
        textFieldsLeft.add(this.textFieldRotationY);
        textFieldsLeft.add(this.textFieldRotationZ);
        textFieldsLeft.add(this.textFieldXRange);
        textFieldsLeft.add(this.textFieldYRange);
        textFieldsLeft.add(this.textFieldDataLimit);
        textFieldsLeft.add(this.textFieldInterval);
        textFieldsLeft.add(this.textFieldYMin);
        textFieldsLeft.add(this.textFieldYMax);
        try {
            autoTextField("Left", this.textFieldsLeft, 0, 25, this.offsetX + 90, this.offsetY + 10, 80, 20);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.textFieldsRight.clear();
        textFieldsRight.add(this.textFieldDisplayName);
        textFieldsRight.add(this.textFieldDisplayNameScale);
        textFieldsRight.add(this.textFieldLineWidth);
        textFieldsRight.add(this.textFieldScaled);
        textFieldsRight.add(this.textFieldAxisFontScaled);
        textFieldsRight.add(this.textFieldGridLineWidth);
        textFieldsRight.add(this.textFieldAxisLineWidth);
        textFieldsRight.add(this.textFieldTickLengthFactor);
        try {
            autoTextField("Right", this.textFieldsRight, 0, 25, this.offsetX + 275, this.offsetY + 10, 80, 20);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

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

        this.textFieldXRange.setMaxStringLength(100);
        this.textFieldXRange.setText(
            isInitialized ? contents.get(7) : String.valueOf(this.tileEntityAdvanceDataMonotor.getXRange(this.index)));

        this.textFieldYRange.setMaxStringLength(100);
        this.textFieldYRange.setText(
            isInitialized ? contents.get(8) : String.valueOf(this.tileEntityAdvanceDataMonotor.getYRange(this.index)));

        this.textFieldDataLimit.setMaxStringLength(100);
        this.textFieldDataLimit.setText(
            isInitialized ? contents.get(9)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getDataLimit(this.index)));

        this.textFieldInterval.setMaxStringLength(100);
        this.textFieldInterval.setText(
            isInitialized ? contents.get(10)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getInterval(this.index)));

        this.textFieldYMin.setMaxStringLength(100);
        this.textFieldYMin.setText(
            isInitialized ? contents.get(11) : String.valueOf(this.tileEntityAdvanceDataMonotor.getYMin(this.index)));

        this.textFieldYMax.setMaxStringLength(100);
        this.textFieldYMax.setText(
            isInitialized ? contents.get(12) : String.valueOf(this.tileEntityAdvanceDataMonotor.getYMax(this.index)));

        // -------------------------------------------------

        this.textFieldDisplayName.setMaxStringLength(100);
        this.textFieldDisplayName
            .setText(isInitialized ? contents.get(13) : this.tileEntityAdvanceDataMonotor.getDisplayName(this.index));

        this.textFieldDisplayNameScale.setMaxStringLength(100);
        this.textFieldDisplayNameScale.setText(
            isInitialized ? contents.get(14)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameScale(this.index)));

        this.textFieldLineWidth.setMaxStringLength(100);
        this.textFieldLineWidth.setText(
            isInitialized ? contents.get(15)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getLineWidth(this.index)));

        this.textFieldScaled.setMaxStringLength(100);
        this.textFieldScaled.setText(
            isInitialized ? contents.get(16) : String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));

        this.textFieldAxisFontScaled.setMaxStringLength(100);
        this.textFieldAxisFontScaled.setText(
            isInitialized ? contents.get(17)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisFontScale(this.index)));

        this.textFieldGridLineWidth.setMaxStringLength(100);
        this.textFieldGridLineWidth.setText(
            isInitialized ? contents.get(18)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getGridLineWidth(this.index)));

        this.textFieldAxisLineWidth.setMaxStringLength(100);
        this.textFieldAxisLineWidth.setText(
            isInitialized ? contents.get(19)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisLineWidth(this.index)));

        this.textFieldTickLengthFactor.setMaxStringLength(100);
        this.textFieldTickLengthFactor.setText(
            isInitialized ? contents.get(20)
                : String.valueOf(this.tileEntityAdvanceDataMonotor.getTickLengthFactor(this.index)));

        // 保存/取消按钮
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

        this.buttonList.add(
            new ADM_GuiButton(
                2,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                I18n.format("adm.datatype.line")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                3,
                this.offsetX + 70,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                I18n.format("adm.datatype.bar")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                4,
                this.offsetX + 140,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                I18n.format("adm.datatype.bar3d")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                5,
                this.offsetX + 210,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                I18n.format("adm.datatype.waterfall")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        this.buttonList.add(
            new ADM_GuiButton(
                6,
                this.offsetX + 280,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                I18n.format("adm.datatype.difference")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                8,
                this.offsetX + buttonRowConfigXoffset1,
                this.offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                I18n.format("adm.button.enableAxis")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        // 名称透明度按钮
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

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        // 轴线透明度按钮
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

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        // 轴字体透明度按钮
        this.buttonList.add(
            new ADM_GuiButton(
                24,
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
                25,
                this.offsetX + buttonRowConfigXoffset1 + 150,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("-")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        // 数据线透明度按钮
        this.buttonList.add(
            new ADM_GuiButton(
                26,
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
                27,
                this.offsetX + buttonRowConfigXoffset1 + 150,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("-")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        // 网格线透明度按钮（保留）
        this.buttonList.add(
            new ADM_GuiButton(
                16,
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
                17,
                this.offsetX + buttonRowConfigXoffset1 + 150,
                this.offsetY + buttonRowConfigYoffset1,
                10,
                10,
                I18n.format("-")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 = 20; // 重置偏移量

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1; // 跳过5行

        this.buttonList.add(
            new ADM_GuiButton(
                9,
                this.offsetX + buttonRowConfigXoffset1,
                this.offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                I18n.format("adm.button.enableData")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        buttonRowConfigYoffset1 += buttonRowConfigYinterval1;

        this.buttonList.add(
            new ADM_GuiButton(
                10,
                this.offsetX + buttonRowConfigXoffset1,
                this.offsetY + buttonRowConfigYoffset1,
                buttonRow2Width,
                20,
                I18n.format("adm.button.enableAxisFont")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow2RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                11,
                this.offsetX + 215,
                this.offsetY + 240,
                40,
                20,
                I18n.format("adm.button.dataType.Item")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                12,
                this.offsetX + 275,
                this.offsetY + 240,
                40,
                20,
                I18n.format("adm.button.dataType.Used")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                13,
                this.offsetX + 402,
                this.offsetY + 240,
                50,
                20,
                I18n.format("adm.button.dataType.Value")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(
                14,
                this.offsetX + 335,
                this.offsetY + 240,
                40,
                20,
                I18n.format("adm.button.dataType.Bytes")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(false)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(15, this.offsetX + 535, this.offsetY + 350, 10, 10, I18n.format("×"))
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(
            new ADM_GuiButton(18, this.offsetX + 535, this.offsetY + 365, 10, 10, I18n.format("+"))
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        // 在添加完所有按钮后，检查已使用的按钮ID
        checkUsedButtonIds();

        setTileEntityDatatype(this.tileEntityAdvanceDataMonotor.getDataType(this.index));

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

        getButtonByid(7).displayString = I18n.format(!this.isEnabled ? "adm.button.disable" : "adm.button.enable");
        getButtonByid(8).displayString = I18n
            .format(!this.isEnabledAxis ? "adm.button.disableAxis" : "adm.button.enableAxis");
        getButtonByid(9).displayString = I18n
            .format(!this.isEnabledData ? "adm.button.disableData" : "adm.button.enableData");
        getButtonByid(10).displayString = I18n
            .format(!this.isEnabledAxisFont ? "adm.button.disableAxisFont" : "adm.button.enableAxisFont");
        getButtonByid(11).displayString = I18n
            .format(this.isTypeItem ? "adm.button.dataType.Item" : "adm.button.dataType.Fluid");
        getButtonByid(12).displayString = I18n
            .format(this.isUsed ? "adm.button.dataType.Used" : "adm.button.dataType.Total");
        getButtonByid(13).displayString = I18n
            .format(this.isValue ? "adm.button.dataType.Percent" : "adm.button.dataType.Value");
        getButtonByid(14).displayString = I18n
            .format(this.isBytes ? "adm.button.dataType.Bytes" : "adm.button.dataType.Type");

        ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(8)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(9)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
        ((ADM_GuiButton) getButtonByid(10)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
    }

    private void setTileEntityDatatype(DataBound.DataType dataType) {

        ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(false);
        ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(false);
        switch (dataType) {
            case line:
                // getButtonByid(2).enabled = false;
                ((ADM_GuiButton) getButtonByid(2)).setUseRGBEffect(true);
                break;
            case bar:
                // getButtonByid(3).enabled = false;
                ((ADM_GuiButton) getButtonByid(3)).setUseRGBEffect(true);
                break;
            case bar3d:
                // getButtonByid(4).enabled = false;
                ((ADM_GuiButton) getButtonByid(4)).setUseRGBEffect(true);
                break;
            case waterfall:
                // getButtonByid(5).enabled = false;
                ((ADM_GuiButton) getButtonByid(5)).setUseRGBEffect(true);
                break;
            case diffrence:
                // getButtonByid(6).enabled = false;
                ((ADM_GuiButton) getButtonByid(6)).setUseRGBEffect(true);
        }
        this.tileEntityAdvanceDataMonotor.setDataType(index, dataType);
    }

    private boolean textContains(GuiButton guiButton, String contains) {
        return guiButton.displayString.contains(contains);
    }

    private void setButtonDisplayString(int id, String displayString) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                button.displayString = displayString;
                // 这里不return是考虑到button id不是唯一的
            }
        }
    }

    private GuiButton getButtonByid(int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return button;
                // button id不是唯一的，但会返回第一个检查到的
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
                    case 0:
                        this.textFieldTileEntityXYZ = textField; // 第1位
                        break;
                    case 1:
                        this.textFieldxOffset = textField;
                        break;
                    case 2:
                        this.textFieldyOffset = textField;
                        break;
                    case 3:
                        this.textFieldzOffset = textField;
                        break;
                    case 4:
                        this.textFieldRotationX = textField;
                        break;
                    case 5:
                        this.textFieldRotationY = textField;
                        break;
                    case 6:
                        this.textFieldRotationZ = textField;
                        break;
                    case 7:
                        this.textFieldXRange = textField;
                        break;
                    case 8:
                        this.textFieldYRange = textField;
                        break;
                    case 9:
                        this.textFieldDataLimit = textField;
                        break;
                    case 10:
                        this.textFieldInterval = textField;
                        break;
                    case 11:
                        this.textFieldYMin = textField;
                        break;
                    case 12:
                        this.textFieldYMax = textField;
                        break;
                }
            } else if (row.equals("Right")) {
                switch (i) {
                    case 0:
                        this.textFieldDisplayName = textField;
                        break;
                    case 1:
                        this.textFieldDisplayNameScale = textField;
                        break;
                    case 2:
                        this.textFieldLineWidth = textField;
                        break;
                    case 3:
                        this.textFieldScaled = textField;
                        break;
                    case 4:
                        this.textFieldAxisFontScaled = textField;
                        break;
                    case 5:
                        this.textFieldGridLineWidth = textField;
                        break;
                    case 6:
                        this.textFieldAxisLineWidth = textField;
                        break;
                    case 7:
                        this.textFieldTickLengthFactor = textField;
                        break;
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

    public void drawTextFieldBackground(List<ADM_GuiTextField> textField) {
        int xCoord;
        int yCoord;
        int textWidth;
        int textHeight;
        for (ADM_GuiTextField textFieldCurrunt : textField) {
            xCoord = textFieldCurrunt.xPosition;
            yCoord = textFieldCurrunt.yPosition + 2;
            textWidth = textFieldCurrunt.width + 20;
            textHeight = textFieldCurrunt.height;
            if (textFieldCurrunt.isFocused()) {
                drawTextFieldFocusBackground(textFieldCurrunt, xCoord, yCoord, 100, 20);
            } else {
                drawTextFieldBackground(textFieldCurrunt, xCoord, yCoord, 100, 20);
            }
        }
    }

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color,
        boolean textCenter) {
        int intervalXCurrent = 0;
        int intervalYCurrent = 0;

        for (String t : text) {
            int xPosition = startX + intervalXCurrent;
            int yPosition = startY + intervalYCurrent;

            // 居中处理逻辑
            if (textCenter) {
                int textWidth = this.fontRendererObj.getStringWidth(t);
                xPosition = startX - textWidth / 2 + intervalXCurrent; // 基于起始位置居中
            }

            this.fontRendererObj.drawString(t, xPosition, yPosition, color);

            intervalXCurrent += intervalX;
            intervalYCurrent += intervalY;
        }
    }

    private boolean isMouseOver(ADM_GuiTextField textField, int mouseX, int mouseY) {
        return mouseX >= textField.xPosition && mouseX < textField.xPosition + textField.width
            && mouseY >= textField.yPosition
            && mouseY < textField.yPosition + textField.height;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        AdvanceDataMonitor.LOG.info("Button ID: {}", button.id);
        NBTTagCompound nbt;
        // 获取现有数据绑定
        NBTTagCompound existingNbt = this.tileEntityAdvanceDataMonotor.getDataBound(this.index);
        // 创建副本用于修改
        nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);
        switch (button.id) {
            case 0 -> {

                // 保留原有的dataValues数据
                nbt.setString("displayName", this.textFieldDisplayName.getText());
                nbt.setTag("dataValues", existingDataValues.copy());
                if (this.dataType == null) {
                    nbt.setString("dataType", "line");
                } else {
                    nbt.setString("dataType", this.dataType);
                }

                // Left 组字段（textFieldName 之前的字段）
                String XYZ = this.textFieldTileEntityXYZ.getText()
                    .replace("，", ",")
                    .replace(" ", "");
                if (!ContentsHelper.isValidPosFormat(XYZ)) {
                    this.errorTips = I18n.format("adm.error.xyz");
                    return;
                } else {
                    nbt.setString("XYZ", XYZ);
                }

                if (!isValidDouble(this.textFieldxOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.xoffset");
                    return;
                } else {
                    nbt.setDouble("xOffset", Double.parseDouble(this.textFieldxOffset.getText()));
                }

                if (!isValidDouble(this.textFieldyOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.yoffset");
                    return;
                } else {
                    nbt.setDouble("yOffset", Double.parseDouble(this.textFieldyOffset.getText()));
                }

                if (!isValidDouble(this.textFieldzOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.zoffset");
                    return;
                } else {
                    nbt.setDouble("zOffset", Double.parseDouble(this.textFieldzOffset.getText()));
                }

                if (!isValidDouble(this.textFieldRotationX.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationx");
                    return;
                } else {
                    nbt.setDouble("rotationX", Double.parseDouble(this.textFieldRotationX.getText()));
                }

                if (!isValidDouble(this.textFieldRotationY.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationy");
                    return;
                } else {
                    nbt.setDouble("rotationY", Double.parseDouble(this.textFieldRotationY.getText()));
                }

                if (!isValidDouble(this.textFieldRotationZ.getText())) {
                    this.errorTips = I18n.format("adm.error.rotationz");
                    return;
                } else {
                    nbt.setDouble("rotationZ", Double.parseDouble(this.textFieldRotationZ.getText()));
                }

                if (!isValidDouble(this.textFieldXRange.getText())) {
                    this.errorTips = I18n.format("adm.error.xrange");
                    return;
                } else {
                    nbt.setDouble("xRange", Double.parseDouble(this.textFieldXRange.getText()));
                }

                // 补充缺失的 YRange 验证
                if (!isValidDouble(this.textFieldYRange.getText())) {
                    this.errorTips = I18n.format("adm.error.yrange");
                    return;
                } else {
                    nbt.setDouble("yRange", Double.parseDouble(this.textFieldYRange.getText()));
                }

                if (!isValidInteger(this.textFieldDataLimit.getText())) {
                    this.errorTips = I18n.format("adm.error.datalimit");
                    return;
                } else {
                    int dataLimit = Integer.parseInt(this.textFieldDataLimit.getText());
                    if (dataLimit > 9999 || dataLimit < 2) {
                        this.errorTips = I18n.format("adm.error.datalimit");
                        return;
                    }
                    nbt.setInteger("dataLimit", Integer.parseInt(this.textFieldDataLimit.getText()));
                }

                if (!isValidInteger(this.textFieldInterval.getText())) {
                    this.errorTips = I18n.format("adm.error.interval");
                    return;
                } else {
                    int interval = Integer.parseInt(this.textFieldInterval.getText());
                    interval = interval <= 2 ? 1 : interval; // 优化条件判断
                    nbt.setInteger("interval", interval);
                }

                if (!isValidDouble(this.textFieldYMin.getText())) {
                    this.errorTips = I18n.format("adm.error.ymin");
                    return;
                } else {
                    nbt.setDouble("yMin", Double.parseDouble(this.textFieldYMin.getText()));
                }

                if (!isValidDouble(this.textFieldYMax.getText())) {
                    this.errorTips = I18n.format("adm.error.ymax");
                    return;
                } else {
                    nbt.setDouble("yMax", Double.parseDouble(this.textFieldYMax.getText()));
                }

                // 补充缺失的 DisplayNameScale
                if (!isValidDouble(this.textFieldDisplayNameScale.getText())) {
                    this.errorTips = I18n.format("adm.error.displayscale");
                    return;
                } else {
                    nbt.setDouble("displayNameScale", Double.parseDouble(this.textFieldDisplayNameScale.getText()));
                }

                if (!isValidDouble(this.textFieldLineWidth.getText())) {
                    this.errorTips = I18n.format("adm.error.linewidth");
                    return;
                } else {
                    nbt.setDouble("lineWidth", Double.parseDouble(this.textFieldLineWidth.getText()));
                }

                if (!isValidDouble(this.textFieldScaled.getText())) {
                    this.errorTips = I18n.format("adm.error.scale");
                    return;
                } else {
                    nbt.setDouble("scale", Double.parseDouble(this.textFieldScaled.getText()));
                }

                if (!isValidDouble(this.textFieldAxisFontScaled.getText())) {
                    this.errorTips = I18n.format("adm.error.axisfontscale");
                    return;
                } else {
                    nbt.setDouble("axisFontScale", Double.parseDouble(this.textFieldAxisFontScaled.getText()));
                }

                if (!isValidDouble(this.textFieldGridLineWidth.getText())) {
                    this.errorTips = I18n.format("adm.error.gridlinewidth");
                    return;
                } else {
                    nbt.setDouble("gridLineWidth", Double.parseDouble(this.textFieldGridLineWidth.getText()));
                }

                if (!isValidDouble(this.textFieldAxisLineWidth.getText())) {
                    this.errorTips = I18n.format("adm.error.axislinewidth");
                    return;
                } else {
                    nbt.setDouble("axisLineWidth", Double.parseDouble(this.textFieldAxisLineWidth.getText()));
                }

                if (!isValidDouble(this.textFieldTickLengthFactor.getText())) {
                    this.errorTips = I18n.format("adm.error.ticklengthfactor");
                    return;
                } else {
                    nbt.setDouble("tickLengthFactor", Double.parseDouble(this.textFieldTickLengthFactor.getText()));
                }

                // 保存按钮11、12、13、14的属性到NBT
                updateDataType(nbt);

                // 后续操作
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
            case 1 -> {
                // this.mc.displayGuiScreen(null);
                this.mc.displayGuiScreen(
                    new GuiMainAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor)
                        .setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
            }
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
                // 获取现有NBT
                isEnabled = !isEnabled;
                ((ADM_GuiButton) getButtonByid(7)).setTextColor(isEnabled ? 0x00FFFF : 0xFF0000);

                // 更新NBT
                nbt.setBoolean("enable", !existingNbt.getBoolean("enable"));
                // 更新TileEntity状态
                tileEntityAdvanceDataMonotor.setEnable(index, !existingNbt.getBoolean("enable"));

                // 更新按钮显示文本
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enable") ? "adm.button.disable" : "adm.button.enable");

                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                // 同步到服务器
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonotor.xCoord,
                        tileEntityAdvanceDataMonotor.yCoord,
                        tileEntityAdvanceDataMonotor.zCoord,
                        nbt));

            }
            case 8 -> {
                isEnabledAxis = !isEnabledAxis;
                ((ADM_GuiButton) getButtonByid(8)).setTextColor(isEnabledAxis ? 0x00FFFF : 0xFF0000);

                nbt.setBoolean("enableAxis", !existingNbt.getBoolean("enableAxis"));
                tileEntityAdvanceDataMonotor.setEnableAxis(index, !existingNbt.getBoolean("enableAxis"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableAxis") ? "adm.button.disableAxis" : "adm.button.enableAxis");
                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                // 同步到服务器
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonotor.xCoord,
                        tileEntityAdvanceDataMonotor.yCoord,
                        tileEntityAdvanceDataMonotor.zCoord,
                        nbt));
            }
            case 9 -> {
                isEnabledData = !isEnabledData;
                ((ADM_GuiButton) getButtonByid(9)).setTextColor(isEnabledData ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableData", !existingNbt.getBoolean("enableData"));
                tileEntityAdvanceDataMonotor.setEnableData(index, !existingNbt.getBoolean("enableData"));
                button.displayString = I18n
                    .format(!existingNbt.getBoolean("enableData") ? "adm.button.disableData" : "adm.button.enableData");
                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonotor.xCoord,
                        tileEntityAdvanceDataMonotor.yCoord,
                        tileEntityAdvanceDataMonotor.zCoord,
                        nbt));
            }
            case 10 -> {
                isEnabledAxisFont = !isEnabledAxisFont;
                ((ADM_GuiButton) getButtonByid(10)).setTextColor(isEnabledAxisFont ? 0x00FFFF : 0xFF0000);
                nbt.setBoolean("enableAxisFont", !existingNbt.getBoolean("enableAxisFont"));
                tileEntityAdvanceDataMonotor.setEnableAxisFont(index, !existingNbt.getBoolean("enableAxisFont"));
                button.displayString = I18n.format(
                    !existingNbt.getBoolean("enableAxisFont") ? "adm.button.disableAxisFont"
                        : "adm.button.enableAxisFont");
                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonotor.xCoord,
                        tileEntityAdvanceDataMonotor.yCoord,
                        tileEntityAdvanceDataMonotor.zCoord,
                        nbt));
            }
            case 11 -> {
                // 切换物品/流体类型
                isTypeItem = !isTypeItem;
                getButtonByid(11).displayString = I18n
                    .format(isTypeItem ? "adm.button.dataType.Item" : "adm.button.dataType.Fluid");
            }
            case 12 -> {
                // 切换已使用/总量模式
                isUsed = !isUsed;
                getButtonByid(12).displayString = I18n
                    .format(isUsed ? "adm.button.dataType.Used" : "adm.button.dataType.Total");
            }
            case 13 -> {
                // 切换数值/百分比显示
                isValue = !isValue;
                getButtonByid(13).displayString = I18n
                    .format(isValue ? "adm.button.dataType.Percent" : "adm.button.dataType.Value");
            }
            case 14 -> {
                // 切换字节/类型单位
                isBytes = !isBytes;
                getButtonByid(14).displayString = I18n
                    .format(isBytes ? "adm.button.dataType.Bytes" : "adm.button.dataType.Type");
            }
            case 15 -> { // 清除数据按钮
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                    clearDataValues();
                    errorTips = I18n.format("adm.message.data_cleared");
                } else {
                    errorTips = I18n.format("adm.tooltip.clearData.shift");
                }

            }
            case 18 -> {
                NBTTagCompound bound = this.tileEntityAdvanceDataMonotor.getDataBound(this.index);
                this.tileEntityAdvanceDataMonotor.processDataImmediately(this.index, bound);
                errorTips = I18n.format("adm.message.data_collected");
            }
            case 16 -> {
                double gridLineAlpha = this.tileEntityAdvanceDataMonotor.getGridLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10; // 步长转换为整数（1代表0.01）
                int alphaInt = (int) Math.round(gridLineAlpha * 100); // 转换为整数百分比

                alphaInt += step;
                if (alphaInt > 100) alphaInt = 0; // 超过100%重置为0

                double newValue = alphaInt / 100.0; // 转回浮点数
                updateGridLineAlpha(newValue, nbt);
            }
            case 17 -> {
                double gridLineAlpha = this.tileEntityAdvanceDataMonotor.getGridLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(gridLineAlpha * 100);

                alphaInt -= step;
                if (alphaInt < 0) alphaInt = 100; // 低于0重置为100%

                double newValue = alphaInt / 100.0;
                updateGridLineAlpha(newValue, nbt);
            }
            // 名称透明度按钮
            case 20 -> {
                double nameAlpha = this.tileEntityAdvanceDataMonotor.getNameAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(nameAlpha * 100);

                alphaInt += step;
                if (alphaInt > 100) alphaInt = 0;

                double newValue = alphaInt / 100.0;
                updateNameAlpha(newValue, nbt);
            }
            case 21 -> {
                double nameAlpha = this.tileEntityAdvanceDataMonotor.getNameAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(nameAlpha * 100);

                alphaInt -= step;
                if (alphaInt < 0) alphaInt = 100;

                double newValue = alphaInt / 100.0;
                updateNameAlpha(newValue, nbt);
            }
            // 轴线透明度按钮
            case 22 -> {
                double axisAlpha = this.tileEntityAdvanceDataMonotor.getAxisLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(axisAlpha * 100);

                alphaInt += step;
                if (alphaInt > 100) alphaInt = 0;

                double newValue = alphaInt / 100.0;
                updateAxisAlpha(newValue, nbt);
            }
            case 23 -> {
                double axisAlpha = this.tileEntityAdvanceDataMonotor.getAxisLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(axisAlpha * 100);

                alphaInt -= step;
                if (alphaInt < 0) alphaInt = 100;

                double newValue = alphaInt / 100.0;
                updateAxisAlpha(newValue, nbt);
            }
            // 轴字体透明度按钮
            case 24 -> {
                double axisFontAlpha = this.tileEntityAdvanceDataMonotor.getAxisFontAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(axisFontAlpha * 100);

                alphaInt += step;
                if (alphaInt > 100) alphaInt = 0;

                double newValue = alphaInt / 100.0;
                updateAxisFontAlpha(newValue, nbt);
            }
            case 25 -> {
                double axisFontAlpha = this.tileEntityAdvanceDataMonotor.getAxisFontAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(axisFontAlpha * 100);

                alphaInt -= step;
                if (alphaInt < 0) alphaInt = 100;

                double newValue = alphaInt / 100.0;
                updateAxisFontAlpha(newValue, nbt);
            }
            // 数据线透明度按钮
            case 26 -> {
                double dataLineAlpha = this.tileEntityAdvanceDataMonotor.getLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(dataLineAlpha * 100);

                alphaInt += step;
                if (alphaInt > 100) alphaInt = 0;

                double newValue = alphaInt / 100.0;
                updateDataLineAlpha(newValue, nbt);
            }
            case 27 -> {
                double dataLineAlpha = this.tileEntityAdvanceDataMonotor.getLineAlpha(index);
                int step = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 10;
                int alphaInt = (int) Math.round(dataLineAlpha * 100);

                alphaInt -= step;
                if (alphaInt < 0) alphaInt = 100;

                double newValue = alphaInt / 100.0;
                updateDataLineAlpha(newValue, nbt);
            }
            default -> {
                // 处理其他按钮的行为
            }
        }

    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        for (ADM_GuiTextField textField : textFieldsLeft) {
            textField.textboxKeyTyped(typedChar, keyCode);
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (ADM_GuiTextField textField : textFieldsLeft) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
            if (textField.isFocused()) {
                this.focusedField = textField;
            }
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
            if (textField.isFocused()) {
                this.focusedField = textField;
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (ADM_GuiTextField textField : textFieldsLeft) {
            textField.updateCursorCounter();
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawImage(guiScreenTypeBox, this.offsetX + 165, this.offsetY + 225, 335, 110);
        // 绘制Shift键提示
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            GuiButton btnClear = getButtonByid(15);

            if (btnClear != null) {
                // 获取需要渲染的文本
                String text = I18n.format("adm.tooltip.clear");

                // 计算文本像素宽度
                int textWidth = this.fontRendererObj.getStringWidth(text);

                // 右对齐计算：按钮左侧位置 - 固定间距 - 文本宽度
                int xPos = btnClear.xPosition - 3 - textWidth;

                // 渲染文本（保持垂直位置不变）
                this.fontRendererObj.drawStringWithShadow(
                    text,
                    xPos, // 动态计算的X位置
                    btnClear.yPosition + 0, // 保持原始Y位置
                    0XFF0000);
            }

        }

        GuiButton btnCollect = getButtonByid(18);
        if (btnCollect != null && mouseX >= btnCollect.xPosition
            && mouseX < btnCollect.xPosition + btnCollect.width
            && mouseY >= btnCollect.yPosition
            && mouseY < btnCollect.yPosition + btnCollect.height) {
            drawHoveringText(I18n.format("adm.tooltip.collect"), mouseX, mouseY);
        }

        // 可选：在GUI上显示已使用的按钮ID数量（调试用）
        if (Config.debugGuiNetworkLink) {
            this.fontRendererObj.drawStringWithShadow(
                "按钮ID数: " + usedButtonIds.size(),
                this.offsetX + 10,
                this.offsetY + 400,
                0x00FF00);
        }

        String[] label1 = { I18n.format("adm.label.xyz"), I18n.format("adm.label.xoffset"),
            I18n.format("adm.label.yoffset"), I18n.format("adm.label.zoffset"), I18n.format("adm.label.xrotation"),
            I18n.format("adm.label.yrotation"), I18n.format("adm.label.zrotation"), I18n.format("adm.label.xrange"),
            I18n.format("adm.label.yrange"), I18n.format("adm.label.datalimit"), I18n.format("adm.label.interval"),
            "yMin", "yMax" };
        autoText(label1, 0, 25, this.offsetX + 20, this.offsetY + 10, this.textColor, false);
        String[] label2 = { I18n.format("adm.label.displayname"), I18n.format("adm.label.displaynamescale"),
            I18n.format("adm.label.linewidth"), I18n.format("adm.label.scaled"),
            I18n.format("adm.label.axisfontscaled"), I18n.format("adm.label.gridlinewidth"), // 新增标签
            I18n.format("adm.label.axislinewidth"), // 新增标签
            I18n.format("adm.label.ticklengthfactor") }; // 新增标签
        autoText(label2, 0, 25, this.offsetX + 170, this.offsetY + 10, this.textColor, false);
        // 修改标签数组，包含所有透明度属性
        String[] label3 = { I18n.format("adm.label.namealpha"), I18n.format("adm.label.axisalpha"),
            I18n.format("adm.label.axisfontalpha"), I18n.format("adm.label.datalinealpha"),
            I18n.format("adm.label.gridlinealpha") };
        autoText(label3, 0, 25, this.offsetX + 490, this.offsetY + 10, this.textColor, true);
        // 修改数值数组，包含所有透明度值
        String[] label4 = { (int) (tileEntityAdvanceDataMonotor.getNameAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getAxisLineAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getAxisFontAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getLineAlpha(index) * 100) + "%",
            (int) (tileEntityAdvanceDataMonotor.getGridLineAlpha(index) * 100) + "%" };
        autoText(label4, 0, 25, this.offsetX + 490, this.offsetY + 20, this.textColor, true);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.data_config_ae_network", this.index + 1), // 本地化标题
            this.offsetX + 322,
            this.offsetY - 35,
            this.textColor);

        this.fontRendererObj.drawString(errorTips, this.offsetX + 230, this.offsetY + 380, 0xff0000);
        drawTextFieldBackground(textFieldsLeft);
        drawTextFieldBackground(textFieldsRight);

        // this.drawCenteredString(this.fontRendererObj, this.currentHashCode, this.offsetX+192, this.offsetY+100,
        // this.textColor);

        hoveredTextField = null;
        for (ADM_GuiTextField textField : textFieldsLeft) {
            textField.drawTextBox();
            if (isMouseOver(textField, mouseX, mouseY)) {
                hoveredTextField = textField;
            }
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.drawTextBox();
            if (isMouseOver(textField, mouseX, mouseY)) {
                hoveredTextField = textField;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 绘制 tooltip
        if (focusedField != null && fieldHints.containsKey(focusedField)) {
            String hint = I18n.format(fieldHints.get(focusedField));
            List<String> wrappedHint = wrapText(hint, 35); // 40字符宽度自动换行

            // 在错误提示上方绘制（调整Y坐标）
            int yPos = this.offsetY + 280;
            int count = 0;
            this.fontRendererObj
                .drawStringWithShadow(I18n.format("adm.property.tips"), this.offsetX + 10, yPos, 0x00FFff);
            for (String line : wrappedHint) {
                this.fontRendererObj.drawStringWithShadow("§l" + line, this.offsetX + 10, yPos + 10, 0x00FFff);
                yPos += 10;
                count++;
            }
        }

    }

    private void drawColoredHoveringText(List<String> textLines, int x, int y) {
        if (textLines == null || textLines.isEmpty()) return;

        int tooltipTextWidth = 0;
        int tooltipHeight = 0;
        final int maxLineWidth = 200;

        // 计算最大宽度和总高度
        for (String line : textLines) {
            int lineWidth = this.fontRendererObj.getStringWidth(line);
            tooltipTextWidth = Math.max(tooltipTextWidth, Math.min(lineWidth, maxLineWidth));
            tooltipHeight += 10;
        }

        tooltipHeight += 4;
        tooltipTextWidth += 8;

        // 调整位置防止溢出屏幕
        int tooltipX = x + 12;
        int tooltipY = y - 12;

        if (tooltipX + tooltipTextWidth > this.width) {
            tooltipX -= 28 + tooltipTextWidth;
        }
        if (tooltipY + tooltipHeight + 6 > this.height) {
            tooltipY = this.height - tooltipHeight - 6;
        }

        // 绘制背景
        this.zLevel = 300.0F;
        drawRect(tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, 0xAF00AAAA);
        drawRect(tooltipX - 2, tooltipY - 2, tooltipX + tooltipTextWidth + 2, tooltipY + tooltipHeight + 2, 0x80000000);
        this.zLevel = 301.0F;

        // 绘制文本
        int currentY = tooltipY;
        for (String line : textLines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                String prefix = line.substring(0, colonIndex + 1)
                    .trim();
                String content = line.substring(colonIndex + 1)
                    .trim();

                // 绘制前缀（固定颜色）
                this.fontRendererObj.drawStringWithShadow(prefix, tooltipX, currentY, 0x00FFFF);

                // 特殊字段颜色处理
                int contentColor = 0xFFFFFF;

                // 绘制内容（带下划线）
                this.fontRendererObj.drawStringWithShadow(
                    "§n" + content,
                    tooltipX + this.fontRendererObj.getStringWidth(prefix) + 2,
                    currentY,
                    contentColor);
            } else {
                this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0x00FFFF);
            }
            currentY += 10;
        }

        this.zLevel = 0.0F;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateDataType(NBTTagCompound nbt) {
        // 根据状态组合构建数据类型名称
        String dataTypeName;

        if (isTypeItem) {
            // 物品类型
            if (isUsed) {
                // 已使用
                dataTypeName = isBytes ? "UsedBytes" : "UsedItemTypes";
            } else {
                // 总量
                dataTypeName = isBytes ? "TotalBytes" : "TotalItemTypes";
            }
        } else {
            // 流体类型
            if (isUsed) {
                // 已使用
                dataTypeName = isBytes ? "UsedFluidBytes" : "UsedFluidTypes";
            } else {
                // 总量
                dataTypeName = isBytes ? "TotalFluidBytes" : "TotalFluidTypes";
            }
        }

        // 更新TileEntity和NBT
        AdvanceDataMonitor.LOG.info("Update data type: " + dataTypeName);
        nbt.setString("name", dataTypeName);

        // 设置显示方式标记（数值/百分比）
        AdvanceDataMonitor.LOG.info("Update isValue: " + isValue);
        nbt.setBoolean("isValue", isValue);

        // 同步到服务器
    }

    private void initDataType() {
        String dataType = tileEntityAdvanceDataMonotor.getName(index);

        switch (dataType) {
            case "TotalBytes":
                isTypeItem = true;
                isUsed = false;
                isBytes = true;
                break;
            case "UsedBytes":
                isTypeItem = true;
                isUsed = true;
                isBytes = true;
                break;
            case "TotalItemTypes":
                isTypeItem = true;
                isUsed = false;
                isBytes = false;
                break;
            case "UsedItemTypes":
                isTypeItem = true;
                isUsed = true;
                isBytes = false;
                break;
            case "TotalFluidBytes":
                isTypeItem = false;
                isUsed = false;
                isBytes = true;
                break;
            case "UsedFluidBytes":
                isTypeItem = false;
                isUsed = true;
                isBytes = true;
                break;
            case "TotalFluidTypes":
                isTypeItem = false;
                isUsed = false;
                isBytes = false;
                break;
            case "UsedFluidTypes":
                isTypeItem = false;
                isUsed = true;
                isBytes = false;
                break;
            default:
                isTypeItem = true;
                isUsed = true;
                isBytes = true;
                break;

        }

    }

    // 添加清除数据的方法
    private void clearDataValues() {
        NBTTagCompound nbt = this.tileEntityAdvanceDataMonotor.getDataBound(this.index);
        nbt.setTag("dataValues", new NBTTagList()); // 清空数据列表

        // 更新TileEntity
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));

    }

    private void updateGridLineAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("gridLineAlpha", newValue);
        tileEntityAdvanceDataMonotor.setGridLineAlpha(index, newValue);
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void updateNameAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("nameAlpha", newValue);
        tileEntityAdvanceDataMonotor.setNameAlpha(index, newValue);
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void updateAxisAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("axisLineAlpha", newValue);
        tileEntityAdvanceDataMonotor.setAxisLineAlpha(index, newValue);
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void updateAxisFontAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("axisFontAlpha", newValue);
        tileEntityAdvanceDataMonotor.setAxisFontAlpha(index, newValue);
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void updateDataLineAlpha(double newValue, NBTTagCompound nbt) {
        nbt.setDouble("lineAlpha", newValue);
        tileEntityAdvanceDataMonotor.setLineAlpha(index, newValue);
        this.tileEntityAdvanceDataMonotor.setDisplayData(this.index, nbt);
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        AdvanceDataMonitor.ADMCHANEL.sendToServer(
            new PacketSynTileEntity(
                tileEntityAdvanceDataMonotor.xCoord,
                tileEntityAdvanceDataMonotor.yCoord,
                tileEntityAdvanceDataMonotor.zCoord,
                nbt));
    }

    private void drawHoveringText(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        this.drawHoveringText(lines, x, y, this.fontRendererObj);
    }

}
