package com.imgood.advancedatamonitor.items;

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
import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.guiscreen.GUINBTViewer;
import com.imgood.advancedatamonitor.network.packet.PacketItemNBT;
import com.imgood.advancedatamonitor.utils.BlockPos;
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
                if (stack.getTagCompound() == null) {
                    stack.setTagCompound(new NBTTagCompound());
                }
                stack.getTagCompound()
                    .removeTag("boundPos");
                stack.getTagCompound()
                    .removeTag("tileNBT");
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
                NBTTagCompound nbt = stack.getTagCompound();
                if (nbt == null) nbt = new NBTTagCompound();

                // 保存坐标
                NBTTagCompound posTag = new NBTTagCompound();
                posTag.setInteger("x", x);
                posTag.setInteger("y", y);
                posTag.setInteger("z", z);
                nbt.setTag("boundPos", posTag);
                stack.setTagCompound(nbt);
            }

            if (world.isRemote) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te != null) {
                    NBTTagCompound teNbt = new NBTTagCompound();
                    te.writeToNBT(teNbt);
                    String jsonData = NBTJsonParserHelper.parseNBTToJson(teNbt)
                        .toString();
                    AdvanceDataMonitor.ADMCHANEL
                        .sendToServer(new PacketItemNBT(player.inventory.currentItem, new BlockPos(x, y, z), jsonData));
                }
            }
            return true;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    private void openNbtGui(ItemStack stack, EntityPlayer player) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            // 检查是否存在绑定的Tile Entity NBT数据
            if (nbt.hasKey("boundPos")) {
                NBTTagCompound posTag = nbt.getCompoundTag("boundPos");
                int x = posTag.getInteger("x");
                int y = posTag.getInteger("y");
                int z = posTag.getInteger("z");
                TileEntity tileEntity = player.worldObj.getTileEntity(x, y, z);
                NBTTagCompound tileNBT = new NBTTagCompound();
                tileEntity.writeToNBT(tileNBT);;
                System.out.println("tileNbt" + tileNBT);
                // 解析并显示Tile Entity的NBT
                JsonObject json = NBTJsonParserHelper.parseNBTToJson(tileNBT);
                System.out.println("json" + json);
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GUINBTViewer(json));
            } else {
                // 提示未绑定数据
                player.addChatMessage(new ChatComponentText("§c未绑定方块的NBT数据!"));
            }
        } else {
            player.addChatMessage(new ChatComponentText("§c物品未存储任何数据!"));
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
