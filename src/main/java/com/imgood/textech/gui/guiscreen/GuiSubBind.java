package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.gui.framework.UiFeedbackArea;
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

    private int offsetX, offsetY;

    private int textColor = 0x00FFFF;
    private int textHoverColor = 0x0055FF;

    public GuiSubBind(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity) {
        this.player = player;
        this.world = world;
        this.tileEntity = tileEntity;
        this.setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        this.setSize(300, 190);
        this.setStretch(false);

    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        this.offsetX = this.width / 2 - 150;
        this.offsetY = this.height / 2 - 95;
        this.setPosition(this.offsetX, this.offsetY);

        textFieldX = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 45, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020)
            .setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020);
        textFieldY = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 125, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020)
            .setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020);
        textFieldZ = new ADM_GuiTextField(this.fontRendererObj, this.offsetX + 205, this.offsetY + 60, 60, 20)
            .setBackgroundTexture(AdmGuiTextures.TEXTFIELD_8020)
            .setFocusedBackgroundTexture(AdmGuiTextures.TEXTFIELD_HOVER_8020);

        textFieldX.setMaxStringLength(10);
        textFieldY.setMaxStringLength(10);
        textFieldZ.setMaxStringLength(10);
        textFieldX.setFocused(true);
        Runnable clearValidation = new Runnable() {

            @Override
            public void run() {
                errorTips = "";
            }
        };
        textFieldX.setOnTextChanged(clearValidation);
        textFieldY.setOnTextChanged(clearValidation);
        textFieldZ.setOnTextChanged(clearValidation);

        this.buttonList.add(
            new ADM_GuiButton(0, this.offsetX + 80, this.offsetY + 140, 60, 20, I18n.format("adm.button.save"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor)
                .setUseHoverEffect(true));

        this.buttonList.add(
            new ADM_GuiButton(1, this.offsetX + 160, this.offsetY + 140, 60, 20, I18n.format("adm.button.cancel"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseRGBEffect(false)
                .setTextColor(textColor)
                .setTextHoverColor(textHoverColor)
                .setUseHoverEffect(true));
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawAdmScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.bind_target"),
            this.offsetX + 150,
            this.offsetY + 15,
            0x00FFFF);

        this.fontRendererObj.drawString("X:", this.offsetX + 32, this.offsetY + 60, 0x00FFFF);
        this.fontRendererObj.drawString("Y:", this.offsetX + 112, this.offsetY + 60, 0x00FFFF);
        this.fontRendererObj.drawString("Z:", this.offsetX + 192, this.offsetY + 60, 0x00FFFF);

        if (!errorTips.isEmpty()) {
            new UiFeedbackArea(this.offsetX + 15, this.offsetY + 92, 270, 30)
                .draw(this.fontRendererObj, errorTips, 0xFF5555);
        }

        // 绘制文本内容
        textFieldX.drawTextBox();
        textFieldY.drawTextBox();
        textFieldZ.drawTextBox();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> { // 保存并打开配置界面
                beginValidation();
                int x, y, z;
                try {
                    x = Integer.parseInt(
                        textFieldX.getText()
                            .trim());
                } catch (NumberFormatException e) {
                    rejectField(textFieldX, I18n.format("adm.error.invalid_coord"));
                    return;
                }
                try {
                    y = Integer.parseInt(
                        textFieldY.getText()
                            .trim());
                } catch (NumberFormatException e) {
                    rejectField(textFieldY, I18n.format("adm.error.invalid_coord"));
                    return;
                }
                try {
                    z = Integer.parseInt(
                        textFieldZ.getText()
                            .trim());
                } catch (NumberFormatException e) {
                    rejectField(textFieldZ, I18n.format("adm.error.invalid_coord"));
                    return;
                }

                if (!world.blockExists(x, y, z)) {
                    rejectField(textFieldX, I18n.format("adm.error.no_block"));
                    return;
                }

                TileEntity te = world.getTileEntity(x, y, z);
                if (te == null) {
                    rejectField(textFieldX, I18n.format("adm.error.not_tileentity"));
                    return;
                }

                // 创建新数据条目并保存坐标
                int newIndex = tileEntity.findLowestFreeBindingIndex();
                if (newIndex < 0) {
                    rejectField(null, I18n.format(
                        "adm.error.data_bindings_full",
                        Integer.valueOf(TileEntityAdvanceDataMonitor.MAX_DATA_BINDINGS)));
                    return;
                }
                net.minecraft.nbt.NBTTagCompound defaultNbt = tileEntity.getDataBound(newIndex);
                defaultNbt.setString("XYZ", x + "," + y + "," + z);
                tileEntity.setDisplayData(newIndex, defaultNbt);

                // 根据目标类型打开对应的详细配置界面
                TileEntityTypeHelper.TileEntityType type = TileEntityTypeHelper.getTileEntityType(te);
                switch (type) {
                    case AE -> mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntity, newIndex));
                    case ADV_NETWORKLINK -> mc
                        .displayGuiScreen(new GuiSubLinkDisplayTypeSelect(player, world, tileEntity, newIndex));
                    default -> mc.displayGuiScreen(new GuiSubAdvanceDataMonitor(player, world, tileEntity, newIndex));
                }
            }
            case 1 -> // 取消，返回主界面
                mc.displayGuiScreen(
                    new GuiMainAdvanceDataMonitor(player, world, tileEntity).setPosition(0, 0)
                        .setSize(200, 200)
                        .setBackgroundTexture(AdmGuiTextures.BACKGROUND_MONITOR_MAIN));
        }
    }

    private void beginValidation() {
        errorTips = "";
        textFieldX.setInvalid(false);
        textFieldY.setInvalid(false);
        textFieldZ.setInvalid(false);
    }

    private void rejectField(ADM_GuiTextField field, String message) {
        errorTips = message != null ? message : "";
        if (field != null) {
            field.setInvalid(true);
            field.setFocused(true);
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
    protected void handleAdmMouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.handleAdmMouseClicked(mouseX, mouseY, mouseButton);
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
