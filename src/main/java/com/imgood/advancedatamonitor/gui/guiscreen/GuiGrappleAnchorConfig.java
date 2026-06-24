package com.imgood.advancedatamonitor.gui.guiscreen;

import static com.imgood.advancedatamonitor.utils.ContentsHelper.isValidHexColor;
import static com.imgood.advancedatamonitor.utils.ContentsHelper.parseHexColorOrDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiButton;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiScreen;
import com.imgood.advancedatamonitor.gui.custom.ADM_GuiTextField;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleAnchorConfig;
import com.imgood.advancedatamonitor.tileentity.TileEntityGrappleAnchor;

/**
 * Display names / 显示名称:
 * - EN: Grapple Anchor Settings
 * - ZH: 挂索节点设置
 * Lang keys: adm.title.grappleAnchorConfig
 */
public class GuiGrappleAnchorConfig extends ADM_GuiScreen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(

        AdvanceDataMonitor.MODID,

        "textures/gui/background_ADM_Sub.png");

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

    private final int anchorX;

    private final int anchorY;

    private final int anchorZ;

    private final EntityPlayer player;

    private final World world;

    private ADM_GuiTextField nameField;

    private ADM_GuiTextField colorField;

    private String errorTips = "";

    private int buttonSaveId = 0;

    private int buttonCancelId = 1;

    public GuiGrappleAnchorConfig(EntityPlayer player, World world, int x, int y, int z) {

        this.player = player;

        this.world = world;

        this.anchorX = x;

        this.anchorY = y;

        this.anchorZ = z;

        this.setBackgroundTexture(BACKGROUND_TEXTURE);

        this.setSize(360, 200);

        this.setStretch(false);

    }

    @Override

    public void initGui() {

        Keyboard.enableRepeatEvents(true);

        this.setPosition((this.width - 360) / 2, (this.height - 200) / 2);

        this.buttonList.clear();

        int centerX = this.width / 2;

        int centerY = this.height / 2;

        TileEntityGrappleAnchor anchor = TileEntityGrappleAnchor.get(world, anchorX, anchorY, anchorZ);

        nameField = new ADM_GuiTextField(this.fontRendererObj, centerX + 20, centerY - 28, 140, 20);

        nameField.setMaxStringLength(32);

        nameField.setBackgroundTexture(TEXTFIELD_TEXTURE);

        nameField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);

        nameField.setHintText(I18n.format("adm.hint.grapple.node_name"));

        nameField.setText(anchor != null ? anchor.getDisplayName() : "");

        nameField.setFocused(true);

        colorField = new ADM_GuiTextField(this.fontRendererObj, centerX + 20, centerY + 2, 80, 20);

        colorField.setMaxStringLength(6);

        colorField.setBackgroundTexture(TEXTFIELD_TEXTURE);

        colorField.setFocusedBackgroundTexture(TEXTFIELD_HOVER_TEXTURE);

        colorField.setHintText(I18n.format("adm.hint.displaycolor"));

        colorField.setText(

            anchor != null ? TileEntityGrappleAnchor.colorToHex(anchor.getIconCursorColor())

                : TileEntityGrappleAnchor.colorToHex(TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR));

        this.buttonList.add(

            new ADM_GuiButton(buttonSaveId, centerX - 60, centerY + 52, 50, 20, I18n.format("adm.button.save"))

                .setTexture(BUTTON_TEXTURE)

                .setHoverTexture(BUTTON_HOVER_TEXTURE)

                .setUseHoverEffect(true)

                .setTextColor(0x00FF00)

                .setTextHoverColor(0x55FF55));

        this.buttonList.add(

            new ADM_GuiButton(buttonCancelId, centerX + 10, centerY + 52, 50, 20, I18n.format("adm.button.cancel"))

                .setTexture(BUTTON_TEXTURE)

                .setHoverTexture(BUTTON_HOVER_TEXTURE)

                .setUseHoverEffect(true)

                .setTextColor(0xFF5555)

                .setTextHoverColor(0xFF0000));

        updateScreen();

    }

    @Override

    protected void actionPerformed(GuiButton button) {

        if (button.id == buttonSaveId) {

            saveConfig();

        } else if (button.id == buttonCancelId) {

            this.mc.displayGuiScreen(null);

        }

    }

    private void saveConfig() {

        String colorText = colorField.getText()

            .trim();

        if (!isValidHexColor(colorText)) {

            errorTips = I18n.format("adm.error.displaycolor");

            return;

        }

        String name = nameField.getText()

            .trim();

        int color = parseHexColorOrDefault(colorText, TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR);

        AdvanceDataMonitor.ADMCHANEL.sendToServer(

            new PacketGrappleAnchorConfig(anchorX, anchorY, anchorZ, name, color));

        this.mc.displayGuiScreen(null);

    }

    @Override

    protected void keyTyped(char typedChar, int keyCode) {

        if (nameField.isFocused()) {

            nameField.textboxKeyTyped(typedChar, keyCode);

            return;

        }

        if (colorField.isFocused()) {

            colorField.textboxKeyTyped(typedChar, keyCode);

            return;

        }

        super.keyTyped(typedChar, keyCode);

    }

    @Override

    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {

        nameField.mouseClicked(mouseX, mouseY, mouseButton);

        colorField.mouseClicked(mouseX, mouseY, mouseButton);

        super.mouseClicked(mouseX, mouseY, mouseButton);

    }

    @Override

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

        super.drawScreen(mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;

        int centerY = this.height / 2;

        this.drawCenteredString(

            this.fontRendererObj,

            I18n.format("adm.title.grappleAnchorConfig"),

            centerX,

            centerY - 68,

            0x00FFFF);

        this.drawString(

            this.fontRendererObj,

            I18n.format("adm.label.grapple.node_name"),

            centerX - 150,

            centerY - 24,

            0xAAAAAA);

        this.drawString(

            this.fontRendererObj,

            I18n.format("adm.label.grapple.icon_cursor_color"),

            centerX - 150,

            centerY + 6,

            0xAAAAAA);

        nameField.drawTextBox();

        colorField.drawTextBox();

        if (isValidHexColor(colorField.getText())) {

            this.drawCenteredString(

                this.fontRendererObj,

                "§l■",

                centerX - 58,

                centerY + 6,

                Integer.parseInt(colorField.getText(), 16));

        }

        if (!errorTips.isEmpty()) {

            this.drawCenteredString(this.fontRendererObj, errorTips, centerX, centerY + 36, 0xFF5555);

        }

    }

    @Override

    public void onGuiClosed() {

        Keyboard.enableRepeatEvents(false);

        super.onGuiClosed();

    }

    @Override

    public boolean doesGuiPauseGame() {

        return false;

    }

}
