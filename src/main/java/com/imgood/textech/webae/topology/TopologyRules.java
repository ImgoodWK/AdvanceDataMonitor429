package com.imgood.textech.webae.topology;

import net.minecraft.item.ItemStack;

/**
 * Built-in topology classification and layout rules.
 *
 * <ul>
 * <li>Grouping: aggregate by fine subtype + item id (network-tool parity)</li>
 * <li>Tree layout: controller hub, terminals above, smart branches below</li>
 * <li>Star layout: double ring (inner=channel devices, outer=zero-channel)</li>
 * <li>Cable tiers: smart ≤8, covered ≤32, dense beyond (edge coloring only)</li>
 * </ul>
 */
public final class TopologyRules {

    public static final int SPATIAL_BIN_SIZE = 64;
    public static final int CABLE_SMART_MAX = 8;
    public static final int CABLE_COVERED_MAX = 32;
    public static final int SMART_BRANCH_COUNT = 4;
    public static final String HUB_GROUP = "controller";
    public static final String LAYOUT_TREE = "tree";
    public static final String LAYOUT_STAR = "star";

    // --- fine-grained subtypes ---
    public static final String SUB_CONTROLLER = "controller";
    public static final String SUB_ENERGY_CELL = "energy_cell";
    public static final String SUB_ENERGY_ACCEPTOR = "energy_acceptor";
    public static final String SUB_TERMINAL_ME = "terminal_me";
    public static final String SUB_TERMINAL_CRAFTING = "terminal_crafting";
    public static final String SUB_TERMINAL_PATTERN_ENCODING = "terminal_pattern_encoding";
    public static final String SUB_TERMINAL_PATTERN_ACCESS = "terminal_pattern_access";
    public static final String SUB_TERMINAL_WIRELESS = "terminal_wireless";
    public static final String SUB_WIRELESS_ACCESS_POINT = "wireless_access_point";
    public static final String SUB_SECURITY_TERMINAL = "security_terminal";
    public static final String SUB_TERMINAL_OTHER = "terminal_other";
    public static final String SUB_BUS_IMPORT = "bus_import";
    public static final String SUB_BUS_EXPORT = "bus_export";
    public static final String SUB_BUS_STORAGE = "bus_storage";
    public static final String SUB_BUS_ORE_FILTER = "bus_ore_filter";
    public static final String SUB_INTERFACE = "interface";
    public static final String SUB_PATTERN_PROVIDER = "pattern_provider";
    public static final String SUB_MONITOR_STORAGE = "monitor_storage";
    public static final String SUB_MONITOR_CONVERSION = "monitor_conversion";
    public static final String SUB_EMITTER_LEVEL = "emitter_level";
    public static final String SUB_EMITTER_ENERGY = "emitter_energy";
    public static final String SUB_DRIVE = "drive";
    public static final String SUB_CHEST = "chest";
    public static final String SUB_IO_PORT = "io_port";
    public static final String SUB_CPU = "cpu";
    public static final String SUB_P2P_ME = "p2p_me";
    public static final String SUB_P2P_ITEM = "p2p_item";
    public static final String SUB_P2P_FLUID = "p2p_fluid";
    public static final String SUB_P2P_POWER = "p2p_power";
    public static final String SUB_P2P_LIGHT = "p2p_light";
    public static final String SUB_P2P_OTHER = "p2p_other";
    public static final String SUB_QUANTUM = "quantum";
    public static final String SUB_MISC = "misc";

    private static final String[] BRANCH_ORDER = { SUB_CONTROLLER, SUB_ENERGY_CELL, SUB_ENERGY_ACCEPTOR, SUB_DRIVE,
        SUB_CHEST, SUB_IO_PORT, SUB_TERMINAL_ME, SUB_TERMINAL_CRAFTING, SUB_TERMINAL_PATTERN_ENCODING,
        SUB_TERMINAL_PATTERN_ACCESS, SUB_TERMINAL_WIRELESS, SUB_WIRELESS_ACCESS_POINT, SUB_SECURITY_TERMINAL,
        SUB_TERMINAL_OTHER, SUB_BUS_IMPORT, SUB_BUS_EXPORT, SUB_BUS_STORAGE, SUB_BUS_ORE_FILTER, SUB_INTERFACE,
        SUB_PATTERN_PROVIDER, SUB_MONITOR_STORAGE, SUB_MONITOR_CONVERSION, SUB_EMITTER_LEVEL, SUB_EMITTER_ENERGY,
        SUB_CPU, SUB_P2P_ME, SUB_P2P_ITEM, SUB_P2P_FLUID, SUB_P2P_POWER, SUB_P2P_LIGHT, SUB_P2P_OTHER, SUB_QUANTUM,
        SUB_MISC };

    private TopologyRules() {}

