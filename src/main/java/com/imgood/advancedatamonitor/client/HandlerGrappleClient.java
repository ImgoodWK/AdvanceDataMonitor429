package com.imgood.advancedatamonitor.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.imgood.advancedatamonitor.AdvanceDataMonitor;
import com.imgood.advancedatamonitor.gui.handler.GuiHandler;
import com.imgood.advancedatamonitor.items.ItemGrappleHook;
import com.imgood.advancedatamonitor.Config;
import com.imgood.advancedatamonitor.loader.LoaderBlock;
import com.imgood.advancedatamonitor.network.packet.PacketGrappleAction;
import com.imgood.advancedatamonitor.utils.BlockPos;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HandlerGrappleClient {

    private boolean lastShiftDown;
    private boolean lastUseDown;
    private boolean lastShiftUseDown;
    private boolean wasAttached;
    private int selectionRefreshCooldown;

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.world.isRemote) {
            return;
        }
        GrappleClientNodeIndex.INSTANCE.rebuildChunkFromBlocks(
            event.world,
            event.getChunk(),
            event.world.provider.dimensionId);
        GrappleSelectionUtil.invalidateRangeCache();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote || !(event.entity instanceof EntityPlayer)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || event.entity != mc.thePlayer) {
            return;
        }
        backfillLoadedChunks(event.world, (EntityPlayer) event.entity);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.world.isRemote || event.block != LoaderBlock.grappleAnchor) {
            return;
        }
        GrappleClientNodeIndex.INSTANCE.removeNode(
            event.world.provider.dimensionId,
            event.x,
            event.y,
            event.z);
        GrappleSelectionUtil.invalidateRangeCache();
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!event.world.isRemote || event.block != LoaderBlock.grappleAnchor) {
            return;
        }
        GrappleClientNodeIndex.INSTANCE.addNode(
            event.world.provider.dimensionId,
            event.x,
            event.y,
            event.z);
        GrappleSelectionUtil.invalidateRangeCache();
    }

    private static void backfillLoadedChunks(World world, EntityPlayer player) {
        if (world == null || player == null) {
            return;
        }
        int dimId = world.provider.dimensionId;
        int centerChunkX = MathHelper.floor_double(player.posX) >> 4;
        int centerChunkZ = MathHelper.floor_double(player.posZ) >> 4;
        int radius = Math.max(2, Config.grappleScanChunkRadius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = world.getChunkFromChunkCoords(centerChunkX + dx, centerChunkZ + dz);
                if (chunk != null) {
                    GrappleClientNodeIndex.INSTANCE.rebuildChunkFromBlocks(world, chunk, dimId);
                }
            }
        }
        GrappleSelectionUtil.invalidateRangeCache();
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.world.isRemote) {
            return;
        }
        GrappleClientNodeIndex.INSTANCE.clearChunk(
            event.world.provider.dimensionId,
            event.getChunk().xPosition,
            event.getChunk().zPosition);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.currentScreen != null) {
            return;
        }

        boolean shiftDown = isShiftDown();
        boolean useDown = isUseKeyDown();
        boolean shiftUse = shiftDown && useDown;
        boolean attached = GrappleClientCache.isAttached();

        if (attached && !wasAttached) {
            lastUseDown = false;
        }
        wasAttached = attached;

        if (attached && shiftDown && !useDown && !lastShiftDown) {
            AdvanceDataMonitor.ADMCHANEL.sendToServer(PacketGrappleAction.detach());
            lastShiftDown = shiftDown;
            lastUseDown = useDown;
            return;
        }
        lastShiftDown = shiftDown;

        if (shiftUse && !lastShiftUseDown) {
            BlockPos anchor = resolveGrappleAnchorTarget(mc, null);
            if (anchor != null) {
                openAnchorConfigGui(player, anchor);
            } else if (ItemGrappleHook.isHoldingHook(player)) {
                openHookConfigGui(player);
            }
            lastShiftUseDown = shiftUse;
            lastUseDown = useDown;
            return;
        }
        lastShiftUseDown = shiftUse;

        if (!ItemGrappleHook.isHoldingHook(player)) {
            lastUseDown = useDown;
            return;
        }

        if (!attached) {
            lastUseDown = useDown;
            return;
        }

        if (useDown && !lastUseDown && !shiftDown) {
            List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, true);
            tryTravel(player, candidates);
        }
        lastUseDown = useDown;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isClient()) {
            return;
        }
        EntityPlayer player = event.player;
        if (!ItemGrappleHook.isHoldingHook(player)) {
            return;
        }

        boolean attached = GrappleClientCache.isAttached();
        if (selectionRefreshCooldown > 0) {
            selectionRefreshCooldown--;
        } else {
            List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, attached);
            GrappleSelectionUtil.refreshSelection(player, candidates, attached, 1.0F);
            if (!attached) {
                selectionRefreshCooldown = 2;
            }
        }

        if (!attached) {
            boolean useDown = isUseKeyDown();
            if (useDown && !lastUseDown && !isShiftDown()) {
                List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, false);
                tryAttach(player, candidates);
            }
            lastUseDown = useDown;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.world.isRemote) {
            return;
        }
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = event.entityPlayer;

        if (isShiftDown()) {
            BlockPos anchor = resolveGrappleAnchorTarget(mc, event);
            if (anchor != null) {
                event.setCanceled(true);
                openAnchorConfigGui(player, anchor);
                return;
            }
            if (ItemGrappleHook.isHoldingHook(player)) {
                event.setCanceled(true);
                openHookConfigGui(player);
            }
            return;
        }

        if (!ItemGrappleHook.isHoldingHook(player)) {
            return;
        }

        boolean attached = GrappleClientCache.isAttached();
        List<BlockPos> candidates = GrappleSelectionUtil.buildCandidateNodes(player, attached);
        BlockPos target = GrappleSelectionUtil.refreshSelection(player, candidates, attached, 1.0F);
        if (target == null) {
            return;
        }

        if (attached) {
            if (!isValidTravelTarget(player, target)) {
                return;
            }
            event.setCanceled(true);
            sendTravel(target);
        } else if (GrappleSelectionUtil.isWithinInteractRange(player, target)) {
            event.setCanceled(true);
            sendAttach(target);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        if (!player.worldObj.isRemote || !GrappleClientCache.isAttached() || GrappleClientCache.isTraveling()) {
            return;
        }
        if (player.ridingEntity != null) {
            return;
        }
        double[] hang = GrappleSelectionUtil.getNodeTravelPathPosition(
            player.worldObj,
            new BlockPos(
                GrappleClientCache.getAnchorX(),
                GrappleClientCache.getAnchorY(),
                GrappleClientCache.getAnchorZ()));
        if (hang != null) {
            player.setPosition(hang[0], hang[1], hang[2]);
        }
        player.motionX = 0.0D;
        player.motionY = 0.0D;
        player.motionZ = 0.0D;
        player.fallDistance = 0.0F;
    }

    private static void openAnchorConfigGui(EntityPlayer player, BlockPos anchor) {
        if (player == null || anchor == null || player.worldObj == null) {
            return;
        }
        player.openGui(
            AdvanceDataMonitor.instance,
            GuiHandler.GRAPPLE_ANCHOR_GUI_ID,
            player.worldObj,
            anchor.getX(),
            anchor.getY(),
            anchor.getZ());
    }

    private void openHookConfigGui(EntityPlayer player) {
        if (player == null || player.worldObj == null || player.getHeldItem() == null) {
            return;
        }
        player.openGui(AdvanceDataMonitor.instance, GuiHandler.GRAPPLE_HOOK_GUI_ID, player.worldObj, 0, 0, 0);
    }

    private static BlockPos resolveGrappleAnchorTarget(Minecraft mc, PlayerInteractEvent event) {
        if (event != null && event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            if (event.world.getBlock(event.x, event.y, event.z) == LoaderBlock.grappleAnchor) {
                return new BlockPos(event.x, event.y, event.z);
            }
        }
        if (mc == null || mc.objectMouseOver == null) {
            return null;
        }
        if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        int x = mc.objectMouseOver.blockX;
        int y = mc.objectMouseOver.blockY;
        int z = mc.objectMouseOver.blockZ;
        if (mc.theWorld != null && mc.theWorld.getBlock(x, y, z) == LoaderBlock.grappleAnchor) {
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private void tryTravel(EntityPlayer player, List<BlockPos> candidates) {
        BlockPos target = GrappleSelectionUtil.refreshSelection(player, candidates, true, 1.0F);
        if (target == null) {
            target = GrappleClientCache.getSelectedTarget();
        }
        if (target != null && isValidTravelTarget(player, target)) {
            sendTravel(target);
        }
    }

    private static boolean isValidTravelTarget(EntityPlayer player, BlockPos target) {
        if (target.getX() == GrappleClientCache.getAnchorX() && target.getY() == GrappleClientCache.getAnchorY()
            && target.getZ() == GrappleClientCache.getAnchorZ()) {
            return false;
        }
        return GrappleSelectionUtil.isTravelReachable(player, target, 1.0F);
    }

    private void tryAttach(EntityPlayer player, List<BlockPos> candidates) {
        BlockPos target = GrappleSelectionUtil.refreshSelection(player, candidates, false, 1.0F);
        if (target == null) {
            target = GrappleClientCache.getSelectedTarget();
        }
        if (target != null && GrappleSelectionUtil.isWithinInteractRange(player, target)) {
            sendAttach(target);
        }
    }

    private static void sendTravel(BlockPos target) {
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketGrappleAction.travel(target.getX(), target.getY(), target.getZ()));
    }

    private static void sendAttach(BlockPos target) {
        AdvanceDataMonitor.ADMCHANEL
            .sendToServer(PacketGrappleAction.attach(target.getX(), target.getY(), target.getZ()));
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean isUseKeyDown() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) {
            return false;
        }
        if (Mouse.isButtonDown(1)) {
            return true;
        }
        return mc.gameSettings != null && mc.gameSettings.keyBindUseItem.getIsKeyPressed();
    }
}
