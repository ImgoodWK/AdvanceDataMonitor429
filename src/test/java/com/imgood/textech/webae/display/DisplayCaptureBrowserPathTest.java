package com.imgood.textech.webae.display;

import org.junit.Assert;
import org.junit.Test;

public class DisplayCaptureBrowserPathTest {

    @Test
    public void parsesRegSzDefaultPath() {
        String output = "\r\nHKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\chrome.exe\r\n"
            + "    (Default)    REG_SZ    C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\r\n";
        Assert.assertEquals(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            DisplayCaptureService.parseRegSzPath(output));
    }

    @Test
    public void cleansDisplayIconWithIndexSuffix() {
        Assert.assertEquals(
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\150.0.4078.83\\msedge.exe",
            DisplayCaptureService.cleanWindowsDisplayIcon(
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\150.0.4078.83\\msedge.exe,0"));
    }

    @Test
    public void cleansQuotedDisplayIcon() {
        Assert.assertEquals(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            DisplayCaptureService
                .cleanWindowsDisplayIcon("\"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\",0"));
    }
}
