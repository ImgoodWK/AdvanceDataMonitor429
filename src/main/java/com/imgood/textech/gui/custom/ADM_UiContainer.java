package com.imgood.textech.gui.custom;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import com.imgood.textech.gui.framework.UiLayoutContext;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional {@link GuiContainer} base with ADM UI theme helpers.
 */
@SideOnly(Side.CLIENT)
public abstract class ADM_UiContainer extends GuiContainer {

    private final UiTheme theme;

    protected ADM_UiContainer(Container container, UiTheme theme) {
        super(container);
        this.theme = theme;
    }

    protected UiTheme theme() {
        return theme;
    }

    protected int panelLeft() {
        return (width - xSize) / 2;
    }

    protected int panelTop() {
        return (height - ySize) / 2;
    }

    protected UiLayoutContext layoutContext() {
        return new UiLayoutContext(theme, fontRendererObj, panelLeft(), panelTop(), xSize, ySize);
    }

    /** Draw main 9-slice panel at GUI-local offset within the container. */
    protected void drawMainPanel(int localX, int localY, int panelW, int panelH) {
        UiPanel.draw(theme, panelLeft() + localX, panelTop() + localY, panelW, panelH);
    }
}
