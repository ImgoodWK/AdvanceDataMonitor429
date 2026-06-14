package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.handler.HandlerNetWork;
import com.imgood.advancedatamonitor.network.handler.HandlerPlannerMerge;
import com.imgood.advancedatamonitor.network.handler.HandlerPlannerSync;
import com.imgood.advancedatamonitor.network.handler.HandlerSynTileEntity;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantAction;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantResponse;
import com.imgood.advancedatamonitor.network.packet.PacketItemCountSync;
import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerMerge;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerSync;
import com.imgood.advancedatamonitor.network.packet.PacketRequestItemCountSync;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class LoaderNetWork {

    public static void registerNetWorks() {
        // 使用固定ID，确保顺序一致
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerNetWork.class,
            PacketItemNBT.class,
            0, // 固定ID 0
            Side.SERVER);

        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
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

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerSynTileEntity.class,
            PacketSynTileEntity.class,
            3, // 固定ID 3
            Side.SERVER);

        // 注册新的同步包
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketRequestItemCountSync.Handler.class,
            PacketRequestItemCountSync.class,
            4, // 固定ID 4
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketItemCountSync.Handler.class,
                PacketItemCountSync.class,
                5, // 固定ID 5
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketAssistantAction.Handler.class, PacketAssistantAction.class, 6, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketAssistantResponse.Handler.class, PacketAssistantResponse.class, 7, Side.CLIENT);
        }

        // Planner sync: client → server
        AdvanceDataMonitor.ADMCHANEL.registerMessage(HandlerPlannerSync.class, PacketPlannerSync.class, 8, Side.SERVER);
        // Planner sync: server → client (for merge result)
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(HandlerPlannerSync.ClientHandler.class, PacketPlannerSync.class, 9, Side.CLIENT);
        }

        // Planner merge: client → server
        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(HandlerPlannerMerge.class, PacketPlannerMerge.class, 10, Side.SERVER);
    }
}
