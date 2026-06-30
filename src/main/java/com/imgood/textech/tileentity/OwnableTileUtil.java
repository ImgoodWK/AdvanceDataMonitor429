package com.imgood.textech.tileentity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public final class OwnableTileUtil {

    public static final String NBT_KEY = "OwnerName";

    private OwnableTileUtil() {}

    public static String readOwner(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(NBT_KEY)) {
            return "";
        }
        String name = tag.getString(NBT_KEY);
        return name == null ? "" : name;
    }

    public static void writeOwner(NBTTagCompound tag, String ownerName) {
        if (tag == null) {
            return;
        }
        if (ownerName == null || ownerName.isEmpty()) {
            tag.removeTag(NBT_KEY);
        } else {
            tag.setString(NBT_KEY, ownerName);
        }
    }

    public static String nameFromPlacer(EntityLivingBase placer) {
        if (placer instanceof EntityPlayer) {
            String name = ((EntityPlayer) placer).getCommandSenderName();
            return name == null ? "" : name;
        }
        return "";
    }

    public static boolean isEmpty(String ownerName) {
        return ownerName == null || ownerName.isEmpty();
    }
}
