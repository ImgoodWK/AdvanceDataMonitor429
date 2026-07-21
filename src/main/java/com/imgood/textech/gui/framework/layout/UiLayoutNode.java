package com.imgood.textech.gui.framework.layout;

import java.util.List;

/**
 * Layout-facing node contract used by {@link UiFlexLayoutEngine} (no Minecraft types).
 */
public interface UiLayoutNode {

    List<? extends UiLayoutNode> layoutChildren();

    boolean isFlexContainer();

    UiFlexDirection flexDirection();

    UiMainAlign mainAlign();

    UiCrossAlign crossAlign();

    UiAlignSelf alignSelf();

    UiInsets padding();

    UiInsets margin();

    int gap();

    /** Preferred width; {@code <= 0} means content-sized. */
    int preferredWidth();

    /** Preferred height; {@code <= 0} means content-sized. */
    int preferredHeight();

    float grow();

    float shrink();

    boolean isAbsolute();

    int absoluteX();

    int absoluteY();

    boolean isVisible();

    void setLayoutBox(UiLayoutBox box);

    UiLayoutBox getLayoutBox();

    /**
     * Intrinsic content width (excluding own margin; padding included for containers after children
     * measure).
     */
    int measureWidth(UiConstraints constraints);

    /** Intrinsic content height for a resolved width. */
    int measureHeight(UiConstraints constraints, int width);
}
