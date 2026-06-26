package com.imgood.advancedatamonitor.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;

import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineSlash;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordLineStab;
import com.imgood.advancedatamonitor.entity.EntityStarrySwordThrown;
import com.imgood.advancedatamonitor.handler.StarryCosmosSounds;
import com.imgood.advancedatamonitor.handler.StarryCosmosSwordConstants;
import com.imgood.advancedatamonitor.handler.StarryCosmosSwordUtil;
import com.imgood.advancedatamonitor.handler.StarryEntityMotionUtil;
import com.imgood.advancedatamonitor.handler.StarrySwordSpawnScheduler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fox.spiteful.avaritia.render.ICosmicRenderItem;

/**
 * Display names / 显示名称:
 * - EN: Empyrean Holy Judgment
 * - ZH: 至高天圣裁
 * Lang keys: item.starryCosmosSword.name, adm.tooltip.starry_cosmos_sword
 *
 * Cosmic shader blade with line slash, throw and sword rain.
 */
public class ItemStarryCosmosSword extends ItemSword implements ICosmicRenderItem {

    private static final ToolMaterial MATERIAL = EnumHelper
        .addToolMaterial("STARRY_COSMOS_SWORD", 3, Integer.MAX_VALUE / 4, 100.0F, 50.0F, 200);

    @SideOnly(Side.CLIENT)
    private IIcon cosmicMask;

    public ItemStarryCosmosSword() {
        super(MATERIAL);
        setUnlocalizedName("starryCosmosSword");
        setTextureName("advancedatamonitor:starry_cosmos_sword");
        setCreativeTab(CreativeTabs.tabCombat);
        setMaxStackSize(1);
    }

    public static void spawnLineSlash(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        player.worldObj.spawnEntityInWorld(new EntityStarrySwordLineSlash(player.worldObj, player));
    }

    /** Shift+right-click: mini vertical stabs on hostile mobs in a 3×3 chunk area. */
    public static void spawnChunkJudgment(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        World world = player.worldObj;
        List<EntityLivingBase> targets = StarryEntityMotionUtil.collectHostileInChunkArea(world, player, 1);
        if (targets.isEmpty()) {
            return;
        }
        StarryCosmosSounds
            .playJudgmentCast(world, player.posX, player.posY + player.getEyeHeight() * 0.5D, player.posZ);
        float displayYaw = player.rotationYaw;
        float miniScale = StarryCosmosSwordConstants.SCALE_LINE_STAB_MINI;
        for (final EntityLivingBase target : targets) {
            if (target == null || target.isDead) {
                continue;
            }
            final EntityPlayer owner = player;
            final World spawnWorld = world;
            final float yaw = displayYaw;
            final float scale = miniScale;
            StarrySwordSpawnScheduler.schedule(new Runnable() {

                @Override
                public void run() {
                    if (target.isDead || spawnWorld.isRemote) {
                        return;
                    }
                    spawnWorld.spawnEntityInWorld(new EntityStarrySwordLineStab(spawnWorld, target, owner, yaw, scale));
                }
            });
        }
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase victim, EntityLivingBase attacker) {
        if (!attacker.worldObj.isRemote) {
            StarryCosmosSwordUtil.instantKill(victim, attacker);
            StarryCosmosSounds
                .playMeleeHit(attacker.worldObj, victim.posX, victim.posY + victim.height * 0.5D, victim.posZ);
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            if (player.isSneaking()) {
                spawnChunkJudgment(player);
            } else {
                StarryCosmosSounds
                    .playThrow(world, player.posX, player.posY + player.getEyeHeight() - 0.1D, player.posZ);
                EntityStarrySwordThrown thrown = new EntityStarrySwordThrown(world, player, stack.copy());
                world.spawnEntityInWorld(thrown);
            }
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("adm.tooltip.starry_cosmos_sword"));
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.epic;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, 0);
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getMaskTexture(ItemStack stack, EntityPlayer player) {
        return cosmicMask;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public float getMaskMultiplier(ItemStack stack, EntityPlayer player) {
        return 1.0F;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerIcons(IIconRegister register) {
        super.registerIcons(register);
        cosmicMask = register.registerIcon("advancedatamonitor:starry_cosmos_sword_mask");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return false;
    }
}
