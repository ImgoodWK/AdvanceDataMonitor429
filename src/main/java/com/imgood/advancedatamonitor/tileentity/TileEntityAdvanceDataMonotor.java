package com.imgood.advancedatamonitor.tileentity;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.utils.DataBound;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileEntityAdvanceDataMonotor extends TileEntity {
    private final Map<Integer, NBTTagCompound> dataBoundList = new HashMap<>();
    private boolean visableScreen = true;
    private boolean visableBody = true;
    private boolean visableBack = true;
    public int facing = 0;

    private final Map<Integer, Integer> tickCounters = new HashMap<>();
    public TileEntityAdvanceDataMonotor() {
        FMLCommonHandler.instance().bus().register(this);
        initializeDefaultData();
    }


    public int getDisplayDataSize() {
        return dataBoundList.size();
    }
    //========================= 核心方法 =========================//
    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;

        for (Map.Entry<Integer, NBTTagCompound> entry : dataBoundList.entrySet()) {
            int index = entry.getKey();
            NBTTagCompound nbt = entry.getValue();

            // 处理空数据绑定的情况
            if (nbt == null) {
                handleNullDataBound(index);
                continue;
            }

            // 获取interval并确保最小值
            int interval = Math.max(getSafeInt(nbt, "interval", 20), 1);
            int currentTick = tickCounters.getOrDefault(index, 0);
            currentTick++;

            if (currentTick >= interval) {
                // 处理数据项
                String[] xyz = parseXYZ(nbt);
                if (xyz != null) {
                    try {
                        processTileEntityData(index, nbt, xyz);
                    } catch (Exception e) {
                        handleProcessingError(index, e);
                    }
                }
                currentTick = 0;
            }

            tickCounters.put(index, currentTick);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("visableScreen", visableScreen);
        compound.setBoolean("visableBody", visableBody);
        compound.setBoolean("visableBack", visableBack);
        compound.setInteger("facing", facing);

        NBTTagCompound displayDataNBT = new NBTTagCompound();
        for (Map.Entry<Integer, NBTTagCompound> entry : dataBoundList.entrySet()) {
            if (entry.getValue() != null) {
                displayDataNBT.setTag("DisplayData" + entry.getKey(), entry.getValue());
            }
        }
        compound.setTag("DisplayDataMap", displayDataNBT);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        dataBoundList.clear();

        visableScreen = compound.getBoolean("visableScreen");
        visableBody = compound.getBoolean("visableBody");
        visableBack = compound.getBoolean("visableBack");
        facing = compound.getInteger("facing");

        if (compound.hasKey("DisplayDataMap")) {
            NBTTagCompound displayDataNBT = compound.getCompoundTag("DisplayDataMap");
            for (Object key : displayDataNBT.func_150296_c()) {
                String keyStr = (String) key;
                if (keyStr.startsWith("DisplayData")) {
                    processDataBoundKey(displayDataNBT, keyStr);
                }
            }
        }
        initializeDefaultData();
    }

    //========================= 数据绑定操作 =========================//
    public void setDisplayData(int index, NBTTagCompound displayData) {
        NBTTagCompound mergedData = mergeWithDefault(displayData);
        dataBoundList.put(index, mergedData);
        markDirty();
        syncData();
    }

    public NBTTagCompound getDataBound(int index) {
        NBTTagCompound nbt = dataBoundList.get(index);
        if (nbt == null) {
            nbt = createDefaultNBT();
            dataBoundList.put(index, nbt);
        }
        return nbt;
    }

    public void removeDataBound(int index) {
        if (dataBoundList.containsKey(index)) {
            dataBoundList.remove(index);
            markDirty();
            syncData();
        }
    }

    //========================= 网络同步 =========================//
    public void syncData() {
        if (worldObj != null && !worldObj.isRemote) {
            NBTTagCompound tag = new NBTTagCompound();
            writeToNBT(tag);
            PacketSynTileEntity packet = new PacketSynTileEntity(xCoord, yCoord, zCoord, tag);
            AdvanceDataMonitor.ADMCHANEL.sendToDimension(packet, worldObj.provider.dimensionId);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbtTag = new NBTTagCompound();
        writeToNBT(nbtTag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbtTag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    //========================= 辅助方法 =========================//
    private void initializeDefaultData() {
        if (dataBoundList.isEmpty()) {
            setDisplayData(0, null);
        }
    }

    private String[] parseXYZ(NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey("XYZ")) return null;

        String xyzStr = getSafeString(nbt, "XYZ", "");
        String[] xyz = xyzStr.split(",");
        if (xyz.length != 3) {
            AdvanceDataMonitor.LOG.warn("Invalid XYZ format: {}", xyzStr);
            return null;
        }
        return xyz;
    }

    private void processTileEntityData(int index, NBTTagCompound nbt, String[] xyz) {
        int targetX = parseIntSafe(xyz[0]);
        int targetY = parseIntSafe(xyz[1]);
        int targetZ = parseIntSafe(xyz[2]);

        if (worldObj == null || !worldObj.blockExists(targetX, targetY, targetZ)) return;

        TileEntity target = worldObj.getTileEntity(targetX, targetY, targetZ);
        if (target == null) return;

        NBTTagCompound targetNbt = new NBTTagCompound();
        target.writeToNBT(targetNbt);

        String dataName = getSafeString(nbt, "name", "null");
        if (targetNbt.hasKey(dataName)) {
            double value = targetNbt.getDouble(dataName);
            addData(index, value, nbt);
        }
    }

    private void addData(int index, double value, NBTTagCompound nbt) {
        NBTTagList dataValues = nbt.getTagList("dataValues", 10);

        NBTTagCompound dataPoint = new NBTTagCompound();
        dataPoint.setDouble("data", value);
        dataValues.appendTag(dataPoint);

        int dataLimit = getSafeInt(nbt, "dataLimit", 10);
        while (dataValues.tagCount() > dataLimit) {
            dataValues.removeTag(0);
        }

        nbt.setTag("dataValues", dataValues);
        markDirty();
        syncData();
    }

    //========================= 安全访问方法 =========================//
    private String getSafeString(NBTTagCompound nbt, String key, String defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getString(key) : defaultValue;
    }

    private int getSafeInt(NBTTagCompound nbt, String key, int defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getInteger(key) : defaultValue;
    }

    private float getSafeFloat(NBTTagCompound nbt, String key, float defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getFloat(key) : defaultValue;
    }

    private double getSafeDouble(NBTTagCompound nbt, String key, double defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getDouble(key) : defaultValue;
    }

    private int parseIntSafe(String str) {
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            AdvanceDataMonitor.LOG.warn("Invalid number format: {}", str);
            return 0;
        }
    }

    //========================= 异常处理 =========================//
    private void handleNullDataBound(int index) {
        AdvanceDataMonitor.LOG.warn("Null data bound at index {}, removing", index);
        dataBoundList.remove(index);
        markDirty();
        syncData();
    }

    private void handleProcessingError(int index, Exception e) {
        AdvanceDataMonitor.LOG.error("Error processing data bound at index {}: {}", index, e.toString());
        AdvanceDataMonitor.LOG.debug("Stack trace:", e);
    }

    private void processDataBoundKey(NBTTagCompound displayDataNBT, String keyStr) {
        try {
            int index = Integer.parseInt(keyStr.substring(11));
            NBTTagCompound boundData = displayDataNBT.getCompoundTag(keyStr);
            if (boundData != null) {
                dataBoundList.put(index, boundData);
            }
        } catch (NumberFormatException e) {
            AdvanceDataMonitor.LOG.error("Invalid data bound index format: {}", keyStr);
        } catch (IndexOutOfBoundsException e) {
            AdvanceDataMonitor.LOG.error("Malformed data bound key: {}", keyStr);
        }
    }

    //========================= NBT 合并方法 =========================//
    private NBTTagCompound mergeWithDefault(NBTTagCompound input) {
        NBTTagCompound defaultNBT = createDefaultNBT();
        if (input == null) return defaultNBT;

        NBTTagCompound merged = (NBTTagCompound) defaultNBT.copy();
        for (Object key : input.func_150296_c()) {
            String keyStr = (String) key;
            merged.setTag(keyStr, input.getTag(keyStr).copy());
        }
        return merged;
    }

    private NBTTagCompound createDefaultNBT() {
        NBTTagCompound defaultData = new NBTTagCompound();
        defaultData.setString("XYZ", "0,0,0");
        defaultData.setString("lineColor", "00FFFF");
        defaultData.setFloat("lineWidth", 5.0f);
        defaultData.setFloat("scale", 1.0f);
        defaultData.setFloat("yOffset", 0.0f);
        defaultData.setFloat("xOffset", 0.0f);
        defaultData.setFloat("rotationX", 0.0f);
        defaultData.setFloat("rotationY", 0.0f);
        defaultData.setFloat("rotationZ", 0.0f);
        defaultData.setInteger("dataLimit", 10);
        defaultData.setDouble("yMin", 0.0);
        defaultData.setDouble("yMax", 20.0);
        defaultData.setString("name", "null");
        defaultData.setString("displayName", "New Display");
        defaultData.setTag("dataValues", new NBTTagList());
        defaultData.setInteger("interval", 20);
        return defaultData;
    }

    //========================= 属性访问器 =========================//
    public boolean isVisableScreen() { return visableScreen; }
    public void setVisableScreen(boolean visable) {
        this.visableScreen = visable;
        markDirty();
        syncData();
    }

    public boolean isVisableBody() { return visableBody; }
    public void setVisableBody(boolean visable) {
        this.visableBody = visable;
        markDirty();
        syncData();
    }

    public boolean isVisableBack() { return visableBack; }
    public void setVisableBack(boolean visable) {
        this.visableBack = visable;
        markDirty();
        syncData();
    }

    public int getFacing() { return facing; }

    public void setFacing(int facing) {
        this.facing = facing;
        markDirty();
        syncData();
    }

    //========================= 数据绑定属性访问器 =========================//
    public String getXYZ(int index) {
        return getSafeString(getDataBound(index), "XYZ", "0,0,0");
    }

    public void setXYZ(int index, String XYZ) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("XYZ", XYZ);
        setDisplayData(index, nbt);
    }

    public String getLineColor(int index) {
        return getSafeString(getDataBound(index), "lineColor", "00FFFF");
    }

    public void setLineColor(int index, String color) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("lineColor", color);
        setDisplayData(index, nbt);
    }

    public float getLineWidth(int index) {
        return getSafeFloat(getDataBound(index), "lineWidth", 5.0f);
    }

    public void setLineWidth(int index, float width) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("lineWidth", width);
        setDisplayData(index, nbt);
    }

    public float getScale(int index) {
        return getSafeFloat(getDataBound(index), "scale", 1.0f);
    }

    public void setScale(int index, float scale) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("scale", scale);
        setDisplayData(index, nbt);
    }

    public Integer getInterval(int index) {
        return getSafeInt(getDataBound(index), "interval", 10);
    }

    public void setInterval(int index, Integer interval) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setInteger("interval", interval);
        setDisplayData(index, nbt);
    }

    public float getYOffset(int index) {
        return getSafeFloat(getDataBound(index), "yOffset", 0.0f);
    }

    public void setYOffset(int index, float offset) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("yOffset", offset);
        setDisplayData(index, nbt);
    }

    public float getRotationX(int index) {
        return getSafeFloat(getDataBound(index), "rotationX", 0.0f);
    }

    public void setRotationX(int index, float rotation) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("rotationX", rotation % 360.0f);
        setDisplayData(index, nbt);
    }

    public float getRotationY(int index) {
        return getSafeFloat(getDataBound(index), "rotationY", 0.0f);
    }

    public void setRotationY(int index, float rotation) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("rotationY", rotation % 360.0f);
        setDisplayData(index, nbt);
    }

    public float getRotationZ(int index) {
        return getSafeFloat(getDataBound(index), "rotationZ", 0.0f);
    }

    public void setRotationZ(int index, float rotation) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("rotationZ", rotation % 360.0f);
        setDisplayData(index, nbt);
    }

    public String getName(int index) {
        return getSafeString(getDataBound(index), "name", "null");
    }

    public void setName(int index, String name) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("name", name);
        setDisplayData(index, nbt);
    }

    //========================= 新增数据类型支持 =========================//
    public DataBound.DataType getDataType(int index) {
        try {
            String type = getSafeString(getDataBound(index), "dataType", "Line");
            return DataBound.DataType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return DataBound.DataType.Line;
        }
    }

    public void setDataType(int index, DataBound.DataType type) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("dataType", type.name());
        setDisplayData(index, nbt);
    }

    //========================= 数据值访问方法 =========================//
    public NBTTagList getDataValues(int index) {
        NBTTagCompound nbt = getDataBound(index);
        NBTTagList list = nbt.getTagList("dataValues", 6);
        return list != null ? list : new NBTTagList();
    }

    public List<Double> getDoubleValues(int index) {
        List<Double> values = new ArrayList<>();
        NBTTagList list = getDataValues(index);
        for (int i = 0; i < list.tagCount(); i++) {
            values.add(list.func_150309_d(i));
        }
        return values;
    }

    public float getXOffset(int index) {
        return getSafeFloat(getDataBound(index), "xOffset", 0.0f);
    }

    public void setXOffset(int index, float offset) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("xOffset", offset);
        setDisplayData(index, nbt);
    }

    public double getYMin(int index) {
        return getSafeDouble(getDataBound(index), "yMin", 0.0);
    }

    public void setYMin(int index, double value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("yMin", value);
        setDisplayData(index, nbt);
    }

    public double getYMax(int index) {
        return getSafeDouble(getDataBound(index), "yMax", 20.0);
    }

    public void setYMax(int index, double value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("yMax", value);
        setDisplayData(index, nbt);
    }

    public int getDataLimit(int index) {
        return getSafeInt(getDataBound(index), "dataLimit", 10);
    }

    public void setDataLimit(int index, int limit) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setInteger("dataLimit", limit);
        setDisplayData(index, nbt);
    }

    public String getDisplayName(int index) {
        return getSafeString(getDataBound(index), "displayName", "Unnamed");
    }

    public void setDisplayName(int index, String name) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("displayName", name);
        setDisplayData(index, nbt);
    }

    //========================= 其他方法 =========================//
    public int getDataBoundCount() {
        return dataBoundList.size();
    }

    public Map<Integer, NBTTagCompound> getDataBoundList() {
        return new HashMap<>(dataBoundList);
    }

    public boolean validateDataBound(int index) {
        NBTTagCompound nbt = getDataBound(index);
        if (nbt == null) return false;

        String[] xyz = parseXYZ(nbt);
        if (xyz == null) return false;

        try {
            int x = parseIntSafe(xyz[0]);
            int y = parseIntSafe(xyz[1]);
            int z = parseIntSafe(xyz[2]);

            TileEntity te = worldObj.getTileEntity(x, y, z);
            if (te == null) return false;

            NBTTagCompound targetNbt = new NBTTagCompound();
            te.writeToNBT(targetNbt);
            return targetNbt.hasKey(getSafeString(nbt, "name", ""));
        } catch (Exception e) {
            return false;
        }
    }
}