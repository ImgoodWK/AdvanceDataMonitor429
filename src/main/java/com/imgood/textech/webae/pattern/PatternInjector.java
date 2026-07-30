package com.imgood.textech.webae.pattern;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.imgood.textech.AdvanceDataMonitor;
import com.imgood.textech.handler.HandlerTick;
import com.imgood.textech.webae.context.WebAeOwnerContext;
import com.imgood.textech.webae.dto.PatternDto.InterfaceDto;
import com.imgood.textech.webae.dto.PatternDto.PatternInjectRequest;
import com.imgood.textech.webae.dto.PatternDto.PatternInjectResult;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;

/**
 * Pattern injector — injects an encoded pattern into a specific ME Interface slot.
 * Must run on the server thread. Uses HandlerTick.enqueueServerTask + CountDownLatch pattern.
 * Uses reflection-based access to ME Interface methods (see InterfaceLocator).
 */
public class PatternInjector {

    private static final long TIMEOUT_MS = 10_000L;

    /**
     * Blocking inject for HTTP handlers.
     */
    public static PatternInjectResult injectBlocking(String playerUuid, PatternInjectRequest request) {
        final PatternInjectResult[] holder = new PatternInjectResult[1];
        final CountDownLatch latch = new CountDownLatch(1);

        HandlerTick.enqueueServerTask(new Runnable() {

            @Override
            public void run() {
                try {
                    EntityPlayerMP player = WebAeOwnerContext.getOwnerPlayerOrFake(playerUuid);
                    if (player == null) {
                        holder[0] = new PatternInjectResult(false, "Owner context unavailable");
                    } else {
                        holder[0] = inject(playerUuid, player, request);
                    }
                } catch (Throwable t) {
                    AdvanceDataMonitor.LOG.error("[WebAE] Pattern injection failed", t);
                    holder[0] = new PatternInjectResult(false, "Injection error: " + t.getMessage());
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            if (latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return holder[0] != null ? holder[0] : new PatternInjectResult(false, "Timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
        }
        return new PatternInjectResult(false, "Injection timed out");
    }

    private static PatternInjectResult inject(String ownerUuid, EntityPlayerMP player, PatternInjectRequest request) {
        try {
            // 1. Decode NBT from JSON
            net.minecraft.nbt.NBTTagCompound nbt;
            try {
                nbt = PatternEncoder.decode(request.encodedNbt);
            } catch (Exception e) {
                return new PatternInjectResult(false, "Failed to decode pattern NBT: " + e.getMessage());
            }

            // 2. Create encoded pattern ItemStack
            ItemStack patternStack;
            try {
                patternStack = AEApi.instance()
                    .definitions()
                    .items()
                    .encodedPattern()
                    .maybeStack(1)
                    .get();
                if (patternStack == null) {
                    return new PatternInjectResult(false, "Failed to create encoded pattern ItemStack");
                }
                patternStack.setTagCompound(nbt);
            } catch (Exception e) {
                return new PatternInjectResult(false, "Failed to create pattern ItemStack: " + e.getMessage());
            }

            // 3. Locate the interface by coordinates
            World world = DimensionManager.getWorld(request.interfaceDim);
            if (world == null) {
                return new PatternInjectResult(false, "Dimension " + request.interfaceDim + " not loaded");
            }

            Object interfaceTarget = InterfaceLocator.resolveInterface(
                request.interfaceX,
                request.interfaceY,
                request.interfaceZ,
                request.interfaceDim,
                request.interfaceSide);
            if (interfaceTarget == null) {
                return new PatternInjectResult(
                    false,
                    "No ME Interface at (" + request.interfaceX
                        + ","
                        + request.interfaceY
                        + ","
                        + request.interfaceZ
                        + ")");
            }

            // 4. Verify it's an AE2 interface
            if (!InterfaceLocator.isInterface(interfaceTarget)) {
                return new PatternInjectResult(false, "Tile entity is not an ME Interface");
            }

            // 5. Permission check (BUILD permission on AE network)
            if (!InterfaceLocator.belongsToGrid(interfaceTarget, ownerUuid, request.networkId)) {
                return new PatternInjectResult(false, "Target interface is not on the selected AE network");
            }
            if (!hasBuildPermission(interfaceTarget, player)) {
                return new PatternInjectResult(false, "No BUILD permission on AE network");
            }

            // 6. Slot validation
            int capacityUpgrades = InterfaceLocator
                .getInstalledUpgrades(interfaceTarget, appeng.api.config.Upgrades.PATTERN_CAPACITY);
            int activeSlots = (capacityUpgrades + 1) * 9;
            if (request.slotIndex < 0 || request.slotIndex >= activeSlots) {
                return new PatternInjectResult(
                    false,
                    "Slot index " + request.slotIndex + " out of range (0-" + (activeSlots - 1) + ")");
            }

            IInventory patterns = InterfaceLocator.getPatterns(interfaceTarget);
            if (patterns == null) {
                return new PatternInjectResult(false, "Cannot access interface pattern inventory");
            }

            // Check if slot is occupied
            ItemStack existing = patterns.getStackInSlot(request.slotIndex);
            if (existing != null && existing.getItem() != null) {
                return new PatternInjectResult(false, "Slot " + request.slotIndex + " is already occupied");
            }

            // 7. Duplicate check — scan all slots for identical pattern
            for (int i = 0; i < activeSlots; i++) {
                ItemStack slotStack = patterns.getStackInSlot(i);
                if (slotStack != null && slotStack.getItem() != null) {
                    if (net.minecraft.item.ItemStack.areItemStacksEqual(patternStack, slotStack)) {
                        return new PatternInjectResult(false, "Pattern already exists in slot " + i);
                    }
                }
            }

            // 7b. Consume the blank pattern only after all failure-prone target checks pass.
            if (request.consumeBlank && !BlankPatternHelper.consumeOne(ownerUuid, request.networkId)) {
                return new PatternInjectResult(false, "NO_BLANK_PATTERN:空白样板不足");
            }

            // 8. Inject the pattern
            patterns.setInventorySlotContents(request.slotIndex, patternStack);

            // 9. Save changes
            InterfaceLocator.saveChanges(interfaceTarget);

            // 10. Fire MENetworkCraftingPatternChange event
            try {
                InterfaceLocator.postPatternChangeEvent(interfaceTarget);
            } catch (Exception e) {
                AdvanceDataMonitor.LOG.warn("[WebAE] Failed to post pattern change event: {}", e.getMessage());
            }

            // Build updated interface DTO
            InterfaceDto updatedDto = InterfaceLocator.buildInterfaceDto(interfaceTarget);

            PatternBrowseService.invalidateAll();

            return new PatternInjectResult(true, "Pattern injected to slot " + request.slotIndex, updatedDto);

        } catch (Exception e) {
            AdvanceDataMonitor.LOG.error("[WebAE] Pattern injection failed", e);
            return new PatternInjectResult(false, "Injection error: " + e.getMessage());
        }
    }

    private static boolean hasBuildPermission(Object target, EntityPlayer player) {
        try {
            IGridNode node = InterfaceLocator.getGridNode(target);
            if (node == null) return false;
            IGrid grid = node.getGrid();
            if (grid == null) return false;
            ISecurityGrid security = grid.getCache(ISecurityGrid.class);
            if (security == null) return true;
            return security.hasPermission(player, SecurityPermissions.BUILD);
        } catch (Exception e) {
            return true;
        }
    }

}
