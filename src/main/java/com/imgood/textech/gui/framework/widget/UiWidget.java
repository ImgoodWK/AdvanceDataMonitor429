package com.imgood.textech.gui.framework.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.layout.UiAlignSelf;
import com.imgood.textech.gui.framework.layout.UiConstraints;
import com.imgood.textech.gui.framework.layout.UiCrossAlign;
import com.imgood.textech.gui.framework.layout.UiFlexDirection;
import com.imgood.textech.gui.framework.layout.UiInsets;
import com.imgood.textech.gui.framework.layout.UiLayoutBox;
import com.imgood.textech.gui.framework.layout.UiLayoutNode;
import com.imgood.textech.gui.framework.layout.UiMainAlign;
import com.imgood.textech.gui.framework.style.UiBackground;
import com.imgood.textech.gui.framework.style.UiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Base node of the TeXTech UI widget tree.
 */
@SideOnly(Side.CLIENT)
public class UiWidget implements UiLayoutNode {

    private final List<UiWidget> children = new ArrayList<UiWidget>();
    private UiWidget parent;
    private UiStyle style = new UiStyle();
    private UiLayoutBox layoutBox = UiLayoutBox.empty();

    private int preferredWidth;
    private int preferredHeight;
    private float grow;
    private float shrink = 1f;
    private UiAlignSelf alignSelf = UiAlignSelf.AUTO;
    private boolean absolute;
    private int absoluteX;
    private int absoluteY;

    private UiFlexDirection flexDirection;
    private UiMainAlign mainAlign = UiMainAlign.START;
    private UiCrossAlign crossAlign = UiCrossAlign.STRETCH;

    /** Screen-space origin for the root widget (guiLeft/guiTop). */
    private int screenOriginX;
    private int screenOriginY;

    public void setScreenOrigin(int screenOriginX, int screenOriginY) {
        this.screenOriginX = screenOriginX;
        this.screenOriginY = screenOriginY;
    }

    public UiWidget child(UiWidget child) {
        if (child == null) {
            return this;
        }
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        return this;
    }

    public UiWidget style(UiStyle style) {
        this.style = style != null ? style : new UiStyle();
        return this;
    }

    public UiWidget gap(int gap) {
        this.style.gap(gap);
        return this;
    }

    public UiWidget padding(int all) {
        this.style.padding(all);
        return this;
    }

    public UiStyle style() {
        return style;
    }

    public UiWidget preferredWidth(int width) {
        this.preferredWidth = width;
        return this;
    }

    public UiWidget preferredHeight(int height) {
        this.preferredHeight = height;
        return this;
    }

    public UiWidget preferredSize(int width, int height) {
        this.preferredWidth = width;
        this.preferredHeight = height;
        return this;
    }

    public UiWidget grow(float grow) {
        this.grow = Math.max(0f, grow);
        return this;
    }

    public UiWidget shrink(float shrink) {
        this.shrink = Math.max(0f, shrink);
        return this;
    }

    public UiWidget alignSelf(UiAlignSelf alignSelf) {
        this.alignSelf = alignSelf != null ? alignSelf : UiAlignSelf.AUTO;
        return this;
    }

    public UiWidget setAbsolute(int x, int y) {
        this.absolute = true;
        this.absoluteX = x;
        this.absoluteY = y;
        return this;
    }

    public UiWidget clearAbsolute() {
        this.absolute = false;
        return this;
    }

    protected void setFlexContainer(UiFlexDirection direction) {
        this.flexDirection = direction;
    }

    public UiWidget mainAlign(UiMainAlign align) {
        this.mainAlign = align != null ? align : UiMainAlign.START;
        return this;
    }

    public UiWidget crossAlign(UiCrossAlign align) {
        this.crossAlign = align != null ? align : UiCrossAlign.STRETCH;
        return this;
    }

    public List<UiWidget> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public UiWidget getParent() {
        return parent;
    }

