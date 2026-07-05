package com.imgood.textech.webae.pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.webae.context.WebAeOwnerContext;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;

/**
 * Consumes one blank AE pattern from the player's AE network storage.
 * Must run on the server thread.
 */
public final class BlankPatternHelper {

    private BlankPatternHelper() {}

    /**
     * @return true if one blank pattern was extracted from network storage
     */
    public static boolean consumeOne(String ownerUuid, int networkId) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            return false;
        }
        IGrid grid = InterfaceLocator.findGrid(ownerUuid, networkId);
        if (grid == null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Blank pattern consume: no grid for network {}", networkId);
            return false;
        }
        IStorageGrid storageGrid;
        try {
            storageGrid = grid.getCache(IStorageGrid.class);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Blank pattern consume: storage grid unavailable", e);
            return false;
        }
        if (storageGrid == null) return false;

        ItemStack blankPrototype = createBlankPatternStack();
        if (blankPrototype == null) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Blank pattern item definition unavailable");
            return false;
        }

        IAEItemStack request = AEApi.instance()
            .storage()
            .createItemStack(blankPrototype);
        if (request == null) return false;
        request.setStackSize(1);

        PlayerSource source = new PlayerSource(player, null);
        try {
            IAEItemStack extracted = storageGrid.getItemInventory()
                .extractItems(request, Actionable.MODULATE, source);
            return extracted != null && extracted.getStackSize() > 0;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Blank pattern extract failed", e);
            return false;
        }
    }

    private static ItemStack createBlankPatternStack() {
        try {
            com.google.common.base.Optional<ItemStack> stack = AEApi.instance()
                .definitions()
                .materials()
                .blankPattern()
                .maybeStack(1);
            if (stack.isPresent()) return stack.get();
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.debug("[WebAE] materials().blankPattern() unavailable: {}", t.getMessage());
        }
        return null;
    }
}
