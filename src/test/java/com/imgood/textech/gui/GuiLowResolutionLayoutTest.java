package com.imgood.textech.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.imgood.textech.gui.framework.UiViewportTransform;

/** Contracts for the 427x240 scaled viewport used by the framebuffer GUI QA runner. */
public class GuiLowResolutionLayoutTest {

    private static final int QA_WIDTH = 427;
    private static final int QA_HEIGHT = 240;

    @Test
    public void preferredPanelsFitInsideQaViewportWithMargin() {
        GuiResponsiveLayout.Panel chat = GuiResponsiveLayout.fitCentered(QA_WIDTH, QA_HEIGHT, 600, 450, 4);
        assertPanel(chat, QA_WIDTH, QA_HEIGHT, 4, 4, 419, 232);

        GuiResponsiveLayout.Panel settings = GuiResponsiveLayout.fitCentered(QA_WIDTH, QA_HEIGHT, 620, 500, 4);
        assertPanel(settings, QA_WIDTH, QA_HEIGHT, 4, 4, 419, 232);
    }

    @Test
    public void preferredPanelsRemainCenteredWhenViewportIsLargeEnough() {
        GuiResponsiveLayout.Panel panel = GuiResponsiveLayout.fitCentered(800, 600, 600, 450, 4);
        assertPanel(panel, 800, 600, 100, 75, 600, 450);
    }

    @Test
    public void frameworkShowcaseFitsQaViewportAndKeepsColumnsSeparated() {
        assertTrue(UiFrameworkDebugLayout.GUI_W <= QA_WIDTH - 8);
        assertTrue(UiFrameworkDebugLayout.GUI_H <= QA_HEIGHT - 8);
        assertTrue(UiFrameworkDebugLayout.COL_LEFT + UiFrameworkDebugLayout.FIELD_W < UiFrameworkDebugLayout.COL_ATLAS);
        assertTrue(
            UiFrameworkDebugLayout.ROW_FIELD + UiFrameworkDebugLayout.FIELD_H <= UiFrameworkDebugLayout.GUI_H - 7);
        assertTrue(UiFrameworkDebugLayout.ROW_FLEX + UiFrameworkDebugLayout.FLEX_H <= UiFrameworkDebugLayout.GUI_H - 8);
        assertTrue(UiFrameworkDebugLayout.FLEX_X >= UiFrameworkDebugLayout.LEFT_COLUMN_RIGHT);
        int atlasBottom = UiFrameworkDebugLayout.ROW_ATLAS_START
            + UiFrameworkDebugLayout.ATLAS_REGION_COUNT * UiFrameworkDebugLayout.ATLAS_LINE_H
            + 9;
        assertTrue(atlasBottom < UiFrameworkDebugLayout.ROW_FLEX);
        int stateSamplesBottom = UiFrameworkDebugLayout.ROW_ATLAS_START
            + UiFrameworkDebugLayout.ATLAS_REGION_COUNT * UiFrameworkDebugLayout.ATLAS_LINE_H
            + 11
            + 16;
        assertTrue(stateSamplesBottom < UiFrameworkDebugLayout.ROW_FLEX);
    }

    @Test
    public void monitorThresholdControlsFitTheLowResolutionViewport() {
        final int panelWidth = 600;
        final int panelHeight = 480;
        final int offsetX = QA_WIDTH / 2 - 270;
        final int offsetY = QA_HEIGHT / 2 - 200;
        UiViewportTransform transform = UiViewportTransform
            .fitCenteredBounds(QA_WIDTH, QA_HEIGHT, panelWidth, panelHeight, 8);

        // AbstractMonitorSubGui centers its panel at offsetX - 20 / offsetY - 35.
        assertProjectedRectInsideViewport(transform, offsetX - 20, offsetY - 35, panelWidth, panelHeight);

        // GuiSubAdvanceDataMonitor's threshold enable/operator controls (IDs 40/41).
        assertProjectedRectInsideViewport(transform, offsetX + 435, offsetY + 20, 155, 20);
        assertProjectedRectInsideViewport(transform, offsetX + 435, offsetY + 50, 155, 20);

        // ADM_GuiTextField visually shifts the constructor coordinates by -21/-8 and draws a 2px border.
        for (int row = 0; row < 4; row++) {
            assertProjectedRectInsideViewport(transform, offsetX + 479, offsetY + 102 + row * 25, 82, 22);
        }
    }

    private static void assertPanel(GuiResponsiveLayout.Panel panel, int screenWidth, int screenHeight, int x, int y,
        int width, int height) {
        assertEquals(x, panel.x());
        assertEquals(y, panel.y());
        assertEquals(width, panel.width());
        assertEquals(height, panel.height());
        assertTrue(panel.x() >= 0);
        assertTrue(panel.y() >= 0);
        assertTrue(panel.x() + panel.width() <= screenWidth);
        assertTrue(panel.y() + panel.height() <= screenHeight);
    }

    private static void assertProjectedRectInsideViewport(UiViewportTransform transform, int x, int y, int width,
        int height) {
        int left = transform.toScreenX(x);
        int top = transform.toScreenY(y);
        int right = transform.toScreenX(x + width);
        int bottom = transform.toScreenY(y + height);
        assertTrue("control starts left of the viewport", Math.min(left, right) >= 0);
        assertTrue("control starts above the viewport", Math.min(top, bottom) >= 0);
        assertTrue("control ends right of the viewport", Math.max(left, right) <= QA_WIDTH);
        assertTrue("control ends below the viewport", Math.max(top, bottom) <= QA_HEIGHT);
    }
}
