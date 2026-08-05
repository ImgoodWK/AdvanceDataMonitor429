package com.imgood.textech.gui.framework;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.Test;

/** Locks the approved sparse ADM runtime atlas, its four-corner frames, and all control states. */
public class AdmUiAtlasContractTest {

    private static final String ATLAS_RESOURCE = "/assets/textech/textures/gui/adm_ui_atlas.png";
    private static final String APPROVED_SHA256 = "203244455b02bff5a996bcd3df4f89788545af87e148848b1c3f7667569c4a91";

    @Test
    public void approvedAtlasHasExpectedIdentityAndChannels() throws Exception {
        byte[] bytes = readAtlasBytes();
        BufferedImage atlas = readAtlas(bytes);
        assertEquals(APPROVED_SHA256, sha256(bytes));
        assertEquals(AdmUiTheme.ATLAS_SIZE, atlas.getWidth());
        assertEquals(AdmUiTheme.ATLAS_SIZE, atlas.getHeight());
        assertTrue(atlas.getColorModel().hasAlpha());
    }

    @Test
    public void sparseFramesUseFourIndependentVisibleCornersAndFixedGlassAlpha() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        AdmUiTheme theme = AdmUiTheme.instance();
        SparseFrameRegion main = theme.sparseMainFrame();
        SparseFrameRegion section = theme.sparseSectionFrame();

