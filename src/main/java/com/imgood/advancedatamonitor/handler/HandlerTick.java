package com.imgood.advancedatamonitor.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.imgood.advancedatamonitor.items.ItemDataWeave;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * @program: AdvanceDataMonitor
 * @description:
 * @author: Imgood
 * @create: 2025-04-23 15:02
 **/
public class HandlerTick {

    private long lastOutput = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            EntityPlayer player = event.player;
            ItemStack stack = player.getHeldItem();

            if (stack != null && stack.getItem() instanceof ItemDataWeave) {
                long now = System.currentTimeMillis();
                if (now - lastOutput > 1000) {
                    lastOutput = now;
                    NBTTagCompound nbt = stack.getTagCompound();
                    if (nbt != null && nbt.hasKey("Position")) {
                        BlockPos pos = new BlockPos(
                            nbt.getCompoundTag("Position")
                                .getInteger("x"),
                            nbt.getCompoundTag("Position")
                                .getInteger("y"),
                            nbt.getCompoundTag("Position")
                                .getInteger("z"));
                    }
                    /*
                     * if (nbt != null && nbt.hasKey("boundPos") && nbt.hasKey("enabledTags")) {
                     * // 获取绑定坐标
                     * NBTTagCompound posTag = nbt.getCompoundTag("boundPos");
                     * BlockPos pos = new BlockPos(
                     * posTag.getInteger("x"),
                     * posTag.getInteger("y"),
                     * posTag.getInteger("z")
                     * );
                     * // 获取TileEntity
                     * TileEntity te = player.worldObj.getTileEntity(pos.x, pos.y, pos.z);
                     * if (te != null) {
                     * NBTTagCompound teNbt = new NBTTagCompound();
                     * te.writeToNBT(teNbt);
                     * // 获取启用的标签
                     * NBTTagCompound enabledTags = nbt.getCompoundTag("enabledTags");
                     * for (Object key : enabledTags.func_150296_c()) {
                     * String tagPath = (String) key;
                     * if (enabledTags.getBoolean(tagPath)) {
                     * // 获取具体值
                     * String value = getNbtValue(teNbt, tagPath);
                     * System.out.println("[ADM] " + tagPath + ": " + value);
                     * }
                     * }
                     * }
                     * }
                     */
                }
            }
        }
    }

    private String getNbtValue(NBTTagCompound nbt, String path) {
        String[] parts = path.split("\\.");
        NBTTagCompound current = nbt;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.getCompoundTag(parts[i]);
        }
        return current.getTag(parts[parts.length - 1])
            .toString();
    }
}
