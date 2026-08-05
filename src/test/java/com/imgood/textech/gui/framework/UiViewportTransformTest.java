package com.imgood.textech.gui.framework;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Guards uniform low-resolution scaling and mouse-coordinate inversion. */
public class UiViewportTransformTest {

    @Test
    public void centeredPanelsFitAndRoundTripAcrossSupportedViewports() {
        assertScenario(320, 180, 600, 450, 8);
        assertScenario(427, 240, 600, 450, 8);
        assertScenario(854, 480, 600, 450, 8);
        assertScenario(1600, 450, 620, 500, 8);
    }

    private static void assertScenario(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
        int margin) {
        UiViewportTransform transform = UiViewportTransform.fitCenteredBounds(
            viewportWidth,
            viewportHeight,
            contentWidth,
            contentHeight,
            margin);
        assertTrue(transform.scale() > 0.0F);
        assertTrue(transform.scale() <= 1.0F);

        int contentLeft = (viewportWidth - contentWidth) / 2;
        int contentTop = (viewportHeight - contentHeight) / 2;
        int screenLeft = transform.toScreenX(contentLeft);
        int screenTop = transform.toScreenY(contentTop);
        int screenRight = transform.toScreenX(contentLeft + contentWidth);
        int screenBottom = transform.toScreenY(contentTop + contentHeight);
        assertTrue(screenLeft >= margin - 1);
        assertTrue(screenTop >= margin - 1);
        assertTrue(screenRight <= viewportWidth - margin + 1);
        assertTrue(screenBottom <= viewportHeight - margin + 1);

        assertRoundTrip(transform, 0, 0);
        assertRoundTrip(transform, viewportWidth / 2, viewportHeight / 2);
        assertRoundTrip(transform, viewportWidth - 1, viewportHeight - 1);
        assertRoundTrip(transform, contentLeft, contentTop);
        assertRoundTrip(transform, contentLeft + contentWidth, contentTop + contentHeight);
    }

    private static void assertRoundTrip(UiViewportTransform transform, int x, int y) {
        int logicalX = transform.toLogicalX(transform.toScreenX(x));
        int logicalY = transform.toLogicalY(transform.toScreenY(y));
        assertTrue("X round-trip exceeded one logical pixel", Math.abs(logicalX - x) <= 1);
        assertTrue("Y round-trip exceeded one logical pixel", Math.abs(logicalY - y) <= 1);
    }
}
