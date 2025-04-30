package com.imgood.advancedatamonitor;

import com.imgood.advancedatamonitor.loader.LoaderBlock;
import com.imgood.advancedatamonitor.loader.LoaerGui;
import com.imgood.advancedatamonitor.loader.LoaderHandler;
import com.imgood.advancedatamonitor.loader.LoaderItem;
import com.imgood.advancedatamonitor.loader.LoaderNetWork;
import com.imgood.advancedatamonitor.loader.LoaderRender;
import com.imgood.advancedatamonitor.loader.LoaderTileEntity;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(modid = AdvanceDataMonitor.MODID, version = Tags.VERSION, name = "AdvanceDataMonitor", acceptedMinecraftVersions = "[1.7.10]")
public class AdvanceDataMonitor {
    public static final String MODID = "advancedatamonitor";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static SimpleNetworkWrapper ADMCHANEL = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);;
    @Mod.Instance(MODID)
    public static AdvanceDataMonitor instance;

    @SidedProxy(clientSide = "com.imgood.advancedatamonitor.ClientProxy", serverSide = "com.imgood.advancedatamonitor.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        LoaderBlock.registerBlocks();
        LoaderItem.registerItems();
        LoaderHandler.registerHandlers();
        LoaderTileEntity.registerTileEntities();
        LoaderRender.registerRenderers();
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        LoaerGui.registerGui();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        LoaderNetWork.registerNetWorks();
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
