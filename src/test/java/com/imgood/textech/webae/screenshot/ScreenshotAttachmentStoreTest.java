package com.imgood.textech.webae.screenshot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenshotAttachmentStoreTest {

    @Test
    public void acceptsOnlyServerGeneratedHexIds() {
        assertTrue(ScreenshotAttachmentStore.isValidId("0123456789abcdef0123456789abcdef"));
        assertFalse(ScreenshotAttachmentStore.isValidId("../0123456789abcdef0123456789abc"));
        assertFalse(ScreenshotAttachmentStore.isValidId("0123456789ABCDEF0123456789ABCDEF"));
        assertFalse(ScreenshotAttachmentStore.isValidId("short"));
        assertFalse(ScreenshotAttachmentStore.isValidId(null));
    }
}
