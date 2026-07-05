package com.imgood.textech.webae.gt;

import net.minecraft.world.World;

import com.imgood.textech.compat.gt.GtCompat;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.webae.dto.GtMachineDto;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public final class GtMachineStateReader {

    private GtMachineStateReader() {}

    public static GtMachineDto readState(World world, BlockPos pos, int dim) {
        if (world == null || pos == null) return null;

        Object te = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(te instanceof IGregTechTileEntity)) return null;

        IGregTechTileEntity gt = (IGregTechTileEntity) te;
        GtMachineDto dto = new GtMachineDto();
        dto.x = pos.getX();
        dto.y = pos.getY();
        dto.z = pos.getZ();
        dto.dim = dim;

        dto.isActive = GtCompat.isActive(gt);
        dto.errorId = GtCompat.getErrorDisplayID(gt);
        dto.problemId = GtCompat.getProblemDisplayID(gt);
        dto.progressTime = GtCompat.getProgressTime(gt);
        dto.maxProgressTime = GtCompat.getMaxProgressTime(gt);

        if (dto.maxProgressTime > 0) {
            dto.progressPercent = (double) dto.progressTime / dto.maxProgressTime * 100.0;
            if (dto.progressPercent > 100.0) dto.progressPercent = 100.0;
        }

        dto.storedEU = GtCompat.getStoredEU(gt);
        dto.euCapacity = GtCompat.getEUCapacity(gt);
        dto.inputVoltage = GtCompat.getInputVoltage(gt);
        dto.outputVoltage = GtCompat.getOutputVoltage(gt);
        dto.recipeMapName = GtCompat.getRecipeMapName(gt);
        dto.machineMode = GtCompat.getMachineMode(gt);
        dto.repairStatus = GtCompat.getRepairStatus(gt);
        dto.parallelCount = GtCompat.getParallelCount(gt);
        if (dto.parallelCount < 1) dto.parallelCount = 1;

        dto.currentOutput = GtCompat.getCurrentRecipeOutput(gt);
        dto.statusText = computeStatusText(dto);

        return dto;
    }

    private static String computeStatusText(GtMachineDto dto) {
        if (dto.errorId > 0) return "Error";
        if (dto.problemId > 0) return "Problem";
        if (dto.repairStatus > 0) return "Maintenance";
        if (dto.isActive) return "Running";
        return "Idle";
    }
}
