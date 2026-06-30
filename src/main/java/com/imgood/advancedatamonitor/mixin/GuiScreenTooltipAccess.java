package com.imgood.advancedatamonitor.mixin;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes protected {@link GuiScreen} helpers so overlay slot tooltips use the same Forge /
 * GTNH tooltip pipeline as normal container slots ({@code renderToolTip} → ItemTooltipEvent).
 */
@Mixin(GuiScreen.class)
public interface GuiScreenTooltipAccess {

    @Invoker("drawHoveringText")
    void adm$drawHoveringText(List textLines, int x, int y, FontRenderer font);

    @Invoker("renderToolTip")
    void adm$renderToolTip(ItemStack stack, int x, int y);
}
