package com.imgood.textech.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.Config;
import com.imgood.textech.entity.EntityGrappleSlide;
import com.imgood.textech.items.ItemGrappleHook;
import com.imgood.textech.network.packet.PacketGrappleSync;
import com.imgood.textech.utils.BlockPos;

public final class GrapplePlayerState {

    private static final Map<UUID, State> STATES = new HashMap<UUID, State>();

    private GrapplePlayerState() {}

    public static boolean isAttached(EntityPlayer player) {
        State state = STATES.get(player.getUniqueID());
        return state != null && state.attached;
    }

    public static boolean isTraveling(EntityPlayer player) {
        State state = STATES.get(player.getUniqueID());
        return state != null && state.traveling;
    }

    public static State getState(EntityPlayer player) {
        return STATES.get(player.getUniqueID());
    }

    public static void attach(EntityPlayer player, int dimId, int x, int y, int z) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        if (!ItemGrappleHook.isHoldingHook(player)) {
            return;
        }
        State existing = STATES.get(player.getUniqueID());
        if (existing != null && existing.traveling) {
            return;
        }
        if (existing != null && existing.attached) {
            if (existing.anchorX == x && existing.anchorY == y && existing.anchorZ == z) {
                return;
            }
            travelTo(player, x, y, z);
            return;
        }
        if (!GrappleNodeIndex.INSTANCE.contains(dimId, x, y, z)) {
            return;
        }
        State state = new State();
        state.attached = true;
        state.traveling = false;
        state.dimId = dimId;
        state.anchorX = x;
        state.anchorY = y;
        state.anchorZ = z;
        state.travelProgress = 0.0F;
        refreshNearby(state);
        STATES.put(player.getUniqueID(), state);
        snapToHangPosition(player, state);
        GrapplePlanningSession.recordNode(player, x, y, z);
        syncToPlayer((EntityPlayerMP) player, state);
    }

    public static void detach(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        cancelTravelEntity(player);
        State state = STATES.remove(player.getUniqueID());
        if (state != null) {
            state.travelQueue.clear();
            if (player instanceof EntityPlayerMP) {
                syncDetached((EntityPlayerMP) player);
            }
        }
    }

    public static void travelTo(EntityPlayer player, int targetX, int targetY, int targetZ) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || !state.attached) {
            return;
        }
        if (!ItemGrappleHook.hasHookAnywhere(player)) {
            detach(player);
            return;
        }
        if (state.traveling) {
            if (tryEnqueueTravel(player, state, targetX, targetY, targetZ) && player instanceof EntityPlayerMP) {
                syncToPlayer((EntityPlayerMP) player, state);
            }
            return;
        }
        if (targetX == state.anchorX && targetY == state.anchorY && targetZ == state.anchorZ) {
            return;
        }
        startTravel(player, state, targetX, targetY, targetZ, ItemGrappleHook.resolveTravelSpeed(player));
    }

    public static boolean travelPath(EntityPlayer player, List<BlockPos> path) {
        if (player == null || player.worldObj.isRemote || path == null || path.size() < 2) {
            return false;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || !state.attached || state.traveling) {
            return false;
        }
        if (!ItemGrappleHook.hasHookAnywhere(player)) {
            detach(player);
            return false;
        }
        double speed = ItemGrappleHook.resolveTravelSpeed(player);
        int dimId = player.worldObj.provider.dimensionId;
        double maxDist = Config.grappleMaxTravelChunkRadius * 16.0D;
        BlockPos start = path.get(0);
        if (!isSameBlock(start.getX(), start.getY(), start.getZ(), state.anchorX, state.anchorY, state.anchorZ)) {
            return false;
        }
        BlockPos firstHop = path.get(1);
        int prevX = state.anchorX;
        int prevY = state.anchorY;
        int prevZ = state.anchorZ;
        for (int i = 1; i < path.size(); i++) {
            BlockPos hop = path.get(i);
            if (!GrappleNodeIndex.INSTANCE.contains(dimId, hop.getX(), hop.getY(), hop.getZ())) {
                return false;
            }
            double[] startHang = resolveHangPosition(player.worldObj, prevX, prevY, prevZ);
            double[] endHang = resolveHangPosition(player.worldObj, hop.getX(), hop.getY(), hop.getZ());
            if (startHang == null || endHang == null) {
                return false;
            }
            double dx = endHang[0] - startHang[0];
            double dy = endHang[1] - startHang[1];
            double dz = endHang[2] - startHang[2];
            double distance = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
            if (distance > maxDist) {
                return false;
            }
            prevX = hop.getX();
            prevY = hop.getY();
            prevZ = hop.getZ();
        }
        state.travelQueue.clear();
        for (int i = 2; i < path.size(); i++) {
            if (state.travelQueue.size() >= Config.grappleMaxTravelQueueSize) {
                return false;
            }
            BlockPos hop = path.get(i);
            state.travelQueue.add(new GrappleQueuedHop(hop.getX(), hop.getY(), hop.getZ(), speed));
        }
        startTravel(player, state, firstHop.getX(), firstHop.getY(), firstHop.getZ(), speed);
        return true;
    }

    private static void startTravel(EntityPlayer player, State state, int targetX, int targetY, int targetZ,
        double travelSpeed) {
        int dimId = player.worldObj.provider.dimensionId;
        if (!GrappleNodeIndex.INSTANCE.contains(dimId, targetX, targetY, targetZ)) {
            return;
        }
        double[] start = resolvePlayerFootPosition(player);
        double[] end = resolveHangPosition(player.worldObj, targetX, targetY, targetZ);
        if (start == null || end == null) {
            return;
        }
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double dz = end[2] - start[2];
        double distance = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double maxDist = Config.grappleMaxTravelChunkRadius * 16.0D;
        if (distance > maxDist) {
            return;
        }

        cancelTravelEntity(player);
        EntityGrappleSlide slide = new EntityGrappleSlide(
            player.worldObj,
            player,
            start[0],
            start[1],
            start[2],
            end[0],
            end[1],
            end[2],
            targetX,
            targetY,
            targetZ,
            travelSpeed);
        player.worldObj.spawnEntityInWorld(slide);
        player.mountEntity(slide);

        state.traveling = true;
        state.travelStartX = start[0];
        state.travelStartY = start[1];
        state.travelStartZ = start[2];
        state.travelEndX = end[0];
        state.travelEndY = end[1];
        state.travelEndZ = end[2];
        state.travelDistance = distance;
        state.travelProgress = 0.0F;
        state.travelTargetX = targetX;
        state.travelTargetY = targetY;
        state.travelTargetZ = targetZ;
        state.activeTravelSpeed = travelSpeed;
        if (player instanceof EntityPlayerMP) {
            syncToPlayer((EntityPlayerMP) player, state);
        }
    }

    private static boolean tryEnqueueTravel(EntityPlayer player, State state, int targetX, int targetY, int targetZ) {
        if (isSameBlock(targetX, targetY, targetZ, state.travelTargetX, state.travelTargetY, state.travelTargetZ)) {
            return false;
        }
        if (isSameBlock(targetX, targetY, targetZ, state.anchorX, state.anchorY, state.anchorZ) && !state.traveling) {
            return false;
        }
        for (GrappleQueuedHop queued : state.travelQueue) {
            if (isSameBlock(targetX, targetY, targetZ, queued.x, queued.y, queued.z)) {
                return false;
            }
        }
        if (state.travelQueue.size() >= Config.grappleMaxTravelQueueSize) {
            return false;
        }
        int dimId = player.worldObj.provider.dimensionId;
        if (!GrappleNodeIndex.INSTANCE.contains(dimId, targetX, targetY, targetZ)) {
            return false;
        }
        int fromX = state.travelTargetX;
        int fromY = state.travelTargetY;
        int fromZ = state.travelTargetZ;
        double[] start = resolveHangPosition(player.worldObj, fromX, fromY, fromZ);
        double[] end = resolveHangPosition(player.worldObj, targetX, targetY, targetZ);
        if (start == null || end == null) {
            return false;
        }
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double dz = end[2] - start[2];
        double distance = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double maxDist = Config.grappleMaxTravelChunkRadius * 16.0D;
        if (distance > maxDist) {
            return false;
        }
        state.travelQueue
            .add(new GrappleQueuedHop(targetX, targetY, targetZ, ItemGrappleHook.resolveTravelSpeed(player)));
        return true;
    }

    public static void completeTravel(EntityPlayer player, int targetX, int targetY, int targetZ) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || !state.attached) {
            return;
        }
        cancelTravelEntity(player);
        state.traveling = false;
        state.travelProgress = 0.0F;
        state.anchorX = targetX;
        state.anchorY = targetY;
        state.anchorZ = targetZ;
        refreshNearby(state);
        GrapplePlanningSession.recordNode(player, targetX, targetY, targetZ);
        if (player instanceof EntityPlayerMP
            && ItemGrappleHook.getHookMode(player) == com.imgood.textech.items.GrappleHookMode.PLANNING) {
            GrappleRouteSync.syncBuffer((EntityPlayerMP) player);
        }
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        snapToHangPosition(mp, state);
        syncToPlayer(mp, state);
        if (!state.travelQueue.isEmpty()) {
            GrappleQueuedHop next = state.travelQueue.remove(0);
            startTravel(mp, state, next.x, next.y, next.z, next.speed);
        }
    }

    public static void updateTravelProgress(EntityPlayer player, float progress) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || !state.traveling) {
            return;
        }
        state.travelProgress = progress;
        if (player instanceof EntityPlayerMP && player.ticksExisted % 2 == 0) {
            syncToPlayer((EntityPlayerMP) player, state);
        }
    }

    public static void tick(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote) {
            return;
        }
        State state = STATES.get(player.getUniqueID());
        if (state == null || !state.attached) {
            return;
        }
        if (!ItemGrappleHook.hasHookAnywhere(player)) {
            detach(player);
            return;
        }
        if (state.traveling) {
            if (!(player.ridingEntity instanceof EntityGrappleSlide)) {
                detach(player);
                return;
            }
            player.fallDistance = 0.0F;
            return;
        }
        snapToHangPosition(player, state);
        player.fallDistance = 0.0F;
    }

    private static void cancelTravelEntity(EntityPlayer player) {
        if (player == null) {
            return;
        }
        if (player.ridingEntity instanceof EntityGrappleSlide) {
            EntityGrappleSlide slide = (EntityGrappleSlide) player.ridingEntity;
            if (player instanceof EntityPlayerMP) {
                slide.dismountPlayer((EntityPlayerMP) player);
            } else {
                player.mountEntity(null);
            }
            slide.setDead();
        }
    }

    private static void snapToHangPosition(EntityPlayer player, State state) {
        double[] hang = resolveHangPosition(player.worldObj, state.anchorX, state.anchorY, state.anchorZ);
        if (hang == null) {
            detach(player);
            return;
        }
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).playerNetServerHandler
                .setPlayerLocation(hang[0], hang[1], hang[2], player.rotationYaw, player.rotationPitch);
        } else {
            player.setPosition(hang[0], hang[1], hang[2]);
        }
    }

    public static double[] resolvePlayerFootPosition(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        return new double[] { player.posX, player.posY, player.posZ };
    }

    public static double[] resolveHangPosition(World world, int x, int y, int z) {
        return GrappleAnchorPositions.resolveHangPosition(world, x, y, z);
    }

    private static void refreshNearby(State state) {
        state.nearbyNodes.clear();
        List<BlockPos> nodes = GrappleNodeIndex.INSTANCE.queryAroundAnchor(state.dimId, state.anchorX, state.anchorZ);
        for (BlockPos pos : nodes) {
            if (pos.getX() == state.anchorX && pos.getY() == state.anchorY && pos.getZ() == state.anchorZ) {
                continue;
            }
            state.nearbyNodes.add(pos);
        }
    }

    public static void onAnchorBroken(int dimId, int x, int y, int z) {
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            if (state.dimId != dimId) {
                continue;
            }
            if (state.anchorX == x && state.anchorY == y && state.anchorZ == z) {
                it.remove();
                EntityPlayerMP player = findPlayer(entry.getKey());
                if (player != null) {
                    cancelTravelEntity(player);
                    syncDetached(player);
                }
                continue;
            }
            removeNearbyNode(state, x, y, z);
            removeQueuedNode(state, x, y, z);
        }
    }

    public static void onNodeIndexChanged(int dimId, ChunkCoordIntPair chunk) {
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            if (!state.attached || state.dimId != dimId) {
                continue;
            }
            int anchorChunkX = state.anchorX >> 4;
            int anchorChunkZ = state.anchorZ >> 4;
            int radius = Config.grappleScanChunkRadius;
            if (Math.abs(chunk.chunkXPos - anchorChunkX) <= radius
                && Math.abs(chunk.chunkZPos - anchorChunkZ) <= radius) {
                refreshNearby(state);
                EntityPlayerMP player = findPlayer(entry.getKey());
                if (player != null) {
                    syncToPlayer(player, state);
                }
            }
        }
    }

    private static void removeNearbyNode(State state, int x, int y, int z) {
        Iterator<BlockPos> it = state.nearbyNodes.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (isSameBlock(x, y, z, pos.getX(), pos.getY(), pos.getZ())) {
                it.remove();
            }
        }
    }

    private static void removeQueuedNode(State state, int x, int y, int z) {
        Iterator<GrappleQueuedHop> it = state.travelQueue.iterator();
        while (it.hasNext()) {
            GrappleQueuedHop hop = it.next();
            if (isSameBlock(x, y, z, hop.x, hop.y, hop.z)) {
                it.remove();
            }
        }
    }

    private static boolean isSameBlock(int x1, int y1, int z1, int x2, int y2, int z2) {
        return x1 == x2 && y1 == y2 && z1 == z2;
    }

    private static EntityPlayerMP findPlayer(UUID id) {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return null;
        }
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (id.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    public static void syncToPlayer(EntityPlayerMP player, State state) {
        if (player == null || state == null) {
            return;
        }
        AdvanceDataMonitor.ADMCHANEL.sendTo(buildSyncPacket(state), player);
    }

    private static void syncDetached(EntityPlayerMP player) {
        AdvanceDataMonitor.ADMCHANEL.sendTo(PacketGrappleSync.detached(), player);
    }

    public static PacketGrappleSync buildSyncPacket(State state) {
        PacketGrappleSync packet = new PacketGrappleSync();
        packet.attached = state.attached;
        packet.traveling = state.traveling;
        packet.anchorX = state.anchorX;
        packet.anchorY = state.anchorY;
        packet.anchorZ = state.anchorZ;
        packet.travelTargetX = state.travelTargetX;
        packet.travelTargetY = state.travelTargetY;
        packet.travelTargetZ = state.travelTargetZ;
        packet.travelProgress = state.travelProgress;
        packet.nodes = new ArrayList<BlockPos>(state.nearbyNodes);
        packet.travelQueue = new ArrayList<BlockPos>();
        for (GrappleQueuedHop hop : state.travelQueue) {
            packet.travelQueue.add(hop.toBlockPos());
        }
        return packet;
    }

    public static final class State {

        public boolean attached;
        public boolean traveling;
        public int dimId;
        public int anchorX;
        public int anchorY;
        public int anchorZ;
        public double travelStartX;
        public double travelStartY;
        public double travelStartZ;
        public double travelEndX;
        public double travelEndY;
        public double travelEndZ;
        public double travelDistance;
        public float travelProgress;
        public int travelTargetX;
        public int travelTargetY;
        public int travelTargetZ;
        public double activeTravelSpeed;
        public final List<BlockPos> nearbyNodes = new ArrayList<BlockPos>();
        public final List<GrappleQueuedHop> travelQueue = new ArrayList<GrappleQueuedHop>();
    }
}
