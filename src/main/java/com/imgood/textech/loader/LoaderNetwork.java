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
import com.imgood.textech.network.packet.PacketMonitorWebSurface;
import com.imgood.textech.network.packet.PacketPlannerMerge;
import com.imgood.textech.network.packet.PacketPlannerSync;
import com.imgood.textech.network.packet.PacketPocketAction;
import com.imgood.textech.network.packet.PacketPocketSync;
import com.imgood.textech.network.packet.PacketRequestItemCountSync;
import com.imgood.textech.network.packet.PacketSuperOrangeConfig;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.webae.network.PacketIconDirectCaptureRequest;
import com.imgood.textech.webae.network.PacketIconDirectCaptureResponse;
import com.imgood.textech.webae.network.PacketScreenshotUpload;
import com.imgood.textech.webae.network.PacketScreenshotUploadAck;
import com.imgood.textech.webae.network.PacketWebAlertNotify;
import com.imgood.textech.webae.network.PacketWebConsoleTokenNotify;
import com.imgood.textech.webae.network.PacketWebIconExportScope;
import com.imgood.textech.webae.network.PacketWebIconPullZip;
import com.imgood.textech.webae.network.PacketWebIconRequest;
import com.imgood.textech.webae.network.PacketWebIconResolveNack;
import com.imgood.textech.webae.network.PacketWebIconUpload;
import com.imgood.textech.webae.network.PacketWebIconUploadAck;
import com.imgood.textech.webae.network.PacketWebMapTileJob;
import com.imgood.textech.webae.network.PacketWebMapTileUpload;
import com.imgood.textech.webae.network.PacketWebRecipeUpload;
import com.imgood.textech.webae.network.PacketWebRecipeUploadAck;
import com.imgood.textech.webae.network.PacketWebUploadTrigger;
import com.imgood.textech.webae.network.PacketWorldMapCaptureAccept;
import com.imgood.textech.webae.network.PacketWorldMapCaptureJob;
import com.imgood.textech.webae.network.PacketWorldMapCaptureOffer;
import com.imgood.textech.webae.network.PacketWorldMapDirectCaptureRequest;
import com.imgood.textech.webae.network.PacketWorldMapDirectCaptureResponse;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotSyncRequest;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotSyncResponse;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTileData;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTilePull;
import com.imgood.textech.webae.network.PacketWorldMapSnapshotTileUpload;

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

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketWebRecipeUpload.Handler.class, PacketWebRecipeUpload.class, 26, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWebRecipeUploadAck.Handler.class,
                PacketWebRecipeUploadAck.class,
                27,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketWebIconUpload.Handler.class, PacketWebIconUpload.class, 28, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebIconUploadAck.Handler.class, PacketWebIconUploadAck.class, 29, Side.CLIENT);
        }

        // S→C: server tells client to begin a recipes/icons upload (OP command trigger)
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebUploadTrigger.Handler.class, PacketWebUploadTrigger.class, 30, Side.CLIENT);
        }

        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWebConsoleTokenNotify.Handler.class,
                PacketWebConsoleTokenNotify.class,
                31,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWebIconExportScope.Handler.class,
                PacketWebIconExportScope.class,
                32,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebIconRequest.Handler.class, PacketWebIconRequest.class, 33, Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketWebIconResolveNack.Handler.class, PacketWebIconResolveNack.class, 36, Side.SERVER);

        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketWebMapTileUpload.Handler.class, PacketWebMapTileUpload.class, 35, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebMapTileJob.Handler.class, PacketWebMapTileJob.class, 34, Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWorldMapCaptureOffer.Handler.class,
                PacketWorldMapCaptureOffer.class,
                37,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWorldMapCaptureJob.Handler.class,
                PacketWorldMapCaptureJob.class,
                39,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWorldMapSnapshotSyncResponse.Handler.class,
                PacketWorldMapSnapshotSyncResponse.class,
                42,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWorldMapSnapshotTileData.Handler.class,
                PacketWorldMapSnapshotTileData.class,
                44,
                Side.CLIENT);
        }

        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketWorldMapCaptureAccept.Handler.class,
            PacketWorldMapCaptureAccept.class,
            38,
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketWorldMapSnapshotTileUpload.Handler.class,
            PacketWorldMapSnapshotTileUpload.class,
            40,
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketWorldMapSnapshotSyncRequest.Handler.class,
            PacketWorldMapSnapshotSyncRequest.class,
            41,
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketWorldMapSnapshotTilePull.Handler.class,
            PacketWorldMapSnapshotTilePull.class,
            43,
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketWorldMapDirectCaptureResponse.Handler.class,
            PacketWorldMapDirectCaptureResponse.class,
            46,
            Side.SERVER);
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketIconDirectCaptureResponse.Handler.class,
            PacketIconDirectCaptureResponse.class,
            48,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketWorldMapDirectCaptureRequest.Handler.class,
                PacketWorldMapDirectCaptureRequest.class,
                45,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketIconDirectCaptureRequest.Handler.class,
                PacketIconDirectCaptureRequest.class,
                47,
                Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebIconPullZip.Handler.class, PacketWebIconPullZip.class, 49, Side.CLIENT);
            AdvanceDataMonitor.ADMCHANEL
                .registerMessage(PacketWebAlertNotify.Handler.class, PacketWebAlertNotify.class, 50, Side.CLIENT);
        }
        AdvanceDataMonitor.ADMCHANEL
            .registerMessage(PacketScreenshotUpload.Handler.class, PacketScreenshotUpload.class, 51, Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketScreenshotUploadAck.Handler.class,
                PacketScreenshotUploadAck.class,
                52,
                Side.CLIENT);
        }
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            PacketMonitorWebSurface.ServerHandler.class,
            PacketMonitorWebSurface.class,
            53,
            Side.SERVER);
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) {
            AdvanceDataMonitor.ADMCHANEL.registerMessage(
                PacketMonitorWebSurface.ClientHandler.class,
                PacketMonitorWebSurface.class,
                53,
                Side.CLIENT);
        }
    }
}
