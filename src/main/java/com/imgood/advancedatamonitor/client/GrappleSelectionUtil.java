package com.imgood.advancedatamonitor.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.handler.GrappleAnchorPositions;
import com.imgood.advancedatamonitor.loader.LoaderBlock;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class GrappleSelectionUtil {

    private GrappleSelectionUtil() {}

    public static List<BlockPos> buildCandidateNodes(EntityPlayer player, boolean attached) {
        if (attached) {
            List<BlockPos> result = new ArrayList<BlockPos>();
            for (BlockPos pos : GrappleClientCache.getNearbyNodes()) {
                appendUniqueCandidate(result, pos);
            }
            appendUniqueCandidate(
                result,
                new BlockPos(
                    GrappleClientCache.getTravelTargetX(),
                    GrappleClientCache.getTravelTargetY(),
                    GrappleClientCache.getTravelTargetZ()));
            BlockPos selected = GrappleClientCache.getSelectedTarget();
            appendUniqueCandidate(result, selected);
            for (BlockPos queued : GrappleClientCache.getTravelQueue()) {
                appendUniqueCandidate(result, queued);
            }
            return result;
        }
        return findNodesInRangeThrottled(player, Config.grappleHintRange);
    }

    /**
     * Nodes to draw as world HUD icons. While attached, all reachable nodes are shown; before attach,
     * only the nearest node within hint range is shown.
     */
    public static List<BlockPos> buildIconNodes(EntityPlayer player, boolean attached) {
        if (attached) {
            return buildCandidateNodes(player, true);
        }
        List<BlockPos> result = new ArrayList<BlockPos>();
        BlockPos nearest = findNearestNodeInRange(player, Config.grappleHintRange);
        if (nearest != null) {
            result.add(nearest);
        }
        return result;
    }

    public static BlockPos findNearestNodeInRange(EntityPlayer player, double maxRange) {
        if (player == null) {
            return null;
        }
        BlockPos nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (BlockPos pos : findNodesInRange(player, maxRange)) {
            double distSq = player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = pos;
            }
        }
        return nearest;
    }

    private static void appendUniqueCandidate(List<BlockPos> result, BlockPos pos) {
        if (pos == null) {
            return;
        }
        for (BlockPos existing : result) {
            if (existing.equals(pos)) {
                return;
            }
        }
        result.add(pos);
    }

    /** Icon billboard anchor (client HUD). */
    public static double[] getNodeRenderPosition(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        if (world.getBlock(pos.getX(), pos.getY(), pos.getZ()) == LoaderBlock.grappleAnchor) {
            return GrappleAnchorPositions.resolveNodeRenderPosition(world, pos.getX(), pos.getY(), pos.getZ());
        }
        return GrappleAnchorPositions.resolveNodeIconBlockCenter(pos.getX(), pos.getY(), pos.getZ());
    }

    /** World billboard anchor for HUD icon; accounts for icon half-size in world Y. */
    public static double[] getNodeIconBillboardPosition(World world, BlockPos pos, float iconHalfSize) {
        if (pos == null) {
            return null;
        }
        return GrappleAnchorPositions.resolveNodeIconBillboardAnchor(pos.getX(), pos.getY(), pos.getZ(), iconHalfSize);
    }

    /** Preview line start: interpolated player lower-body position (attached or mid-slide). */
    public static double[] getLinePreviewStartPosition(EntityPlayer player, float partialTicks) {
        if (player == null) {
            return null;
        }
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        return new double[] { x, y - 0.1D, z };
    }

    /** Entity whose position represents the player's feet for preview lines. */
    public static net.minecraft.entity.Entity resolveFeetEntity(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        if (player.ridingEntity != null) {
            return player.ridingEntity;
        }
        return player;
    }

    public static double[] interpolateEntityPosition(net.minecraft.entity.Entity entity, float partialTicks) {
        if (entity == null) {
            return null;
        }
        return new double[] { entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks,
            entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks,
            entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks };
    }

    /** Interpolated feet position; follows the slide entity while traveling. */
    public static double[] getPlayerFeetPosition(EntityPlayer player, float partialTicks) {
        return interpolateEntityPosition(resolveFeetEntity(player), partialTicks);
    }

    /** Block center of the cell containing the player's lower body (feet level). */
    public static double[] getPlayerLowerBodyBlockPosition(EntityPlayer player, float partialTicks) {
        if (player == null) {
            return null;
        }
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        return new double[] { MathHelper.floor_double(x) + 0.5D, MathHelper.floor_double(y) + 0.5D,
            MathHelper.floor_double(z) + 0.5D };
    }

    /** Travel path preview line anchor (matches server slide endpoints). */
    public static double[] getNodeTravelPathPosition(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        if (world.getBlock(pos.getX(), pos.getY(), pos.getZ()) == LoaderBlock.grappleAnchor) {
            return GrappleAnchorPositions.resolveHangPosition(world, pos.getX(), pos.getY(), pos.getZ());
        }
        return new double[] { pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D };
    }

    /** Line vertex anchor at the node icon (same position as HUD billboard). */
    public static double[] getNodeLinePosition(World world, BlockPos pos) {
        return getNodeRenderPosition(world, pos);
    }

    /** Queued hop preview line anchor (travel path raised by one block). */
    public static double[] getNodeQueuePathPosition(World world, BlockPos pos) {
        double[] base = getNodeTravelPathPosition(world, pos);
        if (base == null) {
            return null;
        }
        return new double[] { base[0], base[1] + 1.0D, base[2] };
    }

    public static double[] getNodeAimPosition(World world, BlockPos pos) {
        return getNodeRenderPosition(world, pos);
    }

    public static List<BlockPos> findNodesNearAnchor(EntityPlayer player, int anchorX, int anchorY, int anchorZ) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        if (player == null || player.worldObj == null) {
            return result;
        }
        World world = player.worldObj;
        double maxRange = Config.grappleScanChunkRadius * 16.0D;
        double maxSq = maxRange * maxRange;
        int range = (int) Math.ceil(maxRange);
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    int x = anchorX + dx;
                    int y = anchorY + dy;
                    int z = anchorZ + dz;
                    if (isSameBlock(x, y, z, anchorX, anchorY, anchorZ)) {
                        continue;
                    }
                    if (world.getBlock(x, y, z) == LoaderBlock.grappleAnchor) {
                        double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                        if (distSq <= maxSq) {
                            result.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isSameBlock(BlockPos pos, int x, int y, int z) {
        return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    private static boolean isSameBlock(int x1, int y1, int z1, int x2, int y2, int z2) {
        return x1 == x2 && y1 == y2 && z1 == z2;
    }

    /**
     * Refresh magnetic selection. Travel mode uses a wide angular cone; attach mode uses screen snap.
     */
    public static BlockPos refreshSelection(EntityPlayer player, List<BlockPos> candidates, boolean travelMode,
        float partialTicks) {
        if (player == null || candidates == null || candidates.isEmpty()) {
            GrappleClientCache.setSelectedTarget(null);
            return null;
        }
        BlockPos picked = travelMode ? pickTravelTarget(player, candidates, partialTicks)
            : pickAttachTarget(player, candidates, partialTicks);
        GrappleClientCache.setSelectedTarget(picked);
        return picked;
    }

    /** Wide-angle look snap for grapple travel after attaching. */
    public static BlockPos pickTravelTarget(EntityPlayer player, List<BlockPos> candidates, float partialTicks) {
        BlockPos angular = pickByLookAlignment(player, candidates, partialTicks, Config.grappleTravelSnapDegrees);
        if (angular != null) {
            return angular;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int expandedSnap = Config.grappleSnapRadiusPx * 2;
        return pickByScreenDistance(
            player,
            candidates,
            partialTicks,
            sr.getScaledWidth(),
            sr.getScaledHeight(),
            expandedSnap);
    }

    /** Tighter snap for initial attach while near a node. */
    public static BlockPos pickAttachTarget(EntityPlayer player, List<BlockPos> candidates, float partialTicks) {
        BlockPos angular = pickByLookAlignment(player, candidates, partialTicks, Config.grappleAttachSnapDegrees);
        if (angular != null) {
            return angular;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return pickByScreenDistance(
            player,
            candidates,
            partialTicks,
            sr.getScaledWidth(),
            sr.getScaledHeight(),
            Config.grappleSnapRadiusPx);
    }

    /**
     * Pick the node most aligned with the view direction within {@code maxAngleDegrees}.
     */
    public static BlockPos pickByLookAlignment(EntityPlayer player, List<BlockPos> candidates, float partialTicks,
        float maxAngleDegrees) {
        if (player == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        float[] look = getLookVector(player);
        float minDot = (float) Math.cos(maxAngleDegrees * Math.PI / 180.0D);

        BlockPos best = null;
        float bestDot = minDot;

        for (BlockPos pos : candidates) {
            float[] toNode = getDirectionToAimPoint(player, pos, partialTicks);
            if (toNode == null) {
                continue;
            }
            float dot = look[0] * toNode[0] + look[1] * toNode[1] + look[2] * toNode[2];
            if (dot >= bestDot) {
                bestDot = dot;
                best = pos;
            }
        }
        return best;
    }

    public static BlockPos pickByScreenDistance(EntityPlayer player, List<BlockPos> candidates, float partialTicks,
        int screenW, int screenH, float snapRadiusPx) {
        if (player == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        float cx = screenW * 0.5F;
        float cy = screenH * 0.5F;
        float snapRadiusSq = snapRadiusPx * snapRadiusPx;

        BlockPos best = null;
        float bestDistSq = snapRadiusSq;

        float[] look = getLookVector(player);

        for (BlockPos pos : candidates) {
            float[] toNode = getDirectionToAimPoint(player, pos, partialTicks);
            if (toNode == null) {
                continue;
            }
            float dot = look[0] * toNode[0] + look[1] * toNode[1] + look[2] * toNode[2];
            if (dot <= 0.05F) {
                continue;
            }

            double[] aim = getNodeRenderPosition(player.worldObj, pos);
            float[] screen = projectToScreen(player, aim[0], aim[1], aim[2], partialTicks, screenW, screenH);
            float sdx = screen[0] - cx;
            float sdy = screen[1] - cy;
            float distSq = sdx * sdx + sdy * sdy;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = pos;
            }
        }
        return best;
    }

    private static float[] getLookVector(EntityPlayer player) {
        float yaw = player.rotationYaw * 0.017453292F;
        float pitch = player.rotationPitch * 0.017453292F;
        float planar = MathHelper.cos(-yaw - (float) Math.PI);
        float planarSin = MathHelper.sin(-yaw - (float) Math.PI);
        float vertical = -MathHelper.cos(-pitch);
        float verticalSin = MathHelper.sin(-pitch);
        return new float[] { planarSin * vertical, verticalSin, planar * vertical };
    }

    private static float[] getRightVector(EntityPlayer player) {
        float yaw = player.rotationYaw * 0.017453292F;
        return new float[] { MathHelper.cos(-yaw - (float) Math.PI), 0.0F, MathHelper.sin(-yaw - (float) Math.PI) };
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static float[] getDirectionToAimPoint(EntityPlayer player, BlockPos pos, float partialTicks) {
        double[] aim = getNodeAimPosition(player.worldObj, pos);
        if (aim == null) {
            return null;
        }
        double ex = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double ey = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight();
        double ez = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double dx = aim[0] - ex;
        double dy = aim[1] - ey;
        double dz = aim[2] - ez;
        double len = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        if (len < 0.01D) {
            return null;
        }
        return new float[] { (float) (dx / len), (float) (dy / len), (float) (dz / len) };
    }

    public static float[] projectToScreen(EntityPlayer player, double wx, double wy, double wz, float partialTicks,
        int screenW, int screenH) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double dx = wx - px;
        double dy = wy - py;
        double dz = wz - pz;

        float[] look = getLookVector(player);
        float[] right = getRightVector(player);
        float[] up = cross(right, look);

        double forward = dx * look[0] + dy * look[1] + dz * look[2];
        if (forward <= 0.05D) {
            return new float[] { -9999.0F, -9999.0F, 0.0F, 0.0F };
        }
        double rightDist = dx * right[0] + dy * right[1] + dz * right[2];
        double upDist = dx * up[0] + dy * up[1] + dz * up[2];

        float fov = 70.0F;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null) {
            fov = mc.gameSettings.fovSetting;
        }
        float aspect = (float) screenW / (float) screenH;
        float tanHalfVert = (float) Math.tan(fov * 0.5D * Math.PI / 180.0D);
        float tanHalfHoriz = tanHalfVert * aspect;

        float ndcX = (float) (rightDist / (forward * tanHalfHoriz));
        float ndcY = (float) (-upDist / (forward * tanHalfVert));
        float screenX = screenW * 0.5F + ndcX * screenW * 0.5F;
        float screenY = screenH * 0.5F + ndcY * screenH * 0.5F;
        return new float[] { screenX, screenY, ndcX, ndcY };
    }

    public static float crosshairDistance(EntityPlayer player, BlockPos pos, float partialTicks, int screenW,
        int screenH) {
        double[] aim = getNodeAimPosition(player.worldObj, pos);
        if (aim == null) {
            return Float.MAX_VALUE;
        }
        float[] screen = projectToScreen(player, aim[0], aim[1], aim[2], partialTicks, screenW, screenH);
        float cx = screenW * 0.5F;
        float cy = screenH * 0.5F;
        float sdx = screen[0] - cx;
        float sdy = screen[1] - cy;
        return MathHelper.sqrt_float(sdx * sdx + sdy * sdy);
    }

    public static List<BlockPos> findNodesInRange(EntityPlayer player, double maxRange) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        if (player == null || player.worldObj == null) {
            return result;
        }
        World world = player.worldObj;
        int dimId = world.provider.dimensionId;
        int px = MathHelper.floor_double(player.posX);
        int pz = MathHelper.floor_double(player.posZ);
        int chunkRadius = (int) Math.ceil(maxRange / 16.0D) + 1;
        double maxSq = maxRange * maxRange;
        for (BlockPos pos : GrappleClientNodeIndex.INSTANCE.queryRadius(dimId, px, pz, chunkRadius)) {
            if (player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= maxSq) {
                result.add(pos);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        result = findNodesInRangeByBlockScan(player, maxRange);
        for (BlockPos pos : result) {
            GrappleClientNodeIndex.INSTANCE.addNode(dimId, pos.getX(), pos.getY(), pos.getZ());
        }
        return result;
    }

    /** Fallback when the client index is empty or stale; scans blocks in a cube around the player. */
    private static List<BlockPos> findNodesInRangeByBlockScan(EntityPlayer player, double maxRange) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        if (player == null || player.worldObj == null) {
            return result;
        }
        World world = player.worldObj;
        int px = MathHelper.floor_double(player.posX);
        int py = MathHelper.floor_double(player.posY);
        int pz = MathHelper.floor_double(player.posZ);
        int range = (int) Math.ceil(maxRange);
        double maxSq = maxRange * maxRange;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;
                    if (world.getBlock(x, y, z) == LoaderBlock.grappleAnchor) {
                        double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                        if (distSq <= maxSq) {
                            result.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void invalidateRangeCache() {
        cachedRangeNodes = new ArrayList<BlockPos>();
        lastRangeScanTick = -1000;
    }

    private static List<BlockPos> cachedRangeNodes = new ArrayList<BlockPos>();
    private static int lastRangeScanTick = -1000;
    private static double lastRangeScanX;
    private static double lastRangeScanY;
    private static double lastRangeScanZ;
    private static double lastRangeScanMaxRange = -1.0D;
    private static final int RANGE_SCAN_INTERVAL_TICKS = 3;
    private static final double RANGE_SCAN_MOVE_THRESHOLD_SQ = 1.0D;

    /** Throttled variant for per-tick/per-frame callers while not attached to a grapple line. */
    public static List<BlockPos> findNodesInRangeThrottled(EntityPlayer player, double maxRange) {
        if (player == null) {
            return new ArrayList<BlockPos>();
        }
        int tick = player.ticksExisted;
        double moveSq = (player.posX - lastRangeScanX) * (player.posX - lastRangeScanX)
            + (player.posY - lastRangeScanY) * (player.posY - lastRangeScanY)
            + (player.posZ - lastRangeScanZ) * (player.posZ - lastRangeScanZ);
        if (!cachedRangeNodes.isEmpty() && tick - lastRangeScanTick < RANGE_SCAN_INTERVAL_TICKS
            && moveSq < RANGE_SCAN_MOVE_THRESHOLD_SQ
            && Math.abs(maxRange - lastRangeScanMaxRange) < 0.01D) {
            return cachedRangeNodes;
        }
        cachedRangeNodes = findNodesInRange(player, maxRange);
        lastRangeScanTick = tick;
        lastRangeScanX = player.posX;
        lastRangeScanY = player.posY;
        lastRangeScanZ = player.posZ;
        lastRangeScanMaxRange = maxRange;
        return cachedRangeNodes;
    }

    public static boolean isWithinInteractRange(EntityPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        double maxSq = Config.grappleInteractRange * Config.grappleInteractRange;
        return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= maxSq;
    }

    /**
     * Client-side preview of whether the server would accept travel to {@code target}.
     * Matches {@code GrapplePlayerState#startTravel} / {@code #tryEnqueueTravel} distance checks.
     */
    public static boolean isTravelReachable(EntityPlayer player, BlockPos target, float partialTicks) {
        if (player == null || target == null || player.worldObj == null || !GrappleClientCache.isAttached()) {
            return false;
        }
        if (target.getX() == GrappleClientCache.getAnchorX() && target.getY() == GrappleClientCache.getAnchorY()
            && target.getZ() == GrappleClientCache.getAnchorZ()
            && !GrappleClientCache.isTraveling()) {
            return false;
        }

        double[] start;
        if (GrappleClientCache.isTraveling()) {
            BlockPos from = new BlockPos(
                GrappleClientCache.getTravelTargetX(),
                GrappleClientCache.getTravelTargetY(),
                GrappleClientCache.getTravelTargetZ());
            start = getNodeTravelPathPosition(player.worldObj, from);
        } else {
            start = getPlayerFeetPosition(player, partialTicks);
        }
        double[] end = getNodeTravelPathPosition(player.worldObj, target);
        if (start == null || end == null) {
            return false;
        }

        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double dz = end[2] - start[2];
        double distance = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double maxDist = Config.grappleMaxTravelChunkRadius * 16.0D;
        return distance <= maxDist;
    }
}
