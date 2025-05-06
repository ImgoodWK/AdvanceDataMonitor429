package com.imgood.advancedatamonitor.gui.guiscreen;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.costom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonotor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static com.imgood.advancedatamonitor.utils.ContentsHelper.wrapText;

public class GuiMainAdvanceDataMonitor extends ADM_GuiScreen {
    private final TileEntityAdvanceDataMonotor tileEntityAdvanceDataMonotor;
    private EntityPlayer player;
    private World world;
    private int index = 0;
    private List<GuiTextField> textFieldsLeft = new ArrayList<>();
    private List<GuiTextField> textFieldsRight = new ArrayList<>();
    private List<NBTTagCompound> dataList = new ArrayList<>();
    private List<String> tooltipLines = new ArrayList<>();
    private GuiTextField hoveredTextField;
    private int facing;
    private String currentFacing;
    private int offsetX = 100;
    private int offsetY = 100;
    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;
    private int displayDataSize;

    private static final ResourceLocation button_texture = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/button_ADM.png");
    private static final ResourceLocation button_hover_texture = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation button_texture_2020 = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/button_ADM_2020.png");
    private static final ResourceLocation button_hover_texture_2020 = new ResourceLocation(AdvanceDataMonitor.MODID, "textures/gui/button_hover_ADM_2020.png");

    private int buttonRowYOffset1 = 100;
    private int buttonRowYOffset2 = 145;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 40;
    private int buttonRow2Width = 40;

