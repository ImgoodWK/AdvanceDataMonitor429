package com.imgood.textech.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.gui.handler.GuiHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Display names / 显示名称:
 * - EN: Super Orange
 * - ZH: 超能砂糖桔
 * Lang keys: item.orange.name, adm.super_orange.tooltip.title
 *
 * Legendary item with mining, matter-ball drops, companion drone, head effects, and per-item feature toggles.
 */
public class ItemSuperOrange extends Item {

    private static final String NBT_KEY_CUSTOM_NAME = "customDisplayName";
    public static final String NBT_MATTER_BALL_ENABLED = "matterBallEnabled";
    public static final String NBT_PICKUP_MATTER_BALL_ENABLED = "pickupMatterBallEnabled";
    public static final String NBT_DROP_MATTER_BALL_ENABLED = "dropMatterBallEnabled";
    public static final String NBT_DROP_MULTIPLIER = "dropMultiplier";
    public static final String NBT_DRONE_ENABLED = "droneEnabled";

    public ItemSuperOrange() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public float getDigSpeed(ItemStack stack, Block block, int meta) {
        return 1000000.0F;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return true;
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            if (player.isSneaking()) {
                player.openGui(AdvanceDataMonitor.instance, GuiHandler.SUPER_ORANGE_GUI_ID, world, 0, 0, 0);
            }
            return stack;
        }

