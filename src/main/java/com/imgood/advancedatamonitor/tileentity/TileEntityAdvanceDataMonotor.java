// TileEntityAdvanceDataMonotor.java
package com.imgood.advancedatamonitor.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import java.util.ArrayList;
import java.util.List;

public class TileEntityAdvanceDataMonotor extends TileEntity {
    private List<Double> dataValues = new ArrayList<>();
    private double yMin = 0.0;
    private double yMax = 20.0;
    private int dataLimit = 10;
    private int lineColor = 0x00FFFF;
    private float lineWidth = 5f;
    private float scale = 1.0f;
    private float heightOffset = 0.0f;
    private float rotationX = 0.0f;
    private float rotationY = 0.0f;
    private float rotationZ = 0.0f;

    public void addData(double value) {
        this.dataValues.add(value);
        while (dataValues.size() > dataLimit) {
            dataValues.remove(0);
        }
        this.markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setDouble("yMin", yMin);
        compound.setDouble("yMax", yMax);
        compound.setInteger("dataLimit", dataLimit);
        compound.setInteger("lineColor", lineColor);
        compound.setFloat("lineWidth", lineWidth);
        compound.setFloat("scale", scale);
        compound.setFloat("heightOffset", heightOffset);
        compound.setFloat("rotationX", rotationX);
        compound.setFloat("rotationY", rotationY);
        compound.setFloat("rotationZ", rotationZ);

        NBTTagList list = new NBTTagList();
        for (Double val : dataValues) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setDouble("val", val);
            list.appendTag(tag);
        }
        compound.setTag("dataValues", list);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        yMin = compound.getDouble("yMin");
        yMax = compound.getDouble("yMax");
        dataLimit = compound.getInteger("dataLimit");
        lineColor = compound.getInteger("lineColor");
        lineWidth = compound.getFloat("lineWidth");
        scale = compound.getFloat("scale");
        heightOffset = compound.getFloat("heightOffset");
        rotationX = compound.getFloat("rotationX");
        rotationY = compound.getFloat("rotationY");
        rotationZ = compound.getFloat("rotationZ");

        dataValues.clear();
        NBTTagList list = compound.getTagList("dataValues", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            dataValues.add(list.getCompoundTagAt(i).getDouble("val"));
        }
    }

    // Getters and Setters
    public List<Double> getDataValues() { return dataValues; }
    public double getYMin() { return yMin; }
    public void setYMin(double yMin) { this.yMin = yMin; markDirty(); }
    public double getYMax() { return yMax; }
    public void setYMax(double yMax) { this.yMax = yMax; markDirty(); }
    public int getDataLimit() { return dataLimit; }
    public void setDataLimit(int limit) { this.dataLimit = limit; markDirty(); }
    public int getLineColor() { return lineColor; }
    public void setLineColor(int color) { this.lineColor = color; markDirty(); }
    public float getLineWidth() { return lineWidth; }
    public void setLineWidth(float width) { this.lineWidth = width; markDirty(); }
    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; markDirty(); }
    public float getHeightOffset() { return heightOffset; }
    public void setHeightOffset(float offset) { this.heightOffset = offset; markDirty(); }
    public float getRotationX() { return rotationX; }
    public void setRotationX(float rot) { this.rotationX = rot % 360.0f; markDirty(); }
    public float getRotationY() { return rotationY; }
    public void setRotationY(float rot) { this.rotationY = rot % 360.0f; markDirty(); }
    public float getRotationZ() { return rotationZ; }
    public void setRotationZ(float rot) { this.rotationZ = rot % 360.0f; markDirty(); }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
}