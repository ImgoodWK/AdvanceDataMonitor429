package com.imgood.advancedatamonitor.handler;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

/**
 * Shared instant-kill logic mirroring Avaritia Infinity Sword behaviour.
 */
public final class StarryCosmosSwordUtil {

    private StarryCosmosSwordUtil() {}

    public static void instantKill(EntityLivingBase victim, EntityLivingBase attacker) {
        if (victim == null || attacker == null || victim.worldObj.isRemote || victim.isDead) {
            return;
        }
        if (victim == attacker) {
            return;
        }

        DamageSourceStarryCosmos source = new DamageSourceStarryCosmos(attacker);
        victim.func_110142_aN()
            .func_94547_a(source, victim.getHealth(), victim.getHealth());
        victim.setHealth(0.0F);
        victim.onDeath(new EntityDamageSource("starryCosmos", attacker));
    }
}
