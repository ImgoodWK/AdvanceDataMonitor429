package com.imgood.textech.webae.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.compat.bq.BqApiFacade;
import com.imgood.textech.compat.bq.BqCompat;
import com.imgood.textech.compat.bq.BqQuestingIdentity;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.QuestClaimResultDto;
import com.imgood.textech.webae.dto.QuestDetailDto;
import com.imgood.textech.webae.dto.QuestRewardDto;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;

/**
 * Claims BetterQuesting item/choice rewards into the owner's selected AE network.
 * Runs on the server main thread only.
 */
public final class QuestRewardClaimService {

    private QuestRewardClaimService() {}

    /**
     * @param choices rewardId (int string) → selected choice index
     */
    public static QuestClaimResultDto claim(String ownerUuid, int networkId, String questId,
        Map<String, Integer> choices) {
        QuestClaimResultDto result = new QuestClaimResultDto();
        result.questId = questId != null ? questId : "";
        result.networkId = networkId;

        if (!Config.webQuestClaimEnabled) {
            return fail(result, "claim_disabled", "Quest reward claim is disabled");
        }
        if (!BqCompat.isFeatureEnabled()) {
            return fail(result, "bq_unavailable", "BetterQuesting is not available");
        }
        if (ownerUuid == null || ownerUuid.isEmpty() || questId == null || questId.isEmpty()) {
            return fail(result, "bad_request", "Missing owner or quest id");
        }

        EntityPlayerMP player = BqQuestingIdentity.resolvePlayer(ownerUuid);
        if (player == null) {
            return fail(result, "no_player", "Owner player context unavailable");
        }

        UUID qUuid = parseUuid(questId);
        if (qUuid == null) {
            return fail(result, "bad_quest_id", "Invalid quest id");
        }
        Object quest = BqApiFacade.getQuest(qUuid);
        if (quest == null) {
            return fail(result, "not_found", "Quest not found");
        }

        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        if (detail == null || detail.questId == null || detail.questId.isEmpty()) {
            return fail(result, "not_found", "Quest not found");
        }
        if (!"UNCLAIMED".equals(detail.state) && !detail.canClaim) {
            return fail(result, "not_unclaimed", "Quest rewards are not claimable now");
        }
        if (!detail.webClaimable) {
            String reason = detail.claimBlockReason != null && !detail.claimBlockReason.isEmpty()
                ? detail.claimBlockReason
                : "in_game_only";
            return fail(result, reason, claimBlockMessage(reason));
        }

        Map<String, Integer> selection = choices != null ? choices : new HashMap<String, Integer>();
        String choiceErr = validateChoices(detail, selection);
        if (choiceErr != null) {
            return fail(result, "choice_required", choiceErr);
        }

        UUID questingUuid = BqQuestingIdentity.resolveQuestingUuid(player);
        if (questingUuid == null) {
            return fail(result, "no_questing_uuid", "Could not resolve questing UUID");
        }

        if (!BqApiFacade.applyChoiceSelections(quest, questingUuid, selection)) {
            return fail(result, "choice_apply_failed", "Failed to apply choice selections");
        }

        if (!BqApiFacade.canClaimQuest(quest, player)) {
            return fail(result, "cannot_claim", "BetterQuesting refused claim (missing choice or not ready)");
        }

        List<ItemStack> expected = BqApiFacade.collectExpectedClaimStacks(quest, questingUuid);
        if (expected == null) {
            return fail(result, "non_item_reward", "Rewards are not pure item/choice stacks");
        }

        IGrid grid = WebAeOwnerContext.getGrid(ownerUuid, networkId);
        if (grid == null) {
            return fail(result, "no_network", "AE network not found for owner");
        }
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) {
            return fail(result, "no_storage", "AE storage grid unavailable");
        }
        PlayerSource source = new PlayerSource(player, null);

        if (!expected.isEmpty() && !simulateInjectAll(storageGrid, source, expected)) {
            return fail(result, "ae_full", "AE network cannot accept all reward items; free space and retry");
        }

