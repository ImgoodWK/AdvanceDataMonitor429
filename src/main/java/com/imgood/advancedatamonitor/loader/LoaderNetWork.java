package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.handler.HandlerLinkScannerAction;
import com.imgood.advancedatamonitor.network.handler.HandlerNetwork;
import com.imgood.advancedatamonitor.network.handler.HandlerPlannerMerge;
import com.imgood.advancedatamonitor.network.handler.HandlerPlannerSync;
import com.imgood.advancedatamonitor.network.handler.HandlerSynTileEntity;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantAction;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantResponse;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleAction;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleAnchorConfig;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleHookConfig;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleSync;
import com.imgood.advancedatamonitor.network.packet.PacketItemCountSync;
import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;
import com.imgood.advancedatamonitor.network.packet.PacketLinkScannerAction;
import com.imgood.advancedatamonitor.network.packet.PacketMonitorRecord;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerMerge;
import com.imgood.advancedatamonitor.network.packet.PacketPlannerSync;
import com.imgood.advancedatamonitor.network.packet.PacketRequestItemCountSync;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class LoaderNetwork {

    public static void registerNetWorks() {
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerNetwork.class,
            PacketItemNBT.class,
            0,
            Side.SERVER);

        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketSynTileEntity.ClientHandler.class,
                PacketSynTileEntity.class,
                1,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerSynTileEntity.class,
            PacketSynTileEntity.class,
            2,
            Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketRequestItemCountSync.Handler.class,
            PacketRequestItemCountSync.class,
            4,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketItemCountSync.Handler.class,
                PacketItemCountSync.class,
                5,
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

        AdvanceDataMonitor.ADMCHANEL.registerMessage(HandlerPlannerSync.class, PacketPlannerSync.class, 8, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(HandlerPlannerSync.ClientHandler.class, PacketPlannerSync.class, 9, Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(HandlerPlannerMerge.class, PacketPlannerMerge.class, 10, Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketGrappleAction.Handler.class, PacketGrappleAction.class, 11, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketGrappleSync.ClientHandler.class,
                PacketGrappleSync.class,
                12,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketGrappleHookConfig.Handler.class,
            PacketGrappleHookConfig.class,
            13,
            Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketGrappleAnchorConfig.ServerHandler.class,
            PacketGrappleAnchorConfig.class,
            14,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketGrappleAnchorConfig.ClientHandler.class,
                PacketGrappleAnchorConfig.class,
                14,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketMonitorRecord.Handler.class,
            PacketMonitorRecord.class,
            15,
            Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            HandlerLinkScannerAction.class,
            PacketLinkScannerAction.class,
            16,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                HandlerLinkScannerAction.ClientHandler.class,
                PacketLinkScannerAction.class,
                16,
                Side.CLIENT);
        }
    }
}
