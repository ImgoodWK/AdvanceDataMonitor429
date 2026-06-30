package com.imgood.textech.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 14:53
 **/
public class NBTDataHelper {

    // 获取坐标数据
    public static BlockPos getPosition(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("Position")) {
                NBTTagCompound posTag = nbt.getCompoundTag("Position");
                return new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z"));
            }
        }
        return null;
    }

    // 获取文本数组
    public static String[] getTextArray(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("TextData")) {
                NBTTagList list = nbt.getTagList("TextData", 8); // 8=字符串类�?
                String[] result = new String[list.tagCount()];
                for (int i = 0; i < list.tagCount(); i++) {
                    result[i] = list.getStringTagAt(i);
                }
                return result;
            }
        }
        return new String[0];
    }
}
