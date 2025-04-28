package com.imgood.advancedatamonitor;

import com.imgood.advancedatamonitor.loader.BlockLoader;
import com.imgood.advancedatamonitor.loader.GuiLoaer;
import com.imgood.advancedatamonitor.loader.HandlerLoader;
import com.imgood.advancedatamonitor.loader.ItemLoader;
import com.imgood.advancedatamonitor.loader.NetWorkLoader;
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
    public static SimpleNetworkWrapper ADMCHANEL = ADMCHANEL = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);;
    @Mod.Instance(MODID)
    public static AdvanceDataMonitor instance;

    @SidedProxy(clientSide = "com.imgood.advancedatamonitor.ClientProxy", serverSide = "com.imgood.advancedatamonitor.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        BlockLoader.registerBlocks();
        ItemLoader.registerItems();
        HandlerLoader.registerHandlers();
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        GuiLoaer.registerGui();
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        NetWorkLoader.registerNetWorks();
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
