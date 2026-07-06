package com.imgood.textech.webae.snapshot;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.webae.dto.GtMachineDto;
import com.imgood.textech.webae.dto.GtMachineListDto;
import com.imgood.textech.webae.gt.GtMachineBinding;
import com.imgood.textech.webae.gt.GtMachineStateReader;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class GtSnapshotCollector {

    private GtSnapshotCollector() {}

    public static GtMachineListDto collect(String ownerUuid, int networkId, TileEntityAdvanceDataMonitor monitor) {
        GtMachineListDto dto = new GtMachineListDto();
        dto.networkId = networkId;
        dto.timestamp = System.currentTimeMillis();

        if (monitor == null) {
            return dto;
        }

        World world = monitor.getWorldObj();
        if (world == null) {
            return dto;
        }
        int currentDim = world.provider.dimensionId;

        List<GtMachineBinding.BoundMachine> bindings = GtMachineBinding.getBoundMachines(monitor);
        if (bindings.isEmpty()) {
            return dto;
        }

        for (GtMachineBinding.BoundMachine bm : bindings) {
            if (bm.dim != currentDim) {
                continue;
            }
            BlockPos pos = new BlockPos(bm.x, bm.y, bm.z);
            if (!world.blockExists(bm.x, bm.y, bm.z)) {
                continue;
            }

            TileEntity te = world.getTileEntity(bm.x, bm.y, bm.z);
            if (!(te instanceof IGregTechTileEntity)) {
                continue;
            }

            GtMachineDto machine = GtMachineStateReader.readState(world, pos, bm.dim);
            if (machine != null) {
                dto.machines.add(machine);
            }
        }

        return dto;
    }

    /**
     * Blocking collect for HTTP handlers. Enqueues on server thread and waits up to timeoutMs.
     */
    public static GtMachineListDto collectBlocking(final String ownerUuid, final int networkId,
        final TileEntityAdvanceDataMonitor monitor, long timeoutMs) {
        final GtMachineListDto[] holder = new GtMachineListDto[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    holder[0] = collect(ownerUuid, networkId, monitor);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] GT snapshot collection failed", t);
                    holder[0] = null;
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return holder[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        AdvanceDataMonitor.LOG
            .warn("[WebAE] GT snapshot collection timed out owner={} network={}", ownerUuid, networkId);
        return null;
    }
}
