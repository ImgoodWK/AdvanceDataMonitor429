package com.imgood.textech.gui.framework;

/**
 * Static access to registered UI themes.
 */
public final class UiThemes {

    public static final UiTheme ADM = AdmUiTheme.instance();
    public static final UiTheme POCKET = PocketUiTheme.instance();

    private UiThemes() {}
}
