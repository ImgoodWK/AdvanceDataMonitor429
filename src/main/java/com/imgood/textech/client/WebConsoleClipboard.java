package com.imgood.textech.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class WebConsoleClipboard {

    private WebConsoleClipboard() {}

    public static boolean copy(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
