package com.imgood.textech.gui.framework.host;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.imgood.textech.gui.framework.UiTheme;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.framework.layout.UiConstraints;
import com.imgood.textech.gui.framework.layout.UiFlexLayoutEngine;
import com.imgood.textech.gui.framework.widget.UiRenderContext;
import com.imgood.textech.gui.framework.widget.UiWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Configuration-screen host for the Flex widget tree.
 * New non-container GUIs should prefer this over {@code ADM_GuiScreen}.
 */
@SideOnly(Side.CLIENT)
public abstract class AdmUiScreen extends GuiScreen {

    private final UiTheme theme;
    private UiWidget root;
    protected int guiLeft;
    protected int guiTop;
    protected int guiWidth = 176;
    protected int guiHeight = 166;

    protected AdmUiScreen() {
        this(UiThemes.ADM);
    }

    protected AdmUiScreen(UiTheme theme) {
        this.theme = theme != null ? theme : UiThemes.ADM;
    }

    protected UiTheme theme() {
        return theme;
    }

    protected UiWidget root() {
        return root;
    }

    /** Build the widget tree; called from {@link #initGui()}. */
    protected abstract UiWidget buildUi();

    protected void setGuiSize(int width, int height) {
        this.guiWidth = width;
        this.guiHeight = height;
    }

    @Override
    public void initGui() {
        super.initGui();
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        root = buildUi();
        layoutUi();
    }

    protected void layoutUi() {
        if (root == null) {
            return;
        }
        UiFlexLayoutEngine.layout(root, UiConstraints.tight(guiWidth, guiHeight));
        root.setScreenOrigin(guiLeft, guiTop);
    }

    protected void renderUi(int mouseX, int mouseY) {
        if (root == null) {
            return;
        }
        UiRenderContext ctx = new UiRenderContext(fontRendererObj, theme, guiLeft, guiTop, mouseX, mouseY);
        root.render(ctx);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        renderUi(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (root != null) {
            root.tick();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (root != null && root.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (root != null && root.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && root != null) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            root.mouseScrolled(mouseX, mouseY, wheel);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
