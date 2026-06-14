package com.imgood.advancedatamonitor.utils;

import net.minecraft.world.World;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 14:58
 **/
public class BlockPos {

    private World world;
    private int x;
    private int y;
    private int z;
    private String XYZ;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public BlockPos(String XYZ, World world) {
        String[] xyz = XYZ.split(",");
        try {
            x = Integer.parseInt(xyz[0]);
            y = Integer.parseInt(xyz[1]);
            z = Integer.parseInt(xyz[2]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.world = world;
    }

    public BlockPos(String XYZ) {
        String[] xyz = XYZ.split(",");
        try {
            x = Integer.parseInt(xyz[0]);
            y = Integer.parseInt(xyz[1]);
            z = Integer.parseInt(xyz[2]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BlockPos(int x, int y, int z, World world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlockPos blockPos = (BlockPos) o;
        return x == blockPos.x && y == blockPos.y && z == blockPos.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "BlockPos{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }

    public String getXYZ() {
        return x + "," + y + "," + z;
    }

    public void setXYZ(String XYZ) {
        String[] xyz = XYZ.split(",");
        try {
            x = Integer.parseInt(xyz[0]);
            y = Integer.parseInt(xyz[1]);
            z = Integer.parseInt(xyz[2]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World World) {
        this.world = World;
    }
}
