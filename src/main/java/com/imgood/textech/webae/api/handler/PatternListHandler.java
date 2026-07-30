package com.imgood.textech.webae.api.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.compat.programmablehatches.ProgrammableHatchesCompat;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.utils.NBTJsonParser;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PatternDto;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto;
import com.imgood.textech.webae.dto.PatternListEntryDto;
import com.imgood.textech.webae.pattern.InterfaceLocator;
import com.imgood.textech.webae.pattern.PatternBrowseService;
import com.imgood.textech.webae.pattern.PatternWebBufferStore;
import com.imgood.textech.webae.pattern.PatternWebBufferStore.Entry;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import fi.iki.elonen.NanoHTTPD;

/**
 * REST handler for the WebAE pattern list endpoints (p2-patterneditor 仿增广样板终端).
 *
 * <p>
 * Endpoints:
 * </p>
 * <ul>
 * <li>{@code GET /api/patterns?network=<id>} — 列出当前网络所有 ME 接口中的样板，
 * 解码各样板 NBT 返回富样板列表（inputs/outputs/crafting/substitute/author/sourceInterface/slot）。</li>
 * <li>{@code GET /api/patterns/<id>} — 单个样板详情（{@code <id>} = {@code <x>:<y>:<z>:<dim>#<slot>}）。</li>
 * <li>{@code DELETE /api/patterns/<id>} — 从接口槽位清除样板（OP>=2）。</li>
 * <li>{@code PUT /api/patterns/<id>} — 编辑已有样板（解码→改→重编码→写回槽位，OP>=2）。</li>
 * </ul>
 *
 * <p>
 * 所有写操作（DELETE/PUT）需 OP>=2 权限；读操作仅需认证。所有 AE2 网络操作
 * 通过 {@link HandlerTick#enqueueServerTask} 在主线程执行。
 * </p>
 */
