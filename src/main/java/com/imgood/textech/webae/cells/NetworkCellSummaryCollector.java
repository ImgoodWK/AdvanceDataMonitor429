package com.imgood.textech.webae.cells;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

/**
 * Collects AE2 cell byte summary including infinite cell detection (mirrors assistant bytes query).
 */
public final class NetworkCellSummaryCollector {

    private NetworkCellSummaryCollector() {}

    public static NetworkCellSummaryDto collect(String ownerUuid, int networkId) {
        NetworkCellSummaryDto dto = new NetworkCellSummaryDto();
        dto.networkId = networkId;
        dto.timestamp = System.currentTimeMillis();

        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            return dto;
        }

        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (group == null) {
            return dto;
        }

        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            return dto;
        }

        ScanResult scan = scanGridCells(grid);
        dto.hasInfiniteItemCells = scan.hasInfiniteItems;
        dto.hasInfiniteFluidCells = scan.hasInfiniteFluids;
        dto.nonInfiniteItemUsed = scan.nonInfiniteItemUsed;
        dto.nonInfiniteItemTotal = scan.nonInfiniteItemTotal;
        dto.nonInfiniteFluidUsed = scan.nonInfiniteFluidUsed;
        dto.nonInfiniteFluidTotal = scan.nonInfiniteFluidTotal;

        dto.itemUsedBytes = scan.nonInfiniteItemUsed;
        dto.itemTotalBytes = scan.nonInfiniteItemTotal;
        if (scan.hasInfiniteItems) {
            dto.itemTotalBytes += scan.infiniteItemBytes;
        }
        dto.fluidUsedBytes = scan.nonInfiniteFluidUsed;
        dto.fluidTotalBytes = scan.nonInfiniteFluidTotal;
        if (scan.hasInfiniteFluids) {
            dto.fluidTotalBytes += scan.infiniteFluidBytes;
        }

        if (scan.nonInfiniteItemTotal > 0) {
            dto.itemUsagePercent = (double) scan.nonInfiniteItemUsed / (double) scan.nonInfiniteItemTotal * 100.0;
        }
        if (scan.nonInfiniteFluidTotal > 0) {
            dto.fluidUsagePercent = (double) scan.nonInfiniteFluidUsed / (double) scan.nonInfiniteFluidTotal * 100.0;
        }
        return dto;
    }

    private static ScanResult scanGridCells(IGrid grid) {
        ScanResult result = new ScanResult();
        try {
            for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                if (!IChestOrDrive.class.isAssignableFrom(clazz)) {
                    continue;
                }
                for (IGridNode node : grid.getMachines(clazz)) {
                    appeng.api.util.DimensionalCoord coord = node.getGridBlock()
                        .getLocation();
                    World world = coord.getWorld();
                    if (world == null) {
                        continue;
                    }
                    TileEntity te = world.getTileEntity(coord.x, coord.y, coord.z);
                    if (te instanceof TileDrive) {
                        TileDrive drive = (TileDrive) te;
                        for (int i = 0; i < drive.getInternalInventory()
                            .getSizeInventory(); i++) {
                            ItemStack stack = drive.getInternalInventory()
                                .getStackInSlot(i);
                            if (stack != null) {
                                classifyCell(result, stack);
                            }
                        }
                    } else if (te instanceof TileChest) {
                        TileChest chest = (TileChest) te;
                        ItemStack stack = chest.getInternalInventory()
                            .getStackInSlot(0);
                        if (stack != null) {
                            classifyCell(result, stack);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static void classifyCell(ScanResult result, ItemStack stack) {
        IMEInventoryHandler itemInv = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInv instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) itemInv).getCellInv();
            if (cell != null) {
                if (AssistantServerServices.isInfiniteCellPublic(cell)) {
                    result.hasInfiniteItems = true;
                    result.infiniteItemBytes += cell.getTotalBytes();
                } else {
                    result.nonInfiniteItemTotal += cell.getTotalBytes();
                    result.nonInfiniteItemUsed += cell.getUsedBytes();
                }
            }
        }
        IMEInventoryHandler fluidInv = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInv instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) fluidInv).getCellInv();
            if (cell != null) {
                if (AssistantServerServices.isInfiniteCellPublic(cell)) {
                    result.hasInfiniteFluids = true;
                    result.infiniteFluidBytes += cell.getTotalBytes();
                } else {
                    result.nonInfiniteFluidTotal += cell.getTotalBytes();
                    result.nonInfiniteFluidUsed += cell.getUsedBytes();
                }
            }
        }
    }

    private static final class ScanResult {

        boolean hasInfiniteItems;
        boolean hasInfiniteFluids;
        long nonInfiniteItemTotal;
        long nonInfiniteItemUsed;
        long nonInfiniteFluidTotal;
        long nonInfiniteFluidUsed;
        long infiniteItemBytes;
        long infiniteFluidBytes;
    }
}
