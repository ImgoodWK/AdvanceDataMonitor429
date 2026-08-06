package com.imgood.textech.webae.icon;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Resolves WebAE pseudo tile icon ids ({@code mod:tile.BlockClass[:meta]}) to {@link ItemStack}s
 * suitable for {@link IconBlockRenderer} / {@link IconExportResolver} block-face export.
 */
@SideOnly(Side.CLIENT)
public final class IconTileResolver {

    private static final String TILE_TOKEN = "tile.";

    /** GTNH AE2 may register under {@code appeng} or {@code appliedenergistics2}. */
    private static final String[] AE_MOD_IDS = { "appeng", "appliedenergistics2" };

    private IconTileResolver() {}

    /**
     * @param candidate full id such as {@code appeng:tile.BlockCableBus:140}
     * @return stack task or null when not a tile id or block cannot be resolved
     */
    public static IconItemEnumerator.StackTask resolve(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        int modSep = candidate.indexOf(':');
        if (modSep <= 0) {
            return null;
        }
        String modId = candidate.substring(0, modSep);
        String tail = candidate.substring(modSep + 1);
        if (!tail.startsWith(TILE_TOKEN)) {
            return null;
        }

        String classAndMeta = tail.substring(TILE_TOKEN.length());
        int meta = 0;
        int metaSep = classAndMeta.lastIndexOf(':');
        if (metaSep > 0) {
            String suffix = classAndMeta.substring(metaSep + 1);
            if (suffix.matches("\\d+")) {
                meta = Integer.parseInt(suffix);
                classAndMeta = classAndMeta.substring(0, metaSep);
            }
        }

        if (classAndMeta.isEmpty()) {
            return null;
        }

        classAndMeta = normalizeClassName(classAndMeta);

        Block block = findBlock(modId, classAndMeta);
        if (block == null) {
            AdvanceDataMonitor.LOG.debug("[WebAE] Tile icon resolve: block not found for {}", candidate);
            return null;
        }

        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return null;
        }

        int blockMeta = meta;
        if ("BlockCableBus".equals(classAndMeta) && meta > 0) {
            // Cable color meta follows ItemMultiPart damage, not block damage — default block face.
            blockMeta = 0;
        }

