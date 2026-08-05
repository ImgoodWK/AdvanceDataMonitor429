package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.gui.UiFrameworkDebugLayout;
import com.imgood.textech.gui.container.ContainerUiFrameworkDebug;
import com.imgood.textech.gui.custom.ADM_UiContainer;
import com.imgood.textech.gui.framework.AdmUiTheme;
import com.imgood.textech.gui.framework.AtlasRegion;
import com.imgood.textech.gui.framework.FixedAspectButtonFamily;
import com.imgood.textech.gui.framework.GuiBlitUtil;
import com.imgood.textech.gui.framework.UiPanel;
import com.imgood.textech.gui.framework.UiButton;
import com.imgood.textech.gui.framework.UiIcon;
import com.imgood.textech.gui.framework.UiSlot;
import com.imgood.textech.gui.framework.UiText;
import com.imgood.textech.gui.framework.UiTextField;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.framework.UiToggleButton;
import com.imgood.textech.gui.framework.layout.UiMainAlign;
import com.imgood.textech.gui.framework.style.UiStyle;
import com.imgood.textech.gui.framework.widget.UiButtonWidget;
import com.imgood.textech.gui.framework.widget.UiFlex;
import com.imgood.textech.gui.framework.widget.UiLabel;
import com.imgood.textech.gui.framework.widget.UiScrollPanel;
import com.imgood.textech.gui.framework.widget.UiWidget;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Showcase GUI for every {@code gui/framework} widget, Flex tree demo, and atlas region map.
 */
@SideOnly(Side.CLIENT)
public class GuiUiFrameworkDebug extends ADM_UiContainer {

    private UiButton demoButton;
    private UiButton disabledButton;
    private UiToggleButton toggleButton;
    private UiTextField demoField;

    public GuiUiFrameworkDebug(EntityPlayer player) {
        super(new ContainerUiFrameworkDebug(player), UiThemes.ADM);
        this.xSize = UiFrameworkDebugLayout.GUI_W;
        this.ySize = UiFrameworkDebugLayout.GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int left = panelLeft();
        int top = panelTop();

        demoButton = new UiButton(
            left + UiFrameworkDebugLayout.COL_LEFT,
            top + UiFrameworkDebugLayout.ROW_BTN,
            UiFrameworkDebugLayout.BTN_W,
            UiFrameworkDebugLayout.BTN_H).setLabel(I18n.format("adm.button.ui_framework.demo"));

        disabledButton = new UiButton(
            left + UiFrameworkDebugLayout.COL_LEFT,
            top + UiFrameworkDebugLayout.ROW_BTN_DISABLED,
            UiFrameworkDebugLayout.BTN_W,
            UiFrameworkDebugLayout.BTN_H).setLabel(I18n.format("adm.button.ui_framework.disabled"))
                .setEnabled(false);

        toggleButton = new UiToggleButton(
            left + UiFrameworkDebugLayout.COL_LEFT,
            top + UiFrameworkDebugLayout.ROW_TOGGLE,
            UiFrameworkDebugLayout.TOGGLE_W,
            UiFrameworkDebugLayout.TOGGLE_H).setIcons(1, 0)
                .setLabels(
                    I18n.format("adm.label.ui_framework.toggle_off"),
                    I18n.format("adm.label.ui_framework.toggle_on"))
                .setState(false);

        demoField = new UiTextField(
            fontRendererObj,
            left + UiFrameworkDebugLayout.COL_LEFT,
            top + UiFrameworkDebugLayout.ROW_FIELD,
            UiFrameworkDebugLayout.FIELD_W,
            UiFrameworkDebugLayout.FIELD_H).setTheme(UiThemes.ADM)
                .setHintText(I18n.format("adm.hint.ui_framework.field"))
                .setMaxStringLength(48);
        demoButton.setOnClick(new Runnable() {

            @Override
            public void run() {
                demoField.setInvalid(!demoField.delegate().isInvalid());
            }
        });

        setUiRoot(buildFlexDemo());
    }