        int neededSlots = countNeededSlots(expected);
        int emptySlots = countEmptyMainSlots(player.inventory);
        if (neededSlots > emptySlots) {
            return fail(
                result,
                "inventory_full",
                "Player inventory needs " + neededSlots
                    + " empty slots for temporary claim staging (has "
                    + emptySlots
                    + ")");
        }

        ItemStack[] before = snapshotMainInventory(player.inventory);
        boolean claimed = BqApiFacade.claimQuestRewards(quest, player);
        if (!claimed) {
            return fail(result, "claim_failed", "BetterQuesting claimReward failed");
        }

        List<ItemStack> gained = diffInventory(before, player.inventory);
        List<ItemStack> toDeliver = gained.isEmpty() && !expected.isEmpty() ? copyStacks(expected) : gained;
        if (!toDeliver.isEmpty()) {
            removeStacksFromInventory(player.inventory, toDeliver);
            List<ItemStack> leftovers = injectAll(storageGrid, source, toDeliver, result.delivered);
            if (!leftovers.isEmpty()) {
                // Race: claim already marked. Prefer returning leftovers to the player inventory.
                for (ItemStack left : leftovers) {
                    if (left == null || left.stackSize <= 0) {
                        continue;
                    }
                    if (!player.inventory.addItemStackToInventory(left.copy())) {
                        player.dropPlayerItemWithRandomChoice(left.copy(), false);
                    }
                }
                AdvanceDataMonitor.LOG.error(
                    "[WebAE Quest] Claim {} for owner {} network {}: AE inject leftover after successful BQ claim; returned to player inventory/drops",
                    questId,
                    ownerUuid,
                    Integer.valueOf(networkId));
                result.success = true;
                result.code = "partial_ae";
                result.message = "Claimed, but AE could not take all items; remainder returned to player inventory";
                result.newState = readState(questId, player);
                return result;
            }
        } else if (expected.isEmpty()) {
            // Quest with zero item stacks (should be rare for webClaimable).
            result.delivered.clear();
        }