        ItemStack stack = new ItemStack(item, 1, blockMeta);
        return new IconItemEnumerator.StackTask(candidate, stack.copy(), null);
    }

    private static Block findBlock(String modId, String className) {
        for (String candidateModId : modIdsForLookup(modId)) {
            Block block = findBlockInMod(candidateModId, className);
            if (block != null) {
                return block;
            }
        }
        return null;
    }

    private static String[] modIdsForLookup(String modId) {
        if ("appeng".equals(modId) || "appliedenergistics2".equals(modId)) {
            return AE_MOD_IDS;
        }
        return new String[] { modId };
    }

    private static Block findBlockInMod(String modId, String className) {
        for (String name : registryNamesFor(modId, className)) {
            Object obj = Block.blockRegistry.getObject(name);
            if (obj instanceof Block) {
                return (Block) obj;
            }
        }

        Block fromApi = resolveFromAeDefinitions(className);
        if (fromApi != null) {
            return fromApi;
        }

        return resolveBlockByReflection(modId, className);
    }

    private static String[] registryNamesFor(String modId, String className) {
        String decap = decapitalize(className);
        String snake = camelToSnake(className);
        return new String[] { modId + ":tile." + className, modId + ":tile." + decap, modId + ":tile." + snake,
            modId + ":" + className, modId + ":" + decap, modId + ":" + snake, modId + ":block" + className,
            modId + ":block" + decap, };
    }

    /** AE2 rv2/rv3: ApiBlocks static fields or AEApi definitions(). */
    private static Block resolveFromAeDefinitions(String className) {
        Block fromApiBlocks = resolveAeApiBlock(className);
        if (fromApiBlocks != null) {
            return fromApiBlocks;
        }
        return resolveAeApiBlockViaDefinitions(className);
    }

    private static Block resolveAeApiBlock(String className) {
        String[] apiClasses = { "appeng.core.api.definitions.ApiBlocks", "appeng.api.definitions.ApiBlocks",
            "appeng.core.ApiBlocks", };
        String[] fieldNames = aeFieldNamesFor(className);
        for (String apiClass : apiClasses) {
            try {
                Class<?> cls = Class.forName(apiClass);
                for (String fieldName : fieldNames) {
                    try {
                        java.lang.reflect.Field field = cls.getField(fieldName);
                        Object value = field.get(null);
                        Block block = unwrapAeBlock(value);
                        if (block != null) {
                            return block;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Block resolveAeApiBlockViaDefinitions(String className) {
        String[] apiClasses = { "appeng.api.AEApi", "appeng.core.api.AEApi" };
        String[] blockMethods = aeDefinitionMethodsFor(className);
        for (String apiClass : apiClasses) {
            try {
                Class<?> cls = Class.forName(apiClass);
                Object api = cls.getMethod("instance")
                    .invoke(null);
                Object definitions = api.getClass()
                    .getMethod("definitions")
                    .invoke(api);
                Object blocks = definitions.getClass()
                    .getMethod("blocks")
                    .invoke(definitions);
                for (String methodName : blockMethods) {
                    try {
                        Object def = blocks.getClass()
                            .getMethod(methodName)
                            .invoke(blocks);
                        Block block = unwrapAeBlock(def);
                        if (block != null) {
                            return block;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Legacy pseudo tile ids → GTNH AE2 block class names. */
    private static String normalizeClassName(String className) {
        if ("BlockCraftingUnit".equals(className)) {
            return "BlockCraftingStorage";
        }
        if ("BlockCraftingTerminal".equals(className)) {
            return "BlockCraftingMonitor";
        }
        return className;
    }

    private static String[] aeFieldNamesFor(String className) {
        if ("BlockCraftingStorage".equals(className)) {
            return new String[] { "craftingStorage", "blockCraftingStorage", "CraftingStorage" };
        }
        if ("BlockCraftingMonitor".equals(className)) {
            return new String[] { "craftingMonitor", "blockCraftingMonitor", "CraftingMonitor" };
        }
        if ("BlockCraftingAccelerator".equals(className)) {
            return new String[] { "craftingAccelerator", "blockCraftingAccelerator", "CraftingAccelerator" };
        }
        if ("BlockCraftingUnit".equals(className)) {
            return new String[] { "craftingStorage", "blockCraftingStorage", "CraftingStorage" };
        }
        if ("BlockController".equals(className)) {
            return new String[] { "controller", "blockController", "Controller" };
        }
        if ("BlockDrive".equals(className)) {
            return new String[] { "drive", "blockDrive", "Drive" };
        }
        if ("BlockInterface".equals(className)) {
            return new String[] { "iface", "blockInterface", "Interface" };
        }
        if ("BlockEnergyCell".equals(className)) {
            return new String[] { "energyCell", "blockEnergyCell", "EnergyCell" };
        }
        if ("BlockQuantumLinkChamber".equals(className)) {
            return new String[] { "quantumLink", "quantumRing", "blockQuantumLinkChamber", "QuantumLinkChamber" };
        }
        if ("BlockCableBus".equals(className)) {
            return new String[] { "cableBus", "blockCableBus", "CableBus" };
        }
        String decap = decapitalize(className);
        if (decap.startsWith("block")) {
            decap = decap.substring(5);
        }
        return new String[] { decap, "block" + className, className };
    }

    private static String[] aeDefinitionMethodsFor(String className) {
        String[] fields = aeFieldNamesFor(className);
        String[] methods = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            methods[i] = fields[i];
        }
        return methods;
    }

    private static Block unwrapAeBlock(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Block) {
            return (Block) value;
        }
        try {
            Object block = value.getClass()
                .getMethod("block")
                .invoke(value);
            if (block instanceof Block) {
                return (Block) block;
            }
        } catch (Throwable ignored) {}
        try {
            Object maybe = value.getClass()
                .getMethod("maybeBlock")
                .invoke(value);
            if (maybe instanceof Block) {
                return (Block) maybe;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Block resolveBlockByReflection(String modId, String className) {
        String[] packages = { "block.crafting", "block", "core.block", "blocks" };
        for (String candidateModId : modIdsForLookup(modId)) {
            for (String pkg : packages) {
                Block block = resolveBlockClass(candidateModId + "." + pkg + "." + className);
                if (block != null) {
                    return block;
                }
            }
        }
        return null;
    }

    private static Block resolveBlockClass(String className) {
        try {
            Class<?> cls = Class.forName(className);
            try {
                Object instance = cls.getField("instance")
                    .get(null);
                if (instance instanceof Block) {
                    return (Block) instance;
                }
            } catch (Throwable ignored) {}
            java.lang.reflect.Field[] fields = cls.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!Block.class.isAssignableFrom(field.getType())) continue;
                Object instance = field.get(null);
                if (instance instanceof Block) {
                    return (Block) instance;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name.toLowerCase();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }
}
