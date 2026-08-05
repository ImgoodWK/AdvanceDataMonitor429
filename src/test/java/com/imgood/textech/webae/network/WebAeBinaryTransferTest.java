package com.imgood.textech.webae.network;

import org.junit.Assert;
import org.junit.Test;

public class WebAeBinaryTransferTest {

    @Test
    public void splitsAndReassemblesWithinTheDeclaredLimit() {
        byte[] payload = new byte[WebAeBinaryTransfer.MAX_PACKET_CHUNK_BYTES + 17];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);

        int chunks = WebAeBinaryTransfer.chunkCount(payload.length, payload.length);
        Assert.assertEquals(2, chunks);
        WebAeBinaryTransfer.SequentialAssembler assembler = new WebAeBinaryTransfer.SequentialAssembler(
            payload.length,
            chunks);
        Assert.assertNull(assembler.accept(0, chunks, WebAeBinaryTransfer.copyChunk(payload, 0)));
        Assert.assertArrayEquals(payload, assembler.accept(1, chunks, WebAeBinaryTransfer.copyChunk(payload, 1)));
    }

    @Test
    public void rejectsEmptyAndOutOfOrderChunks() {
        WebAeBinaryTransfer.SequentialAssembler emptyAssembler = new WebAeBinaryTransfer.SequentialAssembler(32, 2);
        try {
            emptyAssembler.accept(0, 1, new byte[0]);
            Assert.fail("empty chunk must be rejected");
        } catch (IllegalArgumentException expected) {}

        WebAeBinaryTransfer.SequentialAssembler ordered = new WebAeBinaryTransfer.SequentialAssembler(32, 3);
        ordered.accept(0, 3, new byte[] { 1 });
        try {
            ordered.accept(2, 3, new byte[] { 2 });
            Assert.fail("out-of-order chunk must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void rejectsRestartAndAggregateOverflow() {
        WebAeBinaryTransfer.SequentialAssembler restarted = new WebAeBinaryTransfer.SequentialAssembler(32, 2);
        restarted.accept(0, 2, new byte[] { 1 });
        try {
            restarted.accept(0, 2, new byte[] { 2 });
            Assert.fail("restarted transfer must be rejected");
        } catch (IllegalArgumentException expected) {}

        WebAeBinaryTransfer.SequentialAssembler oversized = new WebAeBinaryTransfer.SequentialAssembler(10, 2);
        oversized.accept(0, 2, new byte[] { 1, 2, 3, 4, 5, 6 });
        try {
            oversized.accept(1, 2, new byte[] { 7, 8, 9, 10, 11, 12 });
            Assert.fail("aggregate overflow must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void rejectsInvalidChunkCountsAndIndexes() {
        Assert.assertEquals(0, WebAeBinaryTransfer.chunkCount(0, 100));
        Assert.assertEquals(0, WebAeBinaryTransfer.chunkCount(101, 100));
        Assert.assertNull(WebAeBinaryTransfer.copyChunk(new byte[] { 1 }, 1));
        Assert.assertNull(WebAeBinaryTransfer.copyChunk(null, 0));
    }
}
