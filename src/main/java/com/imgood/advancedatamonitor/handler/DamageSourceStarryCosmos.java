package com.imgood.advancedatamonitor.handler;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

/**
 * Damage source for Starry Cosmos Sword instant-kill attacks.
 */
public class DamageSourceStarryCosmos extends EntityDamageSource {

    public DamageSourceStarryCosmos(EntityLivingBase attacker) {
        super("starryCosmos", attacker);
        this.setDamageBypassesArmor();
    }
}
