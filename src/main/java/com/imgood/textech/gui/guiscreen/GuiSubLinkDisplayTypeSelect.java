package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.imgood.textech.gui.custom.ADM_GuiButton;
import com.imgood.textech.gui.custom.ADM_GuiScreen;
import com.imgood.textech.gui.custom.AdmGuiTextures;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

/**
 * After binding a unified AE network link, choose which display mode to configure.
 * Lang keys: adm.title.link_display_type, adm.button.link_display_*
 */
public class GuiSubLinkDisplayTypeSelect extends ADM_GuiScreen {

    private final EntityPlayer player;
    private final World world;
    private final TileEntityAdvanceDataMonitor tileEntity;
    private final int index;

    private int offsetX;
    private int offsetY;

    public GuiSubLinkDisplayTypeSelect(EntityPlayer player, World world, TileEntityAdvanceDataMonitor tileEntity,
        int index) {
        this.player = player;
        this.world = world;
        this.tileEntity = tileEntity;
        this.index = index;
        this.setBackgroundTexture(AdmGuiTextures.BACKGROUND_SUB);
        this.setSize(280, 160);
        this.setStretch(false);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.offsetX = this.width / 2 - 140;
        this.offsetY = this.height / 2 - 80;
        this.setPosition(this.offsetX, this.offsetY);

        this.buttonList.add(
            new ADM_GuiButton(
                0,
                this.offsetX + 40,
                this.offsetY + 40,
                200,
                20,
                I18n.format("adm.button.link_display_network")).setTexture(AdmGuiTextures.BUTTON)
                    .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                    .setUseRGBEffect(false));
        this.buttonList.add(
            new ADM_GuiButton(
                1,
                this.offsetX + 40,
                this.offsetY + 70,
                200,
                20,
                I18n.format("adm.button.link_display_storage")).setTexture(AdmGuiTextures.BUTTON)
                    .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                    .setUseRGBEffect(false));
        this.buttonList.add(
            new ADM_GuiButton(
                2,
                this.offsetX + 40,
                this.offsetY + 100,
                200,
                20,
                I18n.format("adm.button.link_display_crafting")).setTexture(AdmGuiTextures.BUTTON)
                    .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                    .setUseRGBEffect(false));
        this.buttonList.add(
            new ADM_GuiButton(3, this.offsetX + 110, this.offsetY + 130, 60, 20, I18n.format("adm.button.cancel"))
                .setTexture(AdmGuiTextures.BUTTON)
                .setHoverTexture(AdmGuiTextures.BUTTON_HOVER)
                .setUseRGBEffect(false));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 3) {
            mc.displayGuiScreen(
                new GuiMainAdvanceDataMonitor(player, world, tileEntity).setPosition(0, 0)
                    .setSize(200, 200)
                    .setBackgroundTexture(AdmGuiTextures.BACKGROUND_MONITOR_MAIN));
            return;
        }

        NBTTagCompound nbt = tileEntity.getDataBound(index);
        if (button.id == 1) {
            nbt.setString("dataType", "storage");
            tileEntity.setDisplayData(index, nbt);
            mc.displayGuiScreen(new GuiSubAEAdvanceStorageLink(player, world, tileEntity, index));
        } else if (button.id == 2) {
            nbt.setString("dataType", "crafting");
            tileEntity.setDisplayData(index, nbt);
            mc.displayGuiScreen(new GuiSubAEAdvanceCraftingLink(player, world, tileEntity, index));
        } else {
            if (!nbt.hasKey("dataType") || "crafting".equals(nbt.getString("dataType"))
                || "storage".equals(nbt.getString("dataType"))) {
                nbt.setString("dataType", "line");
            }
            tileEntity.setDisplayData(index, nbt);
            mc.displayGuiScreen(new GuiSubAEAdvanceNetworkLink(player, world, tileEntity, index));
        }
    }

    @Override
    protected void drawAdmScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawAdmScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(
            this.fontRendererObj,
            I18n.format("adm.title.link_display_type"),
            this.width / 2,
            this.offsetY + 15,
            0x00FFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
