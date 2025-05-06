package com.imgood.advancedatamonitor.gui.guiscreen;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import com.imgood.advancedatamonitor.utils.ContentsHelper;
import com.imgood.advancedatamonitor.utils.DataBound;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidDouble;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidHexColor;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidInteger;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.wrapText;

public class GuiSubAdvanceDataMonitor extends ADM_GuiScreen {
    private final TileEntityAdvanceDataMonotor tileEntityAdvanceDataMonotor;
    @SuppressWarnings("unused")
    private EntityPlayer player;
    private World world;

    private int index;
    private List<ADM_GuiTextField> textFieldsLeft = new ArrayList<>();
    private List<ADM_GuiTextField> textFieldsRight = new ArrayList<>();

    private ADM_GuiTextField hoveredTextField;

    private ADM_GuiTextField textFieldTileEntityXYZ;
    private ADM_GuiTextField textFieldYmin;
    private ADM_GuiTextField textFieldYmax;
    private ADM_GuiTextField textFieldDataLimit;
    private ADM_GuiTextField textFieldLineColor;
    private ADM_GuiTextField textFieldLineWidth;
    private ADM_GuiTextField textFieldScaled;
    private ADM_GuiTextField textFieldxOffset;
    private ADM_GuiTextField textFieldyOffset;
    private ADM_GuiTextField textFieldRotationX;
    private ADM_GuiTextField textFieldRotationY;
    private ADM_GuiTextField textFieldRotationZ;
    private ADM_GuiTextField textFieldName;
    private ADM_GuiTextField textFieldDisplayName;
    private ADM_GuiTextField textFieldInterval;

    private List<String> contents = new ArrayList<>();

    private static final ResourceLocation button_texture = new ResourceLocation(AdvanceDataMonitor.MODID,"textures/gui/button_ADM.png");
    private static final ResourceLocation button_hover_texture = new ResourceLocation(AdvanceDataMonitor.MODID,"textures/gui/button_hover_ADM.png");
    private static final ResourceLocation guiScreenHolographicDisplay_Main_Background = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/background_AdvanceDataMonitor_Main.png");
    private static final ResourceLocation textField_texture = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/textfield_ADM_8020.png");
    private static final ResourceLocation textField_hover_texture = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/textfield_hover_ADM_8020.png");
    private static final ResourceLocation textField_selected_texture = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/textfield_selected_ADM.png");
    private static final ResourceLocation textField_selected_texture_01 = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/textfield_selected_ADM_1.png");
    private static final ResourceLocation getGuiScreenHolographicDisplay_Sub_Background = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/background_ADM_Sub.png");

    private int offsetX = 100;
    private int offsetY = 100;
    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;

    private int buttonRowYOffset1 = 300;
    private int buttonRowYOffset2 = 240;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 60;
    private int buttonRow2Width = 60;

    private String errorTips = "";

    private boolean isInitialized = false;
    private boolean isFormValid = false;

