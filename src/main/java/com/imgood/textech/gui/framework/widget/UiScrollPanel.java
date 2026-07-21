package com.imgood.textech.gui.framework.widget;

import net.minecraft.client.gui.Gui;

import com.imgood.textech.gui.framework.layout.UiCrossAlign;
import com.imgood.textech.gui.framework.layout.UiFlexDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Scroll container; mouse wheel adjusts {@link #scrollY}. Children are drawn with a Y offset.
 */
@SideOnly(Side.CLIENT)
public final class UiScrollPanel extends UiWidget {

    private int scrollY;
    private int contentHeight;

    public UiScrollPanel() {
        setFlexContainer(UiFlexDirection.COLUMN);
        crossAlign(UiCrossAlign.START);
    }

    public int scrollY() {
        return scrollY;
    }

    public UiScrollPanel setScrollY(int scrollY) {
        this.scrollY = Math.max(0, scrollY);
        clampScroll();
        return this;
    }

    private void clampScroll() {
        int viewH = getLayoutBox().height;
        int max = Math.max(0, contentHeight - viewH);
        if (scrollY > max) {
            scrollY = max;
        }
        if (scrollY < 0) {
            scrollY = 0;
        }
    }

    @Override
    public void render(UiRenderContext context) {
        if (!isVisible()) {
            return;
        }
        int ax = context.originX() + getLayoutBox().x;
        int ay = context.originY() + getLayoutBox().y;
        drawBackground(context, ax, ay, getLayoutBox().width, getLayoutBox().height);

        contentHeight = 0;
        for (UiWidget child : getChildren()) {
            contentHeight = Math.max(contentHeight, child.getLayoutBox().y + child.getLayoutBox().height);
        }
        clampScroll();

        for (UiWidget child : getChildren()) {
            child.render(context.withOrigin(ax, ay - scrollY));
        }

        int viewH = getLayoutBox().height;
        if (contentHeight > viewH && viewH > 0) {
            int barH = Math.max(8, viewH * viewH / contentHeight);
            int maxTravel = viewH - barH;
            int barY = maxTravel == 0 ? 0 : scrollY * maxTravel / Math.max(1, contentHeight - viewH);
            Gui.drawRect(
                ax + getLayoutBox().width - 3,
                ay + barY,
                ax + getLayoutBox().width - 1,
                ay + barY + barH,
                0xAA00FFFF);
        }
    }

    @Override
    public UiWidget hitTest(int mouseX, int mouseY) {
        if (!isVisible()) {
            return null;
        }
        int ax = absX();
        int ay = absY();
        if (mouseX < ax || mouseY < ay || mouseX >= ax + getLayoutBox().width || mouseY >= ay + getLayoutBox().height) {
            return null;
        }
        int contentY = mouseY + scrollY;
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            UiWidget hit = hitChildScrolled(getChildren().get(i), mouseX, contentY, ax, ay);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    private UiWidget hitChildScrolled(UiWidget child, int mouseX, int contentY, int parentAbsX, int parentAbsY) {
        int cx = parentAbsX + child.getLayoutBox().x;
        int cy = parentAbsY + child.getLayoutBox().y;
        if (mouseX < cx || contentY < cy
            || mouseX >= cx + child.getLayoutBox().width
            || contentY >= cy + child.getLayoutBox().height) {
            return null;
        }
        for (int i = child.getChildren()
            .size() - 1; i >= 0; i--) {
            UiWidget hit = hitChildScrolled(
                child.getChildren()
                    .get(i),
                mouseX,
                contentY,
                cx,
                cy);
            if (hit != null) {
                return hit;
            }
        }
        return child;
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, int wheel) {
        if (wheel == 0) {
            return false;
        }
        setScrollY(scrollY - (wheel > 0 ? 12 : -12));
        return true;
    }
}
