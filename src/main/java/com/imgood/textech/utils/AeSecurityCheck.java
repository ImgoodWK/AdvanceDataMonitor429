package com.imgood.textech.utils;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.imgood.textech.AdvanceDataMonitor;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.SecurityPermissions;

/**
 * AE2 security terminal permission checks for custom blocks. When a player
 * places a connector or decompressor block adjacent to an AE network that has
 * a security terminal, the placement is rejected (block dropped as an item)
 * if the player lacks BUILD permission — mirroring AE2's own security model
 * for network-attached machines.
 *
 * 显示名称 / Display names:
 * - EN: AE2 Security Check
 * - ZH: AE2 安全检查
 * Lang keys: adm.ae.no_build_permission
 */
public final class AeSecurityCheck {

    private AeSecurityCheck() {}

    /**
     * Check whether the placer has BUILD permission on any AE network
     * reachable from the 6 neighbors of (x, y, z). Returns true (fail-open)
     * on the client side, for non-player placers, or when no neighboring AE
     * grid has a security terminal. Returns false only when a neighboring
     * grid has a security terminal that explicitly denies the player BUILD.
     */
    public static boolean checkPlacementPermission(World world, int x, int y, int z, EntityLivingBase placer) {
        if (world == null || world.isRemote) {
            return true;
        }
        if (!(placer instanceof EntityPlayer)) {
            return true;
        }
        EntityPlayer player = (EntityPlayer) placer;
        ForgeDirection[] dirs = ForgeDirection.values();
        for (ForgeDirection dir : dirs) {
            if (dir == ForgeDirection.UNKNOWN) {
                continue;
            }
            TileEntity neighbor = world.getTileEntity(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
            if (!(neighbor instanceof IGridHost)) {
                continue;
            }
            IGridHost gridHost = (IGridHost) neighbor;
            try {
                IGridNode node = gridHost.getGridNode(ForgeDirection.UNKNOWN);
                if (node == null) {
                    continue;
                }
                IGrid grid = node.getGrid();
                if (grid == null) {
                    continue;
                }
                ISecurityGrid security = grid.getCache(ISecurityGrid.class);
                if (security == null) {
                    continue;
                }
                if (!security.hasPermission(player, SecurityPermissions.BUILD)) {
                    return false;
                }
            } catch (Exception ignored) {
                // Grid access failed for this neighbor; skip it.
            }
        }
        return true;
    }

    /**
     * If the placer lacks BUILD permission on an adjacent AE network, drop
     * the block as an item, remove it from the world, and notify the player.
     * Returns true when the block was rejected (caller should skip further
     * initialization), false when placement is allowed.
     */
    public static boolean rejectIfUnauthorized(World world, int x, int y, int z, Block block,
        EntityLivingBase placer, String localizedDenialMessage) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (checkPlacementPermission(world, x, y, z, placer)) {
            return false;
        }
        // Drop the block as an item and remove it, mimicking AE2's
        // security-enforced rejection of unauthorized network attachments.
        int meta = world.getBlockMetadata(x, y, z);
        try {
            block.dropBlockAsItem(world, x, y, z, meta);
        } catch (Exception ignored) {
            AdvanceDataMonitor.LOG.warn("[TeXTech] Failed to drop block as item during AE security rejection", ignored);
        }
        world.setBlockToAir(x, y, z);
        if (placer instanceof EntityPlayer) {
            IChatComponent msg = new ChatComponentText(localizedDenialMessage);
            ((EntityPlayer) placer).addChatMessage(msg);
        }
        return true;
    }
}