        if (player.isSneaking()) {
            return stack;
        } else {
            boolean enabled = !isDroneEnabled(stack);
            setDroneEnabled(stack, enabled);
            if (enabled) {
                player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GOLD
                            + StatCollector.translateToLocalFormatted("adm.super_orange.toggle.drone.on")));
            } else {
                player.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.GRAY
                            + StatCollector.translateToLocalFormatted("adm.super_orange.toggle.drone.off")));
            }
        }

        return stack;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("adm.super_orange.tooltip.title"));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.super_orange.tooltip.open_config"));
        tooltip.add(
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("adm.super_orange.tooltip.toggle_drone"));
        tooltip.add("");

        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("adm.super_orange.tooltip.mine"));

        appendHeadEffectsLine(tooltip);
        appendMatterBallLine(stack, tooltip);
        appendDroneLines(stack, tooltip);
        appendNameplateLines(stack, tooltip);

        tooltip.add(
            EnumChatFormatting.LIGHT_PURPLE
                + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.legendary"));
        super.addInformation(stack, player, tooltip, advanced);
    }

    private void appendHeadEffectsLine(List<String> tooltip) {
        if (!Config.superOrangeHeadEffectsEnabled) {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.head_effects.disabled"));
            return;
        }
        tooltip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.head_effects.enabled"));
    }

    private void appendMatterBallLine(ItemStack stack, List<String> tooltip) {
        int multiplier = getDropMultiplier(stack);
        if (!Config.superOrangeDropMultiplierEnabled) {
            tooltip.add(
                EnumChatFormatting.GRAY + StatCollector
                    .translateToLocalFormatted("adm.super_orange.tooltip.matter_ball.config_off", multiplier));
            return;
        }

        boolean active = isMatterBallFeatureActive(stack);
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("adm.super_orange.tooltip.matter_ball", multiplier, formatState(active)));
        boolean pickupActive = isPickupMatterBallFeatureActive(stack);
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("adm.super_orange.tooltip.pickup_matter_ball", formatState(pickupActive)));
        boolean dropActive = isDropMatterBallFeatureActive(stack);
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocalFormatted("adm.super_orange.tooltip.drop_matter_ball", formatState(dropActive)));
    }

    private void appendDroneLines(ItemStack stack, List<String> tooltip) {
        if (!Config.superOrangeDroneEnabled) {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.drone.config_off"));
            return;
        }

        boolean active = isDroneFeatureActive(stack);
        String state = formatState(active);
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "adm.super_orange.tooltip.drone",
                Config.superOrangeDroneAttackRange,
                Config.superOrangeDroneAttackDamage,
                (double) Config.superOrangeDroneAttacksPerSecond,
                Config.superOrangeDroneMaxClones,
                state));

        if (Config.superOrangeProjectileImmunityEnabled) {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.projectile", state));
        } else {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.projectile.config_off"));
        }
    }

    private void appendNameplateLines(ItemStack stack, List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocalFormatted("adm.super_orange.tooltip.nameplate_rename"));
        String name = getNameplateText(stack);
        if (name != null && !name.isEmpty()) {
            tooltip.add(
                EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted("adm.super_orange.nameplate")
                    + ": "
                    + name);
        }
    }

    private String formatState(boolean active) {
        if (active) {
            return EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted("adm.super_orange.state.on");
        }
        return EnumChatFormatting.RED + StatCollector.translateToLocalFormatted("adm.super_orange.state.off");
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    public static String getNameplateText(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return null;

        if (stack.hasDisplayName()) {
            return stack.getDisplayName();
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && nbt.hasKey(NBT_KEY_CUSTOM_NAME)) {
            String name = nbt.getString(NBT_KEY_CUSTOM_NAME);
            if (!name.isEmpty()) return name;
        }

        return null;
    }

    public static void setCustomName(ItemStack stack, String name) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        NBTTagCompound nbt = getOrCreateNBT(stack);
        nbt.setString(NBT_KEY_CUSTOM_NAME, name != null ? name : "");
        stack.setTagCompound(nbt);
    }

    public static boolean isMatterBallEnabled(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_MATTER_BALL_ENABLED)) {
            return true;
        }
        return nbt.getBoolean(NBT_MATTER_BALL_ENABLED);
    }

    public static void setMatterBallEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        getOrCreateNBT(stack).setBoolean(NBT_MATTER_BALL_ENABLED, enabled);
    }

    public static boolean isPickupMatterBallEnabled(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_PICKUP_MATTER_BALL_ENABLED)) {
            return true;
        }
        return nbt.getBoolean(NBT_PICKUP_MATTER_BALL_ENABLED);
    }

    public static void setPickupMatterBallEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        getOrCreateNBT(stack).setBoolean(NBT_PICKUP_MATTER_BALL_ENABLED, enabled);
    }

    public static boolean isDropMatterBallEnabled(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_DROP_MATTER_BALL_ENABLED)) {
            return true;
        }
        return nbt.getBoolean(NBT_DROP_MATTER_BALL_ENABLED);
    }

    public static void setDropMatterBallEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        getOrCreateNBT(stack).setBoolean(NBT_DROP_MATTER_BALL_ENABLED, enabled);
    }

    public static int getDropMultiplier(ItemStack stack) {
        int configuredDefault = Math.max(1, Config.superOrangeDropMultiplier);
        int max = Math.max(1, Config.superOrangeDropMultiplierMax);
        if (stack == null) {
            return Math.min(configuredDefault, max);
        }
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_DROP_MULTIPLIER)) {
            return Math.min(configuredDefault, max);
        }
        int value = nbt.getInteger(NBT_DROP_MULTIPLIER);
        if (value < 1) {
            value = 1;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }

    public static void setDropMultiplier(ItemStack stack, int multiplier) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        getOrCreateNBT(stack).setInteger(NBT_DROP_MULTIPLIER, multiplier);
    }

    public static boolean isDroneEnabled(ItemStack stack) {
        if (stack == null) return false;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null || !nbt.hasKey(NBT_DRONE_ENABLED)) {
            return true;
        }
        return nbt.getBoolean(NBT_DRONE_ENABLED);
    }

    public static void setDroneEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !(stack.getItem() instanceof ItemSuperOrange)) return;
        getOrCreateNBT(stack).setBoolean(NBT_DRONE_ENABLED, enabled);
    }

    public static boolean isMatterBallFeatureActive(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSuperOrange
            && Config.superOrangeDropMultiplierEnabled
            && isMatterBallEnabled(stack);
    }

    public static boolean isPickupMatterBallFeatureActive(ItemStack stack) {
        return isMatterBallFeatureActive(stack) && isPickupMatterBallEnabled(stack);
    }

    public static boolean isDropMatterBallFeatureActive(ItemStack stack) {
        return isMatterBallFeatureActive(stack) && isDropMatterBallEnabled(stack);
    }

    public static boolean isPickupMatterBallActiveForPlayer(EntityPlayer player) {
        ItemStack stack = findOrangeStack(player);
        return isPickupMatterBallFeatureActive(stack);
    }

    public static boolean isDropMatterBallActiveForPlayer(EntityPlayer player) {
        ItemStack stack = findOrangeStack(player);
        return isDropMatterBallFeatureActive(stack);
    }

    public static boolean isDroneFeatureActive(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSuperOrange
            && Config.superOrangeDroneEnabled
            && isDroneEnabled(stack);
    }

    public static ItemStack findOrangeStack(EntityPlayer player) {
        if (player == null || player.inventory == null) return null;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemSuperOrange) {
                return stack;
            }
        }
        return null;
    }

    public static boolean isMatterBallActiveForPlayer(EntityPlayer player) {
        ItemStack stack = findOrangeStack(player);
        return isMatterBallFeatureActive(stack);
    }

    public static boolean isDroneActiveForPlayer(EntityPlayer player) {
        ItemStack stack = findOrangeStack(player);
        return isDroneFeatureActive(stack);
    }

    public static boolean hasSuperOrange(EntityPlayer player) {
        return findOrangeStack(player) != null;
    }

    private static NBTTagCompound getOrCreateNBT(ItemStack stack) {
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        return nbt;
    }
}
