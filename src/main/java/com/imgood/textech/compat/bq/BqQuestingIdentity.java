package com.imgood.textech.compat.bq;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.webae.context.WebAeOwnerContext;

/**
 * Maps WebAE token owner UUID to BetterQuesting questing UUID (party-aware).
 */
public final class BqQuestingIdentity {

    private BqQuestingIdentity() {}

    public static UUID resolveQuestingUuid(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty() || !BqCompat.isModLoaded()) {
            return null;
        }
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            try {
                return UUID.fromString(ownerUuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return BqApiFacade.getQuestingUuid(player);
    }

    public static EntityPlayerMP resolvePlayer(String ownerUuid) {
        return WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
    }

    public static UUID resolveQuestingUuid(EntityPlayer player) {
        if (player == null || !BqCompat.isModLoaded()) {
            return null;
        }
        return BqApiFacade.getQuestingUuid(player);
    }
}
