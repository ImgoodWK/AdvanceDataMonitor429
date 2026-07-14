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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.utils.NBTJsonParser;
import com.imgood.textech.webae.auth.WebAuthAdminCheck;
import com.imgood.textech.webae.auth.WebAuthOpCheck;
import com.imgood.textech.webae.auth.WebAuthSession;
import com.imgood.textech.webae.cache.SnapshotCache;
import com.imgood.textech.webae.cache.SnapshotScheduler;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PatternDto;
import com.imgood.textech.webae.dto.PatternListEntryDto;
import com.imgood.textech.webae.pattern.InterfaceLocator;
import com.imgood.textech.webae.pattern.PatternBrowseService;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
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
                "{\"success\":true,\"patterns\":" + GSON.toJson(fresh)
                    + ",\"count\":"
                    + fresh.size()
                    + ",\"cached\":true,\"timestamp\":"
                    + ts
                    + "}");
        }
        @SuppressWarnings("unchecked")
        List<PatternListEntryDto> stale = SnapshotCache.instance()
            .getStale(playerUuid, networkId, SnapshotScheduler.TYPE_PATTERNS_RICH);
        if (stale != null) {
            return json(
                NanoHTTPD.Response.Status.OK,
                "{\"success\":true,\"patterns\":" + GSON.toJson(stale)
                    + ",\"count\":"
                    + stale.size()
                    + ",\"cached\":false,\"timestamp\":"
                    + ts
                    + "}");
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
            TileEntity te = findTileEntityAt(iface.x, iface.y, iface.z, iface.dim);
            if (te == null || !InterfaceLocator.isInterface(te)) continue;
            IInventory patterns = InterfaceLocator.getPatterns(te);
            if (patterns == null) continue;
            int activeSlots = (iface.capacityUpgrades + 1) * 9;
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
                    EntityPlayerMP player = WebAuthOpCheck.findPlayer(auth.actorUuid);
                    if (player == null) {
                        notFound[0] = true;
                        return;
                    }
                    TileEntity te = findTileEntityAt(pid.x, pid.y, pid.z, pid.dim);
                    if (te == null || !InterfaceLocator.isInterface(te)) {
                        notFound[0] = true;
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(te);
                    if (patterns == null) {
                        notFound[0] = true;
                        return;
                    }
                    ItemStack stack = patterns.getStackInSlot(pid.slot);
                    if (stack == null || stack.getItem() == null || stack.getTagCompound() == null) {
                        notFound[0] = true;
                        return;
                    }
                    PatternDto.InterfaceDto iface = InterfaceLocator.buildInterfaceDto(te);
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
                    TileEntity te = findTileEntityAt(pid.x, pid.y, pid.z, pid.dim);
                    if (te == null || !InterfaceLocator.isInterface(te)) {
                        errMsg[0] = "Interface not found";
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(te);
                    if (patterns == null) {
                        errMsg[0] = "Cannot access pattern inventory";
                        return;
                    }
                    ItemStack existing = patterns.getStackInSlot(pid.slot);
                    if (existing == null || existing.getItem() == null) {
                        errMsg[0] = "Slot " + pid.slot + " is empty";
                        return;
                    }
                    patterns.setInventorySlotContents(pid.slot, null);
                    InterfaceLocator.saveChanges(te);
                    postPatternChangeEvent(te);
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

    private static NanoHTTPD.Response handlePut(String idPart, String body, WebAuthSession auth,
        String adminHeader) {
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
            if (obj.has("slotIndex") && !obj.get("slotIndex")
                .isJsonNull())
                targetSlot = obj.get("slotIndex")
                    .getAsInt();
        } catch (Exception e) {
            return json(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "{\"success\":false,\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
        }

        final String finalEncodedNbt = encodedNbt;
        final int fx = targetX, fy = targetY, fz = targetZ, fdim = targetDim, fslot = targetSlot;
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
                    TileEntity te = findTileEntityAt(fx, fy, fz, fdim);
                    if (te == null || !InterfaceLocator.isInterface(te)) {
                        errMsg[0] = "Interface not found at (" + fx + "," + fy + "," + fz + " dim " + fdim + ")";
                        return;
                    }
                    // 4. 槽位校验
                    int capacityUpgrades = InterfaceLocator.getInstalledUpgrades(te, Upgrades.PATTERN_CAPACITY);
                    int activeSlots = (capacityUpgrades + 1) * 9;
                    if (fslot < 0 || fslot >= activeSlots) {
                        errMsg[0] = "Slot " + fslot + " out of range (0-" + (activeSlots - 1) + ")";
                        return;
                    }
                    IInventory patterns = InterfaceLocator.getPatterns(te);
                    if (patterns == null) {
                        errMsg[0] = "Cannot access pattern inventory";
                        return;
                    }
                    // 5. 写回槽位（覆盖已有样板）
                    patterns.setInventorySlotContents(fslot, patternStack);
                    InterfaceLocator.saveChanges(te);
                    postPatternChangeEvent(te);
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

    // ---- helpers ----

    /** 解码样板 NBTTagCompound 为 PatternListEntryDto（含 inputs/outputs/flags/author/encodedNbt）。 */
    private static PatternListEntryDto decodePattern(NBTTagCompound nbt, PatternDto.InterfaceDto iface, int slot) {
        if (nbt == null) return null;
        PatternListEntryDto entry = new PatternListEntryDto();
        entry.sourceInterface = iface.x + ":" + iface.y + ":" + iface.z + ":" + iface.dim;
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
            ItemStack stack = ItemStack.loadItemStackFromNBT(stackTag);
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
            return new PatternDto.PatternItemEntry(registryName, displayName, meta, stack.stackSize, isFluid);
        } catch (Throwable t) {
            return null;
        }
    }

    /** patternId 解析：{@code <x>:<y>:<z>:<dim>#<slot>}。 */
    private static PatternId parsePatternId(String id) {
        if (id == null || id.isEmpty()) return null;
        int hashIdx = id.indexOf('#');
        if (hashIdx < 0) return null;
        String coords = id.substring(0, hashIdx);
        String slotStr = id.substring(hashIdx + 1);
        String[] parts = coords.split(":");
        if (parts.length != 4) return null;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            int dim = Integer.parseInt(parts[3]);
            int slot = Integer.parseInt(slotStr);
            return new PatternId(x, y, z, dim, slot);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TileEntity findTileEntityAt(int x, int y, int z, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) return null;
        if (!world.blockExists(x, y, z)) return null;
        return world.getTileEntity(x, y, z);
    }

    /** 触发 AE 网络样板变更事件，让合成 CPU 重新加载样板。 */
    private static void postPatternChangeEvent(TileEntity te) {
        try {
            if (te instanceof IGridHost) {
                IGridNode node = ((IGridHost) te).getGridNode(ForgeDirection.UNKNOWN);
                if (node != null) {
                    IGrid grid = node.getGrid();
                    if (grid != null && te instanceof ICraftingProvider) {
                        grid.postEvent(new MENetworkCraftingPatternChange((ICraftingProvider) te, node));
                    }
                }
            }
        } catch (Throwable t) {
            AdvanceDataMonitor.LOG.warn("[WebAE] Failed to post pattern change event: {}", t.getMessage());
        }
    }

    private static NanoHTTPD.Response json(NanoHTTPD.Response.Status status, String body) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", body);
    }

    private static final class PatternId {

        final int x, y, z, dim, slot;

        PatternId(int x, int y, int z, int dim, int slot) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.slot = slot;
        }
    }
}
