package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.assistant.AssistantOrderLine;
import com.imgood.textech.assistant.AssistantServerServices;
import com.imgood.textech.assistant.CraftSubmitHooks;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.craft.CraftSubmitResult;
import com.imgood.textech.webae.craft.WebAeCraftService;
import com.imgood.textech.webae.cpu.CpuHistoryService;
import com.imgood.textech.webae.dto.OrderBatchRequest;
import com.imgood.textech.webae.dto.OrderRequest;
import com.imgood.textech.webae.dto.OrderResult;
import com.imgood.textech.webae.dto.OrderStatus;
import com.imgood.textech.webae.order.WebAeOrderProgressService;
import com.imgood.textech.webae.pattern.InterfaceLocator;

import appeng.api.networking.crafting.ICraftingLink;
import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for AE crafting orders.
 *
 * <p>
 * POST /api/order — submit single craft order<br>
 * POST /api/order/batch — batch order<br>
 * GET /api/order/status?jobId=&lt;id&gt; — poll job status<br>
 * POST /api/order/cancel — cancel all pending jobs<br>
 * GET /api/order/list — list active orders + history
 */
public class OrderHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private static final long MAIN_THREAD_TIMEOUT_MS = 15_000L;

    private static final int HISTORY_MAX = 200;

    private static final Map<String, OrderTrackEntry> activeOrders = new ConcurrentHashMap<String, OrderTrackEntry>();

    /** Async pending results: main-thread task writes JSON here, HTTP poller reads it. */
    private static final Map<String, String> asyncResults = new ConcurrentHashMap<String, String>();

    private static final LinkedList<OrderStatus> historyOrders = new LinkedList<OrderStatus>();

    /** Active (non-completed) craft orders for OC summary and monitoring. */
    public static int countActiveOrdersForOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, OrderTrackEntry> e : activeOrders.entrySet()) {
            OrderTrackEntry entry = e.getValue();
            if (entry != null && ownerUuid.equals(entry.playerUuid)) {
                count++;
            }
        }
        return count;
    }

    /** Called from AE2 submit callback when CPU accepts the job. */
    public static void bindCraftingLink(String jobId, ICraftingLink link, String craftingId, String resolvedCpuName) {
        if (jobId == null) {
            return;
        }
        OrderTrackEntry entry = activeOrders.get(jobId);
        if (entry == null) {
            return;
        }
        entry.link = link;
        entry.craftingId = craftingId;
        if (resolvedCpuName != null && !resolvedCpuName.isEmpty()) {
            entry.cpuName = resolvedCpuName;
        }
        entry.seenCrafting = true;
        WebAeOrderProgressService.invalidate(entry.playerUuid, entry.networkId);
        CpuHistoryService.instance()
            .recordRunning(
                entry.playerUuid,
                entry.networkId,
                entry.jobId,
                entry.cpuName,
                null,
                entry.cpuInfo != null ? Integer.valueOf(entry.cpuInfo.coProcessors) : null);
    }

    /** Called when calculation/submit fails after the order was tracked as calculating. */
    public static void markFailed(String jobId, String reason) {
        if (jobId == null) {
            return;
        }
        OrderTrackEntry entry = activeOrders.get(jobId);
        if (entry == null) {
            return;
        }
        entry.failReason = reason != null ? reason : "failed";
        entry.failedAt = System.currentTimeMillis();
        OrderStatus status = buildOrderStatus(entry, true);
        status.status = "failed";
        status.failReason = entry.failReason;
        status.message = entry.itemName + " x" + entry.amount + " — " + entry.failReason;
        status.completedAt = entry.failedAt;
        status.progressPercent = entry.lastProgress;
        status.finalProgress = entry.lastProgress;
        moveToHistory(entry, status);
        activeOrders.remove(jobId);
    }

    /** Mark every pre-tracked child of a failed batch instead of silently dropping its history. */
    private static void markBatchFailed(String batchJobId, String reason) {
        if (batchJobId == null || batchJobId.isEmpty()) {
            return;
        }
        String prefix = batchJobId + "-";
        List<String> jobIds = new ArrayList<String>();
        for (Map.Entry<String, OrderTrackEntry> e : activeOrders.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                jobIds.add(e.getKey());
            }
        }
        for (int i = 0; i < jobIds.size(); i++) {
            markFailed(jobIds.get(i), reason);
        }
    }

    public static NanoHTTPD.Response handle(String uri, Map<String, String> params, String body, String playerUuid) {
        if ("/api/order".equals(uri)) {
            return handleSubmit(body, playerUuid);
        }
        if ("/api/order/batch".equals(uri)) {
            return handleBatch(body, playerUuid);
        }
        if ("/api/order/status".equals(uri)) {
            return handleStatus(params, playerUuid);
        }
        if ("/api/order/cancel".equals(uri)) {
            return handleCancel(body, playerUuid);
        }
        if ("/api/order/list".equals(uri)) {
            return handleList(playerUuid);
        }
        return jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, GSON.toJson(fail("Unknown order endpoint")));
    }

    private static NanoHTTPD.Response handleSubmit(String body, String playerUuid) {
        if (body == null || body.isEmpty()) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, GSON.toJson(fail("Missing request body")));
        }
        OrderRequest req;
        try {
            req = GSON.fromJson(body, OrderRequest.class);
        } catch (Exception e) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                GSON.toJson(fail("Invalid JSON: " + e.getMessage())));
        }
        final boolean hasPatternId = req.patternId != null && !req.patternId.trim()
            .isEmpty();
        if (!hasPatternId && (req.itemName == null || req.itemName.trim()
            .isEmpty())) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                GSON.toJson(fail("Missing 'itemName' or 'patternId'")));
        }
        if (req.amount <= 0) {
            req.amount = 1;
        }
        final String rawText = req.rawText != null ? req.rawText
            : (req.itemName != null ? req.itemName : req.patternId);
        final String locale = req.locale != null ? req.locale : "en_US";
        final String cpuName = normalizeCpuName(req.cpuName);
        final String patternId = hasPatternId ? req.patternId.trim() : null;
        final String jobId = UUID.randomUUID()
            .toString()
            .substring(0, 8);
        final long now = System.currentTimeMillis();
        final int networkId = req.networkId;

        // Pre-register a calculating entry so status polls pick it up immediately.
        OrderTrackEntry preEntry = new OrderTrackEntry();
        preEntry.jobId = jobId;
        preEntry.playerUuid = playerUuid;
        preEntry.networkId = networkId;
        preEntry.itemName = req.itemName != null ? req.itemName : (patternId != null ? patternId : "calculating");
        preEntry.amount = req.amount;
        preEntry.patternId = patternId;
        preEntry.submittedAt = now;
        preEntry.cpuName = cpuName;
        preEntry.cpuInfo = WebAeOrderProgressService.snapshotCpuInfo(playerUuid, networkId, cpuName);
        activeOrders.put(jobId, preEntry);

        // Enqueue main-thread work without blocking the HTTP thread.
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    CpuHistoryService.instance()
                        .recordQueued(playerUuid, networkId, jobId, cpuName, patternId);
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
                    if (player == null) {
                        OrderHandler.markFailed(jobId, "Owner network context unavailable");
                        asyncResults.put(jobId, GSON.toJson(fail("Owner network context unavailable")));
                        return;
                    }
                    CraftingCandidate candidate;
                    if (patternId != null) {
                        candidate = resolvePatternCandidate(patternId, req.amount);
                        if (candidate == null) {
                            OrderHandler.markFailed(jobId, "Cannot decode pattern: " + patternId);
                            asyncResults.put(jobId, GSON.toJson(fail("Cannot decode pattern: " + patternId)));
                            return;
                        }
                    } else {
                        List<CraftingCandidate> candidates = WebAeCraftService
                            .craftingCandidates(playerUuid, networkId, rawText, req.itemName, req.amount);
                        if (candidates == null || candidates.isEmpty()) {
                            OrderHandler.markFailed(jobId, "No craftable item found");
                            asyncResults.put(jobId, GSON.toJson(fail("No craftable item found for: " + req.itemName)));
                            return;
                        }
                        candidate = candidates.get(0);
                    }

                    OrderTrackEntry entry = activeOrders.get(jobId);
                    if (entry == null) {
                        entry = preEntry;
                        activeOrders.put(jobId, entry);
                    }
                    entry.itemName = candidate.displayName;

                    CraftSubmitHooks hooks = new CraftSubmitHooks() {

                        @Override
                        public void onSubmitted(ICraftingLink link, String craftingId, String resolvedCpuName) {
                            OrderHandler.bindCraftingLink(jobId, link, craftingId, resolvedCpuName);
                        }

                        @Override
                        public void onFailed(String reason) {
                            OrderHandler.markFailed(jobId, reason);
                        }
                    };

                    CraftSubmitResult craftResult = WebAeCraftService.submitCraftTracked(
                        playerUuid,
                        networkId,
                        candidate,
                        req.amount,
                        rawText,
                        locale,
                        cpuName,
                        jobId,
                        hooks);

                    if (craftResult == null || craftResult.failed || !craftResult.accepted) {
                        String failure = craftResult != null ? craftResult.message : "submit failed";
                        OrderHandler.markFailed(jobId, failure);
                        OrderResult or = fail(failure);
                        or.craftJobId = "";
                        asyncResults.put(jobId, GSON.toJson(or));
                        return;
                    }

                    OrderResult or = new OrderResult();
                    or.success = true;
                    or.craftJobId = jobId;
                    or.message = craftResult.message;
                    or.estimatedTime = -1;
                    asyncResults.put(jobId, GSON.toJson(or));
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Order submit failed", t);
                    OrderHandler.markFailed(jobId, "Internal error");
                    asyncResults.put(jobId, GSON.toJson(fail("Internal error: " + t.getMessage())));
                }
            }
        });

        // Return immediately with 202 + jobId; client polls /api/order/status?jobId=xxx
        String pendingJson = "{\"success\":true,\"craftJobId\":\"" + jobId
            + "\",\"message\":\"calculating\",\"estimatedTime\":-1,\"status\":\"calculating\"}";
        return jsonResponse(NanoHTTPD.Response.Status.ACCEPTED, pendingJson);
    }

    private static NanoHTTPD.Response handleBatch(String body, String playerUuid) {
        if (body == null || body.isEmpty()) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, GSON.toJson(fail("Missing request body")));
        }
        OrderBatchRequest req;
        try {
            req = GSON.fromJson(body, OrderBatchRequest.class);
        } catch (Exception e) {
            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                GSON.toJson(fail("Invalid JSON: " + e.getMessage())));
        }
        if (req.items == null || req.items.isEmpty()) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, GSON.toJson(fail("Missing 'items' array")));
        }
        final String cpuName = normalizeCpuName(req.cpuName);
        final String batchJobId = "batch-" + UUID.randomUUID()
            .toString()
            .substring(0, 8);

        // Pre-register simple entries for each item so status polls work.
        final long now = System.currentTimeMillis();
        for (int i = 0; i < req.items.size(); i++) {
            OrderBatchRequest.OrderItem item = req.items.get(i);
            String subJobId = batchJobId + "-" + i;
            OrderTrackEntry preEntry = new OrderTrackEntry();
            preEntry.jobId = subJobId;
            preEntry.playerUuid = playerUuid;
            preEntry.networkId = req.networkId;
            preEntry.itemName = item.itemName != null ? item.itemName
                : (item.patternId != null ? item.patternId : "calculating");
            preEntry.amount = Math.max(1, item.amount);
            preEntry.patternId = item.patternId != null && !item.patternId.isEmpty() ? item.patternId : null;
            preEntry.submittedAt = now;
            preEntry.cpuName = cpuName;
            preEntry.cpuInfo = WebAeOrderProgressService.snapshotCpuInfo(playerUuid, req.networkId, cpuName);
            activeOrders.put(subJobId, preEntry);
        }

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    for (int i = 0; i < req.items.size(); i++) {
                        OrderBatchRequest.OrderItem item = req.items.get(i);
                        String subJobId = batchJobId + "-" + i;
                        CpuHistoryService.instance()
                            .recordQueued(
                                playerUuid,
                                req.networkId,
                                subJobId,
                                cpuName,
                                item != null ? item.patternId : null);
                    }
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
                    if (player == null) {
                        OrderHandler.markBatchFailed(batchJobId, "Owner network context unavailable");
                        asyncResults.put(batchJobId, GSON.toJson(fail("Owner network context unavailable")));
                        return;
                    }
                    List<AssistantOrderLine> lines = new ArrayList<AssistantOrderLine>();
                    for (int i = 0; i < req.items.size(); i++) {
                        OrderBatchRequest.OrderItem item = req.items.get(i);
                        int amt = Math.max(1, item.amount);
                        List<CraftingCandidate> candidates;
                        String displayLabel;
                        if (item.patternId != null && !item.patternId.isEmpty()) {
                            CraftingCandidate patternCandidate = resolvePatternCandidate(item.patternId, amt);
                            if (patternCandidate == null) {
                                OrderHandler.markBatchFailed(batchJobId, "Cannot decode pattern");
                                asyncResults
                                    .put(batchJobId, GSON.toJson(fail("Cannot decode pattern: " + item.patternId)));
                                return;
                            }
                            candidates = new ArrayList<CraftingCandidate>();
                            candidates.add(patternCandidate);
                            displayLabel = patternCandidate.displayName;
                        } else {
                            if (item.itemName == null || item.itemName.trim()
                                .isEmpty()) {
                                OrderHandler.markBatchFailed(batchJobId, "Missing itemName and patternId");
                                asyncResults.put(
                                    batchJobId,
                                    GSON.toJson(fail("Item #" + (i + 1) + " missing itemName and patternId")));
                                return;
                            }
                            candidates = WebAeCraftService
                                .craftingCandidates(playerUuid, req.networkId, item.itemName, item.itemName, amt);
                            displayLabel = item.itemName;
                        }
                        AssistantOrderLine line = new AssistantOrderLine(i + 1, displayLabel, amt);
                        line.setCandidates(candidates);
                        lines.add(line);
                    }

                    List<OrderResult> results = new ArrayList<OrderResult>();
                    OrderStatus.CpuInfo cpuInfo = WebAeOrderProgressService
                        .snapshotCpuInfo(playerUuid, req.networkId, cpuName);

                    for (int i = 0; i < lines.size(); i++) {
                        final String subJobId = batchJobId + "-" + i;
                        AssistantOrderLine line = lines.get(i);
                        CraftingCandidate candidate = line.selectedOrFirstCandidate();
                        OrderResult or = new OrderResult();
                        or.craftJobId = subJobId;
                        or.estimatedTime = -1;
                        if (candidate == null) {
                            OrderHandler.markFailed(subJobId, "No candidate");
                            or.success = false;
                            or.message = "No candidate";
                            results.add(or);
                            continue;
                        }
                        OrderTrackEntry entry = activeOrders.get(subJobId);
                        if (entry != null) {
                            entry.itemName = candidate.displayName;
                            entry.cpuInfo = cpuInfo;
                        }
                        CraftSubmitHooks hooks = new CraftSubmitHooks() {

                            @Override
                            public void onSubmitted(ICraftingLink link, String craftingId, String resolvedCpuName) {
                                OrderHandler.bindCraftingLink(subJobId, link, craftingId, resolvedCpuName);
                            }

                            @Override
                            public void onFailed(String reason) {
                                OrderHandler.markFailed(subJobId, reason);
                            }
                        };
                        CraftSubmitResult craftResult = WebAeCraftService.submitCraftTracked(
                            playerUuid,
                            req.networkId,
                            candidate,
                            line.amount,
                            line.target,
                            "en_US",
                            cpuName,
                            subJobId,
                            hooks);
                        if (craftResult == null || craftResult.failed || !craftResult.accepted) {
                            OrderHandler.markFailed(
                                subJobId,
                                craftResult != null ? craftResult.message : "submit failed");
                            or.success = false;
                            or.message = craftResult != null ? craftResult.message : "submit failed";
                            or.craftJobId = "";
                            results.add(or);
                            continue;
                        }
                        or.success = true;
                        or.message = craftResult.message;
                        results.add(or);
                    }
                    asyncResults.put(batchJobId, GSON.toJson(results));
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Batch order failed", t);
                    OrderHandler.markBatchFailed(batchJobId, "Internal error");
                    asyncResults.put(batchJobId, GSON.toJson(fail("Internal error: " + t.getMessage())));
                }
            }
        });

        // Return 202 immediately; client polls /api/order/status?jobId=<batchJobId>
        String pendingJson = "{\"success\":true,\"craftJobId\":\"" + batchJobId
            + "\",\"message\":\"calculating\",\"estimatedTime\":-1,\"status\":\"calculating\"}";
        return jsonResponse(NanoHTTPD.Response.Status.ACCEPTED, pendingJson);
    }

    private static NanoHTTPD.Response handleStatus(Map<String, String> params, String playerUuid) {
        String jobId = params.get("jobId");
        if (jobId == null || jobId.isEmpty()) {
            return jsonResponse(NanoHTTPD.Response.Status.BAD_REQUEST, GSON.toJson(fail("Missing 'jobId' parameter")));
        }

        // Check async pending pool first (initial submit still on main thread).
        String asyncJson = asyncResults.get(jobId);
        if (asyncJson != null) {
            asyncResults.remove(jobId);
            return jsonResponse(NanoHTTPD.Response.Status.OK, asyncJson);
        }

        OrderTrackEntry entry = activeOrders.get(jobId);
        if (entry == null) {
            OrderStatus status = findHistoryByJobId(jobId, playerUuid);
            if (status != null) {
                return jsonResponse(NanoHTTPD.Response.Status.OK, GSON.toJson(status));
            }
            return jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, GSON.toJson(fail("Unknown jobId: " + jobId)));
        }
        if (entry.playerUuid != null && playerUuid != null && !entry.playerUuid.equals(playerUuid)) {
            return jsonResponse(NanoHTTPD.Response.Status.NOT_FOUND, GSON.toJson(fail("Unknown jobId: " + jobId)));
        }
        OrderStatus status = buildOrderStatus(entry, true);
        if (isTerminal(status.status)) {
            moveToHistory(entry, status);
            activeOrders.remove(jobId);
        }
        return jsonResponse(NanoHTTPD.Response.Status.OK, GSON.toJson(status));
    }

    private static NanoHTTPD.Response handleCancel(String body, String playerUuid) {
        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    EntityPlayerMP player = getPlayer(playerUuid);
                    if (player == null) {
                        resultHolder[0] = GSON.toJson(fail("Player is offline"));
                        return;
                    }
                    String cancelResult = AssistantServerServices.cancelPendingJobs(player, "en_US");
                    long now = System.currentTimeMillis();
                    Iterator<Map.Entry<String, OrderTrackEntry>> iter = activeOrders.entrySet()
                        .iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, OrderTrackEntry> e = iter.next();
                        OrderTrackEntry entry = e.getValue();
                        if (entry.playerUuid != null && !entry.playerUuid.equals(playerUuid)) {
                            continue;
                        }
                        if (entry.link != null) {
                            try {
                                entry.link.cancel();
                            } catch (Throwable ignored) {}
                        }
                        entry.cancelledAt = now;
                        entry.cancelReason = "web_cancel";
                        OrderStatus status = buildOrderStatus(entry, false);
                        status.status = "cancelled";
                        status.cancelReason = "web_cancel";
                        status.progressPercent = entry.lastProgress;
                        status.finalProgress = entry.lastProgress;
                        status.completedAt = now;
                        moveToHistory(entry, status);
                        iter.remove();
                    }
                    resultHolder[0] = "{\"success\":true,\"message\":"
                        + GSON.toJson(cancelResult != null ? cancelResult : "cancelled")
                        + "}";
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Order cancel failed", t);
                    resultHolder[0] = GSON.toJson(fail("Internal error: " + t.getMessage()));
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return jsonResponse(NanoHTTPD.Response.Status.OK, resultHolder[0]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return jsonResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, GSON.toJson(fail("Order cancel timed out")));
    }

    private static NanoHTTPD.Response handleList(String playerUuid) {
        List<OrderStatus> active = new ArrayList<OrderStatus>();
        List<String> terminalIds = new ArrayList<String>();
        for (Map.Entry<String, OrderTrackEntry> e : activeOrders.entrySet()) {
            OrderTrackEntry entry = e.getValue();
            if (entry.playerUuid != null && !entry.playerUuid.equals(playerUuid)) {
                continue;
            }
            OrderStatus status = buildOrderStatus(entry, true);
            if (isTerminal(status.status)) {
                moveToHistory(entry, status);
                terminalIds.add(e.getKey());
            } else {
                active.add(status);
            }
        }
        for (String id : terminalIds) {
            activeOrders.remove(id);
        }
        List<OrderStatus> history = getHistorySnapshotForOwner(playerUuid);
        return jsonResponse(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"orders\":" + GSON.toJson(active) + ",\"history\":" + GSON.toJson(history) + "}");
    }

    private static boolean isTerminal(String status) {
        return "completed".equals(status) || "cancelled".equals(status) || "failed".equals(status);
    }

    private static OrderStatus buildOrderStatus(OrderTrackEntry entry, boolean useRealProgress) {
        OrderStatus status = new OrderStatus();
        status.craftJobId = entry.jobId;
        status.message = entry.itemName + " x" + entry.amount;
        status.itemName = entry.itemName;
        status.amount = entry.amount;
        status.patternId = entry.patternId;
        status.submittedAt = entry.submittedAt;
        status.cpuName = entry.cpuName;
        status.cpuInfo = entry.cpuInfo;
        status.networkId = entry.networkId;
        status.craftingId = entry.craftingId;
        status.progressKind = "steps";
        status.startItems = entry.startItems;
        status.remainingItems = entry.remainingItems;
        status.elapsedMs = entry.elapsedMs;

        if (entry.failedAt > 0) {
            status.status = "failed";
            status.failReason = entry.failReason;
            status.progressPercent = entry.lastProgress;
            status.finalProgress = entry.lastProgress;
            status.completedAt = entry.failedAt;
            return status;
        }
        if (entry.cancelledAt > 0) {
            status.status = "cancelled";
            status.cancelReason = entry.cancelReason != null ? entry.cancelReason : "cancelled";
            status.progressPercent = entry.lastProgress;
            status.finalProgress = entry.lastProgress;
            status.completedAt = entry.cancelledAt;
            return status;
        }

        if (useRealProgress) {
            WebAeOrderProgressService.Query q = new WebAeOrderProgressService.Query();
            q.ownerUuid = entry.playerUuid;
            q.networkId = entry.networkId;
            q.trackingKey = entry.jobId;
            q.craftingId = entry.craftingId;
            q.link = entry.link;
            q.cpuName = entry.cpuName;
            q.itemName = entry.itemName;
            q.lastProgress = entry.lastProgress;

            WebAeOrderProgressService.Result progress = WebAeOrderProgressService.resolve(q);

            if (progress.onCpu || "crafting".equals(progress.status)) {
                entry.seenCrafting = true;
            }
            if (progress.progressPercent > entry.lastProgress) {
                entry.lastProgress = progress.progressPercent;
            }
            if (progress.startItems > 0) {
                entry.startItems = progress.startItems;
                entry.remainingItems = progress.remainingItems;
            }
            if (progress.elapsedMs > 0) {
                entry.elapsedMs = progress.elapsedMs;
            }

            status.startItems = entry.startItems;
            status.remainingItems = entry.remainingItems;
            status.elapsedMs = entry.elapsedMs;

            if ("cancelled".equals(progress.status)) {
                status.status = "cancelled";
                status.cancelReason = "ae_cancel";
                status.progressPercent = entry.lastProgress;
                status.finalProgress = entry.lastProgress;
                status.completedAt = System.currentTimeMillis();
                entry.cancelledAt = status.completedAt;
                entry.cancelReason = "ae_cancel";
                return status;
            }
            if (progress.completed || "completed".equals(progress.status)) {
                status.status = "completed";
                status.progressPercent = 100;
                status.finalProgress = 100;
                status.completedAt = System.currentTimeMillis();
                return status;
            }

            status.status = progress.status;
            status.progressPercent = progress.progressPercent > 0 ? progress.progressPercent : entry.lastProgress;
            status.completedAt = -1;
            if ("crafting".equals(status.status)) {
                CpuHistoryService.instance()
                    .recordRunning(
                        entry.playerUuid,
                        entry.networkId,
                        entry.jobId,
                        entry.cpuName,
                        Integer.valueOf(status.progressPercent),
                        entry.cpuInfo != null ? Integer.valueOf(entry.cpuInfo.coProcessors) : null);
            }
            return status;
        }

        status.status = entry.seenCrafting ? "crafting" : "pending";
        status.progressPercent = entry.lastProgress;
        status.completedAt = -1;
        return status;
    }

    private static synchronized void moveToHistory(OrderTrackEntry entry, OrderStatus status) {
        if (status != null && isTerminal(status.status) && historyContainsJob(status.craftJobId)) {
            return;
        }
        if ("completed".equals(status.status)) {
            status.progressPercent = 100;
            status.finalProgress = 100;
            if (status.completedAt <= 0) {
                status.completedAt = System.currentTimeMillis();
            }
        }
        if (entry != null) {
            status.networkId = entry.networkId;
            if (isTerminal(status.status)) {
                CpuHistoryService.instance()
                    .recordTerminal(
                        entry.playerUuid,
                        entry.networkId,
                        entry.jobId,
                        status.status,
                        entry.cpuName,
                        Integer.valueOf(status.progressPercent),
                        status.completedAt);
            }
            if (status.craftJobId != null && entry.playerUuid != null) {
                historyOwner.put(status.craftJobId, entry.playerUuid);
            }
        }
        historyOrders.addFirst(status);
        while (historyOrders.size() > HISTORY_MAX) {
            OrderStatus removed = historyOrders.removeLast();
            if (removed != null && removed.craftJobId != null) {
                historyOwner.remove(removed.craftJobId);
            }
        }
    }

    private static final Map<String, String> historyOwner = new ConcurrentHashMap<String, String>();

    /**
     * Lightweight server-tick observer for links that have reached a terminal
     * state.  HTTP status/list requests still resolve full progress, but this
     * path only asks the already-bound ICraftingLink for done/cancelled so CPU
     * history does not depend on a browser continuing to poll.
     */
    private static volatile long lastLinkObserverAt;

    public static void onServerTick(long now) {
        if (now - lastLinkObserverAt < 1000L) {
            return;
        }
        lastLinkObserverAt = now;
        for (Map.Entry<String, OrderTrackEntry> mapEntry : activeOrders.entrySet()) {
            OrderTrackEntry entry = mapEntry.getValue();
            if (entry == null || entry.link == null || entry.jobId == null || entry.failedAt > 0L
                || entry.cancelledAt > 0L) {
                continue;
            }
            boolean cancelled = false;
            boolean done = false;
            try {
                cancelled = entry.link.isCanceled();
                if (!cancelled) {
                    done = entry.link.isDone();
                }
            } catch (Throwable ignored) {
                // A link may disappear while a grid is unloading; leave the
                // order active for the regular resolver to settle it later.
                continue;
            }
            if (!cancelled && !done) {
                continue;
            }

            OrderStatus status = buildOrderStatus(entry, false);
            status.status = cancelled ? "cancelled" : "completed";
            status.progressPercent = cancelled ? entry.lastProgress : 100;
            status.finalProgress = status.progressPercent;
            status.completedAt = now;
            if (cancelled) {
                status.cancelReason = "ae_cancel";
                entry.cancelledAt = now;
                entry.cancelReason = "ae_cancel";
            }
            // Remove first so a concurrent HTTP request cannot continue to
            // expose the same active entry. moveToHistory also de-duplicates
            // terminal records by job id for the opposite race direction.
            if (activeOrders.remove(entry.jobId, entry)) {
                moveToHistory(entry, status);
            }
        }
    }

    private static boolean historyContainsJob(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return false;
        }
        for (OrderStatus existing : historyOrders) {
            if (existing != null && jobId.equals(existing.craftJobId)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized List<OrderStatus> getHistorySnapshotForOwner(String playerUuid) {
        List<OrderStatus> out = new ArrayList<OrderStatus>();
        for (OrderStatus s : historyOrders) {
            if (s == null || s.craftJobId == null) {
                continue;
            }
            String owner = historyOwner.get(s.craftJobId);
            if (owner != null && owner.equals(playerUuid)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Recent completed orders for WebAE alert notifications (A4), filtered by owner when possible.
     */
    public static synchronized List<OrderStatus> getRecentCompletedOrders(String ownerUuid, int limit) {
        List<OrderStatus> result = new ArrayList<OrderStatus>();
        for (OrderStatus s : historyOrders) {
            if (s == null || !"completed".equals(s.status)) {
                continue;
            }
            String owner = historyOwner.get(s.craftJobId);
            if (ownerUuid != null && owner != null && !ownerUuid.equals(owner)) {
                continue;
            }
            if (limit > 0 && result.size() >= limit) {
                break;
            }
            result.add(s);
        }
        return result;
    }

    private static synchronized OrderStatus findHistoryByJobId(String jobId, String playerUuid) {
        for (OrderStatus s : historyOrders) {
            if (jobId.equals(s.craftJobId)) {
                String owner = historyOwner.get(jobId);
                if (owner != null && playerUuid != null && !owner.equals(playerUuid)) {
                    return null;
                }
                return s;
            }
        }
        return null;
    }

    private static String normalizeCpuName(String cpuName) {
        if (cpuName == null) {
            return null;
        }
        String trimmed = cpuName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static EntityPlayerMP getPlayer(String playerUuid) {
        return WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
    }

    private static CraftingCandidate resolvePatternCandidate(String patternId, int amount) {
        if (patternId == null || patternId.isEmpty()) {
            return null;
        }
        int hashIdx = patternId.indexOf('#');
        if (hashIdx < 0) {
            return null;
        }
        String coords = patternId.substring(0, hashIdx);
        String slotStr = patternId.substring(hashIdx + 1);
        String[] parts = coords.split(":");
        if (parts.length != 4) {
            return null;
        }
        final int x, y, z, dim, slot;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
            dim = Integer.parseInt(parts[3]);
            slot = Integer.parseInt(slotStr);
        } catch (NumberFormatException e) {
            return null;
        }
        World world = DimensionManager.getWorld(dim);
        if (world == null || !world.blockExists(x, y, z)) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !InterfaceLocator.isInterface(te)) {
            return null;
        }
        IInventory patterns = InterfaceLocator.getPatterns(te);
        if (patterns == null) {
            return null;
        }
        ItemStack patternStack = patterns.getStackInSlot(slot);
        if (patternStack == null || patternStack.getItem() == null) {
            return null;
        }
        NBTTagCompound nbt = patternStack.getTagCompound();
        if (nbt == null) {
            return null;
        }
        NBTTagList outList = nbt.getTagList("out", 10);
        if (outList == null || outList.tagCount() == 0) {
            return null;
        }
        for (int i = 0; i < outList.tagCount(); i++) {
            NBTTagCompound stackTag = outList.getCompoundTagAt(i);
            ItemStack output = ItemStack.loadItemStackFromNBT(stackTag);
            if (output == null || output.getItem() == null) {
                continue;
            }
            return new CraftingCandidate(0, output, amount);
        }
        return null;
    }

    private static NanoHTTPD.Response jsonResponse(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static OrderResult fail(String message) {
        OrderResult r = new OrderResult();
        r.success = false;
        r.message = message;
        r.craftJobId = "";
        r.estimatedTime = -1;
        return r;
    }

    private static final class OrderTrackEntry {

        String jobId;
        String playerUuid;
        int networkId;
        String itemName;
        long amount;
        String patternId;
        long submittedAt;
        long cancelledAt;
        long failedAt;
        String cpuName;
        OrderStatus.CpuInfo cpuInfo;
        String craftingId;
        ICraftingLink link;
        boolean seenCrafting;
        int lastProgress;
        long startItems;
        long remainingItems;
        long elapsedMs;
        String failReason;
        String cancelReason;
    }
}