    public int absX() {
        if (parent == null) {
            return screenOriginX + layoutBox.x;
        }
        return parent.absX() + layoutBox.x;
    }

    public int absY() {
        if (parent == null) {
            return screenOriginY + layoutBox.y;
        }
        return parent.absY() + layoutBox.y;
    }

    public void render(UiRenderContext context) {
        if (!isVisible()) {
            return;
        }
        int ax = context.originX() + layoutBox.x;
        int ay = context.originY() + layoutBox.y;
        drawBackground(context, ax, ay, layoutBox.width, layoutBox.height);
        drawSelf(context.withOrigin(ax, ay));
        for (int i = 0; i < children.size(); i++) {
            children.get(i)
                .render(context.withOrigin(ax, ay));
        }
    }

    protected void drawBackground(UiRenderContext context, int ax, int ay, int w, int h) {
        UiBackground bg = style.background();
        if (bg == null || bg.kind == UiBackground.Kind.NONE || w <= 0 || h <= 0) {
            return;
        }
        switch (bg.kind) {
            case SOLID:
                Gui.drawRect(ax, ay, ax + w, ay + h, bg.solidArgb);
                break;
            case NINE_SLICE:
                if (bg.nineSlice != null && GuiBlitUtil.hasResource(bg.nineSlice.texture())) {
                    GuiBlitUtil.drawNineSlice(bg.nineSlice, ax, ay, w, h);
                }
                break;
            case FULL_TEXTURE:
                if (bg.texture != null && GuiBlitUtil.hasResource(bg.texture)) {
                    GL11.glColor4f(1f, 1f, 1f, 1f);
                    // stretch full texture via nine-slice with zero border fallback: use panel draw helper
                    GuiBlitUtil.drawFullTexture(bg.texture, ax, ay, w, h);
                }
                break;
            default:
                break;
        }
    }

    protected void drawSelf(UiRenderContext context) {}

