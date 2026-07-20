package com.imgood.textech.webae.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.recipe.RecipeItemEntries;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;

/**
 * Collects AE2 crafting CPUs as cluster-level topology facilities. Each {@link ICraftingCPU}
 * (a multi-block {@link CraftingCPUCluster}) becomes a single aggregated topology node so
 * the abstract tree and device list show one icon per CPU, with constituent blocks listed
 * only in the detail drawer.
 *
 * <p>
 * Must run on the server main thread (grid access).
 * </p>
 */
public final class CraftingCpuTopologyCollector {

    private CraftingCpuTopologyCollector() {}

    public static List<CpuClusterFacility> collect(String ownerUuid, int networkId) {
        List<CpuClusterFacility> result = new ArrayList<CpuClusterFacility>();
        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            return result;
        }
        ICraftingGrid craftingGrid;
        try {
            craftingGrid = grid.getCache(ICraftingGrid.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Topology: crafting grid cache unavailable", e);
            return result;
        }
        if (craftingGrid == null) {
            return result;
        }

        Collection<ICraftingCPU> cpus;
        try {
            cpus = craftingGrid.getCpus();
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Topology: getCpus failed", e);
            return result;
        }
        if (cpus == null || cpus.isEmpty()) {
            return result;
        }

        int fallbackIndex = 1;
        for (ICraftingCPU cpu : cpus) {
            if (cpu == null) {
                continue;
            }
            CpuClusterFacility facility = describeCpu(cpu, fallbackIndex++);
            if (facility != null) {
                result.add(facility);
            }
        }
        return result;
    }

    private static CpuClusterFacility describeCpu(ICraftingCPU cpu, int fallbackIndex) {
        CpuClusterFacility facility = new CpuClusterFacility();
        try {
            facility.name = cpu.getName();
        } catch (Exception ignored) {
            facility.name = "";
        }
        if (facility.name == null || facility.name.isEmpty()) {
            facility.name = "Crafting CPU #" + fallbackIndex;
        }
        facility.displayName = facility.name;
        try {
            facility.coProcessors = cpu.getCoProcessors();
        } catch (Exception ignored) {}
        try {
            facility.availableStorage = cpu.getAvailableStorage();
        } catch (Exception ignored) {}
        try {
            facility.usedStorage = cpu.getUsedStorage();
        } catch (Exception ignored) {}
        try {
            facility.busy = cpu.isBusy();
        } catch (Exception ignored) {}

        int unitCount = 0;
        int storageUnits = 0;
        int acceleratorUnits = 0;
        int monitorUnits = 0;
        if (cpu instanceof CraftingCPUCluster) {
            try {
                Iterator<IGridHost> tiles = ((CraftingCPUCluster) cpu).getTiles();
                while (tiles != null && tiles.hasNext()) {
                    IGridHost host = tiles.next();
                    if (!(host instanceof TileEntity)) {
                        unitCount++;
                        continue;
                    }
                    TileEntity te = (TileEntity) host;
                    CpuClusterFacility.Unit unit = new CpuClusterFacility.Unit();
                    unit.x = te.xCoord;
                    unit.y = te.yCoord;
                    unit.z = te.zCoord;
                    unit.dim = te.getWorldObj() != null ? te.getWorldObj().provider.dimensionId : 0;
                    if (host instanceof TileCraftingTile) {
                        TileCraftingTile ct = (TileCraftingTile) host;
                        try {
                            unit.storage = ct.isStorage();
                        } catch (Exception ignored) {}
                        try {
                            unit.accelerator = ct.isAccelerator();
                        } catch (Exception ignored) {}
                        try {
                            unit.monitor = ct.isStatus();
                        } catch (Exception ignored) {}
                        if (unit.storage) storageUnits++;
                        if (unit.accelerator) acceleratorUnits++;
                        if (unit.monitor) monitorUnits++;
                        unit.iconItemId = resolveUnitIconItemId(ct, te);
                    } else {
                        unit.iconItemId = TopologyRules
                            .iconItemIdForCraftingComponent(unit.storage, unit.accelerator, unit.monitor);
                    }
                    facility.units.add(unit);
                    unitCount++;
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("[WebAE] Topology: CPU cluster tiles enumeration failed", e);
            }
        }
        facility.unitCount = Math.max(unitCount, 1);
        facility.storageUnits = storageUnits;
        facility.acceleratorUnits = acceleratorUnits;
        facility.monitorUnits = monitorUnits;

        // Use the cluster's first unit as the representative coordinate (if any).
        if (!facility.units.isEmpty()) {
            CpuClusterFacility.Unit first = facility.units.get(0);
            facility.x = first.x;
            facility.y = first.y;
            facility.z = first.z;
            facility.dim = first.dim;
        }
        return facility;
    }

    private static String resolveUnitIconItemId(TileCraftingTile ct, TileEntity te) {
        ItemStack stack = representationStack(ct);
        if (stack != null && stack.getItem() != null) {
            return stackItemId(stack);
        }
        boolean storage = false;
        boolean accelerator = false;
        boolean monitor = false;
        try {
            storage = ct.isStorage();
        } catch (Exception ignored) {}
        try {
            accelerator = ct.isAccelerator();
        } catch (Exception ignored) {}
        try {
            monitor = ct.isStatus();
        } catch (Exception ignored) {}
        return TopologyRules.iconItemIdForCraftingComponent(storage, accelerator, monitor);
    }

    private static ItemStack representationStack(TileCraftingTile ct) {
        if (!(ct instanceof IGridHost)) {
            return null;
        }
        try {
            IGridNode node = ((IGridHost) ct).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null) {
                return null;
            }
            IGridBlock block = node.getGridBlock();
            if (block == null) {
                return null;
            }
            ItemStack rep = block.getMachineRepresentation();
            if (rep != null && rep.getItem() != null) {
                return rep;
            }
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Topology: crafting unit representation failed", e);
        }
        return null;
    }

    private static String stackItemId(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "";
        }
        String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null || registryName.isEmpty()) {
            return "";
        }
        int meta = stack.getItemDamage();
        if (meta == Short.MAX_VALUE) meta = 0;
        return RecipeItemEntries.buildItemId(registryName, meta);
    }

    /** One aggregated crafting CPU (multi-block cluster) ready to become a topology node. */
    public static final class CpuClusterFacility {

        public String name = "";
        public String displayName = "";
        public int coProcessors;
        public long availableStorage;
        public long usedStorage;
        public boolean busy;
        public int unitCount = 1;
        public int storageUnits;
        public int acceleratorUnits;
        public int monitorUnits;
        public int x;
        public int y;
        public int z;
        public int dim;
        public final List<Unit> units = new ArrayList<Unit>();

        public static final class Unit {

            public int x;
            public int y;
            public int z;
            public int dim;
            public boolean storage;
            public boolean accelerator;
            public boolean monitor;
            public String iconItemId = "";
        }
    }
}
