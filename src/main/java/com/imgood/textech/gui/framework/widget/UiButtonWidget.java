package com.imgood.textech.gui.framework.widget;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.input.Mouse;

import com.imgood.textech.gui.framework.FixedAspectButtonFamily;
import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.NineSliceRegion;
import com.imgood.textech.gui.framework.UiTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tree-mounted button using complete fixed-aspect theme chrome.
 */
@SideOnly(Side.CLIENT)
public final class UiButtonWidget extends UiWidget {

    private String label = "";
    private boolean enabled = true;
    private Runnable onClick;

    public UiButtonWidget() {
        preferredHeight(16);
        preferredWidth(80);
    }

    public static UiButtonWidget of(String label) {
        return new UiButtonWidget().setLabel(label);
    }

    public static UiButtonWidget save(Runnable onClick) {
        return of("Save").setOnClick(onClick);
    }

    public static UiButtonWidget cancel(Runnable onClick) {
        return of("Cancel").setOnClick(onClick);
    }

    public UiButtonWidget setLabel(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public UiButtonWidget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public UiButtonWidget setOnClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    @Override
    protected int intrinsicContentWidth() {
        return Math.max(40, label.length() * 6 + 16);
    }

    @Override
    protected int intrinsicContentHeight(int width) {
        return 16;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int w = getLayoutBox().width;
        int h = getLayoutBox().height;
        int shellW = FixedAspectButtonFamily.normalizedWidthFor(w, h);
        int shellH = FixedAspectButtonFamily.normalizedHeightFor(w, h);
        int shellX = Math.max(0, (w - shellW) / 2);
        int shellY = Math.max(0, (h - shellH) / 2);
        UiTheme theme = context.theme();
        boolean hovered = enabled && containsMouse(context);
        boolean pressed = hovered && Mouse.isButtonDown(0);
        FixedAspectButtonFamily family = theme.fixedAspectButtons();
        FixedAspectButtonFamily.State state = !enabled ? FixedAspectButtonFamily.State.DISABLED
            : pressed ? FixedAspectButtonFamily.State.PRESSED
                : hovered ? FixedAspectButtonFamily.State.HOVER : FixedAspectButtonFamily.State.NORMAL;
        NineSliceRegion region;
        if (!enabled) {
            region = theme.buttonDisabled();
        } else if (pressed) {
            region = theme.buttonPressed();
        } else if (hovered) {
            region = theme.buttonHover();
        } else {
            region = theme.buttonNormal();
        }
        if (family != null && GuiBlitUtil.hasResource(family.region(state, w, h).texture())) {
            GuiBlitUtil.drawFixedAspectButton(family, state, shellX, shellY, shellW, shellH);
        } else if (region != null && GuiBlitUtil.hasResource(region.texture())) {
            GuiBlitUtil.drawHorizontalSlice(region, 0, 0, w, h);
        }
        if (!label.isEmpty() && context.font() != null) {
            FontRenderer font = context.font();
            int color;
            if (!enabled) {
                color = theme.textDisabled();
            } else if (hovered && style().textHoverColor() >= 0) {
                color = style().textHoverColor();
            } else if (style().textColor() >= 0) {
                color = style().textColor();
            } else {
                color = theme.textAccent();
            }
            int tw = font.getStringWidth(label);
            font.drawStringWithShadow(
                label,
                shellX + Math.max(0, (shellW - tw) / 2),
                shellY + Math.max(0, (shellH - 8) / 2),
                color);
        }
    }

    @Override
    public UiWidget hitTest(int mouseX, int mouseY) {
        if (!isVisible()) {
            return null;
        }
        int w = getLayoutBox().width;
        int h = getLayoutBox().height;
        int shellW = FixedAspectButtonFamily.normalizedWidthFor(w, h);
        int shellH = FixedAspectButtonFamily.normalizedHeightFor(w, h);
        int shellX = absX() + Math.max(0, (w - shellW) / 2);
        int shellY = absY() + Math.max(0, (h - shellH) / 2);
        return mouseX >= shellX && mouseY >= shellY && mouseX < shellX + shellW && mouseY < shellY + shellH
            ? this
            : null;
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        if (!enabled || button != 0) {
            return false;
        }
        if (onClick != null) {
            onClick.run();
        }
        return true;
    }

    private boolean containsMouse(UiRenderContext context) {
        int w = getLayoutBox().width;
        int h = getLayoutBox().height;
        int shellW = FixedAspectButtonFamily.normalizedWidthFor(w, h);
        int shellH = FixedAspectButtonFamily.normalizedHeightFor(w, h);
        int ax = context.originX() + Math.max(0, (w - shellW) / 2);
        int ay = context.originY() + Math.max(0, (h - shellH) / 2);
        int mx = context.mouseX();
        int my = context.mouseY();
        return mx >= ax && my >= ay && mx < ax + shellW && my < ay + shellH;
    }
}