    public static TopologyNodeType classify(String className) {
        return classify(className, null);
    }

    public static TopologyNodeType classify(String className, ItemStack representation) {
        return coarseTypeForSubtype(classifySubtype(className, representation));
    }

    /**
     * Returns a fine-grained subtype id used for grouping ({@code subtype|itemId}).
     */
    public static String classifySubtype(String className, ItemStack representation) {
        if (className == null || className.isEmpty()) {
            return SUB_MISC;
        }
        String simple = simpleName(className);
        if (isCableFacility(simple)) {
            return SUB_MISC;
        }

        if (containsAny(simple, "Controller")) {
            return SUB_CONTROLLER;
        }
        if (containsAny(simple, "EnergyCell")) {
            return SUB_ENERGY_CELL;
        }
        if (containsAny(simple, "EnergyAcceptor")) {
            return SUB_ENERGY_ACCEPTOR;
        }

        if (containsAny(simple, "WirelessCraftingTerminal", "WirelessTerminal")) {
            return SUB_TERMINAL_WIRELESS;
        }
        if (containsAny(simple, "PatternEncodingTerminal")) {
            return SUB_TERMINAL_PATTERN_ENCODING;
        }
        if (containsAny(simple, "PatternAccessTerminal")) {
            return SUB_TERMINAL_PATTERN_ACCESS;
        }
        if (containsAny(simple, "CraftingTerminal")) {
            return SUB_TERMINAL_CRAFTING;
        }
        if (containsAny(simple, "SecurityTerminal")) {
            return SUB_SECURITY_TERMINAL;
        }
        if (containsAny(simple, "WirelessAccessPoint", "WirelessAccess")) {
            return SUB_WIRELESS_ACCESS_POINT;
        }
        if (containsAny(simple, "Terminal")) {
            return SUB_TERMINAL_ME;
        }

        if (containsAny(simple, "ImportBus")) {
            return SUB_BUS_IMPORT;
        }
        if (containsAny(simple, "ExportBus")) {
            return SUB_BUS_EXPORT;
        }
        if (containsAny(simple, "StorageBus")) {
            return SUB_BUS_STORAGE;
        }
        if (containsAny(simple, "OreFilterBus", "OreDictExportBus")) {
            return SUB_BUS_ORE_FILTER;
        }
        if (containsAny(simple, "PartBus")) {
            return SUB_BUS_STORAGE;
        }

        if (containsAny(simple, "PatternProvider")) {
            return SUB_PATTERN_PROVIDER;
        }
        if (containsAny(simple, "Interface")) {
            return SUB_INTERFACE;
        }

        if (containsAny(simple, "StorageMonitor")) {
            return SUB_MONITOR_STORAGE;
        }
        if (containsAny(simple, "ConversionMonitor")) {
            return SUB_MONITOR_CONVERSION;
        }
        if (containsAny(simple, "LevelEmitter")) {
            return SUB_EMITTER_LEVEL;
        }
        if (containsAny(simple, "EnergyLevelEmitter", "EnergyEmitter")) {
            return SUB_EMITTER_ENERGY;
        }
        if (containsAny(simple, "PartMonitor")) {
            return SUB_MONITOR_STORAGE;
        }

        if (containsAny(simple, "IOPort")) {
            return SUB_IO_PORT;
        }
        if (containsAny(simple, "MEChest", "Chest") && !containsAny(simple, "Controller")) {
            return SUB_CHEST;
        }
        if (containsAny(simple, "Drive")) {
            return SUB_DRIVE;
        }

        if (containsAny(simple, "CraftingCPU", "CraftingMonitor", "CraftingUnit", "MolecularAssembler")) {
            return SUB_CPU;
        }

        if (containsAny(simple, "PartP2P", "P2P")) {
            return classifyP2pSubtype(simple, representation);
        }

        if (containsAny(simple, "Quantum", "QNB", "Singularity")) {
            return SUB_QUANTUM;
        }

        if (representation != null && representation.getItem() != null) {
            String reg = net.minecraft.item.Item.itemRegistry.getNameForObject(representation.getItem());
            if (reg != null) {
                String lower = reg.toLowerCase();
                if (lower.contains("import") && lower.contains("bus")) {
                    return SUB_BUS_IMPORT;
                }
                if (lower.contains("export") && lower.contains("bus")) {
                    return SUB_BUS_EXPORT;
                }
                if (lower.contains("storagebus") || (lower.contains("storage") && lower.contains("bus"))) {
                    return SUB_BUS_STORAGE;
                }
                if (lower.contains("terminal")) {
                    return SUB_TERMINAL_ME;
                }
                if (lower.contains("drive")) {
                    return SUB_DRIVE;
                }
                if (lower.contains("chest")) {
                    return SUB_CHEST;
                }
                if (lower.contains("interface")) {
                    return SUB_INTERFACE;
                }
                if (lower.contains("p2p")) {
                    return classifyP2pSubtype(reg, representation);
                }
            }
        }
        return SUB_MISC;
    }

