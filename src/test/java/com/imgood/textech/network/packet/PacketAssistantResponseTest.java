package com.imgood.textech.network.packet;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.assistant.AssistantSessionKind;
import com.imgood.textech.assistant.CandidateBatchMeta;
import com.imgood.textech.assistant.CraftingCandidate;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketAssistantResponseTest {

    @Test
    public void normalResponseRoundTripsWithinTheFmlBudget() {
        PacketAssistantResponse source = PacketAssistantResponse.message("ok");
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= PacketAssistantResponse.MAX_PACKET_BODY_BYTES);
        PacketAssistantResponse decoded = new PacketAssistantResponse();
        decoded.fromBytes(buffer);
        Assert.assertFalse(decoded.malformed);
    }

    @Test
    public void trailingAndOversizedCompressedPayloadsAreRejected() {
        PacketAssistantResponse source = PacketAssistantResponse.message("ok");
        ByteBuf trailing = Unpooled.buffer();
        source.toBytes(trailing);
        trailing.writeByte(1);
        PacketAssistantResponse trailingDecoded = new PacketAssistantResponse();
        trailingDecoded.fromBytes(trailing);
        Assert.assertTrue(trailingDecoded.malformed);

        ByteBuf oversized = assistantHeader();
        oversized.writeByte(1);
        oversized.writeInt(PacketAssistantResponse.MAX_COMPRESSED_PAYLOAD_BYTES + 1);
        PacketAssistantResponse oversizedDecoded = new PacketAssistantResponse();
        oversizedDecoded.fromBytes(oversized);
        Assert.assertTrue(oversizedDecoded.malformed);
    }

    @Test
    public void gzipExpansionPastTheNbtLimitIsRejected() throws Exception {
        byte[] expanded = new byte[2 * 1024 * 1024 + 1];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bytes);
        gzip.write(expanded);
        gzip.close();
        Assert.assertTrue(bytes.size() < PacketAssistantResponse.MAX_COMPRESSED_PAYLOAD_BYTES);

        ByteBuf bomb = assistantHeader();
        bomb.writeByte(1);
        bomb.writeInt(bytes.size());
        bomb.writeBytes(bytes.toByteArray());
        PacketAssistantResponse decoded = new PacketAssistantResponse();
        decoded.fromBytes(bomb);
        Assert.assertTrue(decoded.malformed);
    }

    @Test
    public void incompressibleCandidateCannotPassPacketPreflight() {
        CraftingCandidate candidate = new CraftingCandidate(0, null, 1L);
        byte[] randomBytes = new byte[64 * 1024];
        new Random(12345L).nextBytes(randomBytes);
        candidate.itemNbt.setByteArray("random", randomBytes);
        PacketAssistantResponse response = PacketAssistantResponse.candidates(
            "query",
            Collections.singletonList(candidate),
            AssistantSessionKind.ORDER_CANDIDATES,
            0,
            1,
            1,
            false,
            1,
            1);
        Assert.assertFalse(response.fitsPacketBudget(PacketAssistantResponse.MAX_PACKET_BODY_BYTES));
    }

    @Test
    public void explicitCandidateRangesOverrideConfiguredBatchMath() {
        CandidateBatchMeta meta = new CandidateBatchMeta(3, 8, 100, true, 37, 41);
        Assert.assertEquals(37, meta.rangeStart());
        Assert.assertEquals(41, meta.rangeEnd());
    }

    @Test
    public void assistantActionRejectsTrailingAndOversizedNbt() {
        PacketAssistantAction source = PacketAssistantAction.requestCraftCandidates("query", "item", 1L);
        ByteBuf buffer = Unpooled.buffer();
        source.toBytes(buffer);
        Assert.assertTrue(buffer.readableBytes() <= PacketAssistantAction.MAX_PACKET_BODY_BYTES);
        buffer.writeByte(1);
        PacketAssistantAction decoded = new PacketAssistantAction();
        decoded.fromBytes(buffer);
        Assert.assertTrue(decoded.malformed);

        CraftingCandidate candidate = new CraftingCandidate(0, null, 1L);
        byte[] randomBytes = new byte[64 * 1024];
        new Random(54321L).nextBytes(randomBytes);
        candidate.itemNbt.setByteArray("random", randomBytes);
        PacketAssistantAction oversized = PacketAssistantAction.submitCraft(candidate, 1L, "query");
        try {
            oversized.toBytes(Unpooled.buffer());
            Assert.fail("oversized assistant action NBT must be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    private static ByteBuf assistantHeader() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeByte(0);
        buffer.writeByte(0);
        return buffer;
    }
}
