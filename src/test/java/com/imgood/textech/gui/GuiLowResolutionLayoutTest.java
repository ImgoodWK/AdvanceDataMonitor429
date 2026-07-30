package com.imgood.textech.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
        int atlasBottom = UiFrameworkDebugLayout.ROW_ATLAS_START
            + UiFrameworkDebugLayout.ATLAS_REGION_COUNT * UiFrameworkDebugLayout.ATLAS_LINE_H
            + 9;
        assertTrue(atlasBottom < UiFrameworkDebugLayout.ROW_FLEX);
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
}
