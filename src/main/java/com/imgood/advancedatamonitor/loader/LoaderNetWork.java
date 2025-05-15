package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.handler.HandlerNetWork;
import com.imgood.advancedatamonitor.network.handler.HandlerSynTileEntity;
import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 10:52
 **/
public class LoaderNetWork {

    public static void registerNetWorks() {
        // 使用固定ID，确保顺序一致
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerNetWork.class,
            PacketItemNBT.class,
            0, // 固定ID 0
            Side.SERVER);
        if(FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                    PacketSynTileEntity.ClientHandler.class,
                    PacketSynTileEntity.class,
                    1, // 固定ID 1
                    Side.CLIENT);
        }
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketSynTileEntity.ServerHandler.class,
            PacketSynTileEntity.class,
            2, // 固定ID 2
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(HandlerSynTileEntity.class, PacketSynTileEntity.class, 3, Side.SERVER);
    }
}
