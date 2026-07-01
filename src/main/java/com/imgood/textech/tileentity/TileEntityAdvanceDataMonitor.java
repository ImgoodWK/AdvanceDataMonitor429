package com.imgood.textech.tileentity;

import static com.imgood.textech.utils.TileEntityTypeHelper.getTileEntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.network.packet.PacketSynTileEntity;
import com.imgood.textech.utils.CraftingTemplateParser;
import com.imgood.textech.utils.DataBound;
import com.imgood.textech.utils.TileEntityTypeHelper;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;

/**
 * Display names / 显示名称:
 * - EN: Advance Data Monitor
 * - ZH: 高级数据监视器
 * Lang keys: tile.advDataMonitor.name (parent block)
 */
public class TileEntityAdvanceDataMonitor extends TileEntity implements IOwnableTile {

    /** Maximum number of data bindings per monitor (GUI list capacity). */
    public static final int MAX_DATA_BINDINGS = 36;

    private String ownerName = "";

    private final Map<Integer, NBTTagCompound> dataBoundList = new HashMap<>();
    private boolean visableScreen = true;
    private boolean visableBody = true;
    private boolean visableBack = true;
    private boolean renderBothSides = false;
    public int facing = 0;
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

    @Override
    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    @Override
    public void setOwnerName(String name) {
        this.ownerName = name == null ? "" : name;
        markDirty();
    }

    @Override
    public void setOwnerFromPlacer(EntityLivingBase placer) {
        setOwnerName(OwnableTileUtil.nameFromPlacer(placer));
    }

    @Override
    public void claimOwnerIfEmpty(EntityPlayer player) {
        if (player == null || !OwnableTileUtil.isEmpty(getOwnerName())) {
            return;
        }
        setOwnerName(player.getCommandSenderName());
    }

    public int getDisplayDataSize() {
        return dataBoundList.size();
    }

