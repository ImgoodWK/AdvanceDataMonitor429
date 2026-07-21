package com.imgood.textech.gui.framework.layout;

import org.junit.Assert;
import org.junit.Test;

public class UiFlexLayoutEngineTest {

    @Test
    public void rowPlacesChildrenLeftToRightWithGap() {
        FakeLayoutNode a = FakeLayoutNode.leaf(20, 10);
        FakeLayoutNode b = FakeLayoutNode.leaf(30, 10);
        FakeLayoutNode root = FakeLayoutNode.flexRow()
            .padding(0)
            .gap(4)
            .child(a)
            .child(b);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(100, 20));

        Assert.assertEquals(0, a.getLayoutBox().x);
        Assert.assertEquals(20, a.getLayoutBox().width);
        Assert.assertEquals(24, b.getLayoutBox().x);
        Assert.assertEquals(30, b.getLayoutBox().width);
        Assert.assertEquals(100, root.getLayoutBox().width);
    }

    @Test
    public void columnStacksChildrenWithGap() {
        FakeLayoutNode a = FakeLayoutNode.leaf(10, 10);
        FakeLayoutNode b = FakeLayoutNode.leaf(10, 15);
        FakeLayoutNode root = FakeLayoutNode.flexColumn()
            .gap(2)
            .child(a)
            .child(b);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(40, 80));

        Assert.assertEquals(0, a.getLayoutBox().y);
        Assert.assertEquals(12, b.getLayoutBox().y);
        Assert.assertEquals(15, b.getLayoutBox().height);
    }

    @Test
    public void growSharesExtraMainSpace() {
        FakeLayoutNode a = FakeLayoutNode.leaf(10, 10).grow(1f);
        FakeLayoutNode b = FakeLayoutNode.leaf(10, 10).grow(1f);
        FakeLayoutNode root = FakeLayoutNode.flexRow()
            .gap(0)
            .child(a)
            .child(b);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(100, 10));

        Assert.assertEquals(50, a.getLayoutBox().width);
        Assert.assertEquals(50, b.getLayoutBox().width);
        Assert.assertEquals(50, b.getLayoutBox().x);
    }

    @Test
    public void mainAlignEndPacksToEnd() {
        FakeLayoutNode a = FakeLayoutNode.leaf(20, 10);
        FakeLayoutNode root = FakeLayoutNode.flexRow()
            .mainAlign(UiMainAlign.END)
            .child(a);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(100, 10));

        Assert.assertEquals(80, a.getLayoutBox().x);
    }

    @Test
    public void paddingInsetsChildren() {
        FakeLayoutNode a = FakeLayoutNode.leaf(10, 10);
        FakeLayoutNode root = FakeLayoutNode.flexColumn()
            .padding(5)
            .child(a);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(40, 40));

        Assert.assertEquals(5, a.getLayoutBox().x);
        Assert.assertEquals(5, a.getLayoutBox().y);
    }

    @Test
    public void preferredWidthExemptsStretchOnColumnCrossAxis() {
        FakeLayoutNode a = FakeLayoutNode.leaf(10, 10).preferredWidth(30);
        FakeLayoutNode root = FakeLayoutNode.flexColumn()
            .crossAlign(UiCrossAlign.STRETCH)
            .child(a);

        UiFlexLayoutEngine.layout(root, UiConstraints.tight(100, 40));

        Assert.assertEquals(30, a.getLayoutBox().width);
    }
}
