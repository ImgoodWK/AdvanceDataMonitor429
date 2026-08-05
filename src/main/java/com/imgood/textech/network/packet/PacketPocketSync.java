package com.imgood.textech.network.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.imgood.textech.client.PocketClientCache;
import com.imgood.textech.handler.PocketState;
import com.imgood.textech.utils.NetworkPacketCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * S→C sync packet for the Dimensional Pocket. Carries either a full state
 * snapshot (metadata + all pages) or a single page delta. The client applies
 * it to PocketClientCache so the overlay and tooltip can render local data
 * without round-tripping per slot click.
 */
public class PacketPocketSync implements IMessage {

    public static final int MAX_PACKET_BODY_BYTES = 30000;
    private static final int MAX_PAGE_COUNT = PocketState.PAGES_CAP;
    private static final int MAX_SLOTS_PER_PAGE = PocketState.SLOTS_PER_PAGE_CAP;
    private static final int MAX_NBT_COMPRESSED_BYTES = Short.MAX_VALUE;
    private static final long MAX_NBT_BYTES = 512L * 1024L;
    /** Metadata marker used only as the first packet of a split full snapshot. */
    public static final int FULL_SNAPSHOT_PAGE_INDEX = -1;

    public static final byte KIND_FULL = 0;
    public static final byte KIND_SINGLE_PAGE = 1;
    /** Upgrade counts / overlay prefs only —no page item payloads. */
    public static final byte KIND_METADATA = 2;

    public byte kind = KIND_FULL;
    public int spaceUpgrades;
    public int pageUpgrades;
    public int stackUpgrades;
    public boolean infiniteStackUpgrade;
    public boolean enabled;
    public float windowX;
    public float windowY;
    public boolean collapsed;
    public int pageCount;
    public int slotsPerPage;
    public int pageIndex; // only for KIND_SINGLE_PAGE
    public final List<PagePayload> pages = new ArrayList<PagePayload>();
    // When true, the client handler applies cursorStack to mc.thePlayer.inventory.itemStack.
    // Only set for cursor-mutating actions (WITHDRAW/DEPOSIT); false for all other syncs so
    // a legitimately held vanilla cursor is never cleared by an unrelated pocket sync.
    public boolean hasCursor = false;
    public ItemStack cursorStack = null;
    public boolean malformed;

    public static class PagePayload {

        public int pageIndex;
        public ItemStack[] slots;
    }

    public PacketPocketSync() {}

    public static PacketPocketSync fullState(PocketState state) {
        PacketPocketSync p = new PacketPocketSync();
        p.kind = KIND_FULL;
        p.spaceUpgrades = state.getSpaceUpgrades();
        p.pageUpgrades = state.getPageUpgrades();
        p.stackUpgrades = state.getStackUpgrades();
        p.infiniteStackUpgrade = state.isInfiniteStackUpgrade();
        p.enabled = state.isEnabled();
        p.windowX = state.getWindowX();
        p.windowY = state.getWindowY();
        p.collapsed = state.isCollapsed();
        p.pageCount = state.getPageCount();
        p.slotsPerPage = state.getSlotsPerPage();
        for (int i = 0; i < p.pageCount; i++) {
            PagePayload payload = new PagePayload();
            payload.pageIndex = i;
            payload.slots = new ItemStack[p.slotsPerPage];
            for (int s = 0; s < p.slotsPerPage; s++) {
                payload.slots[s] = state.getStack(i, s);
            }
            p.pages.add(payload);
        }
        return p;
    }

    public static PacketPocketSync singlePage(PocketState state, int pageIndex) {
        PacketPocketSync p = new PacketPocketSync();
        p.kind = KIND_SINGLE_PAGE;
        p.spaceUpgrades = state.getSpaceUpgrades();
        p.pageUpgrades = state.getPageUpgrades();
        p.stackUpgrades = state.getStackUpgrades();
        p.infiniteStackUpgrade = state.isInfiniteStackUpgrade();
        p.enabled = state.isEnabled();
        p.windowX = state.getWindowX();
        p.windowY = state.getWindowY();
        p.collapsed = state.isCollapsed();
        p.pageCount = state.getPageCount();
        p.slotsPerPage = state.getSlotsPerPage();
        p.pageIndex = pageIndex;
        if (pageIndex >= 0 && pageIndex < p.pageCount) {
            PagePayload payload = new PagePayload();
            payload.pageIndex = pageIndex;
            payload.slots = new ItemStack[p.slotsPerPage];
            for (int s = 0; s < p.slotsPerPage; s++) {
                payload.slots[s] = state.getStack(pageIndex, s);
            }
            p.pages.add(payload);
        }
        return p;
    }

