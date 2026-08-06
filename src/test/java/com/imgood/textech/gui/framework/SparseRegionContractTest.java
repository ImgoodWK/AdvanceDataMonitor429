package com.imgood.textech.gui.framework;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** Verifies sparse single-placement chrome, cover cropping, fixed ratios, and feedback bands. */
public class SparseRegionContractTest {

    @Test
    public void admSparseChromeUsesOneUniformScaleAndFourPlacements() {
        SparseFrameRegion frame = AdmUiTheme.instance()
            .sparseMainFrame();
        assertEquals(1.0F, frame.uniformScaleFor(420, 260), 0.0F);
        int[][] positions = GuiBlitUtil.sparseChromePositions(frame, 10, 20, 420, 260, 1.0F);
        assertEquals(4, positions.length);
        assertArrayEquals(new int[] { 10, 20 }, positions[0]);
        assertArrayEquals(new int[] { 408, 20 }, positions[1]);
        assertArrayEquals(new int[] { 10, 258 }, positions[2]);
        assertArrayEquals(new int[] { 408, 258 }, positions[3]);
        assertEquals(
            4,
            Arrays.stream(positions)
                .map(Arrays::toString)
                .distinct()
                .count());
        assertEquals(0.5F, frame.uniformScaleFor(22, 22), 0.0001F);
    }

    @Test
    public void coverCropPreservesOneScaleOnBothAxes() {
        double[] wide = GuiBlitUtil.coverCrop(64, 64, 200, 100);
        assertArrayEquals(new double[] { 0.0D, 16.0D, 64.0D, 32.0D }, wide, 0.0001D);
        assertEquals(200.0D / wide[2], 100.0D / wide[3], 0.000001D);

        double[] tall = GuiBlitUtil.coverCrop(64, 64, 100, 200);
        assertArrayEquals(new double[] { 16.0D, 0.0D, 32.0D, 64.0D }, tall, 0.0001D);
        assertEquals(100.0D / tall[2], 200.0D / tall[3], 0.000001D);
        assertNull(GuiBlitUtil.coverCrop(0, 64, 100, 100));
    }

    @Test
    public void buttonWidthsNormalizeToApprovedFamiliesAndHitShellWidth() {
        assertEquals(20, FixedAspectButtonFamily.normalizedWidthFor(20, 20));
        assertEquals(40, FixedAspectButtonFamily.normalizedWidthFor(40, 20));
        assertEquals(57, FixedAspectButtonFamily.normalizedWidthFor(58, 20));
        assertEquals(76, FixedAspectButtonFamily.normalizedWidthFor(78, 20));
        assertEquals(100, FixedAspectButtonFamily.normalizedWidthFor(120, 20));
        assertEquals(180, FixedAspectButtonFamily.normalizedWidthFor(180, 20));
        assertEquals(228, FixedAspectButtonFamily.normalizedWidthFor(235, 20));
        assertEquals(80, FixedAspectButtonFamily.normalizedWidthFor(98, 16));
        assertEquals(16, FixedAspectButtonFamily.normalizedHeightFor(40, 20));
        assertEquals(19, FixedAspectButtonFamily.normalizedHeightFor(58, 20));
        assertEquals(18, FixedAspectButtonFamily.normalizedHeightFor(180, 20));

        UiButton button = new UiButton(10, 20, 120, 20);
        assertEquals(20, button.x());
        assertEquals(100, button.width());
        assertTrue(button.hitTest(119, 39));
        assertTrue(!button.hitTest(120, 39));

        int[][] requested = { { 40, 20 }, { 58, 20 }, { 72, 20 }, { 90, 20 }, { 235, 20 }, { 10, 10 } };
        for (int[] bounds : requested) {
            assertTrue(FixedAspectButtonFamily.normalizedWidthFor(bounds[0], bounds[1]) <= bounds[0]);
            assertTrue(FixedAspectButtonFamily.normalizedHeightFor(bounds[0], bounds[1]) <= bounds[1]);
        }
    }

    @Test
    public void underlineCropAndVisualStateSelectionAreDeterministic() {
        UnderlineFieldRegion field = AdmUiTheme.instance()
            .underlineField();
        AtlasRegion longest = field.style(UnderlineFieldRegion.State.NORMAL)
            .bottom();
        AtlasRegion fullWidth = field.centeredBottomCrop(UnderlineFieldRegion.State.NORMAL, 480);
        assertEquals(longest.u(), fullWidth.u());
        assertEquals(longest.v(), fullWidth.v());
        assertEquals(longest.width(), fullWidth.width());
        assertEquals(longest.height(), fullWidth.height());
        AtlasRegion crop = field.centeredBottomCrop(UnderlineFieldRegion.State.NORMAL, 100);
        assertEquals(longest.u() + 190, crop.u());
        assertEquals(100, crop.width());
        assertEquals(UnderlineFieldRegion.State.NORMAL, UnderlineFieldRegion.stateFor(true, false, false));
        assertEquals(UnderlineFieldRegion.State.FOCUSED, UnderlineFieldRegion.stateFor(true, false, true));
        assertEquals(UnderlineFieldRegion.State.INVALID, UnderlineFieldRegion.stateFor(true, true, true));
        assertEquals(UnderlineFieldRegion.State.DISABLED, UnderlineFieldRegion.stateFor(false, true, true));
    }

    @Test
    public void feedbackBandWrapLimitCannotOverlapFollowingControls() {
        assertEquals(3, UiFeedbackArea.maxLines(30, UiFeedbackArea.DEFAULT_LINE_HEIGHT));
        assertEquals(Arrays.asList("a", "b", "c"), UiFeedbackArea.firstLines(Arrays.asList("a", "b", "c", "d"), 3));
        assertTrue(
            UiFeedbackArea.firstLines(Arrays.asList("a"), 0)
                .isEmpty());

        UiFeedbackArea feedback = UiFeedbackArea.afterControls(10, 20, 180, 120, 96, 4, 8, 30);
        assertEquals(102, feedback.y());
        assertEquals(30, feedback.height());
        assertTrue(feedback.y() >= 96 + 4);
    }

    @Test
    public void fittedButtonShellsCannotCreateAnOverlapBetweenSeparateRequests() {
        int[] left = fittedBounds(10, 20, 58, 20);
        int[] right = fittedBounds(72, 20, 58, 20);
        assertTrue(left[0] + left[2] <= right[0]);

        int[] upper = fittedBounds(20, 30, 98, 16);
        int[] lower = fittedBounds(20, 48, 98, 16);
        assertTrue(upper[1] + upper[3] <= lower[1]);
    }

    private static int[] fittedBounds(int x, int y, int width, int height) {
        int fittedWidth = FixedAspectButtonFamily.normalizedWidthFor(width, height);
        int fittedHeight = FixedAspectButtonFamily.normalizedHeightFor(width, height);
        return new int[] { x + (width - fittedWidth) / 2, y + (height - fittedHeight) / 2, fittedWidth, fittedHeight };
    }

    @Test
    public void admThemeDoesNotExposeLegacyTiledPrimitivesOnItsPrimaryPath() {
        UiTheme theme = AdmUiTheme.instance();
        assertNull(theme.mainFrame());
        assertNull(theme.sectionFrame());
        assertNull(theme.buttonNormalBar());
        assertNull(theme.buttonHoverBar());
        assertNull(theme.buttonPressedBar());
        assertNull(theme.buttonDisabledBar());
        assertNull(theme.textFieldNormalBar());
        assertNull(theme.textFieldFocusedBar());
    }
}