public class PatternListHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final long MAIN_THREAD_TIMEOUT_MS = 10_000L;

    public static NanoHTTPD.Response handle(String uri, NanoHTTPD.Method method, Map<String, String> params,
        String body, WebAuthSession auth, String adminHeader) {
        String ownerUuid = auth.ownerUuid;
        if ("/api/pattern-buffer".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET /api/pattern-buffer\"}");
            }
            return handleBufferList(params, ownerUuid);
        }
        if ("/api/pattern-buffer/take".equals(uri)) {
            if (method != NanoHTTPD.Method.POST) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use POST /api/pattern-buffer/take\"}");
            }
            return handleBufferTake(body, auth, adminHeader);
        }
        if ("/api/pattern-buffer/place".equals(uri)) {
            if (method != NanoHTTPD.Method.POST) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use POST /api/pattern-buffer/place\"}");
            }
            return handleBufferPlace(body, auth, adminHeader);
        }
        if ("/api/patterns/move".equals(uri)) {
            if (method != NanoHTTPD.Method.POST) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use POST /api/patterns/move\"}");
            }
            return handleMove(body, auth, adminHeader);
        }
        // GET /api/patterns?network=<id>
        if ("/api/patterns".equals(uri)) {
            if (method != NanoHTTPD.Method.GET) {
                return json(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "{\"success\":false,\"message\":\"Use GET /api/patterns\"}");
            }
            return handleList(params, ownerUuid);
        }
        // /api/patterns/<id> — GET 详情 / DELETE 删除 / PUT 编辑
        if (uri.startsWith("/api/patterns/")) {
            String idPart = uri.substring("/api/patterns/".length());
            if (idPart.isEmpty()) {
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Missing pattern id\"}");
            }
            if (method == NanoHTTPD.Method.GET) {
                return handleDetail(idPart, auth);
            }
            if (method == NanoHTTPD.Method.DELETE) {
                return handleDelete(idPart, auth, adminHeader);
            }
            if (method == NanoHTTPD.Method.PUT) {
                return handlePut(idPart, body, auth, adminHeader);
            }
            return json(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "{\"success\":false,\"message\":\"Use GET/DELETE/PUT on /api/patterns/<id>\"}");
        }
        return json(
            NanoHTTPD.Response.Status.NOT_FOUND,
            "{\"success\":false,\"message\":\"Unknown pattern list endpoint: " + uri + "\"}");
    }

    private static NanoHTTPD.Response handleBufferList(Map<String, String> params, String ownerUuid) {
        int networkId = parseNetworkId(params.get("network"));
        if (networkId < 0) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing or invalid network\"}");
        }
        List<Entry> entries = PatternWebBufferStore.instance()
            .list(ownerUuid, networkId);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"entries\":" + GSON.toJson(entries) + ",\"count\":" + entries.size() + "}");
    }

    // ---- GET /api/patterns?network=<id> ----

    private static NanoHTTPD.Response handleList(Map<String, String> params, String playerUuid) {
        String networkStr = params.get("network");
        if (networkStr == null || networkStr.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing 'network' parameter\"}");
        }
        final int networkId;
        try {
            networkId = Integer.parseInt(networkStr);
        } catch (NumberFormatException e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid 'network' parameter\"}");
        }

        SnapshotScheduler.markActive(playerUuid, networkId);
        boolean force = "1".equals(params.get("refresh")) || "true".equalsIgnoreCase(params.get("refresh"));
        if (force) {
            SnapshotCache.instance()
                .invalidateType(playerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH);
            SnapshotScheduler.forceCollectPatternsRich(playerUuid, networkId);
        }

        @SuppressWarnings("unchecked")
        List<PatternListEntryDto> fresh = SnapshotCache.instance()
            .get(playerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH);
        long ts = SnapshotCache.instance()
            .timestampOf(playerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH);
        if (fresh != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"patterns\":" + GSON
                    .toJson(fresh) + ",\"count\":" + fresh.size() + ",\"cached\":true,\"timestamp\":" + ts + "}");
        }
        @SuppressWarnings("unchecked")
        List<PatternListEntryDto> stale = SnapshotCache.instance()
            .getStale(playerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH);
        if (stale != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"patterns\":" + GSON
                    .toJson(stale) + ",\"count\":" + stale.size() + ",\"cached\":false,\"timestamp\":" + ts + "}");
        }
        SnapshotScheduler.forceCollectPatternsRich(playerUuid, networkId);
        return json(
            NanoHTTPD.Response.Status.OK,
            "{\"success\":true,\"patterns\":[],\"count\":0,\"cached\":false,\"timestamp\":0}");
    }

    /**
     * Build rich pattern list and store in {@link SnapshotCache}. Must run on server thread.
     */
    public static void buildAndStoreCache(String ownerUuid, int networkId) {
        EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(ownerUuid);
        if (player == null) {
            return;
        }
        WebAeOwnerContext.NetworkGroup group = WebAeOwnerContext.getNetworkGroup(ownerUuid, networkId);
        if (group != null) {
            WebAeOwnerContext.positionPlayerAtMonitor(player, group);
        }
        List<PatternListEntryDto> list = collectAllPatterns(player, networkId);
        SnapshotCache.instance()
            .put(ownerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH, list);
    }

    /**
     * 遍历当前网络所有 ME 接口的样板槽位，解码每个非空样板 ItemStack 的 NBT 为
     * {@link PatternListEntryDto}。复用 {@link InterfaceLocator#locate} 枚举接口。
     */
    private static List<PatternListEntryDto> collectAllPatterns(EntityPlayerMP player, int networkId) {
        List<PatternListEntryDto> result = new ArrayList<PatternListEntryDto>();
        // 复用 InterfaceLocator.locate 拿到接口列表（含坐标与槽位状态）
        List<PatternDto.InterfaceDto> interfaces = InterfaceLocator.locate(player, networkId);
        if (interfaces == null || interfaces.isEmpty()) return result;

        for (PatternDto.InterfaceDto iface : interfaces) {
            Object target = InterfaceLocator.resolveInterface(iface.x, iface.y, iface.z, iface.dim, iface.partSide);
            if (target == null || !InterfaceLocator.isInterface(target)) continue;
            IInventory patterns = InterfaceLocator.getPatterns(target);
            if (patterns == null) continue;
            int activeSlots = Math.min((iface.capacityUpgrades + 1) * 9, patterns.getSizeInventory());
            for (int slot = 0; slot < activeSlots; slot++) {
                ItemStack stack = patterns.getStackInSlot(slot);
                if (stack == null || stack.getItem() == null) continue;
                NBTTagCompound nbt = stack.getTagCompound();
                if (nbt == null) continue;
                PatternListEntryDto entry = decodePattern(nbt, iface, slot);
                if (entry != null) result.add(entry);
            }
        }
        return result;
    }

    // ---- GET /api/patterns/<id> ----

    private static NanoHTTPD.Response handleDetail(String idPart, WebAuthSession auth) {
        final PatternId pid = parsePatternId(idPart);
        if (pid == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid pattern id format (expected <x>:<y>:<z>:<dim>#<slot>)\"}");
        }

        final PatternListEntryDto[] holder = new PatternListEntryDto[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] notFound = new boolean[1];

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    Object target = InterfaceLocator.resolveInterface(pid.x, pid.y, pid.z, pid.dim, pid.partSide);
                    if (target == null || !InterfaceLocator.isInterface(target)
                        || !InterfaceLocator.belongsToOwnerGrid(target, auth.ownerUuid)) {
                        notFound[0] = true;
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(target);
                    if (!validSlot(target, patterns, pid.slot)) {
                        notFound[0] = true;
                        return;
                    }
                    ItemStack stack = patterns.getStackInSlot(pid.slot);
                    if (stack == null || stack.getItem() == null || stack.getTagCompound() == null) {
                        notFound[0] = true;
                        return;
                    }
                    PatternDto.InterfaceDto iface = InterfaceLocator.buildInterfaceDto(target);
                    holder[0] = decodePattern(stack.getTagCompound(), iface, pid.slot);
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern detail failed", t);
                    notFound[0] = true;
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (notFound[0] || holder[0] == null) {
                    return json(
                        NanoHTTPD.Response.Status.NOT_FOUND,
                        "{\"success\":false,\"pattern\":null,\"message\":\"Pattern not found\"}");
                }
                return json(
                    NanoHTTPD.Response.Status.OK,
                    "{\"success\":true,\"pattern\":" + GSON.toJson(holder[0]) + "}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return json(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "{\"success\":false,\"message\":\"Pattern detail timed out\"}");
    }

    // ---- DELETE /api/patterns/<id> ----

    private static NanoHTTPD.Response handleDelete(String idPart, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Admin permission required to delete patterns\"}");
        }
        final PatternId pid = parsePatternId(idPart);
        if (pid == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid pattern id format\"}");
        }

        final boolean[] ok = new boolean[1];
        final String[] errMsg = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    Object target = InterfaceLocator.resolveInterface(pid.x, pid.y, pid.z, pid.dim, pid.partSide);
                    if (target == null || !InterfaceLocator.isInterface(target)
                        || !InterfaceLocator.belongsToOwnerGrid(target, auth.ownerUuid)) {
                        errMsg[0] = "Interface not found";
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(target);
                    if (!validSlot(target, patterns, pid.slot)) {
                        errMsg[0] = "Cannot access pattern slot";
                        return;
                    }
                    ItemStack existing = patterns.getStackInSlot(pid.slot);
                    if (existing == null || existing.getItem() == null) {
                        errMsg[0] = "Slot " + pid.slot + " is empty";
                        return;
                    }
                    patterns.setInventorySlotContents(pid.slot, null);
                    InterfaceLocator.saveChanges(target);
                    InterfaceLocator.postPatternChangeEvent(target);
                    PatternBrowseService.invalidateAll();
                    ok[0] = true;
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern delete failed", t);
                    errMsg[0] = "Internal error: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (ok[0]) {
                    return json(
                        NanoHTTPD.Response.Status.OK,
                        "{\"success\":true,\"message\":\"Pattern deleted from slot " + pid.slot + "\"}");
                }
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":" + GSON.toJson(errMsg[0] != null ? errMsg[0] : "Delete failed")
                        + "}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return json(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "{\"success\":false,\"message\":\"Pattern delete timed out\"}");
    }

    // ---- PUT /api/patterns/<id> ----

    private static NanoHTTPD.Response handlePut(String idPart, String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) {
            return json(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Admin permission required to edit patterns\"}");
        }
        final PatternId pid = parsePatternId(idPart);
        if (pid == null) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid pattern id format\"}");
        }
        if (body == null || body.isEmpty()) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Missing request body\"}");
        }
        // body: {encodedNbt: "<json>", interfaceX?, interfaceY?, interfaceZ?, interfaceDim?, slotIndex?}
        String encodedNbt;
        int targetX = pid.x, targetY = pid.y, targetZ = pid.z, targetDim = pid.dim, targetSlot = pid.slot;
        String targetSide = pid.partSide;
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            if (!obj.has("encodedNbt") || obj.get("encodedNbt")
                .isJsonNull()) {
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Missing 'encodedNbt'\"}");
            }
            encodedNbt = obj.get("encodedNbt")
                .getAsString();
            if (obj.has("interfaceX") && !obj.get("interfaceX")
                .isJsonNull())
                targetX = obj.get("interfaceX")
                    .getAsInt();
            if (obj.has("interfaceY") && !obj.get("interfaceY")
                .isJsonNull())
                targetY = obj.get("interfaceY")
                    .getAsInt();
            if (obj.has("interfaceZ") && !obj.get("interfaceZ")
                .isJsonNull())
                targetZ = obj.get("interfaceZ")
                    .getAsInt();
            if (obj.has("interfaceDim") && !obj.get("interfaceDim")
                .isJsonNull())
                targetDim = obj.get("interfaceDim")
                    .getAsInt();
            if (obj.has("interfaceSide") && !obj.get("interfaceSide")
                .isJsonNull())
                targetSide = obj.get("interfaceSide")
                    .getAsString();
            if (obj.has("slotIndex") && !obj.get("slotIndex")
                .isJsonNull())
                targetSlot = obj.get("slotIndex")
                    .getAsInt();
        } catch (Exception e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }

        if (targetX != pid.x || targetY != pid.y
            || targetZ != pid.z
            || targetDim != pid.dim
            || targetSlot != pid.slot
            || !sameSide(targetSide, pid.partSide)) {
            return badRequest("PUT edits the original slot; use /api/patterns/move to relocate a physical pattern");
        }

        final String finalEncodedNbt = encodedNbt;
        final int fx = targetX, fy = targetY, fz = targetZ, fdim = targetDim, fslot = targetSlot;
        final String fside = targetSide;
        final boolean[] ok = new boolean[1];
        final String[] errMsg = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    // 1. 解码 NBT
                    NBTTagCompound nbt;
                    try {
                        nbt = com.imgood.textech.webae.pattern.PatternEncoder.decode(finalEncodedNbt);
                    } catch (Exception e) {
                        errMsg[0] = "Failed to decode pattern NBT: " + e.getMessage();
                        return;
                    }
                    // 2. 创建 encoded pattern ItemStack
                    ItemStack patternStack;
                    try {
                        patternStack = AEApi.instance()
                            .definitions()
                            .items()
                            .encodedPattern()
                            .maybeStack(1)
                            .get();
                        if (patternStack == null) {
                            errMsg[0] = "Failed to create encoded pattern ItemStack";
                            return;
                        }
                        patternStack.setTagCompound(nbt);
                    } catch (Exception e) {
                        errMsg[0] = "Failed to create pattern ItemStack: " + e.getMessage();
                        return;
                    }
                    // 3. 定位目标接口
                    Object target = InterfaceLocator.resolveInterface(fx, fy, fz, fdim, fside);
                    if (target == null || !InterfaceLocator.isInterface(target)
                        || !InterfaceLocator.belongsToOwnerGrid(target, auth.ownerUuid)) {
                        errMsg[0] = "Interface not found at (" + fx + "," + fy + "," + fz + " dim " + fdim + ")";
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(target);
                    if (!validSlot(target, patterns, fslot)) {
                        errMsg[0] = "Cannot access pattern slot " + fslot;
                        return;
                    }
                    // 5. 写回槽位（覆盖已有样板）
                    patterns.setInventorySlotContents(fslot, patternStack);
                    InterfaceLocator.saveChanges(target);
                    InterfaceLocator.postPatternChangeEvent(target);
                    PatternBrowseService.invalidateAll();
                    ok[0] = true;
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern put failed", t);
                    errMsg[0] = "Internal error: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (ok[0]) {
                    return json(
                        NanoHTTPD.Response.Status.OK,
                        "{\"success\":true,\"message\":\"Pattern saved to slot " + fslot + "\"}");
                }
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":" + GSON.toJson(errMsg[0] != null ? errMsg[0] : "Save failed")
                        + "}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return json(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "{\"success\":false,\"message\":\"Pattern put timed out\"}");
    }

    // ---- POST /api/patterns/move ----

    private static NanoHTTPD.Response handleMove(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) return adminRequired("move patterns");
        final JsonObject root = parseBodyObject(body);
        if (root == null || !root.has("patternId")) return badRequest("Missing patternId");
        final PatternId source = parsePatternId(
            root.get("patternId")
                .getAsString());
        final TargetAddress target = parseTarget(root);
        final int networkId = getInt(root, "networkId", -1);
        final boolean swap = root.has("swap") && root.get("swap")
            .getAsBoolean();
        if (source == null || target == null || networkId < 0) return badRequest("Invalid move request");

        final boolean[] ok = new boolean[1];
        final String[] error = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                Object sourceTarget = null;
                Object destinationTarget = null;
                IInventory sourceInventory = null;
                IInventory destinationInventory = null;
                ItemStack sourceBefore = null;
                ItemStack destinationBefore = null;
                boolean mutationStarted = false;
                try {
                    sourceTarget = InterfaceLocator
                        .resolveInterface(source.x, source.y, source.z, source.dim, source.partSide);
                    destinationTarget = InterfaceLocator
                        .resolveInterface(target.x, target.y, target.z, target.dim, target.partSide);
                    if (!validNetworkTargets(auth.ownerUuid, networkId, sourceTarget, destinationTarget)) {
                        error[0] = "Source or target interface is not on the selected AE network";
                        return;
                    }
                    sourceInventory = InterfaceLocator.getPatterns(sourceTarget);
                    destinationInventory = InterfaceLocator.getPatterns(destinationTarget);
                    if (!validSlot(sourceTarget, sourceInventory, source.slot)
                        || !validSlot(destinationTarget, destinationInventory, target.slot)) {
                        error[0] = "Source or target slot is out of range";
                        return;
                    }
                    if (sourceInventory == destinationInventory && source.slot == target.slot) {
                        ok[0] = true;
                        return;
                    }
                    ItemStack moving = sourceInventory.getStackInSlot(source.slot);
                    if (moving == null || moving.getItem() == null) {
                        error[0] = "Source slot is empty";
                        return;
                    }
                    ItemStack displaced = destinationInventory.getStackInSlot(target.slot);
                    if (displaced != null && displaced.getItem() != null && !swap) {
                        error[0] = "Target slot is occupied";
                        return;
                    }
                    sourceBefore = moving.copy();
                    destinationBefore = displaced != null ? displaced.copy() : null;
                    mutationStarted = true;
                    destinationInventory.setInventorySlotContents(target.slot, moving);
                    sourceInventory.setInventorySlotContents(source.slot, swap ? displaced : null);
                    InterfaceLocator.saveChanges(sourceTarget);
                    if (destinationTarget != sourceTarget) InterfaceLocator.saveChanges(destinationTarget);
                    InterfaceLocator.postPatternChangeEvent(sourceTarget);
                    if (destinationTarget != sourceTarget) InterfaceLocator.postPatternChangeEvent(destinationTarget);
                    PatternBrowseService.invalidateAll();
                    ok[0] = true;
                } catch (Throwable t) {
                    if (mutationStarted) {
                        rollbackMove(
                            sourceTarget,
                            sourceInventory,
                            source.slot,
                            sourceBefore,
                            destinationTarget,
                            destinationInventory,
                            target.slot,
                            destinationBefore);
                    }
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern move failed", t);
                    error[0] = "Move failed: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        });
        return awaitMutation(latch, ok, error, "Pattern moved", "Pattern move timed out");
    }

    // ---- Web-only physical pattern buffer ----

    private static NanoHTTPD.Response handleBufferTake(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) return adminRequired("buffer patterns");
        final JsonObject root = parseBodyObject(body);
        if (root == null || !root.has("patternId")) return badRequest("Missing patternId");
        final PatternId source = parsePatternId(
            root.get("patternId")
                .getAsString());
        final int networkId = getInt(root, "networkId", -1);
        if (source == null || networkId < 0) return badRequest("Invalid buffer request");

        final boolean[] ok = new boolean[1];
        final String[] error = new String[1];
        final Entry[] added = new Entry[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                Object sourceTarget = null;
                IInventory inventory = null;
                ItemStack sourceBefore = null;
                boolean sourceCleared = false;
                try {
                    sourceTarget = InterfaceLocator
                        .resolveInterface(source.x, source.y, source.z, source.dim, source.partSide);
                    if (!validNetworkTargets(auth.ownerUuid, networkId, sourceTarget)) {
                        error[0] = "Source interface is not on the selected AE network";
                        return;
                    }
                    inventory = InterfaceLocator.getPatterns(sourceTarget);
                    if (!validSlot(sourceTarget, inventory, source.slot)) {
                        error[0] = "Source slot is out of range";
                        return;
                    }
                    ItemStack stack = inventory.getStackInSlot(source.slot);
                    if (stack == null || stack.getItem() == null || stack.getTagCompound() == null) {
                        error[0] = "Source slot is empty";
                        return;
                    }
                    sourceBefore = stack.copy();
                    InterfaceDto iface = InterfaceLocator.buildInterfaceDto(sourceTarget);
                    PatternListEntryDto decoded = decodePattern(stack.getTagCompound(), iface, source.slot);
                    if (decoded == null) {
                        error[0] = "Cannot decode source pattern";
                        return;
                    }
                    added[0] = PatternWebBufferStore.instance()
                        .add(
                            auth.ownerUuid,
                            networkId,
                            decoded.encodedNbt,
                            decoded.sourceInterfaceName,
                            source.slot,
                            decoded.crafting,
                            decoded.outputs);
                    if (added[0] == null) {
                        error[0] = "Web pattern buffer is full or could not be saved";
                        return;
                    }
                    inventory.setInventorySlotContents(source.slot, null);
                    sourceCleared = true;
                    InterfaceLocator.saveChanges(sourceTarget);
                    InterfaceLocator.postPatternChangeEvent(sourceTarget);
                    PatternBrowseService.invalidateAll();
                    ok[0] = true;
                } catch (Throwable t) {
                    boolean restored = !sourceCleared
                        || restoreSlot(sourceTarget, inventory, source.slot, sourceBefore, "buffer take");
                    if (added[0] != null && restored) {
                        PatternWebBufferStore.instance()
                            .remove(auth.ownerUuid, networkId, added[0].id);
                        added[0] = null;
                    }
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern buffer take failed", t);
                    error[0] = "Buffer failed: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        });
        NanoHTTPD.Response response = awaitMutation(
            latch,
            ok,
            error,
            "Pattern moved to Web buffer",
            "Buffer timed out");
        if (ok[0] && added[0] != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"message\":\"Pattern moved to Web buffer\",\"entry\":" + GSON.toJson(added[0])
                    + "}");
        }
        return response;
    }

    private static NanoHTTPD.Response handleBufferPlace(String body, WebAuthSession auth, String adminHeader) {
        if (!WebAuthAdminCheck.isAdmin(auth, adminHeader)) return adminRequired("place buffered patterns");
        final JsonObject root = parseBodyObject(body);
        if (root == null || !root.has("bufferId")) return badRequest("Missing bufferId");
        final String bufferId = root.get("bufferId")
            .getAsString();
        final TargetAddress target = parseTarget(root);
        final int networkId = getInt(root, "networkId", -1);
        if (bufferId.isEmpty() || target == null || networkId < 0) return badRequest("Invalid place request");

        final boolean[] ok = new boolean[1];
        final String[] error = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                Object destinationTarget = null;
                IInventory inventory = null;
                boolean placed = false;
                boolean bufferRemoved = false;
                try {
                    Entry entry = PatternWebBufferStore.instance()
                        .get(auth.ownerUuid, networkId, bufferId);
                    if (entry == null) {
                        error[0] = "Buffered pattern not found";
                        return;
                    }
                    destinationTarget = InterfaceLocator
                        .resolveInterface(target.x, target.y, target.z, target.dim, target.partSide);
                    if (!validNetworkTargets(auth.ownerUuid, networkId, destinationTarget)) {
                        error[0] = "Target interface is not on the selected AE network";
                        return;
                    }
                    inventory = InterfaceLocator.getPatterns(destinationTarget);
                    if (!validSlot(destinationTarget, inventory, target.slot)) {
                        error[0] = "Target slot is out of range";
                        return;
                    }
                    if (inventory.getStackInSlot(target.slot) != null) {
                        error[0] = "Target slot is occupied";
                        return;
                    }
                    ItemStack patternStack = createEncodedPatternStack(entry.encodedNbt);
                    if (patternStack == null) {
                        error[0] = "Cannot decode buffered pattern";
                        return;
                    }
                    inventory.setInventorySlotContents(target.slot, patternStack);
                    placed = true;
                    InterfaceLocator.saveChanges(destinationTarget);
                    Entry removed = PatternWebBufferStore.instance()
                        .remove(auth.ownerUuid, networkId, bufferId);
                    if (removed == null) {
                        inventory.setInventorySlotContents(target.slot, null);
                        InterfaceLocator.saveChanges(destinationTarget);
                        error[0] = "Could not persist Web buffer removal";
                        return;
                    }
                    bufferRemoved = true;
                    InterfaceLocator.postPatternChangeEvent(destinationTarget);
                    PatternBrowseService.invalidateAll();
                    ok[0] = true;
                } catch (Throwable t) {
                    if (placed && !bufferRemoved) {
                        restoreSlot(destinationTarget, inventory, target.slot, null, "buffer place");
                    }
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern buffer place failed", t);
                    error[0] = "Place failed: " + t.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        });
        return awaitMutation(latch, ok, error, "Buffered pattern placed", "Place timed out");
    }

    // ---- helpers ----

    private static NanoHTTPD.Response awaitMutation(CountDownLatch latch, boolean[] ok, String[] error,
        String successMessage, String timeoutMessage) {
        try {
            if (latch.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (ok[0]) {
                    return json(
                        NanoHTTPD.Response.Status.OK,
                        "{\"success\":true,\"message\":" + GSON.toJson(successMessage) + "}");
                }
                return json(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "{\"success\":false,\"message\":"
                        + GSON.toJson(error[0] != null ? error[0] : "Pattern mutation failed")
                        + "}");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return json(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "{\"success\":false,\"message\":" + GSON.toJson(timeoutMessage) + "}");
    }

    private static NanoHTTPD.Response adminRequired(String action) {
        return json(
            NanoHTTPD.Response.Status.FORBIDDEN,
            "{\"success\":false,\"code\":\"admin_required\",\"message\":\"Admin permission required to " + action
                + "\"}");
    }

    private static NanoHTTPD.Response badRequest(String message) {
        return json(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "{\"success\":false,\"message\":" + GSON.toJson(message) + "}");
    }

    private static JsonObject parseBodyObject(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return new JsonParser().parse(body)
                .getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static int getInt(JsonObject root, String name, int fallback) {
        try {
            return root != null && root.has(name)
                && !root.get(name)
                    .isJsonNull() ? root.get(name)
                        .getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static TargetAddress parseTarget(JsonObject root) {
        if (root == null) return null;
        int x = getInt(root, "interfaceX", Integer.MIN_VALUE);
        int y = getInt(root, "interfaceY", Integer.MIN_VALUE);
        int z = getInt(root, "interfaceZ", Integer.MIN_VALUE);
        int dim = getInt(root, "interfaceDim", Integer.MIN_VALUE);
        int slot = getInt(root, "slotIndex", -1);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE
            || z == Integer.MIN_VALUE
            || dim == Integer.MIN_VALUE
            || slot < 0) return null;
        String side = "";
        if (root.has("interfaceSide") && !root.get("interfaceSide")
            .isJsonNull())
            side = root.get("interfaceSide")
                .getAsString();
        return new TargetAddress(x, y, z, dim, side, slot);
    }

    private static int parseNetworkId(String raw) {
        try {
            return raw != null ? Integer.parseInt(raw) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean sameSide(String a, String b) {
        String left = a != null ? a : "";
        String right = b != null ? b : "";
        return left.equalsIgnoreCase(right);
    }

    private static boolean validSlot(Object target, IInventory inventory, int slot) {
        if (inventory == null || slot < 0) return false;
        int activeSlots = (InterfaceLocator.getInstalledUpgrades(target, Upgrades.PATTERN_CAPACITY) + 1) * 9;
        return slot < Math.min(activeSlots, inventory.getSizeInventory());
    }

    private static boolean validNetworkTargets(String ownerUuid, int networkId, Object... targets) {
        if (targets == null || targets.length == 0) return false;
        for (Object target : targets) {
            if (target == null || !InterfaceLocator.isInterface(target)
                || !InterfaceLocator.belongsToGrid(target, ownerUuid, networkId)) return false;
        }
        return true;
    }

    private static ItemStack createEncodedPatternStack(String encodedNbt) {
        try {
            ItemStack patternStack = AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1)
                .get();
            if (patternStack == null) return null;
            patternStack.setTagCompound(com.imgood.textech.webae.pattern.PatternEncoder.decode(encodedNbt));
            return patternStack;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void rollbackMove(Object sourceTarget, IInventory sourceInventory, int sourceSlot,
        ItemStack sourceBefore, Object destinationTarget, IInventory destinationInventory, int destinationSlot,
        ItemStack destinationBefore) {
        try {
            if (sourceInventory != null) sourceInventory.setInventorySlotContents(sourceSlot, copyOrNull(sourceBefore));
            if (destinationInventory != null) {
                destinationInventory.setInventorySlotContents(destinationSlot, copyOrNull(destinationBefore));
            }
            if (sourceTarget != null) InterfaceLocator.saveChanges(sourceTarget);
            if (destinationTarget != null && destinationTarget != sourceTarget) {
                InterfaceLocator.saveChanges(destinationTarget);
            }
            if (sourceTarget != null) InterfaceLocator.postPatternChangeEvent(sourceTarget);
            if (destinationTarget != null && destinationTarget != sourceTarget) {
                InterfaceLocator.postPatternChangeEvent(destinationTarget);
            }
        } catch (Throwable rollbackError) {
            AdvanceDataMonitor.LOG.error("[WebAE] Pattern move rollback failed", rollbackError);
        }
    }

    private static boolean restoreSlot(Object target, IInventory inventory, int slot, ItemStack stack, String action) {
        try {
            if (target == null || inventory == null) return false;
            inventory.setInventorySlotContents(slot, copyOrNull(stack));
            InterfaceLocator.saveChanges(target);
            InterfaceLocator.postPatternChangeEvent(target);
            return true;
        } catch (Throwable rollbackError) {
            AdvanceDataMonitor.LOG.error("[WebAE] Pattern {} rollback failed", action, rollbackError);
            return false;
        }
    }

    private static ItemStack copyOrNull(ItemStack stack) {
        return stack != null ? stack.copy() : null;
    }

    /** 解码样板 NBTTagCompound 为 PatternListEntryDto（含 inputs/outputs/flags/author/encodedNbt）。 */
    private static PatternListEntryDto decodePattern(NBTTagCompound nbt, PatternDto.InterfaceDto iface, int slot) {
        if (nbt == null) return null;
        PatternListEntryDto entry = new PatternListEntryDto();
        entry.sourceInterface = iface.interfaceId != null ? iface.interfaceId
            : InterfaceLocator.address(iface.x, iface.y, iface.z, iface.dim, iface.partSide);
        entry.sourceInterfaceName = iface.name != null ? iface.name : entry.sourceInterface;
        entry.slotIndex = slot;
        entry.patternId = entry.sourceInterface + "#" + slot;
        entry.crafting = nbt.getByte("crafting") != 0;
        entry.substitute = nbt.getByte("substitute") != 0;
        entry.beSubstitute = nbt.hasKey("beSubstitute") && nbt.getByte("beSubstitute") != 0;
        entry.author = nbt.getString("author");
        if (entry.author == null) entry.author = "";

        // inputs (NBTTagList "in")
        NBTTagList inList = nbt.getTagList("in", 10);
        if (inList != null) {
            // 保留 27 槽位（9×3），null 表示空槽
            for (int i = 0; i < 27; i++) {
                if (i < inList.tagCount()) {
                    NBTTagCompound stackTag = inList.getCompoundTagAt(i);
                    PatternDto.PatternItemEntry pe = stackToEntry(stackTag);
                    entry.inputs.add(pe);
                    if (pe != null && pe.programmableCircuit) entry.programmableHatches = true;
                } else {
                    entry.inputs.add(null);
                }
            }
        } else {
            for (int i = 0; i < 27; i++) entry.inputs.add(null);
        }

        // outputs (NBTTagList "out")
        NBTTagList outList = nbt.getTagList("out", 10);
        if (outList != null) {
            for (int i = 0; i < outList.tagCount(); i++) {
                NBTTagCompound stackTag = outList.getCompoundTagAt(i);
                PatternDto.PatternItemEntry pe = stackToEntry(stackTag);
                if (pe != null) entry.outputs.add(pe);
            }
        }

        // encodedNbt: NBTTagCompound → JSON 字符串（用于前端回写/导出）
        try {
            JsonObject json = NBTJsonParser.parseNBTToJson(nbt);
            entry.encodedNbt = json.toString();
        } catch (Exception e) {
            entry.encodedNbt = "";
        }
        return entry;
    }

    /** NBTTagCompound（ItemStack NBT）→ PatternItemEntry。 */
    private static PatternDto.PatternItemEntry stackToEntry(NBTTagCompound stackTag) {
        if (stackTag == null) return null;
        try {
            ItemStack storedStack = ItemStack.loadItemStackFromNBT(stackTag);
            if (storedStack == null || storedStack.getItem() == null) return null;
            boolean programmableCircuit = ProgrammableHatchesCompat.isProgrammingCircuit(storedStack);
            ItemStack stack = programmableCircuit ? ProgrammableHatchesCompat.unwrap(storedStack) : storedStack;
            if (stack == null || stack.getItem() == null) return null;
            String registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (registryName == null || registryName.isEmpty()) registryName = "unknown";
            int meta = stack.getItemDamage();
            if (meta == Short.MAX_VALUE) meta = 0;
            String displayName;
            try {
                displayName = stack.getDisplayName();
            } catch (Throwable t) {
                displayName = registryName;
            }
            if (displayName == null || displayName.isEmpty()) displayName = registryName;
            // AE2FC 流体样板用 fluid_drop 物品承载，通过 NBT 中的 FluidName 标识流体
            boolean isFluid = registryName.startsWith("ae2fc:") && registryName.contains("fluid");
            PatternDto.PatternItemEntry entry = new PatternDto.PatternItemEntry(
                registryName,
                displayName,
                meta,
                Math.max(1, programmableCircuit ? storedStack.stackSize : stack.stackSize),
                isFluid);
            entry.nonConsumable = programmableCircuit;
            entry.programmableCircuit = programmableCircuit;
            if (stack.getTagCompound() != null) {
                try {
                    entry.nbt = NBTJsonParser.parseNBTToJson(stack.getTagCompound())
                        .toString();
                } catch (Exception ignored) {}
            }
            return entry;
        } catch (Throwable t) {
            return null;
        }
    }

    /** patternId 解析：{@code <x>:<y>:<z>:<dim>#<slot>}。 */
    private static PatternId parsePatternId(String id) {
        if (id == null || id.isEmpty()) return null;
        int hashIdx = id.indexOf('#');
        if (hashIdx < 0) return null;
        String address = id.substring(0, hashIdx);
        String slotStr = id.substring(hashIdx + 1);
        String partSide = "";
        int atIdx = address.indexOf('@');
        if (atIdx >= 0) {
            partSide = address.substring(atIdx + 1);
            address = address.substring(0, atIdx);
        }
        String coords = address;
        String[] parts = coords.split(":");
        if (parts.length != 4) return null;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            int dim = Integer.parseInt(parts[3]);
            int slot = Integer.parseInt(slotStr);
            return new PatternId(x, y, z, dim, partSide, slot);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class PatternId {

        final int x, y, z, dim, slot;
        final String partSide;

        PatternId(int x, int y, int z, int dim, String partSide, int slot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.partSide = partSide != null ? partSide : "";
            this.slot = slot;
        }
    }

    private static final class TargetAddress {

        final int x, y, z, dim, slot;
        final String partSide;

        TargetAddress(int x, int y, int z, int dim, String partSide, int slot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.partSide = partSide != null ? partSide : "";
            this.slot = slot;
        }
    }
}