    public static PacketPocketSync metadataState(PocketState state) {
        PacketPocketSync p = new PacketPocketSync();
        p.kind = KIND_METADATA;
        p.spaceUpgrades = state.getSpaceUpgrades();
        p.pageUpgrades = state.getPageUpgrades();
        p.stackUpgrades = state.getStackUpgrades();
        p.infiniteStackUpgrade = state.isInfiniteStackUpgrade();
        p.enabled = state.isEnabled();
        p.windowX = state.getWindowX();
        p.windowY = state.getWindowY();
        p.collapsed = state.isCollapsed();
        p.pageCount = state.getPageCount();
        p.slotsPerPage = state.getSlotsPerPage();
        return p;
    }

    /**
     * Build a complete pocket snapshot before any packet is handed to Forge.
     * The old full-state wire format is retained when it fits; otherwise the
     * metadata marker and every page are returned as one validated batch.
     */
    public static List<PacketPocketSync> fullStatePackets(PocketState state) {
        return fullStatePackets(state, false, null);
    }

    /** Same as {@link #fullStatePackets(PocketState)} with an authoritative cursor update. */
    public static List<PacketPocketSync> fullStatePackets(PocketState state, boolean hasCursor,
        ItemStack cursorStack) {
        if (state == null) {
            return Collections.emptyList();
        }

        PacketPocketSync full = fullState(state);
        full.hasCursor = hasCursor;
        full.cursorStack = cursorStack;
        if (full.fitsPacketBudget()) {
            return Collections.singletonList(full);
        }

        PacketPocketSync metadata = metadataState(state);
        metadata.pageIndex = FULL_SNAPSHOT_PAGE_INDEX;
        metadata.hasCursor = hasCursor;
        metadata.cursorStack = cursorStack;
        if (!metadata.fitsPacketBudget()) {
            return Collections.emptyList();
        }

        List<PacketPocketSync> packets = new ArrayList<PacketPocketSync>(state.getPageCount() + 1);
        packets.add(metadata);
        for (int page = 0; page < state.getPageCount(); page++) {
            PacketPocketSync pagePacket = singlePage(state, page);
            if (!pagePacket.fitsPacketBudget()) {
                return Collections.emptyList();
            }
            packets.add(pagePacket);
        }
        return packets;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int start = buf.writerIndex();
        try {
            validateShape();
            buf.writeByte(kind);
            buf.writeInt(spaceUpgrades);
            buf.writeInt(pageUpgrades);
            buf.writeInt(stackUpgrades);
            buf.writeBoolean(infiniteStackUpgrade);
            buf.writeBoolean(enabled);
            buf.writeFloat(windowX);
            buf.writeFloat(windowY);
            buf.writeBoolean(collapsed);
            buf.writeInt(pageCount);
            buf.writeInt(slotsPerPage);
            buf.writeInt(pageIndex);
            buf.writeShort(pages.size());
            for (PagePayload payload : pages) {
                buf.writeInt(payload.pageIndex);
                NBTTagList slotList = new NBTTagList();
                for (int s = 0; s < payload.slots.length; s++) {
                    if (payload.slots[s] != null) {
                        NBTTagCompound slotTag = new NBTTagCompound();
                        slotTag.setInteger("Slot", s);
                        PocketState.writeItemStackToNBT(payload.slots[s], slotTag);
                        slotList.appendTag(slotTag);
                    }
                }
                NBTTagCompound pageTag = new NBTTagCompound();
                pageTag.setInteger("Page", payload.pageIndex);
                pageTag.setTag("Items", slotList);
                NetworkPacketCodec.writeTag(buf, pageTag, MAX_NBT_COMPRESSED_BYTES);
            }
            buf.writeBoolean(hasCursor);
            if (hasCursor) {
                NBTTagCompound cursorTag = new NBTTagCompound();
                if (cursorStack != null) PocketState.writeItemStackToNBT(cursorStack, cursorTag);
                NetworkPacketCodec.writeTag(buf, cursorTag, MAX_NBT_COMPRESSED_BYTES);
            }
            if (buf.writerIndex() - start > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Pocket sync exceeds packet body limit");
            }
        } catch (RuntimeException error) {
            buf.writerIndex(start);
            throw error;
        }
    }

