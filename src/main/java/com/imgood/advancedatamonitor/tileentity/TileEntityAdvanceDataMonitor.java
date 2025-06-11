package com.imgood.advancedatamonitor.tileentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.network.packet.PacketSynTileEntity;
import com.imgood.advancedatamonitor.utils.DataBound;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityAdvanceDataMonitor extends TileEntity {

    private final Map<Integer, NBTTagCompound> dataBoundList = new HashMap<>();
    private boolean visableScreen = true;
    private boolean visableBody = true;
    private boolean visableBack = true;
    public int facing = 0;
    public boolean TEST_MODE = true;
    private int testRandomData = 0;
    private Random random = new Random();

    private final Map<Integer, Integer> tickCounters = new HashMap<>();
    private float rollRotation;


    public TileEntityAdvanceDataMonitor() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        initializeDefaultData();
    }

    public int getDisplayDataSize() {
        return dataBoundList.size();
    }

    // ========================= 核心方法 =========================//
    @Override
    public void updateEntity() {
        updateRollRotation((float) 91 /getMinDataInterval());
        if (TEST_MODE) {
            refreshRamdomData();
        }
        if (worldObj == null || worldObj.isRemote) return;

        for (Map.Entry<Integer, NBTTagCompound> entry : dataBoundList.entrySet()) {
            int index = entry.getKey();
            boolean enable = getEnable(index);
            if (enable) {
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

    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("visableScreen", visableScreen);
        compound.setBoolean("visableBody", visableBody);
        compound.setBoolean("visableBack", visableBack);
        compound.setInteger("facing", facing);
        compound.setInteger("testRandomData", testRandomData);

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
        testRandomData = compound.getInteger("testRandomData");

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

    // ========================= 数据绑定操作 =========================//
    public void setDisplayData(int index, NBTTagCompound displayData) {
        // 获取旧的interval值
        NBTTagCompound oldData = dataBoundList.get(index);
        int oldInterval = oldData != null ? getSafeInt(oldData, "interval", 20) : 20;

        // 合并新数据
        NBTTagCompound mergedData = mergeWithDefault(displayData);
        int newInterval = getSafeInt(mergedData, "interval", 20);

        // 更新数据绑定列表
        dataBoundList.put(index, mergedData);
        markDirty();
        syncData();

        // 仅在服务端处理立即采集
        if (worldObj != null && !worldObj.isRemote) {
            // 检查interval是否变化
            if (oldInterval != newInterval) {
                // 立即处理一次数据采集
                processDataImmediately(index, mergedData);
                // 重置计数器以确保新interval生效
                tickCounters.put(index, 0);
            }
        }
    }

    private void processDataImmediately(int index, NBTTagCompound nbt) {
        String[] xyz = parseXYZ(nbt);
        if (xyz != null) {
            try {
                processTileEntityData(index, nbt, xyz);
            } catch (Exception e) {
                handleProcessingError(index, e);
            }
        }
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

    // ========================= 网络同步 =========================//
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

    // ========================= 辅助方法 =========================//
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

    // ========================= 安全访问方法 =========================//
    private String getSafeString(NBTTagCompound nbt, String key, String defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getString(key) : defaultValue;
    }

    private boolean getSafeBoolean(NBTTagCompound nbt, String key, boolean defaultValue) {
        return (nbt != null && nbt.hasKey(key)) ? nbt.getBoolean(key) : defaultValue;
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

    // ========================= 异常处理 =========================//
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

    // ========================= NBT 合并方法 =========================//
    private NBTTagCompound mergeWithDefault(NBTTagCompound input) {
        NBTTagCompound defaultNBT = createDefaultNBT();
        if (input == null) return defaultNBT;

        NBTTagCompound merged = (NBTTagCompound) defaultNBT.copy();
        for (Object key : input.func_150296_c()) {
            String keyStr = (String) key;
            merged.setTag(
                keyStr,
                input.getTag(keyStr)
                    .copy());
        }
        return merged;
    }

    private NBTTagCompound createDefaultNBT() {
        NBTTagCompound defaultData = new NBTTagCompound();
        String XYZ = this.xCoord + "," + this.yCoord + "," + this.zCoord;
        defaultData.setString("XYZ", XYZ);
        defaultData.setString("lineColor", "00FFFF");
        defaultData.setFloat("lineWidth", 3.0f);
        defaultData.setFloat("scale", 0.3f);
        defaultData.setFloat("yOffset", -0.5f);
        defaultData.setFloat("xOffset", 0.0f);
        defaultData.setFloat("zOffset", 0.0f);
        defaultData.setFloat("rotationX", -30.0f);
        defaultData.setFloat("rotationY", 0.0f);
        defaultData.setFloat("rotationZ", 0.0f);
        defaultData.setInteger("dataLimit", 100);
        defaultData.setDouble("yMin", 0.0);
        defaultData.setDouble("yMax", 20.0);
        defaultData.setString("name", "testRandomData");
        defaultData.setString("displayName", "演示模式");
        defaultData.setTag("dataValues", new NBTTagList());
        defaultData.setInteger("interval", 1);
        defaultData.setDouble("xRange", 5);
        defaultData.setDouble("yRange", 3);
        defaultData.setString("axisLineColor", "FFFFFF");
        defaultData.setString("axisFontColor", "00FFFF");
        defaultData.setDouble("displayNameScale", 2.0);
        defaultData.setString("displayNameColor", "FFFFFF");
        defaultData.setDouble("axisFontScale", 1.0);
        defaultData.setString("dataType", "line");
        defaultData.setBoolean("enable", true);
        defaultData.setBoolean("enableAxis", true);
        defaultData.setBoolean("enableData", true);
        defaultData.setBoolean("enableAxisFont", true);
        return defaultData;
    }

    // ========================= 属性访问器 =========================//
    public boolean isVisableScreen() {
        return visableScreen;
    }

    public void setVisableScreen(boolean visable) {
        this.visableScreen = visable;
        markDirty();
        syncData();
    }

    public boolean isVisableBody() {
        return visableBody;
    }

    public void setVisableBody(boolean visable) {
        this.visableBody = visable;
        markDirty();
        syncData();
    }

    public boolean isVisableBack() {
        return visableBack;
    }

    public void setVisableBack(boolean visable) {
        this.visableBack = visable;
        markDirty();
        syncData();
    }

    public int getFacing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = facing;
        markDirty();
        syncData();
    }

    // ========================= 数据绑定属性访问器 =========================//
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
        setDisplayData(index, nbt); // 这会触发setDisplayData中的逻辑
    }

    public float getYOffset(int index) {
        return getSafeFloat(getDataBound(index), "yOffset", 0.0f);
    }

    public void setYOffset(int index, float offset) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("yOffset", offset);
        setDisplayData(index, nbt);
    }

    public float getZOffset(int index) {
        return getSafeFloat(getDataBound(index), "zOffset", 0.0f);
    }

    public void setZOffset(int index, float offset) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setFloat("zOffset", offset);
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

    public String getDisplayNameColor(int index) {
        return getSafeString(getDataBound(index), "displayNameColor", "FFFFFF");
    }

    public void setDisplayNameColor(int index, String color) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("displayNameColor", color);
        setDisplayData(index, nbt);
    }

    public double getDisplayNameScale(int index) {
        return getSafeDouble(getDataBound(index), "displayNameScale", 1.0);
    }

    public void setDisplayNameScale(int index, double scale) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("displayNameScale", scale);
        setDisplayData(index, nbt);
    }

    // ========================= 新增数据类型支持 =========================//
    public DataBound.DataType getDataType(int index) {
        try {
            String type = getSafeString(getDataBound(index), "dataType", "line");
            return DataBound.DataType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return DataBound.DataType.line;
        }
    }

    public void setDataType(int index, DataBound.DataType type) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("dataType", type.name());
        setDisplayData(index, nbt);
    }

    public boolean getEnable(int index) {
        return getSafeBoolean(getDataBound(index), "enable", true);
    }

    public void setEnable(int index, boolean enable) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("enable", enable);
        setDisplayData(index, nbt);
    }

    public boolean getEnableAxis(int index) {
        return getSafeBoolean(getDataBound(index), "enableAxis", true);
    }

    public void setEnableAxis(int index, boolean enable) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("enableAxis", enable);
        setDisplayData(index, nbt);
    }

    public boolean getEnableData(int index) {
        return getSafeBoolean(getDataBound(index), "enableData", true);
    }

    public void setEnableData(int index, boolean enableData) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("enableData", enableData);
        setDisplayData(index, nbt);
    }

    public boolean getEnableAxisFont(int index) {
        return getSafeBoolean(getDataBound(index), "enableAxisFont", true);
    }

    public void setEnableAxisFont(int index, boolean enableAxisFont) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("enableAxisFont", enableAxisFont);
        setDisplayData(index, nbt);
    }

    // ========================= 数据值访问方法 =========================//
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

    public void setXRange(int index, double value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("xRange", value);
        setDisplayData(index, nbt);
    }

    public double getAxisFontScale(int index) {
        return getSafeDouble(getDataBound(index), "axisFontScale", 1);
    }

    public void setAxisFontScale(int index, double value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("axisFontScale", value);
        setDisplayData(index, nbt);
    }

    public double getXRange(int index) {
        return getSafeDouble(getDataBound(index), "xRange", 1);
    }

    public void setYRange(int index, double value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("yRange", value);
        setDisplayData(index, nbt);
    }

    public double getYRange(int index) {
        return getSafeDouble(getDataBound(index), "yRange", 1);
    }

    public double getYMin(int index) {
        return getSafeDouble(getDataBound(index), "yMin", 0.0);
    }

    public void setAxisLineColor(int index, String value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("axisLineColor", value);
        setDisplayData(index, nbt);
    }

    public String getAxisLineColor(int index) {
        return getSafeString(getDataBound(index), "axisLineColor", "FFFFFF");
    }

    public void setAxisFontColor(int index, String value) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("axisFontColor", value);
        setDisplayData(index, nbt);
    }

    public String getAxisFontColor(int index) {
        return getSafeString(getDataBound(index), "axisFontColor", "FFFFFF");
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

    public void refreshRamdomData() {
        this.testRandomData = random.nextInt(1000);
        markDirty();
        syncData();
    }

    // ========================= 其他方法 =========================//
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

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        // 8个区块 = 8 * 16 = 128 格（水平方向）
        double range = 128.0; // 从中心向四周延伸 128 格
        return AxisAlignedBB.getBoundingBox(
                xCoord - range,  // 最小X
                yCoord - 64.0,    // 最小Y（假设垂直方向延伸64格）
                zCoord - range,  // 最小Z
                xCoord + range,  // 最大X
                yCoord + 64.0,    // 最大Y
                zCoord + range   // 最大Z
        );
    }

    public float getRollRotation() {
        return rollRotation;
    }

    public void updateRollRotation(float increment) {
        rollRotation = (rollRotation + increment) % 360;
    }

    public int getMinDataInterval() {
        for (int i = 0; i < getDataBoundCount(); i++) {
            int interval = getSafeInt(getDataBound(i), "interval", 1);
            if (interval > 0) return interval;
        }
        return 1;
    }

}
