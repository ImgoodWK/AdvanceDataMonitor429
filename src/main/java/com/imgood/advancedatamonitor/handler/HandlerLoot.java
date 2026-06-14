package com.imgood.advancedatamonitor.handler;

import java.util.Random;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.imgood.advancedatamonitor.loader.LoaderItem;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles loot acquisition for items that don't have crafting recipes:
 * - ItemOrange: 0.1% dungeon chest loot, 0.05% mob kill drop
 */
public class HandlerLoot {

    private static final Random RAND = new Random();
    private static final double ORANGE_DROP_RATE = 0.0005;

    public static void registerChestLoot() {
        ItemStack orange = new ItemStack(LoaderItem.orange);
        ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST)
            .addItem(new WeightedRandomChestContent(orange, 1, 1, 1));
        ChestGenHooks.getInfo(ChestGenHooks.MINESHAFT_CORRIDOR)
            .addItem(new WeightedRandomChestContent(orange, 1, 1, 1));
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.entityLiving == null || event.entityLiving.worldObj.isRemote) return;

        if (!(event.entityLiving instanceof IMob) && !(event.entityLiving instanceof IAnimals)) {
            return;
        }

        if (event.recentlyHit && RAND.nextDouble() < ORANGE_DROP_RATE) {
            ItemStack orange = new ItemStack(LoaderItem.orange);
            EntityItem drop = new EntityItem(
                event.entityLiving.worldObj,
                event.entityLiving.posX,
                event.entityLiving.posY,
                event.entityLiving.posZ,
                orange);
            event.drops.add(drop);
        }
    }
}
