package com.imgood.advancedatamonitor.loader;

import com.imgood.advancedatamonitor.network.handler.NetWorkHandler;
import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;

import cpw.mods.fml.relauncher.Side;
import com.imgood.advancedatamonitor.AdvanceDataMonitor;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 10:52
 **/
public class NetWorkLoader {


    public static void registerNetWorks()
    {
        AdvanceDataMonitor.ADMCHANEL.registerMessage(
            NetWorkHandler.class,
            PacketItemNBT.class,
            0,
            Side.SERVER
        );
    }
}
