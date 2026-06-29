package com.imgood.advancedatamonitor.mixin;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes protected {@link GuiScreen#drawHoveringText} so overlay slot tooltips use the
 * same path as vanilla containers (and GTNH tooltip-style mod hooks).
 */
@Mixin(GuiScreen.class)
public interface GuiScreenTooltipAccess {

    @Invoker("drawHoveringText")
    void adm$drawHoveringText(List textLines, int x, int y, FontRenderer font);
}
