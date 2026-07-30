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
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.Test;

/** Verifies the approved Meowa-derived runtime atlas and the Java UV contract stay synchronized. */
public class AdmUiAtlasContractTest {

    private static final String ATLAS_RESOURCE = "/assets/textech/textures/gui/adm_ui_atlas.png";
    private static final String APPROVED_SHA256 = "0038210d8ba910b9e93a67b3ed628ecac9d7057962148d8aa510e90886e4f029";

    @Test
    public void approvedAtlasHasExpectedIdentityAndChannels() throws Exception {
        byte[] bytes = readAtlasBytes();
        BufferedImage atlas = readAtlas(bytes);

        assertEquals(APPROVED_SHA256, sha256(bytes));
        assertEquals(AdmUiTheme.ATLAS_SIZE, atlas.getWidth());
        assertEquals(AdmUiTheme.ATLAS_SIZE, atlas.getHeight());
        assertTrue(
            "ADM atlas must retain an alpha channel",
            atlas.getColorModel()
                .hasAlpha());

        int minAlpha = 255;
        int maxAlpha = 0;
        for (int y = 0; y < atlas.getHeight(); y++) {
            for (int x = 0; x < atlas.getWidth(); x++) {
                int alpha = atlas.getRGB(x, y) >>> 24;
                minAlpha = Math.min(minAlpha, alpha);
                maxAlpha = Math.max(maxAlpha, alpha);
            }
        }
        assertEquals("Transparent padding is part of the atlas contract", 0, minAlpha);
        assertEquals("Theme chrome must contain fully opaque pixels", 255, maxAlpha);
    }

    @Test
    public void themeRegionsMatchApprovedLayoutAndContainVisiblePixels() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        AdmUiTheme theme = AdmUiTheme.instance();

        NineSliceRegion main = assertRegion(atlas, "mainPanel", theme.mainPanel(), 0, 0, 96, 108, 10);
        NineSliceRegion section = assertRegion(atlas, "sectionPanel", theme.sectionPanel(), 100, 0, 88, 56, 8);
        NineSliceRegion buttonNormal = assertRegion(atlas, "buttonNormal", theme.buttonNormal(), 100, 60, 48, 20, 8);
        NineSliceRegion buttonHover = assertRegion(atlas, "buttonHover", theme.buttonHover(), 100, 82, 48, 20, 8);
        NineSliceRegion buttonPressed = assertRegion(
            atlas,
            "buttonPressed",
            theme.buttonPressed(),
            100,
            104,
            48,
            20,
            8);
        NineSliceRegion buttonDisabled = assertRegion(
            atlas,
            "buttonDisabled",
            theme.buttonDisabled(),
            100,
            126,
            48,
            20,
            8);
        NineSliceRegion fieldNormal = assertRegion(
            atlas,
            "textFieldNormal",
            theme.textFieldNormal(),
            152,
            60,
            48,
            20,
            6);
        NineSliceRegion fieldFocused = assertRegion(
            atlas,
            "textFieldFocused",
            theme.textFieldFocused(),
            204,
            60,
            48,
            20,
            6);
        NineSliceRegion slot = assertRegion(atlas, "slot", theme.slot(), 152, 84, 18, 18, 3);
        NineSliceRegion scrollTrack = assertRegion(atlas, "scrollTrack", theme.scrollTrack(), 174, 84, 10, 42, 3);
        NineSliceRegion scrollThumb = assertRegion(atlas, "scrollThumb", theme.scrollThumb(), 188, 84, 10, 20, 3);
        NineSliceRegion divider = assertRegion(atlas, "divider", theme.divider(), 204, 84, 48, 4, 1);
        NineSliceRegion toggleOff = assertRegion(atlas, "toggleOff", theme.toggleOff(), 152, 108, 28, 14, 4);
        NineSliceRegion toggleOn = assertRegion(atlas, "toggleOn", theme.toggleOn(), 184, 108, 28, 14, 4);
        NineSliceRegion toggleDisabled = assertRegion(
            atlas,
            "toggleDisabled",
            theme.toggleDisabled(),
            216,
            108,
            28,
            14,
            4);
        NineSliceRegion checkOff = assertRegion(atlas, "checkOff", theme.checkOff(), 152, 126, 14, 14, 3);
        NineSliceRegion checkOn = assertRegion(atlas, "checkOn", theme.checkOn(), 170, 126, 14, 14, 3);
        NineSliceRegion checkDisabled = assertRegion(
            atlas,
            "checkDisabled",
            theme.checkDisabled(),
            188,
            126,
            14,
            14,
            3);

