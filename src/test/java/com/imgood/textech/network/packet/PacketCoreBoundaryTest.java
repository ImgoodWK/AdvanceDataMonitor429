package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.client.GrappleClientRouteCache;
import com.imgood.textech.client.PocketClientCache;
import com.imgood.textech.handler.PocketState;
import com.imgood.textech.items.GrappleHookMode;
import com.imgood.textech.items.PlannerMergeMode;
import com.imgood.textech.items.GrappleRouteEntry;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.utils.NetworkPacketCodec;
import com.imgood.textech.utils.WebDashboardSnapshotCodec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCoreBoundaryTest {

    private static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    public void varUtf8RejectsFifthByteIntegerOverflow() {
        ByteBuf overflow = Unpooled.wrappedBuffer(
            new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10 });
        try {
            NetworkPacketCodec.readVarUtf8(overflow, 32);
            Assert.fail("overflowing VarInt length must be rejected before allocation");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void itemNbtRejectsTruncationTrailingAndMalformedUtf8() {
        PacketItemNBT source = new PacketItemNBT(2, new BlockPos(1, 2, 3), repeat("text", 4000));
        ByteBuf encoded = Unpooled.buffer();
        source.toBytes(encoded);
        Assert.assertTrue(source.fitsPacketBudget());

        PacketItemNBT roundTrip = new PacketItemNBT();
        roundTrip.fromBytes(copy(encoded, encoded.readableBytes()));
        Assert.assertFalse(roundTrip.malformed);
        Assert.assertEquals(source.textData, roundTrip.textData);

        PacketItemNBT truncated = new PacketItemNBT();
        truncated.fromBytes(copy(encoded, encoded.readableBytes() - 1));
        Assert.assertTrue(truncated.malformed);
        Assert.assertNull(truncated.position);
        Assert.assertNull(truncated.textData);

        ByteBuf trailingBytes = copy(encoded, encoded.readableBytes());
        trailingBytes.writeByte(1);
        PacketItemNBT trailing = new PacketItemNBT();
        trailing.fromBytes(trailingBytes);
        Assert.assertTrue(trailing.malformed);

        ByteBuf invalidUtf8 = Unpooled.buffer();
        invalidUtf8.writeInt(2);
        invalidUtf8.writeInt(1);
        invalidUtf8.writeInt(2);
        invalidUtf8.writeInt(3);
        invalidUtf8.writeByte(2);
        invalidUtf8.writeBytes(new byte[] { (byte) 0xC3, 0x28 });
        PacketItemNBT malformedUtf8 = new PacketItemNBT();
        malformedUtf8.fromBytes(invalidUtf8);
        Assert.assertTrue(malformedUtf8.malformed);

        try {
            new PacketItemNBT(2, new BlockPos(1, 2, 3), repeat("x", 17000)).toBytes(Unpooled.buffer());
            Assert.fail("over-limit item text must be rejected before send");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void tileEntityNbtUsesBoundedFramingAndRejectsTail() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("mode", "dashboard_snapshot");
        PacketSynTileEntity source = new PacketSynTileEntity(4, 5, 6, data);
        ByteBuf encoded = Unpooled.buffer();
        source.toBytes(encoded);
        Assert.assertTrue(source.fitsPacketBudget());

        PacketSynTileEntity decoded = new PacketSynTileEntity();
        decoded.fromBytes(copy(encoded, encoded.readableBytes()));
        Assert.assertFalse(decoded.malformed);
        Assert.assertEquals("dashboard_snapshot", decoded.getData().getString("mode"));

        PacketSynTileEntity truncated = new PacketSynTileEntity();
        truncated.fromBytes(copy(encoded, encoded.readableBytes() - 1));
        Assert.assertTrue(truncated.malformed);
        Assert.assertNull(truncated.getData());

        ByteBuf trailingBytes = copy(encoded, encoded.readableBytes());
        trailingBytes.writeByte(7);
        PacketSynTileEntity trailing = new PacketSynTileEntity();
        trailing.fromBytes(trailingBytes);
        Assert.assertTrue(trailing.malformed);

        NBTTagCompound legalLarge = new NBTTagCompound();
        legalLarge.setByteArray("random", randomBytes(20000, 11L));
        PacketSynTileEntity legal = new PacketSynTileEntity(1, 2, 3, legalLarge);
        Assert.assertTrue(legal.fitsPacketBudget());
        ByteBuf legalBytes = Unpooled.buffer();
        legal.toBytes(legalBytes);
        Assert.assertTrue(legalBytes.readableBytes() <= PacketSynTileEntity.MAX_PACKET_BODY_BYTES);

        NBTTagCompound tooLarge = new NBTTagCompound();
        tooLarge.setByteArray("random", randomBytes(40000, 12L));
        PacketSynTileEntity oversized = new PacketSynTileEntity(1, 2, 3, tooLarge);
        Assert.assertFalse(oversized.fitsPacketBudget());
        try {
            oversized.toBytes(Unpooled.buffer());
            Assert.fail("over-budget tile NBT must be rejected before send");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void monitorWebSurfaceEnforcesWholeBodyBudget() {
        PacketMonitorWebSurface source = PacketMonitorWebSurface.upload(
            1,
            2,
            3,
            0,
            HASH,
            new byte[WebDashboardSnapshotCodec.MAX_COMPRESSED_BYTES],
            null);
        Assert.assertTrue(source.fitsPacketBudget());
        ByteBuf encoded = Unpooled.buffer();
        source.toBytes(encoded);
        Assert.assertTrue(encoded.readableBytes() <= PacketMonitorWebSurface.MAX_PACKET_BODY_BYTES);

        PacketMonitorWebSurface decoded = new PacketMonitorWebSurface();
        decoded.fromBytes(copy(encoded, encoded.readableBytes()));
        Assert.assertTrue(decoded.isValidPacket());

        ByteBuf trailingBytes = copy(encoded, encoded.readableBytes());
        trailingBytes.writeByte(1);
        PacketMonitorWebSurface trailing = new PacketMonitorWebSurface();
        trailing.fromBytes(trailingBytes);
        Assert.assertFalse(trailing.isValidPacket());

        ByteBuf oversizedBody = Unpooled.buffer(PacketMonitorWebSurface.MAX_PACKET_BODY_BYTES + 1);
        oversizedBody.writeZero(PacketMonitorWebSurface.MAX_PACKET_BODY_BYTES + 1);
        PacketMonitorWebSurface oversizedDecoded = new PacketMonitorWebSurface();
        oversizedDecoded.fromBytes(oversizedBody);
        Assert.assertFalse(oversizedDecoded.isValidPacket());

        NBTTagCompound config = new NBTTagCompound();
        config.setByteArray("random", randomBytes(40000, 13L));
        PacketMonitorWebSurface oversizedConfig = PacketMonitorWebSurface.upload(
            1,
            2,
            3,
            0,
            HASH,
            null,
            config);
        Assert.assertFalse(oversizedConfig.fitsPacketBudget());
        try {
            oversizedConfig.toBytes(Unpooled.buffer());
            Assert.fail("over-budget monitor config must be rejected before send");
        } catch (IllegalArgumentException expected) {}
    }

    @Test
    public void pocketFullStateSplitsOnlyAfterCompletePreflight() {
        PocketState state = new PocketState();
        state.setSpaceUpgrades(PocketState.MAX_SPACE_UPGRADES);
        state.setPageUpgrades(PocketState.MAX_PAGE_UPGRADES);
        for (int page = 0; page < state.getPageCount(); page++) {
            state.setStack(page, 0, largeStack(6000, 20L + page));
        }

        List<PacketPocketSync> packets = PacketPocketSync.fullStatePackets(state);
        Assert.assertTrue("large state must be paged", packets.size() > 1);
        Assert.assertEquals(PacketPocketSync.KIND_METADATA, packets.get(0).kind);
        Assert.assertEquals(PacketPocketSync.FULL_SNAPSHOT_PAGE_INDEX, packets.get(0).pageIndex);
        PocketClientCache.apply(PacketPocketSync.metadataState(state));
        Assert.assertTrue("test state must have multiple pages", state.getPageCount() > 1);
        int expectedCurrentPage = Math.min(1, state.getPageCount() - 1);
        PocketClientCache.setCurrentPage(expectedCurrentPage);
        for (PacketPocketSync packet : packets) {
            Assert.assertTrue(packet.fitsPacketBudget());
            ByteBuf bytes = Unpooled.buffer();
            packet.toBytes(bytes);
            Assert.assertTrue(bytes.readableBytes() <= PacketPocketSync.MAX_PACKET_BODY_BYTES);
            PacketPocketSync decoded = new PacketPocketSync();
            decoded.fromBytes(bytes);
            Assert.assertFalse(decoded.malformed);
            PocketClientCache.apply(decoded);
        }
        Assert.assertEquals(state.getPageCount(), PocketClientCache.getPageCount());
        Assert.assertEquals(state.getSlotsPerPage(), PocketClientCache.getSlotsPerPage());
        Assert.assertEquals("split full fragments must not navigate", expectedCurrentPage,
            PocketClientCache.getCurrentPage());

        int ordinaryTargetPage = expectedCurrentPage == 0 ? 1 : 0;
        PocketClientCache.apply(PacketPocketSync.singlePage(state, ordinaryTargetPage));
        Assert.assertEquals("ordinary single-page sync must still navigate", ordinaryTargetPage,
            PocketClientCache.getCurrentPage());

        PacketPocketSync truncated = new PacketPocketSync();
        ByteBuf metadata = Unpooled.buffer();
        packets.get(0).toBytes(metadata);
        truncated.fromBytes(copy(metadata, metadata.readableBytes() - 1));
        Assert.assertTrue(truncated.malformed);

        ByteBuf trailing = Unpooled.buffer();
        packets.get(0).toBytes(trailing);
        trailing.writeByte(1);
        PacketPocketSync trailingDecoded = new PacketPocketSync();
        trailingDecoded.fromBytes(trailing);
        Assert.assertTrue(trailingDecoded.malformed);

        PocketState impossible = new PocketState();
        impossible.setStack(0, 0, largeStack(40000, 99L));
        Assert.assertTrue(PacketPocketSync.fullStatePackets(impossible).isEmpty());
    }

    @Test
    public void pocketSplitOutOfOrderResetsBeforeNormalSinglePageNavigation() {
        PocketState state = new PocketState();
        state.setSpaceUpgrades(PocketState.MAX_SPACE_UPGRADES);
        state.setPageUpgrades(PocketState.MAX_PAGE_UPGRADES);
        Assert.assertTrue(state.getPageCount() > 1);

        PacketPocketSync marker = PacketPocketSync.metadataState(state);
        marker.pageIndex = PacketPocketSync.FULL_SNAPSHOT_PAGE_INDEX;
        PocketClientCache.apply(marker);
        PocketClientCache.setCurrentPage(0);

        // Page 1 arrives while page 0 is required: abort split mode without navigating.
        Assert.assertFalse(PocketClientCache.apply(PacketPocketSync.singlePage(state, 1)));
        Assert.assertEquals(0, PocketClientCache.getCurrentPage());

        // The next ordinary single-page packet must regain its normal navigation meaning.
        Assert.assertTrue(PocketClientCache.apply(PacketPocketSync.singlePage(state, 1)));
        Assert.assertEquals(1, PocketClientCache.getCurrentPage());
    }

    @Test
    public void grappleRetainsLegacySmallWireAndReassemblesLargeBatches() {
        GrappleRouteEntry smallRoute = route("small", 2, 1L);
        List<PacketGrapplePathSync> legacy = PacketGrapplePathSync.routePackets(
            Collections.singletonList(smallRoute));
        Assert.assertEquals(1, legacy.size());
        Assert.assertEquals(PacketGrapplePathSync.KIND_ROUTES, legacy.get(0).kind);

        PacketGrapplePathSync legacyDecoded = roundTrip(legacy.get(0));
        Assert.assertFalse(legacyDecoded.malformed);
        Assert.assertEquals(1, legacyDecoded.routes.size());

        List<GrappleRouteEntry> smallRoutes129 = new ArrayList<GrappleRouteEntry>();
        for (int i = 0; i < 129; i++) {
            smallRoutes129.add(route("tiny-" + i, 0, 10L + i));
        }
        List<PacketGrapplePathSync> legacy129 = PacketGrapplePathSync.routePackets(smallRoutes129);
        Assert.assertEquals(1, legacy129.size());
        Assert.assertEquals(PacketGrapplePathSync.KIND_ROUTES, legacy129.get(0).kind);

        PacketGrappleAction maxTravel = PacketGrappleAction.travelPath("max-travel", route("travel", 512, 9L).nodes);
        Assert.assertTrue("configured 512-node routes must remain sendable", maxTravel.fitsPacketBudget());
        PacketGrappleAction maxTravelDecoded = new PacketGrappleAction();
        maxTravelDecoded.fromBytes(encode(maxTravel));
        Assert.assertFalse(maxTravelDecoded.malformed);
        PacketGrappleAction oversizedTravel = PacketGrappleAction.travelPath(
            "oversized-travel",
            route("travel-too-large", 513, 10L).nodes);
        Assert.assertFalse("routes above the configured maximum must be rejected", oversizedTravel.fitsPacketBudget());

        List<GrappleRouteEntry> manyRoutes = new ArrayList<GrappleRouteEntry>();
        for (int i = 0; i < 24; i++) {
            manyRoutes.add(route("route-" + i, 512, 100L + i));
        }
        List<PacketGrapplePathSync> batches = PacketGrapplePathSync.routePackets(manyRoutes);
        Assert.assertTrue("large route state must be batched", batches.size() > 1);
        GrappleRouteEntry oldRoute = route("old-live", 1, 99L);
        GrappleClientRouteCache.apply(PacketGrapplePathSync.routes(Collections.singletonList(oldRoute)));
        for (int i = 0; i < batches.size(); i++) {
            PacketGrapplePathSync packet = batches.get(i);
            Assert.assertEquals(PacketGrapplePathSync.KIND_ROUTES_BATCH, packet.kind);
            Assert.assertEquals(i, packet.batchIndex);
            Assert.assertEquals(batches.size(), packet.batchCount);
            Assert.assertTrue(packet.fitsPacketBudget());
            PacketGrapplePathSync decoded = roundTrip(packet);
            Assert.assertFalse(decoded.malformed);
            GrappleClientRouteCache.apply(decoded);
            if (i == 0) {
                Assert.assertEquals("incomplete batch must keep live routes", 1,
                    GrappleClientRouteCache.getRoutes().size());
                Assert.assertEquals("old-live", GrappleClientRouteCache.getRoutes().get(0).routeId);
            }
        }
        Assert.assertEquals(manyRoutes.size(), GrappleClientRouteCache.getRoutes().size());

        List<PacketGrapplePathSync> tooMany = new ArrayList<PacketGrapplePathSync>();
        for (int i = 0; i < PacketGrapplePathSync.MAX_TOTAL_ROUTES + 1; i++) {
            tooMany.add(PacketGrapplePathSync.routes(Collections.singletonList(route("r" + i, 0, i))));
        }
        Assert.assertTrue(PacketGrapplePathSync.routePackets(flattenRoutes(tooMany)).isEmpty());
    }

    @Test
    public void grappleRejectsTruncationTrailingAndInvalidBatchMetadata() {
        PacketGrapplePathSync source = PacketGrapplePathSync.routes(Collections.singletonList(route("r", 2, 1L)));
        ByteBuf encoded = Unpooled.buffer();
        source.toBytes(encoded);

        PacketGrapplePathSync truncated = new PacketGrapplePathSync();
        truncated.fromBytes(copy(encoded, encoded.readableBytes() - 1));
        Assert.assertTrue(truncated.malformed);

        ByteBuf trailingBytes = copy(encoded, encoded.readableBytes());
        trailingBytes.writeByte(1);
        PacketGrapplePathSync trailing = new PacketGrapplePathSync();
        trailing.fromBytes(trailingBytes);
        Assert.assertTrue(trailing.malformed);

        ByteBuf invalidBatch = Unpooled.buffer();
        invalidBatch.writeByte(PacketGrapplePathSync.KIND_ROUTES_BATCH);
        invalidBatch.writeShort(0);
        invalidBatch.writeShort(0);
        PacketGrapplePathSync invalid = new PacketGrapplePathSync();
        invalid.fromBytes(invalidBatch);
        Assert.assertTrue(invalid.malformed);

        ByteBuf oversized = Unpooled.buffer(PacketGrapplePathSync.MAX_PACKET_BODY_BYTES + 1);
        oversized.writeZero(PacketGrapplePathSync.MAX_PACKET_BODY_BYTES + 1);
        PacketGrapplePathSync oversizedDecoded = new PacketGrapplePathSync();
        oversizedDecoded.fromBytes(oversized);
        Assert.assertTrue(oversizedDecoded.malformed);
    }

    @Test
    public void grappleBatchFailureDoesNotPublishStaging() {
        GrappleRouteEntry oldRoute = route("stable", 1, 1L);
        GrappleClientRouteCache.apply(PacketGrapplePathSync.routes(Collections.singletonList(oldRoute)));

        List<GrappleRouteEntry> replacement = new ArrayList<GrappleRouteEntry>();
        replacement.add(route("replacement-a", 0, 2L));
        replacement.add(route("replacement-b", 0, 3L));
        List<PacketGrapplePathSync> batches = PacketGrapplePathSync.routePackets(replacement);
        Assert.assertEquals(1, batches.size());
        PacketGrapplePathSync batch = batches.get(0);
        batch.kind = PacketGrapplePathSync.KIND_ROUTES_BATCH;
        batch.batchIndex = 0;
        batch.batchCount = 2;
        GrappleClientRouteCache.apply(batch);
        Assert.assertEquals(1, GrappleClientRouteCache.getRoutes().size());
        Assert.assertEquals("stable", GrappleClientRouteCache.getRoutes().get(0).routeId);

        PacketGrapplePathSync conflict = PacketGrapplePathSync.routes(
            Collections.singletonList(route("replacement-conflict", 0, 4L)));
        conflict.kind = PacketGrapplePathSync.KIND_ROUTES_BATCH;
        conflict.batchIndex = 1;
        conflict.batchCount = 3;
        GrappleClientRouteCache.apply(conflict);
        Assert.assertEquals(1, GrappleClientRouteCache.getRoutes().size());
        Assert.assertEquals("stable", GrappleClientRouteCache.getRoutes().get(0).routeId);

        // A valid complete sequence commits only when its final packet arrives.
        List<PacketGrapplePathSync> valid = new ArrayList<PacketGrapplePathSync>();
        PacketGrapplePathSync first = PacketGrapplePathSync.routes(
            Collections.singletonList(route("replacement-a", 0, 2L)));
        first.kind = PacketGrapplePathSync.KIND_ROUTES_BATCH;
        first.batchIndex = 0;
        first.batchCount = 2;
        PacketGrapplePathSync last = PacketGrapplePathSync.routes(
            Collections.singletonList(route("replacement-b", 0, 3L)));
        last.kind = PacketGrapplePathSync.KIND_ROUTES_BATCH;
        last.batchIndex = 1;
        last.batchCount = 2;
        valid.add(first);
        valid.add(last);
        GrappleClientRouteCache.apply(valid.get(0));
        Assert.assertEquals("stable", GrappleClientRouteCache.getRoutes().get(0).routeId);
        GrappleClientRouteCache.apply(valid.get(1));
        Assert.assertEquals(2, GrappleClientRouteCache.getRoutes().size());
        Assert.assertEquals("replacement-a", GrappleClientRouteCache.getRoutes().get(0).routeId);
    }

    @Test
    public void nonWebAeC2sPacketsRejectTruncationTrailingAndInvalidBranches() {
        PacketAssistantAction assistant = PacketAssistantAction.cancelServerJobs("cancel");
        assertStrictPacket(encode(assistant), bytes -> {
            PacketAssistantAction packet = new PacketAssistantAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf invalidAssistant = encode(assistant);
        invalidAssistant.setByte(0, 0);
        PacketAssistantAction invalidAssistantPacket = new PacketAssistantAction();
        invalidAssistantPacket.fromBytes(invalidAssistant);
        Assert.assertTrue(invalidAssistantPacket.malformed);

        PacketRequestItemCountSync request = new PacketRequestItemCountSync(1, 2, 3);
        assertStrictPacket(encode(request), bytes -> {
            PacketRequestItemCountSync packet = new PacketRequestItemCountSync();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        NBTTagCompound plannerTag = new NBTTagCompound();
        plannerTag.setString("mode", "safe");
        PacketPlannerSync planner = new PacketPlannerSync(2, plannerTag);
        assertStrictPacket(encode(planner), bytes -> {
            PacketPlannerSync packet = new PacketPlannerSync();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        PacketPlannerMerge merge = new PacketPlannerMerge(PlannerMergeMode.BY_TIME);
        assertStrictPacket(encode(merge), bytes -> {
            PacketPlannerMerge packet = new PacketPlannerMerge();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        PacketGrappleAction grapple = PacketGrappleAction.detach();
        assertStrictPacket(encode(grapple), bytes -> {
            PacketGrappleAction packet = new PacketGrappleAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf invalidGrapple = encode(grapple);
        invalidGrapple.setByte(0, 4);
        PacketGrappleAction invalidGrapplePacket = new PacketGrappleAction();
        invalidGrapplePacket.fromBytes(invalidGrapple);
        Assert.assertTrue(invalidGrapplePacket.malformed);

        PacketGrappleAnchorConfig anchor = new PacketGrappleAnchorConfig(1, 2, 3, "anchor", 0x123456);
        assertStrictPacket(encode(anchor), bytes -> {
            PacketGrappleAnchorConfig packet = new PacketGrappleAnchorConfig();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        assertStrictPacket(encode(PacketLinkScannerAction.scan(1)), bytes -> {
            PacketLinkScannerAction packet = new PacketLinkScannerAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        assertStrictPacket(encode(PacketLinkScannerAction.teleport(1, 2, 3, 4, 5)), bytes -> {
            PacketLinkScannerAction packet = new PacketLinkScannerAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf linkInvalid = encode(PacketLinkScannerAction.scan(1));
        linkInvalid.setByte(0, 3);
        PacketLinkScannerAction invalidLink = new PacketLinkScannerAction();
        invalidLink.fromBytes(linkInvalid);
        Assert.assertTrue(invalidLink.malformed);

        PacketGrapplePathAction path = PacketGrapplePathAction.requestSync();
        assertStrictPacket(encode(path), bytes -> {
            PacketGrapplePathAction packet = new PacketGrapplePathAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf invalidPath = encode(path);
        invalidPath.setByte(0, 7);
        PacketGrapplePathAction invalidPathPacket = new PacketGrapplePathAction();
        invalidPathPacket.fromBytes(invalidPath);
        Assert.assertTrue(invalidPathPacket.malformed);

        PacketPocketAction pocket = PacketPocketAction.requestSync();
        assertStrictPacket(encode(pocket), bytes -> {
            PacketPocketAction packet = new PacketPocketAction();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf invalidPocket = encode(pocket);
        invalidPocket.setByte(0, 20);
        PacketPocketAction invalidPocketPacket = new PacketPocketAction();
        invalidPocketPacket.fromBytes(invalidPocket);
        Assert.assertTrue(invalidPocketPacket.malformed);
        ByteBuf nonFiniteWindow = encode(PacketPocketAction.setWindowPos(0.0F, 0.0F));
        nonFiniteWindow.setInt(5, Float.floatToIntBits(Float.NaN));
        PacketPocketAction nonFiniteWindowPacket = new PacketPocketAction();
        nonFiniteWindowPacket.fromBytes(nonFiniteWindow);
        Assert.assertTrue(nonFiniteWindowPacket.malformed);

        PacketSuperOrangeConfig orange = new PacketSuperOrangeConfig("orange", true, false, true, 2);
        assertStrictPacket(encode(orange), bytes -> {
            PacketSuperOrangeConfig packet = new PacketSuperOrangeConfig();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        PacketGrappleHookConfig hook = new PacketGrappleHookConfig(1.0D, true, false, GrappleHookMode.PATH.getId());
        assertStrictPacket(encode(hook), bytes -> {
            PacketGrappleHookConfig packet = new PacketGrappleHookConfig();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });
        ByteBuf oldHook = Unpooled.buffer(10);
        oldHook.writeDouble(1.0D);
        oldHook.writeBoolean(true);
        oldHook.writeBoolean(false);
        PacketGrappleHookConfig oldHookPacket = new PacketGrappleHookConfig();
        oldHookPacket.fromBytes(oldHook);
        Assert.assertFalse(oldHookPacket.malformed);
        Assert.assertTrue("NaN speed must be rejected", malformedHookWithSpeed(Double.NaN));

        PacketMonitorRecord monitor = new PacketMonitorRecord(1, 2, 3);
        assertStrictPacket(encode(monitor), bytes -> {
            PacketMonitorRecord packet = new PacketMonitorRecord();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        PacketMatterBallDecompressorToggle matter = new PacketMatterBallDecompressorToggle(
            1,
            2,
            3,
            PacketMatterBallDecompressorToggle.KIND_BLOCK_MODE,
            true);
        assertStrictPacket(encode(matter), bytes -> {
            PacketMatterBallDecompressorToggle packet = new PacketMatterBallDecompressorToggle();
            packet.fromBytes(bytes);
            return !packet.malformed;
        });

        PacketAssistantMenuStateQuery query = new PacketAssistantMenuStateQuery();
        query.fromBytes(Unpooled.buffer());
        Assert.assertFalse(query.malformed);
        ByteBuf queryTail = Unpooled.buffer();
        queryTail.writeByte(1);
        query.fromBytes(queryTail);
        Assert.assertTrue(query.malformed);
    }

    @Test
    public void variableNbtPacketsRejectOverBudgetBeforeSend() {
        NBTTagCompound huge = new NBTTagCompound();
        huge.setByteArray("random", randomBytes(40000, 77L));

        assertWriterRollback(PacketAssistantAction.cancelServerJobs(repeat("x", 1100)));

        PacketPlannerSync planner = new PacketPlannerSync(1, huge);
        Assert.assertFalse(planner.fitsPacketBudget());
        assertWriterRollback(planner);

        PacketLinkScannerAction scanner = PacketLinkScannerAction.sync(1, huge);
        Assert.assertFalse(scanner.fitsPacketBudget());
        assertWriterRollback(scanner);
    }

    private static PacketGrapplePathSync roundTrip(PacketGrapplePathSync source) {
        ByteBuf bytes = Unpooled.buffer();
        source.toBytes(bytes);
        PacketGrapplePathSync decoded = new PacketGrapplePathSync();
        decoded.fromBytes(bytes);
        return decoded;
    }

    private interface PacketDecoder {

        boolean decode(ByteBuf bytes);
    }

    private static void assertStrictPacket(ByteBuf encoded, PacketDecoder decoder) {
        Assert.assertTrue("valid packet must decode", decoder.decode(copy(encoded, encoded.readableBytes())));
        Assert.assertFalse("truncated packet must be rejected",
            decoder.decode(copy(encoded, encoded.readableBytes() - 1)));
        ByteBuf trailing = copy(encoded, encoded.readableBytes());
        trailing.writeByte(1);
        Assert.assertFalse("trailing packet bytes must be rejected", decoder.decode(trailing));
    }

    private static ByteBuf encode(cpw.mods.fml.common.network.simpleimpl.IMessage packet) {
        ByteBuf bytes = Unpooled.buffer();
        packet.toBytes(bytes);
        return bytes;
    }

    private static void assertWriterRollback(cpw.mods.fml.common.network.simpleimpl.IMessage packet) {
        ByteBuf bytes = Unpooled.buffer();
        bytes.writeByte(99);
        int start = bytes.writerIndex();
        try {
            packet.toBytes(bytes);
            Assert.fail("over-budget packet must fail before send");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(start, bytes.writerIndex());
        }
    }

    private static boolean malformedHookWithSpeed(double speed) {
        ByteBuf bytes = Unpooled.buffer();
        bytes.writeDouble(speed);
        bytes.writeBoolean(true);
        bytes.writeBoolean(true);
        bytes.writeInt(GrappleHookMode.QUEUE.getId());
        PacketGrappleHookConfig packet = new PacketGrappleHookConfig();
        packet.fromBytes(bytes);
        return packet.malformed;
    }

    private static GrappleRouteEntry route(String id, int nodeCount, long createdAt) {
        GrappleRouteEntry route = new GrappleRouteEntry();
        route.routeId = id;
        route.name = "Route " + id;
        route.createdAt = createdAt;
        for (int i = 0; i < nodeCount; i++) {
            route.nodes.add(new BlockPos(i, i % 128, -i));
        }
        return route;
    }

    private static List<GrappleRouteEntry> flattenRoutes(List<PacketGrapplePathSync> packets) {
        List<GrappleRouteEntry> routes = new ArrayList<GrappleRouteEntry>();
        for (PacketGrapplePathSync packet : packets) {
            routes.addAll(packet.routes);
        }
        return routes;
    }

    private static ItemStack largeStack(int bytes, long seed) {
        ItemStack stack = new ItemStack(new Item(), 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("random", randomBytes(bytes, seed));
        stack.stackTagCompound = tag;
        return stack;
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    private static ByteBuf copy(ByteBuf source, int length) {
        ByteBuf copy = Unpooled.buffer(length);
        source.getBytes(source.readerIndex(), copy, length);
        return copy;
    }
}
