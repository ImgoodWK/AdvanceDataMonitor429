package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.webae.icon.IconExportScope;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWebUploadTriggerTest {

    @Test
    public void largeItemListIsFullyBatchedWithoutExceedingWireBudget() {
        List<String> expected = new ArrayList<String>();
        for (int i = 0; i < 4096; i++) {
            expected.add("example:item_" + i + "_" + repeat('x', 80));
        }
        List<PacketWebUploadTrigger> packets = PacketWebUploadTrigger.createItemIdBatches(
            PacketWebUploadTrigger.TYPE_ICONS,
            "default",
            "hybrid",
            IconExportScope.LIST,
            expected);
        Assert.assertTrue(packets.size() > 1);

        List<String> actual = new ArrayList<String>();
        for (int i = 0; i < packets.size(); i++) {
            PacketWebUploadTrigger packet = packets.get(i);
            Assert.assertEquals(i, packet.batchIndex);
            Assert.assertEquals(packets.size(), packet.batchCount);
            ByteBuf buffer = Unpooled.buffer();
            packet.toBytes(buffer);
            Assert.assertTrue(buffer.readableBytes() <= PacketWebUploadTrigger.MAX_PACKET_BODY_BYTES);
            PacketWebUploadTrigger decoded = new PacketWebUploadTrigger();
            decoded.fromBytes(buffer);
            actual.addAll(PacketWebIconExportScope.parseItemIds(decoded.itemIdsJson));
        }
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void rejectsTooManyItemsAndTrailingBytes() {
        List<String> tooMany = new ArrayList<String>();
        for (int i = 0; i <= PacketWebIconExportScope.MAX_ITEM_IDS; i++) tooMany.add("example:item_" + i);
        try {
            PacketWebUploadTrigger.createItemIdBatches(
                PacketWebUploadTrigger.TYPE_ICONS,
                "default",
                "hybrid",
                IconExportScope.LIST,
                tooMany);
            Assert.fail("oversized list must be rejected");
        } catch (IllegalArgumentException expected) {}

        PacketWebUploadTrigger source = new PacketWebUploadTrigger(
            PacketWebUploadTrigger.TYPE_ICONS,
            "default",
            "hybrid",
            IconExportScope.LIST,
            java.util.Collections.singletonList("example:item"));
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        buffer.writeByte(1);
        PacketWebUploadTrigger decoded = new PacketWebUploadTrigger();
        decoded.fromBytes(buffer);
        Assert.assertEquals("", decoded.uploadType);
    }

    @Test
    public void exportScopeJsonRejectsInvalidRootsAndElements() {
        Assert.assertNull(PacketWebIconExportScope.parseItemIds("{}"));
        Assert.assertNull(PacketWebIconExportScope.parseItemIds("[1]"));
        Assert.assertNull(PacketWebIconExportScope.parseItemIds("[\"bad\\u0001id\"]"));
        Assert.assertNull(PacketWebIconExportScope.parseItemIds("[\"" + repeat('x', 257) + "\"]"));
        Assert.assertEquals(
            java.util.Collections.singletonList("example:item"),
            PacketWebIconExportScope.parseItemIds("[\"example:item\"]"));
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
