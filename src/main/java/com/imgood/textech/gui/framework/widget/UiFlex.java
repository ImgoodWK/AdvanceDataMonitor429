package com.imgood.textech.gui.framework.widget;

import com.imgood.textech.gui.framework.layout.UiFlexDirection;
import com.imgood.textech.gui.framework.style.UiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Flexbox container widget ({@code display:flex}).
 */
@SideOnly(Side.CLIENT)
public final class UiFlex extends UiWidget {

    private UiFlex(UiFlexDirection direction) {
        setFlexContainer(direction);
    }

    public static UiFlex row() {
        return new UiFlex(UiFlexDirection.ROW);
    }

    public static UiFlex column() {
        return new UiFlex(UiFlexDirection.COLUMN);
    }

    @Override
    public UiFlex style(UiStyle style) {
        super.style(style);
        return this;
    }

    @Override
    public UiFlex gap(int gap) {
        super.gap(gap);
        return this;
    }

    @Override
    public UiFlex padding(int all) {
        super.padding(all);
        return this;
    }

    @Override
    public UiFlex child(UiWidget child) {
        super.child(child);
        return this;
    }

    @Override
    public UiFlex grow(float grow) {
        super.grow(grow);
        return this;
    }

    @Override
    public UiFlex preferredWidth(int width) {
        super.preferredWidth(width);
        return this;
    }

    @Override
    public UiFlex preferredHeight(int height) {
        super.preferredHeight(height);
        return this;
    }

    @Override
    public UiFlex mainAlign(com.imgood.textech.gui.framework.layout.UiMainAlign align) {
        super.mainAlign(align);
        return this;
    }

    @Override
    public UiFlex crossAlign(com.imgood.textech.gui.framework.layout.UiCrossAlign align) {
        super.crossAlign(align);
        return this;
    }
}
