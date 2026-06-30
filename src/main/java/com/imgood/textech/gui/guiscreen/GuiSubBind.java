package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.TileEntityTypeHelper;

/**
 * Display names / 显示名称:
 * - EN: Bind Target Block
 * - ZH: 绑定目标方块
 * Lang keys: adm.title.bind_target
 */
public class GuiSubBind extends ADM_GuiScreen {

    private final EntityPlayer player;
    private final World world;
    private final TileEntityAdvanceDataMonitor tileEntity;
    private int index;

    private ADM_GuiTextField textFieldX;
    private ADM_GuiTextField textFieldY;
    private ADM_GuiTextField textFieldZ;

    private String errorTips = "";

    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_ADM.png");
    private static final ResourceLocation BUTTON_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/button_hover_ADM.png");
    private static final ResourceLocation TEXTFIELD_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_ADM_8020.png");
    private static final ResourceLocation TEXTFIELD_HOVER_TEXTURE = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/textfield_hover_ADM_8020.png");
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        AdvanceDataMonitor.MODID,
        "textures/gui/background_ADM_Sub.png");

    private int offsetX, offsetY;

    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;

    public GuiSubBind(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity) {
        this.player = player;
        this.world = world;
        this.tileEntity = tileEntity;
        this.setBackgroundTexture(BACKGROUND);
        this.setSize(300, 150);
        this.setStretch(false);

    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        this.offsetX = this.width / 2 - 125;
        this.offsetY = this.height / 2 - 100;
        this.setPosition(this.offsetX, this.offsetY);

        textFieldX = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 45, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(TEXTFIELD_TEXTURE)
            .setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        textFieldY = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 125, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(TEXTFIELD_TEXTURE)
            .setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);
        textFieldZ = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 205, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(TEXTFIELD_TEXTURE)
            .setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);

        textFieldX.setMaxStringLength(10);
        textFieldY.setMaxStringLength(10);
        textFieldZ.setMaxStringLength(10);
        textFieldX.setFocused(true);

        this.buttonList.add(
            new ADM_GuiButton(0, this.offsetX + 80, this.offsetY + 100, 60, 20, I18n.format("adm.button.save"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor)
                .setUseHoverEffect(true));

        this.buttonList.add(
            new ADM_GuiButton(1, this.offsetX + 160, this.offsetY + 100, 60, 20, I18n.format("adm.button.cancel"))
                .setTexture(BUTTON_TEXTURE)
                .setHoverTexture(BUTTON_HOVER_TEXTURE)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor)
                .setUseHoverEffect(true));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 修正后的标题居中：面板中�?= offsetX + 面板宽度/2 (150)
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.bind_target"),
            this.offsetX + 160,
            this.offsetY - 10,
            0x00FFFF);

        this.fontRendererObj.drawString("X:", this.offsetX + 32, this.offsetY + 60, 0x00FFFF);
        this.fontRendererObj.drawString("Y:", this.offsetX + 112, this.offsetY + 60, 0x00FFFF);
        this.fontRendererObj.drawString("Z:", this.offsetX + 192, this.offsetY + 60, 0x00FFFF);

        if (!errorTips.isEmpty()) {
            this.fontRendererObj.drawString(errorTips, this.offsetX + 10, this.offsetY + 105, 0xFF0000);
        }

        // 绘制文本框背景（严格按照参考实现：使用文本框对象的纹理，固�?100x20 尺寸�?
        drawTextFieldBackground(textFieldX);
        drawTextFieldBackground(textFieldY);
        drawTextFieldBackground(textFieldZ);

        // 绘制文本内容
        textFieldX.drawTextBox();
        textFieldY.drawTextBox();
        textFieldZ.drawTextBox();
    }

    /**
     * 绘制单个文本框的背景�?
     * 完全模仿 GuiSubAEAdvanceCraftingLink 中的背景绘制逻辑�?
     * - 通过 textField.getTextFieldTexture() / getFocusedTextFieldTexture() 获取纹理
     * - 固定绘制尺寸�?100x20（与参考一致）
     */
    private void drawTextFieldBackground(ADM_GuiTextField textField) {
        int x = textField.xPosition;
        int y = textField.yPosition + 2; // 微调Y坐标，与参考实现对�?
        if (textField.isFocused()) {
            this.drawImage(textField.getFocusedTextFieldTexture(), x, y, 60, 20);
        } else {
            this.drawImage(textField.getTextFieldTexture(), x, y, 60, 20);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> { // 保存并打开配置界面
                int x, y, z;
                try {
                    x = Integer.parseInt(
                        textFieldX.getText()
                            .trim());
                    y = Integer.parseInt(
                        textFieldY.getText()
                            .trim());
                    z = Integer.parseInt(
                        textFieldZ.getText()
                            .trim());
                } catch (NumberFormatException e) {
                    errorTips = I18n.format("adm.error.invalid_coord");
                    return;
                }

                if (!world.blockExists(x, y, z)) {
                    errorTips = I18n.format("adm.error.no_block");
                    return;
                }

                TileEntity te = world.getTileEntity(x, y, z);
                if (te == null) {
                    errorTips = I18n.format("adm.error.not_tileentity");
                    return;
                }

                // 创建新数据条目并保存坐标
                int newIndex = tileEntity.getDisplayDataSize();
                net.minecraft.nbt.NBTTagCompound defaultNbt = tileEntity.getDataBound(newIndex);
                defaultNbt.setString("XYZ", x + "," + y + "," + z);
                tileEntity.setDisplayData(newIndex, defaultNbt);

                // 根据目标类型打开对应的详细配置界�?
                TileEntityTypeHelper.TileEntityType type = TileEntityTypeHelper.getTileEntityType(te);
                switch (type) {
                    case AE -> mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntity, newIndex));
                    case ADV_NETWORKLINK -> mc
                        .displayGuiScreen(new GuiSubAEAdvanceNetworkLink(player, world, tileEntity, newIndex));
                    case ADV_CRAFTINGLINK -> mc
                        .displayGuiScreen(new GuiSubAEAdvanceCraftingLink(player, world, tileEntity, newIndex));
                    case ADV_STORAGELINK -> mc
                        .displayGuiScreen(new GuiSubAEAdvanceStorageLink(player, world, tileEntity, newIndex));
                    default -> mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntity, newIndex));
                }
            }
            case 1 -> // 取消，返回主界面
                mc.displayGuiScreen(
                    new GuiMainAdvanceDataMonitor(player, world, tileEntity).setPosition(0, 0)
                        .setSize(200, 200)
                        .setStretch(true)
                        .setBackgroundTexture(
                            new ResourceLocation(
                                AdvanceDataMonitor.MODID,
                                "textures/gui/background_AdvanceDataMonitor_Main.png")));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        super.keyTyped(typedChar, keyCode);
        textFieldX.textboxKeyTyped(typedChar, keyCode);
        textFieldY.textboxKeyTyped(typedChar, keyCode);
        textFieldZ.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        textFieldX.mouseClicked(mouseX, mouseY, mouseButton);
        textFieldY.mouseClicked(mouseX, mouseY, mouseButton);
        textFieldZ.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        textFieldX.updateCursorCounter();
        textFieldY.updateCursorCounter();
        textFieldZ.updateCursorCounter();
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
