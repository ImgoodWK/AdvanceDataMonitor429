package com.imgood.textech.items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.imgood.textech.entity.EntityStarrySwordLineSlash;
import com.imgood.textech.handler.StarryCosmosSounds;
import com.imgood.textech.handler.StarryCosmosSwordUtil;
import com.imgood.textech.handler.StarryEntityMotionUtil;

/**
 * Display names / 显示名称:
 * - EN: Holy Judgment
 * - ZH: 圣裁
 * Lang keys: item.holyJudgment.name, adm.tooltip.holy_judgment
 *
 * Left-click: 20 true damage to hostile mobs along the aim line (no giant stab).
 * Right-click throw chain uses 10 true damage; Shift+right-click has no effect.
 * Melee: 5 true + 5 armor-respecting damage.
 */
public class ItemHolyJudgment extends ItemStarryCosmosSword {

    public ItemHolyJudgment() {
        super("holyJudgment");
    }

    /** Left-click: true damage along aim line —no {@link EntityStarrySwordLineSlash} giant stab. */
    public static void spawnLeftClickAreaJudgment(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        World world = player.worldObj;

        Vec3 look = player.getLookVec();
        double len = look.lengthVector();
        double dirX;
        double dirY;
        double dirZ;
        if (len < 0.001D) {
            dirX = 0.0D;
            dirY = 0.0D;
            dirZ = 1.0D;
        } else {
            dirX = look.xCoord / len;
            dirY = look.yCoord / len;
            dirZ = look.zCoord / len;
        }

        double originX = player.posX + dirX * 0.6D;
        double originY = player.posY + player.getEyeHeight() * 0.45D;
        double originZ = player.posZ + dirZ * 0.6D;

        List<EntityLivingBase> targets = StarryEntityMotionUtil.collectLivingAlongLine(
            world,
            originX,
            originY,
            originZ,
            dirX,
            dirY,
            dirZ,
            EntityStarrySwordLineSlash.SLASH_RANGE,
            EntityStarrySwordLineSlash.LINE_HIT_RADIUS,
            player,
            player);

        if (targets.isEmpty()) {
            return;
        }

        StarryCosmosSounds.playSlash(world, originX, originY, originZ);

        Set<Integer> seen = new HashSet<Integer>();
        for (EntityLivingBase target : targets) {
            if (target == null || target.isDead || !(target instanceof IMob)) {
                continue;
            }
            if (!seen.add(target.getEntityId())) {
                continue;
            }
            StarryCosmosSwordUtil.applyHolyLeftClickDamage(target, player);
            world.spawnParticle(
                "magicCrit",
                target.posX,
                target.posY + target.height * 0.5D,
                target.posZ,
                0.0D,
                0.15D,
                0.0D);
        }
    }

    @Override
    public StarryCosmosSwordUtil.StarryCosmosDamageMode resolveDamageMode(
        StarryCosmosSwordUtil.StarryCosmosAttackKind kind) {
        if (kind == StarryCosmosSwordUtil.StarryCosmosAttackKind.GREATSWORD) {
            return StarryCosmosSwordUtil.StarryCosmosDamageMode.TRUE_TEN;
        }
        return StarryCosmosSwordUtil.StarryCosmosDamageMode.DUAL_FIVE;
    }

    @Override
    public boolean hasEnchantGlow() {
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            return stack;
        }
        return super.onItemRightClick(stack, world, player);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("adm.tooltip.holy_judgment"));
    }
}
