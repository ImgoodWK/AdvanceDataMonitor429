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
import net.minecraft.util.ChatComponentText;
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
 * Phase 3: GT machine coordinate recording and batch binding.
 */
public class ItemDataImprint extends Item {

    private static final String TAG_GT_LIST = "gtMachineList";
    private static final String TAG_SCAN_RADIUS = "scanRadius";
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
        int radius = getScanRadius(stack);
        list.add(EnumChatFormatting.GOLD + "GT Scan Radius: " + radius);
        NBTTagList gtList = getGtMachineList(stack);
        if (gtList != null && gtList.tagCount() > 0) {
            list.add(EnumChatFormatting.GREEN + "GT Machines recorded: " + gtList.tagCount());
        }
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
                    NBTTagList gtList = getGtMachineList(stack);
                    if (gtList != null && gtList.tagCount() > 0) {
                        bindBatchToMonitor(stack, player, (TileEntityAdvanceDataMonitor) te);
                    } else if (stack.hasTagCompound() && stack.getTagCompound()
                        .hasKey("boundPos")) {
                            saveBlockData(stack, world, x, y, z, player);
                        } else {
                            player.addChatMessage(
                                new ChatComponentText(
                                    EnumChatFormatting.RED
                                        + "映录器中没有记录数据。请先 shift+右键一个方块记录其坐标，或 shift+右键 GT 机器记录机器坐标。"));
                        }
                } else {
                    saveBlockData(stack, world, x, y, z, player);
                }
            }
            return true;
        }
        return false;
    }

    // ======================== Shift+RightClick in air ========================

    private void handleShiftRightClick(ItemStack stack, World world, EntityPlayer player) {
        // Shift+right-click in air: batch scan GT machines
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
                        // Only record "host" machines (not GT pipe/cable covers)
                        if (gt.getMetaTileEntity() != null) {
                            // Check if not duplicate
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

        // Merge into NBT
        if (scanned.isEmpty()) {
            player.addChatMessage(
                new ChatComponentText(
                    "\u00a7e\u672a\u5728\u534a\u5f84 " + radius + " \u683c\u5185\u53d1\u73b0GT\u673a\u5668\u3002"));
            return;
        }

        mergeGtMachines(stack, dim, scanned);
        player.addChatMessage(
            new ChatComponentText(
                "\u00a7a\u5df2\u626b\u63cf\u5e76\u8bb0\u5f55 " + scanned.size()
                    + " \u53f0GT\u673a\u5668 (\u534a\u5f84 "
                    + radius
                    + " \u683c)\u3002"));
    }

    // ======================== GT Machine Recording ========================

    private void recordGtMachine(ItemStack stack, World world, int x, int y, int z, EntityPlayer player) {
        int dim = world.provider.dimensionId;
        NBTTagList list = getGtMachineList(stack);

        // Check duplicate
        if (list != null) {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                if (entry.getInteger("dim") == dim && entry.getInteger("x") == x
                    && entry.getInteger("y") == y
                    && entry.getInteger("z") == z) {
                    player.addChatMessage(
                        new ChatComponentText(
                            "\u00a7c\u8be5GT\u673a\u5668\u5df2\u5728\u8bb0\u5f55\u5217\u8868\u4e2d\u3002"));
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
            new ChatComponentText(
                "\u00a7a\u5df2\u8bb0\u5f55GT\u673a\u5668: " + name + " @ (" + x + "," + y + "," + z + ")"));
    }

    // ======================== Batch Bind to Monitor ========================

    private void bindBatchToMonitor(ItemStack stack, EntityPlayer player, TileEntityAdvanceDataMonitor monitor) {
        NBTTagList gtList = getGtMachineList(stack);
        if (gtList == null || gtList.tagCount() == 0) {
            player.addChatMessage(
                new ChatComponentText(
                    "\u00a7c\u6570\u636e\u6620\u5f55\u5668\u4e2d\u6ca1\u6709\u8bb0\u5f55GT\u673a\u5668\u5750\u6807\u3002\u8bf7\u5148shift+\u53f3\u952eGT\u673a\u5668\u6216shift+\u53f3\u952e\u7a7a\u6c14\u626b\u63cf\u3002"));
            return;
        }

        int dim = player.worldObj.provider.dimensionId;
        List<BlockPos> positions = new ArrayList<BlockPos>();
        for (int i = 0; i < gtList.tagCount(); i++) {
            NBTTagCompound entry = gtList.getCompoundTagAt(i);
            // Only bind machines in the same dimension
            if (entry.getInteger("dim") == dim) {
                positions.add(new BlockPos(entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z")));
            }
        }

        if (positions.isEmpty()) {
            player.addChatMessage(
                new ChatComponentText(
                    "\u00a7c\u6620\u5f55\u5668\u4e2d\u7684GT\u673a\u5668\u4e0e\u663e\u793a\u5668\u4e0d\u5728\u540c\u4e00\u7ef4\u5ea6\u3002"));
            return;
        }

        GtMachineBinding.addMachinesBatch(monitor, dim, positions);
        player.addChatMessage(
            new ChatComponentText(
                "\u00a7a\u5df2\u5c06 " + positions.size()
                    + " \u53f0GT\u673a\u5668\u7ed1\u5b9a\u5230\u6570\u636e\u663e\u793a\u5668\u3002"));
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

    private static void mergeGtMachines(ItemStack stack, int dim, List<BlockPos> newPositions) {
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
            if (nbt != null && nbt.hasKey("boundPos")) {
                NBTTagCompound boundPosTag = nbt.getCompoundTag("boundPos");
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
                        new ChatComponentText(
                            "\u00a7c\u8be5\u5750\u6807 (" + newCoordStr
                                + ") \u5df2\u5b58\u5728\u4e8e\u6570\u636e\u663e\u793a\u5668\u7684\u6570\u636e\u4e2d\uff0c\u8df3\u8fc7\u6dfb\u52a0\u3002"));
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
                        new ChatComponentText(
                            "\u00a7c\u9ad8\u7ea7\u6570\u636e\u76d1\u89c6\u5668\u7ed1\u5b9a\u69fd\u4f4d\u5df2\u6ee1\uff08\u6700\u591a36\u6761\uff09\uff0c\u65e0\u6cd5\u6dfb\u52a0\u65b0\u6570\u636e\u3002"));
                    return;
                }

                NBTTagCompound newData = new NBTTagCompound();
                newData.setString("XYZ", newCoordStr);
                newData.setInteger("interval", 20);
                String blockName = nbt.hasKey("boundBlock") ? formatBlockName(nbt.getString("boundBlock"))
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
                    new ChatComponentText(
                        "\u00a7a\u5df2\u5c06\u5750\u6807 (" + newCoordStr
                            + ") \u6dfb\u52a0\u5230\u9ad8\u7ea7\u6570\u636e\u663e\u793a\u5668\uff08\u7d22\u5f15 "
                            + nextIndex
                            + "\uff09"));
                return;
            } else {
                player.addChatMessage(
                    new ChatComponentText(
                        "\u00a7c\u6570\u636e\u6620\u5f55\u5668\u5c1a\u672a\u6620\u5f55\u4efb\u4f55\u5750\u6807\u3002\u8bf7\u5148 shift+\u53f3\u952e \u4e00\u4e2a\u65b9\u5757\u6620\u5f55\u5176\u6570\u636e\uff0c\u518d shift+\u53f3\u952e \u9ad8\u7ea7\u6570\u636e\u663e\u793a\u5668\u6765\u6dfb\u52a0\u3002"));
                return;
            }
        }

        if (nbt != null && nbt.hasKey("mName")) {
            nbt.removeTag("mName");
        }

        if (nbt != null && te instanceof IGregTechTileEntity) {
            nbt.setString("mName", ((IGregTechTileEntity) te).getInventoryName());
        }

        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger("x", x);
        posTag.setInteger("y", y);
        posTag.setInteger("z", z);
        nbt.setTag("boundPos", posTag);

        nbt.setString("boundBlock", Block.blockRegistry.getNameForObject(block));
        nbt.setInteger("boundMeta", meta);

        if (te != null && !(te instanceof TileEntityAdvanceDataMonitor)) {
            NBTTagCompound teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            nbt.setTag("boundTE", teNbt);
            player.addChatMessage(
                new ChatComponentText("\u00a7a\u5df2\u6210\u529f\u6620\u5f55\u65b9\u5757\u6570\u636e!"));
        } else if (te instanceof TileEntityAdvanceDataMonitor) {
            nbt.removeTag("boundTE");
        } else {
            nbt.removeTag("boundTE");
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

    private void clearNBTData(ItemStack stack) {
        if (stack.getTagCompound() != null) {
            stack.getTagCompound()
                .removeTag("boundPos");
            stack.getTagCompound()
                .removeTag("boundBlock");
            stack.getTagCompound()
                .removeTag("boundMeta");
            stack.getTagCompound()
                .removeTag("boundTE");
            // Also clear GT list
            stack.getTagCompound()
                .removeTag(TAG_GT_LIST);
        }
    }

    @SideOnly(Side.CLIENT)
    private void openNbtGui(ItemStack stack, EntityPlayer player) {
        if (!stack.hasTagCompound()) {
            player.addChatMessage(
                new ChatComponentText("\u00a7c\u7269\u54c1\u672a\u5b58\u50a8\u4efb\u4f55\u6570\u636e!"));
            return;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.hasKey("boundPos")) {
            // If there's no boundPos but there are GT machines, show count
            NBTTagList gtList = getGtMachineList(stack);
            if (gtList != null && gtList.tagCount() > 0) {
                player.addChatMessage(
                    new ChatComponentText(
                        "\u00a7b\u5df2\u8bb0\u5f55 " + gtList.tagCount()
                            + " \u53f0GT\u673a\u5668\u3002shift+\u53f3\u952e\u6570\u636e\u663e\u793a\u5668\u53ef\u6279\u91cf\u7ed1\u5b9a\u3002"));
                return;
            }
            player
                .addChatMessage(new ChatComponentText("\u00a7c\u672a\u6620\u5f55\u65b9\u5757\u7684 NBT \u6570\u636e!"));
            return;
        }

        if (nbt.hasKey("boundTE")) {
            NBTTagCompound tileNBT = nbt.getCompoundTag("boundTE");
            ItemClientGui.openNbtViewerGui(tileNBT);
        } else {
            player.addChatMessage(
                new ChatComponentText("\u00a7c\u6620\u5f55\u7684\u65b9\u5757\u6ca1\u6709 NBT \u6570\u636e!"));
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
