package com.imgood.textech.loader;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.network.handler.HandlerLinkScannerAction;
import com.imgood.textech.network.handler.HandlerNetwork;
import com.imgood.textech.network.handler.HandlerPlannerMerge;
import com.imgood.textech.network.handler.HandlerPlannerSync;
import com.imgood.textech.network.handler.HandlerSynTileEntity;
import com.imgood.textech.network.packet.PacketAssistantAction;
import com.imgood.textech.network.packet.PacketAssistantMenuStateQuery;
import com.imgood.textech.network.packet.PacketAssistantMenuStateResponse;
import com.imgood.textech.network.packet.PacketAssistantResponse;
import com.imgood.textech.network.packet.PacketGrappleAction;
import com.imgood.textech.network.packet.PacketGrappleAnchorConfig;
import com.imgood.textech.network.packet.PacketGrappleHookConfig;
import com.imgood.textech.network.packet.PacketGrapplePathAction;
import com.imgood.textech.network.packet.PacketGrapplePathSync;
import com.imgood.textech.network.packet.PacketGrappleSync;
import com.imgood.textech.network.packet.PacketItemCountSync;
import com.imgood.textech.network.packet.PacketItemNBT;
import com.imgood.textech.network.packet.PacketLinkScannerAction;
import com.imgood.textech.network.packet.PacketMatterBallDecompressorToggle;
import com.imgood.textech.network.packet.PacketMonitorRecord;
import com.imgood.textech.network.packet.PacketPlannerMerge;
import com.imgood.textech.network.packet.PacketPlannerSync;
import com.imgood.textech.network.packet.PacketPocketAction;
import com.imgood.textech.network.packet.PacketPocketSync;
import com.imgood.textech.network.packet.PacketRequestItemCountSync;
import com.imgood.textech.network.packet.PacketSuperOrangeConfig;
import com.imgood.textech.network.packet.PacketSynTileEntity;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class LoaderNetwork {

    public static void registerNetWorks() {
        AdvanceDataMonitor.ADMCHANEL.registerMessage(HandlerNetwork.class, PacketItemNBT.class, 0, Side.SERVER);

        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketSynTileEntity.ClientHandler.class, PacketSynTileEntity.class, 1, Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(HandlerSynTileEntity.class, PacketSynTileEntity.class, 2, Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketRequestItemCountSync.Handler.class,
            PacketRequestItemCountSync.class,
            4,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketItemCountSync.Handler.class, PacketItemCountSync.class, 5, Side.CLIENT);
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
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketGrappleSync.ClientHandler.class, PacketGrappleSync.class, 12, Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketGrappleHookConfig.Handler.class, PacketGrappleHookConfig.class, 13, Side.SERVER);

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

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketMonitorRecord.Handler.class, PacketMonitorRecord.class, 15, Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(HandlerLinkScannerAction.class, PacketLinkScannerAction.class, 16, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                HandlerLinkScannerAction.ClientHandler.class,
                PacketLinkScannerAction.class,
                16,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketGrapplePathAction.ServerHandler.class,
            PacketGrapplePathAction.class,
            17,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketGrapplePathSync.ClientHandler.class,
                PacketGrapplePathSync.class,
                18,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketPocketAction.ServerHandler.class, PacketPocketAction.class, 19, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketPocketAction.ClientHandler.class, PacketPocketAction.class, 20, Side.CLIENT);
        }
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketPocketSync.ClientHandler.class, PacketPocketSync.class, 21, Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketSuperOrangeConfig.Handler.class, PacketSuperOrangeConfig.class, 22, Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketMatterBallDecompressorToggle.Handler.class,
            PacketMatterBallDecompressorToggle.class,
            23,
            Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketAssistantMenuStateQuery.Handler.class,
            PacketAssistantMenuStateQuery.class,
            24,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketAssistantMenuStateResponse.Handler.class,
                PacketAssistantMenuStateResponse.class,
                25,
                Side.CLIENT);
        }
    }
}
