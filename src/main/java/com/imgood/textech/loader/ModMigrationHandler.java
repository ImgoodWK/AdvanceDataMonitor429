package com.imgood.textech.loader;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Handles archive migration when mod ID changes from "advancedatamonitor" to "textech",
 * and remaps any lingering missing storage/crafting link entries to the unified network link.
 * Invoked from {@link AdvanceDataMonitor#missingMappings(FMLMissingMappingsEvent)}.
 *
 * <p>
 * Note: {@code advStorageLink} / {@code advCraftingLink} are also still registered as
 * compatibility alias blocks in {@link LoaderBlock} so normal worlds never miss them.
 * This handler remains as a safety net (e.g. intermediate broken builds, modid rename).
 */
public class ModMigrationHandler {

    private static final String LEGACY_MODID = "advancedatamonitor";
    private static final String UNIFIED_LINK_BLOCK = "advNetworkLinkBlock";

    private ModMigrationHandler() {}

    public static void handle(FMLMissingMappingsEvent event) {
        Block unifiedLink = GameRegistry.findBlock(AdvanceDataMonitor.MODID, UNIFIED_LINK_BLOCK);
        if (unifiedLink == null && LoaderBlock.advanceNetworkLinkBlock != null) {
            unifiedLink = LoaderBlock.advanceNetworkLinkBlock;
        }
        Item unifiedLinkItem = unifiedLink != null ? Item.getItemFromBlock(unifiedLink) : null;

        // Prefer get() (this mod); also scan getAll() for our modids in case of rename edge cases.
        for (MissingMapping mapping : event.get()) {
            handleOne(mapping, unifiedLink, unifiedLinkItem);
        }
        for (MissingMapping mapping : event.getAll()) {
            if (mapping.name == null) {
                continue;
            }
            if (!mapping.name.startsWith(LEGACY_MODID + ":")
                && !mapping.name.startsWith(AdvanceDataMonitor.MODID + ":")) {
                continue;
            }
            // Already handled via get() — remap() twice is unsafe; skip if action already set.
            // MissingMapping has no public getter for action in all versions; re-attempt is OK
            // only when still DEFAULT. Use try/catch around remap.
            handleOne(mapping, unifiedLink, unifiedLinkItem);
        }
    }

    private static void handleOne(MissingMapping mapping, Block unifiedLink, Item unifiedLinkItem) {
        String name = mapping.name;
        if (name == null) {
            return;
        }

        String objectName = null;
        if (name.startsWith(LEGACY_MODID + ":")) {
            objectName = name.substring((LEGACY_MODID + ":").length());
        } else if (name.startsWith(AdvanceDataMonitor.MODID + ":")) {
            objectName = name.substring((AdvanceDataMonitor.MODID + ":").length());
        } else {
            return;
        }

        try {
            if (isMergedLinkName(objectName)) {
                if (mapping.type == GameRegistry.Type.BLOCK && unifiedLink != null) {
                    mapping.remap(unifiedLink);
                    logRemap(name, UNIFIED_LINK_BLOCK);
                    return;
                }
                if (mapping.type == GameRegistry.Type.ITEM && unifiedLinkItem != null) {
                    mapping.remap(unifiedLinkItem);
                    logRemap(name, UNIFIED_LINK_BLOCK);
                    return;
                }
            }

            // Same-name remap for other legacy modid objects (advancedatamonitor → textech)
            if (name.startsWith(LEGACY_MODID + ":")) {
                if (mapping.type == GameRegistry.Type.BLOCK) {
                    Block newBlock = GameRegistry.findBlock(AdvanceDataMonitor.MODID, objectName);
                    if (newBlock != null) {
                        mapping.remap(newBlock);
                        logRemap(name, objectName);
                        return;
                    }
                } else if (mapping.type == GameRegistry.Type.ITEM) {
                    Item newItem = GameRegistry.findItem(AdvanceDataMonitor.MODID, objectName);
                    if (newItem == null) {
                        Block asBlock = GameRegistry.findBlock(AdvanceDataMonitor.MODID, objectName);
                        if (asBlock != null) {
                            newItem = Item.getItemFromBlock(asBlock);
                        }
                    }
                    if (newItem != null) {
                        mapping.remap(newItem);
                        logRemap(name, objectName);
                        return;
                    }
                }
            }
        } catch (RuntimeException ex) {
            // Already remapped / incompatible — do not fail the world load.
            AdvanceDataMonitor.LOG.warn(
                "[TeXTech] Mapping already handled or remap skipped for {}: {}",
                mapping.name,
                ex.getMessage());
            return;
        }

        AdvanceDataMonitor.LOG.warn("[TeXTech] Unhandled missing mapping (ignored to protect save): {}", mapping.name);
        try {
            mapping.ignore();
        } catch (RuntimeException ignored) {
            // ignore() may throw if already handled
        }
    }

    private static boolean isMergedLinkName(String objectName) {
        return "advStorageLink".equals(objectName) || "advCraftingLink".equals(objectName);
    }

    private static void logRemap(String from, String to) {
        AdvanceDataMonitor.LOG.info("[TeXTech] Migrated mapping: {} -> {}:{}", from, AdvanceDataMonitor.MODID, to);
    }
}
