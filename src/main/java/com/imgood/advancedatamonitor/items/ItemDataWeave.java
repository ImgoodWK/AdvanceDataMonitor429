package com.imgood.advancedatamonitor.items;

import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imgood.advancedatamonitor.gui.guiscreen.GUINBTViewer;
import com.imgood.advancedatamonitor.utils.NBTJsonParserHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemDataWeave extends Item {

    public ItemDataWeave() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                clearNBTData(stack);
                player.addChatMessage(new ChatComponentText("§a已清除所有绑定数据!"));
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
            player.addChatMessage(new ChatComponentText("§c请勿绑定该方块!"));
            return;
        }
        if (nbt != null  && nbt.hasKey("mName")) {
            nbt.removeTag("mName");
        }

        if (nbt != null  && te instanceof IGregTechTileEntity) {
            nbt.setString("mName",  ((IGregTechTileEntity) te).getInventoryName());
        }

        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }

        // 保存坐标
        NBTTagCompound posTag = new NBTTagCompound();
        posTag.setInteger("x", x);
        posTag.setInteger("y", y);
        posTag.setInteger("z", z);
        nbt.setTag("boundPos", posTag);

        // 保存方块信息
        nbt.setString("boundBlock", Block.blockRegistry.getNameForObject(block));
        nbt.setInteger("boundMeta", meta);

        // 保存TileEntity数据
        if (te != null) {
            NBTTagCompound teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            nbt.setTag("boundTE", teNbt);
            player.addChatMessage(new ChatComponentText("§a已成功绑定方块!"));
        } else if (te instanceof TileEntityAdvanceDataMonitor) {
            nbt.removeTag("boundTE");
        } else {
            nbt.removeTag("boundTE");
        }
    }

    private void clearNBTData(ItemStack stack) {
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().removeTag("boundPos");
            stack.getTagCompound().removeTag("boundBlock");
            stack.getTagCompound().removeTag("boundMeta");
            stack.getTagCompound().removeTag("boundTE");
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
            player.addChatMessage(new ChatComponentText("§c未绑定方块的NBT数据!"));
            return;
        }

        // 从物品NBT中直接读取保存的数据
        if (nbt.hasKey("boundTE")) {
            NBTTagCompound tileNBT = nbt.getCompoundTag("boundTE");
            JsonObject json = NBTJsonParserHelper.parseNBTToJson(tileNBT);
            Minecraft.getMinecraft().displayGuiScreen(new GUINBTViewer(json));
        } else {
            player.addChatMessage(new ChatComponentText("§c绑定的方块没有NBT数据!"));
        }
    }

    public static JsonObject createTestNBT() {
        // 创建根复合标签
        JsonObject root = new JsonObject();
        root.addProperty("type", "TAG_Compound");

        JsonObject rootValue = new JsonObject();

        // 基础类型示例
        addSimpleTag(rootValue, "name", "TAG_String", "Test Item");
        addSimpleTag(rootValue, "count", "TAG_Int", 5);

        // 嵌套的复合标签
        JsonObject enchants = new JsonObject();
        enchants.addProperty("type", "TAG_Compound");
        JsonObject enchantsValue = new JsonObject();

        // 附魔列表
        JsonObject enchant1 = new JsonObject();
        enchant1.addProperty("type", "TAG_Compound");
        JsonObject enchant1Value = new JsonObject();
        addSimpleTag(enchant1Value, "id", "TAG_Short", 16); // 锋利
        addSimpleTag(enchant1Value, "lvl", "TAG_Short", 3);
        enchant1.add("value", enchant1Value);

        JsonObject enchant2 = new JsonObject();
        enchant2.addProperty("type", "TAG_Compound");
        JsonObject enchant2Value = new JsonObject();
        addSimpleTag(enchant2Value, "id", "TAG_Short", 17); // 亡灵杀手
        addSimpleTag(enchant2Value, "lvl", "TAG_Short", 2);
        enchant2.add("value", enchant2Value);

        // 加入列表
        JsonObject enchantList = new JsonObject();
        enchantList.addProperty("type", "TAG_List");
        JsonArray listContents = new JsonArray();
        listContents.add(enchant1);
        listContents.add(enchant2);
        enchantList.add("value", listContents);

        enchantsValue.add("ench", enchantList);
        enchants.add("value", enchantsValue);
        rootValue.add("enchants", enchants);

        // 深度嵌套的复合标签
        JsonObject deepNested = createDeepNested(3); // 创建3层嵌套
        rootValue.add("deepNested", deepNested);

        root.add("value", rootValue);
        return root;
    }

    // 创建深度嵌套的结构
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

    // 添加简单类型的标签
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
