package com.imgood.textech.gui.framework.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal {@link UiLayoutNode} for pure layout unit tests (no Minecraft).
 */
public final class FakeLayoutNode implements UiLayoutNode {

    private final List<FakeLayoutNode> children = new ArrayList<FakeLayoutNode>();
    private UiFlexDirection flexDirection;
    private UiMainAlign mainAlign = UiMainAlign.START;
    private UiCrossAlign crossAlign = UiCrossAlign.STRETCH;
    private UiAlignSelf alignSelf = UiAlignSelf.AUTO;
    private UiInsets padding = UiInsets.ZERO;
    private UiInsets margin = UiInsets.ZERO;
    private int gap;
    private int preferredWidth;
    private int preferredHeight;
    private float grow;
    private float shrink = 1f;
    private boolean absolute;
    private int absoluteX;
    private int absoluteY;
    private boolean visible = true;
    private UiLayoutBox box = UiLayoutBox.empty();
    private int intrinsicW;
    private int intrinsicH;

    public static FakeLayoutNode flexRow() {
        FakeLayoutNode n = new FakeLayoutNode();
        n.flexDirection = UiFlexDirection.ROW;
        return n;
    }

    public static FakeLayoutNode flexColumn() {
        FakeLayoutNode n = new FakeLayoutNode();
        n.flexDirection = UiFlexDirection.COLUMN;
        return n;
    }

    public static FakeLayoutNode leaf(int w, int h) {
        FakeLayoutNode n = new FakeLayoutNode();
        n.intrinsicW = w;
        n.intrinsicH = h;
        return n;
    }

    public FakeLayoutNode child(FakeLayoutNode child) {
        children.add(child);
        return this;
    }

    public FakeLayoutNode padding(int all) {
        padding = UiInsets.all(all);
        return this;
    }

    public FakeLayoutNode gap(int gap) {
        this.gap = gap;
        return this;
    }

    public FakeLayoutNode grow(float grow) {
        this.grow = grow;
        return this;
    }

    public FakeLayoutNode preferredWidth(int w) {
        preferredWidth = w;
        return this;
    }

    public FakeLayoutNode preferredHeight(int h) {
        preferredHeight = h;
        return this;
    }

    public FakeLayoutNode mainAlign(UiMainAlign align) {
        mainAlign = align;
        return this;
    }

    public FakeLayoutNode crossAlign(UiCrossAlign align) {
        crossAlign = align;
        return this;
    }

    public FakeLayoutNode margin(int all) {
        margin = UiInsets.all(all);
        return this;
    }

    @Override
    public List<? extends UiLayoutNode> layoutChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public boolean isFlexContainer() {
        return flexDirection != null;
    }

    @Override
    public UiFlexDirection flexDirection() {
        return flexDirection != null ? flexDirection : UiFlexDirection.COLUMN;
    }

    @Override
    public UiMainAlign mainAlign() {
        return mainAlign;
    }

    @Override
    public UiCrossAlign crossAlign() {
        return crossAlign;
    }

    @Override
    public UiAlignSelf alignSelf() {
        return alignSelf;
    }

    @Override
    public UiInsets padding() {
        return padding;
    }

    @Override
    public UiInsets margin() {
        return margin;
    }

    @Override
    public int gap() {
        return gap;
    }

    @Override
    public int preferredWidth() {
        return preferredWidth;
    }

    @Override
    public int preferredHeight() {
        return preferredHeight;
    }

    @Override
    public float grow() {
        return grow;
    }

    @Override
    public float shrink() {
        return shrink;
    }

    @Override
    public boolean isAbsolute() {
        return absolute;
    }

    @Override
    public int absoluteX() {
        return absoluteX;
    }

    @Override
    public int absoluteY() {
        return absoluteY;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setLayoutBox(UiLayoutBox box) {
        this.box = box;
    }

    @Override
    public UiLayoutBox getLayoutBox() {
        return box;
    }

    @Override
    public int measureWidth(UiConstraints constraints) {
        if (preferredWidth > 0) {
            return constraints.constrainWidth(preferredWidth);
        }
        if (isFlexContainer() && flexDirection == UiFlexDirection.ROW) {
            int sum = padding.horizontal();
            int count = 0;
            for (FakeLayoutNode c : children) {
                sum += c.measureWidth(constraints) + c.margin.horizontal();
                count++;
            }
            if (count > 1) {
                sum += gap * (count - 1);
            }
            return constraints.constrainWidth(sum);
        }
        if (isFlexContainer()) {
            int max = 0;
            for (FakeLayoutNode c : children) {
                max = Math.max(max, c.measureWidth(constraints) + c.margin.horizontal());
            }
            return constraints.constrainWidth(max + padding.horizontal());
        }
        return constraints.constrainWidth(intrinsicW + padding.horizontal());
    }

    @Override
    public int measureHeight(UiConstraints constraints, int width) {
        if (preferredHeight > 0) {
            return constraints.constrainHeight(preferredHeight);
        }
        if (isFlexContainer() && flexDirection == UiFlexDirection.COLUMN) {
            int sum = padding.vertical();
            int count = 0;
            for (FakeLayoutNode c : children) {
                int cw = Math.max(0, width - padding.horizontal() - c.margin.horizontal());
                sum += c.measureHeight(constraints, cw) + c.margin.vertical();
                count++;
            }
            if (count > 1) {
                sum += gap * (count - 1);
            }
            return constraints.constrainHeight(sum);
        }
        if (isFlexContainer()) {
            int max = 0;
            for (FakeLayoutNode c : children) {
                max = Math.max(max, c.measureHeight(constraints, width) + c.margin.vertical());
            }
            return constraints.constrainHeight(max + padding.vertical());
        }
        return constraints.constrainHeight(intrinsicH + padding.vertical());
    }
}
