package com.imgood.textech.gui.custom;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import org.lwjgl.input.Mouse;

import com.imgood.textech.gui.framework.UiLayoutContext;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiTheme;
import com.imgood.textech.gui.framework.layout.UiConstraints;
import com.imgood.textech.gui.framework.layout.UiFlexLayoutEngine;
import com.imgood.textech.gui.framework.widget.UiRenderContext;
import com.imgood.textech.gui.framework.widget.UiWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Optional {@link GuiContainer} base with ADM UI theme helpers and optional Flex widget tree.
 */
@SideOnly(Side.CLIENT)
public abstract class ADM_UiContainer extends GuiContainer {

    private final UiTheme theme;
    private UiWidget uiRoot;

    protected ADM_UiContainer(Container container, UiTheme theme) {
        super(container);
        this.theme = theme;
    }

    protected UiTheme theme() {
        return theme;
    }

    /** Keep the world visible outside the compact tiled container panel. */
    @Override
    public void drawDefaultBackground() {}

    protected int panelLeft() {
        return (width - xSize) / 2;
    }

    protected int panelTop() {
        return (height - ySize) / 2;
    }

    protected UiLayoutContext layoutContext() {
        return new UiLayoutContext(theme, fontRendererObj, panelLeft(), panelTop(), xSize, ySize);
    }

    /** Draw main 9-slice panel at GUI-local offset within the container. */
    protected void drawMainPanel(int localX, int localY, int panelW, int panelH) {
        UiPanel.draw(theme, panelLeft() + localX, panelTop() + localY, panelW, panelH);
    }

    /** Attach an optional Flex widget tree (laid out in container local space). */
    protected void setUiRoot(UiWidget root) {
        this.uiRoot = root;
        layoutUiTree();
    }

    protected UiWidget uiRoot() {
        return uiRoot;
    }

    protected void layoutUiTree() {
        if (uiRoot == null) {
            return;
        }
        UiFlexLayoutEngine.layout(uiRoot, UiConstraints.tight(xSize, ySize));
        uiRoot.setScreenOrigin(panelLeft(), panelTop());
    }

    protected void renderUiTree(int mouseX, int mouseY) {
        if (uiRoot == null) {
            return;
        }
        UiRenderContext ctx = new UiRenderContext(fontRendererObj, theme, panelLeft(), panelTop(), mouseX, mouseY);
        uiRoot.render(ctx);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (uiRoot != null) {
            layoutUiTree();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (uiRoot != null) {
            uiRoot.tick();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (uiRoot != null && uiRoot.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (uiRoot != null && uiRoot.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (uiRoot == null) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            uiRoot.mouseScrolled(mouseX, mouseY, wheel);
        }
    }
}
