package com.imgood.textech.gui.framework.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Flexbox layout engine (ROW/COLUMN, gap, grow/shrink, align, padding/margin).
 * Semantics aligned with Qz-UILib {@code FlexLayouter} (simplified, single-threaded).
 */
public final class UiFlexLayoutEngine {

    private UiFlexLayoutEngine() {}

    public static void layout(UiLayoutNode root, UiConstraints constraints) {
        if (root == null || constraints == null) {
            return;
        }
        layoutNode(root, constraints, 0, 0);
    }

    private static void layoutNode(UiLayoutNode node, UiConstraints constraints, int x, int y) {
        if (!node.isVisible()) {
            node.setLayoutBox(new UiLayoutBox(x, y, 0, 0));
            return;
        }
        if (node.isAbsolute()) {
            x = node.absoluteX();
            y = node.absoluteY();
        }

        int width = resolveWidth(node, constraints);
        int height = resolveHeight(node, constraints, width);
        node.setLayoutBox(new UiLayoutBox(x, y, width, height));

        if (node.isFlexContainer()) {
            layoutFlexChildren(node, width, height);
        } else {
            List<? extends UiLayoutNode> children = visibleChildren(node);
            for (int i = 0; i < children.size(); i++) {
                UiLayoutNode child = children.get(i);
                UiInsets m = child.margin();
                UiConstraints childC = UiConstraints.loose(
                    Math.max(
                        0,
                        width - node.padding()
                            .horizontal() - m.horizontal()),
                    Math.max(
                        0,
                        height - node.padding()
                            .vertical() - m.vertical()));
                layoutNode(child, childC, node.padding().left + m.left, node.padding().top + m.top);
            }
        }
    }

    private static void layoutFlexChildren(UiLayoutNode node, int width, int height) {
        UiInsets pad = node.padding();
        int innerW = Math.max(0, width - pad.horizontal());
        int innerH = Math.max(0, height - pad.vertical());
        boolean row = node.flexDirection() == UiFlexDirection.ROW;
        List<? extends UiLayoutNode> children = visibleChildren(node);
        int gap = node.gap();
        int childCount = children.size();
        int totalGap = childCount > 1 ? gap * (childCount - 1) : 0;

        int[] mainSizes = new int[childCount];
        int[] crossSizes = new int[childCount];
        float totalGrow = 0f;
        float totalShrink = 0f;
        int fixedMain = 0;

        for (int i = 0; i < childCount; i++) {
            UiLayoutNode child = children.get(i);
            UiInsets m = child.margin();
            int maxMain = row ? Math.max(0, innerW - m.horizontal()) : Math.max(0, innerH - m.vertical());
            int maxCross = row ? Math.max(0, innerH - m.vertical()) : Math.max(0, innerW - m.horizontal());
            UiConstraints measureC = row ? UiConstraints.loose(maxMain, maxCross)
                : UiConstraints.loose(maxCross, maxMain);

            int measuredW = child.preferredWidth() > 0 ? child.preferredWidth() : child.measureWidth(measureC);
            int measuredH = child.preferredHeight() > 0 ? child.preferredHeight()
                : child.measureHeight(measureC, measuredW);
            measuredW = measureC.constrainWidth(measuredW);
            measuredH = measureC.constrainHeight(measuredH);

            mainSizes[i] = row ? measuredW : measuredH;
            crossSizes[i] = row ? measuredH : measuredW;
            fixedMain += mainSizes[i] + (row ? m.horizontal() : m.vertical());
            totalGrow += Math.max(0f, child.grow());
            totalShrink += Math.max(0f, child.shrink());
        }

        int mainAvail = (row ? innerW : innerH) - totalGap;
        int free = mainAvail - fixedMain;
        if (free > 0 && totalGrow > 0f) {
            for (int i = 0; i < childCount; i++) {
                float g = Math.max(
                    0f,
                    children.get(i)
                        .grow());
                if (g > 0f) {
                    mainSizes[i] += Math.round(free * (g / totalGrow));
                }
            }
        } else if (free < 0 && totalShrink > 0f) {
            int deficit = -free;
            for (int i = 0; i < childCount; i++) {
                float s = Math.max(
                    0f,
                    children.get(i)
                        .shrink());
                if (s > 0f) {
                    int cut = Math.round(deficit * (s / totalShrink));
                    mainSizes[i] = Math.max(0, mainSizes[i] - cut);
                }
            }
        }

        int mainContent = totalGap;
        for (int i = 0; i < childCount; i++) {
            UiInsets m = children.get(i)
                .margin();
            mainContent += mainSizes[i] + (row ? m.horizontal() : m.vertical());
        }
        int mainStart = 0;
        int mainSpace = row ? innerW : innerH;
        switch (node.mainAlign()) {
            case CENTER:
                mainStart = Math.max(0, (mainSpace - mainContent) / 2);
                break;
            case END:
                mainStart = Math.max(0, mainSpace - mainContent);
                break;
            case START:
            default:
                mainStart = 0;
                break;
        }

        int cursor = (row ? pad.left : pad.top) + mainStart;
        for (int i = 0; i < childCount; i++) {
            UiLayoutNode child = children.get(i);
            UiInsets m = child.margin();
            int crossAvail = row ? innerH : innerW;
            UiCrossAlign cross = effectiveCross(node, child);
            int childCross = crossSizes[i];
            boolean stretch = cross == UiCrossAlign.STRETCH;
            int preferredCross = row ? child.preferredHeight() : child.preferredWidth();
            if (stretch && preferredCross <= 0) {
                childCross = Math.max(0, crossAvail - (row ? m.vertical() : m.horizontal()));
            }

            int crossPos;
            switch (cross) {
                case CENTER:
                    crossPos = Math.max(0, (crossAvail - childCross - (row ? m.vertical() : m.horizontal())) / 2);
                    break;
                case END:
                    crossPos = Math.max(0, crossAvail - childCross - (row ? m.vertical() : m.horizontal()));
                    break;
                case START:
                case STRETCH:
                default:
                    crossPos = 0;
                    break;
            }

            int nx;
            int ny;
            int nw;
            int nh;
            if (row) {
                nx = cursor + m.left;
                ny = pad.top + crossPos + m.top;
                nw = mainSizes[i];
                nh = childCross;
                cursor += mainSizes[i] + m.horizontal() + gap;
            } else {
                ny = cursor + m.top;
                nx = pad.left + crossPos + m.left;
                nw = childCross;
                nh = mainSizes[i];
                cursor += mainSizes[i] + m.vertical() + gap;
            }

            UiConstraints childConstraints = UiConstraints.tight(nw, nh);
            layoutNode(child, childConstraints, nx, ny);
        }
    }

