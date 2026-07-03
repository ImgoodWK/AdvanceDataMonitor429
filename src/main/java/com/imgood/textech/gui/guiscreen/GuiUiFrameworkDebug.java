package com.imgood.textech.gui.guiscreen;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;

import com.imgood.textech.gui.UiFrameworkDebugLayout;
import com.imgood.textech.gui.container.ContainerUiFrameworkDebug;
import com.imgood.textech.gui.custom.ADM_UiContainer;
import com.imgood.textech.gui.framework.AdmUiTheme;
import com.imgood.textech.gui.framework.NineSliceRegion;
import com.imgood.textech.gui.framework.UiButton;
import com.imgood.textech.gui.framework.UiIcon;
import com.imgood.textech.gui.framework.UiSlot;
import com.imgood.textech.gui.framework.UiText;
import com.imgood.textech.gui.framework.UiTextField;
import com.imgood.textech.gui.framework.UiThemes;
import com.imgood.textech.gui.framework.UiToggleButton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Showcase GUI for every {@code gui/framework} widget and {@code adm_ui_atlas.png} region map.
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
            demoField.drawBackground(demoField.delegate()
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
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawMainPanel(0, 0, xSize, ySize);
        int left = panelLeft();
        int top = panelTop();
        UiSlot.drawVanilla(
            left + UiFrameworkDebugLayout.COL_LEFT,
            top + UiFrameworkDebugLayout.ROW_SLOT);
        UiSlot.drawProcedural(
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

        drawComponentRow("UiPanel", "adm.label.ui_framework.component.ui_panel", UiFrameworkDebugLayout.ROW_TEXT - 12);
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

        drawComponentRow("UiButton", "adm.label.ui_framework.component.ui_button", UiFrameworkDebugLayout.ROW_BTN - 10);
        drawComponentRow(
            "UiButton(disabled)",
            "adm.label.ui_framework.component.ui_button_disabled",
            UiFrameworkDebugLayout.ROW_BTN_DISABLED - 10);
        drawComponentRow(
            "UiToggleButton",
            "adm.label.ui_framework.component.ui_toggle",
            UiFrameworkDebugLayout.ROW_TOGGLE - 10);
        drawComponentRow("UiSlot", "adm.label.ui_framework.component.ui_slot", UiFrameworkDebugLayout.ROW_SLOT - 10);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.slot_vanilla"),
            UiFrameworkDebugLayout.COL_LEFT,
            UiFrameworkDebugLayout.ROW_SLOT + 18);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.slot_procedural"),
            UiFrameworkDebugLayout.COL_LEFT + UiFrameworkDebugLayout.SLOT_GAP,
            UiFrameworkDebugLayout.ROW_SLOT + 18);

        drawComponentRow("UiTextField", "adm.label.ui_framework.component.ui_field", UiFrameworkDebugLayout.ROW_FIELD - 10);

        drawAtlasReference();
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.edit_hint"),
            UiFrameworkDebugLayout.COL_LEFT,
            ySize - 14);
    }

    private void drawSectionHeader(String text, int x, int y) {
        UiText.drawAccent(UiThemes.ADM, fontRendererObj, text, x, y);
    }

    private void drawComponentRow(String className, String descKey, int y) {
        UiText.drawLabel(UiThemes.ADM, fontRendererObj, className, UiFrameworkDebugLayout.COL_LEFT, y);
        String desc = I18n.format(descKey);
        if (desc != null && !desc.isEmpty() && !desc.equals(descKey)) {
            UiText.drawLabel(
                UiThemes.ADM,
                fontRendererObj,
                desc,
                UiFrameworkDebugLayout.COL_LEFT + 72,
                y);
        }
    }

    private void drawAtlasReference() {
        AdmUiTheme theme = AdmUiTheme.instance();
        int y = UiFrameworkDebugLayout.ROW_ATLAS_START;
        y = drawRegionLine(theme.mainPanel(), "mainPanel", y);
        y = drawRegionLine(theme.buttonNormal(), "buttonNormal", y);
        y = drawRegionLine(theme.buttonHover(), "buttonHover", y);
        y = drawRegionLine(theme.buttonDisabled(), "buttonDisabled", y);
        y = drawRegionLine(theme.textFieldNormal(), "textFieldNormal", y);
        y = drawRegionLine(theme.textFieldFocused(), "textFieldFocused", y);
        UiText.drawLabel(
            UiThemes.ADM,
            fontRendererObj,
            I18n.format("adm.label.ui_framework.icon_grid"),
            UiFrameworkDebugLayout.COL_ATLAS,
            y);
    }

    private int drawRegionLine(NineSliceRegion region, String name, int y) {
        String line = name + " UV(" + region.u() + "," + region.v() + ") "
            + region.regionW() + "x" + region.regionH() + " b" + region.borderPx();
        UiText.drawLabel(UiThemes.ADM, fontRendererObj, line, UiFrameworkDebugLayout.COL_ATLAS, y);
        return y + UiFrameworkDebugLayout.ATLAS_LINE_H;
    }
}
