package com.imgood.textech.tileentity;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Tile entities that record the placing player's in-game name (not UUID).
 * Stored in NBT as {@link OwnableTileUtil#NBT_KEY}; not shown in block GUIs.
 */
public interface IOwnableTile {

    String getOwnerName();

    void setOwnerName(String name);

    void setOwnerFromPlacer(EntityLivingBase placer);

    /**
     * Claim ownership when empty (legacy DataMonitor migration on first open).
     */
    void claimOwnerIfEmpty(EntityPlayer player);
}
