package com.imgood.textech.gui;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Locks the existing monitor-binding save protocol while threshold controls evolve. */
public class GuiSubAdvanceDataMonitorThresholdContractTest {

    private static final Path SOURCE = Paths
        .get("src/main/java/com/imgood/textech/gui/guiscreen/GuiSubAdvanceDataMonitor.java");

    @Test
    public void thresholdControlsAreReachableAndUseTheExistingTileSyncChain() throws IOException {
        assertTrue("Monitor configuration GUI source is missing: " + SOURCE, Files.isRegularFile(SOURCE));
        String source = new String(Files.readAllBytes(SOURCE), StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertContains(source, "textFieldThresholdValue");
        assertContains(source, "textFieldThresholdHysteresis");
        assertContains(source, "textFieldThresholdOutputMin");
        assertContains(source, "textFieldThresholdOutputMax");
        assertContains(source, "autoTextField(\"Threshold\"");
        assertContains(source, "textFieldsRight.addAll(thresholdFields)");

        assertContains(source, "button(\n                40,");
        assertContains(source, "monitorButton(\n                41,");
        assertContains(source, "case 40 ->");
        assertContains(source, "case 41 ->");

        assertContains(source, "nbt.setTag(MonitorWidgetSpec.THRESHOLD_KEY, threshold)");
        assertContains(source, "saveAndSync(nbt)");
    }

    private static void assertContains(String source, String expected) {
        assertTrue("Missing GUI threshold contract: " + expected, source.contains(expected));
    }
}
