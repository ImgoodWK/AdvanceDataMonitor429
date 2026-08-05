package com.imgood.textech.blocks;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Assert;
import org.junit.Test;

import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

import sun.misc.Unsafe;

public class BlockAdvanceDataMonitorPowerTest {

    @Test
    public void exposesSameCachedPowerOnEverySide() {
        BlockAdvanceDataMonitor block = new BlockAdvanceDataMonitor();
        TileEntityAdvanceDataMonitor monitor = allocateWithoutConstructor(FixedPowerMonitor.class);
        IBlockAccess access = new FixedTileAccess(monitor);

        Assert.assertTrue(block.canProvidePower());
        Assert.assertTrue(block.hasComparatorInputOverride());
        for (int side = 0; side < 6; side++) {
            Assert.assertEquals(7, block.isProvidingWeakPower(access, 1, 2, 3, side));
            Assert.assertEquals(15, block.isProvidingStrongPower(access, 1, 2, 3, side));
        }
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class FixedPowerMonitor extends TileEntityAdvanceDataMonitor {

        private FixedPowerMonitor() {
            throw new AssertionError("constructor must not run in the plain JUnit environment");
        }

        @Override
        public int getWeakPowerOutput() {
            return 7;
        }

        @Override
        public int getStrongPowerOutput() {
            return 15;
        }
    }

    private static final class FixedTileAccess implements IBlockAccess {

        private final TileEntity tileEntity;

        private FixedTileAccess(TileEntity tileEntity) {
            this.tileEntity = tileEntity;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return null;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return tileEntity;
        }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimumBlockLight) {
            return 0;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
            return 0;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return false;
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return null;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            return false;
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean defaultValue) {
            return defaultValue;
        }
    }
}
