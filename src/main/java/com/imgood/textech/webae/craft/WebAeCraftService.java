package com.imgood.textech.webae.craft;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.AssistantServerServices.CraftNotifySource;
import com.imgood.textech.assistant.CraftSubmitHooks;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.tileentity.TileEntityAdvanceNetworkLink;
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
        return AssistantServerServices.craftingCandidatesForLink(player, group.craftingLink, rawText, target, amount);
    }

    /**
     * Legacy string-only submit (alerts / quest). Prefer {@link #submitCraftTracked} for order UI tracking.
     */
    public static String submitCraft(String ownerUuid, int networkId, CraftingCandidate candidate, long amount,
        String rawText, String locale, String cpuName) {
        CraftSubmitResult result = submitCraftTracked(
            ownerUuid,
            networkId,
            candidate,
            amount,
            rawText,
            locale,
            cpuName,
            null,
            null);
        return result != null ? result.message : "submit failed";
    }

    /**
     * Submit craft with optional WebAE tracking key and lifecycle hooks (bind ICraftingLink).
     */
    public static CraftSubmitResult submitCraftTracked(String ownerUuid, int networkId, CraftingCandidate candidate,
        long amount, String rawText, String locale, String cpuName, String trackingKey, CraftSubmitHooks hooks) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (player == null || group == null || group.craftingLink == null) {
            return CraftSubmitResult.failed("No crafting link for network " + networkId);
        }
        WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        String message = AssistantServerServices.submitCraftForLink(
            player,
            group.craftingLink,
            candidate,
            amount,
            rawText,
            locale,
            cpuName,
            CraftNotifySource.WEB_AE,
            trackingKey,
            hooks);
        if (message == null || message.isEmpty()) {
            return CraftSubmitResult.failed("empty submit result");
        }
        if (isFailureMessage(message)) {
            return CraftSubmitResult.failed(message);
        }
        return CraftSubmitResult.calculating(message);
    }

    private static boolean isFailureMessage(String message) {
        String m = message.toLowerCase();
        if (m.contains("订单失败") || m.contains("order failed") || m.contains("订单被拒绝") || m.contains("order rejected")) {
            return true;
        }
        // Success path from trySubmit
        if (m.contains("合成计算已开始") || m.contains("crafting calculation started")) {
            return false;
        }
        // checkCanStart / other rejections without orderFailed prefix
        if (m.contains("拒绝") || m.contains("rejected") || m.contains("unavailable") || m.contains("invalid amount")) {
            return true;
        }
        return false;
    }

    public static TileEntityAdvanceNetworkLink getCraftingLink(String ownerUuid, int networkId) {
        NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        return group != null ? group.craftingLink : null;
    }
}
