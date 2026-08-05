package com.imgood.textech.webae.network;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWorldMapCaptureJobTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test
    public void largeJobsPageByActualWireBudgetWithoutLosingChunks() {
        List<String> chunks = chunks(8_000);
        List<PacketWorldMapCaptureJob> pages = PacketWorldMapCaptureJob
            .createPages(OWNER, 7, 11, chunks, 128, "dynmap,journeymap,client_gl");

        Assert.assertTrue(pages.size() > 1);
        List<String> decodedChunks = new ArrayList<String>();
        int expectedOffset = 0;
        for (int i = 0; i < pages.size(); i++) {
            PacketWorldMapCaptureJob page = pages.get(i);
            Assert.assertEquals(i, page.pageIndex);
            Assert.assertEquals(pages.size(), page.pageCount);
            Assert.assertEquals(expectedOffset, page.chunkOffset);
            Assert.assertEquals(chunks.size(), page.totalChunks);

            ByteBuf buffer = Unpooled.buffer();
            page.toBytes(buffer);
            Assert.assertEquals(page.encodedBodySize(), buffer.readableBytes());
            Assert.assertTrue(buffer.readableBytes() <= PacketWorldMapCaptureJob.MAX_PACKET_BYTES);

            PacketWorldMapCaptureJob decoded = new PacketWorldMapCaptureJob();
            decoded.fromBytes(buffer);
            Assert.assertTrue(decoded.isValid());
            decodedChunks.addAll(decoded.chunks);
            expectedOffset += decoded.chunks.size();
        }
        Assert.assertEquals(chunks.size(), expectedOffset);
        Assert.assertEquals(chunks, decodedChunks);
    }

    @Test
    public void decodeRejectsTrailingBytes() {
        PacketWorldMapCaptureJob page = PacketWorldMapCaptureJob
            .createPages(OWNER, 7, 11, chunks(2), 128, "dynmap")
            .get(0);
        ByteBuf buffer = Unpooled.buffer();
        page.toBytes(buffer);
        buffer.writeByte(1);

        PacketWorldMapCaptureJob decoded = new PacketWorldMapCaptureJob();
        decoded.fromBytes(buffer);
        Assert.assertFalse(decoded.isValid());
        Assert.assertTrue(decoded.chunks.isEmpty());
    }

    @Test
    public void factoryRejectsAnyInvalidChunkInsteadOfTruncating() {
        List<String> chunks = chunks(4_000);
        chunks.add("invalid");
        try {
            PacketWorldMapCaptureJob.createPages(OWNER, 7, 11, chunks, 128, "dynmap");
            Assert.fail("Expected invalid chunk list to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("chunk"));
        }
    }

    private static List<String> chunks(int count) {
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add((i % 3 - 1) + ":" + i + "," + -i);
        }
        return out;
    }
}
