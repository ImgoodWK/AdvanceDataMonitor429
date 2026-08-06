package com.imgood.textech.client.worldmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.webae.network.PacketWorldMapCaptureJob;

public class WorldMapCaptureJobAssemblerTest {

    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test
    public void reassemblesOutOfOrderPagesWithoutMissingChunks() {
        List<String> chunks = chunks(8_000);
        List<PacketWorldMapCaptureJob> pages = PacketWorldMapCaptureJob
            .createPages(OWNER, 7, 11, chunks, 128, "dynmap,journeymap,client_gl");
        Collections.reverse(pages);
        WorldMapCaptureJobAssembler assembler = new WorldMapCaptureJobAssembler(2, 1_000L);

        WorldMapCaptureJobAssembler.AssembledJob complete = null;
        for (PacketWorldMapCaptureJob page : pages) {
            WorldMapCaptureJobAssembler.AssembledJob accepted = assembler.acceptPage(page, 100L);
            if (accepted != null) {
                complete = accepted;
            }
        }
        Assert.assertNotNull(complete);
        Assert.assertEquals(chunks, complete.chunks);
        Assert.assertEquals(0, assembler.pendingCount());
    }

    @Test
    public void missingPageExpiresAndConflictingDuplicateAborts() {
        List<PacketWorldMapCaptureJob> pages = PacketWorldMapCaptureJob
            .createPages(OWNER, 7, 11, chunks(8_000), 128, "dynmap");
        Assert.assertTrue(pages.size() > 1);
        WorldMapCaptureJobAssembler assembler = new WorldMapCaptureJobAssembler(2, 10L);
        Assert.assertNull(assembler.acceptPage(pages.get(0), 100L));
        Assert.assertEquals(1, assembler.pendingCount());
        Assert.assertEquals(1, assembler.pruneExpired(110L));
        Assert.assertEquals(0, assembler.pendingCount());

        PacketWorldMapCaptureJob original = pages.get(0);
        Assert.assertNull(assembler.acceptPage(original, 200L));
        PacketWorldMapCaptureJob conflict = copy(original);
        conflict.chunks.set(0, "0:1,1");
        Assert.assertTrue(conflict.isValid());
        Assert.assertNull(assembler.acceptPage(conflict, 201L));
        Assert.assertEquals(0, assembler.pendingCount());
    }

    private static PacketWorldMapCaptureJob copy(PacketWorldMapCaptureJob source) {
        PacketWorldMapCaptureJob copy = new PacketWorldMapCaptureJob(
            source.ownerUuid,
            source.networkId,
            source.snapshotVersion,
            source.chunks,
            source.tilePx);
        copy.sourcePriority = source.sourcePriority;
        copy.pageIndex = source.pageIndex;
        copy.pageCount = source.pageCount;
        copy.chunkOffset = source.chunkOffset;
        copy.totalChunks = source.totalChunks;
        return copy;
    }

    private static List<String> chunks(int count) {
        List<String> out = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            out.add("0:" + i + "," + -i);
        }
        return out;
    }
}
