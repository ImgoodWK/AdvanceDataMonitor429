package com.imgood.textech.gui.framework.widget;

import net.minecraft.client.resources.I18n;

import com.imgood.textech.gui.framework.UiTheme;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Text label widget.
 */
@SideOnly(Side.CLIENT)
public final class UiLabel extends UiWidget {

    private String text = "";
    private boolean title;
    private boolean shadow = true;

    public static UiLabel of(String text) {
        UiLabel label = new UiLabel();
        label.text = text != null ? text : "";
        return label;
    }

    public static UiLabel lang(String key) {
        return of(I18n.format(key));
    }

    public static UiLabel title(String text) {
        UiLabel label = of(text);
        label.title = true;
        return label;
    }

    public UiLabel setText(String text) {
        this.text = text != null ? text : "";
        return this;
    }

    public UiLabel setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    protected int intrinsicContentWidth() {
        // conservative estimate without FontRenderer at measure time
        return Math.max(8, text.length() * 6);
    }

    @Override
    protected int intrinsicContentHeight(int width) {
        return title ? 12 : 10;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        if (text.isEmpty() || context.font() == null) {
            return;
        }
        UiTheme theme = context.theme();
        int color = style().textColor() >= 0 ? style().textColor()
            : (title ? theme.textAccent() : theme.textPrimary());
        int x = 0;
        int y = title ? 1 : 0;
        if (title) {
            String drawn = text;
            int tw = context.font().getStringWidth(drawn);
            x = Math.max(0, (getLayoutBox().width - tw) / 2);
            if (shadow) {
                context.font().drawStringWithShadow(drawn, x, y, color);
            } else {
                context.font().drawString(drawn, x, y, color);
            }
            return;
        }
        if (shadow) {
            context.font().drawStringWithShadow(text, x, y, color);
        } else {
            context.font().drawString(text, x, y, color);
        }
    }
}
