package com.imgood.advancedatamonitor.items;

import net.minecraft.nbt.NBTTagCompound;

public class LinkScanEntry {

    public int slotIndex;
    public int dimension;
    public int x;
    public int y;
    public int z;
    public String owner;
    public String blockTypeId;
    public String alias;

    public LinkScanEntry() {}

    public LinkScanEntry(int slotIndex, int dimension, int x, int y, int z, String owner, LinkScanBlockType type,
        String alias) {
        this.slotIndex = slotIndex;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner == null ? "" : owner;
        this.blockTypeId = type == null ? "" : type.getId();
        this.alias = alias == null ? "" : alias;
    }

    public String locationKey() {
        return dimension + ":" + x + ":" + y + ":" + z;
    }

    public LinkScanBlockType getBlockType() {
        return LinkScanBlockType.fromId(blockTypeId);
    }

    public boolean hasAlias() {
        return alias != null && !alias.isEmpty();
    }

    public boolean hasOwner() {
        return owner != null && !owner.isEmpty();
    }

    public static LinkScanEntry fromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        LinkScanEntry entry = new LinkScanEntry();
        entry.slotIndex = tag.getInteger("slotIndex");
        entry.dimension = tag.getInteger("dim");
        entry.x = tag.getInteger("x");
        entry.y = tag.getInteger("y");
        entry.z = tag.getInteger("z");
        entry.owner = tag.getString("owner");
        entry.blockTypeId = tag.getString("type");
        entry.alias = tag.getString("alias");
        if (entry.owner == null) {
            entry.owner = "";
        }
        if (entry.alias == null) {
            entry.alias = "";
        }
        return entry;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("slotIndex", slotIndex);
        tag.setInteger("dim", dimension);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setString("owner", owner == null ? "" : owner);
        tag.setString("type", blockTypeId == null ? "" : blockTypeId);
        tag.setString("alias", alias == null ? "" : alias);
        return tag;
    }
}
