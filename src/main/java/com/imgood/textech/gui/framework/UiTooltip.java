package com.imgood.textech.gui.framework;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tooltip placeholder for scroll/tooltip integration.
 * <p>
 * Tooltips must be drawn from a {@code GuiScreen}/{@code GuiContainer} subclass via
 * {@code drawHoveringText(...)} because that method is protected on {@code GuiScreen}.
 * Not used in the initial debug pass.
 */
@SideOnly(Side.CLIENT)
public final class UiTooltip {

    private UiTooltip() {}
}