        result.success = true;
        result.code = "ok";
        result.message = "Rewards claimed into AE network";
        result.newState = readState(questId, player);
        return result;
    }

    private static String readState(String questId, EntityPlayerMP player) {
        QuestDetailDto detail = QuestDataCollector.collectQuestDetail(questId, player);
        return detail != null && detail.state != null ? detail.state : "";
    }

    private static String validateChoices(QuestDetailDto detail, Map<String, Integer> selection) {
        Map<String, Integer> choiceCounts = new HashMap<String, Integer>();
        for (QuestRewardDto reward : detail.rewards) {
            if (reward == null || !reward.choiceOption) {
                continue;
            }
            Integer max = choiceCounts.get(reward.rewardId);
            int idx = reward.choiceIndex;
            if (max == null || idx > max.intValue()) {
                choiceCounts.put(reward.rewardId, Integer.valueOf(idx));
            }
        }
        for (Map.Entry<String, Integer> entry : choiceCounts.entrySet()) {
            Integer picked = selection.get(entry.getKey());
            if (picked == null) {
                return "Missing selection for choice reward " + entry.getKey();
            }
            if (picked.intValue() < 0 || picked.intValue() > entry.getValue()
                .intValue()) {
                return "Invalid selection for choice reward " + entry.getKey();
            }
        }
        return null;
    }

    private static int countNeededSlots(List<ItemStack> stacks) {
        int slots = 0;
        if (stacks == null) {
            return 0;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) {
                continue;
            }
            int max = Math.max(1, stack.getMaxStackSize());
            int remaining = stack.stackSize;
            while (remaining > 0) {
                slots++;
                remaining -= Math.min(remaining, max);
            }
        }
        return slots;
    }

    private static int countEmptyMainSlots(InventoryPlayer inv) {
        if (inv == null || inv.mainInventory == null) {
            return 0;
        }
        int empty = 0;
        for (int i = 0; i < inv.mainInventory.length; i++) {
            if (inv.mainInventory[i] == null) {
                empty++;
            }
        }
        return empty;
    }

    private static boolean simulateInjectAll(IStorageGrid storageGrid, PlayerSource source, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            IAEItemStack request = toAeStack(stack);
            if (request == null) {
                return false;
            }
            IAEItemStack leftover = storageGrid.getItemInventory()
                .injectItems(request, Actionable.SIMULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> injectAll(IStorageGrid storageGrid, PlayerSource source, List<ItemStack> stacks,
        List<String> delivered) {
        List<ItemStack> leftovers = new ArrayList<ItemStack>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            IAEItemStack request = toAeStack(stack);
            if (request == null) {
                leftovers.add(stack.copy());
                continue;
            }
            IAEItemStack leftover = storageGrid.getItemInventory()
                .injectItems(request, Actionable.MODULATE, source);
            long accepted = request.getStackSize();
            if (leftover != null && leftover.getStackSize() > 0) {
                accepted -= leftover.getStackSize();
                ItemStack left = stack.copy();
                left.stackSize = (int) Math.min(Integer.MAX_VALUE, leftover.getStackSize());
                leftovers.add(left);
            }
            if (accepted > 0 && delivered != null) {
                String name = stack.getDisplayName();
                delivered.add((name != null ? name : "?") + " x" + accepted);
            }
        }
        return leftovers;
    }

    private static IAEItemStack toAeStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        ItemStack unit = stack.copy();
        int count = Math.max(1, stack.stackSize);
        unit.stackSize = 1;
        IAEItemStack ae = AEApi.instance()
            .storage()
            .createItemStack(unit);
        if (ae == null) {
            return null;
        }
        ae.setStackSize(count);
        return ae;
    }

    private static ItemStack[] snapshotMainInventory(InventoryPlayer inv) {
        ItemStack[] out = new ItemStack[inv.mainInventory.length];
        for (int i = 0; i < inv.mainInventory.length; i++) {
            ItemStack stack = inv.mainInventory[i];
            out[i] = stack != null ? stack.copy() : null;
        }
        return out;
    }

    private static List<ItemStack> diffInventory(ItemStack[] before, InventoryPlayer after) {
        List<ItemStack> gained = new ArrayList<ItemStack>();
        if (before == null || after == null || after.mainInventory == null) {
            return gained;
        }
        int len = Math.min(before.length, after.mainInventory.length);
        for (int i = 0; i < len; i++) {
            ItemStack prev = before[i];
            ItemStack now = after.mainInventory[i];
            if (now == null) {
                continue;
            }
            if (prev == null) {
                gained.add(now.copy());
                continue;
            }
            if (sameItem(prev, now)) {
                if (now.stackSize > prev.stackSize) {
                    ItemStack delta = now.copy();
                    delta.stackSize = now.stackSize - prev.stackSize;
                    gained.add(delta);
                }
            } else {
                // Slot replaced — treat entire new stack as gained (rare during claim).
                gained.add(now.copy());
            }
        }
        return gained;
    }

    private static void removeStacksFromInventory(InventoryPlayer inv, List<ItemStack> toRemove) {
        if (inv == null || toRemove == null) {
            return;
        }
        for (ItemStack need : toRemove) {
            if (need == null || need.stackSize <= 0) {
                continue;
            }
            int remaining = need.stackSize;
            for (int i = 0; i < inv.mainInventory.length && remaining > 0; i++) {
                ItemStack slot = inv.mainInventory[i];
                if (slot == null || !sameItem(slot, need)) {
                    continue;
                }
                int take = Math.min(remaining, slot.stackSize);
                slot.stackSize -= take;
                remaining -= take;
                if (slot.stackSize <= 0) {
                    inv.mainInventory[i] = null;
                }
            }
        }
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getItem() == null || b.getItem() == null) {
            return false;
        }
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (stacks == null) {
            return out;
        }
        for (ItemStack stack : stacks) {
            if (stack != null) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    private static QuestClaimResultDto fail(QuestClaimResultDto result, String code, String message) {
        result.success = false;
        result.code = code != null ? code : "error";
        result.message = message != null ? message : "Claim failed";
        return result;
    }

    private static String claimBlockMessage(String reason) {
        if ("non_item_reward".equals(reason)) {
            return "Quest has non-item rewards; claim in-game";
        }
        if ("claim_disabled".equals(reason)) {
            return "Quest reward claim is disabled";
        }
        if ("empty_item".equals(reason)) {
            return "Reward item could not be resolved";
        }
        return "Rewards must be claimed in-game";
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
