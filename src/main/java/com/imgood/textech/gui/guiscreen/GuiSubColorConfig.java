package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidHexColor;
import static com.imgood.textech.utils.ContentsHelper.wrapText;

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

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.DataBound;

/**
 * Display names / 显示名称:
 * - EN: Color Config (per-binding sub GUI)
 * - ZH: 颜色数据配置（绑定子界面�?
 * Lang keys: adm.title.data_config_color
 */
public class GuiSubColorConfig extends ADM_GuiScreen {

    private final TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonotor;
    @SuppressWarnings("unused")
    private EntityPlayer player;
    private World world;

    private int index;
    private List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();
    // 文本框与本地化键的映�?
    private final Map<ADM_GuiTextField, String> fieldHints = new HashMap<>();

    private ADM_GuiTextField hoveredTextField;
    private ADM_GuiTextField focusedField = null;

    private ADM_GuiTextField textFieldDisplayNameColor;
    private ADM_GuiTextField textFieldAxisLineColor;
    private ADM_GuiTextField textFieldAxisFontColor;
    private ADM_GuiTextField textFieldLineColor;

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

    // 这里修改偏移其实没用，初始化计算�?
    private int offsetX = -200;
    private int offsetY = 100;
    private int startOffsetX = -150;
    private int startOffsetY = -100;
    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;

    private int buttonRowYOffset1 = 180;
    private int buttonRowYOffset2 = 340;
    private int buttonRowConfigYoffset1 = 20;
    private int buttonRowConfigYinterval1 = 30;
    private int buttonRowConfigYoffset2 = 100;
    private int buttonRowConfigXoffset1 = 360;
    private int buttonRowConfigXoffset2 = 100;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 45;
    private int buttonRow2Width = 60;

    private String errorTips = "";

    private boolean isInitialized = false;
    private String dataType;
    private boolean isEnabled;
    private boolean isEnabledAxis;
    private boolean isEnabledData;
    private boolean isEnabledAxisFont;

