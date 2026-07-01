package com.imgood.textech.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.textech.client.ItemClientGui;
import com.imgood.textech.tileentity.TileEntityAdvanceDataMonitor;

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
 * This records and displays data; it does not weave matter like Data Loom cells.
 */
public class ItemDataImprint extends Item {

    public ItemDataImprint() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("adm.tooltip.data_imprint.story"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.tooltip.data_imprint.note"));
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                clearNBTData(stack);
                player.addChatMessage(new ChatComponentText("§a已清除所有映录数据!"));
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
                saveBlockData(stack, world, x, y, z, player);
            }
            return true;
        }
        return false;
    }

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

                if (monitor.hasBindingAtCoords(boundX, boundY, boundZ)) {
                    player.addChatMessage(new ChatComponentText("§c该坐标 (" + newCoordStr + ") 已存在于数据显示器的数据中，跳过添加。"));
                    return;
                }

                int nextIndex = monitor.findNextAvailableBindingIndex();
                if (nextIndex < 0) {
                    player.addChatMessage(
                        new ChatComponentText(
                            "§c数据显示器数据已满（最多" + TileEntityAdvanceDataMonitor.MAX_DATA_BINDINGS + "条），无法添加新数据。"));
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
                newData.setTag("dataValues", new net.minecraft.nbt.NBTTagList());
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
                    new ChatComponentText("§a已将坐标 (" + newCoordStr + ") 添加到高级数据显示器（索引 " + nextIndex + "）"));
                return;
            } else {
                player.addChatMessage(
                    new ChatComponentText("§c数据映录器尚未映录任何坐标。请先 shift+右键 一个方块映录其数据，再 shift+右键 高级数据显示器来添加。"));
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

        if (te != null) {
            NBTTagCompound teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            nbt.setTag("boundTE", teNbt);
            player.addChatMessage(new ChatComponentText("§a已成功映录方块数据!"));
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
        }
    }

    @SideOnly(Side.CLIENT)
    private void openNbtGui(ItemStack stack, EntityPlayer player) {
        if (!stack.hasTagCompound()) {
            player.addChatMessage(new ChatComponentText("§c物品未存储任何数据!"));
            return;
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.hasKey("boundPos")) {
            player.addChatMessage(new ChatComponentText("§c未映录方块的 NBT 数据!"));
            return;
        }

        if (nbt.hasKey("boundTE")) {
            NBTTagCompound tileNBT = nbt.getCompoundTag("boundTE");
            ItemClientGui.openNbtViewerGui(tileNBT);
        } else {
            player.addChatMessage(new ChatComponentText("§c映录的方块没有 NBT 数据!"));
        }
    }

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