        assertDistinct(atlas, "panel", main, section);
        assertDistinct(atlas, "button states", buttonNormal, buttonHover, buttonPressed, buttonDisabled);
        assertDistinct(atlas, "text field states", fieldNormal, fieldFocused);
        assertDistinct(atlas, "scroll pieces", scrollTrack, scrollThumb);
        assertDistinct(atlas, "toggle states", toggleOff, toggleOn, toggleDisabled);
        assertDistinct(atlas, "check states", checkOff, checkOn, checkDisabled);
        assertTrue(
            "Slot and divider regions must differ",
            pixelSignature(atlas, slot) != pixelSignature(atlas, divider));
    }

    @Test
    public void iconGridContainsNormalAndHoverRows() throws Exception {
        BufferedImage atlas = readAtlas(readAtlasBytes());
        AdmUiTheme theme = AdmUiTheme.instance();
        assertEquals(0, theme.iconGridU());
        assertEquals(160, theme.iconGridV());
        assertEquals(16, theme.iconSize());

        Set<Integer> signatures = new HashSet<>();
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 7; column++) {
                int u = theme.iconGridU() + column * theme.iconSize();
                int v = theme.iconGridV() + row * theme.iconSize();
                NineSliceRegion icon = new NineSliceRegion(
                    AdmUiTheme.ATLAS,
                    AdmUiTheme.ATLAS_SIZE,
                    u,
                    v,
                    theme.iconSize(),
                    theme.iconSize(),
                    0);
                assertVisible(atlas, "icon_" + row + "_" + column, icon);
                signatures.add(pixelSignature(atlas, icon));
            }
        }
        assertTrue("The icon grid must contain multiple distinct glyphs/states", signatures.size() >= 10);
    }

    private static NineSliceRegion assertRegion(BufferedImage atlas, String name, NineSliceRegion region, int u, int v,
        int width, int height, int border) {
        assertNotNull(name + " region is missing", region);
        assertEquals(AdmUiTheme.ATLAS, region.texture());
        assertEquals(AdmUiTheme.ATLAS_SIZE, region.atlasSize());
        assertEquals(name + " u", u, region.u());
        assertEquals(name + " v", v, region.v());
        assertEquals(name + " width", width, region.regionW());
        assertEquals(name + " height", height, region.regionH());
        assertEquals(name + " border", border, region.borderPx());
        assertTrue(name + " exceeds atlas width", region.u() + region.regionW() <= atlas.getWidth());
        assertTrue(name + " exceeds atlas height", region.v() + region.regionH() <= atlas.getHeight());
        assertVisible(atlas, name, region);
        return region;
    }

    private static void assertVisible(BufferedImage atlas, String name, NineSliceRegion region) {
        int visiblePixels = 0;
        Set<Integer> visibleColors = new HashSet<>();
        for (int y = region.v(); y < region.v() + region.regionH(); y++) {
            for (int x = region.u(); x < region.u() + region.regionW(); x++) {
                int argb = atlas.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    visiblePixels++;
                    visibleColors.add(argb);
                }
            }
        }
        assertTrue(name + " is transparent/empty", visiblePixels > 0);
        assertTrue(name + " has no visual detail", visibleColors.size() > 1);
    }

    private static void assertDistinct(BufferedImage atlas, String family, NineSliceRegion... regions) {
        Set<Integer> signatures = new HashSet<>();
        for (NineSliceRegion region : regions) {
            signatures.add(pixelSignature(atlas, region));
        }
        assertEquals(family + " must use distinct atlas pixels", regions.length, signatures.size());
    }

    private static int pixelSignature(BufferedImage atlas, NineSliceRegion region) {
        int signature = 1;
        for (int y = region.v(); y < region.v() + region.regionH(); y++) {
            for (int x = region.u(); x < region.u() + region.regionW(); x++) {
                signature = 31 * signature + atlas.getRGB(x, y);
            }
        }
        return signature;
    }

    private static byte[] readAtlasBytes() throws IOException {
        try (InputStream input = AdmUiAtlasContractTest.class.getResourceAsStream(ATLAS_RESOURCE)) {
            assertNotNull("Missing runtime atlas " + ATLAS_RESOURCE, input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static BufferedImage readAtlas(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull("Runtime atlas is not a readable PNG", image);
        return image;
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xFF));
        }
        return hex.toString();
    }
}