    public GuiMainAdvanceDataMonitor(EntityPlayer player, World world, TileEntityAdvanceDataMonotor tileEntity) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonotor = tileEntity;
        this.facing = tileEntity.facing;
        this.displayDataSize = tileEntity.getDisplayDataSize();
        this.loadDataFromTileEntity();
    }

    public GuiMainAdvanceDataMonitor(TileEntityAdvanceDataMonotor tileEntityAdvanceDataMonotor) {
        this.tileEntityAdvanceDataMonotor = tileEntityAdvanceDataMonotor;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        this.currentFacing = switch (this.tileEntityAdvanceDataMonotor.facing) {
            case 0 -> "South";
            case 1 -> "West";
            case 2 -> "North";
            default -> "East";
        };

        this.offsetX = (this.width / 2) - 192;
        this.offsetY = (this.height / 2) - 90;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 50);
        this.setSize(420, 260);
        this.setStretch(false);
        //方向选择按钮
        this.buttonList.add(new ADM_GuiButton(100,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset1,
                buttonRow2Width,
                20,
                "North")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(101,
                this.offsetX + 60,
                this.offsetY + buttonRowYOffset1,
                buttonRow2Width,
                20,
                "East")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(102,
                this.offsetX + 120,
                this.offsetY + buttonRowYOffset1,
                buttonRow2Width,
                20,
                "West")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(103,
                this.offsetX + 180,
                this.offsetY + buttonRowYOffset1,
                buttonRow2Width,
                20,
                "South")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));
        this.buttonList.add(new ADM_GuiButton(105,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset2,
                buttonRow1Width, 20, "Add")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(new ADM_GuiButton(104,
                this.offsetX + 60,
                this.offsetY + buttonRowYOffset2,
                buttonRow1Width, 20, "Hide")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(new ADM_GuiButton(106,
                this.offsetX + 120,
                this.offsetY + buttonRowYOffset2,
                buttonRow1Width, 20, "Hide")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        this.buttonList.add(new ADM_GuiButton(107,
                this.offsetX + 180,
                this.offsetY + buttonRowYOffset2,
                buttonRow1Width, 20, "Single")
                .setTexture(button_texture)
                .setHoverTexture(button_hover_texture)
                .setUseRGBEffect(buttonRow1RGB)
                .setUseHoverEffect(true)
                .setLeftDecoration(button_hover_texture)
                .setRightDecoration(button_hover_texture)
                .setDecorationWidth(20)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor));

        addButtonsForExistingData(this.displayDataSize,
                20, 20, 12,
                this.offsetX + 5, this.offsetY - 10,
                10, 10);
        refreshButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound nbt = new NBTTagCompound();

        switch (button.id) {
            case 100 -> {
                // 设置朝向并准备NBT数据
                this.tileEntityAdvanceDataMonotor.setFacing(2);
                this.currentFacing = "North";

                // 将数据写入NBT
                nbt.setInteger("facing", 2);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);

                // 发送同步包
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                        new PacketSynTileEntity(
                                tileEntityAdvanceDataMonotor.xCoord,
                                tileEntityAdvanceDataMonotor.yCoord,
                                tileEntityAdvanceDataMonotor.zCoord,
                                nbt
                        )
                );

                initGui();
                refreshButtons();
            }
            case 101 -> {
                this.tileEntityAdvanceDataMonotor.setFacing(3);
                this.currentFacing = "East";

                nbt.setInteger("facing", 3);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);

                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                        new PacketSynTileEntity(
                                tileEntityAdvanceDataMonotor.xCoord,
                                tileEntityAdvanceDataMonotor.yCoord,
                                tileEntityAdvanceDataMonotor.zCoord,
                                nbt
                        )
                );

                initGui();
                refreshButtons();
            }
            case 102 -> {
                this.tileEntityAdvanceDataMonotor.setFacing(1);
                this.currentFacing = "West";

                nbt.setInteger("facing", 1);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);

                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                        new PacketSynTileEntity(
                                tileEntityAdvanceDataMonotor.xCoord,
                                tileEntityAdvanceDataMonotor.yCoord,
                                tileEntityAdvanceDataMonotor.zCoord,
                                nbt
                        )
                );

                initGui();
                refreshButtons();
            }
            case 103 -> {
                this.tileEntityAdvanceDataMonotor.setFacing(0);
                this.currentFacing = "South";

                nbt.setInteger("facing", 0);
                this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);

                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                        new PacketSynTileEntity(
                                tileEntityAdvanceDataMonotor.xCoord,
                                tileEntityAdvanceDataMonotor.yCoord,
                                tileEntityAdvanceDataMonotor.zCoord,
                                nbt
                        )
                );

                initGui();
                refreshButtons();
            }
            case 105 -> {
                if (this.displayDataSize <= 35) {
                    openSubMenu();
                } else {
                    mc.displayGuiScreen(new GuiScreenMessage(this.player, this.world, GuiScreenMessage.MessageType.WARNING, "Maximum number of data reached.", this));
                }
            }
            case 104 -> {
                boolean visableBody = button.displayString.equals("Show");
                this.tileEntityAdvanceDataMonotor.setVisableBody(visableBody);
                refreshButtons();
            }
            case 106 -> {
                boolean visableScreen = button.displayString.equals("Show");
                this.tileEntityAdvanceDataMonotor.setVisableScreen(visableScreen);
                refreshButtons();
            }
            case 107 -> {
                this.tileEntityAdvanceDataMonotor.setVisableBack(!this.tileEntityAdvanceDataMonotor.isVisableBack());
                button.displayString = this.tileEntityAdvanceDataMonotor.isVisableBack() ? "Single" : "Both";
                refreshButtons();
            }
            default -> {
                if (button.id <= 36) {
                    openSubMenu(button.id);
                }
            }
        }

        tileEntityAdvanceDataMonotor.syncData();
        tileEntityAdvanceDataMonotor.markDirty();
    }

    public void addButtonsForExistingData(int displayDataSize, int buttonWidth, int buttonHeight, int maxButtonsPerRow, int offsetX, int offsetY, int xSpacing, int ySpacing) {
        for (int i = 0; i < displayDataSize; i++) {
            int x = offsetX + (i % maxButtonsPerRow) * (buttonWidth + xSpacing);
            int y = offsetY + (i / maxButtonsPerRow) * (buttonHeight + ySpacing);
            this.buttonList.add(new ADM_GuiButton(i, x, y, buttonWidth, buttonHeight, String.valueOf(i + 1))
                    .setTexture(button_texture_2020)
                    .setHoverTexture(button_hover_texture_2020)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        }
    }

    private void openSubMenu() {
        mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor, this.displayDataSize));
    }

    private void openSubMenu(int index) {
        mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(this.player, this.world, this.tileEntityAdvanceDataMonotor, index));
    }

    public void onNewDataSaved(NBTTagCompound newData) {
        this.dataList.add(newData);
        saveDataToTileEntity();
        initGui();
    }

    public void onDataEdited(int index, NBTTagCompound editedData) {
        dataList.set(index, editedData);
        saveDataToTileEntity();
    }

    public void refreshButtons() {
        for (GuiButton guiButton : this.buttonList) {
            if (guiButton.displayString.equals(currentFacing)) {
                guiButton.enabled = false;
            } else {
                guiButton.enabled = true;
            }
        }
        for (GuiButton guiButton : this.buttonList) {
            switch (guiButton.id) {
                case 104 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonotor.isVisableBody() ? "Hide" : "Show";
                }
                case 106 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonotor.isVisableScreen() ? "Hide" : "Show";
                }
                case 107 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonotor.isVisableBack() ? "Single" : "Both";
                }
            }
        }
    }

    private void loadDataFromTileEntity() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.tileEntityAdvanceDataMonotor.writeToNBT(nbt);
        int count = nbt.getInteger("DataCount");
        for (int i = 0; i < count; i++) {
            dataList.add(nbt.getCompoundTag("Data" + i));
        }
    }

    private void saveDataToTileEntity() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("DataCount", dataList.size());
        for (int i = 0; i < dataList.size(); i++) {
            nbt.setTag("Data" + i, dataList.get(i));
        }
        this.tileEntityAdvanceDataMonotor.readFromNBT(nbt);
    }

    private boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseX < button.xPosition + button.width &&
                mouseY >= button.yPosition && mouseY < button.yPosition + button.height;
    }

    private void drawTooltipBackground(int x, int y, int width, int height) {
        int borderSize = 0;

        // 绑定背景材质
        //this.mc.getTextureManager().bindTexture(new ResourceLocation("modid", "textures/gui/tooltipsBackground.png"));
        // 绘制背景，绘制一个纯色或半透明矩形
        drawRect(x, y, x + width, y + height, 0xaf00aaaa); // 半透明黑色背景


        drawTexturedModalRect(x - borderSize, y - borderSize, 0, 0, borderSize, borderSize);
        drawTexturedModalRect(x + width, y - borderSize, 16 - borderSize, 0, borderSize, borderSize);
        drawTexturedModalRect(x - borderSize, y + height, 0, 16 - borderSize, borderSize, borderSize);
        drawTexturedModalRect(x + width, y + height, 16 - borderSize, 16 - borderSize, borderSize, borderSize);

    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRendererObj, "§lAdvance Data Monitor", this.offsetX + 192, this.offsetY - 40, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "Facing", this.offsetX + 110, this.offsetY + 85, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "Data", this.offsetX + 20, this.offsetY + 130, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "Body", this.offsetX + 85, this.offsetY + 130, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "Screen", this.offsetX + 135, this.offsetY + 130, this.textColor);
        this.drawCenteredString(this.fontRendererObj, "Back", this.offsetX + 200, this.offsetY + 130, this.textColor);

        // 新增的悬停提示部分
        for (Object buttonObj : this.buttonList) {
            GuiButton button = (GuiButton) buttonObj;
            if (button.id < this.displayDataSize && isMouseOverButton(button, mouseX, mouseY)) {
                List<String> tooltipData = new ArrayList<>();
                // 添加显示名称
                tooltipData.add("DisplayName: " + tileEntityAdvanceDataMonotor.getDisplayName(button.id));
                // 添加坐标信息
                tooltipData.add("Position: " + tileEntityAdvanceDataMonotor.getXYZ(button.id));
                // 添加数据类型
                tooltipData.add("DataType: " + tileEntityAdvanceDataMonotor.getDataType(button.id));
                tooltipData.add("DataName: " + tileEntityAdvanceDataMonotor.getName(button.id));
                drawColoredHoveringText(tooltipData, mouseX, mouseY, button.id);
                break;
            }
        }

        // 调试用的鼠标坐标显示（可保留）
        String mousePos = String.format("Mouse Position: %d, %d", mouseX, mouseY);
        int x = 10;
        int y = this.height - 20;
        this.fontRendererObj.drawString(mousePos, x, y, 0x00ffff);
    }


    private void drawColoredHoveringText(List<String> textLines, int x, int y, int buttonId) {
        if (textLines == null || textLines.isEmpty()) return;

        int tooltipTextWidth = 0;
        int tooltipHeight = 8;
        int maxLineWidth = 200;

        // 计算提示框尺寸
        for (String line : textLines) {
            int lineWidth = this.fontRendererObj.getStringWidth(line);
            tooltipTextWidth = Math.max(tooltipTextWidth, Math.min(lineWidth, maxLineWidth));
            tooltipHeight += 10;
        }

        // 位置调整
        int tooltipX = x + 12;
        int tooltipY = y - 12;
        tooltipTextWidth += 8;
        tooltipHeight += 2;

        if (tooltipX + tooltipTextWidth > this.width) {
            tooltipX -= 28 + tooltipTextWidth;
        }
        if (tooltipY + tooltipHeight + 6 > this.height) {
            tooltipY = this.height - tooltipHeight - 6;
        }

        // 绘制蓝色背景
        this.zLevel = 300.0F;
        drawRect(tooltipX - 3, tooltipY - 3,
                tooltipX + tooltipTextWidth + 3,
                tooltipY + tooltipHeight + 3,
                // 半透明蓝背景
                0xaf00aaaa);
        drawRect(tooltipX - 2, tooltipY - 2,
                tooltipX + tooltipTextWidth + 2,
                tooltipY + tooltipHeight + 2,
                // 黑色边框
                0x80000000);

        // 绘制文字
        this.zLevel = 301.0F;
        int currentY = tooltipY;
        for (String line : textLines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                // 分割标签和内容
                String prefix = line.substring(0, colonIndex + 1);
                String content = line.substring(colonIndex + 1).trim();

                // 绘制蓝色标签
                this.fontRendererObj.drawStringWithShadow(
                        prefix,
                        tooltipX,
                        currentY,
                        0x00FFFF
                );

                // 绘制白色带下划线内容
                this.fontRendererObj.drawStringWithShadow(
                        "§n" + content,
                        tooltipX + this.fontRendererObj.getStringWidth(prefix) + 2,
                        currentY,
                        0xFFFFFF
                );
            } else {
                // 无冒号的普通文本
                this.fontRendererObj.drawStringWithShadow(
                        line,
                        tooltipX,
                        currentY,
                        0x00FFFF
                );
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