        AtlasRegion[] mainPieces = {
            main.topLeft(), main.topRight(), main.bottomLeft(), main.bottomRight()
        };
        AtlasRegion[] sectionPieces = {
            section.topLeft(), section.topRight(), section.bottomLeft(), section.bottomRight()
        };
        assertDistinctVisible(atlas, "main sparse chrome", mainPieces);
        assertDistinctVisible(atlas, "section sparse chrome", sectionPieces);
        assertRegion(main.topLeft(), 0, 0, 22, 22);
        assertRegion(main.background(), 264, 0, 64, 64);
        assertRegion(section.topLeft(), 0, 66, 14, 14);
        assertRegion(section.background(), 156, 66, 64, 64);
        assertEquals(56, centerAlpha(atlas, main.background()));
        assertEquals(72, centerAlpha(atlas, section.background()));
        assertRegion(theme.titleOrnament(), 222, 66, 160, 12);
        assertRegion(theme.footerOrnament(), 222, 80, 160, 8);

        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 48, 0, 96, 8));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 146, 0, 96, 8));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 244, 0, 8, 64));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 254, 0, 8, 64));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 32, 66, 52, 6));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 86, 66, 52, 6));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 140, 66, 6, 34));
        assertTransparent(atlas, new AtlasRegion(AdmUiTheme.ATLAS, AdmUiTheme.ATLAS_SIZE, 148, 66, 6, 34));
    }

    @Test
    public void completeButtonFamiliesContainSevenRatiosAndFourDistinctStates() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        FixedAspectButtonFamily family = AdmUiTheme.instance().fixedAspectButtons();
        int[] widths = { 20, 50, 60, 80, 100, 200, 240 };
        for (int width : widths) {
            Set<Integer> stateSignatures = new HashSet<Integer>();
            for (FixedAspectButtonFamily.State state : FixedAspectButtonFamily.State.values()) {
                AtlasRegion region = family.region(state, width, FixedAspectButtonFamily.BASE_HEIGHT);
                assertEquals(width, region.width());
                assertEquals(FixedAspectButtonFamily.BASE_HEIGHT, region.height());
                assertVisible(atlas, state + "." + width, region);
                stateSignatures.add(pixelSignature(atlas, region));
            }
            assertEquals("button states must remain visually distinct for width " + width, 4, stateSignatures.size());
        }
    }

    @Test
    public void underlineFieldsContainAllFourStatesAndLongestCenteredCropSource() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        UnderlineFieldRegion field = AdmUiTheme.instance().underlineField();
        Set<Integer> bottoms = new HashSet<Integer>();
        for (UnderlineFieldRegion.State state : UnderlineFieldRegion.State.values()) {
            UnderlineFieldRegion.Style style = field.style(state);
            assertEquals(3, style.left().width());
            assertEquals(20, style.left().height());
            assertEquals(3, style.right().width());
            assertEquals(480, style.bottom().width());
            assertEquals(3, style.bottom().height());
            assertVisible(atlas, state + ".left", style.left());
            assertVisible(atlas, state + ".right", style.right());
            assertVisible(atlas, state + ".bottom", style.bottom());
            bottoms.add(pixelSignature(atlas, style.bottom()));
        }
        assertEquals(4, bottoms.size());
        AtlasRegion centered = field.centeredBottomCrop(UnderlineFieldRegion.State.INVALID, 80);
        assertEquals(200, centered.u());
        assertEquals(80, centered.width());
    }

    @Test
    public void semanticIconGridsContainNormalAndHoverVariants() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        AdmUiTheme theme = AdmUiTheme.instance();
        assertEquals(350, theme.iconGridV());
        assertEquals(398, theme.iconHoverGridV());
        Set<Integer> signatures = new HashSet<Integer>();
        for (int visualState = 0; visualState < 2; visualState++) {
            int originV = visualState == 0 ? theme.iconGridV() : theme.iconHoverGridV();
            for (int icon = 0; icon < 17; icon++) {
                AtlasRegion region = new AtlasRegion(
                    AdmUiTheme.ATLAS,
                    AdmUiTheme.ATLAS_SIZE,
                    theme.iconGridU() + icon % 8 * theme.iconSize(),
                    originV + icon / 8 * theme.iconSize(),
                    theme.iconSize(),
                    theme.iconSize());
                assertVisible(atlas, "icon." + visualState + "." + icon, region);
                signatures.add(pixelSignature(atlas, region));
            }
        }
        assertTrue(signatures.size() >= 30);
    }

    private static void assertRegion(AtlasRegion region, int u, int v, int width, int height) {
        assertNotNull(region);
        assertEquals(AdmUiTheme.ATLAS, region.texture());
        assertEquals(u, region.u());
        assertEquals(v, region.v());
        assertEquals(width, region.width());
        assertEquals(height, region.height());
    }

    private static void assertDistinctVisible(BufferedImage atlas, String name, AtlasRegion[] regions) {
        Set<Integer> signatures = new HashSet<Integer>();
        for (AtlasRegion region : regions) {
            assertVisible(atlas, name, region);
            signatures.add(pixelSignature(atlas, region));
        }
        assertEquals(name, regions.length, signatures.size());
    }

    private static void assertVisible(BufferedImage atlas, String name, AtlasRegion region) {
        int visible = 0;
        for (int y = region.v(); y < region.v() + region.height(); y++) {
            for (int x = region.u(); x < region.u() + region.width(); x++) {
                if ((atlas.getRGB(x, y) >>> 24) != 0) visible++;
            }
        }
        assertTrue(name + " is transparent/empty", visible > 0);
    }

    private static void assertTransparent(BufferedImage atlas, AtlasRegion region) {
        for (int y = region.v(); y < region.v() + region.height(); y++) {
            for (int x = region.u(); x < region.u() + region.width(); x++) {
                assertEquals("removed edge ornament contains visible pixels", 0, atlas.getRGB(x, y) >>> 24);
            }
        }
    }

    private static int pixelSignature(BufferedImage atlas, AtlasRegion region) {
        int signature = 1;
        for (int y = region.v(); y < region.v() + region.height(); y++) {
            for (int x = region.u(); x < region.u() + region.width(); x++) {
                signature = 31 * signature + atlas.getRGB(x, y);
            }
        }
        return signature;
    }

    private static int centerAlpha(BufferedImage atlas, AtlasRegion region) {
        return atlas.getRGB(region.u() + region.width() / 2, region.v() + region.height() / 2) >>> 24;
    }

    private static byte[] readAtlasBytes() throws IOException {
        try (InputStream input = AdmUiAtlasContractTest.class.getResourceAsStream(ATLAS_RESOURCE)) {
            assertNotNull("Missing runtime atlas " + ATLAS_RESOURCE, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private static BufferedImage readAtlas(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image);
        return image;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xFF));
        return hex.toString();
    }
}
