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
import com.imgood.textech.assistant.AssistantServerServices.OrderProgressResult;
import com.imgood.textech.assistant.CraftingCandidate;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.craft.WebAeCraftService;
import com.imgood.textech.webae.dto.OrderBatchRequest;
import com.imgood.textech.webae.dto.OrderRequest;
import com.imgood.textech.webae.dto.OrderResult;
import com.imgood.textech.webae.dto.OrderStatus;
import com.imgood.textech.webae.pattern.InterfaceLocator;

import fi.iki.elonen.NanoHTTPD;

/**
 * 
 * REST handler for AE crafting orders.
 *
 * 
 * 
 * POST /api/order — submit single craft order
 * 
 * POST /api/order/batch — batch order
 * 
 * GET /api/order/status?jobId=<id> — poll job status
 * 
 * POST /api/order/cancel — cancel all pending jobs
 * 
 * GET /api/order/list — list active orders + history
 * 
 */

public class OrderHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();

    private static final long MAIN_THREAD_TIMEOUT_MS = 15_000L;

    private static final int HISTORY_MAX = 200;

    private static final Map<String, OrderTrackEntry> activeOrders = new ConcurrentHashMap<String, OrderTrackEntry>();

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

    private static final LinkedList<OrderStatus> historyOrders = new LinkedList<OrderStatus>();

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

        return jsonResponse(
            NanoHTTPD.Response.Status.NOT_FOUND,

            GSON.toJson(fail("Unknown order endpoint")));

    }

    private static NanoHTTPD.Response handleSubmit(String body, String playerUuid) {

        if (body == null || body.isEmpty()) {

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Missing request body")));

        }

        OrderRequest req;

        try {

            req = GSON.fromJson(body, OrderRequest.class);

        } catch (Exception e) {

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Invalid JSON: " + e.getMessage())));

        }

        if (req.itemName == null || req.itemName.trim()
            .isEmpty()) {

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Missing 'itemName'")));

        }

        if (req.amount <= 0) {

            req.amount = 1;

        }

        final String rawText = req.rawText != null ? req.rawText : req.itemName;

        final String locale = req.locale != null ? req.locale : "en_US";

        final String cpuName = normalizeCpuName(req.cpuName);

        final String jobId = UUID.randomUUID()
            .toString()
            .substring(0, 8);

        final long now = System.currentTimeMillis();

        final int networkId = req.networkId;

        final String[] resultHolder = new String[1];

        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override

            public void run() {

                try {

                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);

                    if (player == null) {

                        resultHolder[0] = GSON.toJson(fail("Owner network context unavailable"));

                        return;

                    }

                    List<CraftingCandidate> candidates = WebAeCraftService

                        .craftingCandidates(playerUuid, networkId, rawText, req.itemName, req.amount);

                    if (candidates == null || candidates.isEmpty()) {

                        resultHolder[0] = GSON.toJson(fail("No craftable item found for: " + req.itemName));

                        return;

                    }

                    CraftingCandidate candidate = candidates.get(0);

                    String craftResult = WebAeCraftService.submitCraft(

                        playerUuid,
                        networkId,
                        candidate,
                        req.amount,
                        rawText,
                        locale,
                        cpuName);

                    OrderTrackEntry entry = new OrderTrackEntry();

                    entry.jobId = jobId;

                    entry.playerUuid = playerUuid;

                    entry.itemName = candidate.displayName;

                    entry.amount = req.amount;

                    entry.submittedAt = now;

                    entry.cpuName = cpuName;

                    entry.cpuInfo = AssistantServerServices.snapshotCpuInfo(player, cpuName);

                    activeOrders.put(jobId, entry);

                    OrderResult or = new OrderResult();

                    or.success = true;

                    or.craftJobId = jobId;

                    or.message = craftResult;

                    or.estimatedTime = -1;

                    resultHolder[0] = GSON.toJson(or);

                } catch (Throwable t) {

                    AdvanceDataMonitor.LOG.error("[WebAE] Order submit failed", t);

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

        return jsonResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,

            GSON.toJson(fail("Order submission timed out")));

    }

    private static NanoHTTPD.Response handleBatch(String body, String playerUuid) {

        if (body == null || body.isEmpty()) {

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Missing request body")));

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

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Missing 'items' array")));

        }

        final String cpuName = normalizeCpuName(req.cpuName);

        final String[] resultHolder = new String[1];

        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override

            public void run() {

                try {

                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);

                    if (player == null) {

                        resultHolder[0] = GSON.toJson(fail("Owner network context unavailable"));

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

                                resultHolder[0] = GSON.toJson(fail("Cannot decode pattern: " + item.patternId));

                                return;

                            }

                            candidates = new ArrayList<CraftingCandidate>();

                            candidates.add(patternCandidate);

                            displayLabel = patternCandidate.displayName;

                        } else {

                            if (item.itemName == null || item.itemName.trim()
                                .isEmpty()) {

                                resultHolder[0] = GSON
                                    .toJson(fail("Item #" + (i + 1) + " missing itemName and patternId"));

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

                    long now = System.currentTimeMillis();

                    OrderStatus.CpuInfo cpuInfo = AssistantServerServices.snapshotCpuInfo(player, cpuName);

                    for (int i = 0; i < lines.size(); i++) {

                        String jobId = UUID.randomUUID()
                            .toString()
                            .substring(0, 8);

                        AssistantOrderLine line = lines.get(i);

                        CraftingCandidate candidate = line.selectedOrFirstCandidate();

                        String lineMessage = "No candidate";

                        if (candidate != null) {

                            lineMessage = WebAeCraftService.submitCraft(
                                playerUuid,
                                req.networkId,
                                candidate,
                                line.amount,
                                line.target,
                                "en_US",
                                cpuName);

                            OrderTrackEntry entry = new OrderTrackEntry();

                            entry.jobId = jobId;

                            entry.playerUuid = playerUuid;

                            entry.itemName = candidate.displayName;

                            entry.amount = line.amount;

                            entry.submittedAt = now;

                            entry.cpuName = cpuName;

                            entry.cpuInfo = cpuInfo;

                            activeOrders.put(jobId, entry);

                        }

                        OrderResult or = new OrderResult();

                        or.success = candidate != null && lineMessage != null
                            && !lineMessage.isEmpty()
                            && !lineMessage.toLowerCase()
                                .contains("fail");

                        or.craftJobId = jobId;

                        or.message = lineMessage;

                        or.estimatedTime = -1;

                        results.add(or);

                    }

                    resultHolder[0] = GSON.toJson(results);

                } catch (Throwable t) {

                    AdvanceDataMonitor.LOG.error("[WebAE] Batch order failed", t);

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

        return jsonResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,

            GSON.toJson(fail("Batch order timed out")));

    }

    private static NanoHTTPD.Response handleStatus(Map<String, String> params, String playerUuid) {

        String jobId = params.get("jobId");

        if (jobId == null || jobId.isEmpty()) {

            return jsonResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,

                GSON.toJson(fail("Missing 'jobId' parameter")));

        }

        OrderTrackEntry entry = activeOrders.get(jobId);

        if (entry == null) {

            OrderStatus status = findHistoryByJobId(jobId);

            if (status != null) {

                return jsonResponse(NanoHTTPD.Response.Status.OK, GSON.toJson(status));

            }

            OrderStatus completed = new OrderStatus();

            completed.craftJobId = jobId;

            completed.status = "completed";

            completed.progressPercent = 100;

            completed.finalProgress = 100;

            completed.message = "Order has been processed.";

            completed.submittedAt = 0;

            completed.completedAt = System.currentTimeMillis();

            return jsonResponse(NanoHTTPD.Response.Status.OK, GSON.toJson(completed));

        }

        EntityPlayerMP player = getPlayer(entry.playerUuid != null ? entry.playerUuid : playerUuid);

        OrderStatus status = buildOrderStatus(entry, player, true);

        if ("completed".equals(status.status) || "cancelled".equals(status.status)) {

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

                        entry.cancelledAt = now;

                        OrderStatus status = buildOrderStatus(entry, player, false);

                        status.status = "cancelled";

                        status.progressPercent = 0;

                        status.completedAt = now;

                        moveToHistory(entry, status);

                        iter.remove();

                    }

                    resultHolder[0] = "{\"success\":true,\"message\":" + GSON.toJson(cancelResult) + "}";

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

        return jsonResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,

            GSON.toJson(fail("Order cancel timed out")));

    }

    private static NanoHTTPD.Response handleList(String playerUuid) {

        EntityPlayerMP player = getPlayer(playerUuid);

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, OrderTrackEntry>> pruneIter = activeOrders.entrySet()
            .iterator();

        while (pruneIter.hasNext()) {

            Map.Entry<String, OrderTrackEntry> e = pruneIter.next();

            OrderTrackEntry entry = e.getValue();

            if (entry.playerUuid != null && !entry.playerUuid.equals(playerUuid)) {

                continue;

            }

            if (now - entry.submittedAt > 300_000L) {

                OrderStatus expired = buildOrderStatus(entry, player, false);

                expired.status = "completed";

                expired.progressPercent = 100;

                expired.finalProgress = 100;

                expired.completedAt = now;

                moveToHistory(entry, expired);

                pruneIter.remove();

            }

        }

        List<OrderStatus> active = new ArrayList<OrderStatus>();

        List<String> completedIds = new ArrayList<String>();

        for (Map.Entry<String, OrderTrackEntry> e : activeOrders.entrySet()) {

            OrderTrackEntry entry = e.getValue();

            if (entry.playerUuid != null && !entry.playerUuid.equals(playerUuid)) {

                continue;

            }

            OrderStatus status = buildOrderStatus(entry, player, true);

            if ("completed".equals(status.status)) {

                moveToHistory(entry, status);

                completedIds.add(e.getKey());

            } else {

                active.add(status);

            }

        }

        for (String id : completedIds) {

            activeOrders.remove(id);

        }

        List<OrderStatus> history = getHistorySnapshot();

        return jsonResponse(
            NanoHTTPD.Response.Status.OK,

            "{\"success\":true,\"orders\":" + GSON.toJson(active)

                + ",\"history\":"
                + GSON.toJson(history)
                + "}");

    }

    private static OrderStatus buildOrderStatus(OrderTrackEntry entry, EntityPlayerMP player, boolean useRealProgress) {

        OrderStatus status = new OrderStatus();

        status.craftJobId = entry.jobId;

        status.message = entry.itemName + " x" + entry.amount;

        status.submittedAt = entry.submittedAt;

        status.cpuName = entry.cpuName;

        status.cpuInfo = entry.cpuInfo;

        if (entry.cancelledAt > 0) {

            status.status = "cancelled";

            status.progressPercent = 0;

            status.finalProgress = 0;

            status.completedAt = entry.cancelledAt;

            return status;

        }

        if (useRealProgress && player != null) {

            OrderProgressResult progress = AssistantServerServices.resolveOrderProgress(

                player,

                entry.cpuName,

                entry.itemName,

                entry.submittedAt);

            status.status = progress.status;

            status.progressPercent = progress.progressPercent;

            if (progress.completed) {

                status.finalProgress = 100;

                status.completedAt = System.currentTimeMillis();

            } else {

                status.completedAt = -1;

            }

            return status;

        }

        int est = AssistantServerServices.estimateTimeProgress(entry.submittedAt);

        if (est >= 100) {

            status.status = "completed";

            status.progressPercent = 100;

            status.finalProgress = 100;

            status.completedAt = System.currentTimeMillis();

        } else if (est > 30) {

            status.status = "crafting";

            status.progressPercent = est;

            status.completedAt = -1;

        } else {

            status.status = "pending";

            status.progressPercent = est;

            status.completedAt = -1;

        }

        return status;

    }

    private static synchronized void moveToHistory(OrderTrackEntry entry, OrderStatus status) {

        if ("completed".equals(status.status)) {

            status.progressPercent = 100;

            status.finalProgress = 100;

            if (status.completedAt <= 0) {

                status.completedAt = System.currentTimeMillis();

            }

        }

        historyOrders.addFirst(status);

        while (historyOrders.size() > HISTORY_MAX) {

            historyOrders.removeLast();

        }

    }

    private static synchronized List<OrderStatus> getHistorySnapshot() {

        return new ArrayList<OrderStatus>(historyOrders);

    }

    /**
     * Recent completed orders for WebAE alert notifications (A4).
     */
    public static synchronized List<OrderStatus> getRecentCompletedOrders(String ownerUuid, int limit) {
        List<OrderStatus> result = new ArrayList<OrderStatus>();
        for (OrderStatus s : historyOrders) {
            if (s == null || !"completed".equals(s.status)) {
                continue;
            }
            if (limit > 0 && result.size() >= limit) {
                break;
            }
            result.add(s);
        }
        return result;
    }

    private static synchronized OrderStatus findHistoryByJobId(String jobId) {

        for (OrderStatus s : historyOrders) {

            if (jobId.equals(s.craftJobId)) {

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

        if (patternId == null || patternId.isEmpty()) return null;

        int hashIdx = patternId.indexOf('#');

        if (hashIdx < 0) return null;

        String coords = patternId.substring(0, hashIdx);

        String slotStr = patternId.substring(hashIdx + 1);

        String[] parts = coords.split(":");

        if (parts.length != 4) return null;

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

        if (world == null || !world.blockExists(x, y, z)) return null;

        TileEntity te = world.getTileEntity(x, y, z);

        if (te == null || !InterfaceLocator.isInterface(te)) return null;

        IInventory patterns = InterfaceLocator.getPatterns(te);

        if (patterns == null) return null;

        ItemStack patternStack = patterns.getStackInSlot(slot);

        if (patternStack == null || patternStack.getItem() == null) return null;

        NBTTagCompound nbt = patternStack.getTagCompound();

        if (nbt == null) return null;

        NBTTagList outList = nbt.getTagList("out", 10);

        if (outList == null || outList.tagCount() == 0) return null;

        for (int i = 0; i < outList.tagCount(); i++) {

            NBTTagCompound stackTag = outList.getCompoundTagAt(i);

            ItemStack output = ItemStack.loadItemStackFromNBT(stackTag);

            if (output == null || output.getItem() == null) continue;

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

        String itemName;

        long amount;

        long submittedAt;

        long cancelledAt;

        String cpuName;

        OrderStatus.CpuInfo cpuInfo;

    }

}