    private UiWidget buildFlexDemo() {
        UiScrollPanel scroll = new UiScrollPanel();
        scroll.preferredHeight(48)
            .grow(1f)
            .style(
                new UiStyle().padding(2)
                    .gap(2)
                    .backgroundSolid(0x44000000));
        for (int i = 1; i <= 8; i++) {
            scroll.child(
                UiLabel.of(I18n.format("adm.label.ui_framework.flex_scroll_item", i))
                    .preferredHeight(10));
        }

        return UiFlex.column()
            .style(
                new UiStyle().padding(4)
                    .gap(4)
                    .backgroundSolid(0x66002020))
            .preferredWidth(UiFrameworkDebugLayout.FLEX_W)
            .preferredHeight(UiFrameworkDebugLayout.FLEX_H)
            .setAbsolute(UiFrameworkDebugLayout.FLEX_X, UiFrameworkDebugLayout.ROW_FLEX)
            .child(
                UiLabel.of(I18n.format("adm.label.ui_framework.section.flex"))
                    .preferredHeight(10))
            .child(
                UiFlex.row()
                    .gap(4)
                    .preferredHeight(16)
                    .child(
                        UiButtonWidget.of(I18n.format("adm.button.ui_framework.flex_a"))
                            .preferredWidth(48)
                            .preferredHeight(16))
                    .child(
                        UiButtonWidget.of(I18n.format("adm.button.ui_framework.flex_b"))
                            .grow(1f)
                            .preferredHeight(16))
                    .mainAlign(UiMainAlign.START))
            .child(scroll);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (demoField != null) {
            demoField.delegate()
                .updateCursorCounter();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (demoField != null) {
            demoField.delegate()
                .mouseClicked(mouseX, mouseY, button);
        }
        if (demoButton != null) {
            demoButton.click(mouseX, mouseY, button);
        }
        if (toggleButton != null) {
            toggleButton.click(mouseX, mouseY, button);
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (demoField != null && demoField.delegate()
            .textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (demoField != null) {
            demoField.drawBackground(
                demoField.delegate()
                    .isFocused());
            demoField.drawTextBox();
        }
        if (demoButton != null) {
            demoButton.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
        }
        if (disabledButton != null) {
            disabledButton.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
        }
        if (toggleButton != null) {
            toggleButton.draw(UiThemes.ADM, fontRendererObj, mouseX, mouseY);
        }
        renderUiTree(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawMainPanel(0, 0, xSize, ySize);
        int left = panelLeft();
        int top = panelTop();
        UiPanel.drawTitleOrnament(theme(), left + 8, top + 3, xSize - 16);
        UiPanel.drawSection(
            theme(),
            left + 4,
            top + UiFrameworkDebugLayout.ROW_SECTION + 6,
            UiFrameworkDebugLayout.COL_ATLAS - 10,
            xSize > 0 ? ySize - UiFrameworkDebugLayout.ROW_SECTION - 10 : 0);
        UiPanel.drawSection(
            theme(),
            left + UiFrameworkDebugLayout.COL_ATLAS - 4,
            top + UiFrameworkDebugLayout.ROW_SECTION + 6,
            xSize - UiFrameworkDebugLayout.COL_ATLAS,
            ySize - UiFrameworkDebugLayout.ROW_SECTION - 10);
        UiSlot.drawVanilla(left + UiFrameworkDebugLayout.COL_LEFT, top + UiFrameworkDebugLayout.ROW_SLOT);
        UiSlot.drawTheme(
            UiThemes.ADM,
            left + UiFrameworkDebugLayout.COL_LEFT + UiFrameworkDebugLayout.SLOT_GAP,
            top + UiFrameworkDebugLayout.ROW_SLOT);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int titleCenter = xSize / 2;
        UiText.drawCenteredTitle(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.title.ui_framework_debug"),
            titleCenter,
            UiFrameworkDebugLayout.ROW_TITLE);

        UiText.drawAccent(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.atlas_file"),
            UiFrameworkDebugLayout.COL_LEFT,
            UiFrameworkDebugLayout.ROW_ATLAS_FILE);

        drawSectionHeader(
            I18n.format("adm.label.ui_framework.section.components"),
            UiFrameworkDebugLayout.COL_LEFT,
            UiFrameworkDebugLayout.ROW_SECTION);
        drawSectionHeader(
            I18n.format("adm.label.ui_framework.section.atlas"),
            UiFrameworkDebugLayout.COL_ATLAS,
            UiFrameworkDebugLayout.ROW_SECTION);

        drawComponentRow(
            "UiPanel",
            "adm.label.ui_framework.component.ui_panel",
            UiFrameworkDebugLayout.ROW_PANEL_LABEL);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.sample_primary"),
            UiFrameworkDebugLayout.COL_LEFT,
            UiFrameworkDebugLayout.ROW_TEXT);
        UiText.drawAccent(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.sample_accent"),
            UiFrameworkDebugLayout.COL_LEFT,
            UiFrameworkDebugLayout.ROW_TEXT_ACCENT);

        drawComponentRow("UiIcon", "adm.label.ui_framework.component.ui_icon", UiFrameworkDebugLayout.ROW_ICON - 10);
        for (int i = 0; i < 4; i++) {
            int ix = UiFrameworkDebugLayout.COL_LEFT + i * UiFrameworkDebugLayout.ICON_GAP;
            int iy = UiFrameworkDebugLayout.ROW_ICON;
            UiIcon.drawThemeIcon(UiThemes.ADM, i, ix, iy, UiFrameworkDebugLayout.ICON_SIZE);
            UiText.drawLabel(
                UiThemes.ADM,
                fontRendererObj,
                String.valueOf(i),
                ix + 4,
                iy + UiFrameworkDebugLayout.ICON_SIZE + 1);
        }

        drawControlLabel("UiButton", UiFrameworkDebugLayout.ROW_BTN, UiFrameworkDebugLayout.BTN_W);
        drawControlLabel("disabled", UiFrameworkDebugLayout.ROW_BTN_DISABLED, UiFrameworkDebugLayout.BTN_W);
        drawControlLabel("toggle", UiFrameworkDebugLayout.ROW_TOGGLE, UiFrameworkDebugLayout.TOGGLE_W);
        drawControlLabel(
            "slots",
            UiFrameworkDebugLayout.ROW_SLOT,
            UiFrameworkDebugLayout.SLOT_GAP + UiFrameworkDebugLayout.ICON_SIZE);

        drawComponentRow(
            "UiTextField",
            "adm.label.ui_framework.component.ui_field",
            UiFrameworkDebugLayout.ROW_FIELD - 10);

        drawAtlasReference();
    }

    private void drawSectionHeader(String text, int x, int y) {
        UiText.drawAccent(UiThemes.ADM, fontRendererObj, text, x, y);
    }

    private void drawComponentRow(String className, String descKey, int y) {
        UiText.drawLabel(UiThemes.ADM, fontRendererObj, className, UiFrameworkDebugLayout.COL_LEFT, y);
        String desc = I18n.format(descKey);
        if (desc != null && !desc.isEmpty() && !desc.equals(descKey)) {
            int descX = UiFrameworkDebugLayout.COL_LEFT + 72;
            int maxWidth = UiFrameworkDebugLayout.LEFT_COLUMN_RIGHT - descX;
            UiText.drawLabel(
                UiThemes.ADM,
                fontRendererObj,
                fontRendererObj.trimStringToWidth(desc, Math.max(1, maxWidth)),
                descX,
                y);
        }
    }

    private void drawControlLabel(String text, int controlY, int controlWidth) {
        int x = UiFrameworkDebugLayout.COL_LEFT + controlWidth + 5;
        int maxWidth = UiFrameworkDebugLayout.LEFT_COLUMN_RIGHT - x;
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            fontRendererObj.trimStringToWidth(text, Math.max(1, maxWidth)),
            x,
            controlY + 4);
    }

    private void drawAtlasReference() {
        AdmUiTheme theme = AdmUiTheme.instance();
        int y = UiFrameworkDebugLayout.ROW_ATLAS_START;
        y = drawRegionLine(theme.sparseMainFrame().topLeft(), "main.corner", y);
        y = drawRegionLine(theme.sparseMainFrame().background(), "main.glass", y);
        y = drawRegionLine(theme.sparseSectionFrame().topLeft(), "section.corner", y);
        y = drawRegionLine(theme.sparseSectionFrame().background(), "section.glass", y);
        y = drawRegionLine(theme.titleOrnament(), "title", y);
        y = drawRegionLine(theme.footerOrnament(), "footer", y);
        y = drawButtonLine(theme.fixedAspectButtons(), FixedAspectButtonFamily.State.NORMAL, 20, y);
        y = drawButtonLine(theme.fixedAspectButtons(), FixedAspectButtonFamily.State.HOVER, 100, y);
        y = drawButtonLine(theme.fixedAspectButtons(), FixedAspectButtonFamily.State.PRESSED, 240, y);
        y = drawRegionLine(theme.underlineField().style(com.imgood.textech.gui.framework.UnderlineFieldRegion.State.INVALID).bottom(), "field.invalid", y);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.button_states"),
            UiFrameworkDebugLayout.COL_ATLAS,
            y);
        drawButtonStateSamples(theme.fixedAspectButtons(), y + 11);
    }

    private void drawButtonStateSamples(FixedAspectButtonFamily family, int y) {
        FixedAspectButtonFamily.State[] states = FixedAspectButtonFamily.State.values();
        int width = 40;
        int height = 16;
        int gap = 4;
        for (int i = 0; i < states.length; i++) {
            FixedAspectButtonFamily.State state = states[i];
            int x = UiFrameworkDebugLayout.COL_ATLAS + i * (width + gap);
            GuiBlitUtil.drawFixedAspectButton(family, state, x, y, width, height);
            UiText.drawOnButton(
                UiThemes.ADM,
                fontRendererObj,
                state.name().substring(0, 1),
                x,
                y,
                width,
                height,
                state != FixedAspectButtonFamily.State.DISABLED,
                state == FixedAspectButtonFamily.State.HOVER);
        }
    }

    private int drawRegionLine(AtlasRegion region, String name, int y) {
        String line = name + " @"
            + region.u()
            + ","
            + region.v()
            + " "
            + region.width()
            + "x"
            + region.height();
        int maxWidth = UiFrameworkDebugLayout.GUI_W - UiFrameworkDebugLayout.COL_ATLAS - 8;
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            fontRendererObj.trimStringToWidth(line, maxWidth),
            UiFrameworkDebugLayout.COL_ATLAS,
            y);
        return y + UiFrameworkDebugLayout.ATLAS_LINE_H;
    }

    private int drawButtonLine(FixedAspectButtonFamily family, FixedAspectButtonFamily.State state, int width, int y) {
        return drawRegionLine(family.region(state, width, FixedAspectButtonFamily.BASE_HEIGHT), state.name() + "." + width, y);
    }
}
