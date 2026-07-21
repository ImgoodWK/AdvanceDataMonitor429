package com.imgood.textech.gui.framework.widget;

import net.minecraft.client.gui.FontRenderer;

import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.NineSliceRegion;
import com.imgood.textech.gui.framework.UiTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tree-mounted button using theme 3-slice chrome.
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
        UiTheme theme = context.theme();
        boolean hovered = enabled && containsMouse(context);
        NineSliceRegion region;
        if (!enabled) {
            region = theme.buttonDisabled();
        } else if (hovered) {
            region = theme.buttonHover();
        } else {
            region = theme.buttonNormal();
        }
        if (region != null && GuiBlitUtil.hasResource(region.texture())) {
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
            font.drawStringWithShadow(label, Math.max(0, (w - tw) / 2), Math.max(0, (h - 8) / 2), color);
        }
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
        int ax = context.originX();
        int ay = context.originY();
        int mx = context.mouseX();
        int my = context.mouseY();
        return mx >= ax && my >= ay && mx < ax + getLayoutBox().width && my < ay + getLayoutBox().height;
    }
}
