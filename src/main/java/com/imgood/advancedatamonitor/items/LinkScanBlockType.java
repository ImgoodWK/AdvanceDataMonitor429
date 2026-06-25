package com.imgood.advancedatamonitor.items;

import net.minecraft.tileentity.TileEntity;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

public enum LinkScanBlockType {

    DATA_MONITOR("data_monitor", TileEntityAdvanceDataMonitor.class),
    NETWORK_LINK("network_link", TileEntityAdvanceNetworkLink.class),
    STORAGE_LINK("storage_link", TileEntityAdvanceStorageLink.class),
    CRAFTING_LINK("crafting_link", TileEntityAdvanceCraftingLink.class);

    private final String id;
    private final Class<? extends TileEntity> tileClass;

    LinkScanBlockType(String id, Class<? extends TileEntity> tileClass) {
        this.id = id;
        this.tileClass = tileClass;
    }

    public String getId() {
        return id;
    }

    public String getLangKey() {
        return "adm.scanner.type." + id;
    }

    public Class<? extends TileEntity> getTileClass() {
        return tileClass;
    }

    public static LinkScanBlockType fromTileEntity(TileEntity tile) {
        if (tile == null) {
            return null;
        }
        for (LinkScanBlockType type : values()) {
            if (type.tileClass.isInstance(tile)) {
                return type;
            }
        }
        return null;
    }

    public static LinkScanBlockType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (LinkScanBlockType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