    private static UiCrossAlign effectiveCross(UiLayoutNode parent, UiLayoutNode child) {
        UiAlignSelf self = child.alignSelf();
        if (self == null || self == UiAlignSelf.AUTO) {
            return parent.crossAlign() != null ? parent.crossAlign() : UiCrossAlign.STRETCH;
        }
        switch (self) {
            case START:
                return UiCrossAlign.START;
            case CENTER:
                return UiCrossAlign.CENTER;
            case END:
                return UiCrossAlign.END;
            case STRETCH:
                return UiCrossAlign.STRETCH;
            default:
                return parent.crossAlign();
        }
    }

    private static int resolveWidth(UiLayoutNode node, UiConstraints constraints) {
        if (node.preferredWidth() > 0) {
            return constraints.constrainWidth(node.preferredWidth());
        }
        return constraints.constrainWidth(node.measureWidth(constraints));
    }

    private static int resolveHeight(UiLayoutNode node, UiConstraints constraints, int width) {
        if (node.preferredHeight() > 0) {
            return constraints.constrainHeight(node.preferredHeight());
        }
        return constraints.constrainHeight(node.measureHeight(constraints, width));
    }

    private static List<? extends UiLayoutNode> visibleChildren(UiLayoutNode node) {
        List<? extends UiLayoutNode> all = node.layoutChildren();
        if (all == null || all.isEmpty()) {
            return all != null ? all : new ArrayList<UiLayoutNode>();
        }
        ArrayList<UiLayoutNode> out = new ArrayList<UiLayoutNode>(all.size());
        for (int i = 0; i < all.size(); i++) {
            UiLayoutNode c = all.get(i);
            if (c != null && c.isVisible() && !c.isAbsolute()) {
                out.add(c);
            }
        }
        // absolute children still get laid out relative to parent after flex pass
        for (int i = 0; i < all.size(); i++) {
            UiLayoutNode c = all.get(i);
            if (c != null && c.isVisible() && c.isAbsolute()) {
                UiConstraints loose = UiConstraints.loose(Integer.MAX_VALUE / 8, Integer.MAX_VALUE / 8);
                layoutNode(c, loose, c.absoluteX(), c.absoluteY());
            }
        }
        return out;
    }
}
