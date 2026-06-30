package com.imgood.textech.handler;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EntityDamageSource;

import com.imgood.textech.items.ItemStarryCosmosSword;

/**
 * Shared combat logic for Empyrean Holy Judgment and Holy Judgment swords.
 */
public final class StarryCosmosSwordUtil {

    public static final float DUAL_TRUE_DAMAGE = 5.0F;
    public static final float DUAL_ARMOR_DAMAGE = 5.0F;
    public static final float GREATSWORD_TRUE_DAMAGE = 10.0F;
    /** Holy Judgment left-click AoE —all hostiles along the aim line, no giant stab. */
    public static final float HOLY_LEFT_CLICK_TRUE_DAMAGE = 20.0F;

    public enum StarryCosmosAttackKind {
        DEFAULT,
        /** Left-click line giant stab or right-click throw / slam / rain chain. */
        GREATSWORD
    }

    public enum StarryCosmosDamageMode {
        INSTANT_KILL,
        DUAL_FIVE,
        TRUE_TEN
    }

    private StarryCosmosSwordUtil() {}

    public static void applyDamage(EntityLivingBase victim, EntityLivingBase attacker, StarryCosmosAttackKind kind) {
        applyDamage(victim, attacker, resolveDamageMode(attacker, kind));
    }

    public static StarryCosmosDamageMode resolveDamageMode(EntityLivingBase attacker, StarryCosmosAttackKind kind) {
        if (attacker instanceof EntityPlayer) {
            ItemStack held = ((EntityPlayer) attacker).getHeldItem();
            if (held != null && held.getItem() instanceof ItemStarryCosmosSword) {
                return ((ItemStarryCosmosSword) held.getItem()).resolveDamageMode(kind);
            }
        }
        return StarryCosmosDamageMode.INSTANT_KILL;
    }

    public static void applyDamage(EntityLivingBase victim, EntityLivingBase attacker, StarryCosmosDamageMode mode) {
        if (victim == null || attacker == null || victim.worldObj.isRemote || victim.isDead) {
            return;
        }
        if (victim == attacker) {
            return;
        }
        if (victim instanceof EntityPlayer) {
            EntityPlayer target = (EntityPlayer) victim;
            if (target.capabilities.isCreativeMode) {
                return;
            }
        }

        if (mode == StarryCosmosDamageMode.DUAL_FIVE) {
            applyDualFiveDamage(victim, attacker);
        } else if (mode == StarryCosmosDamageMode.TRUE_TEN) {
            applyTrueTenDamage(victim, attacker);
        } else {
            instantKill(victim, attacker);
        }
    }

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

    private static void applyDualFiveDamage(EntityLivingBase victim, EntityLivingBase attacker) {
        DamageSourceStarryCosmos trueSource = new DamageSourceStarryCosmos(attacker);
        victim.attackEntityFrom(trueSource, DUAL_TRUE_DAMAGE);

        EntityDamageSource armorSource = new EntityDamageSource("starryCosmos", attacker);
        victim.attackEntityFrom(armorSource, DUAL_ARMOR_DAMAGE);
    }

    private static void applyTrueTenDamage(EntityLivingBase victim, EntityLivingBase attacker) {
        DamageSourceStarryCosmos trueSource = new DamageSourceStarryCosmos(attacker);
        victim.attackEntityFrom(trueSource, GREATSWORD_TRUE_DAMAGE);
    }

    public static void applyHolyLeftClickDamage(EntityLivingBase victim, EntityLivingBase attacker) {
        if (victim == null || attacker == null || victim.worldObj.isRemote || victim.isDead) {
            return;
        }
        if (victim == attacker) {
            return;
        }
        if (victim instanceof EntityPlayer) {
            EntityPlayer target = (EntityPlayer) victim;
            if (target.capabilities.isCreativeMode) {
                return;
            }
        }
        DamageSourceStarryCosmos trueSource = new DamageSourceStarryCosmos(attacker);
        victim.attackEntityFrom(trueSource, HOLY_LEFT_CLICK_TRUE_DAMAGE);
    }
}
