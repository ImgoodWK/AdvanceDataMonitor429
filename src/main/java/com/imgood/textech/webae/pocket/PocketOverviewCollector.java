package com.imgood.textech.webae.pocket;

import net.minecraft.item.ItemStack;

import com.imgood.textech.handler.PocketState;
import com.imgood.textech.handler.PocketStore;
import com.imgood.textech.webae.auth.WebAuthOpCheck;

/**
 * Collects minimal OP-only pocket stats without exposing stored items.
 */
public final class PocketOverviewCollector {

    private PocketOverviewCollector() {}

    public static PocketOverviewDto collect(String actorUuid, String ownerUuid) {
        PocketOverviewDto dto = new PocketOverviewDto();
        dto.opRequired = true;

        if (!WebAuthOpCheck.isOp(actorUuid)) {
            dto.available = false;
            dto.message = "OP permission required for pocket overview.";
            return dto;
        }

        PocketState state = PocketStore.instance()
            .getOrCreate(ownerUuid);
        dto.available = true;
        dto.pageCount = state.getPageCount();
        dto.slotsPerPage = state.getSlotsPerPage();
        dto.spaceUpgrades = state.getSpaceUpgrades();
        dto.pageUpgrades = state.getPageUpgrades();
        dto.stackUpgrades = state.getStackUpgrades();
        dto.infiniteStackUpgrade = state.isInfiniteStackUpgrade();
        dto.enabled = state.isEnabled();
        dto.totalSlots = dto.pageCount * dto.slotsPerPage;

        int occupied = 0;
        for (int p = 0; p < dto.pageCount; p++) {
            for (int s = 0; s < dto.slotsPerPage; s++) {
                ItemStack stack = state.getStack(p, s);
                if (stack != null) {
                    occupied++;
                }
            }
        }
        dto.occupiedSlots = occupied;
        dto.message = "Read-only overview; item contents are not exposed via Web.";
        return dto;
    }
}
