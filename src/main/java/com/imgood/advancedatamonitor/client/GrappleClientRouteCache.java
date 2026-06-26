package com.imgood.advancedatamonitor.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.imgood.advancedatamonitor.items.GrappleRouteEntry;
import com.imgood.advancedatamonitor.network.packet.PacketGrapplePathSync;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class GrappleClientRouteCache {

    /** 预览自动取消时长（游戏 tick，30 秒 = 600 tick）。 */
    public static final long PREVIEW_TIMEOUT_TICKS = 600L;

    private static final List<GrappleRouteEntry> routes = new ArrayList<GrappleRouteEntry>();
    private static final List<BlockPos> recordingBuffer = new ArrayList<BlockPos>();

    /** 多路线预览：routeId → 预览状态（节点 + 过期 tick）。 */
    private static final Map<String, PreviewState> activePreviews = new HashMap<String, PreviewState>();

    private GrappleClientRouteCache() {}

    public static void apply(PacketGrapplePathSync packet) {
        if (packet == null) {
            return;
        }
        if (packet.kind == PacketGrapplePathSync.KIND_ROUTES) {
            routes.clear();
            routes.addAll(packet.routes);
            // 路线列表刷新后，清理已不存在路线的预览
            pruneStalePreviews();
        }
        if (packet.kind == PacketGrapplePathSync.KIND_BUFFER) {
            recordingBuffer.clear();
            recordingBuffer.addAll(packet.buffer);
        }
    }

    public static List<GrappleRouteEntry> getRoutes() {
        return routes;
    }

    public static List<BlockPos> getRecordingBuffer() {
        return recordingBuffer;
    }

    public static int getRecordingBufferSize() {
        return recordingBuffer.size();
    }

    // ===== 多路线预览 API =====

    /** 开启/刷新某条路线的预览（30 秒后自动过期）。 */
    public static void addPreview(String routeId, List<BlockPos> nodes, long currentWorldTick) {
        if (routeId == null || routeId.isEmpty() || nodes == null || nodes.size() < 2) {
            return;
        }
        activePreviews.put(routeId, new PreviewState(nodes, currentWorldTick + PREVIEW_TIMEOUT_TICKS));
    }

    /** 取消某条路线的预览。 */
    public static void removePreview(String routeId) {
        if (routeId == null) {
            return;
        }
        activePreviews.remove(routeId);
    }

    /** 切换某条路线的预览状态：未预览→开启，已预览→取消。返回切换后是否处于预览中。 */
    public static boolean togglePreview(String routeId, List<BlockPos> nodes, long currentWorldTick) {
        if (routeId == null || routeId.isEmpty()) {
            return false;
        }
        if (activePreviews.containsKey(routeId)) {
            activePreviews.remove(routeId);
            return false;
        }
        addPreview(routeId, nodes, currentWorldTick);
        return true;
    }

    public static boolean isPreviewing(String routeId) {
        return routeId != null && activePreviews.containsKey(routeId);
    }

    /** 获取所有当前活跃预览的节点列表（用于渲染）。 */
    public static Collection<PreviewState> getActivePreviews() {
        return activePreviews.values();
    }

    public static boolean hasPreview() {
        return !activePreviews.isEmpty();
    }

    /** 清理已过期的预览。每 tick 调用一次。 */
    public static void cleanupExpired(long currentWorldTick) {
        if (activePreviews.isEmpty()) {
            return;
        }
        activePreviews.values()
            .removeIf(state -> state.expireTick <= currentWorldTick);
    }

    /** 清除所有预览（玩家出发/挂接时调用）。 */
    public static void clearPreview() {
        activePreviews.clear();
    }

    // ===== 旧单条预览 API（向后兼容，基于任意一条活跃预览） =====

    /**
     * @deprecated 使用 {@link #addPreview} 支持多路线。此方法仅设置单条预览（清除其他）。
     */
    @Deprecated
    public static void setPreviewNodes(List<BlockPos> nodes) {
        activePreviews.clear();
        if (nodes != null && nodes.size() >= 2) {
            // 旧 API 无 routeId，用临时 key；不参与 30 秒自动过期（无 tick 信息），需手动 clear
            activePreviews.put("__legacy__", new PreviewState(nodes, Long.MAX_VALUE));
        }
    }

    /**
     * @deprecated 使用 {@link #getActivePreviews}。返回任意一条活跃预览的节点。
     */
    @Deprecated
    public static List<BlockPos> getPreviewNodes() {
        if (activePreviews.isEmpty()) {
            return null;
        }
        return activePreviews.values()
            .iterator()
            .next().nodes;
    }

    // ===== 内部 =====

    /** 清理路线列表中已不存在的 routeId 对应的预览。 */
    private static void pruneStalePreviews() {
        if (activePreviews.isEmpty()) {
            return;
        }
        activePreviews.keySet()
            .removeIf(id -> !isRouteIdPresent(id));
    }

    private static boolean isRouteIdPresent(String routeId) {
        for (GrappleRouteEntry route : routes) {
            if (routeId.equals(route.routeId)) {
                return true;
            }
        }
        return false;
    }

    /** 预览状态：节点列表 + 过期 tick。 */
    public static final class PreviewState {

        public final List<BlockPos> nodes;
        public final long expireTick;

        PreviewState(List<BlockPos> nodes, long expireTick) {
            this.nodes = new ArrayList<BlockPos>(nodes);
            this.expireTick = expireTick;
        }
    }
}
