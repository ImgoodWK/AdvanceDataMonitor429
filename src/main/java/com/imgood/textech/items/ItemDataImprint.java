package com.imgood.textech.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.client.ItemClientGui;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.textech.utils.BlockPos;
import com.imgood.textech.webae.gt.GtMachineBinding;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Display names / 显示名称:
 * - EN: Data Imprint Tool
 * - ZH: 数据映录器
 * Lang keys: item.dataImprint.name
 *
 * Captures TileEntity NBT snapshots and binds them to Data Monitors.
 * Block imprint (boundPos) and GT machine list (gtMachineList) are mutually exclusive.
 */
public class ItemDataImprint extends Item {

    private static final String TAG_GT_LIST = "gtMachineList";
    private static final String TAG_SCAN_RADIUS = "scanRadius";
    private static final String TAG_BOUND_POS = "boundPos";
    private static final String TAG_BOUND_BLOCK = "boundBlock";
    private static final String TAG_BOUND_META = "boundMeta";
    private static final String TAG_BOUND_TE = "boundTE";
    private static final String TAG_M_NAME = "mName";
    private static final int DEFAULT_SCAN_RADIUS = 16;
    private static final int[] SCAN_RADII = { 8, 16, 32, 64 };

    public ItemDataImprint() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("adm.tooltip.data_imprint.story"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_imprint.note"));
        list.add(
            EnumChatFormatting.GOLD + StatCollector
                .translateToLocalFormatted("adm.tooltip.data_imprint.scan_radius", getScanRadius(stack)));
        if (hasGtMachines(stack)) {
            list.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted(
                    "adm.tooltip.data_imprint.gt_count",
                    Integer.valueOf(getGtMachineList(stack).tagCount())));
        } else if (hasBlockImprint(stack)) {
            NBTTagCompound pos = stack.getTagCompound()
                .getCompoundTag(TAG_BOUND_POS);
            list.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted(
                    "adm.tooltip.data_imprint.block_bound",
                    Integer.valueOf(pos.getInteger("x")),
                    Integer.valueOf(pos.getInteger("y")),
                    Integer.valueOf(pos.getInteger("z"))));
        } else {
            list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.tooltip.data_imprint.empty"));
        }
        list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.tooltip.data_imprint.exclusive"));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                handleShiftRightClick(stack, world, player);
            }
        } else {
            if (world.isRemote) {
                openNbtGui(stack, player);
            }
        }
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof IGregTechTileEntity) {
                    recordGtMachine(stack, world, x, y, z, player);
                } else if (te instanceof TileEntityAdvanceDataMonitor) {
                    bindToMonitor(stack, world, x, y, z, player, (TileEntityAdvanceDataMonitor) te);
                } else {
                    saveBlockData(stack, world, x, y, z, player);
                }
            }
            return true;
        }
        return false;
    }

    // ======================== Mutual exclusivity ========================

    public static boolean hasBlockImprint(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey(TAG_BOUND_POS);
    }

    public static boolean hasGtMachines(ItemStack stack) {
        NBTTagList list = getGtMachineList(stack);
        return list != null && list.tagCount() > 0;
    }

    public static boolean hasAnyPayload(ItemStack stack) {
        return hasBlockImprint(stack) || hasGtMachines(stack);
    }

    private static void clearBlockImprint(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound nbt = stack.getTagCompound();
        nbt.removeTag(TAG_BOUND_POS);
        nbt.removeTag(TAG_BOUND_BLOCK);
        nbt.removeTag(TAG_BOUND_META);
        nbt.removeTag(TAG_BOUND_TE);
        nbt.removeTag(TAG_M_NAME);
    }

    private static void clearGtList(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return;
        }
        stack.getTagCompound()
            .removeTag(TAG_GT_LIST);
    }

    private void clearNBTData(ItemStack stack) {
        clearBlockImprint(stack);
        clearGtList(stack);
    }

    /** Before writing a block imprint: drop GT list if present. */
    private void ensureExclusiveForBlock(ItemStack stack, EntityPlayer player) {
        if (hasGtMachines(stack)) {
            clearGtList(stack);
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.cleared_gt_for_block"));
        }
    }

    /** Before writing GT machines: drop block imprint if present. */
    private void ensureExclusiveForGt(ItemStack stack, EntityPlayer player) {
        if (hasBlockImprint(stack)) {
            clearBlockImprint(stack);
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.cleared_block_for_gt"));
        }
    }

    /**
     * Legacy items may hold both payloads. Prefer GT list, clear block imprint, notify once.
     *
     * @return true if a conflicting block imprint was removed
     */
    private boolean normalizeLegacyDualPayload(ItemStack stack, EntityPlayer player) {
        if (hasGtMachines(stack) && hasBlockImprint(stack)) {
            clearBlockImprint(stack);
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.legacy_cleared_block"));
            return true;
        }
        return false;
    }

    private void bindToMonitor(ItemStack stack, World world, int x, int y, int z, EntityPlayer player,
        TileEntityAdvanceDataMonitor monitor) {
        normalizeLegacyDualPayload(stack, player);
        if (hasGtMachines(stack)) {
            bindBatchToMonitor(stack, player, monitor);
        } else if (hasBlockImprint(stack)) {
            saveBlockData(stack, world, x, y, z, player);
        } else {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_payload"));
        }
    }

    // ======================== Shift+RightClick in air ========================

    private void handleShiftRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (hasAnyPayload(stack)) {
            clearNBTData(stack);
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.cleared"));
            return;
        }
        scanGtMachines(stack, world, player);
    }

    private void scanGtMachines(ItemStack stack, World world, EntityPlayer player) {
        int radius = getScanRadius(stack);
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        int dim = world.provider.dimensionId;

        NBTTagList existingList = getGtMachineList(stack);
        List<BlockPos> scanned = new ArrayList<BlockPos>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int tx = px + dx;
                    int ty = py + dy;
                    int tz = pz + dz;
                    if (ty < 0 || ty > 255) continue;
                    if (!world.blockExists(tx, ty, tz)) continue;
                    TileEntity te = world.getTileEntity(tx, ty, tz);
                    if (te instanceof IGregTechTileEntity) {
                        IGregTechTileEntity gt = (IGregTechTileEntity) te;
                        if (gt.getMetaTileEntity() != null) {
                            boolean duplicate = false;
                            if (existingList != null) {
                                for (int i = 0; i < existingList.tagCount(); i++) {
                                    NBTTagCompound entry = existingList.getCompoundTagAt(i);
                                    if (entry.getInteger("dim") == dim && entry.getInteger("x") == tx
                                        && entry.getInteger("y") == ty
                                        && entry.getInteger("z") == tz) {
                                        duplicate = true;
                                        break;
                                    }
                                }
                            }
                            if (!duplicate) {
                                for (BlockPos bp : scanned) {
                                    if (bp.getX() == tx && bp.getY() == ty && bp.getZ() == tz) {
                                        duplicate = true;
                                        break;
                                    }
                                }
                            }
                            if (!duplicate) {
                                scanned.add(new BlockPos(tx, ty, tz));
                            }
                        }
                    }
                }
            }
        }

        if (scanned.isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.scan_none", Integer.valueOf(radius)));
            return;
        }

        mergeGtMachines(stack, dim, scanned, player);
        player.addChatMessage(
            new ChatComponentTranslation(
                "adm.data_imprint.scan_done",
                Integer.valueOf(scanned.size()),
                Integer.valueOf(radius)));
    }

    // ======================== GT Machine Recording ========================

    private void recordGtMachine(ItemStack stack, World world, int x, int y, int z, EntityPlayer player) {
        ensureExclusiveForGt(stack, player);

        int dim = world.provider.dimensionId;
        NBTTagList list = getGtMachineList(stack);

        if (list != null) {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                if (entry.getInteger("dim") == dim && entry.getInteger("x") == x
                    && entry.getInteger("y") == y
                    && entry.getInteger("z") == z) {
                    player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.gt_duplicate"));
                    return;
                }
            }
        }

        if (list == null) {
            list = new NBTTagList();
        }

        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("dim", dim);
        entry.setInteger("x", x);
        entry.setInteger("y", y);
        entry.setInteger("z", z);
        list.appendTag(entry);

        ensureNbt(stack).setTag(TAG_GT_LIST, list);

        IGregTechTileEntity gt = (IGregTechTileEntity) world.getTileEntity(x, y, z);
        String name = "GT Machine";
        try {
            name = gt.getInventoryName();
        } catch (Throwable ignored) {}

        player.addChatMessage(
            new ChatComponentTranslation(
                "adm.data_imprint.gt_recorded",
                name,
                Integer.valueOf(x),
                Integer.valueOf(y),
                Integer.valueOf(z)));
    }

    // ======================== Batch Bind to Monitor ========================

    private void bindBatchToMonitor(ItemStack stack, EntityPlayer player, TileEntityAdvanceDataMonitor monitor) {
        NBTTagList gtList = getGtMachineList(stack);
        if (gtList == null || gtList.tagCount() == 0) {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_gt"));
            return;
        }

        int dim = player.worldObj.provider.dimensionId;
        List<BlockPos> positions = new ArrayList<BlockPos>();
        for (int i = 0; i < gtList.tagCount(); i++) {
            NBTTagCompound entry = gtList.getCompoundTagAt(i);
            if (entry.getInteger("dim") == dim) {
                positions.add(new BlockPos(entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z")));
            }
        }

        if (positions.isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.gt_wrong_dim"));
            return;
        }

        GtMachineBinding.addMachinesBatch(monitor, dim, positions);
        player.addChatMessage(
            new ChatComponentTranslation("adm.data_imprint.gt_bound", Integer.valueOf(positions.size())));
    }

    // ======================== NBT Helpers ========================

    private static NBTTagCompound ensureNbt(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        return nbt;
    }

    public static NBTTagList getGtMachineList(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt.hasKey(TAG_GT_LIST)) {
            return nbt.getTagList(TAG_GT_LIST, 10);
        }
        return null;
    }

    public static int getScanRadius(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return DEFAULT_SCAN_RADIUS;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt.hasKey(TAG_SCAN_RADIUS)) {
            int r = nbt.getInteger(TAG_SCAN_RADIUS);
            for (int valid : SCAN_RADII) {
                if (valid == r) return r;
            }
        }
        return DEFAULT_SCAN_RADIUS;
    }

    public static void setScanRadius(ItemStack stack, int radius) {
        ensureNbt(stack).setInteger(TAG_SCAN_RADIUS, radius);
    }

    private void mergeGtMachines(ItemStack stack, int dim, List<BlockPos> newPositions, EntityPlayer player) {
        ensureExclusiveForGt(stack, player);
        NBTTagList list = getGtMachineList(stack);
        if (list == null) {
            list = new NBTTagList();
        }
        for (BlockPos pos : newPositions) {
            boolean duplicate = false;
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                if (entry.getInteger("dim") == dim && entry.getInteger("x") == pos.getX()
                    && entry.getInteger("y") == pos.getY()
                    && entry.getInteger("z") == pos.getZ()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setInteger("dim", dim);
                entry.setInteger("x", pos.getX());
                entry.setInteger("y", pos.getY());
                entry.setInteger("z", pos.getZ());
                list.appendTag(entry);
            }
        }
        ensureNbt(stack).setTag(TAG_GT_LIST, list);
    }

    // ======================== Existing Block Data Save ========================

    private void saveBlockData(ItemStack stack, World world, int x, int y, int z, EntityPlayer player) {
        Block block = world.getBlock(x, y, z);
        NBTTagCompound nbt = stack.getTagCompound();
        int meta = world.getBlockMetadata(x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);

        if (te instanceof TileEntityAdvanceDataMonitor) {
            if (nbt != null && nbt.hasKey(TAG_BOUND_POS)) {
                NBTTagCompound boundPosTag = nbt.getCompoundTag(TAG_BOUND_POS);
                int boundX = boundPosTag.getInteger("x");
                int boundY = boundPosTag.getInteger("y");
                int boundZ = boundPosTag.getInteger("z");
                String newCoordStr = boundX + "," + boundY + "," + boundZ;

                TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) te;

                int count = monitor.getDataBoundCount();
                boolean duplicate = false;
                for (int i = 0; i < count; i++) {
                    int[] existingPos = monitor.parseBoundXYZ(i);
                    if (existingPos != null) {
                        String existingStr = existingPos[0] + "," + existingPos[1] + "," + existingPos[2];
                        if (newCoordStr.equals(existingStr)) {
                            duplicate = true;
                            break;
                        }
                    }
                }

                if (duplicate) {
                    player.addChatMessage(
                        new ChatComponentTranslation("adm.data_imprint.error.coord_duplicate", newCoordStr));
                    return;
                }

                int nextIndex = 0;
                boolean found = false;
                for (int i = 0; i < TileEntityAdvanceDataMonitor.MAX_DATA_BINDINGS; i++) {
                    if (!monitor.hasDataBoundEntry(i)) {
                        nextIndex = i;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "adm.error.data_bindings_full",
                            Integer.valueOf(TileEntityAdvanceDataMonitor.MAX_DATA_BINDINGS)));
                    return;
                }

                NBTTagCompound newData = new NBTTagCompound();
                newData.setString("XYZ", newCoordStr);
                newData.setInteger("interval", 20);
                String blockName = nbt.hasKey(TAG_BOUND_BLOCK) ? formatBlockName(nbt.getString(TAG_BOUND_BLOCK))
                    : "Bound Block";
                newData.setString("displayName", blockName + " @ " + newCoordStr);
                newData.setString("lineColor", "00FFFF");
                newData.setFloat("lineWidth", 3.0f);
                newData.setFloat("scale", 0.3f);
                newData.setFloat("yOffset", -0.5f);
                newData.setFloat("xOffset", 0.0f);
                newData.setFloat("zOffset", -0.5f);
                newData.setFloat("rotationX", -30.0f);
                newData.setFloat("rotationY", 0.0f);
                newData.setFloat("rotationZ", 0.0f);
                newData.setInteger("dataLimit", 100);
                newData.setDouble("yMin", 0.0);
                newData.setDouble("yMax", 20.0);
                newData.setString("name", "dataImprint_" + nextIndex);
                newData.setTag("dataValues", new NBTTagList());
                newData.setDouble("xRange", 5);
                newData.setDouble("yRange", 3);
                newData.setString("axisLineColor", "FFFFFF");
                newData.setString("axisFontColor", "00FFFF");
                newData.setDouble("displayNameScale", 2.0);
                newData.setString("displayNameColor", "FFFFFF");
                newData.setDouble("axisFontScale", 1.0);
                newData.setBoolean("enable", true);
                newData.setBoolean("graph", true);
                newData.setDouble("graphScale", 0.3);
                newData.setDouble("graphYOffset", -0.5);
                newData.setDouble("graphXOffset", 0.0);
                newData.setDouble("graphZOffset", -0.5);

                monitor.setDisplayData(nextIndex, newData);
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "adm.data_imprint.block_bound_monitor",
                        newCoordStr,
                        Integer.valueOf(nextIndex)));
                return;
            } else {
                player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_block"));
                return;
            }
        }

        ensureExclusiveForBlock(stack, player);

        nbt = stack.getTagCompound();
        if (nbt != null && nbt.hasKey(TAG_M_NAME)) {
            nbt.removeTag(TAG_M_NAME);
        }

        if (nbt != null && te instanceof IGregTechTileEntity) {
            nbt.setString(TAG_M_NAME, ((IGregTechTileEntity) te).getInventoryName());
        }

        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger("x", x);
        posTag.setInteger("y", y);
        posTag.setInteger("z", z);
        nbt.setTag(TAG_BOUND_POS, posTag);

        nbt.setString(TAG_BOUND_BLOCK, Block.blockRegistry.getNameForObject(block));
        nbt.setInteger(TAG_BOUND_META, meta);

        if (te != null && !(te instanceof TileEntityAdvanceDataMonitor)) {
            NBTTagCompound teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            nbt.setTag(TAG_BOUND_TE, teNbt);
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.block_imprinted"));
        } else if (te instanceof TileEntityAdvanceDataMonitor) {
            nbt.removeTag(TAG_BOUND_TE);
        } else {
            nbt.removeTag(TAG_BOUND_TE);
        }
    }

    private static String formatBlockName(String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return "Block";
        }
        String[] parts = registryName.split(":");
        String name = parts.length > 1 ? parts[1] : parts[0];
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @SideOnly(Side.CLIENT)
    private void openNbtGui(ItemStack stack, EntityPlayer player) {
        if (!stack.hasTagCompound()) {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_item_data"));
            return;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.hasKey(TAG_BOUND_POS)) {
            NBTTagList gtList = getGtMachineList(stack);
            if (gtList != null && gtList.tagCount() > 0) {
                player.addChatMessage(
                    new ChatComponentTranslation("adm.data_imprint.gt_hint_bind", Integer.valueOf(gtList.tagCount())));
                return;
            }
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_block_nbt"));
            return;
        }

        if (nbt.hasKey(TAG_BOUND_TE)) {
            NBTTagCompound tileNBT = nbt.getCompoundTag(TAG_BOUND_TE);
            ItemClientGui.openNbtViewerGui(tileNBT);
        } else {
            player.addChatMessage(new ChatComponentTranslation("adm.data_imprint.error.no_te_nbt"));
        }
    }

    // ======================== Test NBT (unchanged) ========================

    public static JsonObject createTestNBT() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "TAG_Compound");

        JsonObject rootValue = new JsonObject();

        addSimpleTag(rootValue, "name", "TAG_String", "Test Item");
        addSimpleTag(rootValue, "count", "TAG_Int", 5);

        JsonObject enchants = new JsonObject();
        enchants.addProperty("type", "TAG_Compound");
        JsonObject enchantsValue = new JsonObject();

        JsonObject enchant1 = new JsonObject();
        enchant1.addProperty("type", "TAG_Compound");
        JsonObject enchant1Value = new JsonObject();
        addSimpleTag(enchant1Value, "id", "TAG_Short", 16);
        addSimpleTag(enchant1Value, "lvl", "TAG_Short", 3);
        enchant1.add("value", enchant1Value);

        JsonObject enchant2 = new JsonObject();
        enchant2.addProperty("type", "TAG_Compound");
        JsonObject enchant2Value = new JsonObject();
        addSimpleTag(enchant2Value, "id", "TAG_Short", 17);
        addSimpleTag(enchant2Value, "lvl", "TAG_Short", 2);
        enchant2.add("value", enchant2Value);

        JsonObject enchantList = new JsonObject();
        enchantList.addProperty("type", "TAG_List");
        JsonArray listContents = new JsonArray();
        listContents.add(enchant1);
        listContents.add(enchant2);
        enchantList.add("value", listContents);

        enchantsValue.add("ench", enchantList);
        enchants.add("value", enchantsValue);
        rootValue.add("enchants", enchants);

        JsonObject deepNested = createDeepNested(3);
        rootValue.add("deepNested", deepNested);

        root.add("value", rootValue);
        return root;
    }

    private static JsonObject createDeepNested(int depth) {
        JsonObject current = new JsonObject();
        current.addProperty("type", "TAG_Compound");

        JsonObject value = new JsonObject();
        addSimpleTag(value, "level", "TAG_Int", depth);

        if (depth > 0) {
            value.add("child", createDeepNested(depth - 1));
        }

        current.add("value", value);
        return current;
    }

    private static void addSimpleTag(JsonObject parent, String key, String type, Object value) {
        JsonObject tag = new JsonObject();
        tag.addProperty("type", type);

        if (value instanceof Number) {
            tag.addProperty("value", (Number) value);
        } else if (value instanceof String) {
            tag.addProperty("value", (String) value);
        } else if (value instanceof Boolean) {
            tag.addProperty("value", (Boolean) value);
        }

        parent.add(key, tag);
    }
}
