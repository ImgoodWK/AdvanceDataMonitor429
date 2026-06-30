package com.imgood.textech;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.imgood.textech.compat.ae.AeCompat;
import com.imgood.textech.items.cell.DataLoomCellHandler;
import com.imgood.textech.loader.LoaderBlock;
import com.imgood.textech.loader.LoaderEntity;
import com.imgood.textech.loader.LoaderGui;
import com.imgood.textech.loader.LoaderHandler;
import com.imgood.textech.loader.LoaderItem;
import com.imgood.textech.loader.LoaderNetwork;
import com.imgood.textech.loader.LoaderRecipe;
import com.imgood.textech.loader.LoaderRender;
import com.imgood.textech.loader.LoaderTileEntity;
import com.imgood.textech.loader.ModMigrationHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(
    modid = AdvanceDataMonitor.MODID,
    version = Tags.VERSION,
    name = "TeXTech",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech;required-after:structurelib")
public class AdvanceDataMonitor {

    public static final String MODID = "textech";
    /** Old mod ID used for archive migration via FMLMissingMappingsEvent. */
    public static final String LEGACY_MODID = "advancedatamonitor";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static SimpleNetworkWrapper ADMCHANEL = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
    @Mod.Instance(MODID)
    public static AdvanceDataMonitor instance;

    @SidedProxy(clientSide = "com.imgood.textech.ClientProxy", serverSide = "com.imgood.textech.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        LoaderBlock.registerBlocks();
        LoaderItem.registerItems();
        LoaderEntity.registerEntities();
        LoaderHandler.registerHandlers();
        LoaderTileEntity.registerTileEntities();
        LoaderRecipe.registerRecipes();
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            LoaderRender.registerRenderers();
        }
    }

    @Mod.EventHandler
    public void missingMappings(FMLMissingMappingsEvent event) {
        ModMigrationHandler.handle(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        LoaderGui.registerGui();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        LoaderNetwork.registerNetWorks();
        // AE2 Api/RegistryContainer requires AEConfig - must run after AE2 preInit (postInit phase).
        AeCompat.init();
        DataLoomCellHandler.register();
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