    private static String classifyP2pSubtype(String hint, ItemStack representation) {
        String hay = hint == null ? "" : hint.toLowerCase();
        if (representation != null && representation.getItem() != null) {
            String reg = net.minecraft.item.Item.itemRegistry.getNameForObject(representation.getItem());
            if (reg != null) {
                hay = hay + " " + reg.toLowerCase();
            }
        }
        if (hay.contains("fluid")) {
            return SUB_P2P_FLUID;
        }
        if (hay.contains("item")) {
            return SUB_P2P_ITEM;
        }
        if (hay.contains("power") || hay.contains("energy")) {
            return SUB_P2P_POWER;
        }
        if (hay.contains("light")) {
            return SUB_P2P_LIGHT;
        }
        if (hay.contains("me") || hay.contains("tunnel")) {
            return SUB_P2P_ME;
        }
        return SUB_P2P_OTHER;
    }

    public static TopologyNodeType coarseTypeForSubtype(String subtype) {
        if (subtype == null || subtype.isEmpty()) {
            return TopologyNodeType.MISC;
        }
        if (SUB_CONTROLLER.equals(subtype)) {
            return TopologyNodeType.CONTROLLER;
        }
        if (SUB_ENERGY_CELL.equals(subtype) || SUB_ENERGY_ACCEPTOR.equals(subtype)) {
            return TopologyNodeType.ENERGY;
        }
        if (SUB_DRIVE.equals(subtype) || SUB_CHEST.equals(subtype) || SUB_IO_PORT.equals(subtype)) {
            return TopologyNodeType.DRIVE;
        }
        if (subtype.startsWith("terminal_") || SUB_WIRELESS_ACCESS_POINT.equals(subtype)
            || SUB_SECURITY_TERMINAL.equals(subtype)) {
            return TopologyNodeType.TERMINAL;
        }
        if (subtype.startsWith("bus_")) {
            return TopologyNodeType.BUS;
        }
        if (SUB_INTERFACE.equals(subtype) || SUB_PATTERN_PROVIDER.equals(subtype)) {
            return TopologyNodeType.INTERFACE;
        }
        if (subtype.startsWith("monitor_") || subtype.startsWith("emitter_")) {
            return TopologyNodeType.MONITOR;
        }
        if (SUB_CPU.equals(subtype)) {
            return TopologyNodeType.CPU;
        }
        if (subtype.startsWith("p2p_")) {
            return TopologyNodeType.P2P;
        }
        if (SUB_QUANTUM.equals(subtype)) {
            return TopologyNodeType.QUANTUM;
        }
        return TopologyNodeType.MISC;
    }

    public static int channelCostFor(TopologyNodeType type) {
        return channelCostForSubtype(null, type);
    }

    public static int channelCostForSubtype(String subtype, TopologyNodeType type) {
        if (subtype != null && !subtype.isEmpty()) {
            if (SUB_CONTROLLER.equals(subtype) || SUB_ENERGY_CELL.equals(subtype)
                || SUB_ENERGY_ACCEPTOR.equals(subtype)
                || SUB_DRIVE.equals(subtype)
                || SUB_CHEST.equals(subtype)
                || SUB_IO_PORT.equals(subtype)
                || SUB_CPU.equals(subtype)
                || SUB_QUANTUM.equals(subtype)) {
                return 0;
            }
            if (subtype.startsWith("terminal_") || SUB_WIRELESS_ACCESS_POINT.equals(subtype)
                || SUB_SECURITY_TERMINAL.equals(subtype)
                || subtype.startsWith("bus_")
                || SUB_INTERFACE.equals(subtype)
                || SUB_PATTERN_PROVIDER.equals(subtype)
                || subtype.startsWith("monitor_")
                || subtype.startsWith("emitter_")
                || subtype.startsWith("p2p_")) {
                return 1;
            }
        }
        if (type == null) {
            return 1;
        }
        switch (type) {
            case CONTROLLER:
            case ENERGY:
            case DRIVE:
            case CPU:
            case QUANTUM:
            case CELL:
            case CABLE_SMART:
            case CABLE_DENSE:
                return 0;
            case TERMINAL:
            case BUS:
            case MONITOR:
            case INTERFACE:
            case P2P:
            case MISC:
            case SPATIAL_BIN:
            default:
                return 1;
        }
    }

    public static boolean isTopTierSubtype(String subtype) {
        if (subtype == null) {
            return false;
        }
        return subtype.startsWith("terminal_") || SUB_WIRELESS_ACCESS_POINT.equals(subtype)
            || SUB_SECURITY_TERMINAL.equals(subtype);
    }

