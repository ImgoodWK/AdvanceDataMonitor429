package com.imgood.textech.webae.craft;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.AssistantServerServices.CraftNotifySource;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.context.WebAeOwnerContext.NetworkGroup;

/**
 * WebAE crafting entry points scoped to an owner network (not player position).
 */
public final class WebAeCraftService {

    private WebAeCraftService() {}

    public static List<CraftingCandidate> craftingCandidates(String ownerUuid, int networkId, String rawText,
        String target, long amount) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null || group.craftingLink == null) {
            return Collections.emptyList();
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        return AssistantServerServices.craftingCandidatesForLink(
            player,
            group.craftingLink,
            rawText,
            target,
            amount);
    }

    public static String submitCraft(String ownerUuid, int networkId, CraftingCandidate candidate, long amount,
        String rawText, String locale, String cpuName) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null || group.craftingLink == null) {
            return "No crafting link for network " + networkId;
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        return AssistantServerServices.submitCraftForLink(
            player,
            group.craftingLink,
            candidate,
            amount,
            rawText,
            locale,
            cpuName,
            CraftNotifySource.WEB_AE);
    }

    public static TileEntityAdvanceCraftingLink getCraftingLink(String ownerUuid, int networkId) {
        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        return group != null ? group.craftingLink : null;
    }
}
