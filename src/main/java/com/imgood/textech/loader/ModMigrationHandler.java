package com.imgood.textech.loader;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.imgood.textech.AdvanceDataMonitor;

import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Handles archive migration when mod ID changes from "advancedatamonitor" to "textech".
 * Invoked from {@link AdvanceDataMonitor#missingMappings(FMLMissingMappingsEvent)}.
 */
public class ModMigrationHandler {

    private static final String LEGACY_MODID = "advancedatamonitor";

    private ModMigrationHandler() {}

    public static void handle(FMLMissingMappingsEvent event) {
        for (MissingMapping mapping : event.get()) {
            if (!mapping.name.startsWith(LEGACY_MODID + ":")) {
                continue;
            }

            String objectName = mapping.name.substring((LEGACY_MODID + ":").length());

            if (mapping.type == GameRegistry.Type.BLOCK) {
                Block newBlock = GameRegistry.findBlock(AdvanceDataMonitor.MODID, objectName);
                if (newBlock != null) {
                    mapping.remap(newBlock);
                    AdvanceDataMonitor.LOG.info(
                        "[TeXTech] Migrated block: {} -> {}:{}",
                        mapping.name,
                        AdvanceDataMonitor.MODID,
                        objectName);
                    continue;
                }
            } else if (mapping.type == GameRegistry.Type.ITEM) {
                Item newItem = GameRegistry.findItem(AdvanceDataMonitor.MODID, objectName);
                if (newItem != null) {
                    mapping.remap(newItem);
                    AdvanceDataMonitor.LOG.info(
                        "[TeXTech] Migrated item: {} -> {}:{}",
                        mapping.name,
                        AdvanceDataMonitor.MODID,
                        objectName);
                    continue;
                }
            }

            AdvanceDataMonitor.LOG.warn("[TeXTech] Failed to migrate mapping: {}", mapping.name);
        }
    }
}