    public UiWidget hitTest(int mouseX, int mouseY) {
        if (!isVisible()) {
            return null;
        }
        int ax = absX();
        int ay = absY();
        if (mouseX < ax || mouseY < ay || mouseX >= ax + layoutBox.width || mouseY >= ay + layoutBox.height) {
            return null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            UiWidget hit = children.get(i)
                .hitTest(mouseX, mouseY);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        UiWidget hit = hitTest(mouseX, mouseY);
        return hit != null && hit.onMouseClicked(mouseX, mouseY, button);
    }

    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i)
                .keyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        return onKeyTyped(typedChar, keyCode);
    }

    protected boolean onKeyTyped(char typedChar, int keyCode) {
        return false;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheel) {
        UiWidget hit = hitTest(mouseX, mouseY);
        UiWidget cur = hit;
        while (cur != null) {
            if (cur.onMouseScrolled(mouseX, mouseY, wheel)) {
                return true;
            }
            cur = cur.parent;
        }
        return false;
    }

    protected boolean onMouseScrolled(int mouseX, int mouseY, int wheel) {
        return false;
    }

    public void tick() {
        for (int i = 0; i < children.size(); i++) {
            children.get(i)
                .tick();
        }
        onTick();
    }

    protected void onTick() {}

    // --- UiLayoutNode ---

    @Override
    public List<? extends UiLayoutNode> layoutChildren() {
        return children;
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
        return style.padding();
    }

    @Override
    public UiInsets margin() {
        return style.margin();
    }

    @Override
    public int gap() {
        return style.gap();
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
        return style.visible();
    }

    @Override
    public void setLayoutBox(UiLayoutBox box) {
        this.layoutBox = box != null ? box : UiLayoutBox.empty();
    }

    @Override
    public UiLayoutBox getLayoutBox() {
        return layoutBox;
    }

    @Override
    public int measureWidth(UiConstraints constraints) {
        if (preferredWidth > 0) {
            return constraints.constrainWidth(preferredWidth);
        }
        if (isFlexContainer()) {
            return measureFlexWidth(constraints);
        }
        int content = intrinsicContentWidth();
        return constraints.constrainWidth(content + padding().horizontal());
    }

    @Override
    public int measureHeight(UiConstraints constraints, int width) {
        if (preferredHeight > 0) {
            return constraints.constrainHeight(preferredHeight);
        }
        if (isFlexContainer()) {
            return measureFlexHeight(constraints, width);
        }
        int innerW = Math.max(0, width - padding().horizontal());
        int content = intrinsicContentHeight(innerW);
        return constraints.constrainHeight(content + padding().vertical());
    }

    protected int intrinsicContentWidth() {
        return 0;
    }

    protected int intrinsicContentHeight(int width) {
        return 0;
    }

    private int measureFlexWidth(UiConstraints constraints) {
        List<UiWidget> kids = children;
        UiInsets pad = padding();
        int gap = gap();
        boolean row = flexDirection == UiFlexDirection.ROW;
        if (row) {
            int sum = pad.horizontal();
            int count = 0;
            for (int i = 0; i < kids.size(); i++) {
                UiWidget c = kids.get(i);
                if (!c.isVisible() || c.isAbsolute()) {
                    continue;
                }
                UiConstraints childC = UiConstraints.loose(constraints.maxWidth, constraints.maxHeight);
                int w = c.preferredWidth() > 0 ? c.preferredWidth() : c.measureWidth(childC);
                sum += w + c.margin()
                    .horizontal();
                count++;
            }
            if (count > 1) {
                sum += gap * (count - 1);
            }
            return constraints.constrainWidth(sum);
        }
        int max = 0;
        for (int i = 0; i < kids.size(); i++) {
            UiWidget c = kids.get(i);
            if (!c.isVisible() || c.isAbsolute()) {
                continue;
            }
            UiConstraints childC = UiConstraints.loose(constraints.maxWidth, constraints.maxHeight);
            int w = c.preferredWidth() > 0 ? c.preferredWidth() : c.measureWidth(childC);
            max = Math.max(
                max,
                w + c.margin()
                    .horizontal());
        }
        return constraints.constrainWidth(max + pad.horizontal());
    }

    private int measureFlexHeight(UiConstraints constraints, int width) {
        List<UiWidget> kids = children;
        UiInsets pad = padding();
        int gap = gap();
        boolean row = flexDirection == UiFlexDirection.ROW;
        int innerW = Math.max(0, width - pad.horizontal());
        if (row) {
            int max = 0;
            for (int i = 0; i < kids.size(); i++) {
                UiWidget c = kids.get(i);
                if (!c.isVisible() || c.isAbsolute()) {
                    continue;
                }
                int cw = c.preferredWidth() > 0 ? c.preferredWidth()
                    : c.measureWidth(UiConstraints.loose(innerW, constraints.maxHeight));
                int ch = c.preferredHeight() > 0 ? c.preferredHeight()
                    : c.measureHeight(UiConstraints.loose(cw, constraints.maxHeight), cw);
                max = Math.max(
                    max,
                    ch + c.margin()
                        .vertical());
            }
            return constraints.constrainHeight(max + pad.vertical());
        }
        int sum = pad.vertical();
        int count = 0;
        for (int i = 0; i < kids.size(); i++) {
            UiWidget c = kids.get(i);
            if (!c.isVisible() || c.isAbsolute()) {
                continue;
            }
            int avail = Math.max(
                0,
                innerW - c.margin()
                    .horizontal());
            int ch = c.preferredHeight() > 0 ? c.preferredHeight()
                : c.measureHeight(UiConstraints.loose(avail, constraints.maxHeight), avail);
            sum += ch + c.margin()
                .vertical();
            count++;
        }
        if (count > 1) {
            sum += gap * (count - 1);
        }
        return constraints.constrainHeight(sum);
    }
}
