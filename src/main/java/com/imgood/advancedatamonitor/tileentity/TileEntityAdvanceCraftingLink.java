package com.imgood.advancedatamonitor.tileentity;

import static com.imgood.advancedatamonitor.AdvanceDataMonitor.LOG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.advancedatamonitor.utils.ContentsHelper;

import appeng.api.config.CraftingAllow;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkTile;

public class TileEntityAdvanceCraftingLink extends AENetworkTile {

    // ---------- 监控指标 ----------
    private int totalCpus = 0;
    private int busyCpus = 0;
    private int cpuTotalBytes = 0;
    private int cpuUsedBytes = 0;
    private int totalCoProcessors = 0;
    private List<CraftingCpuSnapshot> cpuSnapshots = new ArrayList<>();

    public TileEntityAdvanceCraftingLink() {
        this.getProxy()
            .setFlags(new GridFlags[] { GridFlags.REQUIRE_CHANNEL });
    }

    // ================= AE 网络集成 =================
    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.SMART;
    }

    /**
     * 核心数据更新 —— 从网络中获取最新合成 CPU 统计
     */
    public void updateCraftingStats() {

        IGrid grid;
        try {
            grid = this.getProxy()
                .getGrid();
        } catch (GridAccessException e) {
            LOG.error("Crafting Monitor: Failed to access grid", e);
            return;
        }
        if (grid == null) return;

        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) return;

        Collection<ICraftingCPU> cpus = craftingGrid.getCpus();
        if (cpus == null || cpus.isEmpty()) {
            resetStats();
            return;
        }

        int tCpus = 0, bCpus = 0, cproc = 0;
        long totalBytes = 0, usedBytes = 0;
        List<CraftingCpuSnapshot> snapshots = new ArrayList<>();

        for (ICraftingCPU cpu : cpus) {
            if (cpu == null) continue;
            tCpus++;
            boolean busy = cpu.isBusy();
            if (busy) bCpus++;
            int coProc = cpu.getCoProcessors();
            cproc += coProc;

            long available = cpu.getAvailableStorage();
            long used = cpu.getUsedStorage();
            totalBytes += (available + used);
            usedBytes += used;

            // 最终产物信息
            String finalOutputName = null;
            long finalOutputAmount = 0;
            List<String> finalOutputList = new ArrayList<>();
            IAEItemStack finalOutput = cpu.getFinalOutput();
            if (finalOutput != null) {
                try {
                    finalOutputName = finalOutput.getItemStack()
                        .getDisplayName();
                } catch (Exception e) {
                    finalOutputName = finalOutput.toString();
                }
                finalOutputAmount = finalOutput.getStackSize();
                finalOutputList.add(finalOutputName + " x" + finalOutputAmount);
            }

            // 配方输入因 API 限制暂不可用，留空
            List<String> inputList = new ArrayList<>();

            long remainingItems = cpu.getRemainingItemCount();
            long startItems = cpu.getStartItemCount();
            long elapsedTime = cpu.getElapsedTime();
            CraftingAllow allowMode = cpu.getCraftingAllowMode();
            String allowModeStr = allowMode != null ? allowMode.name() : "ALLOW_ALL";
            BaseActionSource source = cpu.getCurrentJobSource();
            String jobSourceStr = source != null ? source.toString() : "";

            snapshots.add(
                new CraftingCpuSnapshot(
                    cpu.getName(),
                    busy,
                    available,
                    used,
                    coProc,
                    finalOutputName,
                    finalOutputAmount,
                    remainingItems,
                    startItems,
                    elapsedTime,
                    allowModeStr,
                    jobSourceStr,
                    inputList.toArray(new String[0]),
                    finalOutputList.toArray(new String[0])));
        }

        this.totalCpus = tCpus;
        this.busyCpus = bCpus;
        this.cpuTotalBytes = (int) totalBytes;
        this.cpuUsedBytes = (int) usedBytes;
        this.totalCoProcessors = cproc;
        this.cpuSnapshots = snapshots;

        markDirty();
    }

    private void resetStats() {
        this.totalCpus = 0;
        this.busyCpus = 0;
        this.cpuTotalBytes = 0;
        this.cpuUsedBytes = 0;
        this.totalCoProcessors = 0;
        this.cpuSnapshots.clear();
    }

    // ================= 事件驱动更新 =================
    @MENetworkEventSubscribe
    public void onCraftingCpuChange(MENetworkCraftingCpuChange event) {
        updateCraftingStats();
    }

    // ================= NBT 持久化 =================
    @Override
    public void writeToNBT_AENetwork(NBTTagCompound tag) {
        tag.setInteger("TotalCpus", totalCpus);
        tag.setInteger("BusyCpus", busyCpus);
        tag.setInteger("CpuTotalBytes", cpuTotalBytes);
        tag.setInteger("CpuUsedBytes", cpuUsedBytes);
        tag.setInteger("TotalCoProcessors", totalCoProcessors);

        NBTTagList snapshotList = new NBTTagList();
        for (CraftingCpuSnapshot snap : cpuSnapshots) {
            NBTTagCompound snapTag = new NBTTagCompound();
            snapTag.setString("Name", snap.name != null ? snap.name : "");
            snapTag.setBoolean("Busy", snap.busy);
            snapTag.setLong("AvailableStorage", snap.availableStorage);
            snapTag.setLong("UsedStorage", snap.usedStorage);
            snapTag.setInteger("CoProcessors", snap.coProcessors);
            snapTag.setString("FinalOutputName", snap.finalOutputName != null ? snap.finalOutputName : "");
            snapTag.setLong("FinalOutputAmount", snap.finalOutputAmount);
            snapTag.setLong("RemainingItems", snap.remainingItems);
            snapTag.setLong("StartItems", snap.startItems);
            snapTag.setLong("ElapsedTime", snap.elapsedTime);
            snapTag.setString("AllowMode", snap.allowMode);
            snapTag.setString("JobSource", snap.jobSource != null ? snap.jobSource : "");

            // 输入数组
            NBTTagList inputList = new NBTTagList();
            for (String s : snap.inputDisplayNames) {
                inputList.appendTag(new NBTTagString(s));
            }
            snapTag.setTag("Inputs", inputList);

            // 最终产物数组
            NBTTagList outputList = new NBTTagList();
            for (String s : snap.finalOutputDisplayNames) {
                outputList.appendTag(new NBTTagString(s));
            }
            snapTag.setTag("FinalOutputs", outputList);

            snapshotList.appendTag(snapTag);
        }
        tag.setTag("CpuSnapshots", snapshotList);
    }

    @Override
    public void readFromNBT_AENetwork(NBTTagCompound tag) {
        this.totalCpus = tag.getInteger("TotalCpus");
        this.busyCpus = tag.getInteger("BusyCpus");
        this.cpuTotalBytes = tag.getInteger("CpuTotalBytes");
        this.cpuUsedBytes = tag.getInteger("CpuUsedBytes");
        this.totalCoProcessors = tag.getInteger("TotalCoProcessors");

        this.cpuSnapshots.clear();
        NBTTagList snapshotList = tag.getTagList("CpuSnapshots", 10);
        for (int i = 0; i < snapshotList.tagCount(); i++) {
            NBTTagCompound snapTag = snapshotList.getCompoundTagAt(i);

            NBTTagList inputTags = snapTag.getTagList("Inputs", 8);
            String[] inputs = new String[inputTags.tagCount()];
            for (int j = 0; j < inputTags.tagCount(); j++) {
                inputs[j] = inputTags.getStringTagAt(j);
            }

            NBTTagList outputTags = snapTag.getTagList("FinalOutputs", 8);
            String[] outputs = new String[outputTags.tagCount()];
            for (int j = 0; j < outputTags.tagCount(); j++) {
                outputs[j] = outputTags.getStringTagAt(j);
            }

            CraftingCpuSnapshot snap = new CraftingCpuSnapshot(
                snapTag.getString("Name"),
                snapTag.getBoolean("Busy"),
                snapTag.getLong("AvailableStorage"),
                snapTag.getLong("UsedStorage"),
                snapTag.getInteger("CoProcessors"),
                snapTag.getString("FinalOutputName"),
                snapTag.getLong("FinalOutputAmount"),
                snapTag.getLong("RemainingItems"),
                snapTag.getLong("StartItems"),
                snapTag.getLong("ElapsedTime"),
                snapTag.getString("AllowMode"),
                snapTag.getString("JobSource"),
                inputs,
                outputs);
            this.cpuSnapshots.add(snap);
        }
    }

    // ================= 客户端同步 =================
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("TotalCpus", totalCpus);
        data.setInteger("BusyCpus", busyCpus);
        data.setInteger("CpuTotalBytes", cpuTotalBytes);
        data.setInteger("CpuUsedBytes", cpuUsedBytes);
        data.setInteger("TotalCoProcessors", totalCoProcessors);

        NBTTagList snapshotList = new NBTTagList();
        for (CraftingCpuSnapshot snap : cpuSnapshots) {
            NBTTagCompound snapTag = new NBTTagCompound();
            snapTag.setString("Name", snap.name != null ? snap.name : "");
            snapTag.setBoolean("Busy", snap.busy);
            snapTag.setLong("AvailableStorage", snap.availableStorage);
            snapTag.setLong("UsedStorage", snap.usedStorage);
            snapTag.setInteger("CoProcessors", snap.coProcessors);
            snapTag.setString("FinalOutputName", snap.finalOutputName != null ? snap.finalOutputName : "");
            snapTag.setLong("FinalOutputAmount", snap.finalOutputAmount);
            snapTag.setLong("RemainingItems", snap.remainingItems);
            snapTag.setLong("StartItems", snap.startItems);
            snapTag.setLong("ElapsedTime", snap.elapsedTime);
            snapTag.setString("AllowMode", snap.allowMode);
            snapTag.setString("JobSource", snap.jobSource != null ? snap.jobSource : "");

            NBTTagList inputList = new NBTTagList();
            for (String s : snap.inputDisplayNames) {
                inputList.appendTag(new NBTTagString(s));
            }
            snapTag.setTag("Inputs", inputList);

            NBTTagList outputList = new NBTTagList();
            for (String s : snap.finalOutputDisplayNames) {
                outputList.appendTag(new NBTTagString(s));
            }
            snapTag.setTag("FinalOutputs", outputList);

            snapshotList.appendTag(snapTag);
        }
        data.setTag("CpuSnapshots", snapshotList);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, data);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound data = pkt.func_148857_g();
        this.totalCpus = data.getInteger("TotalCpus");
        this.busyCpus = data.getInteger("BusyCpus");
        this.cpuTotalBytes = data.getInteger("CpuTotalBytes");
        this.cpuUsedBytes = data.getInteger("CpuUsedBytes");
        this.totalCoProcessors = data.getInteger("TotalCoProcessors");

        this.cpuSnapshots.clear();
        NBTTagList snapshotList = data.getTagList("CpuSnapshots", 10);
        for (int i = 0; i < snapshotList.tagCount(); i++) {
            NBTTagCompound snapTag = snapshotList.getCompoundTagAt(i);

            NBTTagList inputTags = snapTag.getTagList("Inputs", 8);
            String[] inputs = new String[inputTags.tagCount()];
            for (int j = 0; j < inputTags.tagCount(); j++) {
                inputs[j] = inputTags.getStringTagAt(j);
            }

            NBTTagList outputTags = snapTag.getTagList("FinalOutputs", 8);
            String[] outputs = new String[outputTags.tagCount()];
            for (int j = 0; j < outputTags.tagCount(); j++) {
                outputs[j] = outputTags.getStringTagAt(j);
            }

            CraftingCpuSnapshot snap = new CraftingCpuSnapshot(
                snapTag.getString("Name"),
                snapTag.getBoolean("Busy"),
                snapTag.getLong("AvailableStorage"),
                snapTag.getLong("UsedStorage"),
                snapTag.getInteger("CoProcessors"),
                snapTag.getString("FinalOutputName"),
                snapTag.getLong("FinalOutputAmount"),
                snapTag.getLong("RemainingItems"),
                snapTag.getLong("StartItems"),
                snapTag.getLong("ElapsedTime"),
                snapTag.getString("AllowMode"),
                snapTag.getString("JobSource"),
                inputs,
                outputs);
            this.cpuSnapshots.add(snap);
        }
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    // ================= 公共 Getter =================
    public int getTotalCpus() {
        return totalCpus;
    }

    public int getBusyCpus() {
        return busyCpus;
    }

    public int getCpuTotalBytes() {
        return cpuTotalBytes;
    }

    public int getCpuUsedBytes() {
        return cpuUsedBytes;
    }

    public int getTotalCoProcessors() {
        return totalCoProcessors;
    }

    public List<CraftingCpuSnapshot> getCpuSnapshots() {
        return new ArrayList<>(cpuSnapshots);
    }

    public long getCpuUsedStorage(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.usedStorage : -1;
    }

    public long getCpuUsedStorage(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.usedStorage : -1;
    }

    public long getCpuAvailableStorage(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.availableStorage : -1;
    }

    public long getCpuAvailableStorage(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.availableStorage : -1;
    }

    public int getCpuCoProcessors(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.coProcessors : -1;
    }

    public int getCpuCoProcessors(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.coProcessors : -1;
    }

    public boolean isCpuBusy(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null && snap.busy;
    }

    public boolean isCpuBusy(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null && snap.busy;
    }

    @Nullable
    public String getCpuJobSource(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.jobSource : null;
    }

    @Nullable
    public String getCpuJobSource(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.jobSource : null;
    }

    @Nullable
    public String getCpuFinalOutputDisplay(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        if (snap == null || snap.finalOutputName == null || snap.finalOutputName.isEmpty()) return null;
        return snap.finalOutputName + " x" + snap.finalOutputAmount;
    }

    @Nullable
    public String getCpuFinalOutputDisplay(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        if (snap == null || snap.finalOutputName == null || snap.finalOutputName.isEmpty()) return null;
        return snap.finalOutputName + " x" + snap.finalOutputAmount;
    }

    public String[] getCpuFinalOutput(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.finalOutputDisplayNames : new String[0];
    }

    public String[] getCpuFinalOutput(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.finalOutputDisplayNames : new String[0];
    }

    /**
     * 获取配方输入数组（当前版本因 API 限制始终为空，保留接口以备后续扩展）
     */
    public String[] getCpuRecipeInputs(String cpuName) {
        CraftingCpuSnapshot snap = getCpuSnapshotByName(cpuName);
        return snap != null ? snap.inputDisplayNames : new String[0];
    }

    public String[] getCpuRecipeInputs(int index) {
        CraftingCpuSnapshot snap = getCpuSnapshotByIndex(index);
        return snap != null ? snap.inputDisplayNames : new String[0];
    }

    @Nullable
    public CraftingCpuSnapshot getCpuSnapshotByName(String cpuName) {
        for (CraftingCpuSnapshot snap : cpuSnapshots) {
            if (snap.name.equals(cpuName)) return snap;
        }
        return null;
    }

    @Nullable
    public CraftingCpuSnapshot getCpuSnapshotByIndex(int index) {
        if (index >= 0 && index < cpuSnapshots.size()) {
            return cpuSnapshots.get(index);
        }
        return null;
    }

    public String getStatsInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§bTotal CPUs: §b%d§r \n", totalCpus));
        sb.append(String.format("§bBusy: §r§c%d§r  §bIdle: §r§a%d§r\n", busyCpus, totalCpus - busyCpus));
        sb.append(String.format("§bCPU Storage:§r §6%d§r / §6%d§r Bytes\n", cpuUsedBytes, cpuTotalBytes));
        sb.append(String.format("§bCo-Processors: §r§d%d§r\n", totalCoProcessors));

        if (!cpuSnapshots.isEmpty()) {
            sb.append("§e--- CPU Details ---§r\n");
            for (int i = 0; i < cpuSnapshots.size(); i++) {
                CraftingCpuSnapshot snap = cpuSnapshots.get(i);
                String cpuName = snap.name.isEmpty() ? "CPU#" + (i + 1) : snap.name;
                sb.append(String.format("§b§l[%s]§r Busy: %s", cpuName, snap.busy ? "§cYes§r" : "§aNo§r"));
                if (snap.busy && snap.finalOutputName != null && !snap.finalOutputName.isEmpty()) {
                    sb.append(String.format(" Crafting: §6%s x%d§r", snap.finalOutputName, snap.finalOutputAmount));
                    if (snap.startItems > 0) {
                        long progress = snap.startItems - snap.remainingItems;
                        sb.append(String.format(" Progress: %d/%d", progress, snap.startItems));
                    }
                    if (snap.elapsedTime > 0) {
                        sb.append(String.format(" Elapsed: %s", ContentsHelper.formatDuration(snap.elapsedTime)));
                    }
                }
                sb.append(String.format(" Mode: %s", snap.allowMode));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ================= 内部数据类: CPU 快照 =================
    public static class CraftingCpuSnapshot {

        public final String name;
        public final boolean busy;
        public final long availableStorage;
        public final long usedStorage;
        public final int coProcessors;
        public final String finalOutputName;
        public final long finalOutputAmount;
        public final long remainingItems;
        public final long startItems;
        public final long elapsedTime;
        public final String allowMode;
        public final String jobSource;
        public final String[] inputDisplayNames;
        public final String[] finalOutputDisplayNames;

        public CraftingCpuSnapshot(String name, boolean busy, long availableStorage, long usedStorage, int coProcessors,
            String finalOutputName, long finalOutputAmount, long remainingItems, long startItems, long elapsedTime,
            String allowMode, String jobSource) {
            this(
                name,
                busy,
                availableStorage,
                usedStorage,
                coProcessors,
                finalOutputName,
                finalOutputAmount,
                remainingItems,
                startItems,
                elapsedTime,
                allowMode,
                jobSource,
                new String[0],
                new String[0]);
        }

        public CraftingCpuSnapshot(String name, boolean busy, long availableStorage, long usedStorage, int coProcessors,
            String finalOutputName, long finalOutputAmount, long remainingItems, long startItems, long elapsedTime,
            String allowMode, String jobSource, String[] inputDisplayNames, String[] finalOutputDisplayNames) {
            this.name = name;
            this.busy = busy;
            this.availableStorage = availableStorage;
            this.usedStorage = usedStorage;
            this.coProcessors = coProcessors;
            this.finalOutputName = finalOutputName;
            this.finalOutputAmount = finalOutputAmount;
            this.remainingItems = remainingItems;
            this.startItems = startItems;
            this.elapsedTime = elapsedTime;
            this.allowMode = allowMode;
            this.jobSource = jobSource;
            this.inputDisplayNames = inputDisplayNames != null ? inputDisplayNames : new String[0];
            this.finalOutputDisplayNames = finalOutputDisplayNames != null ? finalOutputDisplayNames : new String[0];
        }
    }
}