    public GuiSubAdvanceDataMonitor(EntityPlayer player, World world, TileEntityAdvanceDataMonotor tileEntity, int index) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.index = index;
        this.setBackgroundTexture(getGuiScreenHolographicDisplay_Sub_Background);
        this.setSize(420, 380);
        this.setStretch(false);
    }

    private void saveCurrentState() {
        this.contents.clear();
        this.contents.add(textFieldTileEntityXYZ.getText());
        this.contents.add(textFieldYmin.getText());
        this.contents.add(textFieldYmax.getText());
        this.contents.add(textFieldDataLimit.getText());
        this.contents.add(textFieldLineColor.getText());
        this.contents.add(textFieldLineWidth.getText());
        this.contents.add(textFieldScaled.getText());
        this.contents.add(textFieldInterval.getText());

        this.contents.add(textFieldxOffset.getText());
        this.contents.add(textFieldyOffset.getText());
        this.contents.add(textFieldRotationX.getText());
        this.contents.add(textFieldRotationY.getText());
        this.contents.add(textFieldRotationZ.getText());
        this.contents.add(textFieldName.getText());
        this.contents.add(textFieldDisplayName.getText());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        //this.currentHashCode = this.tileHolographicDisplay.getImgPath(this.index);
        if (isInitialized) {
            saveCurrentState();
        } else {
            this.contents.clear();
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getXYZ(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYMin(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYMax(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDataLimit(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getLineColor(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getLineWidth(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getScale(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getInterval(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getYOffset(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getXOffset(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationX(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationY(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getRotationZ(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getName(this.index)));
            this.contents.add(String.valueOf(this.tileEntityAdvanceDataMonotor.getDisplayName(this.index)));

            isInitialized = true;
        }

        this.offsetX = (this.width / 2) - 192;
        this.offsetY = (this.height / 2) - 90;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX-20, this.offsetY-35);
        this.textFieldsLeft.clear();
        textFieldsLeft.add(this.textFieldTileEntityXYZ);
        textFieldsLeft.add(this.textFieldYmin);
        textFieldsLeft.add(this.textFieldYmax);
        textFieldsLeft.add(this.textFieldDataLimit);
        textFieldsLeft.add(this.textFieldLineColor);
        textFieldsLeft.add(this.textFieldLineWidth);
        textFieldsLeft.add(this.textFieldScaled);
        textFieldsLeft.add(this.textFieldInterval);
        try {
            autoTextField("Left",this.textFieldsLeft, 0, 25,this.offsetX+70, this.offsetY, 80, 20);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.textFieldsRight.clear();
        textFieldsRight.add(this.textFieldxOffset);
        textFieldsRight.add(this.textFieldyOffset);
        textFieldsRight.add(this.textFieldRotationX);
        textFieldsRight.add(this.textFieldRotationY);
        textFieldsRight.add(this.textFieldRotationZ);
        textFieldsRight.add(this.textFieldName);
        textFieldsRight.add(this.textFieldDisplayName);
        try {
            autoTextField("Right",this.textFieldsRight, 0, 25,this.offsetX+235, this.offsetY, 80, 20);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.textFieldTileEntityXYZ.setMaxStringLength(100);
        this.textFieldTileEntityXYZ.setFocused(true);
        this.textFieldTileEntityXYZ.setText(this.tileEntityAdvanceDataMonotor.getXYZ(this.index));
        this.textFieldYmin.setMaxStringLength(100);
        this.textFieldYmin.setText(this.tileEntityAdvanceDataMonotor.getYMin(this.index)+"");
        this.textFieldYmax.setMaxStringLength(100);
        this.textFieldYmax.setText(this.tileEntityAdvanceDataMonotor.getYMax(this.index)+"");
        this.textFieldDataLimit.setMaxStringLength(100);
        this.textFieldDataLimit.setText(this.tileEntityAdvanceDataMonotor.getDataLimit(this.index)+"");
        this.textFieldLineColor.setMaxStringLength(100);
        this.textFieldLineColor.setText(this.tileEntityAdvanceDataMonotor.getLineColor(this.index)+"");
        this.textFieldLineWidth.setMaxStringLength(100);
        this.textFieldLineWidth.setText(this.tileEntityAdvanceDataMonotor.getLineWidth(this.index)+"");
        this.textFieldScaled.setMaxStringLength(100);
        this.textFieldScaled.setText(this.tileEntityAdvanceDataMonotor.getScale(this.index)+"");
        this.textFieldInterval.setMaxStringLength(100);
        this.textFieldInterval.setText(this.tileEntityAdvanceDataMonotor.getInterval(this.index)+"");

        this.textFieldxOffset.setMaxStringLength(100);
        this.textFieldxOffset.setText(this.tileEntityAdvanceDataMonotor.getXOffset(this.index)+"");
        this.textFieldyOffset.setMaxStringLength(100);
        this.textFieldyOffset.setText(this.tileEntityAdvanceDataMonotor.getYOffset(this.index)+"");
        this.textFieldRotationX.setMaxStringLength(100);
        this.textFieldRotationX.setText(this.tileEntityAdvanceDataMonotor.getRotationX(this.index)+"");
        this.textFieldRotationY.setMaxStringLength(100);
        this.textFieldRotationY.setText(this.tileEntityAdvanceDataMonotor.getRotationY(this.index)+"");
        this.textFieldRotationZ.setMaxStringLength(100);
        this.textFieldRotationZ.setText(this.tileEntityAdvanceDataMonotor.getRotationZ(this.index)+"");
        this.textFieldName.setMaxStringLength(100);
        this.textFieldName.setText(this.tileEntityAdvanceDataMonotor.getName(this.index));
        this.textFieldDisplayName.setMaxStringLength(100);
        this.textFieldDisplayName.setText(this.tileEntityAdvanceDataMonotor.getDisplayName(this.index));


        //保存/取消按钮
        this.buttonList.add(new ADM_GuiButton(0,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                "Save")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(1,
                this.offsetX + 70,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                "Cancel")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(new ADM_GuiButton(2,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                "Line")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow2RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(3,
                this.offsetX + 70,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                "Bar")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow2RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(4,
                this.offsetX + 140,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                "Bar 3D")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow2RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(5,
                this.offsetX + 210,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                "Waterfall")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow2RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(6,
                this.offsetX + 280,
                this.offsetY + buttonRowYOffset2,
                buttonRow2Width,
                20,
                "Difference")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow2RGB)
                .setUseHoverEffect(true)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        setTileEntityDatatype(this.tileEntityAdvanceDataMonotor.getDataType(this.index));

    }

    private void setTileEntityDatatype(DataBound.DataType dataType) {
        /*getButtonByid(2).enabled = true;
        getButtonByid(3).enabled = true;
        getButtonByid(4).enabled = true;
        getButtonByid(5).enabled = true;
        getButtonByid(6).enabled = true;*/
        ((ADM_GuiButton)getButtonByid(2)).setUseRGBEffect(false);
        ((ADM_GuiButton)getButtonByid(3)).setUseRGBEffect(false);
        ((ADM_GuiButton)getButtonByid(4)).setUseRGBEffect(false);
        ((ADM_GuiButton)getButtonByid(5)).setUseRGBEffect(false);
        ((ADM_GuiButton)getButtonByid(6)).setUseRGBEffect(false);
        switch (dataType) {
            case Line:
                //getButtonByid(2).enabled = false;
                ((ADM_GuiButton)getButtonByid(2)).setUseRGBEffect(true);
                break;
            case Bar:
                //getButtonByid(3).enabled = false;
                ((ADM_GuiButton)getButtonByid(3)).setUseRGBEffect(true);
                break;
            case Bar_3D:
                //getButtonByid(4).enabled = false;
                ((ADM_GuiButton)getButtonByid(4)).setUseRGBEffect(true);
                break;
            case Waterfall:
                //getButtonByid(5).enabled = false;
                ((ADM_GuiButton)getButtonByid(5)).setUseRGBEffect(true);
                break;
            case Difference:
                //getButtonByid(6).enabled = false;
                ((ADM_GuiButton)getButtonByid(6)).setUseRGBEffect(true);
        }
        this.tileEntityAdvanceDataMonotor.setDataType(this.index, dataType);
    }



    private boolean textContains(GuiButton guiButton, String contains) {
        return guiButton.displayString.contains(contains);
    }

    private void setButtonDisplayString (int id, String displayString) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                button.displayString = displayString;
                //这里不return是考虑到button id不是唯一的
            }
        }
    }

    private GuiButton getButtonByid (int id) {
        for (GuiButton button : this.buttonList) {
            if (button.id == id) {
                return button;
                //button id不是唯一的，但会返回第一个检查到的
            }
        }
        return null;
    }


    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        isInitialized = false;
    }

    public void autoTextField(String row,List<ADM_GuiTextField> textFields, int intervalX, int intervalY, int startX, int startY, int width, int height) throws NoSuchFieldException, IllegalAccessException {
        int intervalXCurrent = 0;
        int intervalYCurrent = 0;
        for (int i = 0; i < textFields.size(); i++) {
            ADM_GuiTextField textField = new ADM_GuiTextField(this.fontRendererObj,
                    startX + intervalXCurrent,
                    startY + intervalYCurrent,
                    width,
                    height)
                    .setBackgroundTexture(textField_texture)
                    .setFocusedBackgroundTexture(textField_hover_texture);
            textFields.set(i, textField);
            intervalXCurrent += intervalX;
            intervalYCurrent += intervalY;

            if (row.equals("Left")) {
                switch (i) {
                    case 0:
                        this.textFieldTileEntityXYZ = textField;
                        break;
                    case 1:
                        this.textFieldYmin = textField;
                        break;
                    case 2:
                        this.textFieldYmax = textField;
                        break;
                    case 3:
                        this.textFieldDataLimit = textField;
                        break;
                    case 4:
                        this.textFieldLineColor = textField;
                        break;
                    case 5:
                        this.textFieldLineWidth = textField;
                        break;
                    case 6:
                        this.textFieldScaled = textField;
                        break;
                    case 7:
                        this.textFieldInterval = textField;
                        break;
                }
            } else if (row.equals("Right")) {
                switch (i) {
                    case 0:
                        this.textFieldxOffset = textField;
                        break;
                    case 1:
                        this.textFieldyOffset = textField;
                        break;
                    case 2:
                        this.textFieldRotationX = textField;
                        break;
                    case 3:
                        this.textFieldRotationY = textField;
                        break;
                    case 4:
                        this.textFieldRotationZ = textField;
                        break;
                    case 5:
                        this.textFieldName = textField;
                        break;
                    case 6:
                        this.textFieldDisplayName = textField;
                        break;
                }
            }

        }
    }

    public void drawTextFieldBackground(ADM_GuiTextField  textField, int x, int y, int width, int height) {
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
        for (ADM_GuiTextField textFieldCurrunt : textField)
        {
            xCoord = textFieldCurrunt.xPosition;
            yCoord = textFieldCurrunt.yPosition+2;
            textWidth = textFieldCurrunt.width+20;
            textHeight = textFieldCurrunt.height;
            if (textFieldCurrunt.isFocused()) {
                drawTextFieldFocusBackground(textFieldCurrunt,xCoord, yCoord, 100, 20);
            } else {
                drawTextFieldBackground(textFieldCurrunt, xCoord, yCoord, 100, 20);
            }
        }
    }

    public void autoText(String[] text, int intervalX, int intervalY, int startX, int startY, int color){
        int intervalXCurrent = 0;
        int intervalYCurrent = 0;
        for (String t : text){
            this.fontRendererObj.drawString(t, startX + intervalXCurrent, startY + intervalYCurrent, this.textColor);
            intervalXCurrent += intervalX;
            intervalYCurrent += intervalY;
        }
    }

    private boolean isMouseOver(ADM_GuiTextField textField, int mouseX, int mouseY) {
        return mouseX >= textField.xPosition && mouseX < textField.xPosition + textField.width &&
                mouseY >= textField.yPosition && mouseY < textField.yPosition + textField.height;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound nbt;
        switch (button.id) {
            case 0 -> {
                nbt = new NBTTagCompound();
                String XYZ = this.textFieldTileEntityXYZ.getText().replace("，",",").replace(" ", "");
                if (!ContentsHelper.isValidPosFormat(XYZ)) {
                    this.errorTips = I18n.format("adm.error.xyz");
                    return;
                } else {
                    nbt.setString("XYZ", XYZ);
                }

                if (!isValidDouble(this.textFieldYmin.getText())) {
                    this.errorTips = I18n.format("adm.error.ymin");
                    return;
                } else {
                    nbt.setDouble("yMin", Double.parseDouble(this.textFieldYmin.getText()));
                }

                if (!isValidDouble(this.textFieldYmax.getText())) {
                    this.errorTips = I18n.format("adm.error.ymax");
                    return;
                } else {
                    nbt.setDouble("yMax", Double.parseDouble(this.textFieldYmax.getText()));
                }

                if (!isValidInteger(this.textFieldDataLimit.getText())) {
                    this.errorTips = I18n.format("adm.error.datalimit");
                    return;
                } else {
                    int dataLimit = Integer.parseInt(this.textFieldDataLimit.getText());
                    if (dataLimit < 0) {
                        nbt.setInteger("dataLimit",10);
                    } else if (dataLimit > 50) {
                        nbt.setInteger("dataLimit",50);
                    }
                    nbt.setInteger("dataLimit", Integer.parseInt(this.textFieldDataLimit.getText()));
                }
                if (!isValidHexColor(this.textFieldLineColor.getText())) {
                    this.errorTips = I18n.format("adm.error.linecolor");
                    return;
                } else {
                    nbt.setString("lineColor", this.textFieldLineColor.getText());
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

                if (!isValidInteger(this.textFieldInterval.getText())) {
                    this.errorTips = I18n.format("adm.error.interval");
                    return;
                } else {
                    int interval = Integer.parseInt(this.textFieldInterval.getText());
                    if (interval <=20) {
                        nbt.setInteger("interval", 20);
                    } else {
                        nbt.setInteger("interval", Integer.parseInt(this.textFieldInterval.getText()));
                    }
                }
                if (!isValidDouble(this.textFieldyOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.yoffset");
                    return;
                } else {
                    nbt.setDouble("yOffset", Double.parseDouble(this.textFieldyOffset.getText()));
                }

                if (!isValidDouble(this.textFieldxOffset.getText())) {
                    this.errorTips = I18n.format("adm.error.xoffset");
                    return;
                } else {
                    nbt.setDouble("xOffset", Double.parseDouble(this.textFieldxOffset.getText()));
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
                nbt.setString("name", this.textFieldName.getText());
                nbt.setString("displayName", this.textFieldDisplayName.getText());

                this.tileEntityAdvanceDataMonotor.setDisplayData(this.index,nbt);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                        new PacketSynTileEntity(
                                tileEntityAdvanceDataMonotor.xCoord,
                                tileEntityAdvanceDataMonotor.yCoord,
                                tileEntityAdvanceDataMonotor.zCoord,
                                nbt
                        ));
                isInitialized = false;
                errorTips = "";
                //this.mc.displayGuiScreen(null);
                this.mc.displayGuiScreen(new GuiMainAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor)
                        .setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
            }
            case 1 -> {
                //this.mc.displayGuiScreen(null);
                this.mc.displayGuiScreen(new GuiMainAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor)
                        .setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(guiScreenHolographicDisplay_Main_Background));
            }
            case 2 -> {
                setTileEntityDatatype(DataBound.DataType.Line);
            }
            case 3 -> {
                setTileEntityDatatype(DataBound.DataType.Bar);
            }
            case 4 -> {
                setTileEntityDatatype(DataBound.DataType.Bar_3D);
            }
            case 5 -> {
                setTileEntityDatatype(DataBound.DataType.Waterfall);
                }
            case 6 -> {
                setTileEntityDatatype(DataBound.DataType.Difference);
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
        }
        for (ADM_GuiTextField textField : textFieldsRight) {
            textField.mouseClicked(mouseX, mouseY, mouseButton);
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
        String[] text = {"§lX,Y,Z", "§lYMin", "§lYMax", "§lData Limit", "§lLine Color","§lLine Width","§lScaled","§lInterval"};
        autoText(text, 0, 25, this.offsetX-4, this.offsetY, this.textColor);
        String[] text2 = {"§lXOffset", "§lYOffset", "§lXRotation", "§lYRotation", "§lZRotation", "§lNBT Name",  "§lDisplay Name"};
        autoText(text2, 0, 25, this.offsetX+150-4, this.offsetY, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "§lData Config Of §n"+(this.index+1), this.offsetX+212, this.offsetY-35, this.textColor);
        this.fontRendererObj.drawString(errorTips, this.offsetX+10, this.offsetY+220, 0xff0000);
        drawTextFieldBackground(textFieldsLeft);
        drawTextFieldBackground(textFieldsRight);
        if (isValidHexColor(this.textFieldLineColor.getText())) {
            this.drawCenteredString(this.fontRendererObj, "§l■", this.offsetX+125, this.offsetY+99, Integer.parseInt(this.textFieldLineColor.getText(), 16));
        }

        //this.drawCenteredString(this.fontRendererObj, this.currentHashCode, this.offsetX+192, this.offsetY+100, this.textColor);

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
        if (hoveredTextField != null) {
            List<String> wrappedText = wrapText(hoveredTextField.getText(), 12);
            drawColoredHoveringText(wrappedText, mouseX, mouseY, this.index);
            //drawHoveringText(wrappedText, mouseX, mouseY, this.fontRendererObj);
        }
    }

    private void drawTooltipBackground(int x, int y, int width, int height) {
        //弃用了所以给了个0
        int borderSize = 0;

        // 绘制背景纯色或半透明矩形
        drawRect(x, y, x + width, y + height, 0xaf00aaaa);

        drawTexturedModalRect(x - borderSize, y - borderSize, 0, 0, borderSize, borderSize);
        drawTexturedModalRect(x + width, y - borderSize, 16 - borderSize, 0, borderSize, borderSize);
        drawTexturedModalRect(x - borderSize, y + height, 0, 16 - borderSize, borderSize, borderSize);
        drawTexturedModalRect(x + width, y + height, 16 - borderSize, 16 - borderSize, borderSize, borderSize);

        /*
        // 上边框
        drawTexturedModalRect(x, y - borderSize, borderSize, 0, width, borderSize);
        // 下边框
        drawTexturedModalRect(x, y + height, borderSize, 16 - borderSize, width, borderSize);
        // 左边框
        drawTexturedModalRect(x - borderSize, y, 0, borderSize, borderSize, height);
        // 右边框
        drawTexturedModalRect(x + width, y, 16 - borderSize, borderSize, borderSize, height);*/
    }

    private void drawColoredHoveringText(List<String> textLines, int x, int y, int buttonId) {
        if (!textLines.isEmpty()) {
            int tooltipTextWidth = 0;
            for (String line : textLines) {
                int lineWidth = this.fontRendererObj.getStringWidth(line);
                if (lineWidth > tooltipTextWidth) {
                    tooltipTextWidth = lineWidth;
                }
            }
            tooltipTextWidth += 1;
            int tooltipX = x + 12;
            int tooltipY = y - 12;
            int tooltipHeight = 8;

            if (textLines.size() > 1) {
                tooltipHeight += 2 + (textLines.size() - 1) * 10;
            }

            if (tooltipX + tooltipTextWidth > this.width) {
                tooltipX -= 28 + tooltipTextWidth;
            }

            if (tooltipY + tooltipHeight + 6 > this.height) {
                tooltipY = this.height - tooltipHeight - 6;
            }

            tooltipX += 10;
            tooltipY += 10;

            // 绘制背景和边框
            this.zLevel = 0F;
            drawTooltipBackground(tooltipX, tooltipY, tooltipTextWidth, tooltipHeight);


            for (int lineNumber = 0; lineNumber < textLines.size(); ++lineNumber) {
                String line = textLines.get(lineNumber);
                int colonIndex = line.indexOf(':');
                String[] buff;
                if (colonIndex != -1) {
                    buff = new String[]{
                            line.substring(0, colonIndex),
                            colonIndex < line.length() - 1 ? line.substring(colonIndex + 1).trim() : ""
                    };
                } else {
                    buff = new String[]{line, ""};
                }
                /*if (line.contains("Color:") || line.contains("Text1:") || line.contains("Text2:") || line.contains("Text3:") || line.contains("Text4:")) {
                    this.fontRendererObj.drawStringWithShadow(buff[0], tooltipX, tooltipY, Integer.parseInt("00ffff", 16));
                    if (!buff[1].isEmpty()) {
                        this.fontRendererObj.drawStringWithShadow(buff[1], tooltipX + this.fontRendererObj.getStringWidth(buff[0]), tooltipY, Integer.parseInt(this.tileEntityAdvanceDataMonotor.getRGBColor(buttonId), 16));
                    }
                } else {
                    this.fontRendererObj.drawStringWithShadow( buff[0], tooltipX, tooltipY, Integer.parseInt("00ffff", 16));
                    if (!buff[1].isEmpty()) {
                        this.fontRendererObj.drawStringWithShadow(buff[1], tooltipX + this.fontRendererObj.getStringWidth(buff[0]), tooltipY, Integer.parseInt("00ffff", 16));
                    }
                }*/

                if (lineNumber == 0) {
                    tooltipY += 2;
                }

                tooltipY += 10;
            }

            this.zLevel = 0.0F;
        }
    }
}
