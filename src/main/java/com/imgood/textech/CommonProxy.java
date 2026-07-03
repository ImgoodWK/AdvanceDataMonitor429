package com.imgood.textech;

import java.io.File;

import com.imgood.textech.command.CommandAssistant;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        File configDir = new File(event.getModConfigurationDirectory(), AdvanceDataMonitor.MODID);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File configFile = new File(configDir, "textech.cfg");
        migrateLegacyMainConfig(event.getModConfigurationDirectory(), configFile);
        Config.synchronizeConfiguration(configFile);

        AdvanceDataMonitor.LOG.info("TeXTech v" + Tags.VERSION + " initialized");
    }

    private static void migrateLegacyMainConfig(File forgeConfigRoot, File targetFile) {
        if (targetFile.exists()) {
            return;
        }
        File legacyDir = new File(forgeConfigRoot, AdvanceDataMonitor.LEGACY_MODID);
        File legacyFile = new File(legacyDir, AdvanceDataMonitor.LEGACY_MODID + ".cfg");
        if (!legacyFile.exists()) {
            return;
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream(legacyFile);
            java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            AdvanceDataMonitor.LOG.info(
                "[TeXTech] Migrated main config: {} -> {}",
                legacyFile.getAbsolutePath(),
                targetFile.getAbsolutePath());
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn(
                "[TeXTech] Failed to migrate main config from {}",
                legacyFile.getAbsolutePath(),
                e);
        }
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandAssistant());
    }
}