    public boolean fitsPacketBudget() {
        ByteBuf scratch = Unpooled.buffer(128);
        try {
            toBytes(scratch);
            return scratch.readableBytes() <= MAX_PACKET_BODY_BYTES;
        } catch (RuntimeException error) {
            return false;
        } finally {
            scratch.release();
        }
    }

    private void validateShape() {
        if (kind != KIND_FULL && kind != KIND_SINGLE_PAGE && kind != KIND_METADATA) {
            throw new IllegalArgumentException("Invalid pocket sync kind");
        }
        if (pageCount < 1 || pageCount > MAX_PAGE_COUNT || slotsPerPage < 1
            || slotsPerPage > MAX_SLOTS_PER_PAGE) {
            throw new IllegalArgumentException("Invalid pocket sync dimensions");
        }
        if (pages.size() > MAX_PAGE_COUNT) {
            throw new IllegalArgumentException("Pocket sync page count exceeds packet limit");
        }
        if (kind == KIND_FULL) {
            if (pageIndex != 0 || pages.size() != pageCount) {
                throw new IllegalArgumentException("Full pocket sync does not contain every page");
            }
        } else if (kind == KIND_SINGLE_PAGE) {
            if (pageIndex < 0 || pageIndex >= pageCount || pages.size() != 1
                || pages.get(0) == null || pages.get(0).pageIndex != pageIndex) {
                throw new IllegalArgumentException("Invalid single pocket page");
            }
        } else if (!pages.isEmpty() || (pageIndex != 0 && pageIndex != FULL_SNAPSHOT_PAGE_INDEX)) {
            throw new IllegalArgumentException("Metadata pocket sync contains page payloads");
        }

        boolean[] seen = new boolean[pageCount];
        for (PagePayload payload : pages) {
            if (payload == null || payload.pageIndex < 0 || payload.pageIndex >= pageCount
                || seen[payload.pageIndex] || payload.slots == null || payload.slots.length != slotsPerPage) {
                throw new IllegalArgumentException("Invalid pocket page payload");
            }
            seen[payload.pageIndex] = true;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        malformed = false;
        kind = KIND_FULL;
        spaceUpgrades = 0;
        pageUpgrades = 0;
        stackUpgrades = 0;
        infiniteStackUpgrade = false;
        enabled = false;
        windowX = 0.0F;
        windowY = 0.0F;
        collapsed = false;
        pageCount = 0;
        slotsPerPage = 0;
        pageIndex = 0;
        pages.clear();
        hasCursor = false;
        cursorStack = null;
        try {
            int start = buf.readerIndex();
            if (buf.readableBytes() > MAX_PACKET_BODY_BYTES) {
                throw new IllegalArgumentException("Pocket sync exceeds packet body limit");
            }
            kind = buf.readByte();
            if (kind != KIND_FULL && kind != KIND_SINGLE_PAGE && kind != KIND_METADATA) {
                throw new IllegalArgumentException("Invalid pocket sync kind");
            }
            spaceUpgrades = buf.readInt();
            pageUpgrades = buf.readInt();
            stackUpgrades = buf.readInt();
            infiniteStackUpgrade = buf.readBoolean();
            enabled = buf.readBoolean();
            windowX = buf.readFloat();
            windowY = buf.readFloat();
            collapsed = buf.readBoolean();
            pageCount = buf.readInt();
            slotsPerPage = buf.readInt();
            pageIndex = buf.readInt();
            if (pageCount < 1 || pageCount > MAX_PAGE_COUNT || slotsPerPage < 1
                || slotsPerPage > MAX_SLOTS_PER_PAGE) {
                throw new IllegalArgumentException("Invalid pocket sync dimensions");
            }
            pages.clear();
            int pagePayloadCount = buf.readUnsignedShort();
            if ((kind == KIND_METADATA && pagePayloadCount != 0)
                || (kind == KIND_SINGLE_PAGE && pagePayloadCount != 1)
                || (kind == KIND_FULL && pagePayloadCount != pageCount)
                || pagePayloadCount > MAX_PAGE_COUNT || pagePayloadCount > buf.readableBytes() / 6) {
                throw new IllegalArgumentException("Invalid pocket page payload count");
            }
            boolean[] seenPages = new boolean[pageCount];
            for (int i = 0; i < pagePayloadCount; i++) {
                int pIndex = buf.readInt();
                if (pIndex < 0 || pIndex >= pageCount || seenPages[pIndex]) {
                    throw new IllegalArgumentException("Invalid pocket page index");
                }
                seenPages[pIndex] = true;
                NBTTagCompound pageTag = NetworkPacketCodec.readTag(
                    buf,
                    MAX_NBT_COMPRESSED_BYTES,
                    MAX_NBT_BYTES);
                PagePayload payload = new PagePayload();
                payload.pageIndex = pIndex;
                payload.slots = new ItemStack[slotsPerPage];
                if (pageTag != null) {
                    if (pageTag.hasKey("Page") && pageTag.getInteger("Page") != pIndex) {
                        throw new IllegalArgumentException("Pocket page tag index mismatch");
                    }
                    NBTTagList slotList = pageTag.getTagList("Items", 10);
                    if (slotList.tagCount() > slotsPerPage) {
                        throw new IllegalArgumentException("Invalid pocket slot payload count");
                    }
                    boolean[] seenSlots = new boolean[slotsPerPage];
                    for (int j = 0; j < slotList.tagCount(); j++) {
                        NBTTagCompound slotTag = slotList.getCompoundTagAt(j);
                        int s = slotTag.getInteger("Slot");
                        if (s < 0 || s >= payload.slots.length || seenSlots[s]) {
                            throw new IllegalArgumentException("Invalid pocket slot index");
                        }
                        seenSlots[s] = true;
                        payload.slots[s] = PocketState.readItemStackFromNBT(slotTag);
                    }
                }
                pages.add(payload);
            }
            if (kind == KIND_FULL && pageIndex != 0) {
                throw new IllegalArgumentException("Invalid full pocket page marker");
            }
            if (kind == KIND_SINGLE_PAGE
                && (pageIndex < 0 || pageIndex >= pageCount || pages.get(0).pageIndex != pageIndex)) {
                throw new IllegalArgumentException("Invalid single pocket page marker");
            }
            if (kind == KIND_METADATA && pageIndex != 0 && pageIndex != FULL_SNAPSHOT_PAGE_INDEX) {
                throw new IllegalArgumentException("Invalid metadata pocket page marker");
            }
            hasCursor = buf.readBoolean();
            if (hasCursor) {
                NBTTagCompound cursorTag = NetworkPacketCodec.readTag(
                    buf,
                    MAX_NBT_COMPRESSED_BYTES,
                    MAX_NBT_BYTES);
                cursorStack = PocketState.readItemStackFromNBT(cursorTag);
            } else {
                cursorStack = null;
            }
            if (buf.readerIndex() - start > MAX_PACKET_BODY_BYTES || buf.isReadable()) {
                throw new IllegalArgumentException("Pocket sync has trailing or oversized data");
            }
        } catch (RuntimeException error) {
            malformed = true;
            kind = KIND_FULL;
            spaceUpgrades = 0;
            pageUpgrades = 0;
            stackUpgrades = 0;
            infiniteStackUpgrade = false;
            enabled = false;
            windowX = 0.0F;
            windowY = 0.0F;
            collapsed = false;
            pageCount = 0;
            slotsPerPage = 0;
            pageIndex = 0;
            pages.clear();
            cursorStack = null;
            hasCursor = false;
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketPocketSync, IMessage> {

        @Override
        public IMessage onMessage(PacketPocketSync message, MessageContext ctx) {
            if (message == null || message.malformed) {
                return null;
            }
            boolean navigateToSinglePage = PocketClientCache.apply(message);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                if (mc.thePlayer.openContainer instanceof com.imgood.textech.gui.container.ContainerPocketStorage) {
                    com.imgood.textech.gui.container.ContainerPocketStorage storage = (com.imgood.textech.gui.container.ContainerPocketStorage) mc.thePlayer.openContainer;
                    if (navigateToSinglePage) {
                        storage.applyClientPage(message.pageIndex);
                    }
                    if (message.kind == PacketPocketSync.KIND_METADATA || message.kind == PacketPocketSync.KIND_FULL) {
                        storage.applyClientUpgradeMetadata();
                    }
                }
                if (mc.thePlayer.openContainer instanceof com.imgood.textech.gui.container.ContainerDimensionalPocket) {
                    ((com.imgood.textech.gui.container.ContainerDimensionalPocket) mc.thePlayer.openContainer)
                        .refreshUpgradeDisplayFromClientCache();
                }
            }
            if (message.hasCursor) {
                if (mc.thePlayer != null) {
                    mc.thePlayer.inventory.setItemStack(message.cursorStack);
                }
            }
            return null;
        }
    }
}
