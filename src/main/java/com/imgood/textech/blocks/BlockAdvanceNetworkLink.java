package com.imgood.textech.blocks;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.gui.handler.GuiHandler;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.textech.utils.AeSecurityCheck;
import com.imgood.textech.webae.onboarding.WebConsoleOnboarding;

/**
 * Display names / 显示名称:
 * - EN: Advanced Network Linker (unified AE link)
 * - ZH: 高级网络链接器（统一 AE 链接器）
 * Lang keys: tile.NetworkLinkBlock.name, adm.title.data_config_ae_network
 */
public class BlockAdvanceNetworkLink extends BlockContainer {

    private static final int UPDATE_INTERVAL = 20;

    public BlockAdvanceNetworkLink() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
        this.setCreativeTab(CreativeTabs.tabRedstone);
        this.setBlockName("NetworkLinkBlock");
        this.setBlockTextureName(AdvanceDataMonitor.MODID + ":adv_network_link");
        this.setTickRandomly(true);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAdvanceNetworkLink();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int direction = MathHelper.floor_double((double) ((placer.rotationYaw + 180) * 4.0F / 360.0F) + 0.5D) & 3;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof TileEntityAdvanceNetworkLink) {
            TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) tileEntity;
            link.facing = direction;
            link.setOwnerFromPlacer(placer);
        }
        String denial = StatCollector.translateToLocal("adm.ae.no_build_permission");
        AeSecurityCheck.rejectIfUnauthorized(world, x, y, z, this, placer, denial);
        if (!world.isRemote && placer instanceof EntityPlayerMP) {
            WebConsoleOnboarding.notifyOwnerOnNetworkLinkPlaced((EntityPlayerMP) placer);
        }
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityAdvanceNetworkLink) {
                TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;
                link.updateNetworkCache();
                link.updateCraftingStats();
            }
            world.scheduleBlockUpdate(x, y, z, this, UPDATE_INTERVAL);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityAdvanceNetworkLink)) {
            return false;
        }
        TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;

        if (player.isSneaking()) {
            if (!world.isRemote) {
                link.updateNetworkCache();
                link.updateCraftingStats();
                player.addChatMessage(new ChatComponentText("AE2 Network Status"));
                player.addChatMessage(
                    new ChatComponentText(
                        "Items: " + link.getItemUsedBytes() + "/" + link.getItemTotalBytes() + " bytes"));
                player.addChatMessage(
                    new ChatComponentText(
                        "Fluids: " + link.getFluidUsedBytes() + "/" + link.getFluidTotalBytes() + " bytes"));
                player.addChatMessage(
                    new ChatComponentText("Item Types: " + link.getItemUsedTypes() + "/" + link.getItemTotalTypes()));
                player.addChatMessage(
                    new ChatComponentText(
                        "Fluid Types: " + link.getFluidUsedTypes() + "/" + link.getFluidTotalTypes()));
                String craftingInfo = link.getCraftingStatsInfo();
                if (craftingInfo != null && !craftingInfo.isEmpty()) {
                    String[] lines = craftingInfo.split("\\n");
                    for (int i = 0; i < lines.length; i++) {
                        player.addChatMessage(new ChatComponentText(lines[i]));
                    }
                }
            }
            return true;
        }

        if (!world.isRemote) {
            link.handleRightClick(player);
        }
        player.openGui(AdvanceDataMonitor.instance, GuiHandler.ADM_STORAGELINK_ID, world, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAdvanceNetworkLink) {
            TileEntityAdvanceNetworkLink link = (TileEntityAdvanceNetworkLink) te;
            for (int i = 0; i < link.getSizeInventory(); i++) {
                ItemStack stack = link.getStackInSlot(i);
                if (stack != null) {
                    dropStack(world, x, y, z, stack);
                    link.setInventorySlotContents(i, null);
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private static void dropStack(World world, int x, int y, int z, ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        float ox = world.rand.nextFloat() * 0.8F + 0.1F;
        float oy = world.rand.nextFloat() * 0.8F + 0.1F;
        float oz = world.rand.nextFloat() * 0.8F + 0.1F;
        while (stack.stackSize > 0) {
            int drop = world.rand.nextInt(21) + 10;
            if (drop > stack.stackSize) {
                drop = stack.stackSize;
            }
            stack.stackSize -= drop;
            ItemStack dropStack = new ItemStack(stack.getItem(), drop, stack.getItemDamage());
            if (stack.hasTagCompound()) {
                dropStack.setTagCompound(
                    (NBTTagCompound) stack.getTagCompound()
                        .copy());
            }
            EntityItem entity = new EntityItem(world, x + ox, y + oy, z + oz, dropStack);
            entity.motionX = world.rand.nextGaussian() * 0.05;
            entity.motionY = 0.2 + world.rand.nextGaussian() * 0.05;
            entity.motionZ = world.rand.nextGaussian() * 0.05;
            world.spawnEntityInWorld(entity);
        }
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = super.getDrops(world, x, y, z, metadata, fortune);
        return drops;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }
}
