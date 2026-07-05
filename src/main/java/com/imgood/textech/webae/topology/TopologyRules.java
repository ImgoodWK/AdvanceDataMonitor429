package com.imgood.textech.webae.topology;

/**
 * Built-in topology classification and layout rules (Phase 1 defaults).
 *
 * <ul>
 * <li>Grouping: aggregate by device class, not by dimension</li>
 * <li>channelCost: controller/drive/cpu/quantum=0; interface/p2p/misc=1</li>
 * <li>Layout: tree (controller → cable tiers → devices; drives → cells)</li>
 * <li>Spatial bin: 64×64 chunks</li>
 * <li>Cable tiers: smart ≤8, covered ≤32, dense beyond</li>
 * </ul>
 */
public final class TopologyRules {

    public static final int SPATIAL_BIN_SIZE = 64;
    public static final int CABLE_SMART_MAX = 8;
    public static final int CABLE_COVERED_MAX = 32;
    public static final String HUB_GROUP = "controller";
    public static final String LAYOUT_TREE = "tree";
    public static final String LAYOUT_STAR = "star";

    private static final String[] BRANCH_ORDER = {
        "drive", "interface", "cpu", "p2p", "quantum", "misc"
    };

    private TopologyRules() {}

    public static TopologyNodeType classify(String className) {
        return classify(className, null);
    }

    public static TopologyNodeType classify(String className, net.minecraft.item.ItemStack representation) {
        if (className == null || className.isEmpty()) {
            return TopologyNodeType.MISC;
        }
        String simple = simpleName(className);
        if (containsAny(simple, "Cable", "GridBlock", "GridNode", "MultiblockNode")) {
            return TopologyNodeType.MISC;
        }
        if (containsAny(simple, "Controller")) {
            return TopologyNodeType.CONTROLLER;
        }
        if (containsAny(simple, "Drive", "Chest", "IOPort", "MEChest")) {
            return TopologyNodeType.DRIVE;
        }
        if (containsAny(simple, "Interface", "PatternProvider", "PatternTerminal")) {
            return TopologyNodeType.INTERFACE;
        }
        if (containsAny(simple, "CraftingCPU", "CraftingMonitor", "CraftingUnit", "MolecularAssembler")) {
            return TopologyNodeType.CPU;
        }
        if (containsAny(simple, "PartP2P", "P2P")) {
            return TopologyNodeType.P2P;
        }
        if (containsAny(simple, "Quantum", "QNB", "Singularity")) {
            return TopologyNodeType.QUANTUM;
        }
        if (representation != null && representation.getItem() != null) {
            String reg = net.minecraft.item.Item.itemRegistry.getNameForObject(representation.getItem());
            if (reg != null) {
                if (reg.contains("Drive") || reg.contains("Chest")) {
                    return TopologyNodeType.DRIVE;
                }
                if (reg.contains("Interface")) {
                    return TopologyNodeType.INTERFACE;
                }
            }
        }
        return TopologyNodeType.MISC;
    }

    public static int channelCostFor(TopologyNodeType type) {
        if (type == null) {
            return 1;
        }
        switch (type) {
            case CONTROLLER:
            case DRIVE:
            case CPU:
            case QUANTUM:
            case CELL:
            case CABLE_SMART:
            case CABLE_DENSE:
                return 0;
            case INTERFACE:
            case P2P:
            case MISC:
            case SPATIAL_BIN:
            default:
                return 1;
        }
    }

    public static String displayNameFor(TopologyNodeType type) {
        if (type == null) {
            return "Misc";
        }
        switch (type) {
            case CONTROLLER:
                return "ME Controller";
            case DRIVE:
                return "Storage (Drive/Chest)";
            case INTERFACE:
                return "Interface / Provider";
            case CPU:
                return "Crafting CPU";
            case P2P:
                return "P2P Tunnel";
            case QUANTUM:
                return "Quantum Bridge";
            case CELL:
                return "Storage Cell";
            case CABLE_SMART:
                return "Smart Cable";
            case CABLE_DENSE:
                return "Dense Cable";
            case SPATIAL_BIN:
                return "Spatial Bin";
            case MISC:
            default:
                return "Other Devices";
        }
    }

    public static String iconItemIdFor(TopologyNodeType type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case CONTROLLER:
                return "appeng:tile.BlockController";
            case DRIVE:
                return "appeng:tile.BlockDrive";
            case INTERFACE:
                return "appeng:tile.BlockInterface";
            case CPU:
                return "appeng:tile.BlockCraftingUnit";
            case P2P:
                return "appeng:item.ItemMultiPart";
            case QUANTUM:
                return "appeng:tile.BlockQuantumLinkChamber";
            case CELL:
                return "appeng:item.ItemBasicStorageCell";
            case CABLE_SMART:
                return "appeng:item.ItemMultiPart";
            case CABLE_DENSE:
                return "appeng:item.ItemMultiPart";
            case MISC:
            case SPATIAL_BIN:
            default:
                return "appeng:item.ItemMultiMaterial";
        }
    }

    public static String cableTypeForLoad(int usedChannels) {
        if (usedChannels <= CABLE_SMART_MAX) {
            return "smart";
        }
        if (usedChannels <= CABLE_COVERED_MAX) {
            return "covered";
        }
        return "dense";
    }

    public static String[] branchOrder() {
        return BRANCH_ORDER;
    }

    public static int spatialBinIndex(int blockCoord) {
        if (blockCoord < 0) {
            return (blockCoord - SPATIAL_BIN_SIZE + 1) / SPATIAL_BIN_SIZE;
        }
        return blockCoord / SPATIAL_BIN_SIZE;
    }

    public static String spatialBinId(int dim, int binX, int binZ) {
        return "bin:" + dim + ":" + binX + ":" + binZ;
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
