package com.imgood.advancedatamonitor.network.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.imgood.advancedatamonitor.client.PocketClientCache;
import com.imgood.advancedatamonitor.handler.PocketState;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S→C sync packet for the Dimensional Pocket. Carries either a full state
 * snapshot (metadata + all pages) or a single page delta. The client applies
 * it to PocketClientCache so the overlay and tooltip can render local data
 * without round-tripping per slot click.
 */
public class PacketPocketSync implements IMessage {

    public static final byte KIND_FULL = 0;
    public static final byte KIND_SINGLE_PAGE = 1;
    /** Upgrade counts / overlay prefs only — no page item payloads. */
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

    @Override
    public void toBytes(ByteBuf buf) {
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
            if (payload.slots != null) {
                for (int s = 0; s < payload.slots.length; s++) {
                    if (payload.slots[s] != null) {
                        NBTTagCompound slotTag = new NBTTagCompound();
                        slotTag.setInteger("Slot", s);
                        payload.slots[s].writeToNBT(slotTag);
                        slotList.appendTag(slotTag);
                    }
                }
            }
            NBTTagCompound pageTag = new NBTTagCompound();
            pageTag.setInteger("Page", payload.pageIndex);
            pageTag.setTag("Items", slotList);
            ByteBufUtils.writeTag(buf, pageTag);
        }
        buf.writeBoolean(hasCursor);
        if (hasCursor) {
            NBTTagCompound cursorTag = new NBTTagCompound();
            if (cursorStack != null) cursorStack.writeToNBT(cursorTag);
            ByteBufUtils.writeTag(buf, cursorTag);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
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
        pages.clear();
        int pagePayloadCount = buf.readShort();
        for (int i = 0; i < pagePayloadCount; i++) {
            int pIndex = buf.readInt();
            NBTTagCompound pageTag = ByteBufUtils.readTag(buf);
            PagePayload payload = new PagePayload();
            payload.pageIndex = pIndex;
            payload.slots = new ItemStack[slotsPerPage];
            if (pageTag != null) {
                NBTTagList slotList = pageTag.getTagList("Items", 10);
                for (int j = 0; j < slotList.tagCount(); j++) {
                    NBTTagCompound slotTag = slotList.getCompoundTagAt(j);
                    int s = slotTag.getInteger("Slot");
                    if (s >= 0 && s < payload.slots.length) {
                        payload.slots[s] = ItemStack.loadItemStackFromNBT(slotTag);
                    }
                }
            }
            pages.add(payload);
        }
        hasCursor = buf.readBoolean();
        if (hasCursor) {
            NBTTagCompound cursorTag = ByteBufUtils.readTag(buf);
            cursorStack = ItemStack.loadItemStackFromNBT(cursorTag);
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientHandler implements IMessageHandler<PacketPocketSync, IMessage> {

        @Override
        public IMessage onMessage(PacketPocketSync message, MessageContext ctx) {
            PocketClientCache.apply(message);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                if (mc.thePlayer.openContainer instanceof com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage) {
                    com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage storage =
                        (com.imgood.advancedatamonitor.gui.container.ContainerPocketStorage) mc.thePlayer.openContainer;
                    if (message.kind == PacketPocketSync.KIND_SINGLE_PAGE) {
                        storage.applyClientPage(message.pageIndex);
                    }
                    if (message.kind == PacketPocketSync.KIND_METADATA || message.kind == PacketPocketSync.KIND_FULL) {
                        storage.applyClientUpgradeMetadata();
                    }
                }
                if (mc.thePlayer.openContainer instanceof com.imgood.advancedatamonitor.gui.container.ContainerDimensionalPocket) {
                    ((com.imgood.advancedatamonitor.gui.container.ContainerDimensionalPocket) mc.thePlayer.openContainer)
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