    // ========================= 核心方法 =========================//
    @Override
    public void updateEntity() {
        updateRollRotation((float) 91 / getMinDataInterval());
        if (Config.debugMonitorTestMode) {
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

    public void processTileEntityData(int index, NBTTagCompound nbt, String[] xyz) {
        int targetX = parseIntSafe(xyz[0]);
        int targetY = parseIntSafe(xyz[1]);
        int targetZ = parseIntSafe(xyz[2]);

        if (worldObj == null || !worldObj.blockExists(targetX, targetY, targetZ)) return;

        TileEntity target = worldObj.getTileEntity(targetX, targetY, targetZ);
        if (target == null) return;

        // ========== 合成监控分支 ==========
        if (getTileEntityType(target) == TileEntityTypeHelper.TileEntityType.ADV_CRAFTINGLINK) {
            TileEntityAdvanceCraftingLink craftingLink = (TileEntityAdvanceCraftingLink) target;

            boolean monitorNetworkWide = nbt.getBoolean("monitorNetworkWide");
            String template = nbt.getString("craftingTemplate");
            NBTTagList lineList = new NBTTagList();

            if (monitorNetworkWide) {
                // 全网络模式：忽略模板，直接使用 getStatsInfo()
                String info = craftingLink.getStatsInfo();
                String[] lines = info.split("\\n");
                for (String line : lines) {
                    lineList.appendTag(new NBTTagString(line));
                }
                nbt.setTag("networkLines", lineList);
                nbt.removeTag("lines"); // 移除可能残留的单 CPU 数据
            } else {
                // 单 CPU / 模板模式
                String[] lines;
                if (template != null && !template.isEmpty()) {
                    lines = parseTemplateWithLink(template, craftingLink);
                } else {
                    // 回退到旧的硬编码格式（向后兼容）
                    String info = craftingLink.getStatsInfo();
                    lines = info.split("\\n");
                }
                for (String line : lines) {
                    lineList.appendTag(new NBTTagString(line));
                }
                nbt.setTag("lines", lineList);
                nbt.removeTag("networkLines"); // 移除可能残留的全网络数据
            }

            // 确保数据类型被标记为 crafting
            if (!nbt.hasKey("dataType")) {
                nbt.setString("dataType", "crafting");
            }

            markDirty();
            syncData();
            return;
        }
        // =============================================

        // --- 原有其他类型的处理（保持不变）---
        if (getTileEntityType(target) == TileEntityTypeHelper.TileEntityType.ADV_STORAGELINK) {
            TileEntityAdvanceStorageLink storageLink = (TileEntityAdvanceStorageLink) target;
            nbt.removeTag("storageStatisticsInterval");
            nbt.setTag("storageItems", storageLink.createStorageItemsSnapshot());
            nbt.setString("dataType", "storage");
            markDirty();
            syncData();
            return;
        }

        String dataName = getSafeString(nbt, "name", "null");
        double value = 0;

        if (getTileEntityType(target) == TileEntityTypeHelper.TileEntityType.ADV_NETWORKLINK) {
            value = processAE2NetworkData(target, dataName);
            boolean isValue = nbt.getBoolean("isValue");
            if (isValue) {
                String[] percentageKeys = { "TotalBytes", "UsedBytes", "TotalItemTypes", "UsedItemTypes",
                    "TotalFluidBytes", "UsedFluidBytes", "TotalFluidTypes", "UsedFluidTypes" };
                for (String key : percentageKeys) {
                    if (key.equals(dataName)) {
                        value = calculatePercentage(target, dataName, value);
                        break;
                    }
                }
            }
        } else {
            NBTTagCompound targetNbt = new NBTTagCompound();
            target.writeToNBT(targetNbt);
            if (targetNbt.hasKey(dataName)) {
                value = targetNbt.getDouble(dataName);
            }
        }
        addData(index, value, nbt);
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("visableScreen", visableScreen);
        compound.setBoolean("visableBody", visableBody);
        compound.setBoolean("visableBack", visableBack);
        compound.setBoolean("renderBothSides", renderBothSides);
        compound.setInteger("facing", facing);
        compound.setInteger("testRandomData", testRandomData);
        OwnableTileUtil.writeOwner(compound, ownerName);

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
        renderBothSides = compound.getBoolean("renderBothSides");
        facing = compound.getInteger("facing");
        testRandomData = compound.getInteger("testRandomData");
        ownerName = OwnableTileUtil.readOwner(compound);

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

        // --- 新增：如果是 Crafting 类型，补全缺失的渲染默认值（仅当字段不存在时）---
        /*
         * if ("crafting".equals(mergedData.getString("dataType"))) {
         * if (!mergedData.hasKey("textColor")) mergedData.setString("textColor", "FFFFFF");
         * if (!mergedData.hasKey("textAlpha")) mergedData.setFloat("textAlpha", 1.0f);
         * if (!mergedData.hasKey("textScale")) mergedData.setFloat("textScale", 0.5f);
         * if (!mergedData.hasKey("textAlign")) mergedData.setInteger("textAlign", 0);
         * if (!mergedData.hasKey("scale")) mergedData.setFloat("scale", 0.3f);
         * if (!mergedData.hasKey("rotationX")) mergedData.setFloat("rotationX", 180f);
         * if (!mergedData.hasKey("rotationY")) mergedData.setFloat("rotationY", 0f);
         * if (!mergedData.hasKey("rotationZ")) mergedData.setFloat("rotationZ", 0.0f);
         * if (!mergedData.hasKey("displayName") || mergedData.getString("displayName").isEmpty()) {
         * mergedData.setString("displayName", "Crafting Monitor");
         * }
         * }
         */
        // ----------------------------------------------------------------

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

    public void processDataImmediately(int index, NBTTagCompound nbt) {
        String[] xyz = parseXYZ(nbt);
        if (xyz != null) {
            try {
                processTileEntityData(index, nbt, xyz);
            } catch (Exception e) {
                handleProcessingError(index, e);
            }
        }
    }

    /**
     * Returns a bound entry without creating placeholder slots.
     */
    public NBTTagCompound peekDataBound(int index) {
        return dataBoundList.get(index);
    }

    public NBTTagCompound getDataBound(int index) {
        NBTTagCompound nbt = dataBoundList.get(index);
        if (nbt == null) {
            nbt = createDefaultNBT();
            dataBoundList.put(index, nbt);
        }
        return nbt;
    }

    public boolean hasBindingAtCoords(int x, int y, int z) {
        String target = x + "," + y + "," + z;
        for (NBTTagCompound nbt : dataBoundList.values()) {
            if (nbt == null || !nbt.hasKey("XYZ")) {
                continue;
            }
            if (target.equals(nbt.getString("XYZ"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the lowest free binding index, or -1 when at {@link #MAX_DATA_BINDINGS}.
     */
    public int findNextAvailableBindingIndex() {
        if (dataBoundList.size() >= MAX_DATA_BINDINGS) {
            return -1;
        }
        for (int i = 0; i < MAX_DATA_BINDINGS; i++) {
            if (!dataBoundList.containsKey(i)) {
                return i;
            }
        }
        return -1;
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

    /**
     * Parses the XYZ string from a bound data entry's NBT into an int array [x, y, z].
     * Returns null if the entry has no valid XYZ binding.
     */
    public int[] parseBoundXYZ(int index) {
        NBTTagCompound nbt = getDataBound(index);
        if (nbt == null || !nbt.hasKey("XYZ")) return null;
        String xyzStr = getSafeString(nbt, "XYZ", "");
        String[] parts = xyzStr.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the data type string for a bound entry index.
     */
    public String getDataTypeString(int index) {
        return getSafeString(getDataBound(index), "dataType", "line");
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
        defaultData.setFloat("zOffset", -0.5f);
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
        defaultData.setBoolean("isValue", true);
        defaultData.setBoolean("enableGrid", true);
        defaultData.setString("gridLineColor", "FFFFFF");
        defaultData.setBoolean("gridLineStyle", true);
        defaultData.setDouble("gridLineWidth", 0.8);
        defaultData.setDouble("gridLineAlpha", 0.3);
        defaultData.setDouble("axisLineWidth", 1.0);
        defaultData.setDouble("tickLengthFactor", 1.0);
        // 新增透明度属性
        defaultData.setDouble("nameAlpha", 1.0);
        defaultData.setDouble("axisLineAlpha", 1.0);
        defaultData.setDouble("axisFontAlpha", 1.0);
        defaultData.setDouble("lineAlpha", 1.0);
        // 用于crafting类型的属性
        defaultData.setFloat("textAlpha", 0.3f);
        defaultData.setFloat("textScale", 1);
        defaultData.setInteger("textAlign", 0);
        defaultData.setBoolean("monitorNetworkWide", true);
        defaultData.setString(
            "craftingTemplate",
            "{br}合成监控{br}总处理器: {totalCpus}  忙碌: {busyCpus}{br}{busyCpus:CPU#1 == 1 ? \"CPU1工作中\" : \"CPU1空闲\"}");
        defaultData.setInteger("storageColumns", 4);
        defaultData.setDouble("storageSpacing", 0.45);
        defaultData.setDouble("storageIconScale", 1.0);
        defaultData.setInteger("storageStatisticsInterval", 20);
        defaultData.setBoolean("showItemCount", true);
        defaultData.setBoolean("showItemDelta", false);
        defaultData.setBoolean("showItemName", false);
        defaultData.setInteger("itemCountOrder", 0);
        defaultData.setInteger("itemDeltaOrder", 1);
        defaultData.setInteger("itemNameOrder", 2);
        defaultData.setInteger("storageSortMode", 0);
        defaultData.setTag("storageItems", new NBTTagList());
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

    public boolean isRenderBothSides() {
        return renderBothSides;
    }

    public void setRenderBothSides(boolean bothSides) {
        this.renderBothSides = bothSides;
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

    // ========================= 新增透明度属性访问器 =========================//
    public double getNameAlpha(int index) {
        return getSafeDouble(getDataBound(index), "nameAlpha", 1.0);
    }

    public void setNameAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("nameAlpha", Math.max(0.0, Math.min(1.0, alpha)));
        setDisplayData(index, nbt);
    }

    public double getAxisLineAlpha(int index) {
        return getSafeDouble(getDataBound(index), "axisLineAlpha", 1.0);
    }

    public void setAxisLineAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("axisLineAlpha", Math.max(0.0, Math.min(1.0, alpha)));
        setDisplayData(index, nbt);
    }

    public double getAxisFontAlpha(int index) {
        return getSafeDouble(getDataBound(index), "axisFontAlpha", 1.0);
    }

    public void setAxisFontAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("axisFontAlpha", Math.max(0.0, Math.min(1.0, alpha)));
        setDisplayData(index, nbt);
    }

    public double getLineAlpha(int index) {
        return getSafeDouble(getDataBound(index), "lineAlpha", 1.0);
    }

    public void setLineAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("lineAlpha", Math.max(0.0, Math.min(1.0, alpha)));
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

    public boolean getIsValue(int index) {
        return getSafeBoolean(getDataBound(index), "isValue", true);
    }

    public void setIsValue(int index, boolean isValue) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("isValue", isValue);
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

    // ========================= 新增的三个方法 =========================//
    public double getGridLineWidth(int index) {
        return getSafeDouble(getDataBound(index), "gridLineWidth", 0.8);
    }

    public double getAxisLineWidth(int index) {
        return getSafeDouble(getDataBound(index), "axisLineWidth", 1.0);
    }

    public double getTickLengthFactor(int index) {
        return getSafeDouble(getDataBound(index), "tickLengthFactor", 1.0);
    }

    public double getGridLineAlpha(int index) {
        return getSafeDouble(getDataBound(index), "gridLineAlpha", 0.5);
    }

    public double setGridLineAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("gridLineAlpha", alpha);
        setDisplayData(index, nbt);
        return alpha;
    }

    public double getTextScale(int index) {
        return getSafeDouble(getDataBound(index), "textScale", 0.3);
    }

    public void setGridLineWidth(int index, double width) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("gridLineWidth", width);
        setDisplayData(index, nbt);
    }

    public void setTextAlpha(int index, double alpha) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setDouble("textAlpha", alpha);
        setDisplayData(index, nbt);
    }

    public double getTextAlpha(int index) {
        return getSafeDouble(getDataBound(index), "textAlpha", 0.8);
    }

    // 监测范围 (单处理器 / 全网络)
    public boolean getMonitorNetworkWide(int index) {
        return getSafeBoolean(getDataBound(index), "monitorNetworkWide", false);
    }

    public void setMonitorNetworkWide(int index, boolean wide) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setBoolean("monitorNetworkWide", wide);
        setDisplayData(index, nbt);
    }

    // 文字对齐 (0=左对齐, 1=居中, 2=右对齐)
    public int getTextAlign(int index) {
        return getSafeInt(getDataBound(index), "textAlign", 0);
    }

    public void setTextAlign(int index, int align) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setInteger("textAlign", Math.max(0, Math.min(2, align))); // 限制范围 0~2
        setDisplayData(index, nbt);
    }

    public String getCraftingTemplate(int index) {
        return getSafeString(getDataBound(index), "craftingTemplate", "");
    }

    public void setCraftingTemplate(int index, String template) {
        NBTTagCompound nbt = getDataBound(index);
        nbt.setString("craftingTemplate", template);
        setDisplayData(index, nbt);
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
        double range = 128.0;
        return AxisAlignedBB.getBoundingBox(
            xCoord - range,
            yCoord - 64.0,
            zCoord - range,
            xCoord + range,
            yCoord + 64.0,
            zCoord + range);
    }

    public float getRollRotation() {
        return rollRotation;
    }

    public void updateRollRotation(float increment) {
        rollRotation = (rollRotation + increment) % 360;
    }

    public int getMinDataInterval() {
        for (NBTTagCompound nbt : dataBoundList.values()) {
            int interval = getSafeInt(nbt, "interval", 1);
            if (interval > 0) {
                return interval;
            }
        }
        return 1;
    }

    // ========================= AE2 网络支持 =========================//
    @Optional.Method(modid = "appliedenergistics2")
    private Map<String, Long> getAE2NetworkStats(IGrid grid) {
        Map<String, Long> stats = new HashMap<>();
        long totalBytes = 0L;
        long usedBytes = 0L;
        long totalItemTypes = 0L;
        long usedItemTypes = 0L;
        long totalFluidBytes = 0L;
        long usedFluidBytes = 0L;
        long totalFluidTypes = 0L;
        long usedFluidTypes = 0L;

        for (IGridNode node : grid.getMachines(TileDrive.class)) {
            TileDrive drive = (TileDrive) node.getMachine();
            long[] driveStats = processDriveInventory(drive);
            totalBytes += driveStats[0];
            usedBytes += driveStats[1];
            totalItemTypes += driveStats[2];
            usedItemTypes += driveStats[3];
            totalFluidBytes += driveStats[4];
            usedFluidBytes += driveStats[5];
            totalFluidTypes += driveStats[6];
            usedFluidTypes += driveStats[7];
        }

        for (IGridNode node : grid.getMachines(TileChest.class)) {
            TileChest chest = (TileChest) node.getMachine();
            long[] chestStats = processChestInventory(chest);
            totalBytes += chestStats[0];
            usedBytes += chestStats[1];
            totalItemTypes += chestStats[2];
            usedItemTypes += chestStats[3];
            totalFluidBytes += chestStats[4];
            usedFluidBytes += chestStats[5];
            totalFluidTypes += chestStats[6];
            usedFluidTypes += chestStats[7];
        }

        stats.put("TotalBytes", totalBytes);
        stats.put("UsedBytes", usedBytes);
        stats.put("TotalItemTypes", totalItemTypes);
        stats.put("UsedItemTypes", usedItemTypes);

        stats.put("TotalFluidBytes", totalFluidBytes);
        stats.put("UsedFluidBytes", usedFluidBytes);
        stats.put("TotalFluidTypes", totalFluidTypes);
        stats.put("UsedFluidTypes", usedFluidTypes);

        return stats;
    }

    @Optional.Method(modid = "appliedenergistics2")
    private long[] processDriveInventory(TileDrive drive) {
        long[] stats = new long[8];
        for (int i = 0; i < drive.getInternalInventory()
            .getSizeInventory(); i++) {
            ItemStack stack = drive.getInternalInventory()
                .getStackInSlot(i);
            if (stack != null) {
                long[] cellStats = processCellStack(stack);
                for (int j = 0; j < cellStats.length; j++) {
                    stats[j] += cellStats[j];
                }
            }
        }
        return stats;
    }

    @Optional.Method(modid = "appliedenergistics2")
    private long[] processChestInventory(TileChest chest) {
        long[] stats = new long[8];
        ItemStack stack = chest.getInternalInventory()
            .getStackInSlot(0);
        if (stack != null) {
            long[] cellStats = processCellStack(stack);
            for (int j = 0; j < cellStats.length; j++) {
                stats[j] += cellStats[j];
            }
        }
        return stats;
    }

    @Optional.Method(modid = "appliedenergistics2")
    private long[] processCellStack(ItemStack stack) {
        long[] stats = new long[8];

        IMEInventoryHandler itemInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler itemHandler = (ICellInventoryHandler) itemInventory;
            ICellInventory itemCell = itemHandler.getCellInv();
            if (itemCell != null) {
                stats[0] = itemCell.getTotalBytes();
                stats[1] = itemCell.getUsedBytes();
                stats[2] = itemCell.getTotalItemTypes();
                stats[3] = itemCell.getStoredItemTypes();
            }
        }

        IMEInventoryHandler fluidInventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInventory instanceof ICellInventoryHandler) {
            ICellInventoryHandler fluidHandler = (ICellInventoryHandler) fluidInventory;
            ICellInventory fluidCell = fluidHandler.getCellInv();
            if (fluidCell != null) {
                stats[4] = fluidCell.getTotalBytes();
                stats[5] = fluidCell.getUsedBytes();
                stats[6] = fluidCell.getTotalItemTypes();
                stats[7] = fluidCell.getStoredItemTypes();
            }
        }

        return stats;
    }

    @Optional.Method(modid = "appliedenergistics2")
    private double processAE2NetworkData(TileEntity target, String dataName) {
        if (!(target instanceof TileEntityAdvanceNetworkLink)) {
            AdvanceDataMonitor.LOG.info("target not instance of TileEntityAdvanceNetworkLink");
            return 0;
        }

        TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) target;
        switch (dataName) {
            case "TotalBytes":
                return link.getItemTotalBytes();
            case "UsedBytes":
                return link.getItemUsedBytes();
            case "TotalItemTypes":
                return link.getItemTotalTypes();
            case "UsedItemTypes":
                return link.getItemUsedTypes();
            case "TotalFluidBytes":
                return link.getFluidTotalBytes();
            case "UsedFluidBytes":
                return link.getFluidUsedBytes();
            case "TotalFluidTypes":
                return link.getFluidTotalTypes();
            case "UsedFluidTypes":
                return link.getFluidUsedTypes();
            default:
                return 0;
        }
    }

    @Optional.Method(modid = "appliedenergistics2")
    private double calculatePercentage(TileEntity target, String dataName, double currentValue) {
        if (!(target instanceof TileEntityAdvanceNetworkLink)) {
            return currentValue;
        }

        TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) target;
        double total = 0;
        double used = 0;

        switch (dataName) {
            case "UsedBytes":
            case "TotalBytes":
                total = link.getItemTotalBytes();
                used = link.getItemUsedBytes();
                break;
            case "UsedItemTypes":
            case "TotalItemTypes":
                total = link.getItemTotalTypes();
                used = link.getItemUsedTypes();
                break;
            case "UsedFluidBytes":
            case "TotalFluidBytes":
                total = link.getFluidTotalBytes();
                used = link.getFluidUsedBytes();
                break;
            case "UsedFluidTypes":
            case "TotalFluidTypes":
                total = link.getFluidTotalTypes();
                used = link.getFluidUsedTypes();
                break;
            default:
                return currentValue;
        }

        return total > 0 ? (used / total) * 100.0 : 0.0;
    }

    /**
     * 使用 CraftingTemplateParser 解析用户自定义模板
     */
    private String[] parseTemplateWithLink(String template, final TileEntityAdvanceCraftingLink link) {
        CraftingTemplateParser.DataProvider provider = new CraftingTemplateParser.DataProvider() {

            @Override
            public Object getValue(String variable) {
                switch (variable) {
                    case "totalCpus":
                        return link.getTotalCpus();
                    case "busyCpus":
                        return link.getBusyCpus();
                    case "cpuTotalBytes":
                        return link.getCpuTotalBytes();
                    case "cpuUsedBytes":
                        return link.getCpuUsedBytes();
                    case "totalCoProcessors":
                        return link.getTotalCoProcessors();
                    default:
                        return null;
                }
            }

            @Override
            public Object getValue(String variable, String argument) {
                // argument 即 CPU 名称（如 "CPU#1"）
                TileEntityAdvanceCraftingLink.CraftingCpuSnapshot snap = link.getCpuSnapshotByName(argument);
                if (snap == null) return null;

                switch (variable) {
                    case "busyCpus":
                        return snap.busy ? 1 : 0;
                    case "coProcessors":
                        return snap.coProcessors;
                    case "usedStorage":
                        return snap.usedStorage;
                    case "availableStorage":
                        return snap.availableStorage;
                    case "finalOutputName":
                        return snap.finalOutputName;
                    case "finalOutputAmount":
                        return snap.finalOutputAmount;
                    case "remainingItems":
                        return snap.remainingItems;
                    case "startItems":
                        return snap.startItems;
                    case "elapsedTime":
                        return snap.elapsedTime;
                    case "allowMode":
                        return snap.allowMode;
                    case "jobSource":
                        return snap.jobSource;
                    default:
                        return null;
                }
            }
        };

        List<String> lineList = CraftingTemplateParser.parse(template, provider);
        return lineList.toArray(new String[0]);
    }

}
