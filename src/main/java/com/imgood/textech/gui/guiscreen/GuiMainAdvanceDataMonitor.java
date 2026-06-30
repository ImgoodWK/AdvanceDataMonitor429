package com.imgood.textech.gui.guiscreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.renders.RenderAdvanceDataMonitor;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.utils.TileEntityTypeHelper;

/**
 * Display names / 显示名称:
 * - EN: Advance Data Monitor (main config GUI)
 * - ZH: 高级数据监视器（主配置界面）
 * Lang keys: adm.title.main
 */
public class GuiMainAdvanceDataMonitor extends ADM_GuiScreen {

    private static final ResourceLocation button_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation button_hover_texture = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation button_texture_2020 = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM_2020.png");
    private static final ResourceLocation button_hover_texture_2020 = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM_2020.png");
    private final TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonitor;
    private final RenderAdvanceDataMonitor renderer = new RenderAdvanceDataMonitor();
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
    private int buttonRowYOffset1 = 100;
    private int buttonRowYOffset2 = 145;
    private boolean buttonRow1RGB = false;
    private boolean buttonRow2RGB = false;
    private int buttonRow1Width = 40;
    private int buttonRow2Width = 40;

    public GuiMainAdvanceDataMonitor(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity) {
        this.player = player;
        this.world = world;
        this.tileEntityAdvanceDataMonitor = tileEntity;
        this.facing = tileEntity.facing;
        this.displayDataSize = tileEntity.getDisplayDataSize();
        this.loadDataFromTileEntity();
    }

