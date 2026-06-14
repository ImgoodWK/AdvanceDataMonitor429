package com.imgood.advancedatamonitor.assistant;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.fluids.FluidStack;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.handler.HandlerTick;
import com.imgood.advancedatamonitor.network.packet.PacketAssistantResponse;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceCraftingLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceDataMonitor;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceNetworkLink;
import com.imgood.advancedatamonitor.tileentity.TileEntityAdvanceStorageLink;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.item.AEItemStack;

public final class AssistantServerServices {

    private AssistantServerServices() {}

    public static List<CraftingCandidate> craftingCandidates(EntityPlayerMP player, String rawText, String target,
        long amount) {
        return craftingCandidates(player, rawText, target, amount, null);
    }

    private static List<CraftingCandidate> craftingCandidates(EntityPlayerMP player, String rawText, String target,
        long amount, String[] outSourceInfo) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server craft candidate request: player={}, raw='{}', target='{}', amount={}",
            player == null ? "null" : player.getCommandSenderName(),
            safe(rawText),
            safe(target),
            amount);
        ConnectorSource<TileEntityAdvanceCraftingLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceCraftingLink.class,
            32);
        if (source.isEmpty()) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Craft candidate request failed: no AdvanceCraftingLink found.");
            return new ArrayList<>();
        }
        if (outSourceInfo != null && outSourceInfo.length > 0) {
            outSourceInfo[0] = source.sourceDescription("zh_CN");
        }
        List<CraftingCandidate> allCandidates = new ArrayList<>();
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        for (TileEntityAdvanceCraftingLink link : source.connectors) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Using CraftingLink at {},{},{}", link.xCoord, link.yCoord, link.zCoord);
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                ICraftingGrid craftingGrid = grid == null ? null : grid.getCache(ICraftingGrid.class);
                if (craftingGrid == null) {
                    AdvanceDataMonitor.LOG.info(
                        "[ADM Assistant] Craft candidate: ICraftingGrid unavailable at {},{}",
                        link.xCoord,
                        link.yCoord);
                    continue;
                }
                PatternLookup patternLookup = lookupPatterns(
                    craftingGrid,
                    target,
                    Math.max(1L, amount),
                    Integer.MAX_VALUE);
                for (CraftingCandidate c : patternLookup.candidates) {
                    if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                    String key = candidateKey(c.toItemStack());
                    if (unique.put(key, c.toItemStack()) != null) continue;
                    allCandidates.add(c);
                }
                if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                if (allCandidates.isEmpty() && !patternLookup.usedStrongApi) {
                    Collection<?> craftables = reflectCraftables(craftingGrid);
                    if (craftables != null) {
                        AdvanceDataMonitor.LOG
                            .info("[ADM Assistant] Raw reflected craftables count={}", craftables.size());
                        List<CraftingCandidate> reflected = craftingCandidatesFromReflected(
                            craftables,
                            target,
                            Math.max(1L, amount));
                        for (CraftingCandidate c : reflected) {
                            if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                            String key = candidateKey(c.toItemStack());
                            if (unique.put(key, c.toItemStack()) != null) continue;
                            allCandidates.add(c);
                        }
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG
                    .error("Failed to build AE2 crafting candidates from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        if (allCandidates.isEmpty()) {
            return allCandidates;
        }
        final String owner = ownerKey(player);
        allCandidates.sort(new Comparator<CraftingCandidate>() {

            @Override
            public int compare(CraftingCandidate a, CraftingCandidate b) {
                int memory = Integer.compare(
                    OrderMemoryStore.instance()
                        .score(owner, rawText, b),
                    OrderMemoryStore.instance()
                        .score(owner, rawText, a));
                return memory != 0 ? memory : a.displayName.compareToIgnoreCase(b.displayName);
            }
        });
        List<CraftingCandidate> reindexed = new ArrayList<>();
        int i = 1;
        for (CraftingCandidate candidate : allCandidates) {
            reindexed.add(new CraftingCandidate(i++, candidate.toItemStack(), candidate.amount));
        }
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Craft candidate result count={}", reindexed.size());
        return reindexed;
    }

    public static List<CraftingCandidate> withdrawCandidates(EntityPlayerMP player, String rawText, String target,
        long amount) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server withdraw candidate request: player={}, raw='{}', target='{}', amount={}",
            player == null ? "null" : player.getCommandSenderName(),
            safe(rawText),
            safe(target),
            amount);
        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Withdraw candidate request failed: no AdvanceStorageLink found.");
            return new ArrayList<>();
        }
        String query = normalizeStorageQuery(target, AssistantIntent.STORAGE_SCOPE_ITEMS);
        List<CraftingCandidate> allCandidates = new ArrayList<>();
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        int index = 1;
        for (TileEntityAdvanceStorageLink link : source.connectors) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Using StorageLink at {},{},{}", link.xCoord, link.yCoord, link.zCoord);
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                IStorageGrid storageGrid = grid == null ? null : grid.getCache(IStorageGrid.class);
                if (storageGrid == null) {
                    AdvanceDataMonitor.LOG.info(
                        "[ADM Assistant] Withdraw candidate: IStorageGrid unavailable at {},{}",
                        link.xCoord,
                        link.yCoord);
                    continue;
                }
                for (IAEItemStack item : storageGrid.getItemInventory()
                    .getStorageList()) {
                    if (item == null || item.getStackSize() <= 0) {
                        continue;
                    }
                    ItemStack stack = item.getItemStack();
                    if (stack == null || stack.getItem() == null) {
                        continue;
                    }
                    if (!query.isEmpty() && !ItemStackUtils.fuzzyNameMatches(stack, query)) {
                        continue;
                    }
                    String key = candidateKey(stack);
                    if (unique.put(key, stack) != null) continue;
                    allCandidates.add(new CraftingCandidate(index++, stack, item.getStackSize()));
                    if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG
                    .error("Failed to build AE2 withdraw candidates from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Withdraw candidate result count={}", allCandidates.size());
        return allCandidates;
    }

    public static List<AssistantOrderLine> batchWithdrawCandidates(EntityPlayerMP player, String rawText,
        List<AssistantOrderLine> lines) {
        List<AssistantOrderLine> result = new ArrayList<AssistantOrderLine>();
        if (lines == null) {
            return result;
        }
        for (AssistantOrderLine line : lines) {
            AssistantOrderLine resolved = line.copyWithoutCandidates();
            resolved.setCandidates(withdrawCandidates(player, rawText, line.target, line.amount));
            result.add(resolved);
        }
        return result;
    }

    public static WithdrawSubmitOutcome submitWithdraw(EntityPlayerMP player, CraftingCandidate candidate, long amount,
        String rawText, String locale, boolean confirmPartial) {
        if (candidate == null) {
            return new WithdrawSubmitOutcome(
                WithdrawSubmitOutcome.Kind.FAILURE,
                withdrawFailed(locale, text(locale, "候选项为空。", "empty candidate.")));
        }
        if (amount <= 0L || amount > Config.assistantMaxWithdrawAmount) {
            return new WithdrawSubmitOutcome(
                WithdrawSubmitOutcome.Kind.FAILURE,
                withdrawFailed(
                    locale,
                    text(locale, "数量无效或超过取出配置上限。", "invalid amount or above configured withdraw limit.")));
        }
        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            return new WithdrawSubmitOutcome(
                WithdrawSubmitOutcome.Kind.FAILURE,
                withdrawFailed(locale, text(locale, "附近没有 Advance Storage Link。", "no nearby Advance Storage Link.")));
        }
        // Try each connector; use the first one that has the item
        WithdrawSubmitOutcome bestOutcome = null;
        for (TileEntityAdvanceStorageLink link : source.connectors) {
            ItemStack stack = candidate.toItemStack();
            if (stack == null || stack.getItem() == null) continue;
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                IStorageGrid storageGrid = grid == null ? null : grid.getCache(IStorageGrid.class);
                if (storageGrid == null) continue;
                long storageAmount = getStorageAmount(link, stack);
                if (storageAmount <= 0L) continue;
                long requestedAmount = Math.min(amount, storageAmount);
                long inventoryFit = PlayerInventoryUtil.computeFitAmount(player, stack, requestedAmount);
                if (inventoryFit <= 0L) {
                    bestOutcome = new WithdrawSubmitOutcome(
                        WithdrawSubmitOutcome.Kind.FAILURE,
                        withdrawFailed(
                            locale,
                            text(locale, "背包已满，无法取出物品。", "inventory is full; cannot withdraw items.")));
                    continue;
                }
                if (inventoryFit < requestedAmount && !confirmPartial) {
                    String message = text(
                        locale,
                        "您的背包最多还能放 " + inventoryFit
                            + " 个 "
                            + stack.getDisplayName()
                            + "（请求 "
                            + requestedAmount
                            + "，库存 "
                            + storageAmount
                            + "）。是否取出 "
                            + inventoryFit
                            + " 个？请回复确认。",
                        "Your inventory can only fit " + inventoryFit
                            + " more "
                            + stack.getDisplayName()
                            + " (requested "
                            + requestedAmount
                            + ", stored "
                            + storageAmount
                            + "). Withdraw "
                            + inventoryFit
                            + "? Say confirm.");
                    return new WithdrawSubmitOutcome(
                        WithdrawSubmitOutcome.Kind.PARTIAL_CONFIRM,
                        message,
                        candidate,
                        requestedAmount,
                        inventoryFit,
                        storageAmount);
                }
                long withdrawAmount = Math.min(requestedAmount, inventoryFit);
                long inserted = extractToPlayer(player, storageGrid, stack, withdrawAmount);
                if (inserted > 0L) {
                    String success = text(
                        locale,
                        "已取出到背包：" + stack.getDisplayName()
                            + " x"
                            + inserted
                            + (inserted < requestedAmount
                                ? "（请求 " + requestedAmount + "，库存剩余约 " + Math.max(0L, storageAmount - inserted) + "）"
                                : "")
                            + "。",
                        "Withdrawn to inventory: " + stack.getDisplayName()
                            + " x"
                            + inserted
                            + (inserted < requestedAmount
                                ? " (requested " + requestedAmount
                                    + ", about "
                                    + Math.max(0L, storageAmount - inserted)
                                    + " remain in storage)"
                                : "")
                            + ".");
                    return new WithdrawSubmitOutcome(WithdrawSubmitOutcome.Kind.SUCCESS, success);
                }
                bestOutcome = new WithdrawSubmitOutcome(
                    WithdrawSubmitOutcome.Kind.FAILURE,
                    withdrawFailed(locale, text(locale, "从 AE2 取出失败。", "failed to withdraw from AE2.")));
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("Failed to withdraw from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        if (bestOutcome != null) return bestOutcome;
        return new WithdrawSubmitOutcome(
            WithdrawSubmitOutcome.Kind.FAILURE,
            withdrawFailed(locale, text(locale, "在所有连接器中均未找到该物品。", "item not found in any connector.")));
    }

    public static String submitBatchWithdraw(EntityPlayerMP player, String rawText, List<AssistantOrderLine> lines,
        String locale) {
        if (lines == null || lines.isEmpty()) {
            return text(locale, "批量取出失败：没有取出行。", "Batch withdraw failed: no withdraw lines.");
        }
        for (AssistantOrderLine line : lines) {
            if (line == null || line.selectedOrFirstCandidate() == null) {
                return text(
                    locale,
                    "批量取出失败：第 " + (line == null ? "?" : line.lineIndex) + " 行没有候选项。没有取出任何物品。",
                    "Batch withdraw failed: no candidate for line " + (line == null ? "?" : line.lineIndex)
                        + ". Nothing was withdrawn.");
            }
            if (line.amount <= 0L || line.amount > Config.assistantMaxWithdrawAmount) {
                return text(
                    locale,
                    "批量取出失败：第 " + line.lineIndex + " 行数量无效。没有取出任何物品。",
                    "Batch withdraw failed: invalid amount for line " + line.lineIndex + ". Nothing was withdrawn.");
            }
        }
        StringBuilder builder = new StringBuilder(text(locale, "批量取出结果：", "Batch withdraw results:"));
        for (AssistantOrderLine line : lines) {
            CraftingCandidate candidate = line.selectedOrFirstCandidate();
            WithdrawSubmitOutcome outcome = submitWithdraw(player, candidate, line.amount, rawText, locale, true);
            builder.append("\n")
                .append(line.lineIndex)
                .append(". ")
                .append(candidate.displayName)
                .append(" x")
                .append(line.amount)
                .append(" -> ")
                .append(outcome.message);
            if (outcome.kind == WithdrawSubmitOutcome.Kind.PARTIAL_CONFIRM) {
                return text(
                    locale,
                    "批量取出暂停：第 " + line.lineIndex + " 行背包空间不足。请先确认单项取出后再继续。\n" + outcome.message,
                    "Batch withdraw paused: line " + line.lineIndex
                        + " needs inventory confirmation. Confirm the single withdraw first.\n"
                        + outcome.message);
            }
            if (outcome.kind == WithdrawSubmitOutcome.Kind.FAILURE) {
                return text(
                    locale,
                    "批量取出失败：第 " + line.lineIndex + " 行失败。已停止后续取出。\n" + outcome.message,
                    "Batch withdraw failed at line " + line.lineIndex
                        + ". Stopped remaining lines.\n"
                        + outcome.message);
            }
        }
        return builder.toString();
    }

    private static long getStorageAmount(TileEntityAdvanceStorageLink link, ItemStack stack) {
        try {
            return link.getItemCountInNetwork(stack);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("Failed to read AE2 storage amount", e);
            return 0L;
        }
    }

    private static long extractToPlayer(EntityPlayerMP player, IStorageGrid storageGrid, ItemStack prototype,
        long amount) {
        if (player == null || storageGrid == null || prototype == null || prototype.getItem() == null || amount <= 0L) {
            return 0L;
        }
        IAEItemStack request = AEApi.instance()
            .storage()
            .createItemStack(prototype);
        if (request == null) {
            return 0L;
        }
        request.setStackSize(amount);
        PlayerSource source = new PlayerSource(player, null);
        IAEItemStack extracted = storageGrid.getItemInventory()
            .extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0L) {
            return 0L;
        }
        ItemStack stack = extracted.getItemStack();
        if (stack == null || stack.stackSize <= 0) {
            return 0L;
        }
        return PlayerInventoryUtil.insertIntoPlayerInventory(player, stack);
    }

    private static String withdrawFailed(String locale, String reason) {
        return text(
            locale,
            "取出失败：" + (reason == null ? "" : reason),
            "Withdraw failed: " + (reason == null ? "" : reason));
    }

    public static List<AssistantOrderLine> batchCraftingCandidates(EntityPlayerMP player, String rawText,
        List<AssistantOrderLine> lines) {
        List<AssistantOrderLine> result = new ArrayList<AssistantOrderLine>();
        if (lines == null) {
            return result;
        }
        for (AssistantOrderLine line : lines) {
            AssistantOrderLine resolved = line.copyWithoutCandidates();
            resolved.setCandidates(craftingCandidates(player, rawText, line.target, line.amount));
            result.add(resolved);
        }
        return result;
    }

    public static String submitBatchCraft(EntityPlayerMP player, String rawText, List<AssistantOrderLine> lines) {
        return submitBatchCraft(player, rawText, lines, "zh_CN");
    }

    public static String submitBatchCraft(EntityPlayerMP player, String rawText, List<AssistantOrderLine> lines,
        String locale) {
        if (lines == null || lines.isEmpty()) {
            return text(locale, "批量订单失败：没有订单行。", "Batch order failed: no order lines.");
        }
        for (AssistantOrderLine line : lines) {
            if (line == null || line.selectedOrFirstCandidate() == null) {
                return text(
                    locale,
                    "批量订单失败：第 " + (line == null ? "?" : line.lineIndex) + " 行没有候选项。没有提交任何任务。",
                    "Batch order failed: no candidate for line " + (line == null ? "?" : line.lineIndex)
                        + ". No jobs were submitted.");
            }
            if (line.amount <= 0 || line.amount > Config.assistantMaxOrderAmount) {
                return text(
                    locale,
                    "批量订单失败：第 " + line.lineIndex + " 行数量无效。没有提交任何任务。",
                    "Batch order failed: invalid amount for line " + line.lineIndex + ". No jobs were submitted.");
            }
        }
        if (AssistantCraftJobManager.instance()
            .availableSlots(player) < lines.size()) {
            return text(
                locale,
                "批量订单失败：可用 AE2 计算槽不足，无法提交 " + lines.size() + " 行任务。没有提交任何任务。",
                "Batch order failed: not enough available AE2 calculation slots for " + lines.size()
                    + " line(s). No jobs were submitted.");
        }
        StringBuilder builder = new StringBuilder(text(locale, "批量订单已开始：", "Batch order started:"));
        for (AssistantOrderLine line : lines) {
            CraftingCandidate candidate = line.selectedOrFirstCandidate();
            String result = submitCraft(player, candidate, line.amount, rawText, locale);
            builder.append("\n")
                .append(line.lineIndex)
                .append(". ")
                .append(candidate.displayName)
                .append(" x")
                .append(line.amount)
                .append(" -> ")
                .append(result);
        }
        return builder.toString();
    }

    public static String cancelPendingJobs(EntityPlayerMP player) {
        return cancelPendingJobs(player, "zh_CN");
    }

    public static String cancelPendingJobs(EntityPlayerMP player, String locale) {
        return AssistantCraftJobManager.instance()
            .cancel(player, locale);
    }

    public static String submitCraft(EntityPlayerMP player, CraftingCandidate candidate, long amount, String rawText) {
        return submitCraft(player, candidate, amount, rawText, "zh_CN");
    }

    public static String submitCraft(EntityPlayerMP player, CraftingCandidate candidate, long amount, String rawText,
        String locale) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server craft submit request: player={}, candidate='{}', registry='{}', amount={}, raw='{}'",
            player == null ? "null" : player.getCommandSenderName(),
            candidate == null ? "null" : safe(candidate.displayName),
            candidate == null ? "" : safe(candidate.registryName),
            amount,
            safe(rawText));
        if (candidate == null) {
            AssistantDebugLog.append("server-submit", "status=FAIL, reason=empty-candidate");
            return orderFailed(locale, text(locale, "候选项为空。", "empty candidate."));
        }
        if (amount <= 0 || amount > Config.assistantMaxOrderAmount) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Craft submit rejected: invalid amount {} max {}",
                amount,
                Config.assistantMaxOrderAmount);
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=invalid-amount, amount=" + amount + ", max=" + Config.assistantMaxOrderAmount);
            return orderFailed(locale, text(locale, "数量无效或超过配置上限。", "invalid amount or above configured limit."));
        }
        ConnectorSource<TileEntityAdvanceCraftingLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceCraftingLink.class,
            32);
        if (source.isEmpty()) {
            AdvanceDataMonitor.LOG.info("[ADM Assistant] Craft submit failed: no AdvanceCraftingLink found.");
            AssistantDebugLog.append("server-submit", "status=FAIL, reason=no-crafting-link");
            return orderFailed(locale, text(locale, "附近没有 Advance Crafting Link。", "no nearby Advance Crafting Link."));
        }
        String lastError = null;
        for (TileEntityAdvanceCraftingLink link : source.connectors) {
            ItemStack stack = candidate.toItemStack();
            if (stack == null || stack.getItem() == null) {
                lastError = orderFailed(locale, text(locale, "候选物品无法还原。", "candidate item could not be restored."));
                continue;
            }
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                ICraftingGrid craftingGrid = grid == null ? null : grid.getCache(ICraftingGrid.class);
                if (craftingGrid == null) {
                    lastError = orderFailed(locale, text(locale, "AE2 合成网络不可用。", "AE2 crafting grid unavailable."));
                    continue;
                }
                CraftingCandidate serverCandidate = resolveServerCandidate(craftingGrid, candidate, amount);
                if (serverCandidate == null) {
                    lastError = orderFailed(
                        locale,
                        text(
                            locale,
                            "所选物品在此 AE2 网络中已不再可合成。",
                            "selected item is no longer craftable in this AE2 network."));
                    continue;
                }
                stack = serverCandidate.toItemStack();
                String apiResult = trySubmit(
                    craftingGrid,
                    grid,
                    link,
                    player,
                    serverCandidate,
                    stack,
                    amount,
                    rawText,
                    locale);
                if (apiResult != null) {
                    return apiResult;
                }
                lastError = orderFailed(
                    locale,
                    text(
                        locale,
                        "AE2 没有接受 " + stack.getDisplayName() + " x" + amount + " 的合成任务。",
                        "AE2 did not accept the crafting job for " + stack.getDisplayName() + " x" + amount + "."));
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("Failed to submit AE2 crafting job", e);
                lastError = orderFailed(locale, e.getMessage());
            }
        }
        return lastError != null ? lastError
            : orderFailed(locale, text(locale, "在所有连接器中均未找到可合成该物品的网络。", "no network found that can craft this item."));
    }

    public static String query(EntityPlayerMP player, AssistantIntentType type, String rawText, String target,
        long amount) {
        return query(player, type, rawText, target, amount, "zh_CN");
    }

    public static String queryStorage(EntityPlayerMP player, String rawText, String target, int storageScope,
        String locale) {
        int effectiveScope = storageScope;
        if (effectiveScope < AssistantIntent.STORAGE_SCOPE_ALL
            || effectiveScope > AssistantIntent.STORAGE_SCOPE_FLUIDS) {
            effectiveScope = detectStorageScope(rawText, target);
        }
        return storageSummary(player, target, effectiveScope, locale);
    }

    public static String query(EntityPlayerMP player, AssistantIntentType type, String rawText, String target,
        long amount, String locale) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server query request: player={}, type={}, raw='{}', target='{}', amount={}, locale={}",
            player == null ? "null" : player.getCommandSenderName(),
            type,
            safe(rawText),
            safe(target),
            amount,
            safe(locale));
        AssistantDebugLog.append(
            "server-query",
            "player=" + (player == null ? "null" : player.getCommandSenderName())
                + ", type="
                + type
                + ", raw='"
                + safe(rawText)
                + "', target='"
                + safe(target)
                + "', amount="
                + amount
                + ", locale="
                + safe(locale));
        switch (type) {
            case QUERY_STORAGE:
                return storageSummary(player, target, detectStorageScope(rawText, target), locale);
            case QUERY_RECIPE:
                return recipeSummary(player, target, amount, locale);
            case QUERY_POWER:
                return WirelessPowerQuery.query(player, zh(locale));
            case QUERY_STEAM:
                return WirelessSteamQuery.query(player, zh(locale));
            case QUERY_WEATHER:
                return weatherSummary(player, locale);
            case QUERY_TIME:
                return timeSummary(player, locale);
            case QUERY_POSITION:
                return positionSummary(player, locale);
            case QUERY_BIOME:
                return biomeSummary(player, locale);
            case QUERY_INVENTORY:
                return inventorySummary(player, target, amount, locale);
            case QUERY_NETWORK:
                return networkSummary(player, locale);
            case QUERY_JOBS:
                return AssistantCraftJobManager.instance()
                    .summary(player, locale);
            case PLAN_CREATE:
            case PLAN_ADD:
                return PlannerServerService.addEntry(player, rawText, target, locale);
            case PLAN_LIST:
                return PlannerServerService.listEntries(player, locale);
            case PLAN_COMPLETE:
                return PlannerServerService.completeEntry(player, (int) amount, locale);
            case PLAN_DELETE:
                return PlannerServerService.deleteEntry(player, (int) amount, locale);
            case PLAN_MODIFY:
                return PlannerServerService.modifyEntry(player, (int) amount, target, locale);
            case QUERY_ITEM_COUNT:
                // Handled specially in PacketAssistantAction to return candidates
                return queryItemCountMessage(player, rawText, target, locale);
            case QUERY_BYTES:
                return bytesSummary(player, locale);
            default:
                return zh(locale) ? msg("unknownQuery", "Unknown query.") : "Unknown query.";
        }
    }

    private static final int MAX_STORAGE_CANDIDATES = 200;

    /**
     * Query AE2 storage network for item/fluid quantities.
     * Returns a List of CraftingCandidate with actual stock amounts.
     * Supports: all items (empty target), specific name, fuzzy name matching.
     */
    public static List<CraftingCandidate> queryItemCount(EntityPlayerMP player, String rawText, String target,
        String locale) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Server item count query: player={}, raw='{}', target='{}'",
            player == null ? "null" : player.getCommandSenderName(),
            safe(rawText),
            safe(target));
        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Item count query failed: no AdvanceStorageLink found.");
            return new ArrayList<>();
        }
        String query = normalizeStorageQuery(target, AssistantIntent.STORAGE_SCOPE_ALL);
        boolean searchFluids = query.contains("fluid") || query.contains("mB")
            || AssistantTextNormalizer.containsAny(query.toLowerCase(), "流体", "液体");
        String cleanQuery = query;
        // Strip fluid scope words to get the actual search term
        if (searchFluids) {
            cleanQuery = AssistantTextNormalizer.removeWords(
                query,
                "fluid", "fluids", "mB", "流体", "液体");
            cleanQuery = stripPunctuation(stripModalParticles(cleanQuery)).trim();
        }
        List<CraftingCandidate> allCandidates = new ArrayList<>();
        Map<String, ItemStack> uniqueItems = new LinkedHashMap<>();
        Map<String, String> uniqueFluids = new LinkedHashMap<>();
        int index = 1;
        for (TileEntityAdvanceStorageLink link : source.connectors) {
            try {
                IGrid grid = link.getProxy().getGrid();
                IStorageGrid storageGrid = grid == null ? null : grid.getCache(IStorageGrid.class);
                if (storageGrid == null) continue;
                // Query items
                for (IAEItemStack item : storageGrid.getItemInventory().getStorageList()) {
                    if (item == null || item.getStackSize() <= 0) continue;
                    ItemStack stack = item.getItemStack();
                    if (stack == null || stack.getItem() == null) continue;
                    if (!cleanQuery.isEmpty() && !ItemStackUtils.fuzzyNameMatches(stack, cleanQuery)) continue;
                    String key = candidateKey(stack);
                    if (uniqueItems.containsKey(key)) continue;
                    uniqueItems.put(key, stack);
                    allCandidates.add(new CraftingCandidate(index++, stack, item.getStackSize()));
                    if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                }
                if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                // Query fluids
                if (searchFluids || cleanQuery.isEmpty()) {
                    try {
                        for (IAEFluidStack fluid : storageGrid.getFluidInventory().getStorageList()) {
                            if (fluid == null || fluid.getStackSize() <= 0) continue;
                            FluidStack fluidStack = fluid.getFluidStack();
                            String name = fluidStack == null || fluidStack.getFluid() == null ? ""
                                : fluidStack.getLocalizedName().toLowerCase();
                            if (!cleanQuery.isEmpty() && !name.contains(cleanQuery)) continue;
                            String displayName = fluidStack == null ? "Unknown Fluid"
                                : fluidStack.getLocalizedName();
                            String key = displayName;
                            if (uniqueFluids.containsKey(key)) continue;
                            uniqueFluids.put(key, displayName);
                            // Create a dummy ItemStack for fluid display
                            // Use the count suffix to indicate fluid (mB)
                            allCandidates.add(new CraftingCandidate(
                                index++,
                                createFluidDisplayStack(displayName),
                                fluid.getStackSize()));
                            if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                        }
                    } catch (Throwable fluidFailure) {
                        AdvanceDataMonitor.LOG.debug("Fluid inventory unavailable", fluidFailure);
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG
                    .error("Failed to query AE2 storage from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        // Sort by quantity descending
        allCandidates.sort(new Comparator<CraftingCandidate>() {
            @Override
            public int compare(CraftingCandidate a, CraftingCandidate b) {
                return Long.compare(b.amount, a.amount);
            }
        });
        // Re-index after sorting
        List<CraftingCandidate> reindexed = new ArrayList<>();
        int i = 1;
        for (CraftingCandidate c : allCandidates) {
            reindexed.add(new CraftingCandidate(i++, c.toItemStack(), c.amount));
        }
        AdvanceDataMonitor.LOG.info("[ADM Assistant] Item count query result count={}", reindexed.size());
        return reindexed;
    }

    private static String queryItemCountMessage(EntityPlayerMP player, String rawText, String target,
        String locale) {
        // This is only used as fallback; the main response goes through candidates
        return zh(locale)
            ? "查询物品数量失败：请通过候选项列表查看结果。"
            : "Item count query failed: check candidate list for results.";
    }

    /**
     * Create a dummy ItemStack for fluid display purposes.
     * Uses a water bucket as placeholder so the client can render something.
     */
    private static ItemStack createFluidDisplayStack(String displayName) {
        ItemStack dummy = new ItemStack(net.minecraft.init.Blocks.glass, 1);
        dummy.setStackDisplayName(displayName + " (Fluid)");
        return dummy;
    }

    /**
     * Query AE2 storage network byte usage with infinite cell detection.
     * Returns formatted bilingual text showing item/fluid byte usage, total bytes,
     * percentage, and whether infinite storage cells are present.
     */
    private static String bytesSummary(EntityPlayerMP player, String locale) {
        boolean chinese = zh(locale);
        ConnectorSource<TileEntityAdvanceNetworkLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceNetworkLink.class,
            32);
        if (source.isEmpty()) {
            return (chinese ? "字节查询失败：附近没有 Advance Network Link。"
                : "Byte query failed: no nearby Advance Network Link.");
        }
        String connectorInfo = "\n(" + source.sourceDescription(locale) + ")";

        long itemTotalBytes = 0L;
        long itemUsedBytes = 0L;
        long fluidTotalBytes = 0L;
        long fluidUsedBytes = 0L;
        boolean hasInfiniteItemCells = false;
        boolean hasInfiniteFluidCells = false;

        // First pass: read byte data from NetworkLink tiles
        for (TileEntityAdvanceNetworkLink link : source.connectors) {
            try {
                itemTotalBytes += link.getItemTotalBytes();
                itemUsedBytes += link.getItemUsedBytes();
                fluidTotalBytes += link.getFluidTotalBytes();
                fluidUsedBytes += link.getFluidUsedBytes();
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("Failed to read bytes from NetworkLink at {},{},{}",
                    link.xCoord, link.yCoord, link.zCoord, e);
            }
        }

        // Second pass: scan individual storage cells to detect infinite cells
        // and also get byte data directly for more accuracy
        InfiniteCellScanResult scanResult = scanNetworkCellsForInfinite(player);
        hasInfiniteItemCells = scanResult.hasInfiniteItems;
        hasInfiniteFluidCells = scanResult.hasInfiniteFluids;

        // Use directly-scanned values if NetworkLink data appears stale
        if (scanResult.nonInfiniteItemTotal > 0) {
            itemTotalBytes = scanResult.nonInfiniteItemTotal + (hasInfiniteItemCells ? scanResult.infiniteItemBytes : 0);
            itemUsedBytes = scanResult.nonInfiniteItemUsed;
        }
        if (scanResult.nonInfiniteFluidTotal > 0) {
            fluidTotalBytes = scanResult.nonInfiniteFluidTotal + (hasInfiniteFluidCells ? scanResult.infiniteFluidBytes : 0);
            fluidUsedBytes = scanResult.nonInfiniteFluidUsed;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(chinese ? "AE2 存储字节占用详情："
            : "AE2 Storage Byte Usage Details:");

        // Item bytes section
        builder.append(chinese ? "\n\n物品存储：" : "\n\nItem Storage:");
        if (hasInfiniteItemCells) {
            builder.append(chinese ? "\n  无限存储元件：存在" : "\n  Infinite cells: present");
            if (itemUsedBytes > 0 || itemTotalBytes > 0) {
                builder.append(chinese ? "（非无限元件统计如下）" : " (non-infinite cell stats below)");
            }
            builder.append("\n  非无限元件：");
        }
        builder.append(chinese ? "\n  已用："
            : "\n  Used: ")
            .append(AssistantFormatter.formatBytes(itemUsedBytes));
        builder.append(chinese ? " / 总量："
            : " / Total: ")
            .append(AssistantFormatter.formatBytes(itemTotalBytes));

        // Calculate percentage for non-infinite cells
        long nonInfiniteItemTotal = scanResult.nonInfiniteItemTotal > 0
            ? scanResult.nonInfiniteItemTotal : itemTotalBytes;
        long nonInfiniteItemUsed = scanResult.nonInfiniteItemUsed > 0
            ? scanResult.nonInfiniteItemUsed : itemUsedBytes;
        if (nonInfiniteItemTotal > 0) {
            double itemPercent = (double) nonInfiniteItemUsed / (double) nonInfiniteItemTotal * 100.0;
            builder.append(chinese ? "  占用率：" : "  Usage: ")
                .append(String.format("%.1f%%", itemPercent));
            if (itemPercent > 90.0) {
                builder.append(chinese ? " (接近满载)" : " (nearly full)");
            }
        }

        // Fluid bytes section
        builder.append(chinese ? "\n\n流体存储：" : "\n\nFluid Storage:");
        if (hasInfiniteFluidCells) {
            builder.append(chinese ? "\n  无限存储元件：存在" : "\n  Infinite cells: present");
            if (fluidUsedBytes > 0 || fluidTotalBytes > 0) {
                builder.append(chinese ? "（非无限元件统计如下）" : " (non-infinite cell stats below)");
            }
            builder.append(chinese ? "\n  非无限元件：" : "\n  Non-infinite:");
        }
        builder.append(chinese ? "\n  已用："
            : "\n  Used: ")
            .append(AssistantFormatter.formatBytes(fluidUsedBytes));
        builder.append(chinese ? " / 总量："
            : " / Total: ")
            .append(AssistantFormatter.formatBytes(fluidTotalBytes));

        long nonInfiniteFluidTotal = scanResult.nonInfiniteFluidTotal > 0
            ? scanResult.nonInfiniteFluidTotal : fluidTotalBytes;
        long nonInfiniteFluidUsed = scanResult.nonInfiniteFluidUsed > 0
            ? scanResult.nonInfiniteFluidUsed : fluidUsedBytes;
        if (nonInfiniteFluidTotal > 0) {
            double fluidPercent = (double) nonInfiniteFluidUsed / (double) nonInfiniteFluidTotal * 100.0;
            builder.append(chinese ? "  占用率：" : "  Usage: ")
                .append(String.format("%.1f%%", fluidPercent));
            if (fluidPercent > 90.0) {
                builder.append(chinese ? " (接近满载)" : " (nearly full)");
            }
        }

        // Summary of infinite cells
        if (hasInfiniteItemCells || hasInfiniteFluidCells) {
            builder.append(chinese ? "\n\n注意：网络中存在无限存储元件：" : "\n\nNote: Infinite storage cells detected in network:");
            if (hasInfiniteItemCells) {
                builder.append(chinese ? "\n  - 无限物品存储元件" : "\n  - Infinite item storage cell(s)");
            }
            if (hasInfiniteFluidCells) {
                builder.append(chinese ? "\n  - 无限流体存储元件" : "\n  - Infinite fluid storage cell(s)");
            }
            builder.append(chinese ? "\n  无限元件不计入占用率统计。" : "\n  Infinite cells are excluded from usage percentage calculation.");
        }

        return builder.toString() + connectorInfo;
    }

    /**
     * Result of scanning network storage cells for infinite and non-infinite byte counts.
     */
    private static final class InfiniteCellScanResult {
        boolean hasInfiniteItems;
        boolean hasInfiniteFluids;
        long nonInfiniteItemTotal;
        long nonInfiniteItemUsed;
        long nonInfiniteFluidTotal;
        long nonInfiniteFluidUsed;
        long infiniteItemBytes;
        long infiniteFluidBytes;
    }

    /**
     * Scan all storage cells (drives and chests) on the AE2 network to detect
     * infinite storage cells (from AE2Things mod) and count byte usage separately.
     */
    private static InfiniteCellScanResult scanNetworkCellsForInfinite(EntityPlayerMP player) {
        InfiniteCellScanResult result = new InfiniteCellScanResult();
        // Find any connector that has AE2 grid access
        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            return result;
        }
        for (TileEntityAdvanceStorageLink link : source.connectors) {
            try {
                IGrid grid = link.getProxy().getGrid();
                if (grid == null) continue;
                for (Class<? extends IGridHost> clazz : grid.getMachinesClasses()) {
                    if (!IChestOrDrive.class.isAssignableFrom(clazz)) continue;
                    for (IGridNode node : grid.getMachines(clazz)) {
                        appeng.api.util.DimensionalCoord coord = node.getGridBlock().getLocation();
                        World world = coord.getWorld();
                        if (world == null) continue;
                        TileEntity te = world.getTileEntity(coord.x, coord.y, coord.z);
                        if (te == null) continue;
                        // Process drive or chest
                        if (te instanceof TileDrive) {
                            TileDrive drive = (TileDrive) te;
                            for (int i = 0; i < drive.getInternalInventory().getSizeInventory(); i++) {
                                ItemStack stack = drive.getInternalInventory().getStackInSlot(i);
                                if (stack != null) {
                                    classifyCell(result, stack);
                                }
                            }
                        } else if (te instanceof TileChest) {
                            TileChest chest = (TileChest) te;
                            ItemStack stack = chest.getInternalInventory().getStackInSlot(0);
                            if (stack != null) {
                                classifyCell(result, stack);
                            }
                        }
                    }
                }
                break; // Only scan one connector's grid
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.debug("Failed to scan network cells for infinite detection", e);
            }
        }
        return result;
    }

    /**
     * Classify a single AE2 storage cell (from drive/chest) as infinite or normal,
     * and accumulate byte counts accordingly.
     */
    private static void classifyCell(InfiniteCellScanResult result, ItemStack stack) {
        // Check item cell
        IMEInventoryHandler itemInv = AEApi.instance().registries().cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);
        if (itemInv instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) itemInv).getCellInv();
            if (cell != null) {
                if (isInfiniteCell(cell)) {
                    result.hasInfiniteItems = true;
                    result.infiniteItemBytes += cell.getTotalBytes();
                } else {
                    result.nonInfiniteItemTotal += cell.getTotalBytes();
                    result.nonInfiniteItemUsed += cell.getUsedBytes();
                }
            }
        }
        // Check fluid cell (GlodBlock/AE2FluidCraft)
        IMEInventoryHandler fluidInv = AEApi.instance().registries().cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);
        if (fluidInv instanceof ICellInventoryHandler) {
            ICellInventory cell = ((ICellInventoryHandler) fluidInv).getCellInv();
            if (cell != null) {
                if (isInfiniteCell(cell)) {
                    result.hasInfiniteFluids = true;
                    result.infiniteFluidBytes += cell.getTotalBytes();
                } else {
                    result.nonInfiniteFluidTotal += cell.getTotalBytes();
                    result.nonInfiniteFluidUsed += cell.getUsedBytes();
                }
            }
        }
        // Also check GlodBlock's FluidCellInventoryHandler for ExtraCells/GTNH fluid cells
        try {
            Class<?> glodHandlerClass = Class.forName("com.glodblock.github.common.storage.FluidCellInventoryHandler");
            if (glodHandlerClass.isInstance(fluidInv)) {
                Object handler = glodHandlerClass.cast(fluidInv);
                Method getCellInv = glodHandlerClass.getMethod("getCellInv");
                Object cellObj = getCellInv.invoke(handler);
                if (cellObj != null) {
                    long totalBytes = (Long) cellObj.getClass().getMethod("getTotalBytes").invoke(cellObj);
                    long usedBytes = (Long) cellObj.getClass().getMethod("getUsedBytes").invoke(cellObj);
                    String className = cellObj.getClass().getName().toLowerCase();
                    if (className.contains("infinity") || className.contains("infinite")
                        || className.contains("creative") || totalBytes > 10_000_000_000_000L
                        || totalBytes == Long.MAX_VALUE || totalBytes >= Long.MAX_VALUE / 2L) {
                        result.hasInfiniteFluids = true;
                        result.infiniteFluidBytes += totalBytes;
                    } else {
                        result.nonInfiniteFluidTotal += totalBytes;
                        result.nonInfiniteFluidUsed += usedBytes;
                    }
                }
            }
        } catch (Throwable ignored) {
            // GlodBlock not available, skip
        }
    }

    /**
     * Detect whether a cell inventory is an infinite storage cell.
     * Checks for:
     * - Class name containing "infinity", "infinite", or "creative"
     * - TotalBytes exceeding a large threshold (indicating infinite capacity)
     */
    private static boolean isInfiniteCell(ICellInventory cell) {
        if (cell == null) return false;
        // Check by class name (AE2Things infinite cells have distinct class names)
        String className = cell.getClass().getName().toLowerCase();
        if (className.contains("infinity") || className.contains("infinite")
            || className.contains("creative")) {
            return true;
        }
        // Check by total bytes threshold (> 1 trillion bytes indicates infinite)
        long totalBytes = cell.getTotalBytes();
        if (totalBytes > 10_000_000_000_000L) { // > 10 TB
            return true;
        }
        // Check for Long.MAX_VALUE (common infinite cell representation)
        if (totalBytes == Long.MAX_VALUE || totalBytes >= Long.MAX_VALUE / 2L) {
            return true;
        }
        return false;
    }

    /**
     * Query AE2 storage network for items and fluids, returning candidates
     * suitable for thumbnail rendering in the GUI.
     * Similar to queryItemCount but also includes byte summary.
     */
    public static List<CraftingCandidate> queryStorageCandidates(EntityPlayerMP player,
        String rawText, String target, int storageScope, String locale) {
        boolean includeItems = storageScope != AssistantIntent.STORAGE_SCOPE_FLUIDS;
        boolean includeFluids = storageScope != AssistantIntent.STORAGE_SCOPE_ITEMS;
        String query = normalizeStorageQuery(target, storageScope);

        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            return new ArrayList<>();
        }

        List<CraftingCandidate> allCandidates = new ArrayList<>();
        Map<String, ItemStack> uniqueItems = new LinkedHashMap<>();
        Map<String, String> uniqueFluids = new LinkedHashMap<>();
        int index = 1;

        for (TileEntityAdvanceStorageLink link : source.connectors) {
            try {
                IGrid grid = link.getProxy().getGrid();
                IStorageGrid storageGrid = grid == null ? null : grid.getCache(IStorageGrid.class);
                if (storageGrid == null) continue;

                if (includeItems) {
                    for (IAEItemStack item : storageGrid.getItemInventory().getStorageList()) {
                        if (item == null || item.getStackSize() <= 0) continue;
                        ItemStack stack = item.getItemStack();
                        if (stack == null || stack.getItem() == null) continue;
                        if (!query.isEmpty() && !ItemStackUtils.fuzzyNameMatches(stack, query)) continue;
                        String key = candidateKey(stack);
                        if (uniqueItems.containsKey(key)) continue;
                        uniqueItems.put(key, stack);
                        allCandidates.add(new CraftingCandidate(index++, stack, item.getStackSize()));
                        if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                    }
                }
                if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;

                if (includeFluids) {
                    try {
                        for (IAEFluidStack fluid : storageGrid.getFluidInventory().getStorageList()) {
                            if (fluid == null || fluid.getStackSize() <= 0) continue;
                            FluidStack fluidStack = fluid.getFluidStack();
                            String name = fluidStack == null || fluidStack.getFluid() == null ? ""
                                : fluidStack.getLocalizedName().toLowerCase();
                            if (!query.isEmpty() && !name.contains(query)) continue;
                            String displayName = fluidStack == null ? "Unknown Fluid"
                                : fluidStack.getLocalizedName();
                            String key = displayName;
                            if (uniqueFluids.containsKey(key)) continue;
                            uniqueFluids.put(key, displayName);
                            allCandidates.add(new CraftingCandidate(
                                index++,
                                createFluidDisplayStack(displayName),
                                fluid.getStackSize()));
                            if (allCandidates.size() >= MAX_STORAGE_CANDIDATES) break;
                        }
                    } catch (Throwable fluidFailure) {
                        AdvanceDataMonitor.LOG.debug("Fluid inventory unavailable", fluidFailure);
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.error("Failed to query AE2 storage from link at {},{}",
                    link.xCoord, link.yCoord, e);
            }
        }

        // Sort by quantity descending (largest first)
        allCandidates.sort(new Comparator<CraftingCandidate>() {
            @Override
            public int compare(CraftingCandidate a, CraftingCandidate b) {
                return Long.compare(b.amount, a.amount);
            }
        });

        // Re-index
        List<CraftingCandidate> reindexed = new ArrayList<>();
        int i = 1;
        for (CraftingCandidate c : allCandidates) {
            reindexed.add(new CraftingCandidate(i++, c.toItemStack(), c.amount));
        }
        return reindexed;
    }

    private static String weatherSummary(EntityPlayerMP player, String locale) {
        if (player == null || player.worldObj == null) {
            return queryFailed(locale, text(locale, "玩家或世界不可用。", "player or world unavailable."));
        }
        World world = player.worldObj;
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        boolean sky = world.provider == null || world.provider.hasNoSky ? false : true;
        String state = thundering ? text(locale, "雷暴", "thunderstorm")
            : raining ? text(locale, "下雨/下雪", "raining/snowing") : text(locale, "晴朗", "clear");
        return text(locale, "当前世界天气：", "Current world weather:") + "\n"
            + text(locale, "维度：", "Dimension: ")
            + world.provider.dimensionId
            + " / "
            + world.provider.getDimensionName()
            + "\n"
            + text(locale, "有自然天空：", "Natural sky: ")
            + yesNo(sky, locale)
            + "\n"
            + text(locale, "状态：", "State: ")
            + state
            + "\n"
            + text(locale, "下雨：", "Raining: ")
            + yesNo(raining, locale)
            + "\n"
            + text(locale, "打雷：", "Thundering: ")
            + yesNo(thundering, locale);
    }

    private static String timeSummary(EntityPlayerMP player, String locale) {
        if (player == null || player.worldObj == null) {
            return queryFailed(locale, text(locale, "玩家或世界不可用。", "player or world unavailable."));
        }
        long dayTime = player.worldObj.getWorldTime() % 24000L;
        long totalDays = player.worldObj.getWorldTime() / 24000L;
        boolean day = dayTime >= 0L && dayTime < 12000L;
        long toNext = day ? 12000L - dayTime : 24000L - dayTime;
        return text(locale, "当前世界时间：", "Current world time:") + "\n"
            + text(locale, "维度时间：", "Day time: ")
            + dayTime
            + " ticks\n"
            + text(locale, "世界天数：", "World days: ")
            + totalDays
            + "\n"
            + text(locale, "昼夜状态：", "Day/night: ")
            + (day ? text(locale, "白天", "day") : text(locale, "夜晚", "night"))
            + "\n"
            + (day ? text(locale, "距离天黑：", "Ticks until night: ") : text(locale, "距离天亮：", "Ticks until day: "))
            + toNext
            + " ticks";
    }

    private static String positionSummary(EntityPlayerMP player, String locale) {
        if (player == null || player.worldObj == null) {
            return queryFailed(locale, text(locale, "玩家或世界不可用。", "player or world unavailable."));
        }
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY);
        int z = MathHelper.floor_double(player.posZ);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String facing = facingName(player.rotationYaw, locale);
        return text(locale, "当前位置：", "Current position:") + "\n"
            + text(locale, "维度：", "Dimension: ")
            + player.worldObj.provider.dimensionId
            + " / "
            + player.worldObj.provider.getDimensionName()
            + "\n"
            + "XYZ: "
            + x
            + ", "
            + y
            + ", "
            + z
            + "\n"
            + text(locale, "区块：", "Chunk: ")
            + chunkX
            + ", "
            + chunkZ
            + "\n"
            + text(locale, "区块内：", "In chunk: ")
            + (x & 15)
            + ", "
            + (z & 15)
            + "\n"
            + text(locale, "朝向：", "Facing: ")
            + facing;
    }

    private static String biomeSummary(EntityPlayerMP player, String locale) {
        if (player == null || player.worldObj == null) {
            return queryFailed(locale, text(locale, "玩家或世界不可用。", "player or world unavailable."));
        }
        int x = MathHelper.floor_double(player.posX);
        int z = MathHelper.floor_double(player.posZ);
        BiomeGenBase biome = player.worldObj.getBiomeGenForCoords(x, z);
        if (biome == null) {
            return queryFailed(locale, text(locale, "无法读取当前位置群系。", "could not read current biome."));
        }
        return text(locale, "当前位置群系：", "Current biome:") + "\n"
            + text(locale, "名称：", "Name: ")
            + biome.biomeName
            + "\n"
            + text(locale, "温度：", "Temperature: ")
            + biome.temperature
            + "\n"
            + text(locale, "降雨量：", "Rainfall: ")
            + biome.rainfall
            + "\n"
            + text(locale, "可降雨/雪：", "Can rain/snow: ")
            + yesNo(biome.canSpawnLightningBolt(), locale)
            + "\n"
            + text(locale, "高湿度：", "High humidity: ")
            + yesNo(biome.isHighHumidity(), locale);
    }

    private static String inventorySummary(EntityPlayerMP player, String target, long amount, String locale) {
        if (player == null || player.inventory == null) {
            return queryFailed(locale, text(locale, "玩家背包不可用。", "player inventory unavailable."));
        }
        int empty = 0;
        int occupied = 0;
        IInventory inv = player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null) empty++;
            else occupied++;
        }
        return text(locale, "背包空间：", "Inventory space:") + "\n"
            + text(locale, "空槽位：", "Empty slots: ")
            + empty
            + "\n"
            + text(locale, "已占用槽位：", "Occupied slots: ")
            + occupied
            + "\n"
            + text(locale, "总槽位：", "Total slots: ")
            + inv.getSizeInventory();
    }

    private static String networkSummary(EntityPlayerMP player, String locale) {
        if (player == null || player.worldObj == null) {
            return queryFailed(locale, text(locale, "玩家或世界不可用。", "player or world unavailable."));
        }
        TileEntityAdvanceDataMonitor monitor = AssistantMonitorRegistry.getMonitor(player);
        boolean recorded = monitor != null;
        if (monitor == null) {
            monitor = AssistantMonitorRegistry.findNearbyAndRecord(player, 32);
        }
        StringBuilder builder = new StringBuilder(text(locale, "ADM 网络/连接器状态：", "ADM network/connector status:"));
        if (monitor == null) {
            builder.append("\n")
                .append(text(locale, "未找到已记录或附近的高级数据显示器。", "No recorded or nearby Advance Data Monitor found."));
        } else {
            builder.append("\n")
                .append(text(locale, "高级数据显示器：", "Advance Data Monitor: "))
                .append(monitor.xCoord)
                .append(",")
                .append(monitor.yCoord)
                .append(",")
                .append(monitor.zCoord)
                .append(
                    recorded ? text(locale, "（来自玩家记录）", " (from player record)")
                        : text(locale, "（本次附近搜索记录）", " (recorded from nearby search)"));
        }
        ConnectorSource<TileEntityAdvanceCraftingLink> crafting = findAllLinkTiles(
            player,
            TileEntityAdvanceCraftingLink.class,
            32);
        ConnectorSource<TileEntityAdvanceStorageLink> storage = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        ConnectorSource<TileEntityAdvanceNetworkLink> network = findAllLinkTiles(
            player,
            TileEntityAdvanceNetworkLink.class,
            32);
        builder.append("\nAdvance Crafting Link: ")
            .append(crafting.connectors.size())
            .append(" (")
            .append(crafting.sourceDescription(locale))
            .append(")");
        builder.append("\nAdvance Storage Link: ")
            .append(storage.connectors.size())
            .append(" (")
            .append(storage.sourceDescription(locale))
            .append(")");
        builder.append("\nAdvance Network Link: ")
            .append(network.connectors.size())
            .append(" (")
            .append(network.sourceDescription(locale))
            .append(")");
        appendConnectorDetails(builder, "Crafting", crafting.connectors);
        appendConnectorDetails(builder, "Storage", storage.connectors);
        appendConnectorDetails(builder, "Network", network.connectors);
        return builder.toString();
    }

    private static void appendConnectorDetails(StringBuilder builder, String label, List<? extends TileEntity> tiles) {
        int index = 1;
        for (TileEntity tile : tiles) {
            builder.append("\n")
                .append(label)
                .append(" #")
                .append(index++)
                .append(": ")
                .append(tile.xCoord)
                .append(",")
                .append(tile.yCoord)
                .append(",")
                .append(tile.zCoord);
        }
    }

    private static String yesNo(boolean value, String locale) {
        return value ? text(locale, "是", "yes") : text(locale, "否", "no");
    }

    private static String facingName(float yaw, String locale) {
        int direction = MathHelper.floor_double((double) (yaw * 4.0F / 360.0F) + 0.5D) & 3;
        switch (direction) {
            case 0:
                return text(locale, "南", "south");
            case 1:
                return text(locale, "西", "west");
            case 2:
                return text(locale, "北", "north");
            case 3:
                return text(locale, "东", "east");
            default:
                return text(locale, "未知", "unknown");
        }
    }

    public static String recipeDetailsForCandidate(EntityPlayerMP player, CraftingCandidate candidate, long amount) {
        return recipeDetailsForCandidate(player, candidate, amount, "zh_CN");
    }

    public static String recipeDetailsForCandidate(EntityPlayerMP player, CraftingCandidate candidate, long amount,
        String locale) {
        if (candidate == null) {
            return recipeFailed(locale, text(locale, "候选项为空。", "empty candidate."));
        }
        ItemStack stack = candidate.toItemStack();
        if (stack == null || stack.getItem() == null) {
            return recipeFailed(locale, text(locale, "候选物品无法还原。", "candidate item could not be restored."));
        }
        return recipeSummary(player, candidate.displayName, amount, stack, locale);
    }

    private static String recipeSummary(EntityPlayerMP player, String target, long amount) {
        return recipeSummary(player, target, amount, "zh_CN");
    }

    private static String recipeSummary(EntityPlayerMP player, String target, long amount, String locale) {
        return recipeSummary(player, target, amount, null, locale);
    }

    private static String recipeSummary(EntityPlayerMP player, String target, long amount, ItemStack exactStack) {
        return recipeSummary(player, target, amount, exactStack, "zh_CN");
    }

    private static String recipeSummary(EntityPlayerMP player, String target, long amount, ItemStack exactStack,
        String locale) {
        String query = target == null ? "" : target.trim();
        ConnectorSource<TileEntityAdvanceCraftingLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceCraftingLink.class,
            32);
        if (source.isEmpty()) {
            return queryFailed(locale, text(locale, "附近没有 Advance Crafting Link。", "no nearby Advance Crafting Link."));
        }
        String connectorInfo = "\n(" + source.sourceDescription(locale) + ")";
        List<CraftingCandidate> allCandidates = new ArrayList<>();
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        ICraftingPatternDetails firstDetail = null;
        for (TileEntityAdvanceCraftingLink link : source.connectors) {
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                ICraftingGrid craftingGrid = grid == null ? null : grid.getCache(ICraftingGrid.class);
                if (craftingGrid == null) continue;
                PatternLookup lookup = exactStack == null
                    ? lookupPatterns(
                        craftingGrid,
                        query,
                        amount <= 0 ? 1 : amount,
                        query.isEmpty() ? Integer.MAX_VALUE : 1)
                    : lookupPatterns(craftingGrid, exactStack, amount <= 0 ? 1 : amount);
                if (!query.isEmpty() && !lookup.details.isEmpty() && firstDetail == null) {
                    firstDetail = lookup.details.get(0);
                }
                for (CraftingCandidate c : lookup.candidates) {
                    String key = candidateKey(c.toItemStack());
                    if (unique.put(key, c.toItemStack()) != null) continue;
                    allCandidates.add(c);
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG
                    .error("Failed to query AE2 crafting patterns from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        if (!query.isEmpty() && firstDetail != null) {
            return patternDetails(firstDetail, allCandidates.isEmpty() ? null : allCandidates.get(0), locale)
                + connectorInfo;
        }
        if (allCandidates.isEmpty()) {
            return (query.isEmpty() ? text(locale, "附近没有找到 AE2 可合成样板。", "No AE2 craftable patterns were found nearby.")
                : text(locale, "附近没有找到匹配的 AE2 可合成样板。", "No matching AE2 craftable patterns were found nearby."))
                + connectorInfo;
        }
        return AssistantFormatter.candidates(
            query.isEmpty() ? text(locale, "AE2 可合成样板总览：", "AE2 craftable pattern overview:")
                : text(locale, "匹配的 AE2 可合成项：", "Matching AE2 craftables:"),
            allCandidates,
            text(locale, "没有找到匹配的候选项。", "No matching candidates were found.")) + connectorInfo;
    }

    private static String storageSummary(EntityPlayerMP player, String target, int scope, String locale) {
        boolean chinese = zh(locale);
        ConnectorSource<TileEntityAdvanceStorageLink> source = findAllLinkTiles(
            player,
            TileEntityAdvanceStorageLink.class,
            32);
        if (source.isEmpty()) {
            AdvanceDataMonitor.LOG.info("[ADM Assistant] Storage query failed: no AdvanceStorageLink found.");
            return (chinese ? msg("storage.noLink", "Query failed: no nearby Advance Storage Link.")
                : "Query failed: no nearby Advance Storage Link.") + "\n(" + source.sourceDescription(locale) + ")";
        }
        String connectorInfo = "\n(" + source.sourceDescription(locale) + ")";
        int effectiveScope = scope;
        String query = normalizeStorageQuery(target, effectiveScope);
        boolean includeItems = effectiveScope != AssistantIntent.STORAGE_SCOPE_FLUIDS;
        boolean includeFluids = effectiveScope != AssistantIntent.STORAGE_SCOPE_ITEMS;
        long itemTypes = 0;
        long itemStacks = 0;
        long fluidTypes = 0;
        long fluidAmount = 0;
        List<String> itemMatches = new ArrayList<>();
        List<String> fluidMatches = new ArrayList<>();
        Map<String, Boolean> seenItemMatches = new java.util.HashMap<>();
        Map<String, Boolean> seenFluidMatches = new java.util.HashMap<>();
        for (TileEntityAdvanceStorageLink link : source.connectors) {
            AdvanceDataMonitor.LOG
                .info("[ADM Assistant] Using StorageLink at {},{},{}", link.xCoord, link.yCoord, link.zCoord);
            try {
                IGrid grid = link.getProxy()
                    .getGrid();
                IStorageGrid storageGrid = grid == null ? null : grid.getCache(IStorageGrid.class);
                if (storageGrid == null) {
                    AdvanceDataMonitor.LOG.info(
                        "[ADM Assistant] Storage query: IStorageGrid unavailable at {},{}",
                        link.xCoord,
                        link.yCoord);
                    continue;
                }
                if (includeItems) {
                    for (IAEItemStack item : storageGrid.getItemInventory()
                        .getStorageList()) {
                        if (item == null) continue;
                        itemTypes++;
                        itemStacks += item.getStackSize();
                        ItemStack stack = item.getItemStack();
                        if (stack != null && item.getStackSize() > 0
                            && (query.isEmpty() || ItemStackUtils.fuzzyNameMatches(stack, query))) {
                            String matchText = stack.getDisplayName() + " x" + item.getStackSize();
                            if (seenItemMatches.put(matchText, true) == null) {
                                itemMatches.add(matchText);
                            }
                        }
                    }
                }
                if (includeFluids) {
                    try {
                        for (IAEFluidStack fluid : storageGrid.getFluidInventory()
                            .getStorageList()) {
                            if (fluid == null) continue;
                            fluidTypes++;
                            fluidAmount += fluid.getStackSize();
                            FluidStack fluidStack = fluid.getFluidStack();
                            String name = fluidStack == null || fluidStack.getFluid() == null ? ""
                                : fluidStack.getLocalizedName()
                                    .toLowerCase();
                            if (query.isEmpty() || name.contains(query)) {
                                String display = fluidStack == null
                                    ? (chinese ? msg("storage.unknownFluid", "Unknown fluid") : "Unknown fluid")
                                    : fluidStack.getLocalizedName();
                                String matchText = display + " " + fluid.getStackSize() + " mB";
                                if (seenFluidMatches.put(matchText, true) == null) {
                                    fluidMatches.add(matchText);
                                }
                            }
                        }
                    } catch (Throwable fluidFailure) {
                        AdvanceDataMonitor.LOG.debug("Fluid inventory unavailable", fluidFailure);
                    }
                }
            } catch (Exception e) {
                AdvanceDataMonitor.LOG
                    .error("Failed to query AE2 storage from link at {},{}", link.xCoord, link.yCoord, e);
            }
        }
        try {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Storage query result: scope={}, query='{}', itemTypes={}, itemTotal={}, fluidTypes={}, fluidTotalMb={}, itemMatches={}, fluidMatches={}",
                effectiveScope,
                safe(query),
                itemTypes,
                itemStacks,
                fluidTypes,
                fluidAmount,
                itemMatches.size(),
                fluidMatches.size());
            AssistantDebugLog.append(
                "server-storage",
                "scope=" + effectiveScope
                    + ", query='"
                    + safe(query)
                    + "', itemTypes="
                    + itemTypes
                    + ", itemTotal="
                    + itemStacks
                    + ", fluidTypes="
                    + fluidTypes
                    + ", fluidTotalMb="
                    + fluidAmount
                    + ", itemMatches="
                    + itemMatches.size()
                    + ", fluidMatches="
                    + fluidMatches.size());
            if (!query.isEmpty()) {
                return targetedStorageResult(query, effectiveScope, chinese, itemMatches, fluidMatches) + connectorInfo;
            }
            StringBuilder builder = new StringBuilder();
            if (effectiveScope == AssistantIntent.STORAGE_SCOPE_FLUIDS) {
                builder.append(
                    chinese ? msg("storage.fluidSummaryTitle", "AE2 fluid storage summary:")
                        : "AE2 fluid storage summary:");
                builder.append(chinese ? msg("storage.fluidTypes", "\nFluid types: ") : "\nFluid types: ")
                    .append(fluidTypes)
                    .append(chinese ? msg("storage.fluidTotal", ", fluid total: ") : ", fluid total: ")
                    .append(fluidAmount)
                    .append(" mB");
                appendMatches(
                    builder,
                    chinese ? msg("storage.fluidList", "Fluid matches") : "Fluid matches",
                    fluidMatches);
            } else if (effectiveScope == AssistantIntent.STORAGE_SCOPE_ITEMS) {
                builder.append(
                    chinese ? msg("storage.itemSummaryTitle", "AE2 item storage summary:")
                        : "AE2 item storage summary:");
                builder.append(chinese ? msg("storage.itemTypes", "\nItem types: ") : "\nItem types: ")
                    .append(itemTypes)
                    .append(chinese ? msg("storage.itemTotal", ", item total: ") : ", item total: ")
                    .append(itemStacks);
                appendMatches(builder, chinese ? msg("storage.itemList", "Item matches") : "Item matches", itemMatches);
            } else {
                builder.append(chinese ? msg("storage.overviewTitle", "AE2 storage summary:") : "AE2 storage summary:");
                builder.append(chinese ? msg("storage.itemTypes", "\nItem types: ") : "\nItem types: ")
                    .append(itemTypes)
                    .append(chinese ? msg("storage.itemTotal", ", item total: ") : ", item total: ")
                    .append(itemStacks);
                builder.append(chinese ? msg("storage.fluidTypes", "\nFluid types: ") : "\nFluid types: ")
                    .append(fluidTypes)
                    .append(chinese ? msg("storage.fluidTotal", ", fluid total: ") : ", fluid total: ")
                    .append(fluidAmount)
                    .append(" mB");
                appendMatches(builder, chinese ? msg("storage.itemList", "Item matches") : "Item matches", itemMatches);
                appendMatches(
                    builder,
                    chinese ? msg("storage.fluidList", "Fluid matches") : "Fluid matches",
                    fluidMatches);
            }
            builder.append(
                chinese
                    ? msg(
                        "storage.capacityUnknown",
                        "\nCapacity/usage percent: unknown for this AE2 adapter; current counts were still read.")
                    : "\nCapacity/usage percent: unknown for this AE2 adapter; current counts were still read.");
            return unescape(builder.toString()) + connectorInfo;
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("Failed to query AE2 storage", e);
            return (chinese ? msg("storage.queryFailedPrefix", "Query failed: ") + e.getMessage()
                : "Query failed: " + e.getMessage()) + connectorInfo;
        }
    }

    private static String targetedStorageResult(String query, int scope, boolean chinese, List<String> itemMatches,
        List<String> fluidMatches) {
        boolean includeItems = scope != AssistantIntent.STORAGE_SCOPE_FLUIDS;
        boolean includeFluids = scope != AssistantIntent.STORAGE_SCOPE_ITEMS;
        int itemCount = includeItems && itemMatches != null ? itemMatches.size() : 0;
        int fluidCount = includeFluids && fluidMatches != null ? fluidMatches.size() : 0;
        if (itemCount + fluidCount == 1) {
            if (itemCount == 1) {
                return itemMatches.get(0);
            }
            return fluidMatches.get(0);
        }
        StringBuilder builder = new StringBuilder();
        if (itemCount > 0) {
            appendMatches(
                builder,
                chinese ? msg("storage.matchingItems", "Matching items") : "Matching items",
                itemMatches);
        }
        if (fluidCount > 0) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            appendMatches(
                builder,
                chinese ? msg("storage.matchingFluids", "Matching fluids") : "Matching fluids",
                fluidMatches);
        }
        if (builder.length() == 0) {
            return chinese ? msg("storage.noMatchPrefix", "No matching AE2 storage entry found for: ") + query
                : "No matching AE2 storage entry found for: " + query;
        }
        return builder.toString();
    }

    private static String text(String locale, String zhText, String enText) {
        return zh(locale) ? zhText : enText;
    }

    private static String orderFailed(String locale, String reason) {
        return text(
            locale,
            "订单失败：" + (reason == null ? "" : reason),
            "Order failed: " + (reason == null ? "" : reason));
    }

    private static String queryFailed(String locale, String reason) {
        return text(
            locale,
            "查询失败：" + (reason == null ? "" : reason),
            "Query failed: " + (reason == null ? "" : reason));
    }

    private static String recipeFailed(String locale, String reason) {
        return text(
            locale,
            "配方查询失败：" + (reason == null ? "" : reason),
            "Recipe query failed: " + (reason == null ? "" : reason));
    }

    private static String msg(String key, String fallback) {
        return AssistantLexicon.message(key, fallback);
    }

    private static void appendMatches(StringBuilder builder, String title, List<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        builder.append("\n")
            .append(AssistantFormatter.numberedEntries(title, matches));
    }

    private static int detectStorageScope(String rawText, String target) {
        String text = ((rawText == null ? "" : rawText) + " " + (target == null ? "" : target)).toLowerCase();
        AssistantLexicon.LexiconData lexicon = AssistantLexicon.get();
        if (AssistantTextNormalizer.containsAny(text, lexicon.fluidScopeWords)) {
            return AssistantIntent.STORAGE_SCOPE_FLUIDS;
        }
        if (AssistantTextNormalizer.containsAny(text, lexicon.itemScopeWords)) {
            return AssistantIntent.STORAGE_SCOPE_ITEMS;
        }
        return AssistantIntent.STORAGE_SCOPE_ALL;
    }

    private static String normalizeStorageQuery(String target, int scope) {
        AssistantLexicon.LexiconData lexicon = AssistantLexicon.get();
        String query = target == null ? ""
            : target.trim()
                .toLowerCase();
        query = AssistantTextNormalizer.removeWords(query, lexicon.storageStripWords);
        if (scope == AssistantIntent.STORAGE_SCOPE_FLUIDS) {
            query = AssistantTextNormalizer.removeWords(query, lexicon.fluidScopeWords);
        } else if (scope == AssistantIntent.STORAGE_SCOPE_ITEMS) {
            query = AssistantTextNormalizer.removeWords(query, lexicon.itemScopeWords);
        }
        return stripPunctuation(stripModalParticles(query));
    }

    private static String stripModalParticles(String text) {
        return AssistantTextNormalizer.stripModalParticles(text);
    }

    private static boolean zh(String locale) {
        return locale == null || locale.trim()
            .isEmpty()
            || locale.toLowerCase()
                .startsWith("zh");
    }

    private static String stripPunctuation(String text) {
        return AssistantTextNormalizer.stripPunctuation(text);
    }

    private static String unescape(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                try {
                    builder.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {}
            }
            builder.append(text.charAt(i));
        }
        return builder.toString();
    }

    private static PatternLookup lookupPatterns(ICraftingGrid craftingGrid, ItemStack targetStack, long amount) {
        PatternLookup result = new PatternLookup();
        if (targetStack == null || targetStack.getItem() == null) {
            return result;
        }
        try {
            Map<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> patterns = craftingGrid
                .getCraftingPatterns();
            result.usedStrongApi = true;
            if (patterns == null || patterns.isEmpty()) {
                return result;
            }
            String targetKey = candidateKey(targetStack);
            for (Entry<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> entry : patterns.entrySet()) {
                IAEItemStack output = entry.getKey();
                if (output == null || !hasUsablePattern(entry.getValue())) {
                    continue;
                }
                ItemStack stack = output.getItemStack();
                if (stack == null || stack.getItem() == null || !targetKey.equals(candidateKey(stack))) {
                    continue;
                }
                result.candidates.add(new CraftingCandidate(1, stack, amount));
                ICraftingPatternDetails detail = firstPattern(entry.getValue());
                if (detail != null) {
                    result.details.add(detail);
                }
                break;
            }
            AssistantDebugLog.append(
                "server-patterns",
                "source=getCraftingPatternsExact, target='" + safe(targetStack.getDisplayName())
                    + "', matches="
                    + result.candidates.size());
        } catch (Throwable e) {
            AdvanceDataMonitor.LOG.warn("[ADM Assistant] Exact AE2 crafting pattern query failed.", e);
            result.usedStrongApi = false;
            result.candidates.clear();
            result.details.clear();
        }
        return result;
    }

    private static PatternLookup lookupPatterns(ICraftingGrid craftingGrid, String target, long amount, int limit) {
        PatternLookup result = new PatternLookup();
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        try {
            Map<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> patterns = craftingGrid
                .getCraftingPatterns();
            result.usedStrongApi = true;
            if (patterns == null || patterns.isEmpty()) {
                AdvanceDataMonitor.LOG.info("[ADM Assistant] AE2 crafting pattern map is empty.");
                return result;
            }
            AdvanceDataMonitor.LOG.info("[ADM Assistant] AE2 crafting pattern outputs count={}", patterns.size());
            for (Entry<IAEItemStack, ? extends Collection<ICraftingPatternDetails>> entry : patterns.entrySet()) {
                IAEItemStack output = entry.getKey();
                if (output == null || !hasUsablePattern(entry.getValue())) {
                    continue;
                }
                ItemStack stack = output.getItemStack();
                if (stack == null || stack.getItem() == null) {
                    continue;
                }
                if (target != null && !target.trim()
                    .isEmpty() && !ItemStackUtils.fuzzyNameMatches(stack, target)) {
                    continue;
                }
                if (unique.put(candidateKey(stack), stack) != null) {
                    continue;
                }
                result.candidates.add(new CraftingCandidate(result.candidates.size() + 1, stack, amount));
                ICraftingPatternDetails detail = firstPattern(entry.getValue());
                if (detail != null) {
                    result.details.add(detail);
                }
            }
            AssistantDebugLog.append(
                "server-patterns",
                "source=getCraftingPatterns, target='" + safe(
                    target) + "', outputs=" + patterns.size() + ", matches=" + result.candidates.size());
        } catch (Throwable e) {
            AdvanceDataMonitor.LOG
                .warn("[ADM Assistant] Strong AE2 crafting pattern query failed; will try reflection fallback.", e);
            result.usedStrongApi = false;
            result.candidates.clear();
            result.details.clear();
        }
        return result;
    }

    private static ICraftingPatternDetails firstPattern(Collection<ICraftingPatternDetails> patterns) {
        if (patterns == null) {
            return null;
        }
        for (ICraftingPatternDetails pattern : patterns) {
            if (pattern != null) {
                return pattern;
            }
        }
        return null;
    }

    private static String patternDetails(ICraftingPatternDetails pattern, CraftingCandidate candidate) {
        return patternDetails(pattern, candidate, "zh_CN");
    }

    private static String patternDetails(ICraftingPatternDetails pattern, CraftingCandidate candidate, String locale) {
        return PatternDetailFormatter.format(pattern, candidate, zh(locale));
    }

    private static final class PatternLookup {

        private boolean usedStrongApi;
        private final List<CraftingCandidate> candidates = new ArrayList<>();
        private final List<ICraftingPatternDetails> details = new ArrayList<>();
    }

    private static List<CraftingCandidate> craftingCandidatesFromReflected(Collection<?> craftables, String target,
        long amount) {
        List<CraftingCandidate> candidates = new ArrayList<>();
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        int index = 1;
        for (Object value : craftables) {
            ItemStack stack = toItemStack(value);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (target != null && !target.trim()
                .isEmpty() && !ItemStackUtils.fuzzyNameMatches(stack, target)) {
                continue;
            }
            if (unique.put(candidateKey(stack), stack) != null) {
                continue;
            }
            candidates.add(new CraftingCandidate(index++, stack, amount));
        }
        AssistantDebugLog.append(
            "server-patterns",
            "source=reflection, target='" + safe(
                target) + "', inputs=" + craftables.size() + ", matches=" + candidates.size());
        return candidates;
    }

    private static boolean hasUsablePattern(Collection<ICraftingPatternDetails> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (ICraftingPatternDetails pattern : patterns) {
            if (pattern != null) {
                return true;
            }
        }
        return false;
    }

    private static String candidateKey(ItemStack stack) {
        return ItemStackUtils.registryName(stack) + ":"
            + stack.getItemDamage()
            + ":"
            + String.valueOf(stack.getTagCompound());
    }

    private static Collection<?> reflectCraftables(ICraftingGrid craftingGrid) throws Exception {
        String[] preferredNames = { "getCraftables", "getCraftingFor", "getCraftingItems", "getCraftableItems",
            "getCraftingPatterns" };
        for (String name : preferredNames) {
            Collection<?> craftables = invokeNoArgCraftableMethod(craftingGrid, name);
            if (craftables != null) {
                return craftables;
            }
        }
        for (Method method : craftingGrid.getClass()
            .getMethods()) {
            Collection<?> craftables = invokePotentialCraftableMethod(craftingGrid, method);
            if (craftables != null) {
                return craftables;
            }
        }
        for (Method method : craftingGrid.getClass()
            .getDeclaredMethods()) {
            Collection<?> craftables = invokePotentialCraftableMethod(craftingGrid, method);
            if (craftables != null) {
                return craftables;
            }
        }
        return null;
    }

    private static Collection<?> invokeNoArgCraftableMethod(ICraftingGrid craftingGrid, String name) throws Exception {
        for (Method method : craftingGrid.getClass()
            .getMethods()) {
            if (method.getName()
                .equals(name)) {
                Collection<?> craftables = invokePotentialCraftableMethod(craftingGrid, method);
                if (craftables != null) {
                    return craftables;
                }
            }
        }
        return null;
    }

    private static Collection<?> invokePotentialCraftableMethod(ICraftingGrid craftingGrid, Method method)
        throws Exception {
        if (method.getParameterTypes().length != 0 || method.getReturnType() == Void.TYPE) {
            return null;
        }
        String lower = method.getName()
            .toLowerCase();
        if (!lower.contains("craft") && !lower.contains("pattern")) {
            return null;
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            method.setAccessible(true);
        }
        Object value;
        try {
            value = method.invoke(craftingGrid);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.debug("[ADM Assistant] Craftable reflection method {} failed", method.getName(), e);
            return null;
        }
        Collection<?> normalized = normalizeCraftableResult(value);
        if (normalized != null) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Craftables reflected via {}() returnType={} count={}",
                method.getName(),
                method.getReturnType()
                    .getName(),
                normalized.size());
            return normalized;
        }
        return null;
    }

    private static Collection<?> normalizeCraftableResult(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection) {
            return (Collection<?>) value;
        }
        if (value instanceof Iterable) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Iterable<?>) value) list.add(item);
            return list;
        }
        try {
            Method iterator = value.getClass()
                .getMethod("iterator");
            Object iterable = iterator.invoke(value);
            if (iterable instanceof java.util.Iterator) {
                List<Object> list = new ArrayList<>();
                java.util.Iterator<?> it = (java.util.Iterator<?>) iterable;
                while (it.hasNext()) list.add(it.next());
                return list;
            }
        } catch (NoSuchMethodException ignored) {}
        try {
            Method getStacks = value.getClass()
                .getMethod("getStacks");
            return normalizeCraftableResult(getStacks.invoke(value));
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private static void logCraftingGridMethods(ICraftingGrid craftingGrid) {
        AdvanceDataMonitor.LOG.info(
            "[ADM Assistant] Available CraftingGrid methods on {}:",
            craftingGrid.getClass()
                .getName());
        for (Method method : craftingGrid.getClass()
            .getMethods()) {
            String lower = method.getName()
                .toLowerCase();
            if (lower.contains("craft") || lower.contains("pattern")) {
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant]   public {} {}({})",
                    method.getReturnType()
                        .getName(),
                    method.getName(),
                    parameterSummary(method));
            }
        }
        for (Method method : craftingGrid.getClass()
            .getDeclaredMethods()) {
            String lower = method.getName()
                .toLowerCase();
            if (lower.contains("craft") || lower.contains("pattern")) {
                AdvanceDataMonitor.LOG.info(
                    "[ADM Assistant]   declared {} {}({})",
                    method.getReturnType()
                        .getName(),
                    method.getName(),
                    parameterSummary(method));
            }
        }
    }

    private static String parameterSummary(Method method) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(types[i].getName());
        }
        return builder.toString();
    }

    private static ItemStack toItemStack(Object value) {
        try {
            if (value instanceof IAEItemStack) {
                return ((IAEItemStack) value).getItemStack();
            }
            Method method = value.getClass()
                .getMethod("getItemStack");
            Object stack = method.invoke(value);
            return stack instanceof ItemStack ? (ItemStack) stack : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CraftingCandidate resolveServerCandidate(ICraftingGrid craftingGrid, CraftingCandidate candidate,
        long amount) {
        if (craftingGrid == null || candidate == null) {
            return null;
        }
        PatternLookup lookup = lookupPatterns(craftingGrid, candidate.displayName, Math.max(1L, amount), 20);
        for (CraftingCandidate value : lookup.candidates) {
            if (sameCandidate(value, candidate)) {
                return new CraftingCandidate(value.index, value.toItemStack(), amount);
            }
        }
        return null;
    }

    private static boolean sameCandidate(CraftingCandidate a, CraftingCandidate b) {
        if (a == null || b == null) {
            return false;
        }
        if (!safe(a.registryName).equals(safe(b.registryName)) || a.meta != b.meta) {
            return false;
        }
        return String.valueOf(a.itemNbt)
            .equals(String.valueOf(b.itemNbt));
    }

    private static String trySubmit(final ICraftingGrid craftingGrid, final IGrid grid,
        final TileEntityAdvanceCraftingLink link, final EntityPlayerMP player, final CraftingCandidate candidate,
        final ItemStack stack, final long amount, final String rawText, final String locale) {
        String pendingError = AssistantCraftJobManager.instance()
            .checkCanStart(player, locale);
        if (pendingError != null) {
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=too-many-pending, target='" + safe(stack.getDisplayName())
                    + "', amount="
                    + amount);
            return pendingError;
        }
        final IAEItemStack request = AEItemStack.create(stack.copy());
        if (request == null) {
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=request-create, target='" + safe(stack.getDisplayName()) + "'");
            return orderFailed(
                locale,
                text(
                    locale,
                    "AE2 无法为 " + stack.getDisplayName() + " 创建物品请求。",
                    "AE2 could not create an item request for " + stack.getDisplayName() + "."));
        }
        request.setStackSize(amount);
        final BaseActionSource source = new PlayerSource(player, link);
        final Future<ICraftingJob> future;
        try {
            future = craftingGrid.beginCraftingJob(player.worldObj, grid, source, request, null);
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[ADM Assistant] AE2 crafting calculation failed to start", e);
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=begin-job-exception, target='" + safe(
                    stack.getDisplayName()) + "', amount=" + amount + ", message='" + safe(e.getMessage()) + "'");
            return orderFailed(locale, e.getMessage());
        }
        if (future == null) {
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=null-future, target='" + safe(stack.getDisplayName()) + "', amount=" + amount);
            return orderFailed(locale, text(locale, "AE2 没有启动合成计算。", "AE2 did not start crafting calculation."));
        }
        AssistantCraftJobManager.instance()
            .register(player, future, stack.getDisplayName(), amount);
        Thread waiter = new Thread(new Runnable() {

            @Override
            public void run() {
                final ICraftingJob job;
                try {
                    job = future.get(Math.max(1, Config.assistantCraftJobTimeoutSeconds), TimeUnit.SECONDS);
                } catch (final TimeoutException e) {
                    try {
                        future.cancel(true);
                    } catch (Throwable ignored) {}
                    AssistantCraftJobManager.instance()
                        .complete(player, future);
                    AssistantDebugLog.append(
                        "server-submit",
                        "status=FAIL, reason=future-timeout, target='" + safe(stack.getDisplayName())
                            + "', amount="
                            + amount
                            + ", timeout="
                            + Config.assistantCraftJobTimeoutSeconds);
                    HandlerTick.enqueueServerTask(new Runnable() {

                        @Override
                        public void run() {
                            sendAssistantMessage(
                                player,
                                orderFailed(
                                    locale,
                                    text(
                                        locale,
                                        "AE2 合成计算超时：" + stack.getDisplayName() + " x" + amount + "。",
                                        "AE2 crafting calculation timed out for " + stack.getDisplayName()
                                            + " x"
                                            + amount
                                            + ".")));
                        }
                    });
                    return;
                } catch (final Exception e) {
                    AssistantCraftJobManager.instance()
                        .complete(player, future);
                    AssistantDebugLog.append(
                        "server-submit",
                        "status=FAIL, reason=future-exception, target='" + safe(stack.getDisplayName())
                            + "', amount="
                            + amount
                            + ", message='"
                            + safe(e.getMessage())
                            + "'");
                    HandlerTick.enqueueServerTask(new Runnable() {

                        @Override
                        public void run() {
                            sendAssistantMessage(player, orderFailed(locale, e.getMessage()));
                        }
                    });
                    return;
                }
                AssistantCraftJobManager.instance()
                    .complete(player, future);
                HandlerTick.enqueueServerTask(new Runnable() {

                    @Override
                    public void run() {
                        finishCraftSubmit(craftingGrid, source, player, candidate, stack, amount, rawText, job, locale);
                    }
                });
            }
        }, "ADM-AE2-CraftSubmit");
        waiter.setDaemon(true);
        waiter.start();
        return text(
            locale,
            "AE2 合成计算已开始：" + stack.getDisplayName() + " x" + amount + "。",
            "AE2 crafting calculation started for " + stack.getDisplayName() + " x" + amount + ".");
    }

    private static void finishCraftSubmit(ICraftingGrid craftingGrid, BaseActionSource source, EntityPlayerMP player,
        CraftingCandidate candidate, ItemStack stack, long amount, String rawText, ICraftingJob job, String locale) {
        if (job == null) {
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=null-job, target='" + safe(stack.getDisplayName()) + "', amount=" + amount);
            sendAssistantMessage(
                player,
                orderFailed(locale, text(locale, "AE2 合成计算没有返回任务。", "AE2 crafting calculation returned no job.")));
            return;
        }
        if (job.isSimulation()) {
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=simulation, target='" + safe(
                    stack.getDisplayName()) + "', amount=" + amount + ", bytes=" + job.getByteTotal());
            sendAssistantMessage(
                player,
                orderFailed(
                    locale,
                    text(
                        locale,
                        "AE2 只能模拟该合成，可能缺少材料或样板无效：" + stack.getDisplayName() + " x" + amount + "。",
                        "AE2 can only simulate this craft. Missing ingredients or invalid pattern for " + stack
                            .getDisplayName() + " x" + amount + ".")));
            return;
        }
        try {
            ICraftingLink craftingLink = craftingGrid.submitJob(job, null, null, true, source, false);
            if (craftingLink == null) {
                AssistantDebugLog.append(
                    "server-submit",
                    "status=FAIL, reason=submit-null-link, target='" + safe(
                        stack.getDisplayName()) + "', amount=" + amount + ", bytes=" + job.getByteTotal());
                sendAssistantMessage(
                    player,
                    orderFailed(
                        locale,
                        text(
                            locale,
                            "AE2 没有接受合成任务：" + stack.getDisplayName() + " x" + amount + "。",
                            "AE2 did not accept the crafting job for " + stack.getDisplayName()
                                + " x"
                                + amount
                                + ".")));
                return;
            }
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] AE2 crafting job submitted: target='{}', amount={}, link={}, bytes={}",
                stack.getDisplayName(),
                amount,
                craftingLink.getCraftingID(),
                job.getByteTotal());
            AssistantDebugLog.append(
                "server-submit",
                "status=OK, target='" + safe(stack.getDisplayName())
                    + "', amount="
                    + amount
                    + ", craftingId="
                    + safe(craftingLink.getCraftingID())
                    + ", bytes="
                    + job.getByteTotal());
            OrderMemoryStore.instance()
                .remember(ownerKey(player), rawText, candidate);
            sendAssistantMessage(
                player,
                text(
                    locale,
                    "OK：已提交 AE2 合成任务：" + stack.getDisplayName() + " x" + amount + "。",
                    "OK: submitted AE2 crafting job for " + stack.getDisplayName() + " x" + amount + "."));
        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[ADM Assistant] AE2 crafting submit failed", e);
            AssistantDebugLog.append(
                "server-submit",
                "status=FAIL, reason=submit-exception, target='" + safe(
                    stack.getDisplayName()) + "', amount=" + amount + ", message='" + safe(e.getMessage()) + "'");
            sendAssistantMessage(player, orderFailed(locale, e.getMessage()));
        }
    }

    private static void sendAssistantMessage(EntityPlayerMP player, String message) {
        if (player != null) {
            AdvanceDataMonitor.ADMCHANEL.sendTo(PacketAssistantResponse.message(message), player);
        }
    }

    private static String ownerKey(EntityPlayerMP player) {
        UUID uuid = player == null ? new UUID(0L, 0L) : player.getUniqueID();
        return uuid.toString();
    }

    private static String safe(String text) {
        if (text == null) {
            return "";
        }
        return text.replace((char) 10, ' ')
            .replace((char) 13, ' ');
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileEntity> T findNearest(EntityPlayerMP player, Class<T> type, int radius) {
        if (player == null || player.worldObj == null) {
            return null;
        }
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        for (int x = px - radius; x <= px + radius; x++) {
            for (int y = Math.max(0, py - radius); y <= Math.min(255, py + radius); y++) {
                for (int z = pz - radius; z <= pz + radius; z++) {
                    TileEntity te = player.worldObj.getTileEntity(x, y, z);
                    if (!type.isInstance(te)) {
                        continue;
                    }
                    double distance = player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = (T) te;
                    }
                }
            }
        }
        return best;
    }

    private static final class ConnectorSource<T extends TileEntity> {

        final List<T> connectors;
        final int boundCount;
        final int nearbyCount;

        ConnectorSource(List<T> connectors, int boundCount, int nearbyCount) {
            this.connectors = connectors;
            this.boundCount = boundCount;
            this.nearbyCount = nearbyCount;
        }

        boolean isEmpty() {
            return connectors.isEmpty();
        }

        String sourceDescription(String locale) {
            boolean zh = "zh".equals(locale) || locale == null || locale.startsWith("zh");
            if (boundCount > 0 && nearbyCount > 0) {
                return zh
                    ? "\u5728AdvanceDataMonitor\u7ed1\u5b9a\u6570\u636e\u4e2d\u627e\u5230" + boundCount
                        + "\u4e2a\u8fde\u63a5\u5668\uff0c\u9644\u8fd1\u641c\u7d22\u5230"
                        + nearbyCount
                        + "\u4e2a\u8fde\u63a5\u5668"
                    : "Found " + boundCount
                        + " connector(s) in AdvanceDataMonitor bindings and "
                        + nearbyCount
                        + " connector(s) nearby";
            }
            if (boundCount > 0) {
                return zh
                    ? "\u5728AdvanceDataMonitor\u7ed1\u5b9a\u6570\u636e\u4e2d\u627e\u5230" + boundCount
                        + "\u4e2a\u8fde\u63a5\u5668"
                    : "Found " + boundCount + " connector(s) in AdvanceDataMonitor bindings";
            }
            return zh ? "\u5728\u9644\u8fd1\u641c\u7d22\u5230" + nearbyCount + "\u4e2a\u8fde\u63a5\u5668"
                : "Found " + nearbyCount + " connector(s) nearby";
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends TileEntity> ConnectorSource<T> findAllLinkTiles(EntityPlayerMP player, Class<T> type,
        int radius) {
        if (player == null || player.worldObj == null) {
            return new ConnectorSource<T>(new ArrayList<T>(), 0, 0);
        }
        // Step 1: Find AdvanceDataMonitors near the player and extract bound connectors
        Set<String> boundCoordKeys = new HashSet<String>();
        List<T> boundConnectors = new ArrayList<T>();
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        for (int x = px - radius; x <= px + radius; x++) {
            for (int y = Math.max(0, py - radius); y <= Math.min(255, py + radius); y++) {
                for (int z = pz - radius; z <= pz + radius; z++) {
                    TileEntity te = player.worldObj.getTileEntity(x, y, z);
                    if (!(te instanceof TileEntityAdvanceDataMonitor)) {
                        continue;
                    }
                    TileEntityAdvanceDataMonitor monitor = (TileEntityAdvanceDataMonitor) te;
                    int count = monitor.getDataBoundCount();
                    for (int i = 0; i < count; i++) {
                        int[] pos = monitor.parseBoundXYZ(i);
                        if (pos == null) continue;
                        String key = pos[0] + "," + pos[1] + "," + pos[2];
                        if (!boundCoordKeys.add(key)) continue;
                        if (!player.worldObj.blockExists(pos[0], pos[1], pos[2])) continue;
                        TileEntity boundTe = player.worldObj.getTileEntity(pos[0], pos[1], pos[2]);
                        if (type.isInstance(boundTe)) {
                            boundConnectors.add((T) boundTe);
                        }
                    }
                }
            }
        }
        int boundCount = boundConnectors.size();
        if (boundCount > 0) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] Found {} bound {} connector(s) from AdvanceDataMonitor bindings for player {}",
                boundCount,
                type.getSimpleName(),
                player.getCommandSenderName());
            return new ConnectorSource<T>(boundConnectors, boundCount, 0);
        }
        // Step 2: Fallback — search nearby for any matching connector not already found
        List<T> nearbyConnectors = new ArrayList<T>();
        T nearest = findNearest(player, type, radius);
        if (nearest != null) {
            String key = nearest.xCoord + "," + nearest.yCoord + "," + nearest.zCoord;
            if (boundCoordKeys.add(key)) {
                nearbyConnectors.add(nearest);
            }
        }
        if (nearbyConnectors.isEmpty()) {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] No {} connector found (neither bound nor nearby) for player {}",
                type.getSimpleName(),
                player.getCommandSenderName());
        } else {
            AdvanceDataMonitor.LOG.info(
                "[ADM Assistant] No bound {} connectors; falling back to {} nearby connector(s) for player {}",
                type.getSimpleName(),
                nearbyConnectors.size(),
                player.getCommandSenderName());
        }
        return new ConnectorSource<T>(nearbyConnectors, 0, nearbyConnectors.size());
    }
}