    public GuiSubColorConfig(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity, int index) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.index = index;
        this.setBackgroundTexture(getGuiScreenHolographicDisplay_Sub_Background);
        this.setSize(400, 250);
        this.setStretch(false);
    }

    private void saveCurrentState() {
        this.contents.clear();

        this.contents.add(textFieldDisplayNameColor.getText());
        this.contents.add(textFieldAxisLineColor.getText());
        this.contents.add(textFieldAxisFontColor.getText());
        this.contents.add(textFieldLineColor.getText());

    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        // this.currentHashCode = this.tileHolographicDisplay.getImgPath(this.index);
        focusedField = textFieldAxisFontColor;
        isEnabled = tileEntityAdvanceDataMonotor.getEnable(index);
        isEnabledAxis = tileEntityAdvanceDataMonotor.getEnableAxis(index);
        isEnabledData = tileEntityAdvanceDataMonotor.getEnableData(index);
        isEnabledAxisFont = tileEntityAdvanceDataMonotor.getEnableAxisFont(index);

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

            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getName(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayName(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayNameColor(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisLineColor(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getAxisFontColor(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getLineColor(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getLineWidth(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));

            isInitialized = true;
        }

        this.offsetX = (this.width / 2) + startOffsetX;
        this.offsetY = (this.height / 2) + startOffsetY;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 35);

        this.textFieldsRight.clear();
        textFieldsRight.add(this.textFieldDisplayNameColor);
        textFieldsRight.add(this.textFieldAxisLineColor);
        textFieldsRight.add(this.textFieldAxisFontColor);
        textFieldsRight.add(this.textFieldLineColor);

        try {
            autoTextField("Right", this.textFieldsRight, 0, 25, this.offsetX + 90, this.offsetY + 10, 80, 20);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.textFieldDisplayNameColor.setFocused(true);
        this.textFieldDisplayNameColor.setMaxStringLength(100);
        this.textFieldDisplayNameColor.setText(this.tileEntityAdvanceDataMonotor.getDisplayNameColor(this.index));

        this.textFieldAxisLineColor.setMaxStringLength(100);
        this.textFieldAxisLineColor.setText(this.tileEntityAdvanceDataMonotor.getAxisLineColor(this.index));

        this.textFieldAxisFontColor.setMaxStringLength(100);
        this.textFieldAxisFontColor.setText(this.tileEntityAdvanceDataMonotor.getAxisFontColor(this.index));

        this.textFieldLineColor.setMaxStringLength(100);
        this.textFieldLineColor.setText(this.tileEntityAdvanceDataMonotor.getLineColor(this.index));

        // 保存/取消按钮
        this.buttonList.add(
            new ADM_GuiButton(
                0,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.save")) // 修改保存按钮
                    .setTexture(button_texture)
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
                I18n.format("adm.button.cancel")) // 修改取消按钮
                    .setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        fieldHints.clear();
        fieldHints.put(textFieldDisplayNameColor, "adm.hint.displaycolor");
        fieldHints.put(textFieldAxisLineColor, "adm.hint.axislinecolor");
        fieldHints.put(textFieldAxisFontColor, "adm.hint.axisfontcolor");
        fieldHints.put(textFieldLineColor, "adm.hint.linecolor");

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
                // 这里不return是考虑到button id不是唯一�?
            }
        }
    }

    private GuiButton getButtonByid(int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return button;
                // button id不是唯一的，但会返回第一个检查到�?
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

            if (row.equals("Right")) {
                switch (i) {
                    case 0:
                        this.textFieldDisplayNameColor = textField;
                        break;
                    case 1:
                        this.textFieldAxisLineColor = textField;
                        break;
                    case 2:
                        this.textFieldAxisFontColor = textField;
                        break;
                    case 3:
                        this.textFieldLineColor = textField;
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

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color) {
        int intervalXCurrent = 0;
        int intervalYCurrent = 0;
        for (String t : text) {
            this.fontRendererObj.drawString(t, startX + intervalXCurrent, startY + intervalYCurrent, this.textColor);
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
        NBTTagCompound nbt;
        // 获取现有数据绑定
        NBTTagCompound existingNbt = this.tileEntityAdvanceDataMonotor.getDataBound(this.index);
        // 创建副本用于修改
        nbt = (NBTTagCompound) existingNbt.copy();
        NBTTagList existingDataValues = existingNbt.getTagList("dataValues", 10);
        switch (button.id) {
            case 0 -> {

                // 保留原有的dataValues数据

                nbt.setTag("dataValues", existingDataValues.copy());
                if (this.dataType == null) {
                    nbt.setString("dataType", "line");
                } else {
                    nbt.setString("dataType", this.dataType);
                }

                // Left 组字段（textFieldName 之前的字段）

                // 补充缺失�?DisplayNameColor
                if (!isValidHexColor(this.textFieldDisplayNameColor.getText())) {
                    this.errorTips = I18n.format("adm.error.displaycolor");
                    return;
                } else {
                    nbt.setString("displayNameColor", this.textFieldDisplayNameColor.getText());
                }

                if (!isValidHexColor(this.textFieldAxisLineColor.getText())) {
                    this.errorTips = I18n.format("adm.error.axislinecolor");
                    return;
                } else {
                    nbt.setString("axisLineColor", this.textFieldAxisLineColor.getText());
                }

                if (!isValidHexColor(this.textFieldAxisFontColor.getText())) {
                    this.errorTips = I18n.format("adm.error.axisfontcolor");
                    return;
                } else {
                    nbt.setString("axisFontColor", this.textFieldAxisFontColor.getText());
                }

                if (!isValidHexColor(this.textFieldLineColor.getText())) {
                    this.errorTips = I18n.format("adm.error.linecolor");
                    return;
                } else {
                    nbt.setString("lineColor", this.textFieldLineColor.getText());
                }

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
                // 更新TileEntity状�?
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
            default -> {
                // 处理其他按钮的行�?
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
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
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        String[] label1 = { I18n.format("adm.label.displaynamecolor"), I18n.format("adm.label.axislinecolor"),
            I18n.format("adm.label.axisfontcolor"), I18n.format("adm.label.linecolor") };
        autoText(label1, 0, 25, this.offsetX + 20, this.offsetY + 10, this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.data_config_color", this.index + 1), // 本地化标�?
            this.offsetX + 200,
            this.offsetY - 37,
            this.textColor);

        this.fontRendererObj.drawString(errorTips, this.offsetX + 150, this.offsetY + 380, 0xff0000);
        drawTextFieldBackground(textFieldsRight);
        if (isValidHexColor(this.textFieldDisplayNameColor.getText())) {
            this.drawCenteredString(
                this.fontRendererObj,
                "§l�?,
                this.offsetX + 150,
                this.offsetY + 10,
                Integer.parseInt(this.textFieldDisplayNameColor.getText(), 16));
        }
        if (isValidHexColor(this.textFieldAxisLineColor.getText())) {
            this.drawCenteredString(
                this.fontRendererObj,
                "§l�?,
                this.offsetX + 150,
                this.offsetY + 35,
                Integer.parseInt(this.textFieldAxisLineColor.getText(), 16));
        }
        if (isValidHexColor(this.textFieldAxisFontColor.getText())) {
            this.drawCenteredString(
                this.fontRendererObj,
                "§l�?,
                this.offsetX + 150,
                this.offsetY + 60,
                Integer.parseInt(this.textFieldAxisFontColor.getText(), 16));
        }
        if (isValidHexColor(this.textFieldLineColor.getText())) {
            this.drawCenteredString(
                this.fontRendererObj,
                "§l�?,
                this.offsetX + 150,
                this.offsetY + 85,
                Integer.parseInt(this.textFieldLineColor.getText(), 16));
        }

        // this.drawCenteredString(this.fontRendererObj, this.currentHashCode, this.offsetX+192, this.offsetY+100,
        // this.textColor);

        hoveredTextField = null;

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

            // 在错误提示上方绘制（调整Y坐标�?
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

        // 计算最大宽度和总高�?
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
                if (hoveredTextField != null) {
                    if (hoveredTextField == textFieldDisplayNameColor) {
                        contentColor = isValidHexColor(content) ? Integer.parseInt(content, 16) : 0xFFFFFF;
                    } else if (hoveredTextField == textFieldAxisLineColor) {
                        contentColor = isValidHexColor(content) ? Integer.parseInt(content, 16) : 0xFFFFFF;
                    }
                }

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
}