    public GuiMainAdvanceDataMonitor(TileEntityAdvanceDataMonitor tileEntityAdvanceDataMonitor) {
        this.tileEntityAdvanceDataMonitor = tileEntityAdvanceDataMonitor;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        this.currentFacing = getFacingString(this.tileEntityAdvanceDataMonitor.facing);

        this.offsetX = (this.width / 2) - 192;
        this.offsetY = (this.height / 2) - 90;
        this.updateScreen();
        this.buttonList.clear();
        this.setPosition(this.offsetX - 20, this.offsetY - 50);
        this.setSize(420, 260);
        this.setStretch(false);

        // 方向选择按钮（修改为单个循环按钮）
        this.buttonList.add(
            new ADM_GuiButton(
                100,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width, // 加宽按钮
                20,
                currentFacing) // 显示当前方向
                    .setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        // 用于打开坐标配置页面绑定坐标
        this.buttonList.add(
            new ADM_GuiButton(
                105,
                this.offsetX + 0,
                this.offsetY + buttonRowYOffset2,
                80,
                20,
                I18n.format("adm.button.bind")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));
        // 功能按钮（修改部分）
        /*
         * this.buttonList.add(
         * new ADM_GuiButton(
         * 105,
         * this.offsetX + 0,
         * this.offsetY + buttonRowYOffset2,
         * buttonRow1Width,
         * 20,
         * I18n.format("adm.button.add")).setTexture(button_texture)
         * .setHoverTexture(button_hover_texture)
         * .setUseRGBEffect(buttonRow1RGB)
         * .setUseHoverEffect(true)
         * .setLeftDecoration(button_hover_texture)
         * .setRightDecoration(button_hover_texture)
         * .setDecorationWidth(20)
         * .setTextColor(textColor)
         * .setTextHoverColor(textHoverColor));
         */

        /*
         * this.buttonList.add(
         * new ADM_GuiButton(
         * 104,
         * this.offsetX + 60,
         * this.offsetY + buttonRowYOffset2,
         * buttonRow1Width,
         * 20,
         * I18n.format("adm.button.add")).setTexture(button_texture)
         * .setHoverTexture(button_hover_texture)
         * .setUseRGBEffect(buttonRow1RGB)
         * .setUseHoverEffect(true)
         * .setLeftDecoration(button_hover_texture)
         * .setRightDecoration(button_hover_texture)
         * .setDecorationWidth(20)
         * .setTextColor(textColor)
         * .setTextHoverColor(textHoverColor));
         */

        this.buttonList.add(
            new ADM_GuiButton(
                108,
                this.offsetX + 120,
                this.offsetY + buttonRowYOffset2,
                buttonRow1Width,
                20,
                I18n.format("adm.button.add")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        /*
         * this.buttonList.add(
         * new ADM_GuiButton(
         * 107,
         * this.offsetX + 180,
         * this.offsetY + buttonRowYOffset2,
         * buttonRow1Width,
         * 20,
         * I18n.format("adm.button.add")).setTexture(button_texture)
         * .setHoverTexture(button_hover_texture)
         * .setUseRGBEffect(buttonRow1RGB)
         * .setUseHoverEffect(true)
         * .setLeftDecoration(button_hover_texture)
         * .setRightDecoration(button_hover_texture)
         * .setDecorationWidth(20)
         * .setTextColor(textColor)
         * .setTextHoverColor(textHoverColor));
         */

        this.buttonList.add(
            new ADM_GuiButton(
                106,
                this.offsetX + 120,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.hide")).setTexture(button_texture)
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
                107,
                this.offsetX + 158,
                this.offsetY + buttonRowYOffset1,
                45,
                20,
                this.tileEntityAdvanceDataMonitor.isVisableScreen() ? I18n.format("adm.button.hide")
                    : I18n.format("adm.button.show")).setTexture(button_texture)
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
                109,
                this.offsetX + 205,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                this.tileEntityAdvanceDataMonitor.isRenderBothSides() ? I18n.format("adm.button.both")
                    : I18n.format("adm.button.single")).setTexture(button_texture)
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
                110,
                this.offsetX + 265,
                this.offsetY + buttonRowYOffset1,
                buttonRow1Width,
                20,
                I18n.format("adm.button.ai")).setTexture(button_texture)
                    .setHoverTexture(button_hover_texture)
                    .setUseRGBEffect(buttonRow1RGB)
                    .setUseHoverEffect(true)
                    .setLeftDecoration(button_hover_texture)
                    .setRightDecoration(button_hover_texture)
                    .setDecorationWidth(20)
                    .setTextColor(textColor)
                    .setTextHoverColor(textHoverColor));

        addButtonsForExistingData(this.displayDataSize, 20, 20, 12, this.offsetX + 5, this.offsetY - 10, 10, 10);
        refreshButtons();
    }

    // 新增方法：根据方向值获取对应的本地化字符串
    private String getFacingString(int facing) {
        return switch (facing) {
            case 0 -> I18n.format("adm.direction.south");
            case 1 -> I18n.format("adm.direction.west");
            case 2 -> I18n.format("adm.direction.north");
            default -> I18n.format("adm.direction.east");
        };
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        NBTTagCompound nbt = new NBTTagCompound();
        AdvanceDataMonitor.LOG.info("button.id: {}", button.id);
        switch (button.id) {

            case 100 -> {
                // 循环切换方向：0->1->2->3->0...
                int newFacing = (this.tileEntityAdvanceDataMonitor.facing + 1) % 4;
                this.tileEntityAdvanceDataMonitor.setFacing(newFacing);
                this.currentFacing = getFacingString(newFacing);

                nbt.setInteger("facing", newFacing);
                this.tileEntityAdvanceDataMonitor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonitor.xCoord,
                        tileEntityAdvanceDataMonitor.yCoord,
                        tileEntityAdvanceDataMonitor.zCoord,
                        nbt));

                // 更新按钮文本
                button.displayString = this.currentFacing;
                refreshButtons();
            }
            case 105 -> {
                mc.displayGuiScreen(new GuiSubBind(this.player, this.world, this.tileEntityAdvanceDataMonitor));
            }
            /*
             * case 105 -> {
             * if (this.displayDataSize <= 35) {
             * openSubMenu();
             * } else {
             * mc.displayGuiScreen(
             * new GuiScreenMessage(
             * this.player,
             * this.world,
             * GuiScreenMessage.MessageType.WARNING,
             * I18n.format("adm.error.max_data"),
             * this));
             * }
             * }
             */
            /*
             * case 104 -> {
             * if (this.displayDataSize <= 35) {
             * openSubAEMenu();
             * } else {
             * mc.displayGuiScreen(
             * new GuiScreenMessage(
             * this.player,
             * this.world,
             * GuiScreenMessage.MessageType.WARNING,
             * I18n.format("adm.error.max_data"),
             * this));
             * }
             * }
             */
            case 106 -> {
                boolean visableBody = button.displayString.equals(I18n.format("adm.button.show"));
                this.tileEntityAdvanceDataMonitor.setVisableBody(visableBody);
                this.tileEntityAdvanceDataMonitor.writeToNBT(nbt);
                // 同步到服务器
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonitor.xCoord,
                        tileEntityAdvanceDataMonitor.yCoord,
                        tileEntityAdvanceDataMonitor.zCoord,
                        nbt));
                refreshButtons();
            }
            case 107 -> {
                boolean visableScreen = button.displayString.equals(I18n.format("adm.button.show"));
                this.tileEntityAdvanceDataMonitor.setVisableScreen(visableScreen);
                nbt.setBoolean("visableScreen", visableScreen);
                this.tileEntityAdvanceDataMonitor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonitor.xCoord,
                        tileEntityAdvanceDataMonitor.yCoord,
                        tileEntityAdvanceDataMonitor.zCoord,
                        nbt));
                refreshButtons();
            }
            case 109 -> {
                boolean bothSides = !this.tileEntityAdvanceDataMonitor.isRenderBothSides();
                this.tileEntityAdvanceDataMonitor.setRenderBothSides(bothSides);
                nbt.setBoolean("renderBothSides", bothSides);
                this.tileEntityAdvanceDataMonitor.writeToNBT(nbt);
                AdvanceDataMonitor.ADMCHANEL.sendToServer(
                    new PacketSynTileEntity(
                        tileEntityAdvanceDataMonitor.xCoord,
                        tileEntityAdvanceDataMonitor.yCoord,
                        tileEntityAdvanceDataMonitor.zCoord,
                        nbt));
                refreshButtons();
            }
            case 110 -> {
                mc.displayGuiScreen(new GuiAIChat(this));
                return;
            }
            /*
             * case 108 -> {
             * mc.displayGuiScreen(
             * new GuiSubAEAdvanceNetworkLink(
             * this.player,
             * this.world,
             * this.tileEntityAdvanceDataMonitor,
             * index));
             * }
             */

            default -> {
                if (button.id <= 36) {
                    if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
                        openSubColorConfigMenu(button.id);
                    } else {
                        openSubMenu(button.id);
                    }

                }
            }
        }

        tileEntityAdvanceDataMonitor.syncData();
        tileEntityAdvanceDataMonitor.markDirty();
    }

    public void addButtonsForExistingData(int displayDataSize, int buttonWidth, int buttonHeight, int maxButtonsPerRow,
        int offsetX, int offsetY, int xSpacing, int ySpacing) {
        for (int i = 0; i < displayDataSize; i++) {
            int x = offsetX + (i % maxButtonsPerRow) * (buttonWidth + xSpacing);
            int y = offsetY + (i / maxButtonsPerRow) * (buttonHeight + ySpacing);
            this.buttonList.add(
                new ADM_GuiButton(i, x, y, buttonWidth, buttonHeight, String.valueOf(i + 1))
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
        mc.displayGuiScreen(
            new GuiSubAdvanceDataMonitor(
                this.player,
                this.world,
                this.tileEntityAdvanceDataMonitor,
                this.displayDataSize));
    }

    private void openSubMenu(int index) {
        BlockPos pos = new BlockPos(tileEntityAdvanceDataMonitor.getXYZ(index), this.world);
        TileEntityTypeHelper.TileEntityType type = TileEntityTypeHelper.getTileEntityType(pos);
        switch (type) {
            case AE -> mc
                .displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntityAdvanceDataMonitor, index));
            case ADV_NETWORKLINK -> mc
                .displayGuiScreen(new GuiSubAEAdvanceNetworkLink(player, world, tileEntityAdvanceDataMonitor, index));
            case ADV_CRAFTINGLINK -> mc
                .displayGuiScreen(new GuiSubAEAdvanceCraftingLink(player, world, tileEntityAdvanceDataMonitor, index));
            case ADV_STORAGELINK -> mc
                .displayGuiScreen(new GuiSubAEAdvanceStorageLink(player, world, tileEntityAdvanceDataMonitor, index));
            default -> mc
                .displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntityAdvanceDataMonitor, index));
        }
    }

    private void openSubColorConfigMenu(int index) {
        mc.displayGuiScreen(new GuiSubColorConfig(this.player, this.world, this.tileEntityAdvanceDataMonitor, index));
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
        // 更新方向按钮文本
        for (GuiButton guiButton : this.buttonList) {
            if (guiButton.id == 100) {
                guiButton.displayString = this.currentFacing;
                break;
            }
        }

        // 更新其他按钮状态
        for (GuiButton guiButton : this.buttonList) {
            switch (guiButton.id) {
                case 106 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonitor.isVisableBody()
                        ? I18n.format("adm.button.hide")
                        : I18n.format("adm.button.show");
                }
                case 107 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonitor.isVisableScreen()
                        ? I18n.format("adm.button.hide")
                        : I18n.format("adm.button.show");
                }
                case 109 -> {
                    guiButton.displayString = this.tileEntityAdvanceDataMonitor.isRenderBothSides()
                        ? I18n.format("adm.button.both")
                        : I18n.format("adm.button.single");
                }
            }
        }
    }

    private void loadDataFromTileEntity() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.tileEntityAdvanceDataMonitor.writeToNBT(nbt);
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
        this.tileEntityAdvanceDataMonitor.readFromNBT(nbt);
    }

    private boolean isMouseOverButton(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.xPosition && mouseX < button.xPosition + button.width
            && mouseY >= button.yPosition
            && mouseY < button.yPosition + button.height;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 标题和标签（修改部分）
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.main"),
            this.offsetX + 192,
            this.offsetY - 40,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.facing"),
            this.offsetX + 20,
            this.offsetY + 88,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.data"),
            this.offsetX + 20,
            this.offsetY + 130,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.aestorage"),
            this.offsetX + 85,
            this.offsetY + 130,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.body"),
            this.offsetX + 135,
            this.offsetY + 88,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.screen"),
            this.offsetX + 168,
            this.offsetY + 88,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.back"),
            this.offsetX + 220,
            this.offsetY + 88,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.aenetwork"),
            this.offsetX + 135,
            this.offsetY + 130,
            this.textColor);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.label.player"),
            this.offsetX + 200,
            this.offsetY + 130,
            this.textColor);

        // 工具提示（修改部分）
        for (Object buttonObj : this.buttonList) {
            GuiButton button = (GuiButton) buttonObj;
            if (button.id < this.displayDataSize && isMouseOverButton(button, mouseX, mouseY)) {
                List<String> tooltipData = new ArrayList<>();
                tooltipData.add(
                    String.format(
                        "%s: %s",
                        I18n.format("adm.tooltip.position"),
                        tileEntityAdvanceDataMonitor.getXYZ(button.id)));
                tooltipData.add(
                    String.format(
                        "%s: %s",
                        I18n.format("adm.tooltip.displayname"),
                        tileEntityAdvanceDataMonitor.getEnable(button.id)
                            ? tileEntityAdvanceDataMonitor.getDisplayName(button.id)
                            : "§m" + tileEntityAdvanceDataMonitor.getDisplayName(button.id)));;
                tooltipData.add(
                    String.format(
                        "%s: %s",
                        I18n.format("adm.tooltip.datatype"),
                        tileEntityAdvanceDataMonitor.getDataType(button.id)));
                tooltipData.add(
                    String.format(
                        "%s: %s",
                        I18n.format("adm.tooltip.dataname"),
                        tileEntityAdvanceDataMonitor.getName(button.id)));
                tooltipData.add(
                    String.format(
                        "%s",
                        tileEntityAdvanceDataMonitor.getEnableAxis(button.id) ? I18n.format("adm.tooltip.enableAxis")
                            : "§m" + I18n.format("adm.tooltip.enableAxis")));
                tooltipData.add(
                    String.format(
                        "%s",
                        tileEntityAdvanceDataMonitor.getEnableData(button.id) ? I18n.format("adm.tooltip.enableData")
                            : "§m" + I18n.format("adm.tooltip.enableData")));
                tooltipData.add(
                    String.format(
                        "%s",
                        tileEntityAdvanceDataMonitor.getEnableAxisFont(button.id)
                            ? I18n.format("adm.tooltip.enableAxisFont")
                            : "§m" + I18n.format("adm.tooltip.enableAxisFont")));
                drawColoredHoveringText(tooltipData, mouseX, mouseY, button.id);
                break;
            }
        }

        // 调试信息（修改部分）
        String mousePos = String.format("%s: %d, %d", I18n.format("adm.debug.mouse_position"), mouseX, mouseY);
        int x = 10;
        int y = this.height - 20;
        this.fontRendererObj.drawString(mousePos, x, y, 0x00ffff);
    }

    private void drawColoredHoveringText(List<String> textLines, int x, int y, int buttonId) {
        if (textLines == null || textLines.isEmpty()) return;

        int tooltipTextWidth = 0;
        int tooltipHeight = 0;
        int maxLineWidth = 200;

        for (String line : textLines) {
            int lineWidth = this.fontRendererObj.getStringWidth(line);
            tooltipTextWidth = Math.max(tooltipTextWidth, Math.min(lineWidth, maxLineWidth));
            tooltipHeight += 10;
        }

        tooltipHeight += 4;
        tooltipTextWidth += 8;

        int tooltipX = x + 12;
        int tooltipY = y - 12;

        if (tooltipX + tooltipTextWidth > this.width) {
            tooltipX -= 28 + tooltipTextWidth;
        }
        if (tooltipY + tooltipHeight + 6 > this.height) {
            tooltipY = this.height - tooltipHeight - 6;
        }

        this.zLevel = 300.0F;
        drawRect(tooltipX - 3, tooltipY - 3, tooltipX + tooltipTextWidth + 3, tooltipY + tooltipHeight + 3, 0xaa00ffff);
        drawRect(tooltipX - 2, tooltipY - 2, tooltipX + tooltipTextWidth + 2, tooltipY + tooltipHeight + 2, 0x80000000);
        this.zLevel = 301.0F;

        int currentY = tooltipY;
        int lineCount = 0;
        for (String line : textLines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                String prefix = line.substring(0, colonIndex + 1)
                    .trim();
                String content = line.substring(colonIndex + 1)
                    .trim();

                // 获取显示名称前缀（包含冒号）
                String displayNamePrefix = I18n.format("adm.tooltip.displayname") + ":";

                this.fontRendererObj.drawStringWithShadow(prefix, tooltipX, currentY, 0x00FFFF);

                // 计算内容颜色
                int contentColor = 0xFFFFFF; // 默认白色
                if (prefix.equals(displayNamePrefix)) {
                    // 从TileEntity获取显示名称颜色
                    contentColor = Integer.parseInt(tileEntityAdvanceDataMonitor.getDisplayNameColor(buttonId), 16);
                }

                this.fontRendererObj.drawStringWithShadow(
                    "§n" + content,
                    tooltipX + this.fontRendererObj.getStringWidth(prefix) + 2,
                    currentY,
                    contentColor // 使用动态颜色
                );
                // 绘制内容部分（带下划线）
                if (tileEntityAdvanceDataMonitor.getEnable(buttonId)) {
                    this.fontRendererObj.drawStringWithShadow(
                        "§n" + content,
                        tooltipX + this.fontRendererObj.getStringWidth(prefix) + 2,
                        currentY,
                        contentColor // 使用动态颜色
                    );
                } else {
                    if (lineCount == 1) {
                        this.fontRendererObj.drawStringWithShadow(
                            "§n§m" + content,
                            tooltipX + this.fontRendererObj.getStringWidth(prefix) + 2,
                            currentY,
                            0xff0000 // 使用动态颜色
                        );
                    }
                }

            } else {
                this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0x00FFFF);
                switch (lineCount) {
                    case 4:
                        if (!tileEntityAdvanceDataMonitor.getEnableAxis(buttonId)) {
                            this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0xFF0000);
                        }
                        break;
                    case 5:
                        if (!tileEntityAdvanceDataMonitor.getDataBound(buttonId)
                            .getBoolean("enableData")) {
                            this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0xFF0000);
                        }
                        break;
                    case 6:
                        if (!tileEntityAdvanceDataMonitor.getDataBound(buttonId)
                            .getBoolean("enableAxisFont")) {
                            this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0xFF0000);
                        }
                        break;
                    default:
                        this.fontRendererObj.drawStringWithShadow(line, tooltipX, currentY, 0x00FFFF);
                        break;
                }
            }
            currentY += 10;
            lineCount++;
        }

        this.zLevel = 0.0F;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void renderDataPreview(int x, int y, int width, int height) {
        // 保存当前OpenGL状态
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        // 设置渲染区域
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, this.height - y - height, width, height);

        // 设置正交投影
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, this.width, this.height, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        // 配置渲染环境
        GL11.glTranslated(x + width / 2, y + height / 2, 0);
        GL11.glScalef(40, 40, 40);
        GL11.glRotatef(180, 0, 0, 1);
        GL11.glRotatef(30, 1, 0, 0);
        GL11.glRotatef(-45, 0, 1, 0);

        // 创建虚拟TileEntity
        TileEntityAdvanceDataMonitor virtualTE = new TileEntityAdvanceDataMonitor();
        virtualTE.setFacing(this.facing);
        virtualTE.getDataBoundList()
            .putAll(tileEntityAdvanceDataMonitor.getDataBoundList());

        // 实际渲染
        renderer.renderTileEntityAt(virtualTE, 0, 0, 0, 0);

        // 恢复OpenGL状态
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopAttrib();
        GL11.glPopMatrix();

        // 绘制边框
        drawRect(x, y, x + width, y + height, 0xFF00FFFF);
    }

}
