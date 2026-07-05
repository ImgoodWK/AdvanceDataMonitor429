package com.imgood.textech.gui.guiscreen;

import static com.imgood.textech.utils.ContentsHelper.isValidHexColor;
import static com.imgood.textech.utils.ContentsHelper.parseHexColorOrDefault;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.custom.ADM_GuiTextField;
import com.imgood.textech.gui.custom.AdmItemConfigScreen;
import com.imgood.textech.network.packet.PacketGrappleAnchorConfig;
import com.imgood.textech.tileentity.TileEntityGrappleAnchor;

/**
 * Display names / 显示名称:
 * - EN: Grapple Anchor Settings
 * - ZH: 挂索节点设置
 * Lang keys: adm.title.grappleAnchorConfig
 */
public class GuiGrappleAnchorConfig extends AdmItemConfigScreen {

    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final EntityPlayer player;
    private final World world;

    private ADM_GuiTextField nameField;
    private ADM_GuiTextField colorField;

    public GuiGrappleAnchorConfig(EntityPlayer player, World world, int x, int y, int z) {
        super(360, 200);
        this.player = player;
        this.world = world;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    @Override
    protected void initConfigContent() {
        int cx = centerX();
        int cy = centerY();
        TileEntityGrappleAnchor anchor = TileEntityGrappleAnchor.get(world, anchorX, anchorY, anchorZ);

        nameField = createTextField(cx + 20, cy - 28, 140, 20);
        nameField.setMaxStringLength(32);
        nameField.setHintText(I18n.format("adm.hint.grapple.node_name"));
        nameField.setText(anchor != null ? anchor.getDisplayName() : "");
        nameField.setFocused(true);

        colorField = createTextField(cx + 20, cy + 2, 80, 20);
        colorField.setMaxStringLength(6);
        colorField.setHintText(I18n.format("adm.hint.displaycolor"));
        colorField.setText(
            anchor != null ? TileEntityGrappleAnchor.colorToHex(anchor.getIconCursorColor())
                : TileEntityGrappleAnchor.colorToHex(TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR));

        buttonList.add(createSaveButton(cx - 60, cy + 52));
        buttonList.add(createCancelButton(cx + 10, cy + 52));
    }

    @Override
    protected void onSave() {
        String colorText = colorField.getText()
            .trim();
        if (!isValidHexColor(colorText)) {
            errorTips = I18n.format("adm.error.displaycolor");
            return;
        }
        String name = nameField.getText()
            .trim();
        int color = parseHexColorOrDefault(colorText, TileEntityGrappleAnchor.DEFAULT_ICON_CURSOR_COLOR);
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(new PacketGrappleAnchorConfig(anchorX, anchorY, anchorZ, name, color));
        closeScreen();
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
        int cx = centerX();
        int cy = centerY();
        drawCenteredString(fontRendererObj, I18n.format("adm.title.grappleAnchorConfig"), cx, cy - 68, 0x00FFFF);
        drawString(fontRendererObj, I18n.format("adm.label.grapple.node_name"), cx - 150, cy - 24, 0xAAAAAA);
        drawString(fontRendererObj, I18n.format("adm.label.grapple.icon_cursor_color"), cx - 150, cy + 6, 0xAAAAAA);
        nameField.drawTextBox();
        colorField.drawTextBox();
        if (isValidHexColor(colorField.getText())) {
            drawCenteredString(fontRendererObj, "§l■", cx - 58, cy + 6, Integer.parseInt(colorField.getText(), 16));
        }
        drawErrorTips(cy + 36);
    }
}
