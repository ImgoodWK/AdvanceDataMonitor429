package com.imgood.textech.handler;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class StarryPlayerLookup {

    private StarryPlayerLookup() {}

    public static EntityPlayer findPlayer(World world, UUID id) {
        if (world == null || id == null) {
            return null;
        }
        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) obj;
                if (id.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    public static EntityLivingBase asLiving(EntityPlayer player) {
        return player;
    }
}
