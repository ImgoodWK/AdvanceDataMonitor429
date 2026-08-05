package com.imgood.textech.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/** Guards the repository-wide contract that every non-pocket in-game screen uses the ADM theme. */
public class GuiThemeCoverageTest {

    private static final Path GUI_SOURCE_ROOT = Paths.get("src/main/java/com/imgood/textech/gui/guiscreen");
    private static final Set<String> POCKET_EXCLUSIONS = new HashSet<>(
        Arrays.asList("GuiDimensionalPocketConfig.java", "GuiPocketStorage.java"));
    private static final Pattern THEMED_BASE = Pattern
        .compile("extends\\s+(ADM_GuiScreen|AdmItemConfigScreen|AbstractMonitorSubGui|ADM_UiContainer|AdmUiScreen)\\b");
    private static final Pattern RAW_GUI_SCREEN = Pattern.compile("extends\\s+GuiScreen\\b");
    private static final Pattern DEFAULT_BACKGROUND_CALL = Pattern.compile("\\.drawDefaultBackground\\s*\\(|\\bdrawDefaultBackground\\s*\\(");
    private static final Pattern VANILLA_BUTTON = Pattern.compile("new\\s+GuiButton\\s*\\(");
    private static final Pattern STANDALONE_GUI_TEXTURE = Pattern.compile("textures/gui/[^\\\"]+\\.png");
    private static final Pattern LEGACY_ADM_CHROME_CALL = Pattern.compile(
        "GuiBlitUtil\\s*\\.\\s*(drawNineSlice|drawHorizontalSlice|drawTiled(?:Frame|Bar)?)\\s*\\(");

    @Test
    public void everyNonPocketGuiUsesTheAdmTheme() throws IOException {
        assertTrue("GUI source root is missing: " + GUI_SOURCE_ROOT, Files.isDirectory(GUI_SOURCE_ROOT));

        List<Path> sources;
        try (Stream<Path> stream = Files.list(GUI_SOURCE_ROOT)) {
            sources = stream.filter(
                path -> path.getFileName()
                    .toString()
                    .endsWith(".java"))
                .sorted()
                .collect(Collectors.toList());
        }

        assertFalse("No GUI sources were discovered", sources.isEmpty());
        Set<String> observedPocketExclusions = new HashSet<>();
        int themedScreens = 0;
        for (Path sourcePath : sources) {
            String fileName = sourcePath.getFileName()
                .toString();
            String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            if (POCKET_EXCLUSIONS.contains(fileName)) {
                observedPocketExclusions.add(fileName);
                continue;
            }

            boolean inheritsTheme = THEMED_BASE.matcher(source)
                .find();
            assertTrue(
                fileName + " must inherit an ADM host",
                inheritsTheme);
            assertFalse(fileName + " must not inherit raw GuiScreen", RAW_GUI_SCREEN.matcher(source).find());
            assertFalse(
                fileName + " must not invoke the vanilla dim/black background",
                DEFAULT_BACKGROUND_CALL.matcher(source).find());
            assertFalse(
                fileName + " must not instantiate a vanilla GuiButton",
                VANILLA_BUTTON.matcher(source)
                    .find());
            assertFalse(
                fileName + " must not bind a standalone legacy GUI PNG",
                STANDALONE_GUI_TEXTURE.matcher(source)
                    .find());
            assertFalse(
                fileName + " must use sparse panels, complete controls, or cover-cropped exact regions",
                LEGACY_ADM_CHROME_CALL.matcher(source)
                    .find());
            themedScreens++;
        }

        assertEquals("The permanent pocket exclusion list changed", POCKET_EXCLUSIONS, observedPocketExclusions);
        assertTrue("The GUI inventory unexpectedly shrank", themedScreens >= 28);
    }
}