    public static boolean isZeroChannelSubtype(String subtype) {
        if (subtype == null) {
            return false;
        }
        return SUB_DRIVE.equals(subtype) || SUB_CHEST.equals(subtype)
            || SUB_IO_PORT.equals(subtype)
            || SUB_CPU.equals(subtype)
            || SUB_QUANTUM.equals(subtype);
    }

    public static boolean isEnergySubtype(String subtype) {
        return SUB_ENERGY_CELL.equals(subtype) || SUB_ENERGY_ACCEPTOR.equals(subtype);
    }

    public static String displayNameFor(TopologyNodeType type) {
        return displayNameForSubtype(null, type);
    }

    public static String displayNameForSubtype(String subtype, TopologyNodeType type) {
        if (subtype != null && !subtype.isEmpty()) {
            if (SUB_BUS_IMPORT.equals(subtype)) {
                return "Import Bus";
            }
            if (SUB_BUS_EXPORT.equals(subtype)) {
                return "Export Bus";
            }
            if (SUB_BUS_STORAGE.equals(subtype)) {
                return "Storage Bus";
            }
            if (SUB_BUS_ORE_FILTER.equals(subtype)) {
                return "Ore Filter Bus";
            }
            if (SUB_TERMINAL_ME.equals(subtype)) {
                return "ME Terminal";
            }
            if (SUB_TERMINAL_CRAFTING.equals(subtype)) {
                return "Crafting Terminal";
            }
            if (SUB_TERMINAL_PATTERN_ENCODING.equals(subtype)) {
                return "Pattern Encoding Terminal";
            }
            if (SUB_TERMINAL_PATTERN_ACCESS.equals(subtype)) {
                return "Pattern Access Terminal";
            }
            if (SUB_TERMINAL_WIRELESS.equals(subtype)) {
                return "Wireless Terminal";
            }
            if (SUB_WIRELESS_ACCESS_POINT.equals(subtype)) {
                return "Wireless Access Point";
            }
            if (SUB_SECURITY_TERMINAL.equals(subtype)) {
                return "Security Terminal";
            }
            if (SUB_ENERGY_CELL.equals(subtype)) {
                return "Energy Cell";
            }
            if (SUB_ENERGY_ACCEPTOR.equals(subtype)) {
                return "Energy Acceptor";
            }
            if (SUB_CHEST.equals(subtype)) {
                return "ME Chest";
            }
            if (SUB_IO_PORT.equals(subtype)) {
                return "ME IO Port";
            }
            if (SUB_DRIVE.equals(subtype)) {
                return "ME Drive";
            }
            if (SUB_PATTERN_PROVIDER.equals(subtype)) {
                return "Pattern Provider";
            }
            if (SUB_P2P_ME.equals(subtype)) {
                return "P2P ME Tunnel";
            }
            if (SUB_P2P_ITEM.equals(subtype)) {
                return "P2P Item Tunnel";
            }
            if (SUB_P2P_FLUID.equals(subtype)) {
                return "P2P Fluid Tunnel";
            }
            if (SUB_P2P_POWER.equals(subtype)) {
                return "P2P Power Tunnel";
            }
            if (SUB_P2P_LIGHT.equals(subtype)) {
                return "P2P Light Tunnel";
            }
        }
        if (type == null) {
            return "Misc";
        }
        switch (type) {
            case CONTROLLER:
                return "ME Controller";
            case ENERGY:
                return "Energy Cell / Acceptor";
            case DRIVE:
                return "Storage (Drive/Chest)";
            case TERMINAL:
                return "ME Terminal";
            case BUS:
                return "ME Bus";
            case MONITOR:
                return "ME Monitor / Emitter";
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
            case ENERGY:
                return "appeng:tile.BlockEnergyCell";
            case DRIVE:
                return "appeng:tile.BlockDrive";
            case TERMINAL:
            case BUS:
            case MONITOR:
                return "appeng:item.ItemMultiPart";
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
            case CABLE_DENSE:
                return "appeng:tile.BlockCableBus";
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

    public static boolean isCableFacility(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        String simple = simpleName(className);
        return containsAny(simple, "Cable", "GridBlock", "GridNode", "MultiblockNode");
    }

    public static boolean isCableFacility(TopologyNodeType type) {
        return type == TopologyNodeType.CABLE_SMART || type == TopologyNodeType.CABLE_DENSE;
    }

    public static int branchOrderIndex(String typeOrSubtypeId) {
        if (typeOrSubtypeId == null) {
            return BRANCH_ORDER.length;
        }
        for (int i = 0; i < BRANCH_ORDER.length; i++) {
            if (BRANCH_ORDER[i].equals(typeOrSubtypeId)) {
                return i;
            }
        }
        return BRANCH_ORDER.length;
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
